package com.altersub.server

import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleStyle
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.session.PickMemory
import com.altersub.core.session.SearchState
import com.altersub.core.session.TitleMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.URLEncoder

class WebRemoteServerTest {

    private class FakeController : RemoteController {
        override val clock = SubtitleClock()
        override val currentContent = MutableStateFlow<ContentMetadata?>(ContentMetadata(title = "Inception", year = 2010))
        override val availableTracks = MutableStateFlow(
            listOf(SubtitleTrack(id = "stremio-1", title = "Inception [eng]", language = "eng", source = "Community", downloadUrl = "u"))
        )
        override val activeTrack = MutableStateFlow<SubtitleTrack?>(null)
        override val overlayRunning = MutableStateFlow(true)
        override val overlayError = MutableStateFlow<String?>(null)
        override val subtitleStyle = MutableStateFlow(SubtitleStyle())

        val searches = mutableListOf<String>()
        val chosen = mutableListOf<String>()
        override val matches = MutableStateFlow<List<TitleMatch>>(emptyList())
        override val searchState = MutableStateFlow(SearchState.IDLE)
        val selectedTracks = mutableListOf<SubtitleTrack>()
        val uploads = mutableListOf<File>()
        val uploadNames = mutableListOf<String>()
        val restored = mutableListOf<String>()
        var syncAdjustments = 0
        var picks = emptyList<PickMemory.Pick>()

        override fun searchByText(query: String) {
            searches += query
        }

        override fun chooseMatch(imdbId: String): Boolean {
            if (matches.value.none { it.imdbId == imdbId }) return false
            chosen += imdbId
            return true
        }

        override fun selectTrack(track: SubtitleTrack) {
            selectedTracks += track
        }

        override fun loadDirectSrt(file: File, displayName: String) {
            uploads += file
            uploadNames += displayName
        }

        override fun recentPicks() = picks

        override fun restorePick(contentKey: String): Boolean {
            if (picks.none { it.key == contentKey }) return false
            restored += contentKey
            return true
        }

        override fun onSyncAdjusted() {
            syncAdjustments++
        }

        override fun updateSubtitleStyle(change: (SubtitleStyle) -> SubtitleStyle) {
            subtitleStyle.update(change)
        }
    }

    @get:Rule
    val tempDir = TemporaryFolder()

    private val http = OkHttpClient()
    private val controller = FakeController()
    private val auth = RemoteAuth()
    private lateinit var token: String
    private lateinit var uploadDir: File
    private lateinit var server: WebRemoteServer

    @Before
    fun setUp() {
        uploadDir = File(tempDir.root, "uploads")
        // Port 0: any free port
        server = WebRemoteServer(controller, auth, uploadDir, port = 0, fonts = { if (it == "regular") FONT_BYTES else null })
        server.start()
        token = pairedToken()
    }

    @After
    fun tearDown() = server.stop()

    private fun pin() = (auth.pairing.value as RemoteAuth.Pairing.Open).pin

    private fun wrongPin(pin: String) = if (pin == "000000") "111111" else "000000"

    private fun pairedToken(): String {
        auth.openPairing()
        return (auth.pair(pin()) as RemoteAuth.PairResult.Paired).token
    }

    private fun url(path: String) = "http://localhost:${server.listeningPort}$path"

    // Requests carry this test's paired token unless told otherwise (null: no token header at all)
    private fun request(path: String, token: String?) = Request.Builder().url(url(path)).apply {
        if (token != null) header("X-AlterSub-Token", token)
    }

    private fun get(path: String, token: String? = this.token) =
        http.newCall(request(path, token).build()).execute()

    private fun post(path: String, token: String? = this.token) =
        http.newCall(request(path, token).post(ByteArray(0).toRequestBody()).build()).execute()

    private fun okhttp3.Response.json() = use { JSONObject(it.body!!.string()) }

    @Test
    fun testServesTheRemotePageWithoutPairing() {
        get("/", token = null).use { response ->
            assertEquals(200, response.code)
            assertTrue(response.header("Content-Type")!!.startsWith("text/html"))
            val page = response.body!!.string()
            assertTrue(page.contains("AlterSub Remote"))
            // Track titles are attacker-controlled (release names, search queries); the page must only ever
            // insert server data as text, so HTML parsing of dynamic content is banned outright (KI-8)
            assertFalse("The remote page must not use innerHTML", page.contains("innerHTML"))
            assertFalse("The remote page must not use insertAdjacentHTML", page.contains("insertAdjacentHTML"))
        }
    }

    @Test
    fun testStatusReportsContentTracksClockAndStyle() {
        controller.clock.seekTo(2_483_000L)
        controller.overlayError.value = "Overlay blocked"

        val status = get("/api/status").json()

        assertEquals("Inception (2010)", status.getString("title"))
        assertEquals("stremio-1", status.getJSONArray("tracks").getJSONObject(0).getString("id"))
        assertEquals(2_483_000L, status.getLong("positionMs"))
        assertEquals("Overlay blocked", status.getString("overlayError"))
        assertEquals("yellow", status.getJSONObject("style").getString("color"))
        assertEquals("#FFE500", status.getJSONObject("style").getJSONObject("palette").getString("yellow"))
    }

