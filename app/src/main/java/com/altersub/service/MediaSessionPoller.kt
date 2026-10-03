package com.altersub.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.altersub.AlterSubApp
import com.altersub.core.clock.SubtitleClock
import com.altersub.detection.AppPackageFilter
import com.altersub.detection.DetectionSource
import com.altersub.detection.DiagLog
import com.altersub.detection.MediaSessionDump
import com.altersub.detection.TitleSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Follows streaming apps' play state and position on TVs where [MediaNotificationListener] can't run: Android
 * refuses notification-listener access on low-RAM devices (`ro.config.low_ram=true`). With the DUMP permission,
 * granted once over ADB, the same data is readable from `dumpsys media_session`.
 *
 * There are no change callbacks this way, so it polls: every [ACTIVE_INTERVAL_MS] while a streaming app has a
 * session, every [IDLE_INTERVAL_MS] otherwise. The dump is requested from the media_session service directly
 * over binder; spawning `dumpsys` for it cost ~40 ms of CPU per poll on a low-end TV (2% of a core).
 * It stands aside whenever the notification listener is connected. Subtitle timing itself stays event-driven
 * (AGENTS.md rule 3): the clock is only re-anchored when the app's reported state actually moves.
 */
class MediaSessionPoller(private val app: AlterSubApp, private val scope: CoroutineScope) {

    private var job: Job? = null
    private var hadTargetSession = false
    private var lastTitle: String? = null

    fun startIfPermitted() {
        if (job?.isActive == true || !hasDumpPermission(app)) return
        Log.i(TAG, "DUMP permission granted: following media sessions by polling")
        job = scope.launch {
            while (isActive) {
                val delayMs = if (MediaNotificationListener.isConnected) IDLE_INTERVAL_MS else pollOnce()
                delay(delayMs)
            }
        }
    }

    /** Reads the sessions once, applies the best one, and returns how long to wait before the next read. */
    private suspend fun pollOnce(): Long {
        val dump = (dumpInProcess() ?: runDumpsys()) ?: return IDLE_INTERVAL_MS
        // Inactive sessions are ignored: apps leave idle ones behind (Prime Video does), and obeying them would
        // keep pausing a clock the user started by hand. A playing session wins, then the most recently updated.
        val session = MediaSessionDump.parse(dump)
            .filter { it.active && AppPackageFilter.isTargetApp(it.packageName) }
            .maxByOrNull { if (it.state == PlaybackState.STATE_PLAYING) Long.MAX_VALUE else it.updatedRealtimeMs }

        if (session == null) {
            if (hadTargetSession) app.onMediaSessionsEnded()
            hadTargetSession = false
            lastTitle = null
            return IDLE_INTERVAL_MS
        }
        hadTargetSession = true
        apply(session)
        return ACTIVE_INTERVAL_MS
    }

    private fun apply(session: MediaSessionDump.Session) {
        session.title?.takeIf { it != lastTitle }?.let { title ->
            lastTitle = title
            DiagLog.d { "[${session.packageName}] polled title \"$title\"" }
            TitleSanitizer.sanitize(title)?.let { app.onContentDetected(it, DetectionSource.MEDIA_SESSION) }
        }

        // No state yet (NONE) says nothing about playback; leave the clock alone
        val state = session.state?.takeIf { it != PlaybackState.STATE_NONE } ?: return
        val playing = state == PlaybackState.STATE_PLAYING
        val clock = app.clock

        if (session.positionMs < 0) {
            if (playing) clock.play() else clock.pause()
            return
        }

        val position = SubtitleClock.extrapolatePosition(
            positionMs = session.positionMs,
            lastUpdateRealtimeMs = session.updatedRealtimeMs,
            nowRealtimeMs = SystemClock.elapsedRealtime(),
            speed = session.speed,
            playing = playing
        )
        app.onPlaybackObserved(session.packageName, position, playing)

        // Re-anchor only on a real change (pause, resume, seek, or drift beyond what's visible), so the render
        // loop isn't woken every poll
        if (playing != clock.isPlaying.value || abs(position - clock.getPositionMs()) > RESYNC_THRESHOLD_MS) {
            DiagLog.d { "[${session.packageName}] sync: state=$state position=$position (clock was ${clock.getPositionMs()})" }
            clock.syncWithExternalPosition(position, playing, session.speed)
        }
    }

    /**
     * The same text `dumpsys media_session` prints, read straight from the service: the binder dump call is
     * permission-checked against our own uid (DUMP), and no extra process is started. Null if unavailable.
     */
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private suspend fun dumpInProcess(): String? {
        val service = mediaSessionService ?: return null
        return try {
            val (readSide, writeSide) = ParcelFileDescriptor.createPipe()
            coroutineScope {
                // Read concurrently so a large dump can't fill the pipe and block the service
                val text = async(Dispatchers.IO) {
                    ParcelFileDescriptor.AutoCloseInputStream(readSide).bufferedReader().use { it.readText() }
                }
                writeSide.use { service.dump(it.fileDescriptor, emptyArray()) }
                withTimeoutOrNull(DUMP_TIMEOUT_MS) { text.await() }
            }?.takeUnless { it.contains("Permission Denial") }
        } catch (e: Exception) {
            Log.w(TAG, "In-process media_session dump failed, falling back to dumpsys: ${e.message}")
            mediaSessionService = null
            null
        }
    }

    private var mediaSessionService: IBinder? = try {
        Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "media_session") as IBinder?
    } catch (e: Exception) {
        null
    }

    private fun runDumpsys(): String? = try {
        val process = ProcessBuilder("dumpsys", "media_session").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        if (output.contains("Permission Denial")) {
            Log.w(TAG, "dumpsys media_session denied; stopping")
            job?.cancel()
            null
        } else {
            output
        }
    } catch (e: Exception) {
        Log.w(TAG, "dumpsys media_session failed: ${e.message}")
        null
    }

    companion object {
        private const val TAG = "MediaSessionPoller"
        private const val ACTIVE_INTERVAL_MS = 2_000L
        private const val IDLE_INTERVAL_MS = 10_000L
        private const val RESYNC_THRESHOLD_MS = 250L
        private const val DUMP_TIMEOUT_MS = 2_000L

        fun hasDumpPermission(context: Context) =
            context.checkSelfPermission(android.Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED
    }
}
