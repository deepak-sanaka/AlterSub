#!/usr/bin/env bash
# Records everything needed to analyse an AlterSub test session on a real TV, until stopped (Ctrl+C).
#
#   tools/capture_device_logs.sh <adb-serial>        e.g. tools/capture_device_logs.sh 192.168.1.20:5555
#
# Writes device-logs/<timestamp>/ (git-ignored: it holds watched titles and network details):
#   device-info.txt  model, Android version, RAM, installed streaming apps, AlterSub build
#   logcat.txt       full logcat (AlterSub diagnostics use the tag AlterSubDiag in debug builds)
#   snapshots.txt    every 15 s: foreground window, active media sessions, AlterSub memory
set -u
SERIAL="${1:?usage: $0 <adb-serial>}"
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB=adb
OUT="$(cd "$(dirname "$0")/.." && pwd)/device-logs/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
adb_() { MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" "$@"; }

{
    echo "captured: $(date)"
    for prop in ro.product.manufacturer ro.product.model ro.product.device ro.build.version.release \
        ro.build.version.sdk ro.build.fingerprint ro.product.cpu.abi ro.opengles.version; do
        echo "$prop=$(adb_ shell getprop "$prop" | tr -d '\r')"
    done
    echo; echo "== memory =="; adb_ shell cat /proc/meminfo | head -4
    echo; echo "== display =="; adb_ shell wm size; adb_ shell wm density
    echo; echo "== streaming apps installed =="
    adb_ shell pm list packages | tr -d '\r' | grep -iE "netflix|amazon|primevideo|disney|hotstar|youtube|hbo|max|hulu|zee5|sonyliv|jio|plex|mx" || echo "(none matched)"
    echo; echo "== AlterSub =="; adb_ shell dumpsys package com.altersub | tr -d '\r' | grep -E "versionName|flags=|lastUpdateTime" | head -4
} > "$OUT/device-info.txt" 2>&1

adb_ logcat -c
adb_ logcat -v threadtime > "$OUT/logcat.txt" 2>&1 &
LOGCAT_PID=$!
trap 'kill $LOGCAT_PID 2>/dev/null; echo "Logs in $OUT"; exit 0' INT TERM

echo "Capturing to $OUT (Ctrl+C to stop)"
while true; do
    {
        echo "===== $(date +%H:%M:%S) ====="
        adb_ shell dumpsys window | tr -d '\r' | grep -m1 mCurrentFocus
        adb_ shell dumpsys media_session | tr -d '\r' | grep -E "package=|state=PlaybackState|metadata:|description=" | head -20
        adb_ shell dumpsys meminfo com.altersub | tr -d '\r' | grep -E "TOTAL:|Java Heap:|Native Heap:" | tr -s ' '
    } >> "$OUT/snapshots.txt" 2>&1
    sleep 15
done
