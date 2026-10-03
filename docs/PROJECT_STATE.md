# AlterSub — Complete Project State & Architecture

> **Document Purpose**: This file serves as the single source of truth for the AlterSub project. Any LLM or developer reading this document will immediately understand the complete state of the codebase, every implemented subsystem, runtime behavior, engineering decisions, and architectural tradeoffs.

---

## 1. Executive Summary & Mission

* **Project Name**: AlterSub
* **Target Platform**: Android TV 9.0 (API 28 / Pie) up to Android 14+ (API 34 / 35 / UpsideDownCake/VanillaIceCream).
* **Hardware Constraint**: Specifically engineered to perform smoothly on **slow Android TV hardware** (low-power quad-core SoCs like Amlogic S905X / MediaTek, 1GB – 1.5GB RAM) with **zero dropped video frames** and **near-zero CPU/battery consumption** during playback.
* **Core Problem Solved**: Commercial streaming applications (Netflix, Prime Video, Disney+, Hotstar) do not permit external subtitle loading and render video inside hardware-protected DRM secure surfaces (Widevine L1). AlterSub acts as an independent system overlay that automatically detects the playing media and renders synchronized subtitles from open community repositories (or local user uploads) on top of DRM video players without root or app modification.

---

## 2. Directory Structure & File Map

```
AlterSub/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml                  # TV leanback declarations, permissions, services
│   │   │   ├── java/com/altersub/
│   │   │   │   ├── AlterSubApp.kt                   # Central singleton coordinator, state flows, server lifecycle
│   │   │   │   ├── core/
│   │   │   │   │   ├── clock/
│   │   │   │   │   │   └── SubtitleClock.kt         # Monotonic clock, play/pause tracker, +/- ms offset
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── ContentMetadata.kt       # Structured title, season, episode, IMDb ID
│   │   │   │   │   │   ├── PlaybackStateInfo.kt     # Playing status, time position, speed, package
│   │   │   │   │   │   ├── SubtitleCue.kt           # Start/end timestamps (ms), text lines
│   │   │   │   │   │   └── SubtitleTrack.kt         # Track metadata (source, URL, language, rating)
│   │   │   │   │   └── parser/
│   │   │   │   │       ├── SrtParser.kt             # SRT/WebVTT parser: BOM/UTF-16/Windows-1252 detection, markup + entity cleanup
│   │   │   │   │       └── SubtitleIndex.kt         # Binary search index (overlap-aware) + next-boundary calculator
│   │   │   │   ├── detection/
│   │   │   │   │   ├── AppPackageFilter.kt          # Target streaming apps (Netflix, Prime, Disney+, etc.)
│   │   │   │   │   ├── DetectionArbiter.kt          # Source priority: MediaSession > manual choice > accessibility
│   │   │   │   │   └── TitleSanitizer.kt            # Regex parser for clean show title, SxxExx, year extraction
│   │   │   │   ├── provider/
│   │   │   │   │   ├── SubtitleProvider.kt          # Base interface for subtitle sources
│   │   │   │   │   ├── StremioSubtitleProvider.kt   # Zero-auth public OpenSubtitles v3 proxy (movies & series)
│   │   │   │   │   ├── YtsSubtitleProvider.kt       # Zero-auth movie subtitle mirror with ZIP unpacker
│   │   │   │   │   ├── OpenSubtitlesApiProvider.kt  # Official OpenSubtitles.com REST API (API key + token)
│   │   │   │   │   └── CompositeSubtitleProvider.kt # Parallel search aggregator & local upload repository
│   │   │   │   ├── server/
│   │   │   │   │   ├── WebRemoteHtml.kt             # Responsive dark-mode mobile web UI for remote control
│   │   │   │   │   └── WebRemoteServer.kt           # Embedded NanoHTTPD micro-server on port 8080
│   │   │   │   ├── service/
│   │   │   │   │   ├── AccessibilityInspectorService.kt # View hierarchy scraper for OSD / title cards
│   │   │   │   │   ├── MediaNotificationListener.kt # Notification listener for MediaSession play/pause tokens
│   │   │   │   │   └── SubtitleOverlayService.kt    # Foreground service managing TYPE_APPLICATION_OVERLAY
│   │   │   │   └── ui/
│   │   │   │       ├── overlay/
│   │   │   │       │   └── SubtitleTextView.kt      # Hardware-accelerated canvas with stroked text & auto-fit
│   │   │   │       └── settings/
│   │   │   │           └── MainActivity.kt          # AppCompat TV setup screen, permission shortcuts, test trigger
│   │   │   └── res/
│   │   │       ├── drawable/                        # ic_launcher, ic_launcher_banner for Android TV
│   │   │       ├── layout/activity_main.xml         # TV setup layout (plain Views; Leanback library is declared but unused)
│   │   │       ├── values/                          # colors, strings, styles
│   │   │       └── xml/accessibility_service_config.xml # Accessibility config with event throttling
│   │   └── test/java/com/altersub/
│   │       ├── core/clock/SubtitleClockTest.kt      # MediaSession position extrapolation
│   │       ├── core/parser/SrtParserTest.kt         # Unit tests for SRT timestamp & cue extraction
│   │       ├── detection/DetectionArbiterTest.kt    # Detection source priority rules
│   │       ├── detection/TitleSanitizerTest.kt      # Unit tests for regex media title & junk filtering
│   │       ├── provider/CompositeSubtitleProviderTest.kt # Phone uploads scoped to their content
│   │       └── provider/StremioSubtitleProviderLiveTest.kt # Live internet test against OpenSubtitles proxy
│   ├── build.gradle.kts                             # App module build configuration
│   └── proguard-rules.pro                           # R8 / Proguard rules for NanoHTTPD and AlterSub models
├── docs/
│   └── PROJECT_STATE.md                             # This file
├── gradle/wrapper/                                  # Gradle 8.13 wrapper binaries & properties
├── AGENTS.md                                        # Rules and constraints for AI agents / contributors
├── build.gradle.kts                                 # Root build configuration
├── gradle.properties                                # JVM & AndroidX memory options
├── local.properties                                 # Android SDK path configuration (git-ignored)
├── settings.gradle.kts                              # Module definitions & repository mirrors
└── README.md                                        # User guide with one-line ADB commands
```

