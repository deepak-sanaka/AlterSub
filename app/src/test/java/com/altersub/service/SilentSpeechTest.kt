package com.altersub.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.TextToSpeech
import org.junit.Assert.*
import org.junit.Test

class SilentSpeechTest {
    private class Callback(
        private val limit: Int = 64,
        private val rejectStart: Boolean = false,
        private val rejectAudio: Boolean = false
    ) : SynthesisCallback {
        val events = mutableListOf<String>()
        val audio = mutableListOf<Byte>()
        var format: Triple<Int, Int, Int>? = null
        override fun getMaxBufferSize() = limit
        override fun start(rate: Int, encoding: Int, channels: Int): Int {
            events += "start"
            format = Triple(rate, encoding, channels)
            return if (rejectStart) TextToSpeech.ERROR else TextToSpeech.SUCCESS
        }
        override fun audioAvailable(buffer: ByteArray, offset: Int, length: Int): Int {
            events += "audio"
            assertTrue(length in 1..limit)
            if (rejectAudio) return TextToSpeech.ERROR
            audio += buffer.slice(offset until offset + length)
            return TextToSpeech.SUCCESS
        }
        override fun done(): Int { events += "done"; return TextToSpeech.SUCCESS }
        override fun error() { events += "error" }
        override fun error(code: Int) = error()
        override fun hasStarted() = "start" in events
        override fun hasFinished() = "done" in events || "error" in events
    }

    @Test fun `silence completes a valid audio stream within callback buffer limits`() {
        val callback = Callback()
        SilentSpeech.complete(callback)
        assertEquals(Triple(16000, AudioFormat.ENCODING_PCM_16BIT, 1), callback.format)
        assertEquals(320, callback.audio.size)
        assertTrue(callback.audio.all { it == 0.toByte() })
        assertEquals(listOf("start", "audio", "audio", "audio", "audio", "audio", "done"), callback.events)
    }

    @Test fun `rejected start never sends audio or a success completion`() {
        val callback = Callback(rejectStart = true)
        SilentSpeech.complete(callback)
        assertEquals(listOf("start", "error"), callback.events)
    }

    @Test fun `rejected audio stops immediately instead of completing successfully`() {
        val callback = Callback(rejectAudio = true)
        SilentSpeech.complete(callback)
        assertEquals(listOf("start", "audio", "error"), callback.events)
    }

    @Test fun `zero buffer size fails without looping`() {
        val callback = Callback(limit = 0)
        SilentSpeech.complete(callback)
        assertEquals(listOf("start", "error"), callback.events)
    }
}
