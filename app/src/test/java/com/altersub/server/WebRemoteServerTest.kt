package com.altersub.server

import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleStyle
import com.altersub.core.model.SubtitleTrack
import com.altersub.detection.DetectionSource
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

        val detections = mutableListOf<Pair<ContentMetadata, DetectionSource>>()
        val selectedTracks = mutableListOf<SubtitleTrack>()
        val uploads = mutableListOf<File>()

        override fun onContentDetected(metadata: ContentMetadata, source: DetectionSource) {
            detections += metadata to source
        }

        override fun selectTrack(track: SubtitleTrack) {
            selectedTracks += track
        }

        override fun loadDirectSrt(file: File, displayName: String) {
            uploads += file
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
        server = WebRemoteServer(controller, auth, uploadDir, port = 0) // Port 0: any free port
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
            assertTrue(response.body!!.string().contains("AlterSub Remote"))
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
    }

    @Test
    fun testOffsetSeekAndTogglePlayDriveTheClock() {
        assertEquals(500L, post("/api/offset?delta=500").json().getLong("offsetMs"))
        assertEquals(500L, controller.clock.userOffsetMs.value)

        assertEquals(60_000L, post("/api/seek?positionMs=60000").json().getLong("positionMs"))
        post("/api/seek?positionMs=abc").use { assertEquals(400, it.code) }
        post("/api/seek?positionMs=-5").use { assertEquals(400, it.code) }

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
    fun testSearchIsAManualDetection() {
        post("/api/search?q=Interstellar").close()
        post("/api/search?q=%20%20").close() // Blank queries are ignored

        val (metadata, source) = controller.detections.single()
        assertEquals("Interstellar", metadata.title)
        assertEquals(DetectionSource.MANUAL, source)
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
        assertNull(controller.detections.firstOrNull())
        assertEquals(0L, controller.clock.userOffsetMs.value)
    }

    @Test
    fun testApiCallsWithoutAPairedTokenAreRejected() {
        get("/api/status", token = null).use { assertEquals(401, it.code) }
        post("/api/offset?delta=500", token = "not-a-token").use { assertEquals(401, it.code) }
        post("/api/search?q=Interstellar", token = "").use { assertEquals(401, it.code) }
        post("/api/toggle-play", token = null).use { assertEquals(401, it.code) }

        assertEquals(0L, controller.clock.userOffsetMs.value)
        assertTrue(controller.detections.isEmpty())
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
        auth.unpairAll()
        get("/api/status").use { assertEquals(401, it.code) }
    }

    @Test
    fun testPairingWithThePinShownOnTheTv() {
        auth.closePairing()
        post("/api/pair?pin=123456", token = null).use { assertEquals(403, it.code) }

        auth.openPairing()
        post("/api/pair?pin=${wrongPin(pin())}", token = null).use { assertEquals(403, it.code) }

        val newToken = post("/api/pair?pin=${pin()}", token = null).json().getString("token")
        get("/api/status", token = newToken).use { assertEquals(200, it.code) }
    }

    @Test
    fun testTooManyWrongPinsLockPairing() {
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
}
