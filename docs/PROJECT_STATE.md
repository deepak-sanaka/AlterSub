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
│   │   │   │   │   │   ├── SubtitleStyle.kt         # User subtitle size/colour/position/background with clamping
│   │   │   │   │   │   └── SubtitleTrack.kt         # Track metadata (source, URL, language, rating)
│   │   │   │   │   ├── parser/
│   │   │   │   │   │   ├── SrtParser.kt             # SRT/WebVTT parser: BOM/UTF-16/Windows-1252 detection, markup + entity cleanup
│   │   │   │   │   │   └── SubtitleIndex.kt         # Binary search index (overlap-aware) + next-boundary calculator
│   │   │   │   │   └── session/
│   │   │   │   │       ├── PickMemory.kt            # Remembered picks: track, offset, progress and app per title (persisted)
│   │   │   │   │       ├── TitleMatching.kt         # Which film a title means: year parsing, same-name films, which films to search
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
│   │   │   │           ├── MainActivity.kt          # TV setup screen: required steps, optional Netflix titles (+ help dialog), phone-remote QR/PIN
│   │   │   │           └── QrCode.kt                # ZXing QR → 1-px-per-module bitmap, scaled up unfiltered
│   │   │   └── res/
│   │   │       ├── drawable/                        # Notification icon, flat card/button/chip shapes, status icons (logo images: drawable-xhdpi/xxhdpi, mipmap-*, raw/)
│   │   │       ├── font/                            # AlterSub Sans (app_sans.xml + 3 static TTFs from Google Sans Flex)
│   │   │       ├── layout/activity_main.xml         # Two-column TV setup layout (plain AppCompat Views); dialog_netflix_help.xml
│   │   │       ├── values/                          # colors, strings, styles
│   │   │       └── xml/accessibility_service_config.xml # Accessibility config with event throttling
│   │   └── test/java/com/altersub/
│   │       ├── core/clock/                          # SubtitleClockTest (position extrapolation), TrackOffsetsTest
│   │       ├── core/model/SubtitleStyleTest.kt      # Style clamping, colour and background validation
│   │       ├── core/parser/                         # SrtParserTest (encodings, VTT, malformed SRT), SubtitleIndexTest (incl. lines around a moment)
│   │       ├── core/session/                        # SubtitleSessionTest (races, remembered picks), PickMemoryTest
│   │       ├── detection/                           # DetectionArbiterTest, TitleSanitizerTest, ScreenTitlePickerTest, AppPackageFilterTest
│   │       ├── provider/                            # MockWebServer tests per provider, HttpAwaitTest, CompositeSubtitleProviderTest,
│   │       │                                        #   StremioSubtitleProviderLiveTest (real network, only with -PliveTests)
│   │       ├── server/                              # WebRemoteServerTest (every route + token checks, port fallback), RemoteAuthTest
│   │       └── ui/settings/QrCodeTest.kt            # The pairing link encodes and decodes back intact
│   ├── src/main/assets/licenses/                    # OFL licence for the bundled UI font
│   ├── build.gradle.kts                             # App module build configuration
│   └── proguard-rules.pro                           # R8 rules for release builds (no blanket keeps; see the file)
├── docs/
│   ├── PROJECT_STATE.md                             # This file
│   └── logo/                                        # The logo: original artwork, yellow master, and how the app's copies were made
├── site/                                        # Public information page for GitHub Pages (index.html, images, favicon)
├── .github/workflows/pages.yml                  # Publishes site/ to GitHub Pages, adding the app's UI font
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
  * Stroke outline (`Paint.Style.STROKE`, width = text size ÷ 7) drawn underneath fill so text remains sharp against white backgrounds (e.g. snowy scenes, explosion flashes). Black, or white around black text.
  * Rounded background box, see-through black (`#B3000000`) by default.
  * Responsive scaling: Clamps line width to 90% of screen width to prevent clipping on any aspect ratio or screen size, and keeps the whole box inside a 3% margin on every edge (where some TVs crop), so the top, bottom and side positions never cut it off.
  * **User style** (`SubtitleStyle`): text size (16–60sp), colour (yellow / white / cyan), height (5–95% down), side (10–90% across) and background, adjustable from the phone remote. Changes apply live and are saved in SharedPreferences.
  * **Backgrounds** (`SubtitleStyle.BACKGROUNDS`): see-through black (default), solid black, see-through white, solid white (both white boxes use black text outlined in white, overriding the chosen colour, which returns on a dark box), or none (the outline alone).

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
* **Which apps (`AppPackageFilter`)**: an explicit allowlist of video apps, matched by exact package name, for both media sessions and screen text. The TV home screen, Settings and music apps are never read. (Matching names containing "tv", "media" or "video" took in the launcher, whose menus were then searched as film titles, and let Hotstar in only by accident.)
* **Strategy B — Accessibility Inspector (`AccessibilityInspectorService`)**:
  * Receives `TYPE_WINDOW_STATE_CHANGED` and `TYPE_WINDOW_CONTENT_CHANGED` from the allowlisted apps only: the service narrows its `packageNames` to the allowlist when it connects, so other apps' UI events are never delivered.
  * One scan per burst of events, 400 ms after the screen settles and at most every 1.5 s, on its own thread (walking another app's views is slow IPC and would delay subtitle cues on the main thread). Reads up to 40 texts from at most 200 nodes (depth 30), each with hints: view ID, class, clickable, heading, height, and whether it sits in a row of cards (a collection with several columns) or in a toolbar, header, menu or dialog.
  * `ScreenTitlePicker` scores the texts. It rejects buttons, inputs, UI text (actions, row names, durations, ratings, badges, metadata lines, synopses, prompts), IDs that label something else (`toolbar_title`, `…_subtitle`, Leanback guided steps) and anything in a header. It favours a unique title-like view ID, headings, an episode marker and the largest text, and penalises cards. It returns nothing unless one title clearly leads: picking nothing is safe (the phone can search), a wrong title loads the wrong film.
  * A title counts once two scans in a row agree (`TitleConfirmation`), and is reported once.
  * `SubtitleSession.onScreenTitle` then checks it against the catalog (§3.4) **before** replacing anything: only a film or series with exactly that name is taken (with its IMDb ID); anything else is ignored and the current subtitles stay.
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

* **Strategy D — Optional Netflix spoken titles (2026-10-04)**:
  * The user enables `NetflixSpeechAccessibilityService` (Netflix-only spoken feedback; no node access or remote-key handling), then selects `NetflixSpeechEngineService` as the system TTS engine through the TV's new **Netflix titles** setup. `SpeechEngineSettings` preserves the original installed voice engine. This option enables Netflix's spoken interface.
  * **Mute announcements** is enabled by default and exposed as a D-pad checkbox on the Netflix titles card (under **Optional** on the setup screen, beside a **?** that explains the feature and what to do in Netflix). Direct Netflix requests are received for detection, then completed with 10 ms of pre-allocated silent PCM (`SilentSpeech`) without backend synthesis or a speech file. Unchecking it forwards Netflix's narration through the original engine. Other apps' requests, including TalkBack's own announcements if independently enabled, always use the original engine without text inspection. This setting does not modify movie audio or TV volume; TalkBack need not be enabled.
  * Audible forwarding streams the original engine's PCM through Android's normal playback callback. Its transient private cache file is deleted after each request; no TV audio or pixels are captured. Only strings from the exact Netflix package UID are passed to `NetflixSpeechDetector`. Utterance diagnostics use debug-only `DiagLog`.
  * An English `On the details screen for ...` announcement creates a candidate for 120 s. Netflix's `Playing` announcement arms it for 20 s; actual Netflix media-session playback must then confirm it. Browsing cards alone never trigger searches. Pause, profile/app changes, another card, expiry, and ended playback invalidate pending work. Catalog results carry a generation check so a stale result cannot replace newer content.
  * Uses the existing screen-detection priority: media-session titles and manual choices win; guesses are not remembered. Catalog verification requires an exact normalized title. Several exact names without a year require a phone choice before subtitle searching rather than blindly taking the first result. Playback timestamps still use Strategy A/A2.
  * English Netflix TV 8.3.11 announcements are supported initially. Direct card launches, autoplay, other languages, and episode transitions need phone search until separately validated. The full Netflix → playback → catalog → subtitles device test and runtime memory measurements are **pending at the user's request**. Speech forwarding completed once on the TV before this deferral; this does not establish end-to-end detection.
  * Code verification: `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` succeeded after the silent option was added. The final unit run contained 157 tests: 156 passed, one live-network test skipped, zero failures/errors. `SilentSpeechTest` checks zero-valued PCM, callback buffer limits, failed start/audio handling, and a zero-sized buffer without looping. Integrated silent Netflix detection still needs device validation. No device tests were resumed after the user requested code-only completion.

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

* **Remembered picks (`PickMemory`, owned by `SubtitleSession`)**:
  * Every track the user chose (search, track pick, upload, restore) or that a media-session title led to is remembered with its offset, playback position and the streaming app it played in; screen-scraped guesses are not. Stored as JSON in `SharedPreferences` (`subtitle_picks`), at most 10, newest first; position-only updates are written at most every 30 s.
  * Detecting a remembered title brings back its track and offset at once (the search still runs, for alternatives). In a typed search, the remembered file is marked **Last used**, and picking it brings its offset back.
  * When nothing is loaded and a streaming app reports playback within 5 minutes of where that app's last pick left off, the pick is restored. That is how Netflix content (no title, KI-26) is recognised after a restart or when resuming a film later. A position far away (e.g. a different film starting from 0) or another app is ignored.
  * The phone page lists recent picks (`recent` in `/api/status`) for one-tap restore (`POST /api/restore?key=`).

### 3.4 Multi-Source Subtitle Sourcing (`CompositeSubtitleProvider`)
**Identifying the film first** (`SubtitleSession` + `CinemetaTitleResolver` + `TitleMatching`): before any subtitle search, the title is looked up in Stremio's Cinemeta catalog, because several films can share a name and the catalog doesn't list the wanted one first. Found on a real TV: "Under the Open Sky" resolved to a 2025 film with no subtitles instead of the 2020 film being watched.
* **Automatic detections** take the catalog's best guess for the title: the film with exactly that name (the year settling a tie), and its IMDb ID and year are passed to every provider. Its files form one group in `results`, and the first is shown.
* **A search typed on the phone** never guesses and never changes the TV until the user picks a file: every film it could mean is searched at once, in the catalog's order (up to 4: the film by that name, other films sharing it, and its parts, e.g. "Dune: Part One" and "Dune: Part Two" for "Dune", since IMDb renamed the 2021 film; or the closest matches for a partial title), and each one's files are listed as a group with the film's **year, country and runtime** (Cinemeta's `/meta` endpoint, cached). Films with no files in the chosen language are left out. Nothing in the catalog → providers search by title, as one group.
* A year in the typed search ("Under the Open Sky 2020", "(2020)") narrows it to that film, looking at the parts too ("Dune 2021" is "Dune: Part One"); a title ending in a number ("Wonder Woman 1984") is matched as typed first. Future years and bare numbers ("2012", "Blade Runner 2049") are treated as title text.
* A detected title several films share (Netflix titles with `requireChoice`) is listed the same way, and `searchState` is `choose` until the user picks a file.
* **Subtitle language**: one of 10 (English by default; Spanish, French, German, Portuguese, Italian, Hindi, Arabic, Chinese, Japanese), picked on the phone and kept on the TV (`SharedPreferences` "subtitles"). Every search asks only for that language (`SubtitleLanguages` maps it to each source's labels, e.g. OpenSubtitles' `eng` or `pob`). Changing it searches the shown results again in the new language; a title already showing switches to its first file in that language.
* **File details**: the file's own name and release (OpenSubtitles' `subtitleFileName` and `releaseFormat`), its language, and how long it runs. The length comes from downloading the file (`SubtitleDuration`: the latest cue end), done only while the phone is showing the results, 3 at a time and at most 15 per search; the download is cached, so picking that file is then instant.

