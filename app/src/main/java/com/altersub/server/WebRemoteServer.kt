package com.altersub.server

import android.util.Log
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleStyle
import com.altersub.detection.DetectionSource
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class WebRemoteServer(
    private val controller: RemoteController,
    private val auth: RemoteAuth,
    private val uploadDir: File,
    port: Int = DEFAULT_PORT
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // Every API call except pairing itself needs a paired phone's token; the page at / holds no data
        if (uri.startsWith("/api/") && uri != "/api/pair" && !auth.isAuthorized(session.headers[RemoteAuth.TOKEN_HEADER])) {
            return jsonResponse(
                JSONObject().put("error", "Pair this phone with the PIN shown on the TV").put("pairingRequired", true),
                Response.Status.UNAUTHORIZED
            ).apply {
                // The request body is left unread, and NanoHTTPD would parse it as the next request on a kept-alive connection
                closeConnection(true)
            }
        }

        return try {
            when {
                uri == "/" && method == Method.GET -> {
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "text/html; charset=UTF-8",
                        WebRemoteHtml.getHtml()
                    )
                }

                uri == "/api/pair" && method == Method.POST -> {
                    handlePair(session.parms["pin"] ?: "")
                }

                uri == "/api/status" && method == Method.GET -> {
                    handleStatus()
                }

                uri == "/api/offset" && method == Method.POST -> {
                    val params = session.parms
                    val delta = params["delta"]?.toLongOrNull() ?: 0L
                    controller.clock.adjustOffset(delta)
                    jsonResponse(JSONObject().put("success", true).put("offsetMs", controller.clock.userOffsetMs.value))
                }

                uri == "/api/seek" && method == Method.POST -> {
                    // Lets the user line the clock up with the player's on-screen time when no MediaSession position is available
                    val positionMs = session.parms["positionMs"]?.toLongOrNull()
                    if (positionMs == null || positionMs < 0) {
                        jsonResponse(JSONObject().put("error", "positionMs must be a non-negative number"), Response.Status.BAD_REQUEST)
                    } else {
                        val clock = controller.clock
                        clock.seekTo(positionMs)
                        jsonResponse(JSONObject().put("success", true).put("positionMs", clock.getPositionMs()))
                    }
                }

                uri == "/api/style" && method == Method.POST -> {
                    // Relative steps and named colours only; SubtitleStyle clamps every value to a legible range
                    val params = session.parms
                    controller.updateSubtitleStyle { style ->
                        if (params["reset"] == "1") {
                            SubtitleStyle()
                        } else {
                            val stepped = style
                                .withTextSizeStep(params["sizeStep"]?.toIntOrNull() ?: 0)
                                .withPositionStep(params["positionStep"]?.toIntOrNull() ?: 0)
                            params["color"]?.let(stepped::withColor) ?: stepped
                        }
                    }
                    jsonResponse(JSONObject().put("success", true).put("style", styleJson(controller.subtitleStyle.value)))
                }

                uri == "/api/toggle-play" && method == Method.POST -> {
                    val clock = controller.clock
                    if (clock.isPlaying.value) clock.pause() else clock.play()
                    jsonResponse(JSONObject().put("success", true).put("isPlaying", clock.isPlaying.value))
                }

                uri == "/api/select-track" && method == Method.POST -> {
                    val trackId = session.parms["id"] ?: ""
                    val track = controller.availableTracks.value.find { it.id == trackId }
                    if (track != null) {
                        controller.selectTrack(track)
                        jsonResponse(JSONObject().put("success", true))
                    } else {
                        jsonResponse(JSONObject().put("error", "Track not found"), Response.Status.NOT_FOUND)
                    }
                }

                uri == "/api/search" && method == Method.POST -> {
                    val query = session.parms["q"] ?: ""
                    if (query.isNotBlank()) {
                        controller.onContentDetected(ContentMetadata(title = query), DetectionSource.MANUAL)
                    }
                    jsonResponse(JSONObject().put("success", true))
                }

                uri == "/api/upload" && method == Method.POST -> {
                    handleUpload(session)
                }

                else -> {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request error: ${e.message}")
            jsonResponse(JSONObject().put("error", e.message), Response.Status.INTERNAL_ERROR)
        }
    }

    private fun handlePair(pin: String): Response = when (val result = auth.pair(pin)) {
        is RemoteAuth.PairResult.Paired ->
            jsonResponse(JSONObject().put("success", true).put("token", result.token))

        is RemoteAuth.PairResult.WrongPin -> jsonResponse(
            JSONObject().put("error", "Wrong PIN. ${result.attemptsLeft} ${if (result.attemptsLeft == 1) "try" else "tries"} left."),
            Response.Status.FORBIDDEN
        )

        RemoteAuth.PairResult.NotOpen -> jsonResponse(
            JSONObject().put("error", "Open AlterSub on the TV to see the PIN, then try again."),
            Response.Status.FORBIDDEN
        )

        RemoteAuth.PairResult.Locked -> jsonResponse(
            JSONObject().put("error", "Too many wrong PINs. Press Back on the TV, reopen AlterSub, and enter the new PIN."),
            Response.Status.TOO_MANY_REQUESTS
        )
    }

    private fun handleStatus(): Response {
        val content = controller.currentContent.value
        val activeTrack = controller.activeTrack.value
        val tracks = controller.availableTracks.value

        val tracksArray = JSONArray()
        for (t in tracks) {
            tracksArray.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("language", t.language)
                    .put("source", t.source)
            )
        }

        val json = JSONObject()
            .put("title", content?.getDisplayName() ?: "")
            .put("activeTrack", activeTrack?.title ?: "")
            .put("activeTrackId", activeTrack?.id ?: "")
            .put("offsetMs", controller.clock.userOffsetMs.value)
            .put("positionMs", controller.clock.getPositionMs())
            .put("isPlaying", controller.clock.isPlaying.value)
            .put("overlayRunning", controller.overlayRunning.value)
            .put("overlayError", controller.overlayError.value ?: "")
            .put("style", styleJson(controller.subtitleStyle.value))
            .put("tracks", tracksArray)

        return jsonResponse(json)
    }

    private fun styleJson(style: SubtitleStyle): JSONObject {
        return JSONObject()
            .put("textSizeSp", style.textSizeSp.toDouble())
            .put("color", style.color)
            .put("verticalPosition", style.verticalPosition.toDouble())
            .put("colors", JSONArray(SubtitleStyle.COLORS.keys.toList()))
            // Hex values so the remote can show real colour swatches
            .put("palette", JSONObject().apply {
                SubtitleStyle.COLORS.forEach { (name, argb) -> put(name, String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)) }
            })
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)

        for (tempPath in files.values) {
            val tempFile = File(tempPath)
            if (tempFile.exists()) {
                uploadDir.mkdirs()
                val targetFile = File(uploadDir, "phone_upload_${System.currentTimeMillis()}.srt")

                FileInputStream(tempFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                controller.loadDirectSrt(targetFile, "Uploaded Subtitle")
                return jsonResponse(JSONObject().put("success", true))
            }
        }

        return jsonResponse(JSONObject().put("error", "No file received"), Response.Status.BAD_REQUEST)
    }

    private fun jsonResponse(
        json: JSONObject,
        status: Response.IStatus = Response.Status.OK
    ): Response {
        return newFixedLengthResponse(status, "application/json; charset=UTF-8", json.toString())
    }

    companion object {
        private const val TAG = "WebRemoteServer"
        const val DEFAULT_PORT = 8080

        /** 8080 is a common default (Kodi's web interface uses it), so a few neighbours are tried before giving up. */
        val PORTS = (DEFAULT_PORT..DEFAULT_PORT + 9).toList()

        /** Starts a server on the first of [ports] that can be bound, or returns null if none can. */
        fun startOnFirstFreePort(ports: List<Int>, create: (port: Int) -> WebRemoteServer): WebRemoteServer? {
            for (port in ports) {
                val server = create(port)
                try {
                    server.start()
                    return server
                } catch (e: IOException) {
                    server.stop() // Closes the socket that failed to bind
                    Log.w(TAG, "Port $port unavailable: ${e.message}")
                }
            }
            return null
        }
    }
}
