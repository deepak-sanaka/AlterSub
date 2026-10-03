package com.altersub.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.altersub.AlterSubApp
import com.altersub.detection.AppPackageFilter
import com.altersub.detection.DiagLog
import com.altersub.detection.ScreenText
import com.altersub.detection.ScreenTextRules
import com.altersub.detection.ScreenTitlePicker
import com.altersub.detection.TitleConfirmation
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the title off a streaming app's screen when nothing better says what's playing. Scans run on their own thread
 * (walking another app's views is slow IPC and would delay subtitle cues on the main thread), at most every
 * [MIN_SCAN_INTERVAL_MS], once the screen has settled, and a title counts only once two scans in a row agree.
 */
class AccessibilityInspectorService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanThread: HandlerThread? = null
    private var scanHandler: Handler? = null
    private val scanPending = AtomicBoolean(false)
    private val scan = Runnable {
        scanPending.set(false)
        scanActiveWindow()
    }

    // Used on the scan thread only
    private val confirmation = TitleConfirmation()
    private var reportedKey: String? = null
    private val bounds = Rect()

    @Volatile
    private var lastScanAt = 0L
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Only the streaming apps' events are delivered: nothing else is read, and other apps' UI events cost nothing
        serviceInfo = serviceInfo?.apply { packageNames = AppPackageFilter.packages.toTypedArray() }
        scanThread = HandlerThread("AlterSubScreenScan").also {
            it.start()
            scanHandler = Handler(it.looper)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != lastPackage) {
            lastPackage = pkg
            DiagLog.d { "foreground package: $pkg (target=${AppPackageFilter.isTargetApp(pkg)})" }
        }
        if (!AppPackageFilter.isTargetApp(pkg)) return

        // A live media session or the user's own pick outranks anything scraped from the screen
        if (!AlterSubApp.instance.acceptsScreenDetection) return

        // One scan for a burst of events, after the screen settles and no sooner than the interval allows
        if (scanPending.compareAndSet(false, true)) {
            val sinceLast = SystemClock.uptimeMillis() - lastScanAt
            scheduleScan(maxOf(SETTLE_MS, MIN_SCAN_INTERVAL_MS - sinceLast))
        }
    }

    private fun scheduleScan(delayMs: Long) {
        val handler = scanHandler
        if (handler == null) scanPending.set(false) else handler.postDelayed(scan, delayMs)
    }

    private fun scanActiveWindow() {
        if (!AlterSubApp.instance.acceptsScreenDetection) {
            confirmation.reset()
            reportedKey = null
            return
        }
        lastScanAt = SystemClock.uptimeMillis()
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString().orEmpty()
        val texts = try {
            if (!AppPackageFilter.isTargetApp(pkg)) return
            readTexts(root)
        } catch (e: Exception) {
            Log.e("AccessibilityInspector", "Error inspecting nodes: ${e.message}")
            return
        } finally {
            root.recycle()
        }

        val ranked = ScreenTitlePicker.rank(texts, pkg)
        val title = ScreenTitlePicker.decide(ranked)
        DiagLog.d {
            "[$pkg] read ${texts.size} texts: " + texts.take(20).joinToString { it.describe() } +
                "; candidates: " + ranked.take(5).joinToString { "\"${it.metadata.getDisplayName()}\"=${it.score}" } +
                " -> ${title?.getDisplayName() ?: "none"}"
        }

        val confirmed = confirmation.offer(title)
        when {
            // Reported once per title: the session then checks it against the catalog over the network
            confirmed != null -> if (confirmed.contentKey != reportedKey) {
                reportedKey = confirmed.contentKey
                mainHandler.post { AlterSubApp.instance.onScreenTitle(confirmed) }
            }
            // A new title: look again shortly, even if the screen stays still and sends no more events
            title != null && scanPending.compareAndSet(false, true) -> scheduleScan(MIN_SCAN_INTERVAL_MS)
        }
    }

    /** The window's text in screen order, with layout hints. Bounded, so a huge screen stays cheap to read. */
    private fun readTexts(root: AccessibilityNodeInfo): List<ScreenText> {
        val out = ArrayList<ScreenText>()
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int, inRow: Boolean, inHeader: Boolean) {
            if (visited++ >= MAX_NODES || out.size >= MAX_TEXTS || depth > MAX_DEPTH) return
            val viewId = node.viewIdResourceName?.substringAfter(":id/")
            val text = node.text?.toString()?.trim().orEmpty()
            val shown = text.ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
            if (shown.length in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH && node.isVisibleToUser) {
                node.getBoundsInScreen(bounds)
                out += ScreenText(
                    text = shown,
                    viewId = viewId,
                    className = node.className?.toString(),
                    clickable = node.isClickable,
                    heading = node.isHeading,
                    inRow = inRow,
                    inHeader = inHeader,
                    fromDescription = text.isEmpty(),
                    heightPx = bounds.height()
                )
            }
            // A collection with more than one column is a row or grid of cards
            val childrenInRow = inRow || (node.collectionInfo?.columnCount ?: 0) > 1
            val childrenInHeader = inHeader || ScreenTextRules.isHeaderContainer(viewId)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    visit(child, depth + 1, childrenInRow, childrenInHeader)
                } finally {
                    child.recycle()
                }
            }
        }

        visit(root, 0, inRow = false, inHeader = false)
        return out
    }

    private fun ScreenText.describe(): String = buildString {
        append('"').append(text).append('"')
        viewId?.let { append(" #").append(it) }
        className?.substringAfterLast('.')?.let { append(' ').append(it) }
        if (heading) append(" heading")
        if (clickable) append(" clickable")
        if (inRow) append(" row")
        if (inHeader) append(" header")
        if (fromDescription) append(" desc")
        append(' ').append(heightPx).append("px")
    }

    override fun onInterrupt() {
        Log.i("AccessibilityInspector", "Service interrupted")
    }

    override fun onDestroy() {
        scanThread?.quitSafely()
        scanThread = null
        scanHandler = null
        super.onDestroy()
    }

    private companion object {
        const val MIN_SCAN_INTERVAL_MS = 1_500L
        const val SETTLE_MS = 400L
        const val MAX_NODES = 200
        const val MAX_DEPTH = 30
        const val MAX_TEXTS = 40
        const val MIN_TEXT_LENGTH = 2
        const val MAX_TEXT_LENGTH = 100
    }
}
