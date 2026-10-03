package com.altersub.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPackageFilterTest {

    @Test
    fun testStreamingAppsAreFollowed() {
        listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.sonyliv", "org.videolan.vlc")
            .forEach { assertTrue(it, AppPackageFilter.isTargetApp(it)) }
    }

    @Test
    fun testTheHomeScreenSettingsAndOtherAppsAreNot() {
        // All of these passed the old name match ("tv", "media", "video")
        listOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.android.tv.settings",
            "com.google.android.tv.remote.service",
            "com.android.providers.media",
            "com.spotify.tv.android",
            "com.mstar.netflixobserver",
            "",
            "com.altersub"
        ).forEach { assertFalse(it, AppPackageFilter.isTargetApp(it)) }
    }
}
