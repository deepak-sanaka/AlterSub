package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioSubtitleProviderLiveTest {

    @Test
    fun testLiveSubtitleSearch() = runBlocking {
        val provider = StremioSubtitleProvider()
        // Query for a popular movie: Inception (IMDb ID: tt1375666)
        val metadata = ContentMetadata(
            title = "Inception",
            imdbId = "tt1375666"
        )

        val tracks = provider.search(metadata, "en")
        println("Found ${tracks.size} subtitle tracks from Stremio OpenSubtitles")
        for (track in tracks.take(3)) {
            println(" -> ${track.title} [${track.downloadUrl}]")
        }

        assertTrue("Expected to find at least one subtitle track", tracks.isNotEmpty())
    }
}
