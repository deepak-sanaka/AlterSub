package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

class CompositeSubtitleProvider(
    // Queried in parallel; results keep this order (official API, community mirror, then YTS)
    private val providers: List<SubtitleProvider> = listOf(
        OpenSubtitlesApiProvider(),
        StremioSubtitleProvider(),
        YtsSubtitleProvider()
    )
) {
    // Tracks uploaded via Phone Web Remote, keyed by the content they were uploaded for
    private val localUploadsByContent = HashMap<String, MutableList<SubtitleTrack>>()

    /**
     * Registers an uploaded file for [content]. With no content detected yet, the upload only
     * plays now and is never offered for titles detected later.
     */
    fun addLocalTrack(file: File, displayName: String, content: ContentMetadata?): SubtitleTrack {
        val track = SubtitleTrack(
            id = "local-${System.currentTimeMillis()}",
            title = "$displayName [Phone Upload]",
            language = "custom",
            source = "Phone Companion Upload",
            downloadUrl = file.absolutePath,
            localFilePath = file.absolutePath
        )
        if (content != null) {
            synchronized(localUploadsByContent) {
                localUploadsByContent.getOrPut(content.contentKey) { mutableListOf() }.add(0, track)
            }
        }
        return track
    }

    fun localTracksFor(content: ContentMetadata): List<SubtitleTrack> = synchronized(localUploadsByContent) {
        localUploadsByContent[content.contentKey]?.toList().orEmpty()
    }

    suspend fun searchAll(metadata: ContentMetadata, language: String = "en"): List<SubtitleTrack> = coroutineScope {
        val results = mutableListOf<SubtitleTrack>()

        // 1. Prepend tracks the user uploaded for this same content
        results.addAll(localTracksFor(metadata))

        // 2. Query enabled online providers in parallel (each skips content it can't serve, e.g. YTS episodes)
        val providerOutputs = providers
            .filter { it.isEnabled }
            .map { provider -> async { provider.search(metadata, language) } }
            .awaitAll()
        for (list in providerOutputs) {
            results.addAll(list)
        }

        // Deduplicate by track id
        return@coroutineScope results.distinctBy { it.id }
    }

    suspend fun downloadTrack(track: SubtitleTrack, targetDir: File): File? {
        // If already local
        if (track.localFilePath != null) {
            val f = File(track.localFilePath)
            if (f.exists()) return f
        }

        return providers.firstOrNull { it.name == track.source }?.download(track, targetDir)
    }
}
