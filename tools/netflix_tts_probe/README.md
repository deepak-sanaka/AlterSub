# Temporary Netflix TTS feasibility probe

This is an isolated Android 9 diagnostic APK, not an AlterSub app module or a
production speech engine. It has no network, screen/audio capture, or storage
permissions. `ProbeTtsService` records a synthesis request only when its caller
UID belongs to the exact package `com.netflix.ninja`. Other callers' text is
discarded. Debug-only text diagnostics use `DiagLog` / tag `AlterSubDiag`.

All synthesis requests return 10 ms of silent PCM. Selecting this engine will
temporarily silence the TV's synthesized speech, including TalkBack. It does
not relay speech to Google TTS. Do not leave it installed/selected after a test.

## Build

From the repository root:

```powershell
.\tools\netflix_tts_probe\build.ps1
# Optional: supply -SdkRoot and -JdkRoot for other local SDK/JDK paths.
```

Requires SDK platform 34, build-tools 35.0.0, a JDK with Java 8 compilation support,
and the existing standard Android debug keystore. Output:
`tools/netflix_tts_probe/build/probe.apk` (git-ignored). This APK is debuggable and
has no release variant. Android synthesis and a completed self-test on the device
are the meaningful checks; it is not part of AlterSub's Gradle/unit-test build.

## Device test

Before changing anything, save exact secure-setting values and whether each key
exists: `tts_default_synth`, every existing `tts_*` key,
`enabled_accessibility_services`, `accessibility_enabled`,
`touch_exploration_enabled`, and
`touch_exploration_granted_accessibility_services`. Preserve any existing enabled
accessibility services when adding TalkBack. Save the original speech/accessibility
setting list to verify restoration later.

1. Install the APK. Run `am start -n com.altersub.ttsprobe/.SelfTestActivity`.
   Expect `self-test-init status=0`, `self-test-start`, and `self-test-done` under
   `AlterSubDiag`. The self-test caller is ignored; its spoken string is not saved.
2. Set the preferred TTS engine to `com.altersub.ttsprobe`, then enable TalkBack.
   Confirm `dumpsys activity services com.altersub.ttsprobe` shows Netflix bound
   directly to `ProbeTtsService`. An existing Netflix TTS connection may need
   reinitialization; do not assume the setting alone changes its current binding.
3. Open a Netflix description page, focus its title/Play control, then play it.
   Observe the visible title and record which action triggered each string.
4. Read the private debug file before uninstalling:
   `adb -s <TV_SERIAL> shell run-as com.altersub.ttsprobe cat files/netflix-utterances.jsonl`.
   Save results only under git-ignored `device-logs/`. Records contain the caller
   UID, exact package, monotonic timestamp, text, language and voice. Text is
   capped at 8192 characters and the file at approximately 256 KB. The callback
   records strings, not pixels or audio.
5. Restore every saved setting exactly (delete a key if it originally did not
   exist), verify the before/after speech/accessibility settings, and uninstall
   `com.altersub.ttsprobe`. Ensure the restored default can resolve to the original
   engine and that the probe no longer has any binding or installed package.

For captures using `original-settings.json` and `original-speech-settings.txt`
as saved by this investigation, cleanup can be run from the repository root:

```powershell
.\tools\netflix_tts_probe\restore.ps1 -CaptureDirectory <saved-private-capture-directory> -Serial <TV_SERIAL>
```

This restores the saved settings, removes newly created `tts_*` keys, uninstalls
the probe, and compares the speech/accessibility setting lists. Copy utterance
evidence to the private capture directory before running it: uninstalling removes
the on-device log.

Opening a description page identifies the focused/browsed title; it does not by
itself prove that title is playing. A production design would need reliable
selection-to-playback confirmation, rejection of control/synopsis text, correct
speech forwarding, resource measurements, and handling of autoplay/title changes.
