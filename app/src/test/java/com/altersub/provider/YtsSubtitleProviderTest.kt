package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YtsSubtitleProviderTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var provider: YtsSubtitleProvider

    @Before
    fun setUp() {
        server.start()
        provider = YtsSubtitleProvider(baseUrl = server.url("").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun testSearchParsesLanguagesRatingsAndHearingImpaired() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"subtitles":{
                    "english":[{"url":"/subtitles/inception-en.zip","rating":7,"hi":1}],
                    "french":[{"url":"/subtitles/inception-fr.zip","rating":3,"hi":0}]
                }}"""
            )
        )

        val tracks = provider.search(ContentMetadata(title = "Inception", imdbId = "tt1375666"), "en")

        assertEquals("/api/v1/movie/tt1375666", server.takeRequest().path)
        assertEquals(1, tracks.size)
        assertEquals(server.url("/subtitles/inception-en.zip").toString(), tracks[0].downloadUrl)
        assertEquals(7f, tracks[0].rating)
        assertTrue(tracks[0].isHearingImpaired)
    }

    @Test
    fun testEpisodesAndUnresolvedTitlesAreNotQueried() = runBlocking {
        assertTrue(provider.search(ContentMetadata(title = "Stranger Things", season = 4, episode = 1, imdbId = "tt1"), "en").isEmpty())
        assertTrue(provider.search(ContentMetadata(title = "Inception"), "en").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun testDownloadExtractsTheSrtFromTheZip() = runBlocking {
        val zipBytes = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("README.txt"))
                zip.write("Downloaded from YTS".toByteArray())
                zip.putNextEntry(ZipEntry("Inception.2010.English.srt"))
                zip.write("1\n00:00:01,000 --> 00:00:02,000\nDreams feel real\n".toByteArray())
            }
        }.toByteArray()
        server.enqueue(MockResponse().setBody(Buffer().write(zipBytes)))

        val track = SubtitleTrack(
            id = "yts-tt1375666-english-0",
            title = "Inception (english) [YTS]",
            language = "english",
            source = provider.name,
            downloadUrl = server.url("/subtitles/inception-en.zip").toString(),
            format = "zip"
        )

        val file = provider.download(track, tempDir.newFolder("subtitles"))!!
        assertTrue(file.readText().contains("Dreams feel real"))
    }
}
