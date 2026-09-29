#!/usr/bin/env python3
"""Draws the Windows taskbar thumbnail buttons (previous, play, pause, next) as 16x16
white glyphs with a soft shadow, anti-aliased by supersampling. No dependencies."""
import os
import struct
import zlib

SIZE, SS = 16, 8  # output size, supersampling factor

def inside(shape, x, y):
    for kind, *p in shape:
        if kind == "rect" and p[0] <= x < p[2] and p[1] <= y < p[3]:
            return True
        if kind == "tri":
            (ax, ay), (bx, by), (cx, cy) = p
            d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by)
            d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy)
            d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay)
            if not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0)):
                return True
    return False

def coverage(shape, dx=0.0, dy=0.0):
    out = []
    for py in range(SIZE):
        row = []
        for px in range(SIZE):
            hits = sum(inside(shape, px + (i + .5) / SS - dx, py + (j + .5) / SS - dy) for i in range(SS) for j in range(SS))
            row.append(hits / (SS * SS))
        out.append(row)
    return out

def png(path, shape):
    glyph, shadow = coverage(shape), coverage(shape, 0.0, 1.0)
    raw = b""
    for y in range(SIZE):
        raw += b"\0"
        for x in range(SIZE):
            g, s = glyph[y][x], shadow[y][x] * 0.55
            a = g + s * (1 - g)
            v = int(round(255 * g / a)) if a else 0
            raw += bytes((v, v, v, int(round(a * 255))))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(data)

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "icons")
os.makedirs(out, exist_ok=True)
png(os.path.join(out, "play.png"), [("tri", (4.5, 2.5), (4.5, 13.5), (13, 8))])
png(os.path.join(out, "pause.png"), [("rect", 3.5, 2.5, 7, 13.5), ("rect", 9, 2.5, 12.5, 13.5)])
png(os.path.join(out, "prev.png"), [("rect", 2.5, 3, 4.5, 13), ("tri", (13.5, 3), (13.5, 13), (5, 8))])
png(os.path.join(out, "next.png"), [("rect", 11.5, 3, 13.5, 13), ("tri", (2.5, 3), (2.5, 13), (11, 8))])
print("wrote", sorted(os.listdir(out)))
