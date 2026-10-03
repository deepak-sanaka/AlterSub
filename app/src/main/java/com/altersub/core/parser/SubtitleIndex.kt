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
     * Milliseconds of media time until the displayed text next changes: the active cue ending or the
     * next cue starting, whichever comes first. Returns [Long.MAX_VALUE] when nothing changes again.
     * Used by SubtitleOverlayService to sleep exactly until the next transition instead of polling.
     */
    fun getTimeUntilNextChange(timeMs: Long): Long {
        var untilChange = Long.MAX_VALUE

        // Cues are active through endTimeMs inclusive, so they disappear one millisecond later
        getCueAt(timeMs)?.let { untilChange = it.endTimeMs + 1 - timeMs }

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

        nextCue?.let { untilChange = minOf(untilChange, it.startTimeMs - timeMs) }
        return untilChange
    }
}
