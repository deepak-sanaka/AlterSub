package com.altersub.core.session

import android.util.Log
import com.altersub.core.clock.SubtitleClock
import com.altersub.core.clock.TrackOffsets
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleLanguages
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.parser.SrtParser
import com.altersub.core.parser.SubtitleDuration
import com.altersub.core.parser.SubtitleIndex
import com.altersub.detection.DetectionArbiter
import com.altersub.detection.DetectionSource
import com.altersub.detection.DiagLog
import com.altersub.provider.CompositeSubtitleProvider
import com.altersub.provider.TitleResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** Where the latest subtitle search stands, for the phone remote. */
enum class SearchState {
    IDLE,
    SEARCHING,

    /** Several films share the detected title: [SubtitleSession.results] lists each, and the user picks a file. */
    CHOOSE,

    /** The search finished without any subtitles. */
    NOT_FOUND,
    FOUND
}

/**
 * Coordinates what is playing → which film it is → subtitle search → download → active track. Kept free of Android
 * components (AlterSubApp owns one and delegates to it) so the race handling can be unit-tested.
 *
 * Every search publishes [results]: the subtitle files it found in the chosen [language], grouped by title. An
 * automatic detection loads its title's first file straight away; a search typed on the phone (and a detected
 * title that several films share) waits for the user to pick a file with [useResult].
 *
 * Every activated track is remembered in [picks] with its offset and progress: detecting the same title again
 * brings back the same track and offset, and after a restart a streaming app resuming near where the last pick
 * left off brings that pick back (the only way to recognise Netflix content, KI-26).
 */
