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
import com.altersub.detection.DiagLog
import com.altersub.provider.CompositeSubtitleProvider
import com.altersub.provider.TitleResolver
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

/** Where the current title's subtitle search stands, for the phone remote. */
enum class SearchState {
    IDLE,
    SEARCHING,

    /** Several films match the title; the user has to pick one of [SubtitleSession.matches]. */
    CHOOSE,

    /** The search finished without any subtitles. */
    NOT_FOUND,
    FOUND
}

/**
 * Coordinates what is playing → which film it is → subtitle search → download → active track. Kept free of Android
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
    private val resolver: TitleResolver = TitleResolver.NONE,
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

    /** Films the current title could be, from the catalog: shown on the phone to choose or correct. */
    private val _matches = MutableStateFlow<List<TitleMatch>>(emptyList())
    val matches: StateFlow<List<TitleMatch>> = _matches.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState.IDLE)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // Guards the arbiter, the jobs below, and every content/track state transition.
    // Detections arrive concurrently from the main thread, web server threads and IO coroutines.
    private val detectionLock = Any()
    private val arbiter = DetectionArbiter()
    private val trackOffsets = TrackOffsets()
    private var searchJob: Job? = null
    private var activationJob: Job? = null
    private var screenCheckJob: Job? = null

    // Only trustworthy picks are remembered: the user's own choices and media-session titles. Screen-scraped
    // guesses (once, a launcher menu taken for a title) would just fill the recent list with noise.
    private var rememberPicks = false

    val acceptsScreenDetection: Boolean get() = arbiter.acceptsScreenDetection

    fun onContentDetected(metadata: ContentMetadata, source: DetectionSource) {
        detect(metadata, source, rawQuery = null)
    }

    /**
     * A title read off a streaming app's screen. Screen text is often UI (a page header, a menu), so it is taken
     * only once the catalog knows a film or series by exactly that name. That's checked before anything is
     * replaced, so text that fails the check leaves the current subtitles alone.
     */
    fun onScreenTitle(metadata: ContentMetadata, requireChoice: Boolean = false, stillCurrent: () -> Boolean = { true }) {
        synchronized(detectionLock) {
            if (!arbiter.acceptsScreenDetection || _currentContent.value?.contentKey == metadata.contentKey) return
            screenCheckJob?.cancel()
            screenCheckJob = scope.launch {
                val candidates = resolver.find(metadata)
                val match = TitleMatching.verify(metadata, candidates)
                if (match == null) {
                    DiagLog.d { "Ignoring screen text \"${metadata.title}\": not a known film or series" }
                    return@launch
                }
                synchronized(detectionLock) verifiedResult@{
                    if (!isActive || !stillCurrent()) return@verifiedResult
                    val exact = candidates.filter { TitleMatching.normalize(it.name) == TitleMatching.normalize(metadata.title) }
                    val ambiguous = requireChoice && exact.size > 1 && metadata.year == null
                    val verified = if (ambiguous) metadata else metadata.copy(title = match.name, year = match.year ?: metadata.year, imdbId = match.imdbId)
                    // The phone still lists the other films of that name, to correct the guess
                    if (detect(verified, DetectionSource.ACCESSIBILITY, rawQuery = null,
                            requireChoice = ambiguous, catalogCandidates = candidates)) {
                        _matches.value = TitleMatching.options(metadata, candidates)
                    }
                }
            }
        }
    }

    /**
     * A search typed on the phone, optionally with a year ("Under the Open Sky 2020"). Unlike automatic
     * detections, an ambiguous title stops and asks the user (see [matches] and [chooseMatch]).
     */
    fun searchByText(query: String) {
        val parsed = ManualQuery.parse(query)
        if (parsed.title.isBlank()) return
        detect(ContentMetadata(title = parsed.title, year = parsed.year), DetectionSource.MANUAL, rawQuery = parsed.raw)
    }

    /** The user picked which film they meant, from [matches]. Returns false if it isn't one of them. */
    fun chooseMatch(imdbId: String): Boolean {
        synchronized(detectionLock) {
            val match = _matches.value.firstOrNull { it.imdbId == imdbId } ?: return false
            val base = _currentContent.value ?: return false
            searchJob?.cancel()
            activationJob?.cancel()
            saveCurrentProgress()
            arbiter.onUserChoice()
            rememberPicks = true

            val target = base.copy(title = match.name, year = match.year ?: base.year, imdbId = match.imdbId)
            _currentContent.value = target
            _availableTracks.value = emptyList()
            _activeTrack.value = null
            _subtitleIndex.value = null
            clock.setOffset(trackOffsets.switchTo(null, clock.userOffsetMs.value))
            _searchState.value = SearchState.SEARCHING
            searchJob = scope.launch { searchSubtitles(target, remembered = null) }
            return true
        }
    }

    /** Returns false if the arbiter kept the current content. */
    private fun detect(detected: ContentMetadata, source: DetectionSource, rawQuery: String?,
        requireChoice: Boolean = false, catalogCandidates: List<TitleMatch>? = null): Boolean {
        synchronized(detectionLock) {
            if (!arbiter.accept(detected, source, _currentContent.value)) return false

            searchJob?.cancel()
            activationJob?.cancel()
            saveCurrentProgress()

            // Seen before: the remembered pick already knows which film this is, and its track and offset.
            // Unless the year or ID given now says it's a different film of the same name.
            val remembered = picks.forContent(detected.contentKey)?.takeIf { pick ->
                (detected.year == null || pick.content.year == null || pick.content.year == detected.year) &&
                    (detected.imdbId == null || pick.content.imdbId == detected.imdbId)
            }
            val metadata = remembered?.content?.copy(sourcePackage = detected.sourcePackage) ?: detected

            // Never leave the previous title's subtitles (or its sync offset) running over the new one
            _currentContent.value = metadata
            _availableTracks.value = emptyList()
            _activeTrack.value = null
            _subtitleIndex.value = null
            _matches.value = emptyList()
            _searchState.value = SearchState.SEARCHING
            clock.setOffset(trackOffsets.switchTo(null, clock.userOffsetMs.value))
            if (source == DetectionSource.ACCESSIBILITY) {
                DiagLog.d { "New content detected via $source: ${metadata.getDisplayName()}" }
            } else {
                Log.i(TAG, "New content detected via $source: ${metadata.getDisplayName()}")
            }
            rememberPicks = source != DetectionSource.ACCESSIBILITY

            if (remembered != null) {
                _availableTracks.value = listOf(remembered.track)
                activateTrack(remembered.track, remembered.offsetMs)
            }

            val interactive = requireChoice || (source == DetectionSource.MANUAL && rawQuery != null)
            searchJob = scope.launch {
                val target = identify(metadata, interactive, rawQuery, catalogCandidates) ?: return@launch
                searchSubtitles(target, remembered?.track)
            }
            return true
        }
    }

    /**
     * Works out which film [metadata] is (so providers get its IMDb ID and the wrong same-named film is never
     * used). Returns null when the user has to choose, or when a newer detection replaced this one.
     */
    private suspend fun identify(metadata: ContentMetadata, interactive: Boolean, rawQuery: String?,
        catalogCandidates: List<TitleMatch>? = null): ContentMetadata? {
        if (metadata.imdbId != null) return metadata
        val candidates = catalogCandidates ?: resolver.find(metadata)

        synchronized(detectionLock) {
            if (_currentContent.value !== metadata) return null
            _matches.value = TitleMatching.options(metadata, candidates)
            return when (val decision = TitleMatching.decide(metadata, candidates, interactive, rawQuery)) {
                is TitleMatching.Decision.Chosen -> {
                    val match = decision.match
                    metadata.copy(title = match.name, year = match.year ?: metadata.year, imdbId = match.imdbId)
                        .also { _currentContent.value = it }
                }

                is TitleMatching.Decision.Ambiguous -> {
                    _matches.value = decision.options
                    _searchState.value = SearchState.CHOOSE
                    null
                }

                // Unknown to the catalog: providers may still find it by title
                TitleMatching.Decision.NoMatch -> metadata
            }
        }
    }

    private suspend fun searchSubtitles(target: ContentMetadata, remembered: SubtitleTrack?) {
        val tracks = provider.searchAll(target, "en")

        synchronized(detectionLock) {
            // Providers wait on the network, so a newer detection may have replaced this one meanwhile
            if (_currentContent.value !== target) return

            // Uploads made while the search was running aren't in its results
            val merged = (listOfNotNull(remembered) + provider.localTracksFor(target) + tracks).distinctBy { it.id }
            _availableTracks.value = merged
            _searchState.value = if (merged.isEmpty()) SearchState.NOT_FOUND else SearchState.FOUND

            val userAlreadyChose = _activeTrack.value != null || activationJob?.isActive == true
            if (!userAlreadyChose && merged.isNotEmpty()) {
                activateTrack(merged.first())
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
            if (!playing || activationJob?.isActive == true || searchJob?.isActive == true || screenCheckJob?.isActive == true) return
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
        _matches.value = emptyList()
        _searchState.value = SearchState.FOUND
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
                    // Same title still wanted? (Identifying the film swaps in an enriched object for the same title)
                    if (!isActive || _currentContent.value?.contentKey != content?.contentKey) return@launch
                    clock.setOffset(trackOffsets.switchTo(track.id, clock.userOffsetMs.value))
                    _subtitleIndex.value = SubtitleIndex(cues)
                    _activeTrack.value = track
                    // The current object, which by now may carry the identified film's IMDb ID and year
                    val current = _currentContent.value
                    if (current != null && rememberPicks) {
                        picks.remember(current, track, clock.userOffsetMs.value, clock.getPositionMs(), appPackage = null)
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
