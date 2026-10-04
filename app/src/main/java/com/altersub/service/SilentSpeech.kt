package com.altersub.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.TextToSpeech

/** Completes a Netflix UI announcement with 10 ms of silence, without synthesizing or caching speech. */
internal object SilentSpeech {
    private val silence = ByteArray(320) // 160 mono samples at 16 kHz, PCM 16-bit.

    fun complete(callback: SynthesisCallback) {
        if (callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
            callback.error()
            return
        }
        val chunkSize = callback.maxBufferSize
        if (chunkSize <= 0) { callback.error(); return }
        var offset = 0
        while (offset < silence.size) {
            val length = minOf(chunkSize, silence.size - offset)
            if (callback.audioAvailable(silence, offset, length) != TextToSpeech.SUCCESS) {
                callback.error()
                return
            }
            offset += length
        }
        callback.done()
    }
}