class SubtitleSession(
    private val provider: CompositeSubtitleProvider,
    private val clock: SubtitleClock,
    private val scope: CoroutineScope,
    private val subtitleDir: File,
    private val picks: PickMemory,
    private val resolver: TitleResolver = TitleResolver.NONE,
    initialLanguage: String = SubtitleLanguages.DEFAULT,
    /** Persists the user's subtitle language. */
    private val onLanguageChanged: (String) -> Unit = {},
    private val onTrackActivated: () -> Unit
) {

    private val _currentContent = MutableStateFlow<ContentMetadata?>(null)
    val currentContent: StateFlow<ContentMetadata?> = _currentContent.asStateFlow()

    /** The current title's files: what [selectTrack] and an upload choose among. */
    private val _availableTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val availableTracks: StateFlow<List<SubtitleTrack>> = _availableTracks.asStateFlow()

    private val _activeTrack = MutableStateFlow<SubtitleTrack?>(null)
    val activeTrack: StateFlow<SubtitleTrack?> = _activeTrack.asStateFlow()

    private val _subtitleIndex = MutableStateFlow<SubtitleIndex?>(null)
    val subtitleIndex: StateFlow<SubtitleIndex?> = _subtitleIndex.asStateFlow()

    private val _results = MutableStateFlow(SearchResults.NONE)
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    /** How long each listed file runs (ms), by track id, or [UNKNOWN_DURATION]; filled in by [onResultsViewed]. */
    private val _durations = MutableStateFlow<Map<String, Long>>(emptyMap())
    val durations: StateFlow<Map<String, Long>> = _durations.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState.IDLE)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    /** The subtitle language (ISO 639-1) every search asks for. */
    private val _language = MutableStateFlow(SubtitleLanguages.byCode(initialLanguage)?.code ?: SubtitleLanguages.DEFAULT)
    val language: StateFlow<String> = _language.asStateFlow()

    // Guards the arbiter, the jobs below, and every content/track state transition.
    // Detections arrive concurrently from the main thread, web server threads and IO coroutines.
    private val detectionLock = Any()
    private val arbiter = DetectionArbiter()
    private val trackOffsets = TrackOffsets()
    private var searchJob: Job? = null
    private var activationJob: Job? = null
    private var screenCheckJob: Job? = null
    private var durationJob: Job? = null

    // What the shown results came from, so a language change can search again: a typed query, or the films a
    // detected title could be
    private var lastQuery: ManualQuery? = null
    private var lastChoice: Pair<ContentMetadata, List<TitleMatch>>? = null

    // Only trustworthy picks are remembered: the user's own choices and media-session titles. Screen-scraped
    // guesses (once, a launcher menu taken for a title) would just fill the recent list with noise.
    private var rememberPicks = false

    val acceptsScreenDetection: Boolean get() = arbiter.acceptsScreenDetection

    fun onContentDetected(metadata: ContentMetadata, source: DetectionSource) {
        detect(metadata, source)
    }

    /**
     * A title read off a streaming app's screen. Screen text is often UI (a page header, a menu), so it is taken
     * only once the catalog knows a film or series by exactly that name. That's checked before anything is
     * replaced, so text that fails the check leaves the current subtitles alone. With [requireChoice], a name
     * several films share isn't guessed: each film's files are listed for the user to pick from.
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
                    detect(verified, DetectionSource.ACCESSIBILITY, choices = if (ambiguous) exact else null)
                }
            }
        }
    }

    /**
     * A search typed on the phone, optionally with a year ("Under the Open Sky 2020"). Every film it could mean
     * is searched, and their files are listed in [results] for the user to pick from; nothing changes on the TV
     * until they do.
     */
    fun searchByText(query: String) {
        val parsed = ManualQuery.parse(query)
        if (parsed.title.isBlank()) return
        synchronized(detectionLock) { startTextSearch(parsed) }
    }

    // Caller must hold detectionLock
    private fun startTextSearch(query: ManualQuery) {
        searchJob?.cancel()
        durationJob?.cancel()
        lastQuery = query
        lastChoice = null
        val language = _language.value
        _results.value = SearchResults(query.raw, language, emptyList(), manual = true)
        _searchState.value = SearchState.SEARCHING
        searchJob = scope.launch { findByText(query, language) }
    }

    private suspend fun findByText(query: ManualQuery, language: String) {
        val job = coroutineContext.job
        val base = ContentMetadata(title = query.title, year = query.year)
        val candidates = resolver.find(base)
        val titles = when (val decision = TitleMatching.decide(base, candidates, interactive = true, rawQuery = query.raw)) {
            is TitleMatching.Decision.Chosen -> listOf(decision.match)
            is TitleMatching.Decision.Ambiguous -> decision.options.take(MAX_GROUPS)
            TitleMatching.Decision.NoMatch -> emptyList()
        }
        val groups = if (titles.isEmpty()) {
            // Unknown to the catalog: the sources may still know it by name
            listOf(TitleGroup(base, null, provider.searchAll(base, language), lastUsedFor(base)))
        } else {
            groupsFor(base, titles, language)
        }.filter { it.tracks.isNotEmpty() }

        synchronized(detectionLock) {
            // A newer search cancelled this one while it waited on the network
            if (!job.isActive) return
            publish(SearchResults(query.raw, language, groups, manual = true), if (groups.isEmpty()) SearchState.NOT_FOUND else SearchState.FOUND)
        }
    }

    private suspend fun groupsFor(base: ContentMetadata, titles: List<TitleMatch>, language: String): List<TitleGroup> =
        coroutineScope {
            titles.map { match ->
                async {
                    val content = base.copy(title = match.name, year = match.year ?: base.year, imdbId = match.imdbId)
                    val details = async { resolver.details(match) }
                    val tracks = provider.searchAll(content, language)
                    TitleGroup(content, details.await(), tracks, lastUsedFor(content))
                }
            }.awaitAll()
        }

    /**
     * The user picked a file from [results]. Its title becomes what's playing (if it wasn't already), and the file
     * is shown. Returns false if the file isn't listed.
     */
    fun useResult(trackId: String): Boolean {
        synchronized(detectionLock) {
            val group = _results.value.groups.firstOrNull { g -> g.tracks.any { it.id == trackId } } ?: return false
            val track = group.tracks.first { it.id == trackId }
            val current = _currentContent.value
            val sameTitle = current != null && current.contentKey == group.content.contentKey &&
                (current.imdbId == null || current.imdbId == group.content.imdbId)
            if (sameTitle) {
                arbiter.onUserChoice()
                // The group may know more (the catalog's IMDb ID) than a detection did
                if (current?.imdbId == null) _currentContent.value = group.content
            } else {
                searchJob?.cancel()
                activationJob?.cancel()
                saveCurrentProgress()
                arbiter.accept(group.content, DetectionSource.MANUAL, current)
                _currentContent.value = group.content
                _activeTrack.value = null
                _subtitleIndex.value = null
                clock.setOffset(trackOffsets.switchTo(null, clock.userOffsetMs.value))
            }
            _availableTracks.value = group.tracks
            if (_searchState.value == SearchState.CHOOSE) _searchState.value = SearchState.FOUND
            // Picked: a language change now switches this title's file, instead of repeating the search
            lastQuery = null
            lastChoice = null
            _results.value = _results.value.copy(manual = false)
            rememberPicks = true
            val remembered = picks.forContent(group.content.contentKey)?.takeIf { it.track.id == trackId }
            activateTrack(track, remembered?.offsetMs)
            return true
        }
    }

    /**
     * The phone is showing [results]: read how long each file runs. That means downloading it, which also makes
     * picking it instant. A few at a time, and only once per file.
     */
    fun onResultsViewed() {
        synchronized(detectionLock) {
            if (durationJob?.isActive == true) return
            val known = _durations.value
            val pending = _results.value.groups.flatMap { it.tracks }.filter { it.id !in known }.take(MAX_MEASURED)
            if (pending.isEmpty()) return
            durationJob = scope.launch {
                val permits = Semaphore(MEASURE_PARALLELISM)
                coroutineScope {
                    for (track in pending) launch { permits.withPermit { measure(track) } }
                }
            }
        }
    }

    private suspend fun measure(track: SubtitleTrack) {
        val durationMs = try {
            provider.downloadTrack(track, subtitleDir)?.let { SubtitleDuration.of(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        synchronized(detectionLock) {
            val updated = _durations.value + (track.id to (durationMs ?: UNKNOWN_DURATION))
            // Keep the map small: only the latest few searches' files matter
            _durations.value = if (updated.size > MAX_REMEMBERED_DURATIONS) updated.entries.drop(updated.size - MAX_REMEMBERED_DURATIONS)
                .associate { it.key to it.value } else updated
        }
    }

    /**
     * The user picked a subtitle language on the phone. It's kept for later searches, and the results showing now
     * are searched again in it (a detected title also switches to its first file in the new language).
     */
    fun setLanguage(code: String): Boolean {
        val language = SubtitleLanguages.byCode(code) ?: return false
        synchronized(detectionLock) {
            if (language.code == _language.value) return true
            _language.value = language.code
            onLanguageChanged(language.code)

            val query = lastQuery
            val choice = lastChoice
            val content = _currentContent.value
            when {
                _results.value.manual && query != null -> startTextSearch(query)
                choice != null && _searchState.value == SearchState.CHOOSE -> startChoice(choice.first, choice.second)
                content != null -> {
                    searchJob?.cancel()
                    durationJob?.cancel()
                    _searchState.value = SearchState.SEARCHING
                    searchJob = scope.launch { searchSubtitles(content, remembered = null, language.code, languageChanged = true) }
                }
            }
            return true
        }
    }

    /** Returns false if the arbiter kept the current content. [choices]: films sharing the title, to pick from. */
    private fun detect(detected: ContentMetadata, source: DetectionSource, choices: List<TitleMatch>? = null): Boolean {
        synchronized(detectionLock) {
            if (!arbiter.accept(detected, source, _currentContent.value)) return false

            searchJob?.cancel()
            activationJob?.cancel()
            durationJob?.cancel()
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
            _results.value = SearchResults.NONE
            lastQuery = null
            lastChoice = null
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
            } else if (choices != null) {
                startChoice(metadata, choices)
                return true
            }

            val language = _language.value
            searchJob = scope.launch {
                val target = identify(metadata) ?: return@launch
                searchSubtitles(target, remembered?.track, language)
            }
            return true
        }
    }

    // Caller must hold detectionLock. Lists every film [base] could be, with its files, for the user to pick from.
    private fun startChoice(base: ContentMetadata, options: List<TitleMatch>) {
        lastChoice = base to options
        val language = _language.value
        _searchState.value = SearchState.SEARCHING
        searchJob = scope.launch {
            val groups = groupsFor(base, options.take(MAX_GROUPS), language).filter { it.tracks.isNotEmpty() }
            synchronized(detectionLock) {
                if (!isActive || _currentContent.value !== base) return@launch
                publish(SearchResults(base.title, language, groups, manual = false), if (groups.isEmpty()) SearchState.NOT_FOUND else SearchState.CHOOSE)
            }
        }
    }

    /**
     * Works out which film [metadata] is (so providers get its IMDb ID and the wrong same-named film is never
     * used), taking the catalog's best guess. Returns null when a newer detection replaced this one.
     */
    private suspend fun identify(metadata: ContentMetadata): ContentMetadata? {
        if (metadata.imdbId != null) return metadata
        val candidates = resolver.find(metadata)

        synchronized(detectionLock) {
            if (_currentContent.value !== metadata) return null
            return when (val decision = TitleMatching.decide(metadata, candidates, interactive = false)) {
                is TitleMatching.Decision.Chosen -> {
                    val match = decision.match
                    metadata.copy(title = match.name, year = match.year ?: metadata.year, imdbId = match.imdbId)
                        .also { _currentContent.value = it }
                }
                // Unknown to the catalog: providers may still find it by title
                else -> metadata
            }
        }
    }

    /**
     * Searches [target]'s files and lists them as one group. The first file is shown unless the user already chose
     * one, or (after [languageChanged]) the one showing is already in the new language.
     */
    private suspend fun searchSubtitles(target: ContentMetadata, remembered: SubtitleTrack?, language: String,
        languageChanged: Boolean = false) {
        val job = coroutineContext.job
        val match = target.imdbId?.let { TitleMatch(it, target.title, target.year, if (target.isEpisode) "series" else "movie") }
        val (tracks, details) = coroutineScope {
            val details = async { match?.let { resolver.details(it) } }
            provider.searchAll(target, language) to details.await()
        }

        synchronized(detectionLock) {
            // Providers wait on the network, so a newer detection may have replaced this one meanwhile
            if (_currentContent.value !== target || !job.isActive) return

            // Uploads made while the search was running aren't in its results
            val merged = (listOfNotNull(remembered) + provider.localTracksFor(target) + tracks).distinctBy { it.id }
            _availableTracks.value = merged
            publish(
                SearchResults(target.getDisplayName(), language, listOf(TitleGroup(target, details, merged, lastUsedFor(target))), manual = false),
                if (merged.isEmpty()) SearchState.NOT_FOUND else SearchState.FOUND
            )

            val active = _activeTrack.value
            val userAlreadyChose = active != null || activationJob?.isActive == true
            val inOtherLanguage = languageChanged && active != null && !SubtitleLanguages.matches(active.language, language)
            val first = merged.firstOrNull { SubtitleLanguages.matches(it.language, language) } ?: merged.firstOrNull()
            if ((!userAlreadyChose || inOtherLanguage) && first != null && first != active) {
                activateTrack(first)
            }
        }
    }

    // Caller must hold detectionLock
    private fun publish(results: SearchResults, state: SearchState) {
        durationJob?.cancel()
        _results.value = results
        _searchState.value = state
    }

    private fun lastUsedFor(content: ContentMetadata): String? = picks.forContent(content.contentKey)?.track?.id

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
        durationJob?.cancel()
        saveCurrentProgress()
        // Behaves like the user's own choice: screen scraping must not replace it
        arbiter.onUserChoice()
        rememberPicks = true
        lastQuery = null
        lastChoice = null
        _currentContent.value = pick.content
        _availableTracks.value = listOf(pick.track)
        _activeTrack.value = null
        _subtitleIndex.value = null
        _results.value = SearchResults(pick.content.getDisplayName(), _language.value,
            listOf(TitleGroup(pick.content, null, listOf(pick.track), pick.track.id)), manual = false)
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

    /** Shows one of the current title's files (see [availableTracks]). */
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

    companion object {
        private const val TAG = "AlterSubApp"

        /** A listed file whose length couldn't be read (the download or the file failed). */
        const val UNKNOWN_DURATION = -1L

        /** How close a resumed position must be to the remembered one to count as the same title. */
        private const val RESUME_WINDOW_MS = 5 * 60_000L
        private const val RECENT_LIMIT = 5

        /** Films searched for one query: a name shared by more than this is narrowed with a year. */
        private const val MAX_GROUPS = 3

        // Durations: each needs a download, so a handful at a time and a bounded number per search
        private const val MAX_MEASURED = 15
        private const val MEASURE_PARALLELISM = 3
        private const val MAX_REMEMBERED_DURATIONS = 200
    }
}
