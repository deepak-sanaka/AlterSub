# AlterSub — Independent Android TV Subtitle Overlay

**AlterSub** displays synchronized subtitles from external sources (OpenSubtitles, community proxies, YTS, or local phone uploads) directly over third-party streaming apps like Netflix, Prime Video, Disney+, and YouTube on Android TV.

Optimized specifically for **Android TV 9 (Pie)** and lower-spec hardware, while fully compatible with modern **Android TV 14 / Google TV**.

---

## Architecture & Features

1. **Independent System Overlay (`SYSTEM_ALERT_WINDOW`)**:
   * Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCHABLE` so remote control clicks pass through to Netflix uninterrupted.
   * High-contrast cinema yellow text with a 2.5dp black stroke outline and semi-transparent bounding box for 100% legibility on any movie scene.
   * Hardware-accelerated, zero-allocation rendering on every frame.

2. **Hybrid Detection Engine (DRM Bypassing)**:
   * **MediaSession Listener**: Hooks into system media sessions to track active playback state (`PLAYING`, `PAUSED`) and live millisecond timestamps.
   * **Accessibility Node Inspector**: Scrapes title cards and episode text (`SxxExx`) directly from Netflix/Prime UI when browsing or pausing.
   * Zero polling overhead: CPU usage drops to **0%** during video playback.

3. **Composite Subtitle Sourcing**:
   * **Tier 1 (Instant Zero-Auth)**: Public Stremio OpenSubtitles v3 mirror (queries OpenSubtitles' massive database without needing any personal API key).
   * **Tier 2 (Movies Zero-Auth)**: YTS Subtitles public API.
   * **Tier 3 (Official API)**: Configurable OpenSubtitles.com REST API with user API key & token.
   * **Tier 4 (Offline Phone Upload)**: Upload any `.srt` directly from your phone browser.

4. **Embedded Phone Web Remote**:
   * Ultra-lightweight local HTTP server running on port `8080`.
   * Open `http://<your-tv-ip>:8080` from any phone on the same Wi-Fi:
     * View live detected movie/series name.
     * Switch subtitle tracks in 1 tap.
     * Fine-tune subtitle sync with `+250ms`, `-250ms`, `+1.0s`, `-1.0s` buttons.
     * Upload a `.srt` from your phone to immediately show on the TV.

---

## Quick Installation & Setup (ADB)

Connect to your Android TV via ADB:

```bash
# 1. Connect to your TV IP
adb connect <YOUR_TV_IP>:5555

# 2. Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Grant Permissions via ADB (Fast 1-Minute Setup)
Instead of navigating nested Android TV menus, run these commands:

```bash
# Grant "Display Over Other Apps" (SYSTEM_ALERT_WINDOW)
adb shell appops set com.altersub SYSTEM_ALERT_WINDOW allow

# Grant Accessibility Service (Title Inspector)
adb shell settings put secure enabled_accessibility_services com.altersub/com.altersub.service.AccessibilityInspectorService
adb shell settings put secure accessibility_enabled 1

# Grant Notification & MediaSession Listener (Play/Pause Sync)
adb shell cmd notification allow_listener com.altersub/com.altersub.service.MediaNotificationListener
```

---

## Phone Web Remote Usage

1. Launch **AlterSub** on your TV.
2. Note the IP address shown on screen (e.g., `http://192.168.1.50:8080`).
3. Open that address in your mobile phone browser.
4. Play any movie on Netflix. AlterSub will detect the title and start overlaying subtitles automatically!