---

## 3. Subsystem Functionality & Data Flow

### 3.1 Subtitle Overlay Engine
* **Service**: `SubtitleOverlayService` running as an Android Foreground Service (`foregroundServiceType="specialUse"`).
* **Window Configuration**:
  * Window type: `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
  * Flags: `FLAG_NOT_FOCUSABLE` | `FLAG_NOT_TOUCHABLE` | `FLAG_LAYOUT_IN_SCREEN` | `FLAG_LAYOUT_NO_LIMITS` | `FLAG_HARDWARE_ACCELERATED`.
  * **Result**: Subtitles render completely transparent to the remote control. Every user click on the TV remote passes straight through to Netflix. (Touchscreens are a different story — see KI-11.)
  * The window is `MATCH_PARENT` (full screen) and the service has no stop path once started (KI-11, KI-12).
  * **Lifecycle**: On TV devices the service is started whenever AlterSub's own screen is resumed. That's a foreground context, where Android always allows foreground-service starts. Because it then stays up, later detections never need a background start. Track activation still calls `startOverlayService` as a fallback, which skips the call when the overlay is already running.
  * **Failures are reported, not fatal**: If a background start is refused (`ForegroundServiceStartNotAllowedException`) or the window can't be added (overlay permission revoked), the reason is published as `overlayError`. It appears in `/api/status` and as a warning on the phone remote, where previously a missing permission crashed the service.
* **Rendering View**: `SubtitleTextView`.
  * High-visibility cinema yellow text fill (`#FFE500`).
  * Black stroke outline (`Paint.Style.STROKE`, width = text size ÷ 7) drawn underneath fill so text remains sharp against white backgrounds (e.g. snowy scenes, explosion flashes).
  * Rounded background box (`#B3000000`) for contrast.
  * Responsive scaling: Clamps line width to 90% of screen width to prevent clipping on any aspect ratio or screen size.
  * **User style** (`SubtitleStyle`): text size (16–60sp), colour (yellow / white / cyan) and vertical position (50–95% down), adjustable from the phone remote. Changes apply live and are saved in SharedPreferences.

### 3.2 Detection Pipeline (DRM Bypassing)
* **Strategy A — MediaSession Hook (`MediaNotificationListener`)**:
  * Registers with Android's `MediaSessionManager` via `NotificationListenerService`.
  * When Netflix / Prime updates playback, OS triggers `onPlaybackStateChanged` and `onMetadataChanged`.
  * Extracts exact playback state (`STATE_PLAYING`, `STATE_PAUSED`), position (`state.position`), and speed.
  * `state.position` is a snapshot taken at `state.lastPositionUpdateTime`; while playing it is advanced by the elapsed time × speed (`SubtitleClock.extrapolatePosition`) before calibrating the clock. An unknown position only updates play/pause.
  * When the last target session disappears, the clock is paused and accessibility detection is re-enabled.
  * Feeds timestamp calibrations into `SubtitleClock`.
* **Strategy B — Accessibility Inspector (`AccessibilityInspectorService`)**:
  * Listens to `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED`.
  * Throttled to execute at most once every 1,500ms to eliminate CPU spikes.
  * Recursively inspects up to 25 view hierarchy text nodes (max depth 6) on target apps (`com.netflix.ninja`, etc.).
  * Filters raw text through `TitleSanitizer`; the first candidate that survives is taken as the title (KI-5).
* **Source Priority (`DetectionArbiter`)**:
  * A live MediaSession title is authoritative; while one exists, accessibility scraping is skipped entirely.
  * A manual search, track pick, or upload from the phone remote holds until the MediaSession reports a *different* title (e.g. autoplay to the next episode). Re-reported identical session metadata does not override it.
  * Accessibility scraping is only a fallback when neither a session nor the user has said what is playing.
  * On a content change, the in-flight search and download are cancelled and the previous title's subtitles are cleared, so stale results can never activate.
  * Phone uploads are tied to the content detected at upload time and are only offered again for that same title/episode.
* **Strategy C — Title Sanitizer (`TitleSanitizer`)**:
  * Detects and separates series patterns: `S04E01`, `Season 4 Episode 1`, `Ep 12`.
  * Detects release years: `(2023)`, `[2024]`.
  * Strips video codec tags (`1080p`, `4K`, `HDR`, `WEBRip`, `BluRay`).
  * Ignores UI buttons (`Play`, `Resume`, `Episodes`, `Audio & Subtitles`, `Next Episode`) — exact matches only (KI-4).

