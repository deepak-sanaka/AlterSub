package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class StremioSubtitleProvider(
    private val client: OkHttpClient = Http.client,
    // Overridable so tests can point the provider at a local mock server
    private val subtitlesBaseUrl: String = "https://opensubtitles-v3.strem.io",
    private val catalogBaseUrl: String = "https://v3-cinemeta.strem.io"
) : SubtitleProvider {

    override val name: String = "Community OpenSubtitles"
    override val isEnabled: Boolean = true

    override suspend fun search(metadata: ContentMetadata, language: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        try {
            var imdbId = metadata.imdbId

            // If we don't have an IMDb ID yet, query public Cinemeta catalog by title
            if (imdbId.isNullOrEmpty()) {
                imdbId = resolveImdbId(metadata)
            }

            if (imdbId.isNullOrEmpty()) {
                return emptyList()
            }

            val endpoint = if (metadata.isEpisode) {
                val s = metadata.season ?: 1
                val e = metadata.episode ?: 1
                "$subtitlesBaseUrl/subtitles/series/$imdbId:$s:$e.json"
            } else {
                "$subtitlesBaseUrl/subtitles/movie/$imdbId.json"
            }

            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "AlterSub/1.0 (Android TV)")
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val subtitlesArray = json.optJSONArray("subtitles") ?: return emptyList()

                for (i in 0 until subtitlesArray.length()) {
                    val subObj = subtitlesArray.getJSONObject(i)
                    val lang = subObj.optString("lang", "en")
                    val url = subObj.optString("url", "")
                    val subId = subObj.optString("id", "$imdbId-$i")

                    // Filter for desired language code (e.g. "eng", "en")
                    if (url.isNotEmpty() && matchesLanguage(lang, language)) {
                        tracks.add(
                            SubtitleTrack(
                                id = "stremio-$subId",
                                title = "${metadata.getDisplayName()} [$lang]",
                                language = lang,
                                source = name,
                                downloadUrl = url,
                                format = "srt"
                            )
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("StremioSubtitleProvider error: " + e.message)
            e.printStackTrace()
        }
        return tracks
    }

    override suspend fun download(track: SubtitleTrack, targetDir: File): File? {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, "${track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.srt")

            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile
            }

            val request = Request.Builder()
                .url(track.downloadUrl)
                .header("User-Agent", "AlterSub/1.0")
                .build()

            client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return null
                    FileOutputStream(targetFile).use { it.write(bytes) }
                    return targetFile
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Download error handling
        }
        return null
    }

    private suspend fun resolveImdbId(metadata: ContentMetadata): String? {
        try {
            val type = if (metadata.isEpisode) "series" else "movie"
            val encodedQuery = URLEncoder.encode(metadata.title, "UTF-8")
            val url = "$catalogBaseUrl/catalog/$type/top/search=$encodedQuery.json"

            val request = Request.Builder().url(url).build()
            client.newCall(request).await().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val metas = json.optJSONArray("metas")
                    if (metas != null && metas.length() > 0) {
                        val first = metas.getJSONObject(0)
                        return first.optString("imdb_id").ifEmpty { first.optString("id") }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return null
    }

    private fun matchesLanguage(trackLang: String, targetLang: String): Boolean {
        val t = trackLang.lowercase()
        val target = targetLang.lowercase()
        return t == target ||
                (target == "en" && (t == "eng" || t == "english")) ||
                (target == "es" && (t == "spa" || t == "spanish")) ||
                (target == "fr" && (t == "fre" || t == "fra" || t == "french")) ||
                target == "all"
    }
}
