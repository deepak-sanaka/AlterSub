package com.altersub.core.parser

import com.altersub.core.model.SubtitleCue

/**
 * Fast in-memory index for subtitle cues.
 * Uses binary search to guarantee O(log N) lookup without allocating memory.
 */
class SubtitleIndex(val cues: List<SubtitleCue>) {

    val size: Int get() = cues.size

    /**
     * Finds the active SubtitleCue at the given timestamp in milliseconds.
     * Returns null if no subtitle is active.
     */
    fun getCueAt(timeMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null

        var low = 0
        var high = cues.size - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val cue = cues[mid]

            when {
                timeMs < cue.startTimeMs -> high = mid - 1
                timeMs > cue.endTimeMs -> low = mid + 1
                else -> return cue // Matched within startTimeMs..endTimeMs
            }
        }

        // Check neighboring cue in case of minor overlap or edge-case
        if (high in cues.indices && cues[high].isActiveAt(timeMs)) return cues[high]
        if (low in cues.indices && cues[low].isActiveAt(timeMs)) return cues[low]

        return null
    }

    /**
     * Calculates milliseconds until the next cue transition (start or end).
     * Used by SubtitleOverlayService to schedule sleep intervals instead of polling at 60 FPS.
     */
    fun getTimeUntilNextChange(timeMs: Long): Long {
        if (cues.isEmpty()) return 1000L

        val activeCue = getCueAt(timeMs)
        if (activeCue != null) {
            // Wait until this cue disappears
            val remainingInActive = activeCue.endTimeMs - timeMs
            return remainingInActive.coerceIn(50L, 1000L)
        }

        // Find the next upcoming cue
        var low = 0
        var high = cues.size - 1
        var nextCue: SubtitleCue? = null

        while (low <= high) {
            val mid = (low + high) ushr 1
            val cue = cues[mid]

            if (cue.startTimeMs > timeMs) {
                nextCue = cue
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        return if (nextCue != null) {
            (nextCue.startTimeMs - timeMs).coerceIn(50L, 1000L)
        } else {
            1000L // End of subtitles, check periodically
        }
    }
}
