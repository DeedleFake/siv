# Simple Image Viewer verification map

This directory is the maintained source for verifying what a user of Simple Image Viewer can do. Read this index, then the matching feature file.

## Baseline preconditions

- `scripts/launch.sh` has printed `READY emulator-5584` and `scripts/doctor.sh` prints `DOCTOR OK`.
- The 540x1200 portrait display is in rotation 0. Gestures assume it.
- Fixtures `siv-portrait.png` (900x1600) and `siv-landscape.png` (1600x900) are in `/sdcard/Download`.
- No ANR dialog is on screen.

## Driving conventions

- Start each recipe with `drive.sh view <fixture>` unless it says otherwise. That gives a fresh app process.
- Fixture quadrants are red top left, green top right, blue bottom left, yellow bottom right. Probe pixels instead of eyeballing.
- Gestures go through `drive.sh gesture`. Do not use `adb shell input` for multi-touch, it cannot do it.
- Every wait is long without KVM. A screencap takes about 10 seconds.

## Proof and skip reporting

- Capture before, during, and after screenshots, plus the `dumpsys` value named by the feature.
- Report an unreachable path with the command tried and what the screen showed.
- A recipe marked "not yet proven" has not run successfully. Do not report it as verified.

## Features

- [Open an image](./open-image.md) covers the VIEW intent, the empty state, and the picker.
- [Radial menu](./radial-menu.md) covers the two-finger menu: open image, brightness, auto-rotate.
- [Zoom and pan](./zoom-pan.md) covers double-tap, pinch, and drag.
