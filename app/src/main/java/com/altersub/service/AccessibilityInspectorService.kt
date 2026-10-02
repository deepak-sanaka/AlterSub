package com.altersub.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.altersub.AlterSubApp
import com.altersub.detection.AppPackageFilter
import com.altersub.detection.TitleSanitizer

class AccessibilityInspectorService : AccessibilityService() {

    private var lastScrapedTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        if (!AppPackageFilter.isTargetApp(pkg)) return

        // Throttle inspection to prevent CPU burden on slow hardware (max once every 1.5 seconds)
        val now = System.currentTimeMillis()
        if (now - lastScrapedTime < 1500L) return
        lastScrapedTime = now

        val root = rootInActiveWindow ?: return
        try {
            inspectNodeHierarchy(root, pkg)
        } catch (e: Exception) {
            Log.e("AccessibilityInspector", "Error inspecting nodes: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    private fun inspectNodeHierarchy(root: AccessibilityNodeInfo, pkg: String) {
        val candidates = mutableListOf<String>()
        collectTextNodes(root, candidates, depth = 0, maxDepth = 6)

        // Find the best matching title
        for (candidate in candidates) {
            val metadata = TitleSanitizer.sanitize(candidate, pkg)
            if (metadata != null) {
                Log.d("AccessibilityInspector", "Found media title: ${metadata.getDisplayName()}")
                AlterSubApp.instance.onContentDetected(metadata)
                break
            }
        }
    }

    private fun collectTextNodes(
        node: AccessibilityNodeInfo?,
        outList: MutableList<String>,
        depth: Int,
        maxDepth: Int
    ) {
        if (node == null || depth > maxDepth || outList.size >= 25) return

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()

        if (!text.isNullOrEmpty() && text.length in 3..100) {
            outList.add(text)
        } else if (!desc.isNullOrEmpty() && desc.length in 3..100) {
            outList.add(desc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTextNodes(child, outList, depth + 1, maxDepth)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.i("AccessibilityInspector", "Service interrupted")
    }
}