### 3.3 Timing & Synchronization Engine
* **Clock Model (`SubtitleClock`)**:
  * Master clock uses `SystemClock.elapsedRealtime()` (monotonic hardware timer unaffected by system time changes).
  * Formula: $\text{CurrentTime} = \text{basePosition} + (\Delta t \times \text{speed}) + \text{userOffsetMs}$.
  * Supports manual offsets (`adjustOffset(+250ms)`, `adjustOffset(-1000ms)`) and manual seeks (`seekTo`, driven by the web remote's "Set time").
  * Offsets are remembered **per subtitle track** (`TrackOffsets`). A new title or a never-seen track starts at 0, and returning to a track restores its offset.
  * All mutators are `@Synchronized`: the clock is written from the main thread (MediaSession), web server threads, and read by the render loop.
* **Smart Sleep Ticker (`SubtitleIndex`)**:
  * Instead of a 60 FPS animation loop, `getTimeUntilNextChange` returns the exact media time until the text next changes: $\min(\text{activeCue.end} + 1, \text{nextCue.start}) - \text{time}$, or `Long.MAX_VALUE` after the last cue.
  * The overlay loop is event-driven. It collects `clock.changes` together with the active `SubtitleIndex`, and any play, pause, seek, sync, offset or track change restarts it immediately.
  * Between changes it sleeps exactly until the next boundary, converted to wall time at the current playback speed (`SubtitleClock.realtimeFor`). While paused, or after the last cue, it doesn't wake at all, and it touches the UI thread only when the text changes.
  * Overlapping cues (e.g. two speakers, or a long "[music]" cue behind dialogue) are all shown, one per line, via `SubtitleIndex.getTextAt`.

### 3.4 Multi-Source Subtitle Sourcing (`CompositeSubtitleProvider`)
Searches all sources concurrently using Kotlin coroutines `async { ... }`. All providers share one `OkHttpClient` (`Http.client`) and use `Call.await()`, so cancelling a superseded search also cancels its in-flight HTTP requests. Every response is closed with `use { }`.
> ⚠️ In the current build **only the Stremio source can return results** in the automatic flow (KI-2).

1. **Stremio Community Mirror (`StremioSubtitleProvider`)**:
   * Queries `https://opensubtitles-v3.strem.io/subtitles/{type}/{imdb_id}.json`.
   * If IMDb ID is missing, auto-resolves via `https://v3-cinemeta.strem.io/catalog/...` (first search hit wins). The resolved ID is not written back to `ContentMetadata`.
   * No API key or registration. This is a public third-party service with no published usage guarantees.
   * The download URLs return UTF-8-converted files (`subencoding-stremio-utf8`).
2. **YTS Mirror (`YtsSubtitleProvider`)**:
   * Queries `https://yts-subs.com/api/v1/movie/{imdb_id}` for movies.
   * Downloads and unpacks zipped `.srt` files on the fly.
   * **Currently unreachable**: it requires an IMDb ID, which detection never provides (KI-2). The endpoint itself has not been verified.
3. **Official OpenSubtitles REST API (`OpenSubtitlesApiProvider`)**:
   * Interfaces with `https://api.opensubtitles.com/api/v1/subtitles`.
   * Enabled only when an API key is set via `updateCredentials()`. **Nothing calls it and there is no settings UI**, so it is never enabled (KI-2).
4. **Phone Companion Upload**:
   * Receives user-uploaded `.srt` files from the phone and activates them immediately.
   * Each upload is tied to the content detected at upload time and offered first only when that same title/episode is detected again.

### 3.5 Embedded Phone Web Remote (`WebRemoteServer` & `WebRemoteHtml`)
* Runs a micro HTTP server via NanoHTTPD on port `8080`, started in `AlterSubApp.onCreate` and never stopped.
* Accessible from any phone on the same Wi-Fi network at `http://<tv-ip>:8080`, **with no authentication** (KI-7, KI-8).
* **Endpoints**:
  * `GET /`: Serves complete, zero-dependency dark-mode HTML/CSS/JS remote.
  * `GET /api/status`: Returns JSON with active movie title, active subtitle track, +/- ms offset, clock position (`positionMs`, excluding offset), play state, and track candidates, plus `overlayRunning` and `overlayError`.
  * `POST /api/offset?delta=<ms>`: Fine-tunes subtitle sync delay.
  * `POST /api/seek?positionMs=<ms>`: Sets the clock to the player's on-screen time (for apps that don't publish a MediaSession position). The remote accepts `41:23` / `1:05:10` input.
  * `POST /api/style?sizeStep=<±n>&positionStep=<±n>&color=<name>` (or `reset=1`): Adjusts subtitle size, vertical position and colour; values are clamped server-side.
  * `POST /api/toggle-play`: Manually forces clock play/pause.
  * `POST /api/select-track?id=<id>`: Switches active subtitle track with 1 tap.
  * `POST /api/search?q=<query>`: Triggers manual search for any title.
  * `POST /api/upload`: Receives multipart `.srt` file upload from phone.

---

## 4. Key Engineering Decisions & Tradeoffs

| Decision | Rationale | Tradeoff / Alternative Considered |
| :--- | :--- | :--- |
| **1. No Screen Capture / MediaProjection** | Netflix and Prime Video run Widevine L1 DRM with `FLAG_SECURE`. Any screen recording/capture returns a completely black frame (`#000000`). Attempting continuous frame capture and OCR would fail and melt a low-spec Android TV SoC. | **Tradeoff**: Cannot do visual OCR or video perceptual hashing. **Mitigation**: Used hybrid MediaSession tokens + Accessibility view scraping. |
| **2. Pure Custom View over Jetpack Compose for Overlay** | Android TV 9 with 1GB RAM suffers heavy GC pauses and frame drops if Compose runtime is loaded into a persistent overlay window. Compose requires 15MB+ heap and periodic recomposition allocations. | **Tradeoff**: UI had to be written in standard Android Canvas drawing code (`onDraw`, `TextPaint`), but memory footprint dropped from ~20MB to **< 1MB**. |
| **3. Smart Sleep vs 60 FPS Animation Loop** | Subtitles change every few seconds, not every 16ms. Running an endless 60 FPS tick causes continuous CPU wakeups. | **Tradeoff**: Minor complexity in calculating transition boundaries (`getTimeUntilNextChange`). **Status**: event-driven since 2026-10-03; zero wakeups while paused or between changes. |
| **4. Zero-Auth Community Proxy as Default Subtitle Source** | Requiring users to sign up for OpenSubtitles API keys, manage rate limits, or pay for VIP access creates friction. | **Tradeoff**: Relies on public Stremio community proxy availability. **Intended mitigation**: `CompositeSubtitleProvider` with YTS, optional official API keys, and phone `.srt` uploads. **Status**: YTS and the official API are not reachable yet (KI-2), so only uploads back up Stremio today. |
| **5. Embedded Phone Web Remote (Port 8080)** | Entering text queries and adjusting millisecond subtitle sync on TV remotes with a D-pad is painfully slow. | **Tradeoff**: Runs a micro-server daemon inside the app. **Mitigation**: Uses NanoHTTPD (50KB binary, < 2MB RAM) rather than a heavy framework like Ktor Server. |
| **6. Dual Launcher Intent Filters** | AlterSub declares both `LEANBACK_LAUNCHER` and standard `LAUNCHER`. | Allows the app to be launched, tested, and inspected on standard Android phones, tablets, emulators, and Android TV boxes without code changes. **Caveat**: on Android 12+ touch devices the full-screen overlay is expected to block touches to other apps (KI-11). |
| **7. MediaSession as the Authority for Content** | Scraped screen text is noisy (row headers, menus); the session title is what the player itself reports. | **Tradeoff**: a session that reports a generic or partial title (e.g. just the app name) now overrides accessibility scraping (KI-6). The user can still override it via manual search. |

