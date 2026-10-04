# AlterSub — Independent Android TV Subtitle Overlay

**AlterSub** displays synchronized subtitles from external sources (OpenSubtitles, community proxies, YTS, or local phone uploads) directly over third-party streaming apps like Netflix, Prime Video, Disney+, and YouTube on Android TV.

Optimized specifically for **Android TV 9 (Pie)** and lower-spec hardware, while fully compatible with modern **Android TV 14 / Google TV**.

---

## Architecture & Features

1. **Independent System Overlay (`SYSTEM_ALERT_WINDOW`)**:
   * Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCHABLE` so remote control clicks pass through to Netflix uninterrupted.
   * High-contrast cinema yellow text with a 2.5dp black stroke outline and semi-transparent bounding box for 100% legibility on any movie scene.
   * Hardware-accelerated, zero-allocation rendering on every frame.

2. **Content Detection and Playback Timing**:
   * **MediaSession Listener**: Hooks into system media sessions to track active playback state (`PLAYING`, `PAUSED`) and live millisecond timestamps.
   * **Accessibility Node Inspector**: Reads title cards and episode text (`SxxExx`) where streaming apps expose accessible text. The tested Netflix TV version exposes none.
   * **Optional Netflix Titles**: Receives Netflix's own description-page speech through a user-selected TTS engine, then waits for Playing and active media playback before searching. No Netflix login is required.
   * Subtitle rendering sleeps between cue changes; low-RAM TVs use the existing media-session poller for playback timing.

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

## Optional Netflix Automatic Titles

Open AlterSub's **Netflix titles** setup on the TV first, so it remembers your existing voice engine. Enable **AlterSub Netflix titles** in Android Accessibility settings, then select **AlterSub (Netflix titles)** as the preferred text-to-speech engine. A working voice engine, such as Speech Services by Google, must remain installed. Netflix may need to be reopened to use the new engine.

TalkBack can remain off. **Mute Netflix announcements** is enabled by default on AlterSub's setup screen: Netflix sends its spoken-interface strings to AlterSub, which receives the title and completes the announcement with 10 ms of silence. No voice synthesis or speech file is generated for these muted requests. This setting affects Netflix's own UI announcements, not movie audio or the TV volume. Other apps' speech, including TalkBack if independently enabled, continues through the original engine without examining its text.

Open a Netflix description page, wait briefly for it to load, then select Play within two minutes. AlterSub confirms playback before resolving the title and searching subtitles. To hear Netflix announcements, uncheck **Mute Netflix announcements**; they will use the original voice engine. Audible forwarding uses a transient private cache file deleted after each request. Release builds do not log utterances. The integrated silent mode and complete title-to-subtitle workflow still need device testing.

Currently supported: the English description-page announcements verified on Netflix TV 8.3.11. Starting directly from a browsing card, autoplay, episode changes, and other languages may need phone search. If several catalog entries share a title, choose the correct film on the phone. Manual choices retain priority over spoken-title guesses.

To disable this option, choose your original preferred speech engine and turn off **AlterSub Netflix titles** in Accessibility settings. Keep the existing media-session permission (or ADB-granted DUMP permission on low-RAM TVs) for playback confirmation and subtitle timing.

## Phone Web Remote Usage

1. Launch **AlterSub** on your TV.
2. Note the IP address shown on screen (e.g., `http://192.168.1.50:8080`).
3. Open that address in your mobile phone browser.
4. Search from the phone, or enable the optional Netflix titles setup above and start playback from an English description page. Select the correct film if the phone offers several catalog matches.