    @Test
    fun testOffsetSeekAndTogglePlayDriveTheClock() {
        assertEquals(500L, post("/api/offset?delta=500").json().getLong("offsetMs"))
        assertEquals(500L, controller.clock.userOffsetMs.value)

        assertEquals(60_000L, post("/api/seek?positionMs=60000").json().getLong("positionMs"))
        post("/api/seek?positionMs=abc").use { assertEquals(400, it.code) }
        post("/api/seek?positionMs=-5").use { assertEquals(400, it.code) }
        assertEquals(2, controller.syncAdjustments) // The offset and the valid seek are remembered for the title

        assertTrue(post("/api/toggle-play").json().getBoolean("isPlaying"))
        assertTrue(controller.clock.isPlaying.value)
    }

    @Test
    fun testStyleStepsColoursAndReset() {
        val style = post("/api/style?sizeStep=2&positionStep=-1&color=white").json().getJSONObject("style")
        assertEquals(34.0, style.getDouble("textSizeSp"), 0.01)
        assertEquals(0.80, style.getDouble("verticalPosition"), 0.001)
        assertEquals("white", style.getString("color"))

        // Unknown colour names are ignored rather than stored
        assertEquals("white", post("/api/style?color=%3Cscript%3E").json().getJSONObject("style").getString("color"))

        post("/api/style?reset=1").close()
        assertEquals(SubtitleStyle(), controller.subtitleStyle.value)
    }

    @Test
    fun testSelectTrackOnlyAcceptsKnownTracks() {
        post("/api/select-track?id=stremio-1").close()
        assertEquals("stremio-1", controller.selectedTracks.single().id)

        post("/api/select-track?id=nope").use { assertEquals(404, it.code) }
        assertEquals(1, controller.selectedTracks.size)
    }

    @Test
    fun testSearchPassesTheTypedText() {
        post("/api/search?q=Under%20the%20open%20sky%202020").close()
        post("/api/search?q=%20%20").close() // Blank queries are ignored

        assertEquals(listOf("Under the open sky 2020"), controller.searches)
    }

    @Test
    fun testAmbiguousTitlesOfferMatchesToChooseFrom() {
        controller.searchState.value = SearchState.CHOOSE
        controller.matches.value = listOf(
            TitleMatch("tt32543911", "Under the Open Sky", 2025),
            TitleMatch("tt12801374", "Under the Open Sky", 2020)
        )

        val status = get("/api/status").json()
        assertEquals("choose", status.getString("searchState"))
        val matches = status.getJSONArray("matches")
        assertEquals("Under the Open Sky (2020)", matches.getJSONObject(1).getString("title"))

        post("/api/choose?imdbId=tt12801374").use { assertEquals(200, it.code) }
        assertEquals(listOf("tt12801374"), controller.chosen)
        post("/api/choose?imdbId=tt0000000").use { assertEquals(404, it.code) }
    }

    private fun upload(srt: String, token: String?) = http.newCall(
        request("/api/upload", token).post(
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("subtitle", "movie.srt", srt.toRequestBody("application/x-subrip".toMediaType()))
                .build()
        ).build()
    ).execute()

    @Test
    fun testUploadSavesTheFileAndActivatesIt() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nUploaded line\n"

        upload(srt, token).use { assertEquals(200, it.code) }

