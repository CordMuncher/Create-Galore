#!/usr/bin/env python3
"""Makes shaders/textures/moon.png: the Moon's near side seen straight on, RGB its colour (sRGB) and A its height.

From NASA's CGI Moon Kit (Scientific Visualization Studio, https://svs.gsfc.nasa.gov/4720/, public domain; credit
NASA's Scientific Visualization Studio): lroc_color_2k.jpg (LRO camera colour mosaic) and ldem_3_8bit.jpg (LOLA
heights, 3 pixels a degree).

    python3 tools/moon_map.py lroc_color_2k.jpg ldem_3_8bit.jpg shaders/textures/moon.png [size]

Made at four times the size and scaled down, so each texel averages the surface under it. Past the disc's edge the
texels hold the disc's average, so blending at the edge pulls in no black."""
import sys
import numpy as np
from PIL import Image

color_path, height_path, out_path = sys.argv[1:4]
size = int(sys.argv[4]) if len(sys.argv) > 4 else 384
big = size * 4
col = np.asarray(Image.open(color_path).convert("RGB"), dtype=np.float32) / 255.0
hgt = np.asarray(Image.open(height_path).convert("L"), dtype=np.float32) / 255.0


def sample(img, u, v):
    h, w = img.shape[:2]
    x = u * w - 0.5
    y = v * h - 0.5
    x0 = np.floor(x).astype(int)
    y0 = np.floor(y).astype(int)
    fx = x - x0
    fy = y - y0
    if img.ndim == 3:
        fx = fx[..., None]
        fy = fy[..., None]

    def at(xx, yy):
        return img[np.clip(yy, 0, h - 1), np.mod(xx, w)]

    return (at(x0, y0) * (1 - fx) * (1 - fy) + at(x0 + 1, y0) * fx * (1 - fy)
            + at(x0, y0 + 1) * (1 - fx) * fy + at(x0 + 1, y0 + 1) * fx * fy)


# Texel centres over [-1, 1]: x to the right (east as seen from Earth), y up (north); z towards Earth.
c = (np.arange(big) + 0.5) / big * 2.0 - 1.0
X, Y = np.meshgrid(c, -c)
r2 = X * X + Y * Y
inside = r2 < 1.0
Z = np.sqrt(np.clip(1.0 - r2, 0.0, 1.0))
lat = np.arcsin(np.clip(Y, -1.0, 1.0))
lon = np.arctan2(X, Z)
u = (lon + np.pi) / (2.0 * np.pi)
v = (np.pi / 2.0 - lat) / np.pi
rgb = sample(col, u, v)
height = sample(hgt, u, v)
rgb[~inside] = rgb[inside].mean(axis=0)
height[~inside] = height[inside].mean()
out = np.dstack([rgb, height[..., None]])
img = Image.fromarray(np.clip(out * 255.0 + 0.5, 0, 255).astype(np.uint8), "RGBA")
img.resize((size, size), Image.Resampling.BOX).save(out_path, optimize=True)
print(out_path)
