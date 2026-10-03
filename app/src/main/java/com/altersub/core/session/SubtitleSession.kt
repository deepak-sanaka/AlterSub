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

/**
 * Coordinates what is playing → subtitle search → download → active track. Kept free of Android
 * components (AlterSubApp owns one and delegates to it) so the race handling can be unit-tested.
 */
class SubtitleSession(
    private val provider: CompositeSubtitleProvider,
    private val clock: SubtitleClock,
    private val scope: CoroutineScope,
    private val subtitleDir: File,
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

    val acceptsScreenDetection: Boolean get() = arbiter.acceptsScreenDetection

    fun onContentDetected(metadata: ContentMetadata, source: DetectionSource) {
        synchronized(detectionLock) {
            if (!arbiter.accept(metadata, source, _currentContent.value)) return

            searchJob?.cancel()
            activationJob?.cancel()

            // Never leave the previous title's subtitles (or its sync offset) running over the new one
            _currentContent.value = metadata
            _availableTracks.value = emptyList()
            _activeTrack.value = null
            _subtitleIndex.value = null
            clock.setOffset(trackOffsets.switchTo(null, clock.userOffsetMs.value))
            Log.i(TAG, "New content detected via $source: ${metadata.getDisplayName()}")

            searchJob = scope.launch {
                val tracks = provider.searchAll(metadata, "en")

                synchronized(detectionLock) {
                    // Providers wait on the network, so a newer detection may have replaced this one meanwhile
                    if (!isActive || _currentContent.value !== metadata) return@launch

                    // Uploads made while the search was running aren't in its results
                    val merged = (provider.localTracksFor(metadata) + tracks).distinctBy { it.id }
                    _availableTracks.value = merged

                    val userAlreadyChose = _activeTrack.value != null || activationJob?.isActive == true
                    if (!userAlreadyChose && merged.isNotEmpty()) {
                        activateTrack(merged.first())
                    }
                }
            }
        }
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
            activateTrack(track)
        }
    }

    fun loadDirectSrt(file: File, displayName: String) {
        synchronized(detectionLock) {
            val track = provider.addLocalTrack(file, displayName, _currentContent.value)
            _availableTracks.value = listOf(track) + _availableTracks.value
            arbiter.onUserChoice()
            activateTrack(track)
        }
    }

    fun setTestSubtitleIndex(index: SubtitleIndex) {
        _subtitleIndex.value = index
    }

    // Caller must hold detectionLock. Replaces any in-flight activation so a slow download can't win over a later choice.
    private fun activateTrack(track: SubtitleTrack) {
        activationJob?.cancel()
        val content = _currentContent.value

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
    }
}
