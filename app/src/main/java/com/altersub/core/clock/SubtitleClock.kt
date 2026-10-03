package com.altersub.core.clock

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil

/**
 * High-precision monotonic clock for subtitle playback synchronization.
 * Handles play/pause states, external position calibrations from MediaSession,
 * and user-defined millisecond offsets.
 * Synchronized because it is driven from the main thread (MediaSession), web server threads and the render loop.
 */
class SubtitleClock {

    private var basePositionMs: Long = 0L
    private var lastAnchorRealtimeMs: Long = SystemClock.elapsedRealtime()
    private var playbackSpeed: Float = 1.0f

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _userOffsetMs = MutableStateFlow(0L)
    val userOffsetMs: StateFlow<Long> = _userOffsetMs.asStateFlow()

    // Bumped on every play/pause/seek/sync/offset change, so the render loop can sleep until something actually changes
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /**
     * Returns the current playback position in milliseconds including the user offset.
     */
    @Synchronized
    fun getCurrentTimeMs(): Long {
        return (getPositionMs() + _userOffsetMs.value).coerceAtLeast(0L)
    }

    /**
     * Returns the estimated video position in milliseconds, without the user offset.
     */
    @Synchronized
    fun getPositionMs(): Long {
        return if (_isPlaying.value) {
            val elapsed = SystemClock.elapsedRealtime() - lastAnchorRealtimeMs
            basePositionMs + (elapsed * playbackSpeed).toLong()
        } else {
            basePositionMs
        }
    }

    /**
     * Converts a span of media time into wall-clock milliseconds at the current playback speed (at least 1ms).
     */
    @Synchronized
    fun realtimeFor(mediaMs: Long): Long {
        return ceil(mediaMs / playbackSpeed.toDouble()).toLong().coerceAtLeast(1L)
    }

    /**
     * Resumes or starts the clock.
     */
    @Synchronized
    fun play() {
        if (!_isPlaying.value) {
            lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
            _isPlaying.value = true
            _changes.value++
        }
    }

    /**
     * Pauses the clock, saving the current position.
     */
    @Synchronized
    fun pause() {
        if (_isPlaying.value) {
            val elapsed = SystemClock.elapsedRealtime() - lastAnchorRealtimeMs
            basePositionMs += (elapsed * playbackSpeed).toLong()
            lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
            _isPlaying.value = false
            _changes.value++
        }
    }

    /**
     * Calibrates clock from external media player position (e.g. MediaSession update).
     * [externalPosMs] must already be current; see [extrapolatePosition].
     */
    @Synchronized
    fun syncWithExternalPosition(externalPosMs: Long, playing: Boolean, speed: Float = 1.0f) {
        playbackSpeed = if (speed > 0f) speed else 1.0f
        basePositionMs = externalPosMs
        lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
        _isPlaying.value = playing
        _changes.value++
    }

    /**
     * Seeks to a specific millisecond position, keeping the current play/pause state.
     */
    @Synchronized
    fun seekTo(positionMs: Long) {
        basePositionMs = positionMs.coerceAtLeast(0L)
        lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
        _changes.value++
    }

    /**
     * Adjusts the manual offset by delta milliseconds (+/- 100ms, +/- 500ms, etc.).
     */
    @Synchronized
    fun adjustOffset(deltaMs: Long) {
        _userOffsetMs.update { it + deltaMs }
        _changes.value++
    }

    /**
     * Sets the exact offset value in milliseconds.
     */
    @Synchronized
    fun setOffset(offsetMs: Long) {
        _userOffsetMs.value = offsetMs
        _changes.value++
    }

    /**
     * Resets the clock to zero.
     */
    @Synchronized
    fun reset() {
        basePositionMs = 0L
        lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
        _userOffsetMs.value = 0L
        _isPlaying.value = false
        _changes.value++
    }

    companion object {
        /**
         * MediaSession positions are snapshots taken at [lastUpdateRealtimeMs] (elapsedRealtime base)
         * and players only refresh them on state changes, so a playing position must be advanced
         * by the time elapsed since the snapshot.
         */
        fun extrapolatePosition(
            positionMs: Long,
            lastUpdateRealtimeMs: Long,
            nowRealtimeMs: Long,
            speed: Float,
            playing: Boolean
        ): Long {
            if (!playing || lastUpdateRealtimeMs <= 0L) return positionMs
            val elapsed = (nowRealtimeMs - lastUpdateRealtimeMs).coerceAtLeast(0L)
            val effectiveSpeed = if (speed > 0f) speed else 1.0f
            return positionMs + (elapsed * effectiveSpeed).toLong()
        }
    }
}
