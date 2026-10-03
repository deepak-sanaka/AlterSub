package com.altersub.core.session

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickMemoryTest {

    private class StringStore : PickMemory.Store {
        var json: String? = null
        var writes = 0
        override fun read() = json
        override fun write(json: String) {
            this.json = json
            writes++
        }
    }

    private var time = 1_000_000L
    private val store = StringStore()
    private fun memory() = PickMemory(store) { time }

    private val film = ContentMetadata(title = "Inception", year = 2010, imdbId = "tt1375666")
    private val episode = ContentMetadata(title = "Dark", season = 1, episode = 2)
    private fun track(id: String, file: String? = null) =
        SubtitleTrack(id = id, title = "$id [eng]", language = "eng", source = "Community", downloadUrl = "https://s/$id", localFilePath = file)

    @Test
    fun testPicksSurviveARestart() {
        memory().remember(film, track("t1"), offsetMs = -10_750, positionMs = 600_000, appPackage = "com.netflix.ninja")
        memory().remember(episode, track("up", file = "/cache/uploads/x.srt"), offsetMs = 0, positionMs = 5_000, appPackage = null)

        val restarted = memory()

        val pick = restarted.forContent(film.contentKey)!!
        assertEquals(film, pick.content)
        assertEquals("t1", pick.track.id)
        assertEquals(-10_750L, pick.offsetMs)
        assertEquals(600_000L, pick.positionMs)
        assertEquals("com.netflix.ninja", pick.appPackage)

        val upload = restarted.forContent(episode.contentKey)!!
        assertEquals(1, upload.content.season)
        assertEquals("/cache/uploads/x.srt", upload.track.localFilePath)
        assertEquals(listOf(episode.contentKey, film.contentKey), restarted.recent().map { it.key })
    }

    @Test
    fun testRememberingAgainReplacesAndMovesToFront() {
        val memory = memory()
        memory.remember(film, track("t1"), 0, 0, "com.netflix.ninja")
        memory.remember(episode, track("e1"), 0, 0, "com.netflix.ninja")
        memory.remember(film, track("t2"), 500, 0, null)

        assertEquals(listOf(film.contentKey, episode.contentKey), memory.recent().map { it.key })
        assertEquals("t2", memory.forContent(film.contentKey)!!.track.id)
        // The app it played in is kept when the new pick doesn't know it
        assertEquals("com.netflix.ninja", memory.forContent(film.contentKey)!!.appPackage)
        assertEquals(film.contentKey, memory.latestForApp("com.netflix.ninja")!!.key)
    }

    @Test
    fun testOnlyTheMostRecentPicksAreKept() {
        val memory = memory()
        repeat(PickMemory.MAX_PICKS + 3) { memory.remember(ContentMetadata(title = "Film $it"), track("t$it"), 0, 0, null) }

        assertEquals(PickMemory.MAX_PICKS, memory.recent().size)
        assertEquals("film ${PickMemory.MAX_PICKS + 2}||", memory.recent().first().key)
        assertNull(memory.forContent("film 0||"))
    }

    @Test
    fun testProgressWritesAreThrottledButOffsetChangesAreNot() {
        val memory = memory()
        memory.remember(film, track("t1"), 0, 0, null)
        val afterRemember = store.writes

        time += 1_000
        memory.updateProgress(film.contentKey, offsetMs = 0, positionMs = 61_000)
        assertEquals(afterRemember, store.writes) // Position only, written recently: kept in memory
        assertEquals(61_000L, memory.forContent(film.contentKey)!!.positionMs)

        memory.updateProgress(film.contentKey, offsetMs = -250, positionMs = 62_000)
        assertEquals(afterRemember + 1, store.writes) // Offset change: saved at once

        time += PickMemory.PROGRESS_WRITE_INTERVAL_MS
        memory.updateProgress(film.contentKey, offsetMs = -250, positionMs = 92_000, appPackage = null)
        assertEquals(afterRemember + 2, store.writes)
        assertEquals(92_000L, memory().forContent(film.contentKey)!!.positionMs)
    }

    @Test
    fun testCorruptStoreStartsEmpty() {
        store.json = "{not json"
        assertEquals(0, memory().recent().size)
    }
}
