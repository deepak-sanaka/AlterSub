# AGENTS.md — Guidance for AI Agents & Automated Contributors

> This document provides critical architectural context, constraints, and instructions for AI coding agents (such as Antigravity, Claude, Copilot, Cursor, etc.) working on the **AlterSub** codebase.

---

## 1. Project Mission & Identity

**AlterSub** is an independent, non-intrusive subtitle overlay application for **Android TV**. It detects media content playing in commercial streaming apps (e.g., Netflix, Prime Video, Disney+, Hotstar) and overlays synchronized external subtitles (sourced from community repositories, APIs, or user phone uploads) directly over the video player.

* **Target Device Constraint**: Specifically engineered to perform smoothly on **Android TV 9 (Pie / API 28)** with **low-spec hardware** (low-power quad-core SoCs, 1GB RAM).
* **Compatibility Range**: `minSdkVersion = 28` (Android 9) to `targetSdkVersion = 34` (Android 14+ / Google TV).

---

## 2. Immutable Engineering Rules & Constraints

When modifying or extending this codebase, **agents must strictly adhere to the following rules**:

### Rule 1: NEVER Use Screen Capture / MediaProjection for Video Frame OCR
* **Reason**: Netflix and other streaming apps render into a hardware-protected secure surface (Widevine L1 DRM) with `FLAG_SECURE`. Any attempt to capture video pixels via `MediaProjection.createVirtualDisplay()` yields pure black pixels (`#000000`). Continuous frame capture also throttles low-power TV processors.
* **Prescribed Pattern**: Content detection must rely exclusively on:
  1. `MediaSessionManager` / `NotificationListenerService` (reads active title & live playhead timestamps).
  2. `AccessibilityService` (inspects view hierarchy text nodes on UI title cards when navigating or pausing).
  3. User search / Phone Companion Web Remote override.

### Rule 2: NEVER Introduce Heavy UI Runtimes into the Subtitle Overlay
* **Reason**: Jetpack Compose adds ~15MB+ heap memory overhead and incurs frequent garbage collection pauses during recomposition, causing visible video stutter on 1GB RAM TV boxes.
* **Prescribed Pattern**: The overlay window (`SubtitleOverlayService`) must use the custom hardware-accelerated [`SubtitleTextView`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/ui/overlay/SubtitleTextView.kt). Paint objects and bounding rects must remain pre-allocated; never allocate new objects in `onDraw`.

### Rule 3: NEVER Implement a 60 FPS Polling Loop for Subtitle Timing
* **Reason**: Subtitle cues change every few seconds, not every 16ms. A continuous 60 FPS timer drains battery, wakes CPU cores, and adds contention.
* **Prescribed Pattern**: The timing loop in [`SubtitleOverlayService`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/SubtitleOverlayService.kt) must query [`SubtitleIndex.getTimeUntilNextChange(timeMs)`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/parser/SubtitleIndex.kt) and sleep until the active cue ends or the next cue begins. CPU usage must remain near **0.0%** during playback.

### Rule 4: Subtitle Overlay Window Flags
* The overlay must always maintain `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCHABLE` so remote control D-pad clicks pass through directly to the underlying streaming app uninterrupted.

---

## 3. Technology Stack & Key Dependencies

* **Language**: Kotlin 1.9+ (Coroutines, StateFlow).
* **Architecture**: Clean Architecture with modular separation (`core/`, `detection/`, `provider/`, `server/`, `service/`, `ui/`).
* **HTTP Client**: OkHttp 4 (`okhttp:4.12.0`).
* **Embedded Web Server**: NanoHTTPD 2.3 (`org.nanohttpd:nanohttpd:2.3.1`) — ultra-lightweight (~50KB jar, <2MB RAM).
* **UI**:
  * TV Dashboard: AndroidX Leanback (`leanback:1.0.0`) + AppCompat.
  * Overlay: Pure Android `Canvas` / `TextPaint` custom View.

---

## 4. Directory & Subsystem Map

* **`core/model/`**: Data models (`ContentMetadata`, `SubtitleCue`, `SubtitleTrack`, `PlaybackStateInfo`).
* **`core/clock/`**: [`SubtitleClock.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/clock/SubtitleClock.kt) — monotonic elapsed realtime timekeeper with $\pm\text{ms}$ user offset.
* **`core/parser/`**:
  * [`SrtParser.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/parser/SrtParser.kt) — single-pass UTF-8/BOM SRT parser with precompiled tag stripping (parses once per track; the per-frame zero-allocation rule applies to `SubtitleTextView.onDraw`).
  * [`SubtitleIndex.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/parser/SubtitleIndex.kt) — flat binary search index ($O(\log N)$) with smart sleep interval calculator.
* **`detection/`**:
  * [`TitleSanitizer.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/TitleSanitizer.kt) — regex cleaner for `SxxExx`, years, and UI junk filter.
  * [`AppPackageFilter.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/AppPackageFilter.kt) — target streaming app package registry.
  * [`DetectionArbiter.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/DetectionArbiter.kt) — source priority: MediaSession > user's manual choice > accessibility scraping.
* **`provider/`**:
  * [`StremioSubtitleProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/StremioSubtitleProvider.kt) — zero-auth OpenSubtitles community proxy.
  * [`YtsSubtitleProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/YtsSubtitleProvider.kt) — zero-auth movie subtitle endpoint.
  * [`OpenSubtitlesApiProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/OpenSubtitlesApiProvider.kt) — official OpenSubtitles.com REST API.
  * [`CompositeSubtitleProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/CompositeSubtitleProvider.kt) — multi-source parallel aggregator & local upload repository.
* **`server/`**:
  * [`WebRemoteServer.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/server/WebRemoteServer.kt) — NanoHTTPD embedded server on port 8080.
  * [`WebRemoteHtml.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/server/WebRemoteHtml.kt) — dark-mode mobile remote UI.
* **`service/`**:
  * [`SubtitleOverlayService.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/SubtitleOverlayService.kt) — `TYPE_APPLICATION_OVERLAY` foreground service.
  * [`AccessibilityInspectorService.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/AccessibilityInspectorService.kt) — window text scraper.
  * [`MediaNotificationListener.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/MediaNotificationListener.kt) — `MediaSessionManager` listener.

---

## 5. Development & Verification Commands

Always run these commands from the project root (`C:\Users\deepa\AlterSub`):

```powershell
# 1. Run all unit tests (must pass before committing changes)
.\gradlew.bat testDebugUnitTest

# 2. Compile and package the debug APK
.\gradlew.bat assembleDebug

# 3. Install directly onto a running emulator or connected TV
.\gradlew.bat installDebug
```

### Essential ADB Commands for Testing
```powershell
# Grant overlay permission
adb shell appops set com.altersub SYSTEM_ALERT_WINDOW allow

# Grant accessibility inspector
adb shell settings put secure enabled_accessibility_services com.altersub/com.altersub.service.AccessibilityInspectorService
adb shell settings put secure accessibility_enabled 1

# Grant notification / media session listener
adb shell cmd notification allow_listener com.altersub/com.altersub.service.MediaNotificationListener

# Forward embedded web remote port to host PC
adb forward tcp:8888 tcp:8080
```
