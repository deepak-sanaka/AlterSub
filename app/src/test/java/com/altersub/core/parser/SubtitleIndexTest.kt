package com.altersub.core.parser

import com.altersub.core.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleIndexTest {

    private val index = SubtitleIndex(
        listOf(
            SubtitleCue(1, 1000L, 4000L, "First"),
            SubtitleCue(2, 6000L, 9000L, "Second")
        )
    )

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
