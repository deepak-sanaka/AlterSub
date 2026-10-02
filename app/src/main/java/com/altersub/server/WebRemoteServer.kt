package com.altersub.server

import android.content.Context
import android.util.Log
import com.altersub.AlterSubApp
import com.altersub.core.model.ContentMetadata
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WebRemoteServer(
    private val context: Context,
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
                    AlterSubApp.instance.clock.adjustOffset(delta)
                    jsonResponse(JSONObject().put("success", true).put("offsetMs", AlterSubApp.instance.clock.userOffsetMs.value))
                }

                uri == "/api/toggle-play" && method == Method.POST -> {
                    val clock = AlterSubApp.instance.clock
                    if (clock.isPlaying.value) clock.pause() else clock.play()
                    jsonResponse(JSONObject().put("success", true).put("isPlaying", clock.isPlaying.value))
                }

                uri == "/api/select-track" && method == Method.POST -> {
                    val trackId = session.parms["id"] ?: ""
                    val track = AlterSubApp.instance.availableTracks.value.find { it.id == trackId }
                    if (track != null) {
                        AlterSubApp.instance.loadAndActivateTrack(track)
                        jsonResponse(JSONObject().put("success", true))
                    } else {
                        jsonResponse(JSONObject().put("error", "Track not found"), Response.Status.NOT_FOUND)
                    }
                }

                uri == "/api/search" && method == Method.POST -> {
                    val query = session.parms["q"] ?: ""
                    if (query.isNotBlank()) {
                        AlterSubApp.instance.onContentDetected(ContentMetadata(title = query))
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
        val app = AlterSubApp.instance
        val content = app.currentContent.value
        val activeTrack = app.activeTrack.value
        val tracks = app.availableTracks.value

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
            .put("offsetMs", app.clock.userOffsetMs.value)
            .put("isPlaying", app.clock.isPlaying.value)
            .put("tracks", tracksArray)

        return jsonResponse(json)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)

        for ((key, tempPath) in files) {
            val tempFile = File(tempPath)
            if (tempFile.exists()) {
                val uploadDir = File(context.cacheDir, "uploads").apply { mkdirs() }
                val targetFile = File(uploadDir, "phone_upload_${System.currentTimeMillis()}.srt")

                FileInputStream(tempFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                AlterSubApp.instance.loadDirectSrt(targetFile, "Uploaded Subtitle")
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
