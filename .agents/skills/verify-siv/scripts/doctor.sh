#!/usr/bin/env bash
# Read-only: is the verification emulator ours, booted, and running the APK built from this checkout?
. "$(dirname "$0")/env.sh" || exit 2
fail=0
check() { if eval "$2"; then log "ok   $1"; else log "FAIL $1"; fail=1; fi; }

check "emulator pid file exists (we started it)" '[ -f "$SIV_STATE/emulator.pid" ]'
check "emulator process alive" 'kill -0 "$(cat "$SIV_STATE/emulator.pid" 2>/dev/null)" 2>/dev/null'
check "adb sees $SIV_SERIAL" 'adb devices | grep -q "^$SIV_SERIAL[[:space:]]*device"'
check "boot completed" '[ "$(ashell getprop sys.boot_completed | tr -d "\r")" = 1 ]'
check "adbd is root (needed for touch injection)" '[ "$(ashell id -u | tr -d "\r")" = 0 ]'
check "display is 540x1200 portrait rotation 0" '[ "$(ashell wm size | tr -d "\r")" = "Physical size: 540x1200" ] && ashell dumpsys window displays | grep -q "mCurrentRotation=ROTATION_0"'
check "installed APK is byte-identical to the one built here" '[ "$(ashell sha256sum "$(ashell pm path "$SIV_PKG" | tr -d "\r" | cut -d: -f2)" | cut -d" " -f1)" = "$(sha256sum "$SIV_APK" | cut -d" " -f1)" ]'
check "no ANR dialog on screen" '! ashell dumpsys window | grep -q "Application Not Responding"'
check "fixtures present on device" 'ashell ls /sdcard/Download/siv-portrait.png /sdcard/Download/siv-landscape.png >/dev/null 2>&1'
log "guest load average: $(ashell cat /proc/loadavg | cut -d' ' -f1-3)"
log "top activity: $(ashell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/^ *//')"
[ $fail = 0 ] && echo "DOCTOR OK" || { echo "DOCTOR FAILED" >&2; exit 1; }
