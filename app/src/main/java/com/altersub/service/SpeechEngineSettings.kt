package com.altersub.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech

object SpeechEngineSettings {
    private const val PREFS = "netflix_speech"
    private const val BACKEND = "backend"
    private const val MUTE_NETFLIX = "mute_netflix"

    fun muteNetflix(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(MUTE_NETFLIX, true)

    fun setMuteNetflix(context: Context, mute: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(MUTE_NETFLIX, mute).apply()
    }

    fun isSelected(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, "tts_default_synth") == context.packageName

    /** Save the user's original voice engine before they choose AlterSub in system settings. */
    fun prepare(context: Context): String? {
        val current = Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        if (!current.isNullOrBlank() && current != context.packageName && installed(context, current)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(BACKEND, current).apply()
        }
        return backend(context)
    }

    fun backend(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(BACKEND, null)
        if (saved != null && saved != context.packageName && installed(context, saved)) return saved
        val services = context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
        val other = services.filter { it.serviceInfo.packageName != context.packageName }
        val current = Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        val choice = other.firstOrNull { it.serviceInfo.packageName == current }
            ?: other.firstOrNull { it.serviceInfo.packageName == "com.google.android.tts" }
            ?: other.firstOrNull { it.serviceInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 }
            ?: other.firstOrNull()
        return choice?.serviceInfo?.packageName?.also {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(BACKEND, it).apply()
        }
    }

    private fun installed(context: Context, pkg: String): Boolean = context.packageManager
        .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(pkg), 0).isNotEmpty()
}
