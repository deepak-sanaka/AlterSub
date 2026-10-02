package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import java.io.File

interface SubtitleProvider {
    val name: String
    val isEnabled: Boolean

    /**
     * Searches for subtitles matching the given media content metadata.
     */
    suspend fun search(metadata: ContentMetadata, language: String = "en"): List<SubtitleTrack>

    /**
     * Downloads the given subtitle track into a local file and returns the File.
     */
    suspend fun download(track: SubtitleTrack, targetDir: File): File?
}
