#!/usr/bin/env python3
"""Write deterministic PNG fixtures: quadrant colors make screenshots checkable by pixel sampling."""
import struct
import sys
import zlib

QUADRANTS = {
    "tl": (255, 0, 0),
    "tr": (0, 255, 0),
    "bl": (0, 0, 255),
    "br": (255, 255, 0),
}


def png(path, width, height):
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            key = ("t" if y < height // 2 else "b") + ("l" if x < width // 2 else "r")
            rows += bytes(QUADRANTS[key])

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(rows), 9))
    data += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


if __name__ == "__main__":
    out = sys.argv[1]
    png(f"{out}/siv-landscape.png", 1600, 900)
    png(f"{out}/siv-portrait.png", 900, 1600)
