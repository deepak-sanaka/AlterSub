package com.altersub.core.parser

import com.altersub.core.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleIndexTest {

    private val index = SubtitleIndex(
        listOf(
            SubtitleCue(1, 1000L, 4000L, "First"),
            SubtitleCue(2, 6000L, 9000L, "Second")
        )
    )

    @Test
    fun testCuesAroundAMoment() {
        val cues = (0 until 6).map { SubtitleCue(it, it * 4_000L, it * 4_000L + 2_000L, "Line " + it) }
        val many = SubtitleIndex(cues)
        fun around(timeMs: Long, before: Int, after: Int) = many.cuesAround(timeMs, before, after).map { it.text }

        // "Before" includes the cue starting exactly then; "after" is what hasn't started yet
        assertEquals(listOf("Line 1", "Line 2", "Line 3", "Line 4"), around(8_000L, 2, 2))
        assertEquals(listOf("Line 0", "Line 1"), around(5_000L, 5, 0))
        assertEquals(listOf("Line 0", "Line 1"), around(-1L, 3, 2))
        assertEquals(listOf("Line 4", "Line 5"), around(60_000L, 2, 9))
        assertEquals(emptyList<String>(), around(8_000L, 0, 0))
        assertEquals(emptyList<String>(), SubtitleIndex(emptyList()).cuesAround(1_000L, 3, 3))
    }

    @Test
    fun testSleepsExactlyUntilNextCueStarts() {
        assertEquals(500L, index.getTimeUntilNextChange(500L))
        assertEquals(1999L, index.getTimeUntilNextChange(4001L)) // In the gap between cues
    }

    @Test
    fun testSleepsUntilActiveCueDisappears() {
        // Cues are active through endTimeMs inclusive, so they vanish one millisecond later
        assertEquals(2001L, index.getTimeUntilNextChange(2000L))
        assertEquals(1L, index.getTimeUntilNextChange(4000L))
    }

    @Test
    fun testNothingLeftToChangeAfterLastCue() {
        assertEquals(Long.MAX_VALUE, index.getTimeUntilNextChange(9001L))
        assertEquals(Long.MAX_VALUE, SubtitleIndex(emptyList()).getTimeUntilNextChange(0L))
    }

    @Test
    fun testOverlappingCuesAreShownTogether() {
        val overlapping = SubtitleIndex(
            listOf(
                SubtitleCue(1, 1000L, 5000L, "- Where were you?"),
                SubtitleCue(2, 3000L, 4000L, "- Out.")
            )
        )
        assertEquals("- Where were you?", overlapping.getTextAt(2000L))
        assertEquals("- Where were you?\n- Out.", overlapping.getTextAt(3500L))
        assertEquals("- Out.", overlapping.getCueAt(3500L)?.text) // Latest-starting active cue
        assertEquals("- Where were you?", overlapping.getTextAt(4500L))
        assertNull(overlapping.getTextAt(5001L))
    }

    @Test
    fun testLongEarlyCueStaysVisibleBehindLaterCues() {
        val withBackgroundCue = SubtitleIndex(
            listOf(
                SubtitleCue(1, 0L, 100_000L, "[ominous music]"),
                SubtitleCue(2, 2000L, 3000L, "First"),
                SubtitleCue(3, 4000L, 5000L, "Second")
            )
        )
        assertEquals("[ominous music]\nSecond", withBackgroundCue.getTextAt(4500L))
        assertEquals("[ominous music]", withBackgroundCue.getTextAt(3500L))
        assertEquals(501L, withBackgroundCue.getTimeUntilNextChange(4500L)) // "Second" ends first
    }

    @Test
    fun testNextStartInsideActiveCueWins() {
        val overlapping = SubtitleIndex(
            listOf(
                SubtitleCue(1, 1000L, 5000L, "Long line"),
                SubtitleCue(2, 3000L, 4000L, "Interjection")
            )
        )
        assertEquals(1000L, overlapping.getTimeUntilNextChange(2000L))
    }
}
