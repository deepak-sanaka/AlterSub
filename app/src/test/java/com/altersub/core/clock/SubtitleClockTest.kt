package com.altersub.core.clock

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleClockTest {

    @Test
    fun testPlayingPositionAdvancesBySnapshotAge() {
        // Snapshot of 60s taken 5s ago
        val position = SubtitleClock.extrapolatePosition(60_000L, 100_000L, 105_000L, 1.0f, playing = true)
        assertEquals(65_000L, position)
    }

    @Test
    fun testPlaybackSpeedScalesElapsedTime() {
        val position = SubtitleClock.extrapolatePosition(60_000L, 100_000L, 104_000L, 1.5f, playing = true)
        assertEquals(66_000L, position)
    }

    @Test
    fun testPausedPositionIsNotAdvanced() {
        val position = SubtitleClock.extrapolatePosition(60_000L, 100_000L, 400_000L, 1.0f, playing = false)
        assertEquals(60_000L, position)
    }

    @Test
    fun testMissingOrFutureSnapshotTimeIsNotAdvanced() {
        assertEquals(60_000L, SubtitleClock.extrapolatePosition(60_000L, 0L, 400_000L, 1.0f, playing = true))
        assertEquals(60_000L, SubtitleClock.extrapolatePosition(60_000L, 500_000L, 400_000L, 1.0f, playing = true))
    }

    @Test
    fun testZeroSpeedWhilePlayingIsTreatedAsNormalSpeed() {
        val position = SubtitleClock.extrapolatePosition(60_000L, 100_000L, 102_000L, 0f, playing = true)
        assertEquals(62_000L, position)
    }
}
