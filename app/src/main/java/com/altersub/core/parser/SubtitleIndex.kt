package com.altersub.core.parser

import com.altersub.core.model.SubtitleCue

/**
 * Fast in-memory index for subtitle cues sorted by start time.
 * Lookups binary-search the start times (O(log N)), then walk back only while an earlier cue could
 * still be on screen, so overlapping cues (e.g. two speakers in SDH subtitles) are all found.
 */
class SubtitleIndex(val cues: List<SubtitleCue>) {

    // maxEndUpTo[i] is the latest endTimeMs among cues[0..i]: once it falls before the lookup time,
    // no earlier cue can still be active and the walk back stops
    private val maxEndUpTo = LongArray(cues.size).also { maxEnds ->
        var maxEnd = Long.MIN_VALUE
        for (i in cues.indices) {
            maxEnd = maxOf(maxEnd, cues[i].endTimeMs)
            maxEnds[i] = maxEnd
        }
    }

    val size: Int get() = cues.size

    /**
     * Finds the latest-starting SubtitleCue active at the given timestamp in milliseconds.
     * Returns null if no subtitle is active.
     */
    fun getCueAt(timeMs: Long): SubtitleCue? {
        var i = lastStartingAtOrBefore(timeMs)
        while (i >= 0 && maxEndUpTo[i] >= timeMs) {
            if (cues[i].isActiveAt(timeMs)) return cues[i]
            i--
        }
        return null
    }

    /**
     * Text of every cue active at the given timestamp, in start order and one cue per line,
     * or null when nothing is on screen. Called only at cue boundaries, so the join is cheap.
     */
    fun getTextAt(timeMs: Long): String? {
        val active = ArrayList<SubtitleCue>(2)
        var i = lastStartingAtOrBefore(timeMs)
        while (i >= 0 && maxEndUpTo[i] >= timeMs) {
            if (cues[i].isActiveAt(timeMs)) active.add(cues[i])
            i--
        }
        return when (active.size) {
            0 -> null
            1 -> active[0].text
            // Collected newest-first; some files also duplicate a line as two cues
            else -> active.asReversed().map { it.text }.distinct().joinToString("\n")
        }
    }

    /**
     * Milliseconds of media time until the displayed text next changes: an active cue ending or the
     * next cue starting, whichever comes first. Returns [Long.MAX_VALUE] when nothing changes again.
     * Used by SubtitleOverlayService to sleep exactly until the next transition instead of polling.
     */
    fun getTimeUntilNextChange(timeMs: Long): Long {
        var untilChange = Long.MAX_VALUE
        val last = lastStartingAtOrBefore(timeMs)

        // Cues are active through endTimeMs inclusive, so they disappear one millisecond later
        var i = last
        while (i >= 0 && maxEndUpTo[i] >= timeMs) {
            val endTimeMs = cues[i].endTimeMs
            if (endTimeMs >= timeMs) untilChange = minOf(untilChange, endTimeMs + 1 - timeMs)
            i--
        }

        if (last + 1 < cues.size) {
            untilChange = minOf(untilChange, cues[last + 1].startTimeMs - timeMs)
        }
        return untilChange
    }

    /**
     * Up to [before] cues starting at or before [timeMs] and up to [after] cues starting after it, in start order:
     * the lines around a moment, for the phone to pick the one the user just heard.
     */
    fun cuesAround(timeMs: Long, before: Int, after: Int): List<SubtitleCue> {
        val firstAfter = lastStartingAtOrBefore(timeMs) + 1
        return cues.subList(maxOf(0, firstAfter - before.coerceAtLeast(0)), minOf(cues.size, firstAfter + after.coerceAtLeast(0)))
    }

    /** Index of the last cue starting at or before [timeMs], or -1 if none has started yet. */
    private fun lastStartingAtOrBefore(timeMs: Long): Int {
        var low = 0
        var high = cues.size - 1
        var found = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startTimeMs <= timeMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}
