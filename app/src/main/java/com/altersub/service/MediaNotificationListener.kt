package com.altersub.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.altersub.AlterSubApp
import com.altersub.core.clock.SubtitleClock
import com.altersub.detection.AppPackageFilter
import com.altersub.detection.DetectionSource
import com.altersub.detection.DiagLog
import com.altersub.detection.TitleSanitizer

class MediaNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = mutableListOf<Pair<MediaController, SessionCallback>>()

    /** One callback per session, so diagnostics can say which app published what. */
    private inner class SessionCallback(private val pkg: String) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state == null) return
            DiagLog.d {
                "[$pkg] playback state=${state.state} position=${state.position} speed=${state.playbackSpeed} " +
                    "updated ${SystemClock.elapsedRealtime() - state.lastPositionUpdateTime} ms ago"
            }
            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            val speed = state.playbackSpeed
            val clock = AlterSubApp.instance.clock

            // Some players publish play/pause without a position; keep our own estimate in that case
            if (state.position == PlaybackState.PLAYBACK_POSITION_UNKNOWN) {
                Log.d("MediaSessionListener", "PlaybackState changed: playing=$isPlaying, pos=unknown")
                if (isPlaying) clock.play() else clock.pause()
                return
            }

            val position = SubtitleClock.extrapolatePosition(
                positionMs = state.position,
                lastUpdateRealtimeMs = state.lastPositionUpdateTime,
                nowRealtimeMs = SystemClock.elapsedRealtime(),
                speed = speed,
                playing = isPlaying
            )

            Log.d("MediaSessionListener", "PlaybackState changed: playing=$isPlaying, pos=$position (reported ${state.position})")
            clock.syncWithExternalPosition(position, isPlaying, speed)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (metadata == null) return
            DiagLog.d { "[$pkg] metadata ${describe(metadata)}" }
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: return

            Log.d("MediaSessionListener", "Metadata changed: title=$title")
            val sanitized = TitleSanitizer.sanitize(title)
            if (sanitized != null) {
                AlterSubApp.instance.onContentDetected(sanitized, DetectionSource.MEDIA_SESSION)
            } else {
                DiagLog.d { "[$pkg] title rejected by TitleSanitizer: \"$title\"" }
            }
        }
    }

    /** Every text and number the session publishes (artwork skipped), e.g. {TITLE=..., DURATION=...}. */
    private fun describe(metadata: MediaMetadata): String = metadata.keySet().sorted().mapNotNull { key ->
        val value = metadata.getText(key)?.toString()
            ?: metadata.getLong(key).takeIf { it != 0L }?.toString()
            ?: return@mapNotNull null
        key.removePrefix("android.media.metadata.") + "=" + value
    }.joinToString(prefix = "{", postfix = "}")

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("MediaSessionListener", "NotificationListener connected")

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val componentName = ComponentName(this, MediaNotificationListener::class.java)

        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener({ controllers ->
                updateControllers(controllers)
            }, componentName)

            val initialSessions = mediaSessionManager?.getActiveSessions(componentName)
            updateControllers(initialSessions)
        } catch (e: Exception) {
            Log.e("MediaSessionListener", "Error attaching session listener: ${e.message}")
        }
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        val hadTargetSessions = activeControllers.isNotEmpty()
        for ((controller, callback) in activeControllers) {
            controller.unregisterCallback(callback)
        }
        activeControllers.clear()

        DiagLog.d {
            "active sessions: " + controllers.orEmpty().joinToString { c ->
                val pkg = c.packageName.orEmpty()
                if (AppPackageFilter.isTargetApp(pkg)) "$pkg (target)" else pkg
            }.ifEmpty { "none" }
        }

        for (controller in controllers.orEmpty()) {
            val pkg = controller.packageName ?: ""
            if (AppPackageFilter.isTargetApp(pkg)) {
                val callback = SessionCallback(pkg)
                activeControllers.add(controller to callback)
                controller.registerCallback(callback)

                // Inspect initial state
                controller.playbackState?.let { callback.onPlaybackStateChanged(it) }
                controller.metadata?.let { callback.onMetadataChanged(it) }
            }
        }

        // Only on the transition: unrelated session changes must not pause a clock the user started manually
        if (hadTargetSessions && activeControllers.isEmpty()) {
            AlterSubApp.instance.onMediaSessionsEnded()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for ((controller, callback) in activeControllers) {
            controller.unregisterCallback(callback)
        }
        activeControllers.clear()
    }
}
