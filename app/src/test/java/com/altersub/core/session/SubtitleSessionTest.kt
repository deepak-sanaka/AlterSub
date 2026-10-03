package com.altersub.core.session

import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import com.altersub.detection.DetectionSource
import com.altersub.provider.CompositeSubtitleProvider
import com.altersub.provider.SubtitleProvider
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

    @get:Rule
    val tempDir = TemporaryFolder()

    /** Search results are held back until the test releases them, so stale-result races are reproducible. */
    private class FakeProvider : SubtitleProvider {
        override val name = "Fake"
        override val isEnabled = true
        private val pendingSearches = HashMap<String, CompletableDeferred<List<SubtitleTrack>>>()

        fun respond(title: String, vararg trackIds: String) {
            pending(title).complete(trackIds.map { track(it) })
        }

        fun track(id: String) = SubtitleTrack(id = id, title = id, language = "en", source = name, downloadUrl = id)

        private fun pending(title: String) = synchronized(pendingSearches) {
            pendingSearches.getOrPut(title) { CompletableDeferred() }
        }

        override suspend fun search(metadata: ContentMetadata, language: String) = pending(metadata.title).await()

        // Each track's single cue shows its own id, so the active index is easy to identify
        override suspend fun download(track: SubtitleTrack, targetDir: File): File {
            targetDir.mkdirs()
            return File(targetDir, "${track.id}.srt").apply {
                writeText("1\n00:00:01,000 --> 00:00:02,000\n${track.id}\n")
            }
        }
    }

    private val fake = FakeProvider()
    private val clock = SubtitleClock()
    private var overlayStarts = 0

    private fun TestScope.newSession() = SubtitleSession(
        provider = CompositeSubtitleProvider(listOf(fake)),
        clock = clock,
        // Runs on the test scheduler; each test answers every search it starts so nothing is left pending
        scope = this,
        subtitleDir = File(tempDir.root, "subtitles"),
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
}
