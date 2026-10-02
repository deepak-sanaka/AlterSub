package com.altersub.detection

object AppPackageFilter {

    private val TARGET_STREAMING_PACKAGES = setOf(
        "com.netflix.ninja",                     // Netflix Android TV
        "com.netflix.mediaclient",               // Netflix Mobile/Tablet build on TV
        "com.amazon.amazonvideo.livingroom",     // Prime Video Android TV
        "com.disney.disneyplus",                 // Disney+
        "com.google.android.youtube.tv",         // YouTube Android TV
        "com.hotstar.tv",                        // Disney+ Hotstar TV
        "org.videolan.vlc",                      // VLC
        "com.plexapp.android",                   // Plex
        "org.xbmc.kodi",                         // Kodi
        "com.apple.atve.androidtv.appletv",      // Apple TV
        "com.hbomax.android.tv",                 // Max (HBO)
        "com.wbd.stream"                         // Max Global
    )

    fun isTargetApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (TARGET_STREAMING_PACKAGES.contains(packageName)) return true

        // Accept any video-related packages
        val lower = packageName.lowercase()
        return lower.contains("video") || lower.contains("movie") || lower.contains("media") || lower.contains("tv")
    }
}
