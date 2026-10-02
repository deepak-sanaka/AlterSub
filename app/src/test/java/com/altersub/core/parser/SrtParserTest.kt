package com.altersub.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

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
