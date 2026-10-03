package com.altersub.core.session

import android.util.Log
import com.altersub.core.clock.SubtitleClock
import com.altersub.core.clock.TrackOffsets
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.parser.SrtParser
import com.altersub.core.parser.SubtitleIndex
import com.altersub.detection.DetectionArbiter
import com.altersub.detection.DetectionSource
import com.altersub.provider.CompositeSubtitleProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * Coordinates what is playing → subtitle search → download → active track. Kept free of Android
 * components (AlterSubApp owns one and delegates to it) so the race handling can be unit-tested.
 *
 * Every activated track is remembered in [picks] with its offset and progress: detecting or searching the
 * same title again brings back the same track and offset, and after a restart a streaming app resuming near
 * where the last pick left off brings that pick back (the only way to recognise Netflix content, KI-26).
 */
class SubtitleSession(
    private val provider: CompositeSubtitleProvider,
    private val clock: SubtitleClock,
    private val scope: CoroutineScope,
    private val subtitleDir: File,
    private val picks: PickMemory,
    private val onTrackActivated: () -> Unit
) {

    private val _currentContent = MutableStateFlow<ContentMetadata?>(null)
    val currentContent: StateFlow<ContentMetadata?> = _currentContent.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val availableTracks: StateFlow<List<SubtitleTrack>> = _availableTracks.asStateFlow()

    private val _activeTrack = MutableStateFlow<SubtitleTrack?>(null)
    val activeTrack: StateFlow<SubtitleTrack?> = _activeTrack.asStateFlow()

    private val _subtitleIndex = MutableStateFlow<SubtitleIndex?>(null)
    val subtitleIndex: StateFlow<SubtitleIndex?> = _subtitleIndex.asStateFlow()

    // Guards the arbiter, the jobs below, and every content/track state transition.
    // Detections arrive concurrently from the main thread, web server threads and IO coroutines.
    private val detectionLock = Any()
    private val arbiter = DetectionArbiter()
    private val trackOffsets = TrackOffsets()
    private var searchJob: Job? = null
    private var activationJob: Job? = null

    // Only trustworthy picks are remembered: the user's own choices and media-session titles. Screen-scraped
    // guesses (KI-3..KI-5, e.g. a launcher menu taken for a title) would just fill the recent list with noise.
    private var rememberPicks = false

    val acceptsScreenDetection: Boolean get() = arbiter.acceptsScreenDetection

    fun onContentDetected(metadata: ContentMetadata, source: DetectionSource) {
        synchronized(detectionLock) {
            if (!arbiter.accept(metadata, source, _currentContent.value)) return

            searchJob?.cancel()
            activationJob?.cancel()
            saveCurrentProgress()

            // Never leave the previous title's subtitles (or its sync offset) running over the new one
            _currentContent.value = metadata
            _availableTracks.value = emptyList()
            _activeTrack.value = null
            _subtitleIndex.value = null
            clock.setOffset(trackOffsets.switchTo(null, clock.userOffsetMs.value))
            Log.i(TAG, "New content detected via $source: ${metadata.getDisplayName()}")
            rememberPicks = source != DetectionSource.ACCESSIBILITY

            // Seen before: bring back the same track and offset straight away instead of the search's first hit
            val remembered = picks.forContent(metadata.contentKey)
            if (remembered != null) {
                _availableTracks.value = listOf(remembered.track)
                activateTrack(remembered.track, remembered.offsetMs)
            }

            searchJob = scope.launch {
                val tracks = provider.searchAll(metadata, "en")

                synchronized(detectionLock) {
                    // Providers wait on the network, so a newer detection may have replaced this one meanwhile
                    if (!isActive || _currentContent.value !== metadata) return@launch

                    // Uploads made while the search was running aren't in its results
                    val merged = (listOfNotNull(remembered?.track) + provider.localTracksFor(metadata) + tracks)
                        .distinctBy { it.id }
                    _availableTracks.value = merged

                    val userAlreadyChose = _activeTrack.value != null || activationJob?.isActive == true
                    if (!userAlreadyChose && merged.isNotEmpty()) {
                        activateTrack(merged.first())
                    }
                }
            }
        }
    }

    /**
     * A streaming app reported its playback (from the media-session listener or poller). Keeps the remembered
     * progress current, and when nothing is loaded, brings back the app's last pick if playback resumed near
     * where that pick left off: that is how a restart, or returning to the same Netflix film, is recognised.
     */
    fun onPlaybackObserved(appPackage: String, positionMs: Long, playing: Boolean) {
        synchronized(detectionLock) {
            val content = _currentContent.value
            if (_activeTrack.value != null && content != null) {
                picks.updateProgress(content.contentKey, clock.userOffsetMs.value, positionMs, appPackage)
                return
            }
            // Never override something the user (or a detection) is in the middle of loading
            if (!playing || activationJob?.isActive == true || searchJob?.isActive == true) return
            val pick = picks.latestForApp(appPackage) ?: return
            if (abs(positionMs - pick.positionMs) > RESUME_WINDOW_MS) return

            Log.i(TAG, "Resuming ${pick.content.getDisplayName()} in $appPackage at ${positionMs / 1000}s")
            restore(pick)
        }
    }

    /** The user moved the sync (offset or "Set time") on the phone remote: remember it for this title. */
    fun onSyncAdjusted() {
        synchronized(detectionLock) {
            saveCurrentProgress()
        }
    }

    /** Recent picks, newest first, for the phone remote's one-tap list. */
    fun recentPicks(): List<PickMemory.Pick> = picks.recent(RECENT_LIMIT)

    /** User tapped a recent pick on the phone remote. Returns false if it is no longer remembered. */
    fun restorePick(contentKey: String): Boolean {
        synchronized(detectionLock) {
            val pick = picks.forContent(contentKey) ?: return false
            restore(pick)
            return true
        }
    }

    // Caller must hold detectionLock. Saves the latest offset and position of what is being left, so coming
    // back to it never brings back an older offset than the one last used.
    private fun saveCurrentProgress() {
        val content = _currentContent.value ?: return
        if (_activeTrack.value == null) return
        picks.updateProgress(content.contentKey, clock.userOffsetMs.value, clock.getPositionMs())
    }

    // Caller must hold detectionLock
    private fun restore(pick: PickMemory.Pick) {
        searchJob?.cancel()
        activationJob?.cancel()
        saveCurrentProgress()
        // Behaves like the user's own choice: screen scraping must not replace it
        arbiter.onUserChoice()
        rememberPicks = true
        _currentContent.value = pick.content
        _availableTracks.value = listOf(pick.track)
        _activeTrack.value = null
        _subtitleIndex.value = null
        clock.setOffset(trackOffsets.switchTo(null, clock.userOffsetMs.value))
        activateTrack(pick.track, pick.offsetMs)
    }

    fun onMediaSessionsEnded() {
        synchronized(detectionLock) {
            arbiter.onMediaSessionsEnded()
        }
        // The player is gone, so stop advancing subtitles over whatever is on screen now
        clock.pause()
    }

    /** User picked a track on the phone remote. */
    fun selectTrack(track: SubtitleTrack) {
        synchronized(detectionLock) {
            arbiter.onUserChoice()
            rememberPicks = true
            activateTrack(track)
        }
    }

    fun loadDirectSrt(file: File, displayName: String) {
        synchronized(detectionLock) {
            val track = provider.addLocalTrack(file, displayName, _currentContent.value)
            _availableTracks.value = listOf(track) + _availableTracks.value
            arbiter.onUserChoice()
            rememberPicks = true
            activateTrack(track)
        }
    }

    fun setTestSubtitleIndex(index: SubtitleIndex) {
        _subtitleIndex.value = index
    }

    // Caller must hold detectionLock. Replaces any in-flight activation so a slow download can't win over a later choice.
    // [rememberedOffsetMs] seeds the track's offset when a remembered pick is brought back.
    private fun activateTrack(track: SubtitleTrack, rememberedOffsetMs: Long? = null) {
        activationJob?.cancel()
        val content = _currentContent.value
        rememberedOffsetMs?.let { trackOffsets.preset(track.id, it) }

        activationJob = scope.launch {
            try {
                val srtFile = provider.downloadTrack(track, subtitleDir)
                if (srtFile == null || !srtFile.exists()) return@launch

                val cues = FileInputStream(srtFile).use { SrtParser.parse(it) }

                synchronized(detectionLock) {
                    if (!isActive || _currentContent.value !== content) return@launch
                    clock.setOffset(trackOffsets.switchTo(track.id, clock.userOffsetMs.value))
                    _subtitleIndex.value = SubtitleIndex(cues)
                    _activeTrack.value = track
                    if (content != null && rememberPicks) {
                        picks.remember(content, track, clock.userOffsetMs.value, clock.getPositionMs(), appPackage = null)
                    }
                }
                Log.i(TAG, "Activated track: ${track.title} with ${cues.size} cues")

                // Ensure overlay service is running
                onTrackActivated()
            } catch (e: CancellationException) {
                throw e // Superseded by a newer choice or content: not an error
            } catch (e: Exception) {
                Log.e(TAG, "Error activating track: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "AlterSubApp"

        /** How close a resumed position must be to the remembered one to count as the same title. */
        const val RESUME_WINDOW_MS = 5 * 60_000L
        const val RECENT_LIMIT = 5
    }
}
