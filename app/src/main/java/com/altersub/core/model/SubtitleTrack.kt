package com.altersub.core.model

data class SubtitleTrack(
    val id: String,
    val title: String,
    val language: String,
    val source: String,
    val downloadUrl: String,
    val format: String = "srt",
    val isHearingImpaired: Boolean = false,
    val rating: Float? = null,
    val downloadCount: Int? = null,
    val localFilePath: String? = null
)
