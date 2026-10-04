# Additional Netflix identification probes — 2026-10-04

## Scope and result

**Implementation update:** AlterSub now contains an optional Netflix spoken-title route: `NetflixSpeechEngineService` receives Netflix strings silently by default, with optional audible forwarding through the original voice engine, `NetflixSpeechAccessibilityService` enables Netflix's spoken interface, and `NetflixSpeechDetector` combines an English description title with Playing and actual Netflix media playback. Setup instructions are in [README](../README.md#optional-netflix-automatic-titles); behavior and pending checks are in [PROJECT_STATE](PROJECT_STATE.md#32-detection-pipeline-drm-bypassing). Full Netflix-to-subtitle device testing is deferred at the user's request. The standalone probe established title availability, and one integrated self-test completed speech forwarding; neither establishes full playback association or runtime cost. TV speech/accessibility settings have been restored, and the temporary helper removed.

Investigation on the connected Hisense HiSmartTV A4, Android 9 / API 28,
with Netflix `com.netflix.ninja` **8.3.11 build 12041**. Netflix was foreground and
its session reported active playback during the metadata probes. Those initial
probes used no screen capture, audio capture, playback commands, account access,
app modification, or additional permissions. The watched title was not supplied
for those initial probes. Later user-confirmed UI checks and their input commands
are documented separately below.

**A new description-page title signal was successfully captured through a
temporary user-selected TTS engine.** Netflix sent `Under the Open Sky` and
`On the details screen for Under the Open Sky` directly to that engine. The
media-session, static accessibility-node, live accessibility-event, and local
metadata probes still yielded no current title. The TTS experiment establishes
access to spoken browsing/description text on this device and Netflix version;
it does not yet establish reliable identification of the content actually playing.
The diagnostic remains outside AlterSub's production app.

## Paths checked beyond the previous investigation

| Path | Observation | Consequence |
| --- | --- | --- |
| Media session queue / active item | `queueTitle=null, size=0`; `active item id=-1`; metadata still `size=0, description=null` during playback | No queue title or content identifier to resolve. |
| Direct MediaController extras | A small Java probe using `getActiveSessions(null)` under ADB's shell UID was rejected with `SecurityException: Missing permission to control media` | **Extras remain unverified.** DUMP permission does not grant controller access. Do not label extras empty based on the dump. |
| Activity/task launch intent | `ACTION_MAIN`, `LEANBACK_LAUNCHER`, `.MainActivity`; no `/watch/` or `/title/` URI | A normal launch doesn't identify subsequent selections inside Netflix. A deep-link launch could preserve the initially selected ID, but cannot track subsequent browsing/autoplay by itself. |
| Exported preapp content provider | `content query --uri content://com.netflix.mediaclient.preapp` and `/recommendations` both returned `No result found` | The installed APK's `PreAppRecoContentProvider.query()` consists of returning null. `openFile()` serves cached background images, rather than a current-playing record. Other URI guesses will not turn its query into a title source. |
| Netflix service dump | `dumpsys activity service com.netflix.ninja/.NetflixService` returned `nothing to dump` | No title exposed by its service dump. |
| Netflix private broadcast interfaces | Installed manifest declares TILES, DET, TOKEN, SND and APP_STATUS permissions as `signature\|privileged` | These aren't ordinary companion-app integrations. DUMP does not grant them. No protected broadcasts were sent. |
| DIAL discovery | `GET :8008/ssdp/device-desc.xml` succeeds; `GET :8008/apps/Netflix` returns `state=running`, description `Netflix`, port `9080`, capability `websocket` | Confirms the local service and advertises the remote-control endpoint, but supplies no watched title/ID. The run-link UUID is an application instance, not a Netflix title ID. |
| DIAL additional data / run link | `/apps/Netflix/dial_data` and the advertised run-link path return HTTP 404 | No extra public data at those paths on this implementation. |
| MDX HTTP endpoint | `GET :9080/`, `/status`, `/mdx`, `/session`, `/mdx/status` all return `status=ok` | A generic health response, not a playback-status API. |
| MDX WebSocket | A handshake to `ws://TV:9080/` without credentials returns HTTP 403 | This exact unauthenticated connection is refused. It doesn't establish whether a properly authenticated Netflix controller can obtain the title, or whether a different protocol path works. |
| Android media player / extractor dumps | Player dump lists stale handlers; extractor dump contains unrelated sounds, no Netflix title or stream entry | No further identifying metadata in these snapshots. |

The manifest and provider bytecode were inspected from a read-only copy of the
installed APK using the Android SDK's `aapt` and `dexdump`. No modified Netflix
APK was generated or installed.

## Corner-title hierarchy check

