"""Draws the Stellar Core item texture (16x16) for the bridge mod."""
import math, sys
from PIL import Image

out = sys.argv[1]
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
cx = cy = 7.5
for y in range(16):
    for x in range(16):
        d = math.hypot(x - cx, y - cy)
        if d > 7.2:
            continue
        if d > 6.2:  # brass ring
            px[x, y] = (122, 84, 40, 255) if (x + y) % 3 else (176, 128, 62, 255)
            continue
        t = d / 6.2
        ang = math.atan2(y - cy, x - cx)
        swirl = 0.5 + 0.5 * math.sin(3 * ang + 6 * t)
        r = int(40 + 110 * swirl * (1 - t) + 30 * t)
        g = int(30 + 60 * (1 - t))
        b = int(120 + 120 * (1 - t))
        px[x, y] = (min(r, 255), min(g, 255), min(b, 255), 255)
# star in the middle
for x, y, c in [(7, 7, 255), (8, 7, 255), (7, 8, 255), (8, 8, 255), (7, 5, 220), (8, 10, 220),
                (5, 8, 220), (10, 7, 220), (7, 4, 160), (8, 11, 160), (4, 8, 160), (11, 7, 160)]:
    px[x, y] = (c, c, min(255, c + 20), 255)
for x, y in [(4, 4), (11, 11), (11, 4)]:
    px[x, y] = (200, 220, 255, 255)
img.save(out)