---

## 5. Verification & Test Status

### 5.1 Automated Unit Tests
* **Test Runner**: Gradle JUnit 4 on the JVM, with the real `org.json` artifact on the test classpath (Android's stub would throw).
* **Status (2026-10-03)**: 41 tests, all passing offline. The one live-network test (`StremioSubtitleProviderLiveTest`) is skipped unless run with `-PliveTests`.
* **Test Suites**:
  * [`DetectionArbiterTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/DetectionArbiterTest.kt): MediaSession outranks scraping; a manual choice holds until the session title changes; scraping resumes after sessions end. (Passes)
  * [`SubtitleClockTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/core/clock/SubtitleClockTest.kt): MediaSession position extrapolation (elapsed time × speed, paused, missing/future snapshot, zero speed). (Passes)
  * [`CompositeSubtitleProviderTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/provider/CompositeSubtitleProviderTest.kt): phone uploads are only offered for their own content; uploads with no detected content are never re-offered. (Passes)
  * [`SrtParserTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/core/parser/SrtParserTest.kt): Verifies timestamp conversions (`00:01:23,456` $\rightarrow$ ms), multi-line cues, HTML tag cleanup (`<i>`, `<b>`), and binary search interval queries. (Passes)
  * [`TitleSanitizerTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/TitleSanitizerTest.kt): Verifies regex extraction of `Stranger Things S04E01`, `Wednesday Season 1 Episode 3`, `Inception (2010)`, and rejection of UI junk like `Audio & Subtitles`. (Passes)
  * [`StremioSubtitleProviderLiveTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/provider/StremioSubtitleProviderLiveTest.kt): Live integration test connecting to the internet, querying for "Inception" (`tt1375666`), and returning 5 real English `.srt` download URLs without authentication. (Passes)

### 5.2 Device & Emulator Verification
* **Device Tested**: `Medium_Phone_API_35` (Android 15 / API 35 x86_64) **only**. This is a phone emulator, not a TV.
* **Overlay Verification**:
  * Tested with the in-app "Test Subtitle Overlay" button, rendering over AlterSub's own settings screen.
  * Confirmed: High-contrast yellow stroked text, centered bounding box, auto-scaling, and proper layering over system views.
* **Web Remote Verification**:
  * Port forwarded host `tcp:8888` to emulator `tcp:8080`.
  * Verified HTTP GET `/` returns HTML.
  * Verified HTTP POST `/api/offset?delta=500` updates internal monotonic clock to `500ms`.
  * Verified HTTP GET `/api/status` returns live JSON payload.
* **Web Remote Verification (2026-10-03, same emulator)**:
  * `POST /api/seek?positionMs=2483000` set the clock to 41:23, and it advanced in real time once started. Invalid or negative input returns 400.
  * Typing `1:05:10` into the remote's "Set time" field (mobile viewport) set the clock to 1:05:10.
  * Upload scoping:
    1. Search "Inception", then upload an `.srt` (the upload activates).
    2. Search "Interstellar" (Interstellar's own track activates; the upload does not follow).
    3. Search "Inception" again (the upload is offered first and re-activated).
  * Back-to-back searches (three pairs): only the second title's track was activated each time, and no tracks from the first search leaked into the list.
* **Android TV Emulator Verification (2026-10-03)**: AVD `Android_TV_API_28`. That's Android 9 / API 28 (`sdk_google_atv_x86`), 1920×1080 at 320 dpi, with 1GB RAM to mimic target boxes. See §6 for setup.
  * **Install & permissions**: The APK installs and launches on API 28. The three `adb` permission grants in §6 work unchanged, and no crashes were logged.
  * **TV launcher**: The app is registered as a `LEANBACK_LAUNCHER` app and appears in the TV launcher's Apps list.
  * **D-pad**: Three DPAD_DOWN presses reach "Test Subtitle Overlay", and DPAD_CENTER starts it. However, the focused button is only partly scrolled into view (KI-25).
  * **Overlay**: Renders at 1080p, as the topmost `APPLICATION_OVERLAY` window, in a foreground service (`foregroundId=1001`). Subtitles appeared within ~0.5s of the key press.
  * **Rule 4 (no focus stealing)**: With the overlay visible, `mCurrentFocus` stays on the app beneath and DPAD_UP keeps moving focus between its buttons. HOME reaches the TV launcher.
  * **Web remote end to end**: Search "Inception" (5 tracks, 1,190 cues activated), then `seek` to 10:42 and pause. The matching line ("Is out.") rendered over the TV home screen.
  * **Memory**: `dumpsys meminfo com.altersub` showed TOTAL PSS ≈ 49 MB (Java heap 11 MB, native 17.7 MB). That was with the settings activity still in the back stack, plus the overlay, both services and the web server running.
  * **Reproduced KI-3/KI-4/KI-5**: Pressing HOME let the accessibility service scrape `com.google.android.tvlauncher` (accepted because the package name contains "tv"). It took the "CUSTOMIZE CHANNELS" button as a title, searched for it, and activated 2,007 cues of an unrelated film over the home screen, with no streaming app involved.
  * **Reproduced KI-17**: Accessibility and notification access were both enabled, yet both rows still showed "ENABLE". Since fixed (§7.5).

### 5.3 Not Yet Verified
* Any physical Android TV device. API 28 has only been exercised on the emulator above.
* Any real streaming app: Netflix, Prime Video, Disney+, Hotstar, YouTube. These generally won't install or play on emulator images, which lack Play certification and hardware DRM (Widevine L1).
* MediaSession detection and position sync against a real player, and the `DetectionArbiter` rules with a live session. Accessibility scraping has been observed on the emulator (only on the TV launcher, see above).
* Overlay rendering over DRM-protected video, and performance on real 1GB-RAM hardware. Emulator memory figures are indicative only.

See KI-1.

---

## 6. How to Build & Deploy

### Prerequisites
* Java JDK 17 or 21.
* Android SDK (Platforms: 28 through 34).

### Gradle Commands
```powershell
# Run all unit tests (offline; live-network tests are skipped)
.\gradlew.bat testDebugUnitTest

# Include tests that hit real network services
.\gradlew.bat testDebugUnitTest -PliveTests

# Assemble the debug APK
.\gradlew.bat assembleDebug

# Install directly on a connected device/emulator
.\gradlew.bat installDebug
```

### Fast ADB Deployment to Android TV
```powershell
# Connect over Wi-Fi
adb connect <TV_IP>:5555

# Install APK
adb -s <TV_IP>:5555 install -r app\build\outputs\apk\debug\app-debug.apk

# Grant all required permissions in one command
adb -s <TV_IP>:5555 shell "appops set com.altersub SYSTEM_ALERT_WINDOW allow && settings put secure enabled_accessibility_services com.altersub/com.altersub.service.AccessibilityInspectorService && settings put secure accessibility_enabled 1 && cmd notification allow_listener com.altersub/com.altersub.service.MediaNotificationListener"
```

### Android TV Emulator (Android 9 / API 28)
Easiest path: Android Studio → **Device Manager → Create Virtual Device → TV → Television (1080p)** → system image **Pie (API 28) Android TV x86**. Then set RAM to **1024 MB** under advanced settings to mimic target boxes. The API 28 TV image requires accepting the **Android SDK Preview License**.

Command-line equivalent (requires *Android SDK Command-line Tools*):
```powershell
# Install the image. The new `android` CLI uses slash-separated package paths.
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\android.exe" sdk install system-images/android-28/android-tv/x86

# Create the AVD. avdmanager's launcher misreads a bare Java "21" as < 17, so point JAVA_HOME at
# Android Studio's bundled JBR. Use --% so cmd.exe doesn't split the package id on ';'.
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'   # adjust to your install's jbr folder
'no' | & "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" --% create avd -n Android_TV_API_28 -k "system-images;android-28;android-tv;x86" -d tv_1080p

# In %USERPROFILE%\.android\avd\Android_TV_API_28.avd\config.ini set:
#   hw.ramSize=1024   hw.gpu.enabled=yes   hw.keyboard=yes

# Boot
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Android_TV_API_28 -gpu auto
```
Drive it with D-pad key events, e.g. `adb shell input keyevent KEYCODE_DPAD_DOWN` / `KEYCODE_DPAD_CENTER` / `KEYCODE_HOME`, or with the host keyboard's arrow keys.

---

## 7. Known Issues & Implications

> Last reviewed **2026-10-03** (full code review + emulator testing). Each issue has a stable ID (`KI-n`) so commits, docs and agents can reference it.
>
> * **Severity**:
>   * **High**: the core flow (detect → find → sync) can fail or show the wrong subtitles on a real device.
>   * **Medium**: security/privacy exposure, or a platform behaviour that degrades the experience.
>   * **Low**: robustness, performance hygiene, testing, or docs.
> * **Status**:
>   * **Confirmed**: verified in code and/or reproduced.
>   * **Expected**: follows from platform rules, not yet reproduced on a device.

### 7.1 Summary

| ID | Severity | Area | Issue |
| :--- | :--- | :--- | :--- |
| KI-1 | High | Verification | Never run on a physical TV or with any streaming app (TV emulator on API 28 now verified) |
| KI-2 | High | Sourcing | Only the Stremio source can return results; YTS and official API unreachable |
| KI-3 | High | Detection | App package filter matches the TV launcher, Settings, and other non-streaming apps |
| KI-4 | High | Detection | `TitleSanitizer` turns sequels into episodes and misreads numbers as years |
| KI-5 | High | Detection | Accessibility takes the first surviving text node as the title |
| KI-6 | Medium | Detection | MediaSession title is trusted even if generic or partial |
| KI-7 | Medium | Security | Web remote is unauthenticated, LAN-wide, and always on |
| KI-8 | Medium | Security | Stored XSS in the web remote's track list |
| KI-9 | Medium | Security | Uploads have no size limit or content validation |
| KI-10 | Medium | Privacy / Distribution | Accessibility service watches every app and requests unused capabilities |
| KI-11 | Medium | Platform | Full-screen overlay window: touch blocking on phones, extra compositing on TVs |
| KI-12 | Medium | Platform | Overlay foreground service never stops once started |
| KI-18 | Medium | Timing | Multiple active media sessions all drive the same clock |
| KI-22 | Low | Testing | No tests for sleep calculation, provider parsing, web server, orchestration |
| KI-24 | Low | Build / Config | Unused Leanback dependency; unnecessary `usesCleartextTraffic` |
| KI-25 | Low | UI | TV setup screen: focused button only partly scrolled into view; weak focus highlight |

### 7.2 High Severity

#### KI-1 · Never verified on the target platform — *Confirmed (partly addressed)*
* **Issue**: Testing so far ran on emulators only. Since 2026-10-03 that includes an Android TV 9 (API 28, 1GB) emulator, on which the overlay, D-pad behaviour, TV launcher and web remote work (§5.2). Nothing has run on a physical TV or alongside any real streaming app, which emulator images generally can't run (no Play certification, no Widevine L1).
* **Implication**: The project's central premise is unproven. That premise is that Netflix/Prime/Disney+ on Android TV publish a MediaSession with a usable title and position, or expose title text to accessibility. If they don't, automatic detection and sync do nothing. The user is left with manual search plus "Set time", which does work.
* **Fix direction**: Install on a real TV, enable verbose logging in `MediaNotificationListener` and `AccessibilityInspectorService`, and record what each target app actually reports (title keys, position updates, `lastPositionUpdateTime`). This result should drive the priority of KI-3 to KI-6.

#### KI-2 · Only one subtitle source actually works — *Confirmed*
* **Where**: `YtsSubtitleProvider.search` (requires `imdbId`), `OpenSubtitlesApiProvider.isEnabled` (requires an API key), `StremioSubtitleProvider.resolveImdbId`.
* **Issue**:
  * Detection never sets `ContentMetadata.imdbId`. Stremio resolves an IMDb ID through Cinemeta but keeps it private, so YTS always returns nothing.
  * `OpenSubtitlesApiProvider.updateCredentials()` is never called and there is no UI to enter a key, so the official API is never enabled.
* **Implication**:
  * Every automatic search depends on two public Stremio endpoints (`opensubtitles-v3.strem.io`, `v3-cinemeta.strem.io`). If they are down, rate-limited or change format, no subtitles are found and there is no fallback.
  * The "multi-source" resilience described in §3.4 and §4 does not exist yet.
  * Cinemeta's first search hit is used unconditionally, so ambiguous titles can resolve to the wrong film.
* **Fix direction**: Resolve the IMDb ID once (in the composite or a resolver) and pass it to all providers. Add API-key entry, e.g. a web remote settings card persisted to `SharedPreferences`. Consider year-aware candidate selection.

#### KI-3 · Package filter is far too broad — *Confirmed (reproduced on the Android TV 9 emulator)*
* **Observed**: Pressing HOME let the accessibility service scrape `com.google.android.tvlauncher`. Within ~1s it detected the launcher's "CUSTOMIZE CHANNELS" button as a title, searched for it, and activated 2,007 cues of an unrelated film over the home screen (§5.2).
* **Where**: `AppPackageFilter.isTargetApp` accepts any package containing `video`, `media`, or `tv`.
* **Issue**: This matches `com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx` (Google TV home), `com.android.tv.settings`, media providers, and any music or IPTV app with those substrings.
* **Implication**:
  * Accessibility scraping runs on the home screen and Settings when no media session is active. It searches for row titles like "For you" or "Apps", wasting network calls and potentially activating wrong subtitles.
  * Sessions from unrelated apps (e.g. a music app) can drive the clock and content (see KI-18).
* **Fix direction**: Use the explicit package allowlist only. Optionally let the user add packages from the web remote.

#### KI-4 · TitleSanitizer misparses common movie titles — *Confirmed (reproduced with the same regexes)*
* **Issue**:
  * The standalone-episode regex `(?:e|ep|episode)\s*(\d{1,3})` has no word boundary, so `Despicable Me 2` and `The Lego Movie 2` become S1E2, and `Se7en` becomes S1E7.
  * The year regex takes in-title numbers: `Blade Runner 2049` gets year 2049 and is shortened to "Blade Runner", and `Wonder Woman 1984` gets year 1984.
  * The UI-junk filter is an exact-match list, so `Trending Now`, `My List` and `Continue Watching` pass as titles.
* **Implication**:
  * Sequels are searched as TV series, which skips YTS and usually finds nothing or the wrong thing.
  * Scraped menu text becomes "content".
  * Manual searches are deliberately *not* run through the sanitizer until this is fixed.
* **Fix direction**:
  * Require word boundaries and an explicit episode token (`\bE\d`, `\bEp\.?\s*\d`, `\bEpisode\s+\d`).
  * Only treat parenthesised or bracketed years, or trailing years, as release years.
  * Expand the junk filter to prefix/contains rules.
  * Add tests with real sequel titles.

#### KI-5 · Accessibility picks the first surviving text as the title — *Confirmed*
* **Where**: `AccessibilityInspectorService.inspectNodeHierarchy`.
* **Issue**: Text nodes are collected depth-first (max depth 6, max 25 nodes), and the first one the sanitizer doesn't reject wins. There is no notion of "title card", font size, or position.
* **Implication**: The fallback detector is essentially random on rich UIs, and depth 6 may be too shallow for modern players. Since KI-1 is unverified, it's unknown whether this path ever finds the real title.
* **Fix direction**:
  * Score candidates (SxxExx present, length, node class/viewId hints per app).
  * Require the same title to be seen twice before switching.
  * Add per-app view-ID rules once real hierarchies are captured.

### 7.3 Medium Severity

#### KI-6 · MediaSession title is trusted blindly — *Expected (design trade-off)*
* **Issue**: `DetectionArbiter` makes any sanitized session title authoritative and disables scraping while the session lives. Some apps may report only the app name, an episode name without the show (with the show in `METADATA_KEY_ARTIST`/`ALBUM`), or a localized title.
* **Implication**: A bad session title triggers a wrong search, and the accessibility fallback can't correct it. The user must manual-search, and that choice holds until the session title changes.
* **Fix direction**: Reject titles equal to the app label, and combine `TITLE` with `ARTIST`/`ALBUM`/`DISPLAY_SUBTITLE` per app. Validate on device (KI-1).

#### KI-7 · Web remote is open to the whole LAN — *Confirmed*
* **Where**: `AlterSubApp.onCreate` starts NanoHTTPD on `0.0.0.0:8080`, with no stop path.
* **Issue**: There is no PIN or token. The accessibility and notification services keep the process alive, so the server effectively runs permanently.
* **Implication**:
  * Anyone on the same network (guest Wi-Fi, housemates, a compromised IoT device) can upload files, trigger searches, change sync, or select tracks.
  * Port 8080 is a common default (e.g. Kodi's web interface). If it's taken, the server fails to start, and that is only logged, so the TV still shows the URL.
* **Fix direction**:
  * Show a short PIN or QR code with a token on the TV, and require it on `/api/*`.
  * Bind only while the overlay is active, or offer an off switch.
  * Fall back to another port and display the one actually bound.

#### KI-8 · Stored XSS in the web remote — *Confirmed*
* **Where**: `renderTracks()` in `WebRemoteHtml.kt` builds HTML with unescaped `t.title`, `t.source`, `t.id` and `t.language`.
* **Issue**: Track titles come from OpenSubtitles release names (uploader-controlled), manual search queries, and scraped screen text.
* **Implication**:
  * A crafted release name or search query runs script in every phone viewing the remote. That script can drive all the unauthenticated endpoints (KI-7).
  * Titles containing `<` or `'` also break the list or the `onclick` handler.
* **Fix direction**: Build the list with `document.createElement` + `textContent`, and attach handlers with `addEventListener`.

#### KI-9 · Uploads are not limited or validated — *Confirmed*
* **Where**: `WebRemoteServer.handleUpload`.
* **Issue**: There is no size cap. Any file is saved as `.srt` in `cacheDir/uploads`, and a file that parses to 0 cues is still marked active. Neither `cacheDir/uploads` nor `cacheDir/subtitles` is ever pruned.
* **Implication**:
  * Large uploads can exhaust storage on low-storage TV boxes.
  * The remote shows "Active: Uploaded Subtitle" while nothing ever renders.
* **Fix direction**: Reject uploads over ~2MB. Reject files that parse to 0 cues, and report the error to the phone. Prune old cache files.

#### KI-10 · Accessibility service scope and policy risk — *Confirmed*
* **Where**: `res/xml/accessibility_service_config.xml`.
* **Issue**: There is no `android:packageNames` restriction, so the service receives events from every app. It uses `flagIncludeNotImportantViews`, and declares `canRequestFilterKeyEvents` and `typeViewClicked` without using them.
* **Implication**:
  * There is extra CPU on every UI event system-wide. The arbiter now skips the tree walk while a session or manual choice is active, but events are still delivered.
  * The privacy surface is broader than necessary.
  * Google Play's AccessibilityService policy is likely to reject this non-accessibility use, so distribution is realistically sideload-only.
* **Fix direction**: Set `packageNames` to the streaming allowlist (KI-3), and remove the unused capabilities and event types.

#### KI-11 · Full-screen overlay window — *Expected (not reproduced)*
* **Where**: `SubtitleOverlayService.attachOverlay` uses `MATCH_PARENT × MATCH_PARENT`, window alpha 1.0.
* **Issue**: Android 12+ blocks touches that pass through `TYPE_APPLICATION_OVERLAY` windows whose window opacity is above 0.8. This is based on window alpha, not pixel transparency.
* **Implication**:
  * On phones and tablets (Decision #6), touches to other apps are expected to be blocked while the overlay is up. TV D-pad input is unaffected.
  * On TVs, a full-screen translucent layer over secure video may cost extra compositing work on weak SoCs.
* **Fix direction**: Use a bottom-anchored window sized to the subtitle area, or set `layoutParams.alpha = 0.8f`.

#### KI-12 · Overlay service never stops — *Confirmed*
* **Issue**: No code path calls `stopSelf()` or `stopService()`. After the first activation, the foreground notification, overlay window and render loop live until the process dies.
* **Implication**: A persistent notification and a permanent window layer, even with no content playing.
* **Fix direction**: Stop the service when there has been no subtitle index and no active session for N minutes, or when the user disables it from the remote.

#### KI-18 · Multiple media sessions share one clock — *Confirmed*
* **Where**: `MediaNotificationListener` registers one callback on every target controller.
* **Issue**: If two target apps have active sessions (e.g. YouTube paused in the background while Netflix plays), both feed `syncWithExternalPosition` and `onContentDetected`.
* **Implication**: The clock can jump between two unrelated positions, and content may flip between titles.
* **Fix direction**: Follow only the controller that is `STATE_PLAYING` (or the most recently active one), using a per-controller callback that knows its package.

### 7.4 Low Severity

| ID | Issue | Implication | Fix direction |
| :--- | :--- | :--- | :--- |
| KI-22 | No tests for `SubtitleIndex.getTimeUntilNextChange`, provider JSON parsing, `WebRemoteServer` routes, or `AlterSubApp` orchestration. | Regressions in sync timing, provider format changes, and endpoint behaviour go unnoticed. | Unit-test the index; MockWebServer for providers; extract orchestration from `Application` for JVM tests. |
| KI-24 | `androidx.leanback` is declared but unused; `android:usesCleartextTraffic="true"` though all outbound calls are HTTPS (inbound server traffic is unaffected by this flag). | Larger APK than necessary; cleartext is allowed for no reason. | Remove both. |
| KI-25 | On the 1080p TV emulator, D-pad focus reaches "Test Subtitle Overlay", but the `ScrollView` (32dp padding) leaves the button mostly below the visible area. Default AppCompat buttons give only a faint raised-shadow focus cue. | From the couch, users can't see which button is focused or what they're about to press. | Bottom padding inside the scrolled content (or `clipToPadding=false`); a TV focus style (scale + bright outline) via a state-list drawable, or Leanback/`androidx.tv` components. |

### 7.5 Resolved

| Date | Issue | Resolution |
| :--- | :--- | :--- |
| 2026-10-03 | A phone upload was prepended to *every* search, so it auto-activated for every later title. | Uploads are keyed to the content detected at upload time (`CompositeSubtitleProvider.localTracksFor`). |
| 2026-10-03 | Concurrent searches/downloads raced; an older one finishing late could activate the wrong title's subtitles, and old subtitles stayed on screen for new content. | Search and activation jobs are cancelled on change; results are discarded if the content changed; state is cleared on change; a pending auto-pick never overrides the user's choice. |
| 2026-10-03 | Accessibility scraping could override what the media session reported. | `DetectionArbiter`: MediaSession > manual choice > accessibility; scraping is skipped while it would be ignored. |
| 2026-10-03 | The MediaSession position was used as-is, even though it can be stale by seconds or minutes. | Extrapolated from `lastPositionUpdateTime` × speed; an unknown position only updates play/pause. |
| 2026-10-03 | There was no way to set the clock position if the app publishes no position. | `POST /api/seek` + "Set time" field in the web remote; `positionMs` added to `/api/status`. |
| 2026-10-03 | `SubtitleClock` was mutated from several threads without synchronization. | Mutators and readers are `@Synchronized`; the offset is updated atomically. |
| 2026-10-03 | **KI-23**: `architecture-plan.pdf` was an outdated, image-only design plan describing unbuilt components, and its MediaProjection OCR strategy contradicted AGENTS.md Rule 1. | Deleted, and later stripped from git history along with old build output. This document and AGENTS.md are the design references. |
| 2026-10-03 | **KI-13**: the render loop slept at most 500ms (≥2 wakeups/s) and posted to the UI thread every tick. | Event-driven loop over `clock.changes` + the active index: sleeps exactly to the next cue boundary, never wakes while paused, and posts only when the text changes. |
| 2026-10-03 | **KI-14**: `SubtitleTextView.onDraw` split the text on every draw; `SrtParser` and `TitleSanitizer` compiled regexes on every line/call. | Lines are split once in `setSubtitle` and drawn by index (no allocation in `onDraw`); all regexes and the UI-junk set are precompiled fields. |
| 2026-10-03 | **KI-15**: the parser read UTF-8 only, showed one of several overlapping cues, merged cues when a blank line was missing, and dropped hour-less VTT timestamps. | BOM / BOM-less UTF-16 / strict-UTF-8 detection with Windows-1252 fallback; an overlap-aware index (`getTextAt`) showing all active cues; recovery from missing separators; VTT `mm:ss.mmm`, cue settings and HTML entities. |
| 2026-10-03 | **KI-16**: the overlay foreground service was only ever started on demand from background contexts, and a refused start or missing overlay permission failed silently or crashed the service. | Started from `MainActivity.onResume` on TV devices and kept alive; no redundant restarts while running; failures are caught and surfaced as `overlayError` in `/api/status` and the phone remote. Re-test background starts when raising `targetSdk`. |
| 2026-10-03 | **KI-17**: the TV setup screen only showed the overlay permission state; subtitle size/colour/position could not be changed. | Accessibility and notification-access states are read on resume (✅/❌ + buttons disabled when granted); a "Subtitle style" card on the phone remote (`/api/style`) adjusts size, colour and position live, persisted across restarts. Also fixed while verifying: the three permission buttons crashed the app on Android TV builds without those settings screens (`ActivityNotFoundException`); they now show the ADB grant command instead. |
| 2026-10-03 | **KI-19**: the user sync offset carried over from one title to the next. | `TrackOffsets` remembers the offset per subtitle track: content changes reset it to 0, and switching back to a track restores its own offset. |
| 2026-10-03 | **KI-20**: three separate OkHttpClients, unclosed non-2xx responses, and blocking `execute()` calls that ignored coroutine cancellation. | One shared `Http.client`; every response closed via `use { }`; a cancellable `Call.await()` cancels the HTTP call with the coroutine (providers re-throw `CancellationException` instead of swallowing it). |
| 2026-10-03 | **KI-21**: the live Stremio test ran in `testDebugUnitTest`, which AGENTS.md requires before every commit, so commits failed offline. | Live-network tests are skipped via `Assume` unless Gradle is run with `-PliveTests` (passed to the test JVM as `altersub.liveTests`). |

---

## 8. Current Project State & Next Steps

* **Current Status**: Prototype / alpha.
  * **Works today**: builds and unit tests. On an Android TV 9 (API 28, 1GB) emulator, the overlay renders at 1080p without stealing D-pad focus. The web remote works end to end: manual search with automatic Stremio download, upload, track selection, offset, and "Set time".
  * **Unproven**: automatic detection and sync against real streaming apps on a physical Android TV (KI-1). On the emulator, accessibility auto-detection fired on the TV launcher's UI text (KI-3).
* **Artifact Location**: `app/build/outputs/apk/debug/app-debug.apk` (~10.9 MB).
* **Recommended Next Steps** (in order):
  1. **Device validation (KI-1)**: real Android TV + Netflix/Prime/Disney+; record MediaSession and accessibility output per app.
  2. **Sourcing resilience (KI-2)**: propagate the IMDb ID so YTS works; add OpenSubtitles API-key entry.
  3. **Detection accuracy (KI-3, KI-4, KI-5, KI-6)**: explicit package allowlist, sanitizer fixes with real-title tests, candidate scoring.
  4. **Web remote hardening (KI-7, KI-8, KI-9)**: PIN/token, escaped rendering, upload limits.
  5. **Overlay lifecycle (KI-11, KI-12)**: bottom-anchored window, stop when idle.
* **Potential Future Enhancements**:
  1. **TMDb Direct API integration**: For exotic media titles where Cinemeta auto-resolution returns multiple candidates.
  2. **ASS / SSA Styled Subtitles**: Parser currently strips advanced ASS vector tags to plain text; could optionally parse colored dialogue tags.
  3. **SMB / Local Network Storage Explorer**: Allow reading `.srt` files directly from a network-attached storage (NAS) or local shared folder.
