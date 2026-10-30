#!/usr/bin/env python3
"""Writes shaders/textures/cloud_noise.dat, the clouds' noise (lib/clouds.glsl): a 64 x 64 x 64 RGBA8 volume that
repeats seamlessly on every side, made the same way each time (fixed seeds), so the file only changes when this
script does. Each channel is 0..255:

    R  puffs: cell noise (four cells across, eight under it) mixed with smooth gradient noise (four and eight cells
       across), so it comes in round lumps with uneven outlines
    G  cell noise, four cells across with eight under it (1 at a cell's heart, 0 at its edge)
    B  cell noise, eight cells across
    A  smooth gradient noise, four cells across with eight under it (the clouds' cover from place to place, read on
       one slice)

    python3 shaderpack2/tools/cloud_noise.py
"""

from pathlib import Path

import numpy as np

SIZE = 64
OUT = Path(__file__).resolve().parent.parent / "shaders" / "textures" / "cloud_noise.dat"

# Every texel's centre, 0..1 over the volume, as (z, y, x) arrays.
_axis = (np.arange(SIZE) + 0.5) / SIZE
Z, Y, X = np.meshgrid(_axis, _axis, _axis, indexing="ij")
P = np.stack([X, Y, Z], axis=-1)


def cells(freq, seed):
    """Cell noise, `freq` cells across: 1 at a cell's point, falling smoothly (flat at the top) to 0 a cell's width
    away (repeats)."""
    rng = np.random.default_rng(seed)
    points = rng.random((freq, freq, freq, 3))
    q = P * freq
    base = np.floor(q).astype(int)
    best = np.full(q.shape[:-1], 10.0)
    for dz in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                offset = np.array([dx, dy, dz])
                c = base + offset
                w = c % freq
                feature = c + points[w[..., 2], w[..., 1], w[..., 0]]
                best = np.minimum(best, np.linalg.norm(feature - q, axis=-1))
    d = np.clip(best, 0.0, 1.0)
    return 1.0 - d * d * (3.0 - 2.0 * d)


def gradient(freq, seed):
    """Smooth gradient noise, `freq` cells across, about -1..1 (repeats)."""
    rng = np.random.default_rng(seed)
    g = rng.normal(size=(freq, freq, freq, 3))
    g /= np.linalg.norm(g, axis=-1, keepdims=True)
    q = P * freq
    base = np.floor(q).astype(int)
    f = q - base
    fade = f * f * f * (f * (f * 6.0 - 15.0) + 10.0)
    total = np.zeros(q.shape[:-1])
    for dz in (0, 1):
        for dy in (0, 1):
            for dx in (0, 1):
                offset = np.array([dx, dy, dz])
                w = (base + offset) % freq
                dot = np.sum(g[w[..., 2], w[..., 1], w[..., 0]] * (f - offset), axis=-1)
                weight = np.where(offset[0], fade[..., 0], 1.0 - fade[..., 0]) \
                    * np.where(offset[1], fade[..., 1], 1.0 - fade[..., 1]) \
                    * np.where(offset[2], fade[..., 2], 1.0 - fade[..., 2])
                total += weight * dot
    return total * 1.6


def octaves(make, freqs, weights, seed):
    return sum(w * make(f, seed + i) for i, (f, w) in enumerate(zip(freqs, weights))) / sum(weights)


def to_unit(a):
    lo, hi = np.percentile(a, 0.5), np.percentile(a, 99.5)
    return np.clip((a - lo) / (hi - lo), 0.0, 1.0)


def main():
    # Nothing finer than eight texels a cell anywhere: the clouds' outlines come from where these cross a level, and
    # finer noise there gives outlines that follow the texel grid (straight edges, square corners, combs along the
    # axes) once the texture's own blending between texels shows.
    smooth = to_unit(octaves(gradient, (4, 8), (1.0, 0.5), 10))
    lumps = octaves(cells, (4, 8), (0.7, 0.3), 20)
    # Round lumps from the cell noise, their outlines broken up by the smooth noise.
    puffs = to_unit(lumps * 0.6 + smooth * 0.4)
    coarse = to_unit(octaves(cells, (4, 8), (0.7, 0.3), 30))
    fine = to_unit(cells(8, 40))
    cover = to_unit(octaves(gradient, (4, 8), (1.0, 0.35), 50))
    volume = np.stack([puffs, coarse, fine, cover], axis=-1)
    data = np.round(volume * 255.0).astype(np.uint8)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(data.tobytes())
    print("wrote %s (%d bytes)" % (OUT, data.nbytes))


if __name__ == "__main__":
    main()