Searches all sources concurrently using Kotlin coroutines `async { ... }`. All providers share one `OkHttpClient` (`Http.client`) and use `Call.await()`, so cancelling a superseded search also cancels its in-flight HTTP requests. Every response is closed with `use { }`.
> ⚠️ In the current build **only the Stremio source can return results** in the automatic flow (KI-2).

1. **Stremio Community Mirror (`StremioSubtitleProvider`)**:
   * Queries `https://opensubtitles-v3.strem.io/subtitles/{type}/{imdb_id}.json`.
   * Normally receives the IMDb ID from the identification step above. Only if that found nothing does it fall back to its own Cinemeta lookup (first hit).
   * No API key or registration. This is a public third-party service with no published usage guarantees.
   * The download URLs return UTF-8-converted files (`subencoding-stremio-utf8`).
2. **YTS Mirror (`YtsSubtitleProvider`)**:
   * Queries `https://yts-subs.com/api/v1/movie/{imdb_id}` for movies.
   * Downloads and unpacks zipped `.srt` files on the fly.
   * **Dead**: the endpoint returns 404 (an HTML page) for every film, checked on 2026-10-05 (KI-2).
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
  * **One phone at a time.** While a phone is paired, `/api/pair` returns 409 (even with the right PIN, without counting as a wrong guess), and the TV hides the QR/PIN and shows "Phone paired" with the address. Unpair from the TV (**Unpair phone**) or from the phone's **TV connection** disclosure (**Disconnect this phone**, `POST /api/unpair`). The token persists across restarts (`SharedPreferences`; older builds' multi-phone lists keep only the newest).
  * The TV setup screen has two columns that start and end level: on the left, **Required** steps (overlay, with **Test subtitles** once allowed; screen titles; play/pause) and **Optional** extras (Netflix titles, with a neutral status icon so it never counts as a missing step); on the right, the phone remote. Every action button is the same size (132 × 44 dp), and the next-step hint sits in the header. The whole screen fits 960 × 540 dp without scrolling.
  * The TV's "Phone remote" card has a status chip (Waiting for phone / Paired / Locked / Off), the QR code with three short scan steps, the address in large type (`192.168.x.x:8080`, no `http://` needed) with the PIN, and **Turn off / Turn on** (persisted).
