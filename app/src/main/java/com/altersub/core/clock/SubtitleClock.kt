package com.altersub.core.clock

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-precision monotonic clock for subtitle playback synchronization.
 * Handles play/pause states, external position calibrations from MediaSession,
 * and user-defined millisecond offsets.
 */
class SubtitleClock {

    private var basePositionMs: Long = 0L
    private var lastAnchorRealtimeMs: Long = SystemClock.elapsedRealtime()
    private var playbackSpeed: Float = 1.0f

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _userOffsetMs = MutableStateFlow(0L)
    val userOffsetMs: StateFlow<Long> = _userOffsetMs.asStateFlow()

    /**
     * Returns the current playback position in milliseconds including the user offset.
     */
    fun getCurrentTimeMs(): Long {
        val calculated = if (_isPlaying.value) {
            val elapsed = SystemClock.elapsedRealtime() - lastAnchorRealtimeMs
            basePositionMs + (elapsed * playbackSpeed).toLong()
        } else {
            basePositionMs
        }
        return (calculated + _userOffsetMs.value).coerceAtLeast(0L)
    }

    /**
     * Resumes or starts the clock.
     */
    fun play() {
        if (!_isPlaying.value) {
            lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
            _isPlaying.value = true
        }
    }

    /**
     * Pauses the clock, saving the current position.
     */
    fun pause() {
        if (_isPlaying.value) {
            val elapsed = SystemClock.elapsedRealtime() - lastAnchorRealtimeMs
            basePositionMs += (elapsed * playbackSpeed).toLong()
            lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
            _isPlaying.value = false
        }
    }

    /**
     * Calibrates clock from external media player position (e.g. MediaSession update).
     */
    fun syncWithExternalPosition(externalPosMs: Long, playing: Boolean, speed: Float = 1.0f) {
        playbackSpeed = if (speed > 0f) speed else 1.0f
        basePositionMs = externalPosMs
        lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
        _isPlaying.value = playing
    }

    /**
     * Seeks to a specific millisecond position.
     */
    fun seekTo(positionMs: Long) {
        basePositionMs = positionMs.coerceAtLeast(0L)
        lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
    }

    /**
     * Adjusts the manual offset by delta milliseconds (+/- 100ms, +/- 500ms, etc.).
     */
    fun adjustOffset(deltaMs: Long) {
        _userOffsetMs.value += deltaMs
    }

    /**
     * Sets the exact offset value in milliseconds.
     */
    fun setOffset(offsetMs: Long) {
        _userOffsetMs.value = offsetMs
    }

    /**
     * Resets the clock to zero.
     */
    fun reset() {
        basePositionMs = 0L
        lastAnchorRealtimeMs = SystemClock.elapsedRealtime()
        _userOffsetMs.value = 0L
        _isPlaying.value = false
    }
}
