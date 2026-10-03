package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompositeSubtitleProviderTest {

    private val inception = ContentMetadata(title = "Inception", year = 2010)
    private val strangerS4E1 = ContentMetadata(title = "Stranger Things", season = 4, episode = 1)

    @Test
    fun testUploadIsOnlyOfferedForItsOwnContent() {
        val provider = CompositeSubtitleProvider()
        val upload = provider.addLocalTrack(File("inception.srt"), "Uploaded Subtitle", inception)

        assertEquals(listOf(upload), provider.localTracksFor(ContentMetadata(title = "inception")))
        assertTrue(provider.localTracksFor(strangerS4E1).isEmpty())
        assertTrue(provider.localTracksFor(strangerS4E1.copy(episode = 2)).isEmpty())
    }

    @Test
    fun testUploadWithoutDetectedContentIsNeverOfferedLater() {
        val provider = CompositeSubtitleProvider()
        provider.addLocalTrack(File("unknown.srt"), "Uploaded Subtitle", null)

        assertTrue(provider.localTracksFor(inception).isEmpty())
        assertTrue(provider.localTracksFor(strangerS4E1).isEmpty())
    }

    @Test
    fun testNewestUploadComesFirst() {
        val provider = CompositeSubtitleProvider()
        val first = provider.addLocalTrack(File("a.srt"), "A", inception)
        val second = provider.addLocalTrack(File("b.srt"), "B", inception)

        assertEquals(listOf(second, first), provider.localTracksFor(inception))
    }
}
