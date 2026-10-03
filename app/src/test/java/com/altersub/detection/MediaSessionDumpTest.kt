package com.altersub.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionDumpTest {

    // Captured from a low-RAM Android 9 TV while Netflix played a film
    private val netflix = """
        MEDIA SESSION SERVICE (dumpsys media_session)

        7 sessions listeners.
        Global priority session is null
        User Records:
        Record for full_user=0
          Media button session is com.netflix.ninja/Netflix media session (userId=0)
          Sessions Stack - have 1 sessions:
            Netflix media session com.netflix.ninja/Netflix media session (userId=0)
              ownerPid=1234, ownerUid=10059, userId=0
              package=com.netflix.ninja
              launchIntent=null
              mediaButtonReceiver=null
              active=true
              flags=3
              rating type=0
              controllers: 9
              state=PlaybackState {state=3, position=1631281, buffered position=0, speed=1.0, updated=33322234, actions=1049466, custom actions=[], active item id=-1, error=null}
              audioAttrs=AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0x0 tags= bundle=null
              volumeType=1, controlType=2, max=0, current=0
              metadata:size=0, description=null
              queueTitle=null, size=0

        Audio playback (lastly played comes first)
          uid=10059 packages=com.netflix.ninja
    """.trimIndent()

    // Same TV, earlier: Hotstar playing a live match with a titled session, Prime Video idle
    private val hotstarAndPrime = """
          Sessions Stack - have 2 sessions:
            Hotstar com.hotstar/Hotstar (userId=0)
              package=in.startv.hotstar
              active=true
              state=PlaybackState {state=3, position=198373, buffered position=244000, speed=1.0, updated=30946058, actions=7340027, custom actions=[], active item id=0, error=null}
              metadata:size=5, description=India vs West Indies: 3rd ODI, null, null
            Prime com.amazon.amazonvideo.livingroom/Prime (userId=0)
              package=com.amazon.amazonvideo.livingroom
              active=false
              state=PlaybackState {state=0, position=0, buffered position=0, speed=1.0, updated=11750858, actions=1049455, custom actions=[], active item id=-1, error=null}
              metadata:size=0, description=null

        Audio playback (lastly played comes first)
    """.trimIndent()

    @Test
    fun testNetflixSessionHasPositionButNoTitle() {
        val session = MediaSessionDump.parse(netflix).single()

        assertEquals("com.netflix.ninja", session.packageName)
        assertTrue(session.active)
        assertEquals(3, session.state)
        assertEquals(1_631_281L, session.positionMs)
        assertEquals(1.0f, session.speed)
        assertEquals(33_322_234L, session.updatedRealtimeMs)
        assertNull(session.title)
    }

    @Test
    fun testSeveralSessionsAndTitles() {
        val (hotstar, prime) = MediaSessionDump.parse(hotstarAndPrime)

        assertEquals("in.startv.hotstar", hotstar.packageName)
        assertEquals("India vs West Indies: 3rd ODI", hotstar.title)
        assertEquals(198_373L, hotstar.positionMs)

        assertEquals("com.amazon.amazonvideo.livingroom", prime.packageName)
        assertFalse(prime.active)
        assertEquals(0, prime.state)
        assertNull(prime.title)
    }

    @Test
    fun testTitlesMayContainCommas() {
        val dump = """
              package=com.example.tv
              state=PlaybackState {state=2, position=5000, buffered position=0, speed=0.0, updated=100, actions=0, custom actions=[], active item id=-1, error=null}
              metadata:size=3, description=Crouching Tiger, Hidden Dragon, Season 1, null
        """.trimIndent()

        assertEquals("Crouching Tiger, Hidden Dragon", MediaSessionDump.parse(dump).single().title)
    }

    @Test
    fun testSessionsWithoutStateAndEmptyDumps() {
        val dump = """
              package=com.example.music
              active=true
              state=null
              metadata:size=0, description=null
        """.trimIndent()

        val session = MediaSessionDump.parse(dump).single()
        assertNull(session.state)
        assertEquals(-1L, session.positionMs)
        assertTrue(MediaSessionDump.parse("MEDIA SESSION SERVICE (dumpsys media_session)\n\nUser Records:\n").isEmpty())
    }
}
