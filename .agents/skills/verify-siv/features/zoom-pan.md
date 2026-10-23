# Zoom and pan

An open image zooms from 1x to 8x with pinch or double-tap (3x, then back to 1x), and pans while zoomed. Panning stops at the image edge.

## Sub-features

- `zoom-double-tap` zooms to 3x at the tap point and back.
- `zoom-pinch` scales between 1x and 8x around the pinch center.
- `pan-clamp` keeps the image from leaving the screen.

## How to get to it (user POV)

- Double-tap the image.
- Pinch with two fingers.
- Drag while zoomed.

## Driving it with scripts/drive.sh

Preconditions: baseline from the index, portrait fixture open.

- **Double-tap.** Run `drive.sh gesture dtap 150 320`. Tapping inside the red quadrant zooms toward that point, so the green probe (405,300) should read red. Not yet proven. The run that tried it showed no change after 25 seconds, and the cause is unknown. The injected events reached the device (confirmed with `getevent`), so suspect Compose's double-tap timing on a loaded software emulator.
- **Pinch.** Run `drive.sh gesture pinch 270 600 60 400`. Expect the quadrant boundary lines to move out of the center. Not yet proven.
- **Proof.** Screenshot before and after, with probes.

## Gotchas

- Zoom state resets whenever a new image arrives.
- Double-tap has a short timeout between taps. Without KVM the guest may be too slow to see two taps as one gesture.
