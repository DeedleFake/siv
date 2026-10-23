#!/usr/bin/env bash
# Drive the app on the verification emulator.
#   drive.sh view portrait|landscape     open a fixture through an ACTION_VIEW intent (fresh app process)
#   drive.sh wait-image portrait|landscape   block until the fixture's quadrants are on screen
#   drive.sh shot <file.png>             screencap (takes ~10s without KVM)
#   drive.sh px <file.png> x,y ...       print RGB at points
#   drive.sh gesture <touch.py args>     inject a touch gesture, e.g. gesture dtap 150 320
#   drive.sh brightness                  the display brightness the system is applying
#   drive.sh state                       top activity, rotation, brightness
#   drive.sh dismiss-anr                 tap "Wait" on ANR dialogs until none show
#   drive.sh stop                        force-stop the app (also drops its brightness override)
set -euo pipefail
. "$(dirname "$0")/env.sh"
cmd=${1:?usage: drive.sh <command> [args]}; shift

# Quadrant probe points (red, green, blue, yellow) on the 540x1200 display for each fixture.
probes() { case $1 in portrait) echo "135,300 405,300 135,900 405,900";; landscape) echo "300,135 900,135 300,405 900,405";; *) return 1;; esac; }

case $cmd in
  view)
    ashell am force-stop "$SIV_PKG"
    ashell am start -a android.intent.action.VIEW -d "file:///sdcard/Download/siv-$1.png" -t image/png "$SIV_PKG/.MainActivity" ;;
  wait-image)
    red=${PROBE_RED:-$(probes "$1" | cut -d' ' -f1)}
    deadline=$((SECONDS + ${SIV_IMAGE_TIMEOUT:-180}))
    while :; do
      adbs exec-out screencap -p >"$SIV_STATE/probe.png"
      read -r _ r g b < <(python3 "$SIV_SCRIPTS/png-pixel.py" "$SIV_STATE/probe.png" "$red" | tail -1)
      [ "$r" -gt 200 ] && [ "$g" -lt 50 ] && [ "$b" -lt 50 ] && { echo "image on screen: $red = $r $g $b"; break; }
      [ $SECONDS -lt $deadline ] || { echo "wait-image: image not shown, $red = $r $g $b (an ANR dialog dims it: run drive.sh dismiss-anr)" >&2; exit 1; }
    done ;;
  shot) mkdir -p "$(dirname "$1")"; adbs exec-out screencap -p >"$1" ;;
  px) python3 "$SIV_SCRIPTS/png-pixel.py" "$@" ;;
  gesture)
    python3 "$SIV_SCRIPTS/touch.py" 540 1200 "$@" >"$SIV_STATE/gesture.sh"
    adbs push "$SIV_STATE/gesture.sh" /data/local/tmp/siv-gesture.sh >/dev/null
    SIV_SHELL_TIMEOUT=300 ashell sh /data/local/tmp/siv-gesture.sh ;;
  brightness) ashell dumpsys display | grep -m1 "Display Brightness=" | sed 's/^ *//' ;;
  state)
    ashell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/^ *//'
    ashell dumpsys window displays | grep -m1 mCurrentRotation | sed 's/^ *//'
    "$0" brightness ;;
  dismiss-anr)
    # Taps "Wait" up to 5 times until no ANR dialog remains. Prints "clear" when none is showing.
    for _ in 1 2 3 4 5; do
      ashell uiautomator dump /sdcard/siv-ui.xml >/dev/null
      xy=$(adbs exec-out cat /sdcard/siv-ui.xml | python3 -c '
import re, sys
xml = sys.stdin.read()
m = re.search(r"text=\"Wait\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", xml) if "isn\x27t responding" in xml else None
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
else:
    print("clear")')
      [ "$xy" = clear ] && { echo clear; exit 0; }
      echo "ANR dialog, tapping Wait at $xy"
      ashell input tap $xy
      sleep 5
    done
    echo "ANR dialog still showing after 5 taps" >&2; exit 1 ;;
  stop) ashell am force-stop "$SIV_PKG" ;;
  *) echo "unknown command $cmd" >&2; exit 2 ;;
esac
