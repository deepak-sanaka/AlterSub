package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class OpenSubtitlesApiProvider(
    private var apiKey: String = "",
    private var authToken: String = "",
    private val client: OkHttpClient = Http.client,
    // Overridable so tests can point the provider at a local mock server
    private val baseUrl: String = "https://api.opensubtitles.com/api/v1"
) : SubtitleProvider {

    override val name: String = "Official OpenSubtitles.com"
    override val isEnabled: Boolean get() = apiKey.isNotBlank()

    fun updateCredentials(newApiKey: String, newAuthToken: String = "") {
        apiKey = newApiKey
        authToken = newAuthToken
    }

    override suspend fun search(metadata: ContentMetadata, language: String): List<SubtitleTrack> {
        if (!isEnabled) return emptyList()

        val tracks = mutableListOf<SubtitleTrack>()
        try {
            val queryParams = StringBuilder("languages=$language")
            if (!metadata.imdbId.isNullOrEmpty()) {
                val cleanImdb = metadata.imdbId.removePrefix("tt")
                queryParams.append("&imdb_id=$cleanImdb")
            } else {
                queryParams.append("&query=${URLEncoder.encode(metadata.title, "UTF-8")}")
            }

            if (metadata.isEpisode) {
                metadata.season?.let { queryParams.append("&season_number=$it") }
                metadata.episode?.let { queryParams.append("&episode_number=$it") }
            }

            val request = Request.Builder()
                .url("$baseUrl/subtitles?$queryParams")
                .header("Api-Key", apiKey)
                .header("User-Agent", "AlterSub v1.0")
                .apply {
                    if (authToken.isNotEmpty()) {
                        header("Authorization", "Bearer $authToken")
                    }
                }
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return emptyList()

                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val subId = item.optString("id", "")
                    val attr = item.optJSONObject("attributes") ?: continue
                    val release = attr.optString("release", metadata.title)
                    val lang = attr.optString("language", language)
                    val files = attr.optJSONArray("files")
                    val fileId = files?.optJSONObject(0)?.optInt("file_id", 0) ?: 0
                    val hearingImpaired = attr.optBoolean("hearing_impaired", false)

                    if (fileId > 0) {
                        tracks.add(
                            SubtitleTrack(
                                id = "os-$subId-$fileId",
                                title = release,
                                language = lang,
                                source = name,
                                downloadUrl = fileId.toString(), // Used as file_id for download endpoint
                                isHearingImpaired = hearingImpaired
                            )
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return tracks
    }

    override suspend fun download(track: SubtitleTrack, targetDir: File): File? {
        if (!isEnabled) return null

        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, "${track.id}.srt")
            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile
            }

            // Request download link from OpenSubtitles API
            val fileId = track.downloadUrl.toIntOrNull() ?: return null
            val jsonPayload = JSONObject().put("file_id", fileId).toString()
            val body = jsonPayload.toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("$baseUrl/download")
                .header("Api-Key", apiKey)
                .header("User-Agent", "AlterSub v1.0")
                .apply {
                    if (authToken.isNotEmpty()) {
                        header("Authorization", "Bearer $authToken")
                    }
                }
                .post(body)
                .build()

            val directLink = client.newCall(req).await().use { resp ->
                if (!resp.isSuccessful) return null
                JSONObject(resp.body?.string() ?: "").optString("link", "")
            }
            if (directLink.isEmpty()) return null

            val fileReq = Request.Builder().url(directLink).build()
            client.newCall(fileReq).await().use { fileResp ->
                if (fileResp.isSuccessful) {
                    val bytes = fileResp.body?.bytes() ?: return null
                    FileOutputStream(targetFile).use { it.write(bytes) }
                    return targetFile
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return null
    }
}
