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
import com.altersub.server.RemoteAuth
import com.altersub.server.RemoteController
import com.altersub.server.WebRemoteServer
import com.altersub.service.MediaSessionPoller
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

    /** Play-state fallback for low-RAM TVs, active once the DUMP permission is granted over ADB. */
    val mediaSessionPoller by lazy { MediaSessionPoller(this, appScope) }

    /** Phone remote pairing (one phone). Its token is persisted, so the phone stays paired across restarts. */
    val remoteAuth by lazy {
        val prefs = getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE)
        RemoteAuth(
            // Older builds stored several comma-separated tokens; only the newest phone stays paired
            savedToken = prefs.getString(KEY_TOKENS, null)?.split(',')?.lastOrNull { it.isNotEmpty() },
            onTokenChanged = { token -> prefs.edit().putString(KEY_TOKENS, token.orEmpty()).apply() }
        )
    }

    /**
     * The phone page's UI font, served from the TV so the page needs no internet. Read per request and not
     * kept: browsers cache it for a week, so holding ~235 KB in this long-lived process would be wasted RAM.
     */
    private fun webFont(weight: String): ByteArray? {
        val resId = when (weight) {
            "regular" -> R.font.app_sans_regular
            "medium" -> R.font.app_sans_medium
            "bold" -> R.font.app_sans_bold
            else -> return null
        }
        return resources.openRawResource(resId).use { it.readBytes() }
    }

    sealed interface WebRemoteState {
        object Off : WebRemoteState
        data class Running(val port: Int) : WebRemoteState
        /** None of [WebRemoteServer.PORTS] could be bound. */
        object Failed : WebRemoteState
    }

    private var webRemoteServer: WebRemoteServer? = null
    private val _webRemoteState = MutableStateFlow<WebRemoteState>(WebRemoteState.Off)
    val webRemoteState: StateFlow<WebRemoteState> = _webRemoteState.asStateFlow()

    /** Switches the phone remote on or off (from the TV screen) and remembers the choice. */
    fun setWebRemoteEnabled(enabled: Boolean) {
        getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_REMOTE_ENABLED, enabled).apply()
        if (enabled) startWebRemote() else stopWebRemote()
    }

    private fun startWebRemote() {
        if (webRemoteServer != null) return

        val server = WebRemoteServer.startOnFirstFreePort(WebRemoteServer.PORTS) { port ->
            WebRemoteServer(this, remoteAuth, File(cacheDir, "uploads"), port, ::webFont)
        }
        webRemoteServer = server
        if (server != null) {
            _webRemoteState.value = WebRemoteState.Running(server.listeningPort)
            Log.i("AlterSubApp", "Companion Web Remote started on port ${server.listeningPort}")
        } else {
            _webRemoteState.value = WebRemoteState.Failed
            Log.e("AlterSubApp", "Companion Web Remote could not bind any of ${WebRemoteServer.PORTS}")
        }
    }

    private fun stopWebRemote() {
        webRemoteServer?.stop()
        webRemoteServer = null
        _webRemoteState.value = WebRemoteState.Off
    }

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

        // Embedded web server for the phone companion remote, unless it was switched off on the TV
        mediaSessionPoller.startIfPermitted()

        if (getSharedPreferences(REMOTE_PREFS, MODE_PRIVATE).getBoolean(KEY_REMOTE_ENABLED, true)) {
            startWebRemote()
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
        private const val REMOTE_PREFS = "web_remote"
        private const val KEY_TOKENS = "pairedTokens"
        private const val KEY_REMOTE_ENABLED = "enabled"

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
