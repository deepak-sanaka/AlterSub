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

class WebRemoteServer(
    private val controller: RemoteController,
    private val uploadDir: File,
    port: Int = 8080
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return try {
            when {
                uri == "/" && method == Method.GET -> {
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "text/html; charset=UTF-8",
                        WebRemoteHtml.getHtml()
                    )
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
            Log.e("WebRemoteServer", "Request error: ${e.message}")
            jsonResponse(JSONObject().put("error", e.message), Response.Status.INTERNAL_ERROR)
        }
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
}
