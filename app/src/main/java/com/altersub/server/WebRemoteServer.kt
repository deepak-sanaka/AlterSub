package com.altersub.server

import android.util.Log
import com.altersub.core.model.SubtitleCue
import com.altersub.core.model.SubtitleLanguages
import com.altersub.core.model.SubtitleStyle
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.session.TitleGroup
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

class WebRemoteServer(
    private val controller: RemoteController,
    private val auth: RemoteAuth,
    private val uploadDir: File,
    port: Int = DEFAULT_PORT,
    /** Bytes of the page's UI font by weight name ("regular", "medium", "bold"), or null if unavailable. */
    private val fonts: (String) -> ByteArray? = { null }
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

                // The paired phone disconnecting itself (the TV screen can also unpair it)
                uri == "/api/unpair" && method == Method.POST -> {
                    auth.revoke(session.headers[RemoteAuth.TOKEN_HEADER])
                    jsonResponse(JSONObject().put("success", true))
                }

                uri.startsWith(FONT_PATH) && method == Method.GET -> {
                    serveFont(uri.removePrefix(FONT_PATH))
                }

                uri == "/api/status" && method == Method.GET -> {
                    handleStatus()
                }

                uri == "/api/offset" && method == Method.POST -> {
                    val params = session.parms
                    val delta = params["delta"]?.toLongOrNull() ?: 0L
                    controller.clock.adjustOffset(delta)
                    controller.onSyncAdjusted()
                    jsonResponse(JSONObject().put("success", true).put("offsetMs", controller.clock.userOffsetMs.value))
                }

                // Sync to a line: the phone marks the moment the user hears someone speak, then says which line it was
                uri == "/api/sync/mark" && method == Method.POST -> {
                    val index = controller.subtitleIndex.value
                    if (index == null) {
                        noSubtitles()
                    } else {
                        val markMs = (controller.clock.getCurrentTimeMs() - REACTION_MS).coerceAtLeast(0L)
                        jsonResponse(JSONObject().put("markMs", markMs).put("lines", linesJson(index.cuesAround(markMs, LINES_EACH_SIDE, LINES_EACH_SIDE))))
                    }
                }

                uri == "/api/lines" && method == Method.GET -> {
                    val index = controller.subtitleIndex.value
                    val aroundMs = session.parms["aroundMs"]?.toLongOrNull()
                    when {
                        index == null -> noSubtitles()
                        aroundMs == null -> jsonResponse(JSONObject().put("error", "aroundMs must be a number"), Response.Status.BAD_REQUEST)
                        else -> {
                            val before = (session.parms["before"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_LINES)
                            val after = (session.parms["after"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_LINES)
                            jsonResponse(JSONObject().put("lines", linesJson(index.cuesAround(aroundMs, before, after))))
                        }
                    }
                }

                uri == "/api/sync/line" && method == Method.POST -> {
                    val markMs = session.parms["markMs"]?.toLongOrNull()
                    val startMs = session.parms["startMs"]?.toLongOrNull()
                    if (markMs == null || startMs == null || markMs < 0 || startMs < 0) {
                        jsonResponse(JSONObject().put("error", "markMs and startMs must be non-negative numbers"), Response.Status.BAD_REQUEST)
                    } else {
                        // The line the user heard at the mark should start at the mark: move the subtitles by the gap
                        val deltaMs = startMs - markMs
                        controller.clock.adjustOffset(deltaMs)
                        controller.onSyncAdjusted()
                        jsonResponse(JSONObject().put("success", true).put("deltaMs", deltaMs).put("offsetMs", controller.clock.userOffsetMs.value))
                    }
                }

                uri == "/api/seek" && method == Method.POST -> {
                    // Lets the user line the clock up with the player's on-screen time when no MediaSession position is available
                    val positionMs = session.parms["positionMs"]?.toLongOrNull()
                    if (positionMs == null || positionMs < 0) {
                        jsonResponse(JSONObject().put("error", "positionMs must be a non-negative number"), Response.Status.BAD_REQUEST)
                    } else {
                        val clock = controller.clock
                        clock.seekTo(positionMs)
                        controller.onSyncAdjusted()
                        jsonResponse(JSONObject().put("success", true).put("positionMs", clock.getPositionMs()))
                    }
                }

                uri == "/api/style" && method == Method.POST -> {
                    // Relative steps and named colours and backgrounds only; SubtitleStyle clamps every value to a legible range
                    val params = session.parms
                    controller.updateSubtitleStyle { style ->
                        if (params["reset"] == "1") {
                            SubtitleStyle()
                        } else {
                            val stepped = style
                                .withTextSizeStep(params["sizeStep"]?.toIntOrNull() ?: 0)
                                .withPositionStep(params["positionStep"]?.toIntOrNull() ?: 0)
                            val colored = params["color"]?.let(stepped::withColor) ?: stepped
                            params["background"]?.let(colored::withBackground) ?: colored
                        }
                    }
                    jsonResponse(JSONObject().put("success", true).put("style", styleJson(controller.subtitleStyle.value)))
                }

                uri == "/api/toggle-play" && method == Method.POST -> {
                    val clock = controller.clock
                    if (clock.isPlaying.value) clock.pause() else clock.play()
                    jsonResponse(JSONObject().put("success", true).put("isPlaying", clock.isPlaying.value))
                }

                uri == "/api/search" && method == Method.POST -> {
                    val query = session.parms["q"] ?: ""
                    if (query.isNotBlank()) controller.searchByText(query)
                    jsonResponse(JSONObject().put("success", true))
                }

                // The phone's file picker: polled while it's open, which also starts reading each file's length
                uri == "/api/results" && method == Method.GET -> {
                    controller.onSearchResultsViewed()
                    jsonResponse(resultsJson())
                }

                uri == "/api/use" && method == Method.POST -> {
                    if (controller.useSearchResult(session.parms["id"] ?: "")) {
                        jsonResponse(JSONObject().put("success", true))
                    } else {
                        jsonResponse(JSONObject().put("error", "That file is no longer listed. Search again."), Response.Status.NOT_FOUND)
                    }
                }

                uri == "/api/language" && method == Method.POST -> {
                    if (controller.setSubtitleLanguage(session.parms["code"] ?: "")) {
                        jsonResponse(JSONObject().put("success", true).put("language", controller.subtitleLanguage.value))
                    } else {
                        jsonResponse(JSONObject().put("error", "Unknown language"), Response.Status.BAD_REQUEST)
                    }
                }

                uri == "/api/restore" && method == Method.POST -> {
                    if (controller.restorePick(session.parms["key"] ?: "")) {
                        jsonResponse(JSONObject().put("success", true))
                    } else {
                        jsonResponse(JSONObject().put("error", "No longer remembered"), Response.Status.NOT_FOUND)
                    }
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

        RemoteAuth.PairResult.AlreadyPaired -> jsonResponse(
            JSONObject().put(
                "error",
                "This TV is already paired with another phone. Unpair it on the TV (Phone remote \u2192 Unpair phone) or from that phone, then try again."
            ),
            Response.Status.CONFLICT
        )
    }

    /** UI font files for the phone page. Public (no pairing needed) and cached by the browser for a week. */
    private fun serveFont(fileName: String): Response {
        val weight = FONT_FILE.matchEntire(fileName)?.groupValues?.get(1)
        val bytes = weight?.let(fonts)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        return newFixedLengthResponse(Response.Status.OK, "font/ttf", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "public, max-age=604800")
        }
    }

    private fun handleStatus(): Response {
        val content = controller.currentContent.value
        val activeTrack = controller.activeTrack.value

        val json = JSONObject()
            .put("title", content?.getDisplayName() ?: "")
            .put("activeTrack", activeTrack?.let { it.fileName ?: it.title } ?: "")
            .put("activeTrackId", activeTrack?.id ?: "")
            .put("activeTrackLanguage", activeTrack?.let { languageName(it) } ?: "")
            .put("offsetMs", controller.clock.userOffsetMs.value)
            .put("positionMs", controller.clock.getPositionMs())
            .put("isPlaying", controller.clock.isPlaying.value)
            .put("overlayRunning", controller.overlayRunning.value)
            .put("overlayError", controller.overlayError.value ?: "")
            .put("style", styleJson(controller.subtitleStyle.value))
            .put("recent", recentJson(content?.contentKey))
            .put("searchState", controller.searchState.value.name.lowercase())
            .put("resultsCount", controller.searchResults.value.groups.sumOf { it.tracks.size })
            .put("language", controller.subtitleLanguage.value)
            .put("languages", LANGUAGES_JSON)

        return jsonResponse(json)
    }

    /**
     * Search results for the phone's picker, grouped by title. File names, release names and titles come from
     * uploaders and catalogs: the page renders them with textContent only (KI-8).
     */
    private fun resultsJson(): JSONObject {
        val results = controller.searchResults.value
        val durations = controller.subtitleDurations.value
        val activeId = controller.activeTrack.value?.id
        return JSONObject()
            .put("state", controller.searchState.value.name.lowercase())
            .put("query", results.query)
            .put("language", results.language.ifEmpty { controller.subtitleLanguage.value })
            .put("languageName", SubtitleLanguages.byCode(results.language.ifEmpty { controller.subtitleLanguage.value })?.name ?: "")
            .put("groups", JSONArray().apply { for (group in results.groups) put(groupJson(group, durations, activeId)) })
    }

    private fun groupJson(group: TitleGroup, durations: Map<String, Long>, activeId: String?): JSONObject {
        val content = group.content
        val details = group.details
        return JSONObject()
            .put("title", content.title)
            .put("year", details?.year ?: content.year ?: JSONObject.NULL)
            .put("country", details?.country ?: JSONObject.NULL)
            .put("runtimeMinutes", details?.runtimeMinutes ?: JSONObject.NULL)
            .put("episode", if (content.isEpisode) "S%02dE%02d".format(Locale.ROOT, content.season, content.episode) else JSONObject.NULL)
            .put("files", JSONArray().apply {
                for (track in group.tracks) {
                    val duration = durations[track.id]
                    put(
                        JSONObject()
                            .put("id", track.id)
                            .put("fileName", track.fileName ?: track.title)
                            .put("language", languageName(track))
                            .put("release", track.release ?: JSONObject.NULL)
                            .put("source", track.source)
                            // null: still being checked; -1: couldn't be read
                            .put("durationMs", duration ?: JSONObject.NULL)
                            .put("active", track.id == activeId)
                            .put("lastUsed", track.id == group.lastUsedTrackId)
                    )
                }
            })
    }

    private fun languageName(track: SubtitleTrack): String =
        if (track.localFilePath != null) "Your file" else SubtitleLanguages.nameOf(track.language)

    /** Remembered picks other than what is loaded now, for one-tap restore. */
    private fun recentJson(currentKey: String?): JSONArray {
        val recent = JSONArray()
        for (pick in controller.recentPicks()) {
            if (pick.key == currentKey) continue
            recent.put(
                JSONObject()
                    .put("key", pick.key)
                    .put("title", pick.content.getDisplayName())
                    .put("track", pick.track.title)
                    .put("offsetMs", pick.offsetMs)
            )
        }
        return recent
    }

    private fun styleJson(style: SubtitleStyle): JSONObject {
        return JSONObject()
            .put("textSizeSp", style.textSizeSp.toDouble())
            .put("color", style.color)
            .put("verticalPosition", style.verticalPosition.toDouble())
            .put("colors", JSONArray(SubtitleStyle.COLORS.keys.toList()))
            // Hex values so the remote can show real colour swatches
            .put("palette", JSONObject().apply {
                SubtitleStyle.COLORS.forEach { (name, argb) -> put(name, hex(argb)) }
            })
            .put("background", style.background)
            // Each background's box (null for none, with its opacity), forced text colour (null keeps the chosen one)
            // and outline, so the remote can preview them
            .put("backgrounds", JSONArray().apply {
                SubtitleStyle.BACKGROUNDS.forEach { (name, background) ->
                    put(JSONObject()
                        .put("name", name)
                        .put("box", background.boxArgb?.let(::hex) ?: JSONObject.NULL)
                        .put("boxOpacity", background.boxArgb?.let { ((it ushr 24) / 255.0 * 100).roundToInt() / 100.0 } ?: 0.0)
                        .put("text", background.textArgb?.let(::hex) ?: JSONObject.NULL)
                        .put("edge", hex(background.edgeArgb)))
                }
            })
    }

    private fun hex(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)

        for ((field, tempPath) in files) {
            val tempFile = File(tempPath)
            if (tempFile.exists()) {
                uploadDir.mkdirs()
                val targetFile = File(uploadDir, "phone_upload_${System.currentTimeMillis()}.srt")

                FileInputStream(tempFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                controller.loadDirectSrt(targetFile, uploadName(session.parms[field]))
                return jsonResponse(JSONObject().put("success", true))
            }
        }

        return jsonResponse(JSONObject().put("error", "No file received"), Response.Status.BAD_REQUEST)
    }

    /**
     * The uploaded file's own name, so several uploads can be told apart (KI-30). NanoHTTPD puts a file
     * field's original name in the matching parameter. The page renders it with textContent (KI-8).
     */
    private fun uploadName(originalFileName: String?): String {
        val name = originalFileName.orEmpty()
            .substringAfterLast('/').substringAfterLast('\\')
            .substringBeforeLast('.')
            .replace(Regex("[\\p{Cntrl}]"), " ")
            .trim()
            .take(80)
        return name.ifEmpty { "Uploaded Subtitle" }
    }

    private fun noSubtitles(): Response =
        jsonResponse(JSONObject().put("error", "Choose subtitles first, then sync them."), Response.Status.CONFLICT)

    // Line text comes from the subtitle file; the page renders it with textContent (KI-8)
    private fun linesJson(cues: List<SubtitleCue>) = JSONArray().apply {
        for (cue in cues) put(JSONObject().put("startMs", cue.startTimeMs).put("text", cue.text))
    }

    private fun jsonResponse(
        json: JSONObject,
        status: Response.IStatus = Response.Status.OK
    ): Response {
        return newFixedLengthResponse(status, "application/json; charset=UTF-8", json.toString())
    }

    companion object {
        private const val TAG = "WebRemoteServer"
        private const val FONT_PATH = "/fonts/"
        private val FONT_FILE = Regex("app-sans-(regular|medium|bold)\\.ttf")
        const val DEFAULT_PORT = 8080

        /** The languages offered on the phone: code, English name, and the name in that language. */
        private val LANGUAGES_JSON = JSONArray().apply {
            for (language in SubtitleLanguages.ALL) {
                put(JSONObject().put("code", language.code).put("name", language.name).put("nativeName", language.nativeName))
            }
        }

        /**
         * People tap a beat after a line starts (reaction time, plus the request reaching the TV), so the mark is
         * set this much earlier; otherwise every synced line would show a little late.
         */
        const val REACTION_MS = 300L
        private const val LINES_EACH_SIDE = 10
        private const val MAX_LINES = 50

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
