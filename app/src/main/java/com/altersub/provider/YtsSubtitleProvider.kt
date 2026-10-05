package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleLanguages
import com.altersub.core.model.SubtitleTrack
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

class YtsSubtitleProvider(
    private val client: OkHttpClient = Http.client,
    // Overridable so tests can point the provider at a local mock server
    private val baseUrl: String = "https://yts-subs.com"
) : SubtitleProvider {

    override val name: String = "YTS Movie Subtitles"
    override val isEnabled: Boolean = true

    override suspend fun search(metadata: ContentMetadata, language: String): List<SubtitleTrack> {
        // YTS only supports movies (not episodic series)
        if (metadata.isEpisode || metadata.imdbId.isNullOrEmpty()) {
            return emptyList()
        }

        val tracks = mutableListOf<SubtitleTrack>()
        try {
            val url = "$baseUrl/api/v1/movie/${metadata.imdbId}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AlterSub/1.0")
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val subsObj = json.optJSONObject("subtitles") ?: return emptyList()

                val keys = subsObj.keys()
                while (keys.hasNext()) {
                    val lang = keys.next()
                    if (SubtitleLanguages.matches(lang, language)) {
                        val langArray = subsObj.getJSONArray(lang)
                        for (i in 0 until langArray.length()) {
                            val subItem = langArray.getJSONObject(i)
                            val subUrl = subItem.optString("url", "")
                            val rating = subItem.optInt("rating", 0).toFloat()
                            val hi = subItem.optInt("hi", 0) == 1

                            if (subUrl.isNotEmpty()) {
                                tracks.add(
                                    SubtitleTrack(
                                        id = "yts-${metadata.imdbId}-$lang-$i",
                                        title = "${metadata.title} ($lang) [YTS]",
                                        language = lang,
                                        source = name,
                                        downloadUrl = "$baseUrl$subUrl",
                                        format = "zip",
                                        isHearingImpaired = hi,
                                        rating = rating
                                    )
                                )
                            }
                        }
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
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, "${track.id}.srt")

            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile
            }

            val request = Request.Builder().url(track.downloadUrl).build()
            val bytes = client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes() ?: return null
            }

            // If URL returns a zip file, unpack the first .srt inside
            if (track.format == "zip" || track.downloadUrl.endsWith(".zip")) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".srt", ignoreCase = true)) {
                            FileOutputStream(targetFile).use { out ->
                                zis.copyTo(out)
                            }
                            return targetFile
                        }
                        entry = zis.nextEntry
                    }
                }
            } else {
                FileOutputStream(targetFile).use { it.write(bytes) }
                return targetFile
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return null
    }
}
