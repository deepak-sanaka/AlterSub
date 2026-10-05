package com.altersub.provider

import com.altersub.core.model.ContentMetadata
import com.altersub.core.session.TitleDetails
import com.altersub.core.session.TitleMatch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/** Finds the films or series a title could refer to, so the right one (and its IMDb ID) can be chosen. */
fun interface TitleResolver {
    suspend fun find(metadata: ContentMetadata): List<TitleMatch>

    /** Country, runtime and year of one match, shown with its subtitle files; null when unknown. */
    suspend fun details(match: TitleMatch): TitleDetails? = null

    companion object {
        val NONE = TitleResolver { emptyList() }
    }
}

/** Stremio's public Cinemeta catalog: free, no key, and the same IMDb IDs the subtitle sources use. */
class CinemetaTitleResolver(
    private val baseUrl: String = "https://v3-cinemeta.strem.io",
    private val client: OkHttpClient = Http.client
) : TitleResolver {

    override suspend fun find(metadata: ContentMetadata): List<TitleMatch> {
        val type = if (metadata.isEpisode) "series" else "movie"
        val url = "$baseUrl/catalog/$type/top/search=${URLEncoder.encode(metadata.title, "UTF-8")}.json"
        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) return emptyList()
                val metas = JSONObject(response.body?.string().orEmpty()).optJSONArray("metas") ?: return emptyList()
                (0 until metas.length()).mapNotNull { i ->
                    val meta = metas.getJSONObject(i)
                    val imdbId = meta.optString("imdb_id").ifEmpty { meta.optString("id") }
                    val name = meta.optString("name")
                    if (!imdbId.startsWith("tt") || name.isEmpty()) return@mapNotNull null
                    TitleMatch(imdbId, name, yearOf(meta), type)
                }.distinctBy { it.imdbId }.take(MAX_RESULTS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList() // Offline or a bad answer: providers fall back to their own title lookups
        }
    }

    // Details don't change, and a language switch looks them up again: keep the few recent ones
    private val detailsCache = object : LinkedHashMap<String, TitleDetails>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TitleDetails>) = size > DETAILS_CACHE_SIZE
    }

    override suspend fun details(match: TitleMatch): TitleDetails? {
        synchronized(detailsCache) { detailsCache[match.imdbId] }?.let { return it }
        val url = "$baseUrl/meta/${match.type}/${match.imdbId}.json"
        return try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                if (!response.isSuccessful) return null
                val meta = JSONObject(response.body?.string().orEmpty()).optJSONObject("meta") ?: return null
                TitleDetails(
                    country = meta.optString("country").trim().ifEmpty { null },
                    runtimeMinutes = runtimeMinutesOf(meta.optString("runtime")),
                    year = yearOf(meta)
                ).also { found -> synchronized(detailsCache) { detailsCache[match.imdbId] = found } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** "148 min", "2h 8min" or "2 h" → minutes. */
    private fun runtimeMinutesOf(text: String): Int? {
        val hours = Regex("(\\d+)\\s*h").find(text)?.groupValues?.get(1)?.toInt() ?: 0
        val minutes = Regex("(\\d+)\\s*min").find(text)?.groupValues?.get(1)?.toInt() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    /** "2020", or the start of a range like "2017–2020". */
    private fun yearOf(meta: JSONObject): Int? =
        Regex("""(?:19|20)\d{2}""").find(meta.optString("releaseInfo").ifEmpty { meta.optString("year") })?.value?.toInt()

    private companion object {
        const val MAX_RESULTS = 12
        const val DETAILS_CACHE_SIZE = 24
    }
}
