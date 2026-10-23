#!/usr/bin/env bash
# Build the debug APK, boot the verification emulator from an empty AVD if needed, install, and seed.
# Prints "READY <serial>" on success. Slow without KVM: expect 10+ minutes for the first boot.
set -euo pipefail
. "$(dirname "$0")/env.sh"
mkdir -p "$SIV_STATE" "$ANDROID_AVD_HOME"
PIDFILE=$SIV_STATE/emulator.pid

if adb devices | grep -q "^$SIV_SERIAL"; then
  [ -f "$PIDFILE" ] || { echo "launch: $SIV_SERIAL is running but this skill did not start it; refusing to drive it. Set SIV_PORT to another even port." >&2; exit 1; }
  log "reusing emulator we started earlier (pid $(cat "$PIDFILE"))"
else
  log "build"
  (cd "$SIV_REPO" && ./gradlew --no-daemon -q assembleDebug)
  [ -f "$SIV_APK" ] || { echo "launch: build produced no $SIV_APK" >&2; exit 1; }

  [ -d "$SDK/emulator" ] && [ -d "$SDK/system-images/android-36/default/x86_64" ] || {
    log "installing emulator and $SIV_IMAGE"
    yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --install emulator "$SIV_IMAGE" >/dev/null
  }
  if [ ! -d "$ANDROID_AVD_HOME/$SIV_AVD.avd" ]; then
    log "create AVD (540x1200, 2 GB RAM: small on purpose, software rendering is slow)"
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$SIV_AVD" -k "$SIV_IMAGE" -d pixel_6 >/dev/null
    C=$ANDROID_AVD_HOME/$SIV_AVD.avd/config.ini
    sed -i -e 's/^hw.lcd.width=.*/hw.lcd.width=540/' -e 's/^hw.lcd.height=.*/hw.lcd.height=1200/' \
      -e 's/^hw.lcd.density=.*/hw.lcd.density=240/' -e 's/^hw.ramSize=.*/hw.ramSize=2048/' "$C"
  fi

  ACCEL=${SIV_ACCEL:-}
  if [ -z "$ACCEL" ]; then [ -w /dev/kvm ] && ACCEL=auto || ACCEL=off; fi
  log "start emulator on port $SIV_PORT, accel=$ACCEL"
  setsid nohup "$SDK/emulator/emulator" -avd "$SIV_AVD" -port "$SIV_PORT" -no-window -no-audio -no-boot-anim \
    -no-snapshot -gpu swiftshader_indirect -accel "$ACCEL" >"$SIV_STATE/emulator.log" 2>&1 &
  echo $! >"$PIDFILE"
fi

log "waiting for boot (up to ${SIV_BOOT_TIMEOUT:-2400}s)"
deadline=$((SECONDS + ${SIV_BOOT_TIMEOUT:-2400}))
until [ "$(timeout 20 adb -s "$SIV_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  [ $SECONDS -lt $deadline ] || { echo "launch: boot timed out, see $SIV_STATE/emulator.log" >&2; exit 1; }
  sleep 10
done
log "booted"

adbs root >/dev/null 2>&1 || true
adbs wait-for-device
# Root is required for touch injection. It is only available on userdebug images such as this one.
[ "$(ashell id -u | tr -d '\r')" = 0 ] || { echo "launch: adbd is not root; touch injection will fail" >&2; exit 1; }

if [ "$(ashell pm list packages "$SIV_PKG" | tr -d '\r')" != "package:$SIV_PKG" ] ||
   [ "$(ashell sha256sum "$(ashell pm path "$SIV_PKG" | tr -d '\r' | cut -d: -f2)" | cut -d' ' -f1)" != "$(sha256sum "$SIV_APK" | cut -d' ' -f1)" ]; then
  log "install APK (about 2 minutes without KVM)"
  timeout 900 adb -s "$SIV_SERIAL" install -r "$SIV_APK"
fi
ashell pm grant "$SIV_PKG" android.permission.READ_MEDIA_IMAGES
ashell 'settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0; settings put global hide_error_dialogs 1; settings put secure immersive_mode_confirmations confirmed'

FX=$SIV_STATE/fixtures
mkdir -p "$FX"
python3 "$SIV_SCRIPTS/make-fixture.py" "$FX"
ashell mkdir -p /sdcard/Download
adbs push "$FX/siv-landscape.png" "$FX/siv-portrait.png" /sdcard/Download/ >/dev/null

log "settling for ${SIV_SETTLE:-90}s (post-boot work makes the system UI throw ANR dialogs; guest load average stays above 20 without KVM, so it is not a usable gate)"
sleep "${SIV_SETTLE:-90}"
echo "READY $SIV_SERIAL"
