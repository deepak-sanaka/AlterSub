package com.altersub.core.session

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the user's subtitle picks so they survive restarts and needn't be repeated: for each title, the
 * track chosen, its sync offset, how far playback got, and which streaming app it played in.
 *
 * Netflix never reports what it is playing (KI-26), so "same app, resumed near where the last pick left off"
 * is the only way to recognise it again; [latestForApp] and the saved position exist for that. Kept free of
 * Android: [Store] is backed by SharedPreferences in the app and by a string in tests.
 */
class PickMemory(private val store: Store, private val now: () -> Long = System::currentTimeMillis) {

    interface Store {
        fun read(): String?
        fun write(json: String)
    }

    data class Pick(
        val content: ContentMetadata,
        val track: SubtitleTrack,
        val offsetMs: Long,
        val positionMs: Long,
        /** Streaming app it was last seen playing in, if known. */
        val appPackage: String?,
        val updatedAt: Long
    ) {
        val key: String get() = content.contentKey
    }

    // Most recent first
    private val picks: MutableList<Pick> = load().toMutableList()
    private var lastWriteAt = 0L

    @Synchronized
    fun remember(content: ContentMetadata, track: SubtitleTrack, offsetMs: Long, positionMs: Long, appPackage: String?) {
        val previous = picks.firstOrNull { it.key == content.contentKey }
        picks.removeAll { it.key == content.contentKey }
        picks.add(0, Pick(content, track, offsetMs, positionMs, appPackage ?: previous?.appPackage, now()))
        while (picks.size > MAX_PICKS) picks.removeAt(picks.size - 1)
        persist()
    }

    /**
     * Records sync and progress for a remembered title. Offset changes and app changes are saved at once;
     * position-only updates (several a minute while playing) are written at most every [PROGRESS_WRITE_INTERVAL_MS].
     */
    @Synchronized
    fun updateProgress(contentKey: String, offsetMs: Long, positionMs: Long, appPackage: String? = null) {
        val index = picks.indexOfFirst { it.key == contentKey }
        if (index < 0) return
        val old = picks[index]
        val updated = old.copy(
            offsetMs = offsetMs,
            positionMs = positionMs,
            appPackage = appPackage ?: old.appPackage,
            updatedAt = now()
        )
        picks.removeAt(index)
        picks.add(0, updated)
        val important = old.offsetMs != offsetMs || old.appPackage != updated.appPackage || index != 0
        if (important || now() - lastWriteAt >= PROGRESS_WRITE_INTERVAL_MS) persist()
    }

    @Synchronized
    fun forContent(contentKey: String): Pick? = picks.firstOrNull { it.key == contentKey }

    @Synchronized
    fun latestForApp(appPackage: String): Pick? = picks.firstOrNull { it.appPackage == appPackage }

    @Synchronized
    fun recent(limit: Int = MAX_PICKS): List<Pick> = picks.take(limit)

    private fun persist() {
        store.write(JSONArray().apply { picks.forEach { put(toJson(it)) } }.toString())
        lastWriteAt = now()
    }

    private fun load(): List<Pick> = try {
        val array = JSONArray(store.read() ?: "[]")
        (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList() // A corrupt store must never stop the app from starting
    }

    private fun toJson(pick: Pick) = JSONObject()
        .put("content", JSONObject()
            .put("title", pick.content.title)
            .putOpt("season", pick.content.season)
            .putOpt("episode", pick.content.episode)
            .putOpt("year", pick.content.year)
            .putOpt("imdbId", pick.content.imdbId))
        .put("track", JSONObject()
            .put("id", pick.track.id)
            .put("title", pick.track.title)
            .put("language", pick.track.language)
            .put("source", pick.track.source)
            .put("downloadUrl", pick.track.downloadUrl)
            .putOpt("localFilePath", pick.track.localFilePath))
        .put("offsetMs", pick.offsetMs)
        .put("positionMs", pick.positionMs)
        .putOpt("appPackage", pick.appPackage)
        .put("updatedAt", pick.updatedAt)

    private fun fromJson(json: JSONObject): Pick? {
        val c = json.optJSONObject("content") ?: return null
        val t = json.optJSONObject("track") ?: return null
        return Pick(
            content = ContentMetadata(
                title = c.getString("title"),
                season = c.optIntOrNull("season"),
                episode = c.optIntOrNull("episode"),
                year = c.optIntOrNull("year"),
                imdbId = c.optString("imdbId").ifEmpty { null }
            ),
            track = SubtitleTrack(
                id = t.getString("id"),
                title = t.getString("title"),
                language = t.optString("language"),
                source = t.optString("source"),
                downloadUrl = t.optString("downloadUrl"),
                localFilePath = t.optString("localFilePath").ifEmpty { null }
            ),
            offsetMs = json.optLong("offsetMs"),
            positionMs = json.optLong("positionMs"),
            appPackage = json.optString("appPackage").ifEmpty { null },
            updatedAt = json.optLong("updatedAt")
        )
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null

    companion object {
        const val MAX_PICKS = 10
        const val PROGRESS_WRITE_INTERVAL_MS = 30_000L
    }
}
