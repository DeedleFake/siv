#!/usr/bin/env bash
# Stop the emulator this skill started and remove scratch state. Never touches $SIV_EVIDENCE.
# Keeps the AVD so the next launch skips creation. --purge also deletes the AVD and all state.
. "$(dirname "$0")/env.sh" || exit 2
PIDFILE=$SIV_STATE/emulator.pid
if [ -f "$PIDFILE" ]; then
  pid=$(cat "$PIDFILE")
  if adb devices | grep -q "^$SIV_SERIAL"; then timeout 60 adb -s "$SIV_SERIAL" emu kill >/dev/null 2>&1; fi
  for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 2; done
  if kill -0 "$pid" 2>/dev/null; then log "emulator pid $pid ignored emu kill; sending SIGTERM"; kill "$pid"; sleep 5; fi
  kill -0 "$pid" 2>/dev/null && { log "pid $pid still alive"; exit 1; }
  rm -f "$PIDFILE"
else
  log "no pid file: nothing of ours to stop"
fi
rm -rf "$SIV_STATE/fixtures" "$SIV_STATE/probe.png" "$SIV_STATE/gesture.sh" "$SIV_STATE/emulator.log"
[ "${1:-}" = --purge ] && rm -rf "$SIV_STATE"
adb devices | grep -q "^$SIV_SERIAL" && { log "FAIL $SIV_SERIAL still listed"; exit 1; }
log "cleanup done; evidence kept at $SIV_EVIDENCE"
