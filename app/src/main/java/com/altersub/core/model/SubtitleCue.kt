package com.altersub.core.model

data class SubtitleCue(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
) {
    fun isActiveAt(timeMs: Long): Boolean {
        return timeMs in startTimeMs..endTimeMs
    }
}
