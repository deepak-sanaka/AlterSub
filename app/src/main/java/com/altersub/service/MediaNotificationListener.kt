package com.altersub.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import com.altersub.AlterSubApp
import com.altersub.detection.AppPackageFilter
import com.altersub.detection.TitleSanitizer

class MediaNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = mutableListOf<MediaController>()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state == null) return
            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            val position = state.position
            val speed = state.playbackSpeed

            Log.d("MediaSessionListener", "PlaybackState changed: playing=$isPlaying, pos=$position")
            AlterSubApp.instance.clock.syncWithExternalPosition(position, isPlaying, speed)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (metadata == null) return
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: return

            Log.d("MediaSessionListener", "Metadata changed: title=$title")
            val sanitized = TitleSanitizer.sanitize(title)
            if (sanitized != null) {
                AlterSubApp.instance.onContentDetected(sanitized)
            }
        }
    }

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
        for (c in activeControllers) {
            c.unregisterCallback(callback)
        }
        activeControllers.clear()

        if (controllers == null) return

        for (controller in controllers) {
            val pkg = controller.packageName ?: ""
            if (AppPackageFilter.isTargetApp(pkg)) {
                activeControllers.add(controller)
                controller.registerCallback(callback)

                // Inspect initial state
                controller.playbackState?.let { callback.onPlaybackStateChanged(it) }
                controller.metadata?.let { callback.onMetadataChanged(it) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for (c in activeControllers) {
            c.unregisterCallback(callback)
        }
        activeControllers.clear()
    }
}
