package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

class CompositeSubtitleProvider(
    val stremioProvider: StremioSubtitleProvider = StremioSubtitleProvider(),
    val ytsProvider: YtsSubtitleProvider = YtsSubtitleProvider(),
    val openSubtitlesApiProvider: OpenSubtitlesApiProvider = OpenSubtitlesApiProvider()
) {
    // In-memory list for tracks uploaded directly via Phone Web Remote
    private val localUploadedTracks = mutableListOf<SubtitleTrack>()

    fun addLocalTrack(file: File, displayName: String): SubtitleTrack {
        val track = SubtitleTrack(
            id = "local-${System.currentTimeMillis()}",
            title = "$displayName [Phone Upload]",
            language = "custom",
            source = "Phone Companion Upload",
            downloadUrl = file.absolutePath,
            localFilePath = file.absolutePath
        )
        localUploadedTracks.add(0, track)
        return track
    }

    suspend fun searchAll(metadata: ContentMetadata, language: String = "en"): List<SubtitleTrack> = coroutineScope {
        val results = mutableListOf<SubtitleTrack>()

        // 1. Prepend any manually uploaded local tracks
        results.addAll(localUploadedTracks)

        // 2. Query enabled online providers in parallel
        val deferredList = listOfNotNull(
            if (openSubtitlesApiProvider.isEnabled) async { openSubtitlesApiProvider.search(metadata, language) } else null,
            if (stremioProvider.isEnabled) async { stremioProvider.search(metadata, language) } else null,
            if (ytsProvider.isEnabled && !metadata.isEpisode) async { ytsProvider.search(metadata, language) } else null
        )

        val providerOutputs = deferredList.awaitAll()
        for (list in providerOutputs) {
            results.addAll(list)
        }

        // Deduplicate by title/language
        return@coroutineScope results.distinctBy { it.id }
    }

    suspend fun downloadTrack(track: SubtitleTrack, targetDir: File): File? {
        // If already local
        if (track.localFilePath != null) {
            val f = File(track.localFilePath)
            if (f.exists()) return f
        }

        return when {
            track.source == openSubtitlesApiProvider.name -> openSubtitlesApiProvider.download(track, targetDir)
            track.source == ytsProvider.name -> ytsProvider.download(track, targetDir)
            else -> stremioProvider.download(track, targetDir)
        }
    }
}
