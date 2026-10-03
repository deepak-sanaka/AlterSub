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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
    private lateinit var uploadDir: File
    private lateinit var server: WebRemoteServer

    @Before
    fun setUp() {
        uploadDir = File(tempDir.root, "uploads")
        server = WebRemoteServer(controller, uploadDir, port = 0) // Port 0: any free port
        server.start()
    }

    @After
    fun tearDown() = server.stop()

    private fun url(path: String) = "http://localhost:${server.listeningPort}$path"

    private fun get(path: String) = http.newCall(Request.Builder().url(url(path)).build()).execute()

    private fun post(path: String) =
        http.newCall(Request.Builder().url(url(path)).post(ByteArray(0).toRequestBody()).build()).execute()

    private fun okhttp3.Response.json() = use { JSONObject(it.body!!.string()) }

    @Test
    fun testServesTheRemotePage() {
        get("/").use { response ->
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

    @Test
    fun testUploadSavesTheFileAndActivatesIt() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nUploaded line\n"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("subtitle", "movie.srt", srt.toRequestBody("application/x-subrip".toMediaType()))
            .build()

        http.newCall(Request.Builder().url(url("/api/upload")).post(body).build()).execute().use {
            assertEquals(200, it.code)
        }

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
}
