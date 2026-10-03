package com.altersub.detection

import com.altersub.core.model.ContentMetadata

enum class DetectionSource { ACCESSIBILITY, MEDIA_SESSION, MANUAL }

/**
 * Decides which detections may replace the current content.
 * Media sessions are authoritative, screen scraping is only a fallback, and a user's manual
 * choice holds until the media session itself moves on to a new title (e.g. the next episode).
 * Not thread-safe: callers must synchronize.
 */
class DetectionArbiter {

    @Volatile
    private var currentSource: DetectionSource? = null
    private var lastMediaSessionKey: String? = null

    @Volatile
    var isMediaSessionActive: Boolean = false
        private set

    /** False while a scraped title would be ignored anyway, so the inspector can skip the tree walk. */
    val acceptsScreenDetection: Boolean
        get() = !isMediaSessionActive && currentSource != DetectionSource.MANUAL

    /**
     * Returns true if [detected] should replace [current] and trigger a new subtitle search.
     */
    fun accept(detected: ContentMetadata, source: DetectionSource, current: ContentMetadata?): Boolean {
        val isSameContent = current?.contentKey == detected.contentKey

        when (source) {
            DetectionSource.ACCESSIBILITY -> {
                if (!acceptsScreenDetection || isSameContent) return false
            }
            DetectionSource.MEDIA_SESSION -> {
                isMediaSessionActive = true
                val isNewSessionTitle = detected.contentKey != lastMediaSessionKey
                lastMediaSessionKey = detected.contentKey

                // Sessions re-report the same metadata on every reconnect; only a real title change overrides the user
                if (currentSource == DetectionSource.MANUAL && !isNewSessionTitle) return false
                if (isSameContent) {
                    currentSource = source
                    return false
                }
            }
            DetectionSource.MANUAL -> Unit
        }

        currentSource = source
        return true
    }

    /** The user picked or uploaded a track; screen scraping must not swap the content out from under them. */
    fun onUserChoice() {
        if (currentSource != DetectionSource.MEDIA_SESSION) {
            currentSource = DetectionSource.MANUAL
        }
    }

    fun onMediaSessionsEnded() {
        isMediaSessionActive = false
        lastMediaSessionKey = null
    }
}
