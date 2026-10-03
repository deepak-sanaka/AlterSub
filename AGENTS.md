# AGENTS.md — Guidance for AI Agents & Automated Contributors

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
  1. `MediaSessionManager` / `NotificationListenerService` (reads active title & live playhead timestamps). On low-RAM TVs, where Android refuses notification access, `MediaSessionPoller` reads the same data from the `media_session` dump with an ADB-granted DUMP permission.
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
* **QR codes**: ZXing core (`com.google.zxing:core:3.5.3`), encoder only; pure Java, no camera or UI code.
* **UI font**: AlterSub Sans, three static Latin instances of Google Sans Flex (SIL OFL 1.1) built by `tools/build_app_font.py` (~78 KB each). Applied to the TV screen in code by `ui/AppFont` and served to the phone page by the web server. Never set it via the theme or `android:fontFamily`: on API 28 that makes the framework and AppCompat each build a font collection with the full system fallback chain (+4 MB native memory, measured). The TTFs are stored uncompressed (`noCompress`) so they are memory-mapped. Don't bundle the 4 MB variable font, and don't name derived files or families "Google Sans" (Google's trademark notes). The subtitle overlay keeps its own typeface.
* **UI**:
  * TV Dashboard: AppCompat with plain Views (D-pad navigable); listed in the TV launcher via `LEANBACK_LAUNCHER`. No Leanback library. Keep it cheap to draw: flat shape drawables (no elevation shadows, border strokes or blur), no idle animations, and no view background that repaints the full screen over the window background.
  * Overlay: Pure Android `Canvas` / `TextPaint` custom View.

---

## 4. Directory & Subsystem Map

* **`core/model/`**: Data models (`ContentMetadata`, `SubtitleCue`, `SubtitleTrack`, `SubtitleStyle`, `PlaybackStateInfo`).
* **`core/session/`**: [`PickMemory.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/session/PickMemory.kt) — remembered picks (track, offset, progress, app) per title, persisted; only user choices and media-session titles are remembered, never screen-scraped guesses. [`SubtitleSession.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/session/SubtitleSession.kt) — detection → search → download → active-track orchestration (cancellation, stale-result and user-choice rules). `AlterSubApp` delegates to it; keep it free of Android components so `SubtitleSessionTest` can run on the JVM.
* **`core/clock/`**: [`SubtitleClock.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/clock/SubtitleClock.kt) — monotonic elapsed realtime timekeeper with $\pm\text{ms}$ user offset.
* **`core/parser/`**:
  * [`SrtParser.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/parser/SrtParser.kt) — single-pass UTF-8/BOM SRT parser with precompiled tag stripping (parses once per track; the per-frame zero-allocation rule applies to `SubtitleTextView.onDraw`).
  * [`SubtitleIndex.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/core/parser/SubtitleIndex.kt) — flat binary search index ($O(\log N)$) with smart sleep interval calculator.
* **`detection/`**:
  * [`TitleSanitizer.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/TitleSanitizer.kt) — regex cleaner for `SxxExx`, years, and UI junk filter.
  * [`AppPackageFilter.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/AppPackageFilter.kt) — target streaming app package registry.
  * [`DetectionArbiter.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/DetectionArbiter.kt) — source priority: MediaSession > user's manual choice > accessibility scraping.
  * [`MediaSessionDump.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/MediaSessionDump.kt) — parser for the `media_session` dump (Android 9 format; tested against captures from a real TV).
  * [`DiagLog.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/detection/DiagLog.kt) — debug-only detection diagnostics (tag `AlterSubDiag`); never log other apps' screen text outside it.
* **`provider/`**:
  * [`StremioSubtitleProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/StremioSubtitleProvider.kt) — zero-auth OpenSubtitles community proxy.
  * [`YtsSubtitleProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/YtsSubtitleProvider.kt) — zero-auth movie subtitle endpoint.
  * [`OpenSubtitlesApiProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/OpenSubtitlesApiProvider.kt) — official OpenSubtitles.com REST API.
  * [`CompositeSubtitleProvider.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/provider/CompositeSubtitleProvider.kt) — multi-source parallel aggregator & local upload repository.
* **`server/`**:
  * [`WebRemoteServer.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/server/WebRemoteServer.kt) — NanoHTTPD embedded server on port 8080 (falls back to 8081–8089). Talks only to the [`RemoteController`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/server/RemoteController.kt) interface (implemented by `AlterSubApp`), so routes are tested in `WebRemoteServerTest`.
  * [`RemoteAuth.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/server/RemoteAuth.kt) — PIN pairing. Every `/api/*` route except `/api/pair` requires a paired phone's token (`X-AlterSub-Token`); keep new routes behind that check.
  * [`WebRemoteHtml.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/server/WebRemoteHtml.kt) — dark-mode mobile remote UI. Server data (track titles come from uploaders and screen text) is only ever inserted with `textContent`; never use `innerHTML` (`WebRemoteServerTest` enforces this).
* **`service/`**:
  * [`SubtitleOverlayService.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/SubtitleOverlayService.kt) — `TYPE_APPLICATION_OVERLAY` foreground service.
  * [`AccessibilityInspectorService.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/AccessibilityInspectorService.kt) — window text scraper.
  * [`MediaNotificationListener.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/MediaNotificationListener.kt) — `MediaSessionManager` listener.
  * [`MediaSessionPoller.kt`](file:///c:/Users/deepa/AlterSub/app/src/main/java/com/altersub/service/MediaSessionPoller.kt) — low-RAM fallback: polls the `media_session` dump over binder (DUMP permission granted via ADB) every 2 s while a streaming session is active.

---

## 5. Development & Verification Commands

Always run these commands from the project root (`C:\Users\deepa\AlterSub`):

```powershell
# 1. Run all unit tests (must pass before committing changes; works offline)
.\gradlew.bat testDebugUnitTest

# 1b. Also run tests that hit real network services (e.g. the Stremio proxy)
.\gradlew.bat testDebugUnitTest -PliveTests

# 2. Compile and package the debug APK (unshrunk)
.\gradlew.bat assembleDebug

# 2b. Release APK, shrunk by R8 (code + resources). If you add reflection, JSON-to-class mapping, or
#     resources looked up by name, add a targeted rule to app/proguard-rules.pro and test a release
#     build on a device: R8 problems only show up at runtime.
.\gradlew.bat assembleRelease

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

# Low-RAM TVs (getprop ro.config.low_ram = true) silently ignore the line above; grant this instead
adb shell pm grant com.altersub android.permission.DUMP

# Record a real-TV test session (debug build; diagnostics use the logcat tag AlterSubDiag)
bash tools/capture_device_logs.sh <TV_IP>:5555

# Forward embedded web remote port to host PC (use the port the TV screen shows if 8080 was taken),
# then open http://localhost:8888 and enter the PIN shown on the AlterSub TV screen
# (or open http://localhost:8888/#pin=<PIN>, which is what the TV's QR code does)
adb forward tcp:8888 tcp:8080
```
