package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.session.TitleMatch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CinemetaTitleResolverTest {

    private val server = MockWebServer()
    private lateinit var resolver: CinemetaTitleResolver

    @Before
    fun setUp() {
        server.start()
        resolver = CinemetaTitleResolver(baseUrl = server.url("").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun testParsesMatchesWithYears() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"metas":[
                    {"imdb_id":"tt32543911","id":"tt32543911","name":"Under the Open Sky","releaseInfo":"2025"},
                    {"imdb_id":"tt12801374","name":"Under the Open Sky","releaseInfo":"2020"},
                    {"id":"tt0000001","name":"Dark","releaseInfo":"2017–2020"},
                    {"id":"kitsu:123","name":"Not an IMDb title"},
                    {"imdb_id":"tt12801374","name":"Duplicate"}
                ]}"""
            )
        )

        val matches = resolver.find(ContentMetadata(title = "Under the open sky"))

        assertEquals("/catalog/movie/top/search=Under+the+open+sky.json", server.takeRequest().path)
        assertEquals(
            listOf(
                TitleMatch("tt32543911", "Under the Open Sky", 2025),
                TitleMatch("tt12801374", "Under the Open Sky", 2020),
                TitleMatch("tt0000001", "Dark", 2017)
            ),
            matches
        )
    }

    @Test
    fun testEpisodesSearchTheSeriesCatalog() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"metas":[]}"""))
        resolver.find(ContentMetadata(title = "Dark", season = 1, episode = 2))
        assertEquals("/catalog/series/top/search=Dark.json", server.takeRequest().path)
    }

    @Test
    fun testFailuresYieldNoMatches() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(resolver.find(ContentMetadata(title = "x")).isEmpty())
        assertTrue(resolver.find(ContentMetadata(title = "x")).isEmpty())
    }
}
