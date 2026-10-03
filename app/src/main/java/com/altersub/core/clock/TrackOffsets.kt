package com.altersub.core.clock

/**
 * Remembers the user's sync offset per subtitle track: each release is timed differently, so an
 * offset tuned for one track must not carry over to another title or release.
 * Not thread-safe: callers must synchronize.
 */
class TrackOffsets {

    private val offsetsByTrackId = HashMap<String, Long>()
    private var currentTrackId: String? = null

    /**
     * Records [currentOffsetMs] for the track being left and returns the offset to apply for
     * [trackId]: the one remembered for it, or 0 for a new track or when no track is active.
     */
    fun switchTo(trackId: String?, currentOffsetMs: Long): Long {
        currentTrackId?.let { offsetsByTrackId[it] = currentOffsetMs }
        currentTrackId = trackId
        return trackId?.let { offsetsByTrackId[it] } ?: 0L
    }
}
