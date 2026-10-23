# Radial menu

Holding two fingers still for about half a second opens a three-slice menu centered between them. Sliding the pair toward a slice and lifting both fingers runs it. The menu works with or without an image.

## Sub-features

- `menu-open` draws the dimmed circle and the labels `Open Image`, `Brightness: AUTO` or `Brightness: MAX`, and `Auto-Rotate: ON` or `Auto-Rotate: OFF`.
- `menu-brightness` toggles the window brightness between the system value and full.
- `menu-rotate` toggles automatic rotation to the image's aspect ratio.
- `menu-open-image` opens the photo picker.
- `menu-cancel` lifts fingers inside the center circle (about 45px) and does nothing.

## How to get to it (user POV)

- Hold two fingers down on the image or the empty state, slide, release.

## Driving it with scripts/drive.sh

Preconditions: baseline from the index, portrait fixture open (`drive.sh view portrait`, `drive.sh wait-image portrait`).

Slice directions from the touch midpoint, in display pixels (down is positive y): `Open Image` straight up, `Brightness` toward (+100,+58), `Auto-Rotate` toward (-100,+58).

- **Toggle brightness (proven).** Run `scripts/prove-brightness.sh`. It runs `drive.sh gesture hold2 270 600 100 58 1500 30000`, takes a screenshot while the menu is held, then reads `drive.sh brightness`. Expect the held screenshot's yellow-quadrant probe (330,700) dimmed, 122 122 0 when measured (that point sits inside the highlighted Brightness slice; 89 is the unhighlighted dim), the same probe back to 255 255 0 after release, and `Display Brightness=1.0` where it was about 0.4 before. `drive.sh stop` returns it to the earlier value.
- **Toggle auto-rotate.** Same gesture with `-100 58`. Not yet proven. Expect the next menu to read `Auto-Rotate: OFF`, and after opening the landscape fixture the rotation stays at 0.
- **Open Image.** Same gesture with `0 -100`. Not yet proven. Expect the photo picker on top in `drive.sh state`.
- **Cancel.** Same gesture with `0 0`. Not yet proven.
- **Proof.** Screenshots before, during, and after, the `.px` files, and the brightness lines in `steps.log`.

## Gotchas

- Labels are drawn on a Canvas. There is no accessibility node, so read the screenshot or probe pixels.
- Toggled state lives in the activity. Stopping the app resets it.
- Touch injection needs root adbd and portrait rotation 0. Gestures pass the position as fractions of the 540x1200 display.
- The kernel drops repeated identical touch coordinates. `touch.py` offsets repeats by one pixel where it matters.
- The long-press timeout counts guest time. Without KVM keep the hold above 1 second.
