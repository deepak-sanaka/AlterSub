package com.altersub.detection

import org.junit.Assert.*
import org.junit.Test

class NetflixSpeechDetectorTest {
    private var time = 0L
    private val detector = NetflixSpeechDetector { time }
    private fun details(title: String = "Under the Open Sky") = detector.onSpeech("On the details screen for $title")
    @Test fun `browsing and play button do not trigger detection`() {
        details()
        detector.onSpeech("Play")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
    }
    @Test fun `details plus playing requires media playback and emits once`() {
        details(); detector.onSpeech("Playing")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, false))
        assertEquals("Under the Open Sky", detector.onPlayback(NetflixSpeechDetector.PACKAGE, true)?.metadata?.title)
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
        detector.onSpeech("Playing")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
    }
    @Test fun `generic card title and controls are never candidates`() {
        detector.onSpeech("Under the Open Sky"); detector.onSpeech("Playing")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
        details("Play"); detector.onSpeech("Playing")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
    }
    @Test fun `expired details and delayed playback are discarded`() {
        details(); time = 120001; detector.onSpeech("Playing")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
        details(); detector.onSpeech("Playing"); time += 20001
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
    }
    @Test fun `switching app or profile invalidates pending title`() {
        details(); detector.onSpeech("Playing")
        detector.onPlayback("com.other.player", true)
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
        details(); detector.onSpeech("Choose a Profile"); detector.onSpeech("Playing")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
    }
    @Test fun `new details invalidate old catalog result`() {
        details(); detector.onSpeech("Playing")
        val old = detector.onPlayback(NetflixSpeechDetector.PACKAGE, true)!!
        assertTrue(detector.isCurrent(old.generation))
        details("Other film")
        assertFalse(detector.isCurrent(old.generation))
    }
    @Test fun `moving to a different card discards details candidate`() {
        details(); detector.onSpeech("Another film"); detector.onSpeech("Playing")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
    }
    @Test fun `controls and remaining time preserve title through playback startup`() {
        details(); detector.onSpeech("Play"); detector.onSpeech("1 of 8 options")
        detector.onSpeech("Button."); detector.onSpeech("Item 1 of 8")
        detector.onSpeech("Playing"); detector.onSpeech("2 hours, 5 minutes, 24 seconds remaining")
        val title = detector.onPlayback(NetflixSpeechDetector.PACKAGE, true)!!
        detector.onSpeech("Playing")
        assertTrue(detector.isCurrent(title.generation))
    }
    @Test fun `pause prevents an armed title from being accepted`() {
        details(); detector.onSpeech("Playing"); detector.onSpeech("Paused")
        assertNull(detector.onPlayback(NetflixSpeechDetector.PACKAGE, true))
    }
    @Test fun `details survive an inactive media poll but stopped playback invalidates a pending search`() {
        details(); detector.onPlaybackEnded(); detector.onSpeech("Playing")
        val title = detector.onPlayback(NetflixSpeechDetector.PACKAGE, true)!!
        assertEquals("Under the Open Sky", title.metadata.title)
        detector.onPlaybackEnded()
        assertFalse(detector.isCurrent(title.generation))
    }
}
