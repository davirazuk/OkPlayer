#!/usr/bin/env python3
"""Draws the LiveArea images (icon, background, startup banner) for the VPK.

The Vita wants 8-bit indexed PNGs, so this renders in RGB and maps every pixel to a
fixed 6x7x6 colour cube. Pure standard library; run from vita/: python3 tools/make_livearea.py
"""
import math
import struct
import zlib

LEVELS = (6, 7, 6)


def palette():
    pal = []
    for r in range(LEVELS[0]):
        for g in range(LEVELS[1]):
            for b in range(LEVELS[2]):
                pal.append((round(r * 255 / (LEVELS[0] - 1)), round(g * 255 / (LEVELS[1] - 1)), round(b * 255 / (LEVELS[2] - 1))))
    return pal


def index_of(rgb, x, y):
    # Ordered dithering hides the banding a 252 colour palette would otherwise show.
    bayer = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))
    d = (bayer[y % 4][x % 4] / 16.0) - 0.5
    idx = []
    for c, n in zip(rgb, LEVELS):
        v = max(0.0, min(1.0, c / 255.0)) * (n - 1) + d
        idx.append(max(0, min(n - 1, int(round(v)))))
    return (idx[0] * LEVELS[1] + idx[1]) * LEVELS[2] + idx[2]


def write_png(path, w, h, pixel):
    pal = palette()
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.append(index_of(pixel(x, y), x, y))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 3, 0, 0, 0))
    png += chunk(b"PLTE", bytes(v for rgb in pal for v in rgb))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def sky(x, y, w, h):
    d = x / w * 0.45 + y / h * 0.55
    c = mix((47, 143, 206), (19, 90, 145), d / 0.45) if d < 0.45 else mix((19, 90, 145), (10, 47, 82), (d - 0.45) / 0.55)
    gx, gy = (x - w * 0.27) / (w * 0.75), (y - h * 1.15) / (h * 0.7)
    glow = max(0.0, 1 - math.hypot(gx, gy))
    return mix(c, (126, 211, 255), glow * 0.7)


def disc(x, y, cx, cy, r, base):
    """Returns the disc's colour over `base` at (x, y): silver, rainbow sheen, dark label, hub."""
    dx, dy = x - cx, y - cy
    d = math.hypot(dx, dy) / r
    if d > 1:
        return base
    if d < 0.12:
        return (5, 6, 7) if d < 0.05 else (200, 208, 216)
    if d < 0.36:
        return (38, 56, 79)
    if d < 0.38:
        return (235, 240, 245)
    v = 225 - 25 * d + (-6 if int(d * r) % 3 == 0 else 0)
    c = (v - 8, v - 2, v + 6)
    ang = (math.atan2(dy, dx) + math.pi) / (2 * math.pi)
    for a, col, alpha in ((0.10, (245, 169, 184), 0.5), (0.16, (255, 240, 170), 0.35), (0.22, (58, 167, 234), 0.5),
                          (0.60, (245, 169, 184), 0.4), (0.70, (58, 167, 234), 0.45)):
        k = max(0.0, 1 - abs(ang - a) / 0.05) * alpha
        c = mix(c, col, k)
    return c


def main():
    write_png("sce_sys/icon0.png", 128, 128, lambda x, y: disc(x, y, 64, 64, 54, sky(x, y, 128, 128)))
    write_png("sce_sys/livearea/contents/bg.png", 840, 500, lambda x, y: disc(x, y, 640, 250, 170, sky(x, y, 840, 500)))
    write_png("sce_sys/livearea/contents/startup.png", 280, 158, lambda x, y: disc(x, y, 140, 79, 62, sky(x, y, 280, 158)))


if __name__ == "__main__":
    main()