* **Phone page**: single self-contained page; nothing loads from the internet. The UI font is served by the TV (`GET /fonts/app-sans-{regular,medium,bold}.ttf`, public, cached for a week, `font-display: swap`). Three bottom tabs show one task at a time: **Subtitles** (search with a language picker, the file showing on the TV, uploads and recent picks), **Timing**, and **Style**. The shared header shows the current title and subtitle state, plus TV connection status and a reconnect notice. Failed commands display the server's error instead of implying success; stale status responses cannot replace newer state.
  * **Subtitles**: **Find** opens a sheet with a spinner while the TV searches, then the files grouped by film: a header with the film's name, year, country, runtime and file count, and a card per file with its own file name, language, release and how long it runs (filled in as each is checked, "Checking length" until then). Tapping a card shows that file on the TV and closes the sheet. **Choose another file** opens the same sheet for the latest results. A **Subtitles in** picker (on the tab and in the sheet) sets the language; changing it searches again. No results → a message suggesting another language, the year, or a file of your own.
  * **Timing**, top to bottom:
    * The current timing as one signed number, shown as a delay: **+1.5 s** shows subtitles later, **−0.5 s** sooner, **0 s** is the file's own timing. **Reset** is always there (greyed out at 0 s). The sign matches the buttons below, so no "earlier"/"later" wording can contradict a tap. (The clock's offset has the opposite sign: it is added to the cue time.)
    * A tip card above the timing: timing is easiest to set near the start of a film or episode, where the first time someone speaks is simple to match with the first subtitle. It shows on every visit until **OK, understood** hides it; an info button then appears beside the heading and opens the same tip in a tooltip (closed by its cross, Escape or a tap elsewhere).
    * **Adjust timing** (first, because it works whatever the spoken language): **−0.5 s** ("Words appear late") and **+0.5 s** ("Words appear early"), one step per tap. The step picker (0.25 / 0.5 / 1 / 5 s, default 0.5 s, remembered on the phone) changes the buttons' labels too.
    * **Sync to a line**, for when the user understands the spoken language: tap **I hear a line now** the moment someone starts speaking. The TV saves where the subtitles were (`/api/sync/mark`, minus 300 ms for reaction time) and returns the 10 lines either side, with a "Subtitles were here" divider; more lines load in either direction. Picking the line that was heard moves the subtitles so it starts at the saved moment (`/api/sync/line`), with **Undo** in the confirmation.
    * The timer, subtitle pause/resume and "Set time" stay under **Manual timing controls**, distinguished from video playback.
  * **Style** has an approximate live text preview and controls for size, height (Up/Down), side (Left/Right), background (a tile per option, drawn from the server's `backgrounds`) and text colour swatches from the server's `palette`. On a white background the swatches are disabled and the text is black. Native buttons, keyboard tab navigation, focus indicators and generous touch targets support phone and keyboard use. Dynamic titles and filenames still use `textContent` exclusively.
  * **Redesign validation (2026-10-04)**: local browser checks against sample API data covered PIN errors/pairing, empty and missing-result states, same-name title choices, file upload, offset direction/step/reset, manual seek/pause, and appearance updates. Narrow layouts were checked at 320 px and 390 px without horizontal overflow. JavaScript syntax, all 156 offline unit tests, debug packaging and the shrunk release build passed; one live-network test was skipped. The redesigned page has not yet been tested against the TV.
  * **Timing redesign (2026-10-04)**: checked in a browser at 375 px against a mock TV with a running clock and sample dialogue: the −/+ buttons and the signed number moving together at each step size, Reset (disabled at 0 s), marking a line with the divider landing mid-list, loading earlier lines down to the first one, syncing to an earlier line (timing became positive by the gap), and Undo, with no horizontal overflow. Not yet tried on the TV.
  * **Subtitle search redesign (2026-10-05)**: checked in a browser at 375 px against a mock TV (spinner, two same-name films as groups, lengths filling in, language switch, empty result, Escape, no horizontal overflow), then end to end on the Android TV emulator against the live sources: "under the open sky" listed the 2020 film (Japan, 2 h 6 min) with three English files and their real names, the 2025 film was left out for having none, the lengths read 2:06:15, 2:06:11 and 2:06:15, picking one loaded it on the TV, and the language choice survived an app restart.
* **Endpoints**:
  * `GET /`: Serves complete, zero-dependency dark-mode HTML/CSS/JS remote.
  * `POST /api/pair?pin=<pin>`: Exchanges the TV's PIN for a token (403 wrong PIN or screen closed, 409 another phone is paired, 429 locked).
  * `POST /api/unpair`: The paired phone unpairs itself.
  * `GET /api/status`: Returns JSON with active movie title, active subtitle file (`activeTrack`, its name; `activeTrackLanguage`), +/- ms offset, clock position (`positionMs`, excluding offset), play state, `searchState` (`searching`, `choose`, `not_found`, `found`, `idle`), `resultsCount`, the subtitle `language` and the 10 `languages` offered, plus `overlayRunning` and `overlayError`.
  * `POST /api/offset?delta=<ms>`: Fine-tunes subtitle sync delay.
  * `POST /api/sync/mark`: Saves the subtitle time the user heard a line at (`markMs`, 300 ms before the request arrives) and returns the 10 lines on either side (`startMs`, `text`). 409 when no subtitles are loaded.
  * `GET /api/lines?aroundMs=<ms>&before=<n>&after=<n>`: More lines around a time, at most 50 each way.
  * `POST /api/sync/line?markMs=<ms>&startMs=<ms>`: Moves the subtitles by `startMs − markMs`, so the heard line starts at the mark; returns `deltaMs` (for Undo) and the new `offsetMs`. Remembered for the title like any offset.
  * `POST /api/seek?positionMs=<ms>`: Sets the clock to the player's on-screen time (for apps that don't publish a MediaSession position). The remote accepts `41:23` / `1:05:10` input.
  * `POST /api/style?sizeStep=<±n>&positionStep=<±n>&horizontalStep=<±n>&color=<name>&background=<name>` (or `reset=1`): Adjusts subtitle size, height, side, colour and background; values are clamped server-side, and unknown colour or background names are ignored.
  * `POST /api/toggle-play`: Manually forces clock play/pause.
  * `POST /api/search?q=<query>`: Searches a typed title, optionally ending in a year; results arrive in `/api/results`.
  * `GET /api/results`: The latest search's files, grouped by title (`title`, `year`, `country`, `runtimeMinutes`, `episode`), each with `id`, `fileName`, `language`, `release`, `source`, `durationMs` (null while being checked, -1 when unreadable), `active` and `lastUsed`. Polled by the phone while its file sheet is open, which also starts the length checks.
  * `POST /api/use?id=<id>`: Shows a listed file; its title becomes what's playing. 404 if it isn't listed.
  * `POST /api/language?code=<code>`: Changes and keeps the subtitle language (400 if not one of the 10).
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
* **Status (2026-10-06)**: 178 tests, all passing offline. The one live-network test (`StremioSubtitleProviderLiveTest`) is skipped unless run with `-PliveTests`.
* **Test Suites**:
  * [`DetectionArbiterTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/DetectionArbiterTest.kt): MediaSession outranks scraping; a manual choice holds until the session title changes; scraping resumes after sessions end. (Passes)
  * [`SubtitleClockTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/core/clock/SubtitleClockTest.kt): MediaSession position extrapolation (elapsed time × speed, paused, missing/future snapshot, zero speed). (Passes)
  * [`CompositeSubtitleProviderTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/provider/CompositeSubtitleProviderTest.kt): phone uploads are only offered for their own content; uploads with no detected content are never re-offered. (Passes)
  * [`SrtParserTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/core/parser/SrtParserTest.kt): Verifies timestamp conversions (`00:01:23,456` $\rightarrow$ ms), multi-line cues, HTML tag cleanup (`<i>`, `<b>`), and binary search interval queries. (Passes)
  * [`ScreenTitlePickerTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/ScreenTitlePickerTest.kt) and [`AppPackageFilterTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/AppPackageFilterTest.kt): the launcher menu read on the real TV, player overlays, details pages next to rows of cards, browse screens, page headers and Leanback guided steps (from the emulator), prompts, UI text vs real titles, two-scan confirmation; the launcher, Settings and music apps are not followed. (Passes)
  * [`TitleSanitizerTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/detection/TitleSanitizerTest.kt): Verifies regex extraction of `Stranger Things S04E01`, `Wednesday Season 1 Episode 3`, `Inception (2010)`, and rejection of UI junk like `Audio & Subtitles`. (Passes)
  * [`RemoteAuthTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/server/RemoteAuthTest.kt) and [`WebRemoteServerTest`](file:///c:/Users/deepa/AlterSub/app/src/test/java/com/altersub/server/WebRemoteServerTest.kt): PINs only while the TV screen is open, lockout after 5 wrong PINs, token persistence, one paired phone at a time (a second is refused until it unpairs, from the TV or the phone itself); every route rejects missing/unknown tokens (an unpaired upload saves nothing and doesn't desync the connection); fallback to the next free port; the page never uses `innerHTML` (KI-8). (Passes)
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
  * **Reproduced KI-3/KI-4/KI-5** (KI-3 and KI-5 since fixed): Pressing HOME let the accessibility service scrape `com.google.android.tvlauncher` (accepted because the package name contains "tv"). It took the "CUSTOMIZE CHANNELS" button as a title, searched for it, and activated 2,007 cues of an unrelated film over the home screen, with no streaming app involved.
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

* **Screen-title detection (2026-10-04, Android TV emulator, with the preinstalled Leanback sample app allowlisted for the test only)**:
  * The service bound with only window events, and scans ran on their own thread, a few hundred ms after each screen settled.
  * Browse screen (a row of cards): every card scored below zero and nothing was picked.
  * Grid page: its header ("Vertical Video Grid", Leanback's title bar) won when it was the only text; text inside a title bar, toolbar, header, menu or dialog is now skipped, and the page then picked nothing.
  * Guided step (a wizard page): its heading was picked, then rejected by the catalog check ("not a known film or series"), so nothing was replaced; guided steps are now skipped outright.
  * A real film title on screen is covered by unit tests only: the sample's video catalog doesn't load on the emulator.
  * Emulator quirk: after a reinstall the accessibility service stayed unbound until its setting was deleted and set again.

* **Real TV (2026-10-03)**: an Android 9 / API 28 TV with 1.9 GB RAM, 32-bit ARM (`armeabi-v7a`), 1080p at 320 dpi, **`ro.config.low_ram=true`**. Debug build over network ADB, logs recorded with `tools/capture_device_logs.sh`.
  * **Notification-listener access is impossible** on this TV: `cmd notification allow_listener` is silently ignored and the setting has no screen, because Android never grants it on low-RAM devices. Hence Strategy A2 (§3.2).
  * **Netflix** (`com.netflix.ninja`): its session reports accurate state and position (verified against AlterSub's clock to ±0.15 s) but **no metadata at all**, and its UI exposes **no accessibility text** (0 texts on every scrape). Strategy D now implements optional spoken-title detection; its full Netflix playback/search device test is pending. Phone search remains available.
  * **Additional Netflix probes (2026-10-04)**: Netflix 8.3.11 build 12041 exposes no session queue title/ID, content ID in the normal launch intent, or title in its service dump. Its exported preapp provider's query always returns null (it serves cached artwork). DIAL exposes only app state and the MDX port; HTTP there returns a health response, and an unauthenticated WebSocket handshake is refused with 403. Direct controller extras remain **unverified** because shell access was denied. See [the research notes](NETFLIX_IDENTIFICATION_RESEARCH.md) and `tools/probe_netflix_metadata.ps1` for reproduction and remaining companion/MDX experiments.
  * **Netflix screen capture (2026-10-04)**: blocked even over ADB, which has more access than any app, and even on the browse screen with nothing playing. `screencap` was refused (SurfaceFlinger logged `FB is protected: PERMISSION_DENIED`). `screenrecord`, which captures through a virtual display like an app's MediaProjection would, recorded pure black frames (luma 0 everywhere, 5.9 KB for 4 s), menu text included. Netflix's surfaces are secure layers and its video buffers are DRM-protected. As a control, the same two commands on the TV home screen captured normally (1.3 MB screenshot, 711 KB recording with real content).
  * **Hotstar** (`in.startv.hotstar`): its session carries the title ("India vs West Indies: 3rd ODI") and position. Its screen also exposed no accessibility text. **Prime Video** left an inactive, empty session behind.
  * **KI-3 reproduced on real hardware** (since fixed): the launcher's long-press menu ("Context Menu") was taken as a title and searched.
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
| KI-26 | High | Detection | Optional Netflix spoken-title route implemented; full device validation pending |
| KI-2 | High | Sourcing | Only the Stremio source returns results in practice; the official API has no key entry |
| KI-4 | High | Detection | `TitleSanitizer` turns sequels into episodes and misreads numbers as years |
| KI-6 | Medium | Detection | MediaSession title is trusted even if generic or partial |
| KI-9 | Medium | Security | Uploads have no size limit or content validation |
| KI-11 | Medium | Platform | Full-screen overlay window: touch blocking on phones, extra compositing on TVs |
| KI-12 | Medium | Platform | Overlay foreground service never stops once started |
| KI-18 | Medium | Timing | Multiple active media sessions all drive the same clock |
| KI-28 | Medium | Platform | Low-RAM TVs need a one-time ADB grant before subtitles follow pause and seek |
| KI-31 | Medium | Detection | Apps outside the allowlist aren't followed at all, and adding one needs a code change |
| KI-10 | Low | Distribution | Google Play is likely to reject the accessibility service |
| KI-29 | Low | Performance | The phone page's 2 s status poll costs ~1% CPU on the TV while open |

### 7.2 High Severity

#### KI-1 · Only partly verified on the target platform — *Confirmed (partly addressed)*
* **Issue**: First real-TV session done on 2026-10-03 (§5.2). It answered the central question for Netflix: **position and play state are available, the title is not** (neither in its session nor on screen). Hotstar does publish a title. Other apps, a non-low-RAM TV, and long sessions are still untested (§5.3).
* **Implication**: Automatic *sync* works (Strategy A2 on low-RAM TVs, Strategy A elsewhere). A standalone TTS experiment captured a description-page title, and Strategy D now integrates this signal with playback confirmation. The full Netflix device test is deferred at the user's request (KI-26); phone search remains available.
* **Fix direction**: Test Prime Video, Disney+ and YouTube the same way (`tools/capture_device_logs.sh` + `AlterSubDiag`), and a non-low-RAM TV, including the remembered-pick restore (§3.2) against real Netflix playback.

#### KI-26 · Optional Netflix spoken-title detection — *Implemented; full device validation pending*
* **Where**: `com.netflix.ninja` on Android TV.
* **Issue**: Its media session has state and position but empty metadata, and its UI (drawn by Netflix's own engine) exposes no accessibility text. The original session/node detection strategies cannot learn the title, and screen capture is blocked too: screenshots are refused and recordings come out black, even over ADB (§5.2). Further read-only probes on Netflix 8.3.11 found no usable title in queue, launch-intent, provider, service or unauthenticated local discovery/control paths; this is not proof about every Netflix version or a properly authenticated controller. A three-minute live accessibility-event capture with TalkBack enabled received six Netflix events, containing only the app label or empty text. Installed-APK inspection confirmed Netflix calls Android TTS directly. A subsequent standalone, user-selected TTS-engine test **successfully captured `Under the Open Sky` and `On the details screen for Under the Open Sky` from Netflix**, with the user confirming the description page. This is a browsing/description title signal; the experiment alone did not establish playback association. The temporary silent probe was uninstalled and all speech/accessibility settings restored. See [the additional research](NETFLIX_IDENTIFICATION_RESEARCH.md).
* **Implementation (2026-10-04)**: Strategy D receives the description-page title through AlterSub's optional TTS engine, silences Netflix announcements by default (or forwards them to the saved original voice engine), and gates searching on a Playing announcement plus active Netflix media playback. It rejects stale/browsing candidates, preserves manual priority, and requires a phone choice for ambiguous catalog names. Unit tests cover these rules. Speech forwarding completed with 97,200 PCM bytes in one TV self-test; the user then requested code-only completion and deferred full Netflix testing. Original speech/accessibility settings were restored and the temporary helper uninstalled.
* **Implication**: The optional route now exists in code but is not yet verified as a complete Netflix-to-subtitles workflow. Phone search remains the fallback, particularly for direct card launches, autoplay, episode changes, and non-English announcements. Remembered picks (§3.2) bring a title back after a restart or resume, and recent picks are one tap away.
* **Next validation**: Enable the two optional setup switches, open an English Netflix description page, let it load, and select Play. Check that browsing alone does not search, Netflix announcements are silent by default, unmuting restores speech, other apps' speech remains audible, movie audio is unaffected, the correct title reaches the phone, ambiguity asks rather than guesses, and pause/resume/seek retain sync. Then test the shrunk release build and measure the app plus backend engine's memory/CPU. The earlier silent probe measured about 18.5 MB PSS despite a 12.8 KB APK; current integrated costs remain unmeasured. The tested screen-node and event paths still yield no title.

#### KI-2 · Only one subtitle source actually works — *Confirmed*
* **Where**: `YtsSubtitleProvider.search` (requires `imdbId`), `OpenSubtitlesApiProvider.isEnabled` (requires an API key), `StremioSubtitleProvider.resolveImdbId`.
* **Issue**:
  * YTS (`yts-subs.com/api/v1/movie/{imdb_id}`) returns 404 for every film (checked 2026-10-05): it's dead, though the provider still asks.
  * `OpenSubtitlesApiProvider.updateCredentials()` is never called and there is no UI to enter a key, so the official API is never enabled.
* **Implication**:
  * Every automatic search depends on two public Stremio endpoints (`opensubtitles-v3.strem.io`, `v3-cinemeta.strem.io`). If they are down, rate-limited or change format, no subtitles are found and there is no fallback.
  * The "multi-source" resilience described in §3.4 and §4 does not exist yet.
* **Fix direction**: Remove or replace the YTS provider. Add API-key entry for the official OpenSubtitles API, e.g. a web remote settings card persisted to `SharedPreferences`.

#### KI-4 · TitleSanitizer misparses common movie titles — *Confirmed (reproduced with the same regexes)*
* **Issue**:
  * The standalone-episode regex `(?:e|ep|episode)\s*(\d{1,3})` has no word boundary, so `Despicable Me 2` and `The Lego Movie 2` become S1E2, and `Se7en` becomes S1E7.
  * The year regex takes in-title numbers: `Blade Runner 2049` gets year 2049 and is shortened to "Blade Runner", and `Wonder Woman 1984` gets year 1984.
* **Implication**:
  * Sequels are searched as TV series, which skips YTS and usually finds nothing or the wrong thing.
  * Manual searches are deliberately *not* run through the sanitizer until this is fixed.
* **Fix direction**:
  * Require word boundaries and an explicit episode token (`\bE\d`, `\bEp\.?\s*\d`, `\bEpisode\s+\d`).
  * Only treat parenthesised or bracketed years, or trailing years, as release years.
  * Add tests with real sequel titles.

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

#### KI-31 · Only allowlisted apps are followed — *Confirmed (by design)*
* **Where**: `AppPackageFilter.packages`.
* **Issue**: Media sessions and screen text are read only from the apps on the list (the fix for the launcher being read as a streaming app). A video app missing from it gets no automatic play/pause/seek following and no detection; only the phone's search and manual timing work there.
* **Fix direction**: Let the user add the app that's playing from the phone remote (persisted), and extend the list as real apps are tested (KI-1).

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

#### KI-28 · Play-state following needs an ADB grant on low-RAM TVs — *Confirmed on a real TV*
* **Where**: `MediaSessionPoller`, setup screen step 3.
* **Issue**: Android blocks notification-listener access on low-RAM devices; the fallback needs `pm grant com.altersub android.permission.DUMP`, which only ADB can do. Without it, subtitles don't follow pause or seek.
* **Implication**: Ordinary users of 1–2 GB TVs can't get automatic sync without a computer.
* **Fix direction**: Keep the setup screen's ADB instructions; consider a small guided "grant over Wi-Fi" flow. Without the grant, the phone's **Sync to a line** re-aligns subtitles in one tap after a pause or seek.

### 7.4 Low Severity

#### KI-10 · Google Play policy for the accessibility service — *Expected*
* **Where**: `AccessibilityInspectorService`, `res/xml/accessibility_service_config.xml`.
* **Issue**: The service reads other apps' screens for a purpose that isn't accessibility. Its scope is now minimal: events only from the allowlisted apps (`packageNames` set when it connects), only window changes, no key filtering or click events. It keeps `flagIncludeNotImportantViews` and `flagReportViewIds`, which the title scoring needs.
* **Implication**: Google Play's AccessibilityService policy is likely to reject it, so distribution is realistically sideload-only. The service is optional: everything except screen-title detection works without it.

#### KI-29 · Phone page status polling costs CPU on the TV — *Confirmed on a real TV*
* **Where**: `WebRemoteHtml` polls `/api/status` every 2 s; NanoHTTPD starts a thread per request.
* **Issue**: ~1.2% of one core on the low-RAM test TV while the page is open.
* **Fix direction**: Poll more slowly when nothing changes, stop when the page is hidden (`visibilitychange`), or switch to a long-poll that answers only on change.

---

## 8. Current Project State & Next Steps

* **Current Status**: Prototype / alpha.
  * **Works today**: builds and 178 offline unit tests. On an Android TV 9 (API 28, 1GB) emulator, the overlay renders at 1080p without stealing D-pad focus, and the event-driven render loop switches cues on time and idles at ~0.1% CPU while paused. The TV setup screen shows real permission states with visible D-pad focus. The web remote works end to end: single-phone QR or PIN pairing with unpairing from either side, manual search with automatic Stremio download, upload (named after the file), track selection, per-track offset, one-tap sync to a line the user hears, "Set time", subtitle style, remembered picks restored after restarts or from a one-tap Recent list, and a "which film?" choice when several films share the searched title (or a year in the search).
  * **Open issues**: §7.1. Only Netflix and Hotstar have been tried on a real TV (KI-1); the new optional Netflix spoken-title route needs full device validation (KI-26). Screen-title detection is tuned on tests and the emulator's Leanback sample, not yet on real apps' screens.
* **Artifact Location**: release `app/build/outputs/apk/release/app-release-unsigned.apk` (~1.7 MB, R8-shrunk; needs a release signing config before distribution), debug `app/build/outputs/apk/debug/app-debug.apk` (~9.7 MB from a clean build, unshrunk; incremental debug builds leave dead space and can be much larger).
* **Recommended Next Steps** (in order):
  1. **Real-TV follow-up (KI-1, KI-26)**: test Prime/Disney+/YouTube, a non-low-RAM TV, the remembered-pick restore with Netflix, and Sync to a line against real playback; next-episode offer for series.
  2. **Sourcing resilience (KI-2)**: replace the dead YTS source; add OpenSubtitles API-key entry.
  3. **Detection accuracy (KI-4, KI-6, KI-31)**: sanitizer fixes with real-title tests, session-title checks, adding apps from the phone. Tune `ScreenTitlePicker` on real apps' screens (`AlterSubDiag` logs each scan's texts, hints and scores).
  4. **Web remote hardening (KI-9)**: upload size limits and validation.
  5. **Overlay lifecycle (KI-11, KI-12)**: bottom-anchored window, stop when idle.
* **Potential Future Enhancements**:
  0. **User-selectable subtitle font** (planned): the overlay currently uses the system sans-serif in bold, while the app UI uses AlterSub Sans. Let the user pick the subtitle font (e.g. from the phone remote's Appearance card), keeping the choice small and bundled so low-end TVs aren't loading large fonts.
  1. **TMDb Direct API integration**: For exotic media titles where Cinemeta auto-resolution returns multiple candidates.
  2. **ASS / SSA Styled Subtitles**: Parser currently strips advanced ASS vector tags to plain text; could optionally parse colored dialogue tags.
  3. **SMB / Local Network Storage Explorer**: Allow reading `.srt` files directly from a network-attached storage (NAS) or local shared folder.
