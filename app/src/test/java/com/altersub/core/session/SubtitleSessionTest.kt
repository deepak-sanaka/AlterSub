package com.altersub.core.session

import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import com.altersub.detection.DetectionSource
import com.altersub.provider.CompositeSubtitleProvider
import com.altersub.provider.SubtitleProvider
import com.altersub.provider.TitleResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleSessionTest {

    @Test fun `a spoken title two films share lists both, and waits for the user to pick a file`() = runTest {
        catalog["under the open sky"] = listOf(sky2025, sky2020)
        val session = newSession()
        session.onScreenTitle(ContentMetadata("Under the Open Sky"), requireChoice = true)
        advanceUntilIdle()
        fake.respond(sky2025.imdbId, "sky2025-eng")
        fake.respond(sky2020.imdbId, "sky2020-eng")
        advanceUntilIdle()

        assertEquals(SearchState.CHOOSE, session.searchState.value)
        assertNull(session.currentContent.value?.imdbId)
        assertEquals(listOf(2025, 2020), session.results.value.groups.map { it.content.year })
        assertNull(session.activeTrack.value) // Nothing is guessed

        assertTrue(session.useResult("sky2020-eng"))
        advanceUntilIdle()
        assertEquals("Under the Open Sky (2020)", session.currentContent.value?.getDisplayName())
        assertEquals("sky2020-eng", session.shownText())
        assertEquals(SearchState.FOUND, session.searchState.value)
    }

    @Test fun `stale spoken title cannot finish catalog verification`() = runTest {
        val answer = CompletableDeferred<List<TitleMatch>>()
        val session = SubtitleSession(CompositeSubtitleProvider(listOf(fake)), clock, this,
            File(tempDir.root, "stale-speech"), PickMemory(pickStore), TitleResolver { answer.await() }) {}
        var current = true
        session.onScreenTitle(ContentMetadata("Inception"), requireChoice = true) { current }
        advanceUntilIdle() // The catalog lookup is now suspended in flight.
        current = false
        answer.complete(listOf(TitleMatch("tt1375666", "Inception", 2010)))
        advanceUntilIdle()
        assertNull(session.currentContent.value)
        assertTrue(fake.searched.isEmpty())
    }

    @get:Rule
    val tempDir = TemporaryFolder()

    /**
     * Search results are held back until the test releases them, so stale-result races are reproducible. Searches
     * are answered by film (IMDb ID, or the title when there's none) and language.
     */
    private class FakeProvider : SubtitleProvider {
        override val name = "Fake"
        override val isEnabled = true
        private val pendingSearches = HashMap<String, CompletableDeferred<List<SubtitleTrack>>>()

        fun respond(film: String, vararg trackIds: String, language: String = "en") {
            pending(film, language).complete(trackIds.map { track(it, language) })
        }

        fun track(id: String, language: String = "en") =
            SubtitleTrack(id = id, title = id, language = language, source = name, downloadUrl = id)

        private fun pending(film: String, language: String) = synchronized(pendingSearches) {
            pendingSearches.getOrPut("$film|$language") { CompletableDeferred() }
        }

        val searched = mutableListOf<ContentMetadata>()
        val languages = mutableListOf<String>()

        override suspend fun search(metadata: ContentMetadata, language: String): List<SubtitleTrack> {
            synchronized(searched) {
                searched += metadata
                languages += language
            }
            return pending(metadata.imdbId ?: metadata.title, language).await()
        }

        // Each track's single cue shows its own id, so the active index is easy to identify; "broken" ones aren't subtitles
        override suspend fun download(track: SubtitleTrack, targetDir: File): File {
            targetDir.mkdirs()
            return File(targetDir, "${track.id}.srt").apply {
                writeText(if (track.id.startsWith("broken")) "not a subtitle file" else "1\n00:00:01,000 --> 00:00:02,000\n${track.id}\n")
            }
        }
    }

    private val fake = FakeProvider()
    private val clock = SubtitleClock()
    private var overlayStarts = 0

    // Shared by every session a test creates, like SharedPreferences across app restarts
    private val pickStore = object : PickMemory.Store {
        var json: String? = null
        override fun read() = json
        override fun write(json: String) {
            this.json = json
        }
    }

    // Catalog answers by title; empty unless a test sets them (then sessions behave as before title matching)
    private val catalog = HashMap<String, List<TitleMatch>>()
    private var catalogLookups = 0
    private val resolver = TitleResolver { metadata ->
        catalogLookups++
        catalog[metadata.title.lowercase()].orEmpty()
    }

    private val sky2025 = TitleMatch("tt32543911", "Under the Open Sky", 2025)
    private val sky2020 = TitleMatch("tt12801374", "Under the Open Sky", 2020)

    private fun TestScope.newSession(
        clock: SubtitleClock = this@SubtitleSessionTest.clock,
        language: String = "en",
        onLanguageChanged: (String) -> Unit = {}
    ) = SubtitleSession(
        provider = CompositeSubtitleProvider(listOf(fake)),
        clock = clock,
        // Runs on the test scheduler; each test answers every search it starts so nothing is left pending
        scope = this,
        subtitleDir = File(tempDir.root, "subtitles"),
        picks = PickMemory(pickStore),
        resolver = resolver,
        initialLanguage = language,
        onLanguageChanged = onLanguageChanged,
        onTrackActivated = { overlayStarts++ }
    )

    private fun SubtitleSession.shownText() = subtitleIndex.value?.getTextAt(1500L)

    private fun upload(name: String) = tempDir.newFile("$name.srt").apply {
        writeText("1\n00:00:01,000 --> 00:00:02,000\n$name\n")
    }

    @Test
    fun testDetectionSearchesAndActivatesTheFirstTrack() = runTest {
        val session = newSession()

        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Inception", "inception-1", "inception-2")
        advanceUntilIdle()

        assertEquals(listOf("inception-1", "inception-2"), session.availableTracks.value.map { it.id })
        assertEquals("inception-1", session.activeTrack.value?.id)
        assertEquals("inception-1", session.shownText())
        assertEquals(1, overlayStarts)
        // The phone can still pick another file: the detected title is listed as one group
        assertEquals(listOf("Inception"), session.results.value.groups.map { it.content.title })
        assertFalse(session.results.value.manual)
    }

    @Test
    fun testNewContentClearsOldSubtitlesAndDropsStaleResults() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Inception", "inception-1")
        advanceUntilIdle()

        // A new title clears the old subtitles at once, before its own search returns
        session.onContentDetected(ContentMetadata(title = "Interstellar"), DetectionSource.MANUAL)
        assertNull(session.activeTrack.value)
        assertNull(session.subtitleIndex.value)
        assertTrue(session.availableTracks.value.isEmpty())

        // And a third title arrives while Interstellar's search is still running...
        advanceUntilIdle()
        session.onContentDetected(ContentMetadata(title = "Tenet"), DetectionSource.MANUAL)
        advanceUntilIdle()

        // ...so Interstellar's late results must never show up
        fake.respond("Interstellar", "interstellar-1")
        advanceUntilIdle()
        assertTrue(session.availableTracks.value.isEmpty())

        fake.respond("Tenet", "tenet-1")
        advanceUntilIdle()
        assertEquals("Tenet", session.currentContent.value?.title)
        assertEquals("tenet-1", session.activeTrack.value?.id)
        assertEquals("tenet-1", session.shownText())
    }

    @Test
    fun testUserChoiceIsNotOverriddenByALateAutoPick() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        advanceUntilIdle()

        // The user uploads their own file while the search is still running
        session.loadDirectSrt(upload("my-inception"), "My Inception")
        advanceUntilIdle()
        assertEquals("my-inception", session.shownText())

        fake.respond("Inception", "inception-1")
        advanceUntilIdle()

        assertEquals(listOf("My Inception [Phone Upload]", "inception-1"), session.availableTracks.value.map { it.title })
        assertEquals("my-inception", session.shownText())
    }

    @Test
    fun testUploadsStayWithTheirOwnTitle() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        session.loadDirectSrt(upload("my-inception"), "My Inception")
        advanceUntilIdle()

        session.onContentDetected(ContentMetadata(title = "Interstellar"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Interstellar", "interstellar-1")
        advanceUntilIdle()

        assertEquals(listOf("interstellar-1"), session.availableTracks.value.map { it.id })
        assertEquals("interstellar-1", session.shownText())
    }

    @Test
    fun testSyncOffsetIsRememberedPerTrack() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Inception", "inception-1")
        advanceUntilIdle()
        clock.adjustOffset(750L)

        session.onContentDetected(ContentMetadata(title = "Interstellar"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Interstellar", "interstellar-1")
        advanceUntilIdle()
        assertEquals(0L, clock.userOffsetMs.value)

        // Searching Inception again re-activates the same track, which brings its offset back
        session.onContentDetected(ContentMetadata(title = "Inception", year = 2010), DetectionSource.MANUAL)
        advanceUntilIdle()
        assertEquals("inception-1", session.activeTrack.value?.id)
        assertEquals(750L, clock.userOffsetMs.value)
    }

    @Test
    fun testMediaSessionEndingPausesClockAndReenablesScraping() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MEDIA_SESSION)
        assertFalse(session.acceptsScreenDetection)
        clock.play()

        session.onMediaSessionsEnded()

        assertFalse(clock.isPlaying.value)
        assertTrue(session.acceptsScreenDetection)

        fake.respond("Inception")
        advanceUntilIdle()
    }

    @Test
    fun testATitleSeenBeforeGetsItsTrackAndOffsetBackAfterARestart() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Inception", "inception-1", "inception-2")
        advanceUntilIdle()
        session.selectTrack(fake.track("inception-2"))
        advanceUntilIdle()
        clock.adjustOffset(-10_750L)
        session.onSyncAdjusted()

        // A new process: fresh clock and session, same saved picks
        val restartedClock = SubtitleClock()
        val restarted = newSession(restartedClock)
        restarted.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        advanceUntilIdle()

        // The remembered track wins over the search's first hit, with its offset
        assertEquals("inception-2", restarted.activeTrack.value?.id)
        assertEquals("inception-2", restarted.shownText())
        assertEquals(-10_750L, restartedClock.userOffsetMs.value)
    }

    @Test
    fun testResumingTheSameAppNearTheSavedPositionRestoresThePick() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Under a Sky"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Under a Sky", "sky-1")
        advanceUntilIdle()
        clock.adjustOffset(-2_000L)
        // Netflix reports no title, only playback: the pick learns which app it plays in, and how far it got
        session.onPlaybackObserved("com.netflix.ninja", positionMs = 1_200_000L, playing = true)

        val restartedClock = SubtitleClock()
        val restarted = newSession(restartedClock)
        restarted.onPlaybackObserved("com.netflix.ninja", positionMs = 1_260_000L, playing = true)
        advanceUntilIdle()

        assertEquals("Under a Sky", restarted.currentContent.value?.title)
        assertEquals("sky-1", restarted.activeTrack.value?.id)
        assertEquals(-2_000L, restartedClock.userOffsetMs.value)
    }

    @Test
    fun testAFarAwayPositionOrAnotherAppIsNotTakenForThePick() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Under a Sky"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Under a Sky", "sky-1")
        advanceUntilIdle()
        session.onPlaybackObserved("com.netflix.ninja", positionMs = 1_200_000L, playing = true)

        val restarted = newSession(SubtitleClock())
        // Probably a different film: playback started from the beginning
        restarted.onPlaybackObserved("com.netflix.ninja", positionMs = 4_000L, playing = true)
        // A different app altogether
        restarted.onPlaybackObserved("com.amazon.amazonvideo.livingroom", positionMs = 1_200_000L, playing = true)
        advanceUntilIdle()

        assertNull(restarted.currentContent.value)
        assertNull(restarted.activeTrack.value)
    }

    @Test
    fun testScreenScrapedGuessesAreNotRemembered() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Context Menu"), DetectionSource.ACCESSIBILITY)
        advanceUntilIdle()
        fake.respond("Context Menu", "junk-1")
        advanceUntilIdle()

        assertEquals("junk-1", session.activeTrack.value?.id) // Still plays: it may be right
        assertTrue(session.recentPicks().isEmpty())           // But it isn't offered again later
    }

    @Test
    fun testAScreenTitleIsTakenOnlyWhenTheCatalogKnowsIt() = runTest {
        val inception = TitleMatch("tt1375666", "Inception", 2010)
        catalog["inception"] = listOf(inception)
        catalog["vertical video grid"] = listOf(TitleMatch("tt0326900", "The Grid", 2004)) // Loose matches only
        val session = newSession()

        session.onScreenTitle(ContentMetadata(title = "INCEPTION"))
        advanceUntilIdle()
        fake.respond(inception.imdbId, "inception-1")
        advanceUntilIdle()

        assertEquals("tt1375666", fake.searched.single().imdbId)
        assertEquals("inception-1", session.shownText())
        assertEquals("tt1375666", session.results.value.groups.single().content.imdbId)

        // A page header read off the screen next isn't a film, so it doesn't replace the subtitles
        session.onScreenTitle(ContentMetadata(title = "Vertical Video Grid"))
        advanceUntilIdle()

        assertEquals("Inception (2010)", session.currentContent.value?.getDisplayName())
        assertEquals("inception-1", session.shownText())
        assertEquals(1, fake.searched.size)
    }

    @Test
    fun testScreenTitlesAreNotLookedUpWhileTheUserHasChosen() = runTest {
        catalog["dark"] = listOf(TitleMatch("tt5753856", "Dark", 2017, "series"))
        val session = newSession()
        session.searchByText("Inception")
        advanceUntilIdle()
        fake.respond("Inception", "inception-1")
        advanceUntilIdle()
        assertTrue(session.useResult("inception-1"))
        advanceUntilIdle()
        val lookups = catalogLookups

        session.onScreenTitle(ContentMetadata(title = "Dark"))
        advanceUntilIdle()

        assertEquals(lookups, catalogLookups)
        assertEquals("Inception", session.currentContent.value?.title)
    }

    @Test
    fun testRecentPicksCanBeRestoredInOneTap() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Inception", "inception-1")
        advanceUntilIdle()
        session.onContentDetected(ContentMetadata(title = "Interstellar"), DetectionSource.MANUAL)
        advanceUntilIdle()
        fake.respond("Interstellar", "interstellar-1")
        advanceUntilIdle()

        assertEquals(listOf("Interstellar", "Inception"), session.recentPicks().map { it.content.title })
        assertTrue(session.restorePick(session.recentPicks()[1].key))
        advanceUntilIdle()

        assertEquals("Inception", session.currentContent.value?.title)
        assertEquals("inception-1", session.shownText())
        assertFalse(session.restorePick("never seen||"))
    }

    @Test
    fun testATypedSearchListsEachFilmsFilesAndWaitsForAPick() = runTest {
        catalog["under the open sky"] = listOf(sky2025, sky2020)
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MEDIA_SESSION)
        advanceUntilIdle()
        fake.respond("Inception", "inception-1")
        advanceUntilIdle()

        session.searchByText("Under the open sky")
        advanceUntilIdle()
        assertEquals(SearchState.SEARCHING, session.searchState.value)
        fake.respond(sky2025.imdbId) // No files for the 2025 film
        fake.respond(sky2020.imdbId, "sky2020-a", "sky2020-b")
        advanceUntilIdle()

        // Only films with files are listed, each with its own; the TV keeps showing what it was
        val results = session.results.value
        assertTrue(results.manual)
        assertEquals("Under the open sky", results.query)
        assertEquals(listOf("tt12801374"), results.groups.map { it.content.imdbId })
        assertEquals(listOf("sky2020-a", "sky2020-b"), results.groups.single().tracks.map { it.id })
        assertEquals(SearchState.FOUND, session.searchState.value)
        assertEquals("inception-1", session.shownText())

        assertTrue(session.useResult("sky2020-b"))
        advanceUntilIdle()
        assertEquals("Under the Open Sky (2020)", session.currentContent.value?.getDisplayName())
        assertEquals("sky2020-b", session.shownText())
        assertFalse(session.useResult("not-listed"))
    }

    @Test
    fun testAYearNarrowsTheSearchToThatFilm() = runTest {
        catalog["under the open sky"] = listOf(sky2025, sky2020)
        val session = newSession()

        session.searchByText("Under the open sky 2020")
        advanceUntilIdle()
        fake.respond(sky2020.imdbId, "sky2020-eng")
        advanceUntilIdle()

        assertEquals(listOf("tt12801374"), fake.searched.map { it.imdbId })
        assertEquals(listOf("sky2020-eng"), session.results.value.groups.single().tracks.map { it.id })
        assertNull(session.activeTrack.value)
    }

    @Test
    fun testATitleTheCatalogDoesntKnowIsStillSearchedByName() = runTest {
        val session = newSession()
        session.searchByText("Our Wedding Video")
        advanceUntilIdle()
        fake.respond("Our Wedding Video", "wedding-1")
        advanceUntilIdle()

        val group = session.results.value.groups.single()
        assertEquals("Our Wedding Video", group.content.title)
        assertNull(group.content.imdbId)
    }

    @Test
    fun testASearchWithoutSubtitlesIsReported() = runTest {
        catalog["inception"] = listOf(TitleMatch("tt1375666", "Inception", 2010))
        val session = newSession()

        session.searchByText("inception")
        advanceUntilIdle()
        fake.respond("tt1375666")
        advanceUntilIdle()

        assertEquals(SearchState.NOT_FOUND, session.searchState.value)
        assertTrue(session.results.value.groups.isEmpty())
    }

    @Test
    fun testAFileUsedBeforeIsMarkedAndBringsBackItsOffset() = runTest {
        catalog["under the open sky"] = listOf(sky2025, sky2020)
        val first = newSession()
        first.searchByText("Under the open sky 2020")
        advanceUntilIdle()
        fake.respond(sky2020.imdbId, "sky2020-eng", "sky2020-other")
        advanceUntilIdle()
        first.useResult("sky2020-eng")
        advanceUntilIdle()
        clock.adjustOffset(-1_500L)
        first.onSyncAdjusted()

        val restartedClock = SubtitleClock()
        val restarted = newSession(restartedClock)
        restarted.searchByText("under the open sky 2020")
        advanceUntilIdle()

        assertEquals("sky2020-eng", restarted.results.value.groups.single().lastUsedTrackId)
        restarted.useResult("sky2020-eng")
        advanceUntilIdle()
        assertEquals(-1_500L, restartedClock.userOffsetMs.value)
    }

    @Test
    fun testSearchesAskForTheChosenLanguageWhichIsKept() = runTest {
        var saved: String? = null
        val session = newSession(language = "es", onLanguageChanged = { saved = it })

        session.searchByText("Inception")
        advanceUntilIdle()
        fake.respond("Inception", "inception-es", language = "es")
        advanceUntilIdle()
        assertEquals(listOf("es"), fake.languages)
        assertEquals("es", session.results.value.language)

        // A new language is kept, and the same search runs again in it
        assertTrue(session.setLanguage("fr"))
        advanceUntilIdle()
        fake.respond("Inception", "inception-fr", language = "fr")
        advanceUntilIdle()
        assertEquals("fr", saved)
        assertEquals("fr", session.language.value)
        assertEquals(listOf("inception-fr"), session.results.value.groups.single().tracks.map { it.id })
        assertFalse(session.setLanguage("xx"))
    }

    @Test
    fun testALanguageChangeSwitchesADetectedTitleToAFileInThatLanguage() = runTest {
        val session = newSession()
        session.onContentDetected(ContentMetadata(title = "Inception"), DetectionSource.MEDIA_SESSION)
        advanceUntilIdle()
        fake.respond("Inception", "inception-en")
        advanceUntilIdle()
        assertEquals("inception-en", session.shownText())

        session.setLanguage("es")
        advanceUntilIdle()
        fake.respond("Inception", "inception-es", language = "es")
        advanceUntilIdle()
        assertEquals("inception-es", session.shownText())
    }

    @Test
    fun testFilesAreMeasuredOnlyOnceThePhoneShowsThem() = runTest {
        val session = newSession()
        session.searchByText("Inception")
        advanceUntilIdle()
        fake.respond("Inception", "inception-1", "broken-1")
        advanceUntilIdle()
        assertTrue(session.durations.value.isEmpty()) // Nothing downloaded until the phone looks

        session.onResultsViewed()
        advanceUntilIdle()
        assertEquals(mapOf("inception-1" to 2_000L, "broken-1" to SubtitleSession.UNKNOWN_DURATION), session.durations.value)
    }

}
