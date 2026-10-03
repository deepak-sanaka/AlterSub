package com.altersub

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.parser.SrtParser
import com.altersub.core.parser.SubtitleIndex
import com.altersub.detection.DetectionArbiter
import com.altersub.detection.DetectionSource
import com.altersub.provider.CompositeSubtitleProvider
import com.altersub.server.WebRemoteServer
import com.altersub.service.SubtitleOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class AlterSubApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock = SubtitleClock()
    val compositeProvider = CompositeSubtitleProvider()

    private val _currentContent = MutableStateFlow<ContentMetadata?>(null)
    val currentContent: StateFlow<ContentMetadata?> = _currentContent.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val availableTracks: StateFlow<List<SubtitleTrack>> = _availableTracks.asStateFlow()

    private val _activeTrack = MutableStateFlow<SubtitleTrack?>(null)
    val activeTrack: StateFlow<SubtitleTrack?> = _activeTrack.asStateFlow()

    private val _subtitleIndex = MutableStateFlow<SubtitleIndex?>(null)
    val subtitleIndex: StateFlow<SubtitleIndex?> = _subtitleIndex.asStateFlow()

    private var webRemoteServer: WebRemoteServer? = null

    // Guards the arbiter, the jobs below, and every content/track state transition.
    // Detections arrive concurrently from the main thread, web server threads and IO coroutines.
    private val detectionLock = Any()
    private val arbiter = DetectionArbiter()
    private var searchJob: Job? = null
    private var activationJob: Job? = null

    val acceptsScreenDetection: Boolean get() = arbiter.acceptsScreenDetection

    private val _overlayRunning = MutableStateFlow(false)
    val overlayRunning: StateFlow<Boolean> = _overlayRunning.asStateFlow()

    // Why subtitles can't be displayed right now (shown on the phone remote), or null when the overlay is fine
    private val _overlayError = MutableStateFlow<String?>(null)
    val overlayError: StateFlow<String?> = _overlayError.asStateFlow()

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

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Start embedded web server for mobile companion remote
        try {
            webRemoteServer = WebRemoteServer(this, 8080)
            webRemoteServer?.start()
            Log.i("AlterSubApp", "Companion Web Remote started on port 8080")
        } catch (e: Exception) {
            Log.e("AlterSubApp", "Failed to start Companion Web Remote: ${e.message}")
        }
    }

    fun onContentDetected(metadata: ContentMetadata, source: DetectionSource) {
        synchronized(detectionLock) {
            if (!arbiter.accept(metadata, source, _currentContent.value)) return

            searchJob?.cancel()
            activationJob?.cancel()

            // Never leave the previous title's subtitles running over the new one
            _currentContent.value = metadata
            _availableTracks.value = emptyList()
            _activeTrack.value = null
            _subtitleIndex.value = null
            Log.i("AlterSubApp", "New content detected via $source: ${metadata.getDisplayName()}")

            searchJob = appScope.launch {
                val tracks = compositeProvider.searchAll(metadata, "en")

                synchronized(detectionLock) {
                    // Providers block on network I/O, so a newer detection may have replaced this one meanwhile
                    if (!isActive || _currentContent.value !== metadata) return@launch

                    // Uploads made while the search was running aren't in its results
                    val merged = (compositeProvider.localTracksFor(metadata) + tracks).distinctBy { it.id }
                    _availableTracks.value = merged

                    val userAlreadyChose = _activeTrack.value != null || activationJob?.isActive == true
                    if (!userAlreadyChose && merged.isNotEmpty()) {
                        activateTrack(merged.first())
                    }
                }
            }
        }
    }

    fun onMediaSessionsEnded() {
        synchronized(detectionLock) {
            arbiter.onMediaSessionsEnded()
        }
        // The player is gone, so stop advancing subtitles over whatever is on screen now
        clock.pause()
    }

    /** User picked a track on the phone remote. */
    fun selectTrack(track: SubtitleTrack) {
        synchronized(detectionLock) {
            arbiter.onUserChoice()
            activateTrack(track)
        }
    }

    fun loadDirectSrt(file: File, displayName: String) {
        synchronized(detectionLock) {
            val track = compositeProvider.addLocalTrack(file, displayName, _currentContent.value)
            _availableTracks.value = listOf(track) + _availableTracks.value
            arbiter.onUserChoice()
            activateTrack(track)
        }
    }

    // Caller must hold detectionLock. Replaces any in-flight activation so a slow download can't win over a later choice.
    private fun activateTrack(track: SubtitleTrack) {
        activationJob?.cancel()
        val content = _currentContent.value

        activationJob = appScope.launch {
            try {
                val subDir = File(cacheDir, "subtitles")
                val srtFile = compositeProvider.downloadTrack(track, subDir)
                if (srtFile == null || !srtFile.exists()) return@launch

                val cues = FileInputStream(srtFile).use { SrtParser.parse(it) }

                synchronized(detectionLock) {
                    if (!isActive || _currentContent.value !== content) return@launch
                    _subtitleIndex.value = SubtitleIndex(cues)
                    _activeTrack.value = track
                }
                Log.i("AlterSubApp", "Activated track: ${track.title} with ${cues.size} cues")

                // Ensure overlay service is running
                startOverlayService(this@AlterSubApp)
            } catch (e: Exception) {
                Log.e("AlterSubApp", "Error activating track: ${e.message}")
            }
        }
    }

    fun setTestSubtitleIndex(index: SubtitleIndex) {
        _subtitleIndex.value = index
    }

    companion object {
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
