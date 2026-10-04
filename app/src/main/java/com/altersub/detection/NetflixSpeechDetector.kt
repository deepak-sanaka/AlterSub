package com.altersub.detection

import com.altersub.core.model.ContentMetadata

/** English announcements verified on Netflix TV 8.3.11. No polling or retained utterance history. */
class NetflixSpeechDetector(private val now: () -> Long) {
    data class Confirmed(val metadata: ContentMetadata, val generation: Long)
    private var candidate: ContentMetadata? = null
    private var seenAt = 0L
    private var playAt: Long? = null
    private var generation = 0L

    @Synchronized fun onSpeech(text: String) {
        val value = text.trim()
        when {
            value.startsWith(DETAILS, ignoreCase = true) -> {
                generation++
                candidate = value.substring(DETAILS.length).takeIf { it.length in 2..200 }
                    ?.let { TitleSanitizer.sanitize(it, PACKAGE) }
                seenAt = now()
                playAt = null
            }
            value.equals("Playing", ignoreCase = true) -> {
                if (candidate != null) {
                    if (now() - seenAt in 0..TITLE_TTL_MS) playAt = now() else clear()
                }
            }
            value.equals("Paused", ignoreCase = true) -> playAt = null
            value.equals("Choose a Profile", ignoreCase = true) ||
                (value.startsWith("On ", ignoreCase = true) && value.contains("screen", ignoreCase = true)) -> clear()
            candidate != null && !isPageControl(value) &&
                normalizedTitle(value) != normalizedTitle(candidate!!.title) -> clear()
        }
    }

    @Synchronized fun onPlayback(packageName: String, playing: Boolean): Confirmed? {
        if (packageName != PACKAGE) {
            if (playing) clear()
            return null
        }
        val armedAt = playAt ?: return null
        if (now() - armedAt !in 0..PLAY_TTL_MS || now() - seenAt !in 0..TITLE_TTL_MS) {
            clear()
            return null
        }
        if (!playing) return null
        val result = candidate?.let { Confirmed(it, generation) }
        candidate = null
        playAt = null
        return result
    }

    @Synchronized fun isCurrent(token: Long): Boolean = token == generation
    @Synchronized fun onPlaybackEnded() {
        if (playAt != null || candidate == null) clear()
    }
    @Synchronized fun clear() { candidate = null; playAt = null; generation++ }

    companion object {
        const val PACKAGE = "com.netflix.ninja"
        private const val DETAILS = "On the details screen for "
        private const val TITLE_TTL_MS = 120_000L
        private const val PLAY_TTL_MS = 20_000L
        private val pageControls = setOf("play", "resume", "play from beginning", "more info", "more like this",
            "my list", "rate", "audio and subtitles", "audio & subtitles", "trailer", "back", "button")
        private val status = Regex("(?i)^(?:(?:item )?\\d+ of \\d+.*|\\d+% complete|.* seconds remaining)$")
        private fun isPageControl(value: String) = value.trimEnd('.').lowercase() in pageControls || status.matches(value)
        private fun normalizedTitle(value: String) = com.altersub.core.session.TitleMatching.normalize(value)
    }
}
