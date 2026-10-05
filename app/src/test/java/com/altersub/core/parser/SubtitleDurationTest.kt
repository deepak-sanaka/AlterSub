package com.altersub.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleDurationTest {

    @Test
    fun testSrtRunsUntilItsLatestCueEnds() {
        val srt = "1\r\n00:00:01,000 --> 00:00:02,500\r\nHello\r\n\r\n" +
            "2\r\n02:19:22,738 --> 02:19:25,104\r\nGoodbye\r\n\r\n" +
            // Out of order, but ends earlier: the latest end wins
            "3\r\n01:00:00,000 --> 01:00:01,000\r\nMiddle\r\n"
        assertEquals((2 * 3600 + 19 * 60 + 25) * 1000L + 104, SubtitleDuration.of(srt.toByteArray()))
    }

    @Test
    fun testWebVttWithoutHours() {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:04.250\nShort clip\n\n01:02.000 --> 01:05.5\nEnd\n"
        assertEquals(65_500L, SubtitleDuration.of(vtt.toByteArray()))
    }

    @Test
    fun testUtf16FilesAreRead() {
        val srt = "1\n00:00:01,000 --> 00:42:00,000\nLine\n"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + srt.toByteArray(Charsets.UTF_16LE)
        assertEquals(42 * 60_000L, SubtitleDuration.of(bytes))
    }

    @Test
    fun testNoCuesHasNoDuration() {
        assertNull(SubtitleDuration.of("<html>Not found</html>".toByteArray()))
        assertNull(SubtitleDuration.of(ByteArray(0)))
    }
}
