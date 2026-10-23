#!/usr/bin/env bash
# Feature radial-menu / menu-brightness: two-finger hold, slide to "Brightness", release.
# Asserts the menu is drawn, the system applies brightness 1.0 afterwards, and stopping the app restores it.
# Evidence goes to $SIV_EVIDENCE/radial-menu/<timestamp>/. Exit 0 only if every assertion holds.
set -euo pipefail
. "$(dirname "$0")/env.sh"
OUT=$SIV_EVIDENCE/radial-menu/$(date +%Y%m%d-%H%M%S)
mkdir -p "$OUT"
exec > >(tee -a "$OUT/steps.log") 2>&1
drive() { log "drive.sh $*" >&2; "$SIV_SCRIPTS/drive.sh" "$@"; }
assert() { if eval "$2"; then log "PASS $1"; else log "FAIL $1"; exit 1; fi; }
red() { drive px "$1" "$2" | tail -1 | cut -d' ' -f2; }

drive dismiss-anr
"$SIV_SCRIPTS/doctor.sh"
drive view portrait
drive wait-image portrait
drive dismiss-anr
BEFORE=$(drive brightness); log "$BEFORE"
drive shot "$OUT/01-before.png"
drive px "$OUT/01-before.png" 330,700 | tee "$OUT/01-before.px"

log "gesture: two fingers at (270,600), hold 1.5s, slide (+100,+58) toward Brightness, hold 30s, release"
drive gesture hold2 270 600 100 58 1500 30000 &
G=$!
sleep 8
drive shot "$OUT/02-menu-open.png"
drive px "$OUT/02-menu-open.png" 330,700 | tee "$OUT/02-menu-open.px"
wait $G
sleep 5
AFTER=$(drive brightness); log "$AFTER"
drive shot "$OUT/03-after-release.png"
drive px "$OUT/03-after-release.png" 330,700 | tee "$OUT/03-after-release.px"

X1=$(red "$OUT/01-before.png" 330,700); X2=$(red "$OUT/02-menu-open.png" 330,700); X3=$(red "$OUT/03-after-release.png" 330,700)
assert "yellow quadrant is full-bright before the gesture (R=$X1)" '[ "$X1" -ge 250 ]'
assert "menu dims the quadrant while held (R=$X2, expect ~89)" '[ "$X2" -gt 50 ] && [ "$X2" -lt 140 ]'
assert "menu is gone after release (R=$X3)" '[ "$X3" -ge 250 ]'
assert "brightness before is not 1.0 ($BEFORE)" '! echo "$BEFORE" | grep -q "Brightness=1.0$"'
assert "system applies brightness 1.0 after the toggle ($AFTER)" 'echo "$AFTER" | grep -q "Brightness=1.0$"'

drive stop
sleep 5
RESTORED=$(drive brightness); log "$RESTORED"
assert "stopping the app restores the baseline brightness ($RESTORED)" '[ "$RESTORED" = "$BEFORE" ]'
log "PROOF OK, evidence in $OUT"
