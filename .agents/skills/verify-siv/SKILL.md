---
name: verify-siv
description: Drive Simple Image Viewer (the Android app in this repo, package dev.deedles.simpleimageviewer) on a headless emulator with adb to prove user-visible behavior: opening an image, zoom, and the two-finger radial menu. Use when a change touches app/src/main and you need runtime proof, not just a build.
---

# Verify Simple Image Viewer

The app is a single-activity Compose image viewer. A user opens an image (ACTION_VIEW, share, or photo picker), zooms and pans it, and holds two fingers down for a radial menu. Everything runs through `scripts/` on a headless Android 16 emulator. Read `features/README.md` before driving.

## Requirements

- `ANDROID_HOME` pointing at an SDK that has `platform-tools` and `cmdline-tools/latest`. `launch.sh` installs the emulator and the `system-images;android-36;default;x86_64` image if they are missing.
- A JDK 21. `env.sh` looks at `JAVA_HOME`, `~/.gradle/jdks`, and `/usr/lib/jvm`.
- `python3` (standard library only).
- Root adbd. The `default` image allows it. Google Play images do not, and touch injection needs it.

## Launch

```
scripts/launch.sh
```

Builds the debug APK with `./gradlew --no-daemon assembleDebug`, creates the `siv-verify` AVD (540x1200, 2 GB) under `$SIV_STATE/avd` if missing, starts the emulator on port 5584 (serial `emulator-5584`), waits for `sys.boot_completed`, installs the APK, grants `READ_MEDIA_IMAGES`, turns animations and error dialogs off, pushes two fixture PNGs to `/sdcard/Download`, and waits 90 seconds (`SIV_SETTLE`) for post-boot work to quiet down. It prints `READY emulator-5584` when usable.

Without KVM the emulator runs in software (`-accel off`). Measured on the box this was written on: boot about 10 minutes, APK install about 2 minutes, one screencap about 10 seconds, and the system UI throws ANR dialogs while the guest is busy (load average stays above 20 even when idle). `launch.sh` picks `-accel off` when `/dev/kvm` is not writable. Set `SIV_ACCEL=off` to force it. On that box `/dev/kvm` was present but `KVM_CREATE_VCPU` crashed the host kernel (`kvm_spurious_fault` in dmesg), so do not force KVM there.

Isolation: one emulator per `SIV_PORT` (even number, default 5584) and per `SIV_STATE` (default `/tmp/siv-verify`). If the serial is already listed and no `$SIV_STATE/emulator.pid` exists, `launch.sh` refuses to touch it. Choose another port.

## Doctor

```
scripts/doctor.sh
```

Read-only. Checks the pid file, process, adb state, boot, root, 540x1200 rotation 0, that the installed APK is byte-identical to the one built from this checkout, no ANR dialog, and the fixtures. Prints `DOCTOR OK` or exits 1. Run it first whenever a result looks odd. If it reports an ANR dialog, run `scripts/drive.sh dismiss-anr`.

## Drive

`scripts/drive.sh` wraps everything. Run it with no arguments for the list. The commands that matter:

- `drive.sh view portrait|landscape` opens `/sdcard/Download/siv-<name>.png` through `ACTION_VIEW` in a fresh app process.
- `drive.sh wait-image portrait|landscape` blocks until the fixture's red quadrant is on screen.
- `drive.sh gesture hold2 <cx> <cy> <dx> <dy> <hold_ms> <post_ms>` holds two fingers at the point, then slides the pair by (dx,dy). `gesture dtap <x> <y>` double-taps. `gesture pinch <cx> <cy> <from_px> <to_px>` pinches. Coordinates are display pixels in portrait.
- `drive.sh shot <file>` and `drive.sh px <file> x,y ...` capture the screen and read pixels.
- `drive.sh state` prints the top activity, display rotation, and applied brightness.

The fixtures (`scripts/make-fixture.py`) are four flat quadrants, red top left, green top right, blue bottom left, yellow bottom right, so one pixel read tells you which part of the image is where. Portrait fixture on the portrait display: image spans y 120 to 1080, quadrant probes (135,300) red, (405,300) green, (135,900) blue, (405,900) yellow.

The radial menu and zoom are Canvas and gesture drawn, so they expose no accessibility nodes. `uiautomator dump` sees only system dialogs. Use pixels and `dumpsys`.

## Evidence

`scripts/prove-brightness.sh` is the worked proof for the radial menu. It writes `01-before.png`, `02-menu-open.png`, `03-after-release.png`, matching `.px` files, and `steps.log` to `$SIV_EVIDENCE/radial-menu/<timestamp>/` (default `$TMPDIR/siv-verify-evidence`).

Proof standard for any feature:

- Drive the real gesture or intent. Never call app code or write app state.
- Capture the state before, during, and after the action.
- Pair the picture with a side effect the system reports, such as `dumpsys display` brightness or `dumpsys window` rotation.
- Read pixels. A screenshot that nobody compared proves nothing.
- Fixtures are local files. The photo picker is a system app and is outside what this skill proves.

## Cleanup

```
scripts/cleanup.sh          # stop our emulator, remove fixtures and scratch
scripts/cleanup.sh --purge  # also delete the AVD
```

Stops only the emulator whose pid is in `$SIV_STATE/emulator.pid` (`adb emu kill`, then SIGTERM if it lingers). Never kill by process name. It never touches `$SIV_EVIDENCE`. The adb server is shared and left running.
