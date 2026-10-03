package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Hits the real Stremio OpenSubtitles proxy, so it is skipped unless explicitly requested:
 * ./gradlew testDebugUnitTest -PliveTests
 */
class StremioSubtitleProviderLiveTest {

    @Before
    fun requireLiveTestsEnabled() {
        assumeTrue(
            "Live network test skipped; run with -PliveTests",
            System.getProperty("altersub.liveTests") == "true"
        )
    }

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
