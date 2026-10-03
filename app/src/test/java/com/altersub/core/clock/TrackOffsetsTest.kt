package com.altersub.core.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackOffsetsTest {

    @Test
    fun testNewTrackStartsAtZero() {
        val offsets = TrackOffsets()
        assertEquals(0L, offsets.switchTo("inception-a", currentOffsetMs = 0L))

        // User tunes Inception by +750ms, then a new title is detected (no track active yet)
        assertEquals(0L, offsets.switchTo(null, currentOffsetMs = 750L))
        assertEquals(0L, offsets.switchTo("interstellar-a", currentOffsetMs = 0L))
    }

    @Test
    fun testOffsetIsRestoredWhenReturningToATrack() {
        val offsets = TrackOffsets()
        offsets.switchTo("release-a", 0L)
        assertEquals(0L, offsets.switchTo("release-b", currentOffsetMs = -1_250L)) // Tuned A, switched to B
        assertEquals(-1_250L, offsets.switchTo("release-a", currentOffsetMs = 400L)) // Back to A
        assertEquals(400L, offsets.switchTo("release-b", currentOffsetMs = -1_250L)) // And B kept its own
    }

    @Test
    fun testReselectingSameTrackKeepsCurrentOffset() {
        val offsets = TrackOffsets()
        offsets.switchTo("release-a", 0L)
        assertEquals(300L, offsets.switchTo("release-a", currentOffsetMs = 300L))
    }
}