        assertEquals("movie", controller.uploadNames.single()) // movie.srt, without the extension
        val saved = controller.uploads.single()
        assertEquals(uploadDir, saved.parentFile)
        assertEquals(srt, saved.readText())
    }

    @Test
    fun testUploadWithoutAFileIsRejected() {
        post("/api/upload").use { assertEquals(400, it.code) }
        assertTrue(controller.uploads.isEmpty())
    }

    @Test
    fun testUnknownRoutesAndWrongMethodsAre404() {
        get("/api/nope").use { assertEquals(404, it.code) }
        get("/api/offset?delta=100").use { assertEquals(404, it.code) } // Mutations require POST
        assertTrue(controller.searches.isEmpty())
        assertEquals(0L, controller.clock.userOffsetMs.value)
    }

    @Test
    fun testApiCallsWithoutAPairedTokenAreRejected() {
        get("/api/status", token = null).use { assertEquals(401, it.code) }
        post("/api/offset?delta=500", token = "not-a-token").use { assertEquals(401, it.code) }
        post("/api/search?q=Interstellar", token = "").use { assertEquals(401, it.code) }
        post("/api/toggle-play", token = null).use { assertEquals(401, it.code) }

        assertEquals(0L, controller.clock.userOffsetMs.value)
        assertTrue(controller.searches.isEmpty())
        assertFalse(controller.clock.isPlaying.value)
    }

    @Test
    fun testUnpairedUploadIsRejectedBeforeAnythingIsSaved() {
        upload("1\n00:00:01,000 --> 00:00:02,000\nSneaky\n", token = null).use { assertEquals(401, it.code) }

        assertTrue(controller.uploads.isEmpty())
        assertFalse(uploadDir.exists())
        // The unread body must not be parsed as the next request on the same connection
        get("/api/status").use { assertEquals(200, it.code) }
    }

    @Test
    fun testUnpairingOnTheTvRevokesAccess() {
        auth.unpair()
        get("/api/status").use { assertEquals(401, it.code) }
    }

    @Test
    fun testThePairedPhoneCanUnpairItself() {
        post("/api/unpair", token = "not-the-paired-phone").use { assertEquals(401, it.code) }
        assertTrue(auth.isPaired.value)

        post("/api/unpair").use { assertEquals(200, it.code) }

        assertFalse(auth.isPaired.value)
        get("/api/status").use { assertEquals(401, it.code) }
    }

    @Test
    fun testASecondPhoneCannotPairWhileOneIsPaired() {
        auth.openPairing()
        post("/api/pair?pin=${pin()}", token = null).use { assertEquals(409, it.code) }
        get("/api/status").use { assertEquals(200, it.code) } // The paired phone keeps working
    }

    @Test
    fun testPairingWithThePinShownOnTheTv() {
        auth.unpair()
        auth.closePairing()
        post("/api/pair?pin=123456", token = null).use { assertEquals(403, it.code) }

        auth.openPairing()
        post("/api/pair?pin=${wrongPin(pin())}", token = null).use { assertEquals(403, it.code) }

        val newToken = post("/api/pair?pin=${pin()}", token = null).json().getString("token")
        get("/api/status", token = newToken).use { assertEquals(200, it.code) }
    }

    @Test
    fun testTooManyWrongPinsLockPairing() {
        auth.unpair()
        auth.openPairing()
        val pin = pin()

        repeat(RemoteAuth.MAX_FAILED_ATTEMPTS - 1) {
            post("/api/pair?pin=${wrongPin(pin)}", token = null).use { assertEquals(403, it.code) }
        }
        post("/api/pair?pin=${wrongPin(pin)}", token = null).use { assertEquals(429, it.code) }
        post("/api/pair?pin=$pin", token = null).use { assertEquals(429, it.code) }
    }

    @Test
    fun testFallsBackToTheNextPortWhenOneIsTaken() {
        ServerSocket(0).use { taken ->
            val fallback = WebRemoteServer.startOnFirstFreePort(listOf(taken.localPort, 0)) { port ->
                WebRemoteServer(controller, auth, uploadDir, port)
            }!!
            try {
                assertNotEquals(taken.localPort, fallback.listeningPort)
            } finally {
                fallback.stop()
            }

            val none = WebRemoteServer.startOnFirstFreePort(listOf(taken.localPort)) { port ->
                WebRemoteServer(controller, auth, uploadDir, port)
            }
            assertNull(none)
        }
    }

    @Test
    fun testServesTheUiFontWithoutPairing() {
        get("/fonts/app-sans-regular.ttf", token = null).use { response ->
            assertEquals(200, response.code)
            assertEquals("font/ttf", response.header("Content-Type"))
            assertTrue(response.header("Cache-Control")!!.contains("max-age"))
            assertTrue(FONT_BYTES.contentEquals(response.body!!.bytes()))
        }
        get("/fonts/app-sans-bold.ttf", token = null).use { assertEquals(404, it.code) } // Not provided by this test
        get("/fonts/../../etc/passwd", token = null).use { assertEquals(404, it.code) }
    }

    @Test
    fun testRecentPicksAreListedAndRestoredInOneTap() {
        fun pick(title: String, year: Int? = null) = PickMemory.Pick(
            content = ContentMetadata(title = title, year = year),
            track = SubtitleTrack(id = "t-$title", title = "$title [eng]", language = "eng", source = "Community", downloadUrl = "u"),
            offsetMs = -500,
            positionMs = 0,
            appPackage = null,
            updatedAt = 0
        )
        controller.picks = listOf(pick("Inception", 2010), pick("Dark"))

        val recent = get("/api/status").json().getJSONArray("recent")
        assertEquals(1, recent.length()) // What is loaded now (Inception) isn't offered again
        val dark = recent.getJSONObject(0)
        assertEquals("Dark", dark.getString("title"))
        assertEquals("Dark [eng]", dark.getString("track"))
        assertEquals(-500L, dark.getLong("offsetMs"))

        post("/api/restore?key=" + URLEncoder.encode(dark.getString("key"), "UTF-8")).use { assertEquals(200, it.code) }
        assertEquals(listOf("dark||"), controller.restored)
        post("/api/restore?key=nope").use { assertEquals(404, it.code) }
    }

    private companion object {
        val FONT_BYTES = byteArrayOf(0, 1, 0, 0, 42)
    }
}
