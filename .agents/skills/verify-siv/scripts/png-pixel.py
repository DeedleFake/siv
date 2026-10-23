#!/usr/bin/env python3
"""Print RGB at points in an 8-bit non-interlaced PNG (adb screencap output).

Usage: png-pixel.py <file.png> <x,y> [<x,y> ...]
Prints one line per point: "x,y r g b". Also prints "size WxH" first.
"""
import struct
import sys
import zlib


def read(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, hdr = 8, b"", None
    while pos < len(data):
        n, tag = struct.unpack(">I4s", data[pos : pos + 8])
        body = data[pos + 8 : pos + 8 + n]
        if tag == b"IHDR":
            hdr = struct.unpack(">IIBBBBB", body)
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    w, h, depth, ctype, _, _, interlace = hdr
    assert depth == 8 and interlace == 0 and ctype in (2, 6), f"unsupported PNG {hdr}"
    bpp = 3 if ctype == 2 else 4
    raw = zlib.decompress(idat)
    stride = w * bpp
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1 : (y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        rows.append(line)
        prev = line
    return w, h, bpp, rows


if __name__ == "__main__":
    w, h, bpp, rows = read(sys.argv[1])
    print(f"size {w}x{h}")
    for p in sys.argv[2:]:
        x, y = (int(v) for v in p.split(","))
        i = x * bpp
        print(f"{x},{y}", *rows[y][i : i + 3])
