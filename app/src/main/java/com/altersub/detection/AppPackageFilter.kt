package com.altersub.detection

/**
 * The video apps AlterSub follows. Only these are read, both their media sessions and their screen text. Everything
 * else is ignored, including the TV home screen, Settings and music apps (KI-3). Matching by name ("tv", "media")
 * took in the launcher, whose menus were then searched as film titles.
 */
object AppPackageFilter {

    val packages: Set<String> = setOf(
        "com.netflix.ninja",                     // Netflix (Android TV)
        "com.netflix.mediaclient",               // Netflix (phone/tablet build)
        "com.amazon.amazonvideo.livingroom",     // Prime Video (Android TV)
        "com.amazon.avod.thirdpartyclient",      // Prime Video (phone/tablet build)
        "com.disney.disneyplus",                 // Disney+
        "in.startv.hotstar",                     // JioHotstar
        "com.hotstar.tv",                        // Disney+ Hotstar (older TV build)
        "com.sonyliv",                           // Sony LIV
        "com.graymatrix.did",                    // ZEE5
        "com.jio.media.jiotvplus",               // JioTV+
        "com.google.android.youtube.tv",         // YouTube (Android TV)
        "com.liskovsoft.smarttubetv.beta",       // SmartTube
        "com.apple.atve.androidtv.appletv",      // Apple TV
        "com.hbomax.android.tv",                 // Max (HBO)
        "com.wbd.stream",                        // Max
        "com.hulu.livingroomplus",               // Hulu (Android TV)
        "com.crunchyroll.crunchyroid",           // Crunchyroll
        "com.mubi",                              // MUBI
        "com.plexapp.android",                   // Plex
        "org.jellyfin.androidtv",                // Jellyfin
        "tv.emby.embyatv",                       // Emby
        "com.stremio.one",                       // Stremio
        "org.xbmc.kodi",                         // Kodi
        "org.videolan.vlc",                      // VLC
        "com.mxtech.videoplayer.ad",             // MX Player
        "com.mxtech.videoplayer.pro"             // MX Player Pro
    )

    fun isTargetApp(packageName: String): Boolean = packageName in packages
}
