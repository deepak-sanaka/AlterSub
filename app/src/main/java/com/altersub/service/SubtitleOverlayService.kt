package com.altersub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.altersub.AlterSubApp
import com.altersub.R
import com.altersub.ui.overlay.SubtitleTextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SubtitleOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var subtitleView: SubtitleTextView? = null
    private var renderJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        attachOverlay()
        startRenderLoop()
    }

    private fun startInForeground() {
        val channelId = "altersub_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Subtitle Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the subtitle overlay active over streaming apps"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.overlay_service_title))
            .setContentText(getString(R.string.overlay_service_description))
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    private fun attachOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        subtitleView = SubtitleTextView(this)
        windowManager?.addView(subtitleView, layoutParams)
    }

    private fun startRenderLoop() {
        renderJob?.cancel()
        val app = AlterSubApp.instance
        renderJob = app.appScope.launch {
            var shownText = ""

            // Event-driven (AGENTS.md Rule 3): any clock or track change restarts the block below immediately,
            // so it only has to wake at cue boundaries, and never while paused or past the last cue
            combine(app.clock.changes, app.subtitleIndex) { _, index -> index }.collectLatest { index ->
                while (true) {
                    val currentTime = app.clock.getCurrentTimeMs()
                    val text = index?.getTextAt(currentTime) ?: ""

                    // Only touch the UI thread when the text actually changes
                    if (text != shownText) {
                        shownText = text
                        subtitleView?.post { subtitleView?.setSubtitle(text) }
                    }

                    val untilChange = index?.getTimeUntilNextChange(currentTime) ?: Long.MAX_VALUE
                    if (!app.clock.isPlaying.value || untilChange == Long.MAX_VALUE) awaitCancellation()
                    delay(app.clock.realtimeFor(untilChange))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        renderJob?.cancel()
        subtitleView?.let {
            windowManager?.removeView(it)
            subtitleView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
