package com.altersub

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleStyle
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.parser.SubtitleIndex
import com.altersub.core.session.SubtitleSession
import com.altersub.detection.DetectionSource
import com.altersub.provider.CompositeSubtitleProvider
import com.altersub.server.RemoteController
import com.altersub.server.WebRemoteServer
import com.altersub.service.SubtitleOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import java.io.File

class AlterSubApp : Application(), RemoteController {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val clock = SubtitleClock()
    val compositeProvider = CompositeSubtitleProvider()

    // Created lazily: cacheDir is only available once the Application is attached
    private val session by lazy {
        SubtitleSession(compositeProvider, clock, appScope, File(cacheDir, "subtitles")) {
            startOverlayService(this)
        }
    }

    override val currentContent: StateFlow<ContentMetadata?> get() = session.currentContent
    override val availableTracks: StateFlow<List<SubtitleTrack>> get() = session.availableTracks
    override val activeTrack: StateFlow<SubtitleTrack?> get() = session.activeTrack
    val subtitleIndex: StateFlow<SubtitleIndex?> get() = session.subtitleIndex
    val acceptsScreenDetection: Boolean get() = session.acceptsScreenDetection

    private var webRemoteServer: WebRemoteServer? = null

    private val _overlayRunning = MutableStateFlow(false)
    override val overlayRunning: StateFlow<Boolean> = _overlayRunning.asStateFlow()

    // Why subtitles can't be displayed right now (shown on the phone remote), or null when the overlay is fine
    private val _overlayError = MutableStateFlow<String?>(null)
    override val overlayError: StateFlow<String?> = _overlayError.asStateFlow()

    fun onOverlayStarted() {
        _overlayRunning.value = true
        _overlayError.value = null
    }

    fun onOverlayStopped() {
        _overlayRunning.value = false
    }

    fun onOverlayFailed(reason: String) {
        _overlayRunning.value = false
        _overlayError.value = reason
        Log.e("AlterSubApp", "Subtitle overlay unavailable: $reason")
    }

    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    override val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    /** Applies [change] to the subtitle style and persists it so it survives restarts. */
    override fun updateSubtitleStyle(change: (SubtitleStyle) -> SubtitleStyle) {
        val style = _subtitleStyle.updateAndGet(change)
        getSharedPreferences(STYLE_PREFS, MODE_PRIVATE).edit()
            .putFloat(KEY_TEXT_SIZE, style.textSizeSp)
            .putString(KEY_COLOR, style.color)
            .putFloat(KEY_POSITION, style.verticalPosition)
            .apply()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val stylePrefs = getSharedPreferences(STYLE_PREFS, MODE_PRIVATE)
        _subtitleStyle.value = SubtitleStyle(
            textSizeSp = stylePrefs.getFloat(KEY_TEXT_SIZE, SubtitleStyle.DEFAULT_TEXT_SIZE_SP),
            color = stylePrefs.getString(KEY_COLOR, null) ?: SubtitleStyle.DEFAULT_COLOR,
            verticalPosition = stylePrefs.getFloat(KEY_POSITION, SubtitleStyle.DEFAULT_VERTICAL_POSITION)
        )

        // Start embedded web server for mobile companion remote
        try {
            webRemoteServer = WebRemoteServer(this, File(cacheDir, "uploads"), 8080)
            webRemoteServer?.start()
            Log.i("AlterSubApp", "Companion Web Remote started on port 8080")
        } catch (e: Exception) {
            Log.e("AlterSubApp", "Failed to start Companion Web Remote: ${e.message}")
        }
    }

    override fun onContentDetected(metadata: ContentMetadata, source: DetectionSource) =
        session.onContentDetected(metadata, source)

    fun onMediaSessionsEnded() = session.onMediaSessionsEnded()

    /** User picked a track on the phone remote. */
    override fun selectTrack(track: SubtitleTrack) = session.selectTrack(track)

    override fun loadDirectSrt(file: File, displayName: String) = session.loadDirectSrt(file, displayName)

    fun setTestSubtitleIndex(index: SubtitleIndex) = session.setTestSubtitleIndex(index)

    companion object {
        private const val STYLE_PREFS = "subtitle_style"
        private const val KEY_TEXT_SIZE = "textSizeSp"
        private const val KEY_COLOR = "color"
        private const val KEY_POSITION = "verticalPosition"

        lateinit var instance: AlterSubApp
            private set

        fun startOverlayService(context: Context) {
            // Once running it stays up, so never re-issue a foreground-service start (possibly from the background)
            if (instance.overlayRunning.value) return

            try {
                context.startForegroundService(Intent(context, SubtitleOverlayService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses foreground-service starts from the background unless an exemption applies
                // (ForegroundServiceStartNotAllowedException); targetSdk 35 narrows the overlay-permission one
                instance.onOverlayFailed(
                    "Android blocked starting the subtitle overlay in the background. Open AlterSub on the TV once, then go back to your app."
                )
                Log.e("AlterSubApp", "Could not start SubtitleOverlayService: ${e.message}")
            }
        }
    }
}
