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
import com.altersub.provider.CompositeSubtitleProvider
import com.altersub.server.WebRemoteServer
import com.altersub.service.SubtitleOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun onContentDetected(metadata: ContentMetadata) {
        val current = _currentContent.value
        if (current?.title == metadata.title &&
            current.season == metadata.season &&
            current.episode == metadata.episode
        ) {
            return // Same content already loaded
        }

        _currentContent.value = metadata
        Log.i("AlterSubApp", "New content detected: ${metadata.getDisplayName()}")

        // Launch background search across all providers
        appScope.launch {
            val tracks = compositeProvider.searchAll(metadata, "en")
            _availableTracks.value = tracks

            if (tracks.isNotEmpty()) {
                // Automatically activate top result
                loadAndActivateTrack(tracks.first())
            }
        }
    }

    fun loadAndActivateTrack(track: SubtitleTrack) {
        appScope.launch {
            try {
                val subDir = File(cacheDir, "subtitles")
                val srtFile = compositeProvider.downloadTrack(track, subDir)
                if (srtFile != null && srtFile.exists()) {
                    FileInputStream(srtFile).use { input ->
                        val cues = SrtParser.parse(input)
                        val index = SubtitleIndex(cues)
                        _subtitleIndex.value = index
                        _activeTrack.value = track
                        Log.i("AlterSubApp", "Activated track: ${track.title} with ${cues.size} cues")

                        // Ensure overlay service is running
                        startOverlayService(this@AlterSubApp)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlterSubApp", "Error activating track: ${e.message}")
            }
        }
    }

    fun loadDirectSrt(file: File, displayName: String) {
        val track = compositeProvider.addLocalTrack(file, displayName)
        loadAndActivateTrack(track)
    }

    fun setTestSubtitleIndex(index: SubtitleIndex) {
        _subtitleIndex.value = index
    }

    companion object {
        lateinit var instance: AlterSubApp
            private set

        fun startOverlayService(context: Context) {
            val intent = Intent(context, SubtitleOverlayService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("AlterSubApp", "Could not start SubtitleOverlayService: ${e.message}")
            }
        }
    }
}
