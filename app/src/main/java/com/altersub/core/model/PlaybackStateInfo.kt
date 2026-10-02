package com.altersub.core.model

data class PlaybackStateInfo(
    val isPlaying: Boolean,
    val positionMs: Long = 0L,
    val speed: Float = 1.0f,
    val packageName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
