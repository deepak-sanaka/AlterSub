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

class OpenSubtitlesApiProviderTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server.start()
        baseUrl = server.url("/api/v1").toString()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun testDisabledWithoutApiKey() = runBlocking {
        val provider = OpenSubtitlesApiProvider(baseUrl = baseUrl)
        assertTrue(provider.search(ContentMetadata(title = "Inception"), "en").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun testSearchSendsKeyAndEpisodeFiltersAndParsesFiles() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[
                    {"id":"9001","attributes":{"release":"Stranger.Things.S04E01.WEB","language":"en",
                     "hearing_impaired":true,"files":[{"file_id":555}]}},
                    {"id":"9002","attributes":{"release":"No files","language":"en","files":[]}}
                ]}"""
            )
        )
        val provider = OpenSubtitlesApiProvider(apiKey = "test-key", authToken = "test-token", baseUrl = baseUrl)

        val tracks = provider.search(
            ContentMetadata(title = "Stranger Things", season = 4, episode = 1, imdbId = "tt4574334"), "en"
        )

        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("Api-Key"))
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("en", request.requestUrl!!.queryParameter("languages"))
        assertEquals("4574334", request.requestUrl!!.queryParameter("imdb_id")) // "tt" prefix stripped
        assertEquals("4", request.requestUrl!!.queryParameter("season_number"))
        assertEquals("1", request.requestUrl!!.queryParameter("episode_number"))

        assertEquals(1, tracks.size)
        assertEquals("os-9001-555", tracks[0].id)
        assertEquals("555", tracks[0].downloadUrl)
        assertTrue(tracks[0].isHearingImpaired)
    }

    @Test
    fun testDownloadFollowsTheTemporaryLink() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"link":"${server.url("/files/555.srt")}"}"""))
        server.enqueue(MockResponse().setBody("1\n00:00:01,000 --> 00:00:02,000\nFriends don't lie\n"))
        val provider = OpenSubtitlesApiProvider(apiKey = "test-key", baseUrl = baseUrl)
        val track = SubtitleTrack(
            id = "os-9001-555", title = "S04E01", language = "en", source = provider.name, downloadUrl = "555"
        )

        val file = provider.download(track, tempDir.newFolder("subtitles"))!!

        val linkRequest = server.takeRequest()
        assertEquals("POST", linkRequest.method)
        assertEquals("/api/v1/download", linkRequest.path)
        assertTrue(linkRequest.body.readUtf8().contains("\"file_id\":555"))
        assertEquals("/files/555.srt", server.takeRequest().path)
        assertTrue(file.readText().contains("Friends don't lie"))
    }
}
