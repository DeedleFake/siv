# Open an image

Opening shows one image fullscreen on black with the system bars hidden. The app opens it from another app's VIEW or SEND intent, or from the photo picker when it has no image.

## Sub-features

- `open-view` shows the image from an `ACTION_VIEW` intent.
- `open-orientation` rotates the screen to match a landscape or portrait image.
- `open-empty` shows the empty-state text and opens the photo picker when launched with no image.
- `open-send` shows the image from an `ACTION_SEND` intent with `EXTRA_STREAM`.

## How to get to it (user POV)

- Choose the app from another app's "Open with" list.
- Share an image to the app.
- Launch the app from the launcher with no image. The picker opens on its own and tapping the empty-state text opens it again.

## Driving it with scripts/drive.sh

Preconditions: baseline from the index.

- **View portrait.** Run `drive.sh view portrait` then `drive.sh wait-image portrait`. The red quadrant probe (135,300) reads about 255 0 0 and the rotation stays 0.
- **View landscape.** Run `drive.sh view landscape` then `drive.sh wait-image landscape`. `drive.sh state` reports `mCurrentRotation=ROTATION_90` and the red probe is (300,135). Not yet proven on the current image. A run on a Google APIs image showed the landscape fixture on a 2400x1080 screen, so rotation happens, but that run was not scripted.
- **Empty state.** Run `drive.sh stop` then `adb -s emulator-5584 shell am start -n dev.deedles.simpleimageviewer/.MainActivity`. Expect the photo picker activity on top in `drive.sh state`. Not yet proven.
- **Proof.** `drive.sh shot` after each step and `drive.sh px` on the probes.

## Gotchas

- Launching `view` while the app is already open delivers the intent to the running instance. `drive.sh view` force-stops first for that reason.
- The first image load after install is slow. `wait-image` polls up to 180 seconds.
- An ANR dialog dims the image to about 40 percent. A probe of 102 0 0 instead of 255 0 0 means a dialog is up. Run `drive.sh dismiss-anr`.
- `file://` needs `READ_MEDIA_IMAGES`. `launch.sh` grants it. Real galleries send `content://` URIs, which this recipe does not cover.
