package com.altersub.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class SrtParserTest {

    @Test
    fun testParseSrtContent() {
        val srtSample = """
            1
            00:00:01,000 --> 00:00:04,500
            <i>Hello, world!</i>
            This is line two.

            2
            00:00:05,000 --> 00:00:08,000
            Second subtitle cue.
        """.trimIndent()

        val cues = SrtParser.parse(ByteArrayInputStream(srtSample.toByteArray(Charsets.UTF_8)))
        assertEquals(2, cues.size)

        val first = cues[0]
        assertEquals(1, first.index)
        assertEquals(1000L, first.startTimeMs)
        assertEquals(4500L, first.endTimeMs)
        assertEquals("Hello, world!\nThis is line two.", first.text)

        val second = cues[1]
        assertEquals(5000L, second.startTimeMs)
        assertEquals(8000L, second.endTimeMs)
        assertEquals("Second subtitle cue.", second.text)
    }

    @Test
    fun testTimestampParsing() {
        assertEquals(1000L, SrtParser.parseTimestamp("00:00:01,000"))
        assertEquals(83456L, SrtParser.parseTimestamp("00:01:23.456"))
        assertEquals(3661200L, SrtParser.parseTimestamp("01:01:01,200"))
    }

    @Test
    fun testLegacyWindows1252FileKeepsAccents() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nCafé crème, señor\n"
        val cues = SrtParser.parse(ByteArrayInputStream(srt.toByteArray(Charset.forName("windows-1252"))))
        assertEquals("Café crème, señor", cues.single().text)
    }

    @Test
    fun testUnicodeEncodingsAndBoms() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nŻółć — ünïcödé\n"
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + srt.toByteArray(Charsets.UTF_8)
        val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + srt.toByteArray(Charsets.UTF_16LE)
        val utf16LeNoBom = srt.toByteArray(Charsets.UTF_16LE)

        for (bytes in listOf(utf8Bom, utf16LeBom, utf16LeNoBom)) {
            val cues = SrtParser.parse(ByteArrayInputStream(bytes))
            assertEquals("Żółć — ünïcödé", cues.single().text)
            assertEquals(1000L, cues.single().startTimeMs)
        }
    }

    @Test
    fun testMissingBlankLineBetweenCues() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            First line
            2
            00:00:03,000 --> 00:00:04,000
            Second line
        """.trimIndent()

        val cues = SrtParser.parse(ByteArrayInputStream(srt.toByteArray(Charsets.UTF_8)))
        assertEquals(2, cues.size)
        assertEquals("First line", cues[0].text)
        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals("Second line", cues[1].text)
        assertEquals(3000L, cues[1].startTimeMs)
    }

    @Test
    fun testWebVttCuesSettingsAndEntities() {
        val vtt = """
            WEBVTT

            NOTE translated by a volunteer

            00:01.000 --> 00:02.500 align:start position:10%
            <v Roger>Hello &amp; welcome</v>

            intro
            00:00:03.000 --> 00:00:04.000
            Use &lt;b&gt; for bold
        """.trimIndent()

        val cues = SrtParser.parse(ByteArrayInputStream(vtt.toByteArray(Charsets.UTF_8)))
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals(2500L, cues[0].endTimeMs)
        assertEquals("Hello & welcome", cues[0].text)
        assertEquals("Use <b> for bold", cues[1].text)
        assertEquals(83456L, SrtParser.parseTimestamp("01:23.456"))
    }

    @Test
    fun testSubtitleIndexLookup() {
        val srtSample = """
            1
            00:00:01,000 --> 00:00:04,000
            First line

            2
            00:00:06,000 --> 00:00:09,000
            Second line
        """.trimIndent()

        val cues = SrtParser.parse(ByteArrayInputStream(srtSample.toByteArray(Charsets.UTF_8)))
        val index = SubtitleIndex(cues)

        assertNull(index.getCueAt(500L)) // Before first cue
        assertNotNull(index.getCueAt(2000L)) // Inside first cue
        assertEquals("First line", index.getCueAt(2000L)?.text)
        assertNull(index.getCueAt(5000L)) // Gap between cues
        assertNotNull(index.getCueAt(7000L)) // Inside second cue
        assertEquals("Second line", index.getCueAt(7000L)?.text)
    }
}
