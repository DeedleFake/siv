#!/usr/bin/env python3
"""Emit a device shell script that injects multi-touch gestures into the emulator's touch device.

Usage: touch.py <W> <H> hold2 <cx> <cy> <dx> <dy> <hold_ms> <post_ms>
       touch.py <W> <H> dtap <x> <y>
       touch.py <W> <H> pinch <cx> <cy> <from_px> <to_px>
W and H are the portrait pixel size from `wm size`. The second tap of dtap is 1px right of the first, because the kernel drops repeated identical coordinates. Two-finger gestures use fingers 60px apart
horizontally. Pipe the output to `adb shell sh` as root (/dev/input needs it).
Each frame is one write() of raw input_event structs, so timing is not set by process spawns.
Only valid while the display is in rotation 0.
"""
import struct
import sys

DEV = "/dev/input/event2"
EV_ABS, EV_SYN = 3, 0
SLOT, TID, PX, PY, PRESSURE = 0x2F, 0x39, 0x35, 0x36, 0x3A


class Gesture:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.lines = []

    def raw(self, x, y):
        return round(x * 32767 / (self.w - 1)), round(y * 32767 / (self.h - 1))

    def frame(self, events):
        data = b""
        for kind, code, value in events + [(EV_SYN, 0, 0)]:
            data += struct.pack("<qqHHi", 0, 0, kind, code, value)
        esc = "".join(f"\\{b:03o}" for b in data)
        self.lines.append(f"printf '{esc}' > {DEV}")

    def sleep(self, ms):
        self.lines.append(f"sleep {ms / 1000:.2f}")

    def down(self, slot, tid, x, y):
        rx, ry = self.raw(x, y)
        return [(EV_ABS, SLOT, slot), (EV_ABS, TID, tid), (EV_ABS, PX, rx), (EV_ABS, PY, ry), (EV_ABS, PRESSURE, 100)]

    def move(self, slot, x, y):
        rx, ry = self.raw(x, y)
        return [(EV_ABS, SLOT, slot), (EV_ABS, PX, rx), (EV_ABS, PY, ry)]

    @staticmethod
    def up(slot):
        return [(EV_ABS, SLOT, slot), (EV_ABS, TID, -1)]


def hold2(g, cx, cy, dx, dy, hold_ms, post_ms, gap=30):
    g.frame(g.down(0, 100, cx - gap, cy) + g.down(1, 101, cx + gap, cy))
    g.sleep(hold_ms)
    steps = 8
    for i in range(1, steps + 1):
        mx, my = cx + dx * i / steps, cy + dy * i / steps
        g.frame(g.move(0, mx - gap, my) + g.move(1, mx + gap, my))
        g.sleep(100)
    g.sleep(post_ms)
    g.frame(g.up(0) + g.up(1))


def dtap(g, x, y):
    for i in range(2):
        g.frame(g.down(0, 100 + i, x + i, y))
        g.sleep(50)
        g.frame(g.up(0))
        g.sleep(80)


def pinch(g, cx, cy, start, end):
    g.frame(g.down(0, 100, cx - start / 2, cy) + g.down(1, 101, cx + start / 2, cy))
    for i in range(1, 11):
        d = start + (end - start) * i / 10
        g.frame(g.move(0, cx - d / 2, cy) + g.move(1, cx + d / 2, cy))
        g.sleep(100)
    g.frame(g.up(0) + g.up(1))


if __name__ == "__main__":
    w, h, kind, *a = sys.argv[1:]
    g = Gesture(int(w), int(h))
    {"hold2": hold2, "pinch": pinch, "dtap": dtap}[kind](g, *[float(v) for v in a])
    print("\n".join(g.lines))
