package com.altersub.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Lets Netflix enable its own spoken UI. It does not read nodes, handle keys, or speak other apps. */
class NetflixSpeechAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
