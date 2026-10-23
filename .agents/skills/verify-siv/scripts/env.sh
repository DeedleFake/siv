#!/usr/bin/env bash
# Sourced by every script in this directory. Defines paths, the adb wrapper, and tool discovery.
SIV_SCRIPTS=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SIV_REPO=$(cd "$SIV_SCRIPTS/../../../.." && pwd)
SIV_STATE=${SIV_STATE:-/tmp/siv-verify}
SIV_EVIDENCE=${SIV_EVIDENCE:-${TMPDIR:-/tmp}/siv-verify-evidence}
SIV_PORT=${SIV_PORT:-5584}
SIV_SERIAL=emulator-$SIV_PORT
SIV_AVD=siv-verify
SIV_IMAGE="system-images;android-36;default;x86_64"
SIV_PKG=dev.deedles.simpleimageviewer
SIV_APK=$SIV_REPO/app/build/outputs/apk/debug/app-debug.apk
export ANDROID_AVD_HOME=$SIV_STATE/avd

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] && [ -x "$SDK/platform-tools/adb" ] || { echo "env: set ANDROID_HOME to an SDK with platform-tools" >&2; return 1 2>/dev/null || exit 1; }
export ANDROID_HOME=$SDK ANDROID_SDK_ROOT=$SDK

java_major() { "$1/bin/java" -version 2>&1 | sed -n '1s/.*"\([0-9]*\)[."].*/\1/p'; }
if [ -z "${JAVA_HOME:-}" ] || [ "$(java_major "$JAVA_HOME" 2>/dev/null)" != 21 ]; then
  JAVA_HOME=
  for d in "$HOME"/.gradle/jdks/*21* /usr/lib/jvm/*21* /workspace/siv-env/jdk-21*; do
    [ -x "$d/bin/java" ] && [ "$(java_major "$d")" = 21 ] && JAVA_HOME=$d && break
  done
fi
[ -n "$JAVA_HOME" ] || { echo "env: need a JDK 21 (app/build.gradle.kts sets jvmToolchain(21)); set JAVA_HOME" >&2; return 1 2>/dev/null || exit 1; }
export JAVA_HOME PATH="$JAVA_HOME/bin:$SDK/platform-tools:$PATH"

adbs() { adb -s "$SIV_SERIAL" "$@"; }
ashell() { timeout "${SIV_SHELL_TIMEOUT:-90}" adb -s "$SIV_SERIAL" shell "$@"; }
log() { printf '[%s] %s\n' "$(date +%T)" "$*"; }
