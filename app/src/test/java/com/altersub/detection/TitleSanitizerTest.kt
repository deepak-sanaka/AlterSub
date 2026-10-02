package com.altersub.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSanitizerTest {

    @Test
    fun testSeriesSanitization() {
        val meta = TitleSanitizer.sanitize("Stranger Things S04E01 Chapter One", "com.netflix.ninja")
        assertNotNull(meta)
        assertEquals("Stranger Things", meta?.title)
        assertEquals(4, meta?.season)
        assertEquals(1, meta?.episode)
        assertTrue(meta?.isEpisode == true)
    }

    @Test
    fun testWordySeasonEpisodeSanitization() {
        val meta = TitleSanitizer.sanitize("Wednesday Season 1 Episode 3", "com.netflix.ninja")
        assertNotNull(meta)
        assertEquals("Wednesday", meta?.title)
        assertEquals(1, meta?.season)
        assertEquals(3, meta?.episode)
    }

    @Test
    fun testMovieWithYearSanitization() {
        val meta = TitleSanitizer.sanitize("Inception (2010) [1080p]", "com.netflix.ninja")
        assertNotNull(meta)
        assertEquals("Inception", meta?.title)
        assertEquals(2010, meta?.year)
    }

    @Test
    fun testUiJunkRejection() {
        assertNull(TitleSanitizer.sanitize("Audio & Subtitles", "com.netflix.ninja"))
        assertNull(TitleSanitizer.sanitize("Next Episode", "com.netflix.ninja"))
        assertNull(TitleSanitizer.sanitize("Play", "com.netflix.ninja"))
        assertNull(TitleSanitizer.sanitize("More Info", "com.netflix.ninja"))
    }
}
