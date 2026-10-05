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
    val localFilePath: String? = null,
    /** The subtitle file's own name ("Inception.2010.1080p.BluRay.x264.srt"), when the source gives it. */
    val fileName: String? = null,
    /** The kind of release the file was timed for ("Blu-ray", "Web"), when the source gives it. */
    val release: String? = null
)
