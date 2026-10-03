package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StremioSubtitleProviderTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var provider: StremioSubtitleProvider

    @Before
    fun setUp() {
        server.start()
        val base = server.url("").toString().removeSuffix("/")
        provider = StremioSubtitleProvider(subtitlesBaseUrl = base, catalogBaseUrl = base)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun testSearchParsesTracksAndFiltersLanguage() = runBlocking {
        server.enqueue(
            json(
                """{"subtitles":[
                    {"id":"101","url":"https://subs.example/101","lang":"eng"},
                    {"id":"102","url":"https://subs.example/102","lang":"spa"},
                    {"id":"103","url":"https://subs.example/103","lang":"english"},
                    {"id":"104","url":"","lang":"eng"}
                ]}"""
            )
        )

        val tracks = provider.search(ContentMetadata(title = "Inception", year = 2010, imdbId = "tt1375666"), "en")

        assertEquals("/subtitles/movie/tt1375666.json", server.takeRequest().path)
        assertEquals(listOf("stremio-101", "stremio-103"), tracks.map { it.id })
        assertEquals("https://subs.example/101", tracks[0].downloadUrl)
        assertEquals("Inception (2010) [eng]", tracks[0].title)
    }

    @Test
    fun testEpisodesUseTheSeriesEndpoint() = runBlocking {
        server.enqueue(json("""{"subtitles":[]}"""))
        provider.search(ContentMetadata(title = "Stranger Things", season = 4, episode = 1, imdbId = "tt4574334"), "en")
        assertEquals("/subtitles/series/tt4574334:4:1.json", server.takeRequest().path)
    }

    @Test
    fun testMissingImdbIdIsResolvedThroughTheCatalog() = runBlocking {
        server.enqueue(json("""{"metas":[{"imdb_id":"tt1375666","name":"Inception"}]}"""))
        server.enqueue(json("""{"subtitles":[{"id":"1","url":"https://subs.example/1","lang":"eng"}]}"""))

        val tracks = provider.search(ContentMetadata(title = "Inception"), "en")

        assertEquals("/catalog/movie/top/search=Inception.json", server.takeRequest().path)
        assertEquals("/subtitles/movie/tt1375666.json", server.takeRequest().path)
        assertEquals(1, tracks.size)
    }

    @Test
    fun testServerErrorsAndBadJsonYieldNoTracks() = runBlocking {
        server.enqueue(json("""{"error":"down"}""", code = 503))
        server.enqueue(json("not json"))
        val metadata = ContentMetadata(title = "Inception", imdbId = "tt1375666")

        assertTrue(provider.search(metadata, "en").isEmpty())
        assertTrue(provider.search(metadata, "en").isEmpty())
    }

    @Test
    fun testDownloadWritesFileAndReusesCache() = runBlocking {
        server.enqueue(MockResponse().setBody("1\n00:00:01,000 --> 00:00:02,000\nHi\n"))
        val track = SubtitleTrack(
            id = "stremio-7",
            title = "Inception [eng]",
            language = "eng",
            source = provider.name,
            downloadUrl = server.url("/file/7").toString()
        )
        val dir = tempDir.newFolder("subtitles")

        val file = provider.download(track, dir)!!
        assertEquals("/file/7", server.takeRequest().path)
        assertTrue(file.readText().contains("Hi"))

        // A second activation is served from the cached file without another request
        assertEquals(file, provider.download(track, dir))
        assertEquals(1, server.requestCount)
    }
}