A later check on 2026-10-04 targeted the title shown in Netflix's top-right
playback UI. The user reported **Under the Open Sky** and was asked to keep that
label visible. Netflix was the focused app before and after the capture.

Two independent hierarchy representations were inspected, without screenshots:

- `uiautomator dump` returned **seven Netflix accessibility nodes**. Every node
  had empty `text` and `content-desc` attributes, including `gibbon` and `player`.
  No virtual title node was exposed.
- `dumpsys activity com.netflix.ninja/.MainActivity` showed the native view tree:

```text
gibbonMain (FrameLayout)
  playerContainer (RelativeLayout)
    player (TappableSurfaceView)
  gibbon (SurfaceView)
  splashImage (ImageView, gone)
  progressBar (ProgressBar, gone)
```

There was no `TextView` for the title. The evidence strongly suggests the visible
corner text is drawn inside Netflix's rendering surface rather than represented
as a separate Android text view. A custom renderer can expose a virtual
accessibility hierarchy, but the captured UI exposes no title through one.
See [Android's custom-view accessibility documentation](https://developer.android.com/guide/topics/ui/accessibility/views/custom-views).

Private evidence is saved in git-ignored
`device-logs/netflix-hierarchy-20261004/window.xml` and `activity.txt`. This is a
specific check of the reported playback label on this installed Netflix version,
not merely an assumption based on its earlier browse-screen scans.

## Explicit pause/play-controls accessibility check

A further check on 2026-10-04 repeated the node dump specifically for Netflix's
playback controls. The user confirmed: "the playback is currently paused and the
title appears on the top right". After this confirmation, a successful full dump
again contained seven Netflix nodes, all with empty `text` and `content-desc`.
A compressed dump contained only three nodes: the root `FrameLayout`, `gibbon`,
and `player`, also with empty text and descriptions. Neither dump exposed a
title, a play/pause label, or additional virtual controls.

The initial baseline full dump and the dump after `KEYCODE_MEDIA_PAUSE` also
contained the same seven empty nodes. Two subsequent full attempts returned
`null root node` and provide no evidence about screen text; a `DPAD_UP` input
was then used to refresh the controls, followed by successful compressed and
full dumps. No seek, resume command, or screenshot was used in this check.

Netflix's media-session snapshots continued to report `state=3` despite the
user's paused-screen confirmation. Accordingly, these snapshots are not treated
as independent verification of the visual paused state. Netflix remained the
focused app. The result is limited to the control UI on this installed version.

Private XML and session/focus evidence is in git-ignored
`device-logs/netflix-playback-nodes-20261004-115102/`; the confirmed full tree is
`paused-title-confirmed.xml` and the compressed tree is
`paused-controls-compressed.xml`. There is still no title text for AlterSub's
accessibility inspector to extract from this UI.

## Live accessibility events with TalkBack enabled

On 2026-10-04, the user authorized a live announcement-event test. Installed
TalkBack was `14.2.0.740629310 leanback` and initially disabled. A temporary
shell-only Java/DEX listener connected through `UiAutomation` with
`FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES=1`, so TalkBack could run alongside
the recorder. The listener requested all event types, a zero notification timeout,
and only package `com.netflix.ninja`. It recorded event text/descriptions and the
source node's text/descriptions through an isolated `AlterSubDiag` diagnostic
output channel. No app APK was modified or installed.

TalkBack was temporarily enabled and confirmed bound with spoken feedback in
`dumpsys accessibility`. Netflix was restored to the foreground from the TV's
screensaver using wake/up inputs; another up input refreshed its control UI.
The three-minute capture received **six Netflix events**:

| Event | Count | Text |
| --- | --- | --- |
| `TYPE_WINDOW_STATE_CHANGED` | 2 | `Netflix` (app label) |
| `TYPE_WINDOW_CONTENT_CHANGED` | 2 | Empty |
| `TYPE_VIEW_FOCUSED` | 1 | Empty |
| `TYPE_VIEW_ACCESSIBILITY_FOCUSED` | 1 | Empty |

There was no title-bearing event or `TYPE_ANNOUNCEMENT`. The user reported that
Netflix spoke the title on its description page, while movie playback announced
volume and play/pause controls. This report was not timestamped against the
capture, so it is not evidence that a particular spoken title was synchronously
observed and intercepted by the recorder.

Independent evidence identifies a direct speech path on this installed version:

- `dumpsys activity services com.google.android.tts` showed **Netflix itself**
  bound to `GoogleTtsService`, in addition to TalkBack's separate binding.
- Read-only inspection of `classes2.dex` in the installed Netflix APK found
  `com.netflix.ninja.TextToSpeechWrapper.isSpokenAccessibilityEnabled()`, which
  checks `getEnabledAccessibilityServiceList(1)` (spoken feedback), and
  `ttsSpeak(String, int)`, which passes its string directly to Android's
  `TextToSpeech.speak(CharSequence, int, Bundle, String)`.

This confirms a direct TTS implementation and explains how Netflix can speak
its internal UI strings without placing them in accessibility nodes or event
text. It does not prove that Netflix never emits title events on any screen or
version. This event recorder was not a TTS interceptor. A subsequent, separate
user-selected engine test is documented below.

All saved accessibility settings were restored to their original values, the
before/after accessibility-setting lists matched, and TalkBack was unbound after
the test. The temporary shell listener, DEX, pulled APK, and disassembly files
were removed. No audio/screen capture, seek, or playback command was used.
Private evidence is in git-ignored
`device-logs/netflix-live-events-20261004-115948/`: `events.txt`,
`talkback-state-active.txt`, `tts-bindings.txt`, `tts-code-evidence.txt`,
`original-settings.json`, and the restored-setting/state snapshots.

## Successful description-title capture through a temporary TTS engine

The user then authorized testing the TTS route on 2026-10-04. A standalone,
debug-only APK, `com.altersub.ttsprobe`, was built from
`tools/netflix_tts_probe/` and temporarily installed. It implements Android's
public `TextToSpeechService` API. `SynthesisRequest.getCallerUid()` is checked
against the exact Netflix package before any spoken text is saved. Requests
from TalkBack or other apps are ignored without recording their strings. There
are no network or capture permissions. Each synthesis callback returns 10 ms
of silent PCM; this is a diagnostic engine, not speech forwarding.

The initial self-test completed Android's synthesis protocol. A language-support
status mismatch in the first prototype was corrected, and the improved self-test
also passed `setLanguage(Locale.US)` with status `1`, selecting an offline
`en-US` voice before completing synthesis. The signed final APK was **12,759
bytes** and was tested on the real Android 9 TV.

The preferred speech engine was temporarily changed to the probe, and TalkBack
was enabled. Service diagnostics confirmed Netflix and TalkBack bound directly
to `ProbeTtsService`. The user navigated Netflix's description page, and a Back
input during the test returned Netflix to its profile chooser; the user selected
their profile and reopened the movie page. **36 direct Netflix synthesis requests**
were saved. The caller UID was `10059`, resolved to `com.netflix.ninja`.

| Exact string | Monotonic elapsed time (ms) | Meaning |
| --- | --- | --- |
| `Playing` | 6,565,007 | Playback/control announcement |
| `2 hours, 5 minutes, 24 seconds remaining` | 6,565,068 | Remaining-time announcement |
| `Under the Open Sky` | 6,668,158 | Title announced during navigation |
| `On the details screen for Under the Open Sky` | 6,672,553 | Explicit description-page context and title |

The user confirmed that the description page was open. These are captured
**text inputs** sent by Netflix to Android's selected TTS engine, rather than
transcribed sound, OCR, node text, or account-history data. The explicit details
announcement offers a stronger title context than an arbitrary spoken card name.

One snapshot measured the probe at **18,925 KB total PSS**, with 60 KB swap PSS.
APK size is therefore not a memory-cost estimate. Speech forwarding, CPU cost,
long-session behavior, and interactions with the subtitle overlay are untested.

All saved speech/accessibility settings were restored exactly, including deleting
the `tts_default_synth` key that originally did not exist. The before/after setting
lists matched. TalkBack was unbound, the probe APK was uninstalled, and package
and service checks confirmed no probe remained on the TV. Source/build scripts
are retained for reproducibility; generated APKs/classes are git-ignored.

Private evidence is in git-ignored
`device-logs/netflix-tts-probe-20261004-121425/`. `title-evidence.json` contains
only the two title-bearing records; the full utterance file also contains other
Netflix UI speech and should remain private. Settings snapshots, self-test logs,
confirmed bindings and the memory snapshot are saved alongside it.

### What this enables, and what is still unproven

This is a feasible source of Netflix's spoken description-page title on the tested
version without Netflix credentials or screen capture. It requires selecting a
custom speech engine and enabling spoken accessibility; it is not a passive
listener attached to Google TTS. A production engine would need correct speech
forwarding and substantially more lifecycle/resource testing.

Opening a page or focusing a card is not evidence that its film is playing.
AlterSub would still need to associate a strong details-page title with the user's
subsequent playback action, reject UI/synopsis text, resolve catalog ambiguity,
and discard stale candidates. Autoplay, episodes, title changes, and other Netflix
versions were not tested. No title-detection or subtitle-search behavior was added
to AlterSub in this experiment.

## Playback UI-only capture check

On 2026-10-04, after the user asked about capturing only the top-right title,
read-only `dumpsys SurfaceFlinger` inspection showed two Netflix `SurfaceView`
layers covering the display:

| Layer | Buffer / composition | Flags |
| --- | --- | --- |
| `SurfaceView ... MainActivity#1` | 1280 x 720 RGBA buffer, above the video; consistent with the Gibbon UI renderer | `0x00000080` |
| `SurfaceView ... MainActivity#0` | 3840 x 2160, sideband video layer | `0x00000082` |

Both contain the `eLayerSecure = 0x80` bit documented in
[AOSP LayerState.h](https://android.googlesource.com/platform/frameworks/native/+/cdb6b16dec3a541b455be99d075004cb2f0a0cd7/libs/gui/include/gui/LayerState.h).
The UI-layer identity is inferred from its format, dimensions, ordering, and the
native view tree above; no captured pixels were used to identify the title.
[SurfaceView.setSecure](https://developer.android.com/reference/android/view/SurfaceView#setSecure(boolean))
protects that surface's contents from screenshots independently of whether they
are video or UI. A corner crop or exclusion of the video surface therefore does
not expose this secure UI surface through ordinary screenshot APIs.

This adds layer-level evidence to the earlier failed screenshot and black
recording tests documented in `PROJECT_STATE.md`. No new screenshot or recording
was attempted during this check. The Netflix layer dump is saved privately in
git-ignored `device-logs/netflix-hierarchy-20261004/surface-layers.txt`.

## Reproduce

Run from the project root while Netflix is actually playing:

```powershell
.\tools\probe_netflix_metadata.ps1 -Serial <TV_IP>:5555
# For a USB connection, supply its LAN address separately:
.\tools\probe_netflix_metadata.ps1 -Serial <USB_SERIAL> -TvAddress <TV_IP>
```

The script saves package, session, intent, provider, service, and local HTTP
responses under git-ignored `device-logs/netflix-probe-<timestamp>/`. It sends
read-only ADB requests, HTTP GETs and a WebSocket handshake. It does not start
Netflix, interrupt playback, pair, or send player commands. The DIAL port defaults
to 8008 for this TV and is configurable; failure there is not proof that a device
has no DIAL server. HTTP requests explicitly bypass the host's proxy settings
(the configured proxy otherwise prevented the LAN requests in this session).

Check both the session's active state and which package owns its metadata. A
title from another background player must never identify Netflix.

## Remaining experiments with a plausible mechanism

1. **A user-initiated companion/deep-link launch.** The installed manifest accepts
   Netflix `https://www.netflix.com/watch/...` and `/title/...` links. If the user
   selects the title through AlterSub's companion, AlterSub can search subtitles
   using that explicit choice before launching Netflix and then use the existing
   session clock. This avoids discovering hidden metadata, but is a changed
   workflow, not passive identification. Launch behavior, ID-to-IMDb resolution,
   episode IDs, and later content changes need separate testing before shipping.
2. **A properly authenticated MDX controller.** Netflix documents user
   authentication and controller-target pairing. DIAL alone is insufficient.
   Investigate only with a supported authenticated controller; the current probes
   neither paired nor accessed account tokens. Feasibility for a third-party
   controller and availability of current-title information remain unverified.
3. **An explicitly connected Netflix browser companion.** A user-controlled
   Netflix browser can observe the watch-page ID/title and pass the choice to the
   paired phone API. That identifies playback initiated in that browser, but does
   not automatically describe independent playback selected inside the TV app.

Phone search and remembered picks remain the working paths for independent TV
playback. Episode-end inference can help a known series, but a position reset can
also be a seek or a different title; it is not an identification signal by itself.

## Viewing-history approach ruled out for the product

Netflix exposes profile viewing activity through an authenticated browser, which
could in principle supply an account-level title candidate. This route was
considered on 2026-10-04 but rejected at the user's direction: AlterSub must not
require Netflix credentials or authenticated account-history monitoring. The
[Netflix Terms of Use](https://help.netflix.com/legal/termsofuse) checked during
this investigation restrict automated access and data extraction without explicit
authorization, and permit Netflix to terminate or restrict service for violations.
The enforcement outcome or ban likelihood for a particular account was not
established.

The browser reached Netflix's sign-in page only. No authenticated history was
read, no update-latency experiment was completed, and no Netflix credentials were
collected. The unfinished local history-probe files were removed. Do not revive
this route as a product dependency without a new explicit user instruction.

## Primary references

- [Netflix DIAL reference implementation](https://github.com/Netflix/dial-reference)
  describes discovery/launch and application state.
- [Netflix MDX user authentication](https://github.com/Netflix/msl/wiki/MDX-User-Authentication)
  describes authentication, pairing, and protected controller identity.
- [Android MediaController reference](https://developer.android.com/reference/android/media/session/MediaController)
  documents queue and extras; the device permission failure above is separate from
  whether Netflix actually populates those fields.
