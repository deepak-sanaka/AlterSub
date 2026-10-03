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
│   │   │   │   ├── AlterSubApp.kt                   # Application: owns clock, session, style, overlay state; implements RemoteController
│   │   │   │   ├── core/
│   │   │   │   │   ├── clock/
│   │   │   │   │   │   ├── SubtitleClock.kt         # Monotonic clock, play/pause tracker, +/- ms offset, change signal
│   │   │   │   │   │   └── TrackOffsets.kt          # Sync offset remembered per subtitle track
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── ContentMetadata.kt       # Structured title, season, episode, IMDb ID
│   │   │   │   │   │   ├── PlaybackStateInfo.kt     # Playing status, time position, speed, package
│   │   │   │   │   │   ├── SubtitleCue.kt           # Start/end timestamps (ms), text lines
│   │   │   │   │   │   ├── SubtitleStyle.kt         # User subtitle size/colour/position with clamping
│   │   │   │   │   │   └── SubtitleTrack.kt         # Track metadata (source, URL, language, rating)
│   │   │   │   │   ├── parser/
│   │   │   │   │   │   ├── SrtParser.kt             # SRT/WebVTT parser: BOM/UTF-16/Windows-1252 detection, markup + entity cleanup
│   │   │   │   │   │   └── SubtitleIndex.kt         # Binary search index (overlap-aware) + next-boundary calculator
│   │   │   │   │   └── session/
│   │   │   │   │       └── SubtitleSession.kt       # Detection → search → download → active track (race-safe, JVM-testable)
│   │   │   │   ├── detection/
│   │   │   │   │   ├── AppPackageFilter.kt          # Target streaming apps (Netflix, Prime, Disney+, etc.)
│   │   │   │   │   ├── DetectionArbiter.kt          # Source priority: MediaSession > manual choice > accessibility
│   │   │   │   │   └── TitleSanitizer.kt            # Regex parser for clean show title, SxxExx, year extraction
│   │   │   │   ├── provider/
│   │   │   │   │   ├── SubtitleProvider.kt          # Base interface for subtitle sources
│   │   │   │   │   ├── Http.kt                      # Shared OkHttpClient + cancellable Call.await()
│   │   │   │   │   ├── StremioSubtitleProvider.kt   # Zero-auth public OpenSubtitles v3 proxy (movies & series)
│   │   │   │   │   ├── YtsSubtitleProvider.kt       # Zero-auth movie subtitle mirror with ZIP unpacker
│   │   │   │   │   ├── OpenSubtitlesApiProvider.kt  # Official OpenSubtitles.com REST API (API key + token)
│   │   │   │   │   └── CompositeSubtitleProvider.kt # Parallel search over a provider list & per-content upload repository
│   │   │   │   ├── server/
│   │   │   │   │   ├── RemoteAuth.kt                # PIN pairing + per-phone tokens for the web remote
│   │   │   │   │   ├── RemoteController.kt          # What the web remote can read/do (AlterSubApp implements it)
│   │   │   │   │   ├── WebRemoteHtml.kt             # Responsive dark-mode mobile web UI for remote control
│   │   │   │   │   └── WebRemoteServer.kt           # Embedded NanoHTTPD micro-server (port 8080, falls back to 8081–8089)
│   │   │   │   ├── service/
│   │   │   │   │   ├── AccessibilityInspectorService.kt # View hierarchy scraper for OSD / title cards
│   │   │   │   │   ├── MediaNotificationListener.kt # Notification listener for MediaSession play/pause tokens
│   │   │   │   │   └── SubtitleOverlayService.kt    # Foreground service managing TYPE_APPLICATION_OVERLAY
│   │   │   │   └── ui/
│   │   │   │       ├── overlay/
│   │   │   │       │   └── SubtitleTextView.kt      # Hardware-accelerated canvas with stroked text & auto-fit
│   │   │   │       ├── AppFont.kt                   # Applies AlterSub Sans in code (one shared font collection)
│   │   │       └── settings/
│   │   │   │           ├── MainActivity.kt          # TV setup screen: setup checklist, phone-remote QR/PIN, test trigger
│   │   │   │           └── QrCode.kt                # ZXing QR → 1-px-per-module bitmap, scaled up unfiltered
│   │   │   └── res/
│   │   │       ├── drawable/                        # Launcher icons, flat card/button/chip shapes, status icons
│   │   │       ├── font/                            # AlterSub Sans (app_sans.xml + 3 static TTFs from Google Sans Flex)
│   │   │       ├── layout/activity_main.xml         # Two-column TV setup layout (plain AppCompat Views)
│   │   │       ├── values/                          # colors, strings, styles
│   │   │       └── xml/accessibility_service_config.xml # Accessibility config with event throttling
│   │   └── test/java/com/altersub/
│   │       ├── core/clock/                          # SubtitleClockTest (position extrapolation), TrackOffsetsTest
│   │       ├── core/model/SubtitleStyleTest.kt      # Style clamping and colour validation
│   │       ├── core/parser/                         # SrtParserTest (encodings, VTT, malformed SRT), SubtitleIndexTest
│   │       ├── core/session/SubtitleSessionTest.kt  # Orchestration races with a controllable fake provider
│   │       ├── detection/                           # DetectionArbiterTest, TitleSanitizerTest
│   │       ├── provider/                            # MockWebServer tests per provider, HttpAwaitTest, CompositeSubtitleProviderTest,
│   │       │                                        #   StremioSubtitleProviderLiveTest (real network, only with -PliveTests)
│   │       ├── server/                              # WebRemoteServerTest (every route + token checks, port fallback), RemoteAuthTest
│   │       └── ui/settings/QrCodeTest.kt            # The pairing link encodes and decodes back intact
│   ├── src/main/assets/licenses/                    # OFL licence for the bundled UI font
│   ├── build.gradle.kts                             # App module build configuration
│   └── proguard-rules.pro                           # R8 rules for release builds (no blanket keeps; see the file)
├── docs/
│   └── PROJECT_STATE.md                             # This file
├── tools/build_app_font.py                          # Rebuilds the UI font files from upstream Google Sans Flex
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
* **Strategy A2 — Session polling for low-RAM TVs (`MediaSessionPoller`)**:
  * Android refuses notification-listener access to every non-system app on low-RAM devices (`ro.config.low_ram=true`, common on 1–2 GB TVs, e.g. the TV tested in §5.2), so Strategy A can't run there.
  * Instead, with the DUMP permission granted once over ADB (`adb shell pm grant com.altersub android.permission.DUMP`), AlterSub reads the `media_session` service's dump directly over binder (no `dumpsys` process; falls back to spawning one), parses it with `MediaSessionDump`, and applies the active target session's state, position (extrapolated from its `updated` time) and title the same way as Strategy A.
  * Polls every 2 s while a streaming app has an active session, every 10 s otherwise, and stands aside whenever the notification listener is connected. Inactive sessions (Prime Video leaves one behind) are ignored. The clock is re-anchored only on a real change (pause, resume, seek, or >250 ms drift).
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
* Runs a micro HTTP server via NanoHTTPD, started in `AlterSubApp.onCreate` unless switched off on the TV.
* **Port**: `8080`, or the first free port in `8081–8089` if it's taken (e.g. by Kodi). The TV screen shows the URL with the port actually bound, or says that none was free.
* **Pairing** (`RemoteAuth`):
  * The TV setup screen shows a 6-digit PIN, valid only while that screen is open. A phone that enters it gets a random 128-bit token, kept in the page's `localStorage` and sent as `X-AlterSub-Token` on every `/api/*` call.
  * Without a valid token, every `/api/*` route except `/api/pair` returns 401 before reading the request (so unpaired uploads are never written). The page at `/` holds no data and stays public.
  * Five wrong PINs lock pairing until the TV screen is reopened, which issues a new PIN.
  * **QR code**: the TV shows a QR code for `http://<tv-ip>:<port>/#pin=<PIN>`. Scanning it opens the remote already paired. The PIN rides in the URL fragment, which browsers never send to the server, and the page removes it from the address bar once read. It changes whenever the PIN does.
  * **One phone at a time.** While a phone is paired, `/api/pair` returns 409 (even with the right PIN, without counting as a wrong guess), and the TV hides the QR/PIN and shows "Phone paired" with the address. Unpair from the TV (**Unpair phone**) or from the phone itself ("Unpair this phone", `POST /api/unpair`). The token persists across restarts (`SharedPreferences`; older builds' multi-phone lists keep only the newest).
  * The TV's "Phone remote" card has a status chip (Waiting for phone / Paired / Locked / Off), the QR code with three short scan steps, the address in large type (`192.168.x.x:8080`, no `http://` needed) with the PIN, and **Turn off / Turn on** (persisted).
* **Phone page**: single self-contained page; nothing loads from the internet. The UI font is served by the TV (`GET /fonts/app-sans-{regular,medium,bold}.ttf`, public, cached for a week, `font-display: swap`). Cards for Now playing, Timing (offset, clock, "Match the player's time"), Subtitles (search, track list, upload) and Appearance (size, position, colour swatches from the server's `palette`). A header pill shows whether the TV is reachable.
* **Endpoints**:
  * `GET /`: Serves complete, zero-dependency dark-mode HTML/CSS/JS remote.
  * `POST /api/pair?pin=<pin>`: Exchanges the TV's PIN for a token (403 wrong PIN or screen closed, 409 another phone is paired, 429 locked).
  * `POST /api/unpair`: The paired phone unpairs itself.
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
| **5. Embedded Phone Web Remote (Port 8080)** | Entering text queries and adjusting millisecond subtitle sync on TV remotes with a D-pad is painfully slow. | **Tradeoff**: Runs a micro-server daemon inside the app, reachable from the LAN. **Mitigation**: Uses NanoHTTPD (50KB binary, < 2MB RAM) rather than a heavy framework like Ktor Server. Only phones paired with the PIN on the TV screen can use the API, and the server can be switched off on the TV. Plain HTTP, so the token is visible to anyone sniffing the Wi-Fi. |
| **6. Dual Launcher Intent Filters** | AlterSub declares both `LEANBACK_LAUNCHER` and standard `LAUNCHER`. | Allows the app to be launched, tested, and inspected on standard Android phones, tablets, emulators, and Android TV boxes without code changes. **Caveat**: on Android 12+ touch devices the full-screen overlay is expected to block touches to other apps (KI-11). |
| **7. MediaSession as the Authority for Content** | Scraped screen text is noisy (row headers, menus); the session title is what the player itself reports. | **Tradeoff**: a session that reports a generic or partial title (e.g. just the app name) now overrides accessibility scraping (KI-6). The user can still override it via manual search. |

---

## 5. Verification & Test Status

### 5.1 Automated Unit Tests
* **Test Runner**: Gradle JUnit 4 on the JVM, with the real `org.json` artifact on the test classpath (Android's stub would throw).
* **Status (2026-10-03)**: 87 tests, all passing offline. The one live-network test (`StremioSubtitleProviderLiveTest`) is skipped unless run with `-PliveTests`.
* **Test Suites**:
  * [`DetectionArbiterTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/DetectionArbiterTest.kt): MediaSession outranks scraping; a manual choice holds until the session title changes; scraping resumes after sessions end. (Passes)
  * [`SubtitleClockTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/core/clock/SubtitleClockTest.kt): MediaSession position extrapolation (elapsed time × speed, paused, missing/future snapshot, zero speed). (Passes)
  * [`CompositeSubtitleProviderTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/provider/CompositeSubtitleProviderTest.kt): phone uploads are only offered for their own content; uploads with no detected content are never re-offered. (Passes)
  * [`SrtParserTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/core/parser/SrtParserTest.kt): Verifies timestamp conversions (`00:01:23,456` $\rightarrow$ ms), multi-line cues, HTML tag cleanup (`<i>`, `<b>`), and binary search interval queries. (Passes)
  * [`TitleSanitizerTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/TitleSanitizerTest.kt): Verifies regex extraction of `Stranger Things S04E01`, `Wednesday Season 1 Episode 3`, `Inception (2010)`, and rejection of UI junk like `Audio & Subtitles`. (Passes)
  * [`RemoteAuthTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/server/RemoteAuthTest.kt) and [`WebRemoteServerTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/server/WebRemoteServerTest.kt): PINs only while the TV screen is open, lockout after 5 wrong PINs, token persistence and the 8-phone limit; every route rejects missing/unknown tokens (an unpaired upload saves nothing and doesn't desync the connection); fallback to the next free port; the page never uses `innerHTML` (KI-8). (Passes)
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
  * **D-pad**: Three DPAD_DOWN presses reach "Test Subtitle Overlay", and DPAD_CENTER starts it. At the time, the focused button was only partly scrolled into view (KI-25, since fixed).
  * **Overlay**: Renders at 1080p, as the topmost `APPLICATION_OVERLAY` window, in a foreground service (`foregroundId=1001`). Subtitles appeared within ~0.5s of the key press.
  * **Rule 4 (no focus stealing)**: With the overlay visible, `mCurrentFocus` stays on the app beneath and DPAD_UP keeps moving focus between its buttons. HOME reaches the TV launcher.
  * **Web remote end to end**: Search "Inception" (5 tracks, 1,190 cues activated), then `seek` to 10:42 and pause. The matching line ("Is out.") rendered over the TV home screen.
  * **Memory**: `dumpsys meminfo com.altersub` showed TOTAL PSS ≈ 49 MB (Java heap 11 MB, native 17.7 MB). That was with the settings activity still in the back stack, plus the overlay, both services and the web server running.
  * **Reproduced KI-3/KI-4/KI-5**: Pressing HOME let the accessibility service scrape `com.google.android.tvlauncher` (accepted because the package name contains "tv"). It took the "CUSTOMIZE CHANNELS" button as a title, searched for it, and activated 2,007 cues of an unrelated film over the home screen, with no streaming app involved.
  * **Reproduced KI-17**: Accessibility and notification access were both enabled, yet both rows still showed "ENABLE". Since fixed.
  * **Web remote pairing (KI-7 fix)**:
    * The TV screen showed the URL and a grouped PIN ("177 770"). Unpaired `/api/status` returned 401.
    * In the phone page: a wrong PIN showed "Wrong PIN. 4 tries left." (not wiped by the status poll). The right PIN unlocked the remote; a +250 ms nudge applied, and the phone stayed paired after a reload and after force-stopping the app.
    * With the TV screen closed (HOME), the PIN was refused. After five wrong PINs, the TV showed the lockout message and even the right PIN got 429.
    * **Unpair All** emptied the stored tokens; the old token got 401 and the phone page fell back to the PIN prompt. D-pad focus moves to **Turn Off** instead of jumping up the screen when Unpair All disables itself.
    * **Turn Off** stopped the server. With port 8080 held by another process, **Turn On** bound 8081, the TV showed `:8081`, and the page loaded there.
  * **Track list escaping (KI-8 fix)**:
    * Rendering tracks whose title, source and language held `<img onerror>`, `<b onmouseover>` and `<svg onload>` payloads ran nothing and created no elements; the payloads showed as literal text, as did `Tom & Jerry's <Movie>`.
    * Clicking a track whose id was `x')+alert(1)+('` sent exactly that id to `/api/select-track`.
    * A real "Inception" search still listed 5 tracks, and clicking one switched the active track.
  * **UI refresh (two-column TV screen, QR pairing, new phone page)**:
    * The TV screen fits 960×540dp without scrolling. Initial D-pad focus lands on the first unfinished step's button (or the test button once all are done), and finished steps hide their buttons. With all three granted, the chip reads "Ready".
    * The QR code in a 1080p screenshot decoded (ZXing) to `http://192.168.232.2:8080/#pin=371785`, matching the PIN on screen. Opening that link in a browser with no stored token paired it and removed the PIN from the address bar.
    * Phone page at 375px: no horizontal scroll; search, track switching, timing and colour swatches all work; a wrong PIN shows the error on the pairing card.
    * **Single phone + font (later the same day)**: An install over a build with 3 stored tokens kept only the newest, so the TV opened in the "Paired" state. **Unpair phone** on the TV switched the card to the QR/PIN view live and moved focus to **Turn off**. Pairing through the QR link worked; a second pairing attempt with the correct PIN got 409 with an explanation; "Unpair this phone" on the phone cleared its token and returned it to the pairing card, and the TV went back to "Waiting for phone". The phone page loaded all three font weights from the TV.
    * **Footprint of the UI refresh + font** (clean debug builds; memory as total PSS, 3 runs each, ±3 MB run-to-run noise on the emulator):
      * Debug APK 8.90 MB → 9.72 MB (+0.82 MB): ZXing code +565 KB (debug builds are unshrunk), fonts +235 KB (stored uncompressed), resources/licence ~+12 KB.
      * Memory with the TV screen open 39.8–39.9 MB → 39.4–39.9 MB, in the background 40.3 → 40.1 MB: no measurable change.
      * The first font build set the font through the theme, which cost a steady **+4.2 MB of native memory** (framework and AppCompat each build a font collection carrying the full system fallback chain). Applying it in code via `ui/AppFont` (one shared typeface, weights derived from it) removed that entirely. Storing the TTFs uncompressed lets them be memory-mapped instead of inflated, and the web server no longer keeps a copy of the font bytes.
    * **R8 release build** (code + resource shrinking, no blanket keep rules), signed locally with the debug key for testing:
      * APK 8.17 MB (release, unshrunk) → 1.72 MB. Memory (total PSS, 3 runs) 33.3–33.5 MB → 28.8–29.3 MB, almost all from mapped code (10.6 → 6.3 MB). Start time unchanged (~0.83 s).
      * Everything checked on the shrunk build: TV screen (font, QR, icons, focus), test subtitles overlay, overlay/notification/accessibility services running (the accessibility service only bound after an emulator reboot, and the debug build behaved the same, so it is an emulator quirk), and every phone-remote route: page and fonts, 401 when unpaired, pairing, 409 for a second phone, a real Stremio search with download and activation, track switching, offset, seek, style + palette, upload, play toggle, and unpairing. No crashes.
    * **Cost on the emulator** (software GL, so absolute numbers are pessimistic; same key presses, old vs new screen): UI-thread time per frame ~1–2 ms for both, GPU command time 13.6–14.6 ms (old) vs 16–17.5 ms (new), total frame time 37–41 ms vs 37–42 ms. No frames are drawn while idle. PSS 42.7 MB (old) vs 42.1 MB (new). Card borders and a second full-screen background fill were removed to get there.

* **Real TV (2026-10-03)**: an Android 9 / API 28 TV with 1.9 GB RAM, 32-bit ARM (`armeabi-v7a`), 1080p at 320 dpi, **`ro.config.low_ram=true`**. Debug build over network ADB, logs recorded with `tools/capture_device_logs.sh`.
  * **Notification-listener access is impossible** on this TV: `cmd notification allow_listener` is silently ignored and the setting has no screen, because Android never grants it on low-RAM devices. Hence Strategy A2 (§3.2).
  * **Netflix** (`com.netflix.ninja`): its session reports accurate state and position (verified against AlterSub's clock to ±0.15 s) but **no metadata at all**, and its UI exposes **no accessibility text** (0 texts on every scrape). Netflix can't be identified automatically on Android TV; the user has to search from the phone.
  * **Hotstar** (`in.startv.hotstar`): its session carries the title ("India vs West Indies: 3rd ODI") and position. Its screen also exposed no accessibility text. **Prime Video** left an inactive, empty session behind.
  * **KI-3 reproduced on real hardware**: the launcher's long-press menu ("Context Menu") was taken as a title and searched.
  * **Sync test**: a feature film on Netflix with an English subtitle file timed for the same 126-minute cut. The file ran **10.75 s ahead** of Netflix's version (fixed with the phone's −/+ buttons, constant across the film). Manual "Set time" was hard to get right from Netflix's whole-second progress bar. With Strategy A2, playing, pausing, resuming and seeking were followed within ~2 s.
  * **Cost** (debug build, Netflix playing, phone remote open; 30 s samples): spawning `dumpsys` cost ~40 ms CPU per poll (2% of one core); the in-process binder dump removed that (child processes 0 ticks). What remains is mainly the phone page's 2 s status poll (NanoHTTPD starts a thread per request, ~1.2% of a core while the page is open) and accessibility events from the player (~1%). AlterSub's PSS was ~21–23 MB.
  * **Tooling note**: a network change on the TV breaks a running network `adb logcat` stream; the capture script now reconnects.

### 5.3 Not Yet Verified
* Prime Video, Disney+, YouTube and other apps' playback on the real TV (only Netflix playback and Hotstar's session were examined), and a TV that is *not* flagged low-RAM (Strategy A).
* Accessibility title detection on any streaming app: neither Netflix nor Hotstar exposed text.
* Long sessions on the real TV (memory growth, overlay over hours of playback) and release-build performance there.

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

# Assemble the debug APK (unshrunk, for development)
.\gradlew.bat assembleDebug

# Assemble the release APK: R8 code + resource shrinking. Output is unsigned until a release
# keystore is configured; keep app/build/outputs/mapping/release/mapping.txt for each shipped
# build, it is needed to read crash stack traces.
.\gradlew.bat assembleRelease

# Install directly on a connected device/emulator
.\gradlew.bat installDebug
```

### Fast ADB Deployment to Android TV
Enable **Developer options → USB debugging** on the TV. Most Android TVs then also accept ADB over the network on port 5555 (`adb mdns services` lists them); the first connection shows an "Allow debugging?" prompt on the TV. Android 11+ / Google TV may need **Wireless debugging → Pair device with pairing code** and `adb pair <ip>:<port> <code>` first.

```powershell
# Connect over Wi-Fi
adb connect <TV_IP>:5555

# Install APK
adb -s <TV_IP>:5555 install -r app\build\outputs\apk\debug\app-debug.apk

# Grant all required permissions in one command
adb -s <TV_IP>:5555 shell "appops set com.altersub SYSTEM_ALERT_WINDOW allow && settings put secure enabled_accessibility_services com.altersub/com.altersub.service.AccessibilityInspectorService && settings put secure accessibility_enabled 1 && cmd notification allow_listener com.altersub/com.altersub.service.MediaNotificationListener"
```

### Recording a Test Session on a Real TV
Use the **debug** build for testing: it adds detection diagnostics under the logcat tag `AlterSubDiag` (every app's media session and its metadata, the foreground package, the text the accessibility scraper saw and what it picked). Release builds strip these because they include other apps' screen text.

```bash
tools/capture_device_logs.sh <TV_IP>:5555   # Ctrl+C to stop
```
It writes `device-logs/<timestamp>/` (git-ignored): `device-info.txt` (model, Android version, RAM, installed streaming apps), `logcat.txt` (full logcat), and `snapshots.txt` (every 15 s: foreground window, media sessions, AlterSub memory).

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

To use the web remote from the host: `adb forward tcp:8888 tcp:8080` (use the port the TV screen shows), open `http://localhost:8888`, and enter the PIN from the AlterSub screen on the TV. Scripts can pair with `curl -X POST "http://localhost:8888/api/pair?pin=<PIN>"` and send the returned token as an `X-AlterSub-Token` header.

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
| KI-1 | High | Verification | Only partly verified on a real TV (Netflix + Hotstar on one low-RAM TV) |
| KI-26 | High | Detection | Netflix publishes no title anywhere, so it can never be detected automatically |
| KI-2 | High | Sourcing | Only the Stremio source can return results; YTS and official API unreachable |
| KI-3 | High | Detection | App package filter matches the TV launcher, Settings, and other non-streaming apps |
| KI-4 | High | Detection | `TitleSanitizer` turns sequels into episodes and misreads numbers as years |
| KI-5 | High | Detection | Accessibility takes the first surviving text node as the title |
| KI-6 | Medium | Detection | MediaSession title is trusted even if generic or partial |
| KI-9 | Medium | Security | Uploads have no size limit or content validation |
| KI-10 | Medium | Privacy / Distribution | Accessibility service watches every app and requests unused capabilities |
| KI-11 | Medium | Platform | Full-screen overlay window: touch blocking on phones, extra compositing on TVs |
| KI-12 | Medium | Platform | Overlay foreground service never stops once started |
| KI-18 | Medium | Timing | Multiple active media sessions all drive the same clock |
| KI-27 | Medium | Timing | Subtitle files can be offset from the streaming cut, and the fix is lost on restart |
| KI-28 | Medium | Platform | Low-RAM TVs need a one-time ADB grant before subtitles follow pause and seek |
| KI-29 | Low | Performance | The phone page's 2 s status poll costs ~1% CPU on the TV while open |
| KI-30 | Low | UX | Every phone upload is listed as "Uploaded Subtitle" |

### 7.2 High Severity

#### KI-1 · Only partly verified on the target platform — *Confirmed (partly addressed)*
* **Issue**: First real-TV session done on 2026-10-03 (§5.2). It answered the central question for Netflix: **position and play state are available, the title is not** (neither in its session nor on screen). Hotstar does publish a title. Other apps, a non-low-RAM TV, and long sessions are still untested (§5.3).
* **Implication**: Automatic *sync* works (Strategy A2 on low-RAM TVs, Strategy A elsewhere); automatic *identification* of Netflix content is impossible with current techniques, so the phone search is the primary path for Netflix.
* **Fix direction**: Test Prime Video, Disney+ and YouTube the same way (`tools/capture_device_logs.sh` + `AlterSubDiag`), and a non-low-RAM TV. Make the Netflix path fast: remember the last search per app (KI-26) and keep the per-track offset across restarts (KI-27).

#### KI-26 · Netflix can't be identified automatically — *Confirmed on a real TV*
* **Where**: `com.netflix.ninja` on Android TV.
* **Issue**: Its media session has state and position but empty metadata, and its UI (drawn by Netflix's own engine) exposes no accessibility text. Neither detection strategy can learn the title.
* **Implication**: For the most important target app, the user must search on the phone for every title, every time.
* **Fix direction**: Remember the user's choice per app and resume it when the same app plays again; offer recent searches in the phone page; keep the matched subtitle and offset across restarts (KI-27). Don't spend effort on Netflix screen scraping.

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

#### KI-27 · Subtitle offset vs. the streaming cut, and lost on restart — *Confirmed on a real TV*
* **Where**: `TrackOffsets`, `SubtitleSession` (in memory only).
* **Issue**: A subtitle file for the right cut ran 10.75 s ahead of Netflix's version (a different opening). The user fixed it with −/+, but the offset, the active track and the search results live only in memory: any app restart or update loses them.
* **Implication**: The user has to search, pick and re-sync again after every restart, and "Set time" by hand is imprecise.
* **Fix direction**: Persist the active track and its offset per content; add "tap when you hear this line" sync so the offset is found in one tap.

#### KI-28 · Play-state following needs an ADB grant on low-RAM TVs — *Confirmed on a real TV*
* **Where**: `MediaSessionPoller`, setup screen step 3.
* **Issue**: Android blocks notification-listener access on low-RAM devices; the fallback needs `pm grant com.altersub android.permission.DUMP`, which only ADB can do. Without it, subtitles don't follow pause or seek.
* **Implication**: Ordinary users of 1–2 GB TVs can't get automatic sync without a computer.
* **Fix direction**: Keep the setup screen's ADB instructions; consider a small guided "grant over Wi-Fi" flow, or tap-to-sync as the no-ADB fallback (KI-27).

### 7.4 Low Severity

#### KI-29 · Phone page status polling costs CPU on the TV — *Confirmed on a real TV*
* **Where**: `WebRemoteHtml` polls `/api/status` every 2 s; NanoHTTPD starts a thread per request.
* **Issue**: ~1.2% of one core on the low-RAM test TV while the page is open.
* **Fix direction**: Poll more slowly when nothing changes, stop when the page is hidden (`visibilitychange`), or switch to a long-poll that answers only on change.

#### KI-30 · Uploads are indistinguishable — *Confirmed on a real TV*
* **Where**: `WebRemoteServer.handleUpload` names every upload "Uploaded Subtitle".
* **Issue**: Several uploads appear as identical entries in the track list.
* **Fix direction**: Use the uploaded file's name (sanitised) as the track title.

---

## 8. Current Project State & Next Steps

* **Current Status**: Prototype / alpha.
  * **Works today**: builds and 87 offline unit tests. On an Android TV 9 (API 28, 1GB) emulator, the overlay renders at 1080p without stealing D-pad focus, and the event-driven render loop switches cues on time and idles at ~0.1% CPU while paused. The TV setup screen shows real permission states with visible D-pad focus. The web remote works end to end: single-phone QR or PIN pairing with unpairing from either side, manual search with automatic Stremio download, upload, track selection, per-track offset, "Set time" and subtitle style.
  * **Open issues**: High and Medium only (§7.1). Most importantly, automatic detection and sync against real streaming apps on a physical TV is unproven (KI-1), and on the emulator accessibility auto-detection fired on the TV launcher's UI text (KI-3).
* **Artifact Location**: release `app/build/outputs/apk/release/app-release-unsigned.apk` (~1.7 MB, R8-shrunk; needs a release signing config before distribution), debug `app/build/outputs/apk/debug/app-debug.apk` (~9.7 MB from a clean build, unshrunk; incremental debug builds leave dead space and can be much larger).
* **Recommended Next Steps** (in order):
  1. **Real-TV follow-up (KI-1, KI-26, KI-27)**: test Prime/Disney+/YouTube and a non-low-RAM TV; persist the active track + offset; remember choices per app; tap-to-sync.
  2. **Sourcing resilience (KI-2)**: propagate the IMDb ID so YTS works; add OpenSubtitles API-key entry.
  3. **Detection accuracy (KI-3, KI-4, KI-5, KI-6)**: explicit package allowlist, sanitizer fixes with real-title tests, candidate scoring.
  4. **Web remote hardening (KI-9)**: upload size limits and validation.
  5. **Overlay lifecycle (KI-11, KI-12)**: bottom-anchored window, stop when idle.
* **Potential Future Enhancements**:
  0. **User-selectable subtitle font** (planned): the overlay currently uses the system sans-serif in bold, while the app UI uses AlterSub Sans. Let the user pick the subtitle font (e.g. from the phone remote's Appearance card), keeping the choice small and bundled so low-end TVs aren't loading large fonts.
  1. **TMDb Direct API integration**: For exotic media titles where Cinemeta auto-resolution returns multiple candidates.
  2. **ASS / SSA Styled Subtitles**: Parser currently strips advanced ASS vector tags to plain text; could optionally parse colored dialogue tags.
  3. **SMB / Local Network Storage Explorer**: Allow reading `.srt` files directly from a network-attached storage (NAS) or local shared folder.
