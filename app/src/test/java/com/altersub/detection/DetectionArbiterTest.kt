package com.altersub.detection

import com.altersub.core.model.ContentMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionArbiterTest {

    private val inception = ContentMetadata(title = "Inception")
    private val trending = ContentMetadata(title = "Trending Now")
    private val strangerS4E1 = ContentMetadata(title = "Stranger Things", season = 4, episode = 1)
    private val strangerS4E2 = ContentMetadata(title = "Stranger Things", season = 4, episode = 2)

    @Test
    fun testAccessibilityDetectsWhenNothingElseKnown() {
        val arbiter = DetectionArbiter()
        assertTrue(arbiter.accept(inception, DetectionSource.ACCESSIBILITY, null))
        assertFalse(arbiter.accept(ContentMetadata(title = "INCEPTION"), DetectionSource.ACCESSIBILITY, inception))
    }

    @Test
    fun testMediaSessionOutranksAccessibility() {
        val arbiter = DetectionArbiter()
        assertTrue(arbiter.accept(trending, DetectionSource.ACCESSIBILITY, null))
        assertTrue(arbiter.accept(inception, DetectionSource.MEDIA_SESSION, trending))

        // Scraped UI text can no longer replace the session's title
        assertFalse(arbiter.acceptsScreenDetection)
        assertFalse(arbiter.accept(trending, DetectionSource.ACCESSIBILITY, inception))
    }

    @Test
    fun testMediaSessionConfirmingScrapedTitleDoesNotResearch() {
        val arbiter = DetectionArbiter()
        assertTrue(arbiter.accept(inception, DetectionSource.ACCESSIBILITY, null))
        assertFalse(arbiter.accept(inception, DetectionSource.MEDIA_SESSION, inception))
        assertFalse(arbiter.acceptsScreenDetection)
    }

    @Test
    fun testManualSearchHoldsUntilSessionTitleChanges() {
        val arbiter = DetectionArbiter()
        assertTrue(arbiter.accept(strangerS4E1, DetectionSource.MEDIA_SESSION, null))
        assertTrue(arbiter.accept(inception, DetectionSource.MANUAL, strangerS4E1))

        // Reconnects re-report the same session metadata; that must not undo the user's search
        assertFalse(arbiter.accept(strangerS4E1, DetectionSource.MEDIA_SESSION, inception))

        // Autoplay to the next episode is a real change and takes over again
        assertTrue(arbiter.accept(strangerS4E2, DetectionSource.MEDIA_SESSION, inception))
    }

    @Test
    fun testUserChoiceBlocksScreenScraping() {
        val arbiter = DetectionArbiter()
        assertTrue(arbiter.accept(inception, DetectionSource.ACCESSIBILITY, null))
        arbiter.onUserChoice()

        assertFalse(arbiter.acceptsScreenDetection)
        assertFalse(arbiter.accept(trending, DetectionSource.ACCESSIBILITY, inception))
    }

    @Test
    fun testScreenScrapingResumesAfterSessionsEnd() {
        val arbiter = DetectionArbiter()
        assertTrue(arbiter.accept(inception, DetectionSource.MEDIA_SESSION, null))
        arbiter.onUserChoice() // Picking a track during a session doesn't make it a manual override

        arbiter.onMediaSessionsEnded()

        assertTrue(arbiter.acceptsScreenDetection)
        assertTrue(arbiter.accept(strangerS4E1, DetectionSource.ACCESSIBILITY, inception))
    }
}
