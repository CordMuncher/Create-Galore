#!/usr/bin/env python3
"""The water's wave noise at coarser levels (shaders/textures/wave_noise_1.bin .. _4.bin, each half the size of the
one before, box-filtered: the mip levels Iris doesn't make for a raw texture), and the numbers lib/water.glsl takes
from the noise: how steep its slopes are on average, and how much of that a pixel still shows once the noise is read
at the level its footprint calls for (the rest becomes the water's roughness).

    python3 tools/wave_slopes.py
"""
from pathlib import Path

import numpy as np

TEXTURES = Path(__file__).resolve().parent.parent / "shaders" / "textures"
LEVELS = 4


def box_down(a):
    h, w = a.shape
    return a.reshape(h // 2, 2, w // 2, 2).mean(axis=(1, 3))


def bilinear(a, u, v):
    """a sampled at texel coordinates (u, v) (texel centres at .5), repeating, as GL_LINEAR does."""
    h, w = a.shape
    x = u - 0.5
    y = v - 0.5
    x0 = np.floor(x).astype(int)
    y0 = np.floor(y).astype(int)
    fx = x - x0
    fy = y - y0
    x0 %= w
    y0 %= h
    x1 = (x0 + 1) % w
    y1 = (y0 + 1) % h
    top = a[y0, x0] * (1 - fx) + a[y0, x1] * fx
    bottom = a[y1, x0] * (1 - fx) + a[y1, x1] * fx
    return top * (1 - fy) + bottom * fy


def filtered(levels, u, v, texels):
    """The noise at level-0 texel coordinates (u, v), read the way water.glsl reads it for a footprint of texels."""
    lod = np.clip(np.log2(np.maximum(texels, 1.0)), 0.0, LEVELS)
    lo = int(np.floor(lod))
    f = lod - lo
    a = bilinear(levels[lo], u / 2 ** lo, v / 2 ** lo)
    if f == 0.0 or lo == LEVELS:
        return a
    b = bilinear(levels[lo + 1], u / 2 ** (lo + 1), v / 2 ** (lo + 1))
    return a * (1 - f) + b * f


def main():
    noise = np.fromfile(TEXTURES / "wave_noise.bin", dtype="<u2").reshape(1024, 1024).astype(np.float64) / 65535.0
    levels = [noise]
    for i in range(1, LEVELS + 1):
        levels.append(box_down(levels[-1]))
        np.round(levels[-1] * 65535.0).clip(0, 65535).astype("<u2").tofile(TEXTURES / f"wave_noise_{i}.bin")

    # The slopes' mean square per texel, along u (x) and v (z), from a fine central difference.
    rng = np.random.default_rng(1)
    u = rng.uniform(0, 1024, 200000)
    v = rng.uniform(0, 1024, 200000)
    e = 0.05
    du = (bilinear(noise, u + e, v) - bilinear(noise, u - e, v)) / (2 * e)
    dv = (bilinear(noise, u, v + e) - bilinear(noise, u, v - e)) / (2 * e)
    print(f"mean square slope per texel: u {np.mean(du ** 2):.4e}  v {np.mean(dv ** 2):.4e}")

    # How much of it a forward difference over a footprint of t texels keeps, reading the level the footprint calls for.
    fine = np.mean(du ** 2)
    print("footprint texels -> share of the mean square slope kept")
    kept = []
    for t in [0.5, 1, 1.5, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64]:
        span = max(t, 0.05)
        d = (filtered(levels, u + span, v, t) - filtered(levels, u, v, t)) / span
        share = np.mean(d ** 2) / fine
        kept.append((t, share))
        print(f"  {t:5}: {share:.3f}")
    # A fit: share = 1 / (1 + (t / a)^b).
    best = None
    for a in np.linspace(0.5, 16, 156):
        for b in np.linspace(0.5, 3, 51):
            err = sum((1 / (1 + (t / a) ** b) - s) ** 2 for t, s in kept)
            if best is None or err < best[0]:
                best = (err, a, b)
    print(f"fit: kept = 1 / (1 + (t / {best[1]:.2f})^{best[2]:.2f})  (squared error {best[0]:.4f})")


if __name__ == "__main__":
    main()
