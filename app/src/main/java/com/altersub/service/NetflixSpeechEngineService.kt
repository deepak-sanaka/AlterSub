package com.altersub.service

import android.os.Bundle
import android.os.Binder
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.altersub.AlterSubApp
import com.altersub.detection.DiagLog
import com.altersub.detection.NetflixSpeechDetector
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Receives Netflix title strings; other apps always use the original engine's normal speech. */
class NetflixSpeechEngineService : TextToSpeechService() {
    private val ready = CountDownLatch(1)
    private var backend: TextToSpeech? = null
    @Volatile private var initialized = false
    @Volatile private var transfer: Transfer? = null
    @Volatile private var voiceCache: List<Voice>? = null
    private var sequence = 0L

    override fun onCreate() {
        super.onCreate()
        File(cacheDir, "speech-forward.wav").delete()
        val engine = SpeechEngineSettings.backend(this) ?: run { ready.countDown(); return }
        // An explicit, installed non-AlterSub engine prevents forwarding back into ourselves.
        backend = TextToSpeech(this, { status ->
            initialized = status == TextToSpeech.SUCCESS
            ready.countDown()
        }, engine)
        backend?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onBeginSynthesis(id: String, rate: Int, format: Int, channels: Int) {
                transfer?.takeIf { it.id == id }?.begin(rate, format, channels)
            }
            override fun onAudioAvailable(id: String, audio: ByteArray) {
                transfer?.takeIf { it.id == id }?.audio(audio)
            }
            override fun onDone(id: String) { transfer?.takeIf { it.id == id }?.finish(true) }
            override fun onError(id: String) { transfer?.takeIf { it.id == id }?.finish(false) }
            override fun onStop(id: String, interrupted: Boolean) { transfer?.takeIf { it.id == id }?.cancel() }
        })
    }

    private fun engine(): TextToSpeech? {
        // TextToSpeechService.onCreate() calls onLoadLanguage before our backend exists.
        // Waiting there would block the main thread that must bind and initialize that backend.
        if (backend == null) return null
        return if (ready.await(15, TimeUnit.SECONDS) && initialized) backend else null
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onGetLanguage(): Array<String> = Locale.getDefault().let {
        arrayOf(it.isO3Language, it.isO3Country, it.variant)
    }
    override fun onIsLanguageAvailable(language: String, country: String, variant: String): Int =
        if (ownClient()) TextToSpeech.LANG_NOT_SUPPORTED
        else engine()?.isLanguageAvailable(locale(language, country, variant)) ?: TextToSpeech.LANG_NOT_SUPPORTED
    override fun onLoadLanguage(language: String, country: String, variant: String): Int =
        engine()?.setLanguage(locale(language, country, variant)) ?: TextToSpeech.LANG_NOT_SUPPORTED
    // A remote getVoices() call is expensive on low-RAM TVs; reuse the engine's immutable catalog.
    private fun voices(): List<Voice> = voiceCache ?: synchronized(ready) {
        voiceCache ?: engine()?.voices?.toList()?.also { voiceCache = it }.orEmpty()
    }
    // Android may fall back to the selected default if the explicit backend fails to bind.
    // Reject that backend's queries as well as its synthesis, so it cannot recurse during initialization.
    private fun ownClient(): Boolean = Binder.getCallingUid() == android.os.Process.myUid()
    override fun onGetVoices(): List<Voice> = if (ownClient()) emptyList() else voices()
    override fun onIsValidVoiceName(voiceName: String): Int =
        if (!ownClient() && voices().any { it.name == voiceName }) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName: String): Int {
        val tts = engine() ?: return TextToSpeech.ERROR
        val voice = voices().firstOrNull { it.name == voiceName } ?: return TextToSpeech.ERROR
        return tts.setVoice(voice)
    }
    override fun onGetDefaultVoiceNameFor(language: String, country: String, variant: String): String? {
        if (ownClient()) return null
        val tts = engine() ?: return null
        if (tts.setLanguage(locale(language, country, variant)) < 0) return null
        return tts.voice?.name
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        // If a failed backend binding falls back to the selected default (us), fail instead of recursing.
        if (request.callerUid == android.os.Process.myUid()) { callback.error(); return }
        if (SpeechEngineSettings.isSelected(this) &&
            packageManager.getPackagesForUid(request.callerUid)?.contains(NetflixSpeechDetector.PACKAGE) == true) {
            val text = request.charSequenceText.toString()
            if (text.length <= 1024) {
                DiagLog.d { "Netflix speech: \"$text\"" }
                AlterSubApp.instance.onNetflixSpeech(text)
            }
            if (SpeechEngineSettings.muteNetflix(this)) {
                SilentSpeech.complete(callback)
                return
            }
        }
        val tts = engine() ?: run { callback.error(); return }
        val voice = voices().firstOrNull { it.name == request.voiceName }
        if (voice != null) tts.setVoice(voice)
        else tts.setLanguage(locale(request.language, request.country, request.variant))
        tts.setSpeechRate(request.speechRate / 100f)
        tts.setPitch(request.pitch / 100f)
        val output = File(cacheDir, "speech-forward.wav")
        val current = Transfer("speech-${++sequence}", callback)
        transfer = current
        try {
            val parameters = Bundle(request.params)
            // Playback volume/pan are applied by the outer Android synthesis callback.
            parameters.remove(TextToSpeech.Engine.KEY_PARAM_VOLUME)
            parameters.remove(TextToSpeech.Engine.KEY_PARAM_PAN)
            if (tts.synthesizeToFile(request.charSequenceText, parameters, output, current.id) != TextToSpeech.SUCCESS) {
                current.finish(false)
            }
            val timeoutSeconds = (request.charSequenceText.length / 8L).coerceIn(30, 180)
            if (!current.done.await(timeoutSeconds, TimeUnit.SECONDS)) {
                current.finish(false)
                tts.stop()
            }
            DiagLog.d { "Speech forwarded: ${current.bytes} PCM bytes (success=${current.success})" }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            current.cancel()
            tts.stop()
        } catch (_: Exception) {
            current.finish(false)
        } finally {
            if (transfer === current) transfer = null
            output.delete()
        }
    }

    override fun onStop() { transfer?.cancel(); backend?.stop() }
    override fun onDestroy() {
        transfer?.cancel()
        backend?.shutdown()
        backend = null
        voiceCache = null
        super.onDestroy()
    }

    private class Transfer(val id: String, private val callback: SynthesisCallback) {
        val done = CountDownLatch(1)
        var bytes = 0L
            private set
        var success = false
            private set
        private var started = false
        private var finished = false
        @Synchronized fun begin(rate: Int, format: Int, channels: Int) {
            if (!finished) {
                started = callback.start(rate, format, channels) == TextToSpeech.SUCCESS
                if (!started) finish(false)
            }
        }
        @Synchronized fun audio(audio: ByteArray) {
            if (!started || finished) return
            var offset = 0
            while (offset < audio.size && !finished) {
                val length = minOf(callback.maxBufferSize, audio.size - offset)
                if (length <= 0 || callback.audioAvailable(audio, offset, length) != TextToSpeech.SUCCESS) {
                    finish(false)
                    return
                }
                bytes += length
                offset += length
            }
        }
        @Synchronized fun finish(ok: Boolean) {
            if (finished) return
            finished = true
            success = ok && started
            if (success) callback.done() else callback.error()
            done.countDown()
        }
        @Synchronized fun cancel() {
            if (finished) return
            finished = true
            done.countDown()
        }
    }

    private fun locale(language: String, country: String, variant: String): Locale = Locale(
        languages[language] ?: language, countries[country] ?: country, variant
    )
    companion object {
        private val languages by lazy { Locale.getISOLanguages().associateBy { Locale(it).isO3Language } }
        private val countries by lazy { Locale.getISOCountries().associateBy { Locale("", it).isO3Country } }
    }
}
