#!/usr/bin/env python3
"""Writes the water's wave pattern (lib/waves.glsl), repeating seamlessly, made the same way each time (a fixed seed),
so the files only change when this script does:
- shaders/textures/water_waves.dat, 128 x 128 RGBA8: the surface's slope;
- shaders/textures/water_curve.dat, 128 x 128 x 16 RGBA8: how the slope changes from place to place (the surface's
  curvature), which is what gathers the light under water into lines (the caustics).

One band of a wind sea: every wave between 4 and 16 crests across the tile (most of them round 8), running mostly
along +u (the wind) and spread about 45 degrees either side of it, at random phases, each as steep as the next (the
slopes of a real sea are spread evenly from long waves to short). What is stored is the surface's slope, in two
halves: rg its slope (d/du, d/dv) at the start of the band's swing, ba a quarter of a swing later. The surface at any
time is rg * cos(phase) + ba * sin(phase), which moves every wave in the band along its own way, crests forming and
dissolving as the waves pass through each other, and comes back to where it started after one swing. The shader lays
the same pattern down at several sizes (each twice as fine and swinging faster), which add up to the whole sea.

Values are slope / PEAK * 0.5 + 0.5; the slope's root mean square is 1 before that (the shader scales each band).

The curvature is that slope's change per tile along u and v: rgb d(slope u)/du, d(slope u)/dv (the same as d(slope
v)/du) and d(slope v)/dv, as value / CURVE_PEAK * 0.5 + 0.5. It is stored at 16 moments of the swing, one after the
other along the third axis (the first at the start of the swing), so one filtered read between two of them gives the
curvature at any moment, exactly as smooth as the waves are: differences of the slope's own filtered reads are not
(they showed as a fine hatch in the light lines).

    python3 shaderpack2/tools/wave_noise.py
"""

from pathlib import Path

import numpy as np

TEXTURES = Path(__file__).resolve().parent.parent / "shaders" / "textures"
OUT = TEXTURES / "water_waves.dat"
OUT_CURVE = TEXTURES / "water_curve.dat"
SIZE = 128
MIDDLE = 8.0         # crests across the tile the band is strongest at
PEAK = 4.0           # the slope stored as 1 (in root mean squares): nothing reaches it
MOMENTS = 16         # moments of the swing the curvature is stored at
CURVE_PEAK = 176.0   # the curvature stored as 1 (the slope's change per tile): nothing reaches it


def main():
    rng = np.random.default_rng(7)
    freq = np.fft.fftfreq(SIZE, 1.0 / SIZE)  # whole numbers of crests across the tile
    ku, kv = np.meshgrid(freq, freq, indexing="xy")
    n = np.hypot(ku, kv)
    with np.errstate(divide="ignore", invalid="ignore"):
        octave = np.log2(n / MIDDLE)
        # Two octaves wide, falling off as cos^2 either side of the middle: bands an octave apart add up evenly.
        window = np.where(np.abs(octave) < 1.0, np.cos(0.5 * np.pi * octave) ** 2, 0.0)
        along = np.where(n > 0, ku / n, 0.0)
        across = np.where(n > 0, kv / n, 0.0)
        # Down the wind and about 45 degrees either side; none against it.
        spread = np.where(along > 0, along ** 3, 0.0)
        # As much slope in every octave and direction: each wave's own slope falls with how many share its ring.
        steep = np.where(n > 0, np.sqrt(window * spread) / n, 0.0)
    phase = rng.random((SIZE, SIZE)) * 2.0 * np.pi
    spectrum = steep * np.exp(1j * phase)
    # The slope of sum(amp * cos(k.x - swing + phase)) is the real part of field * exp(-i swing).
    field_u = np.fft.ifft2(1j * along * spectrum) * SIZE * SIZE
    field_v = np.fft.ifft2(1j * across * spectrum) * SIZE * SIZE
    # Over a swing the squared slope along an axis averages |field|^2 / 2; both axes add.
    rms = np.sqrt(np.mean(np.abs(field_u) ** 2 + np.abs(field_v) ** 2) / 2.0)
    field_u /= rms
    field_v /= rms
    channels = np.stack([field_u.real, field_v.real, field_u.imag, field_v.imag], axis=-1)
    print("largest value %.2f root mean squares (PEAK %.1f)" % (np.abs(channels).max(), PEAK))
    # A little noise under the rounding, so no step of 1/255 shows as a band in a smooth reflection.
    dither = rng.random(channels.shape) - 0.5
    data = np.clip(np.round((channels / PEAK * 0.5 + 0.5) * 255.0 + dither * 0.8), 0, 255).astype(np.uint8)
    OUT.write_bytes(data.tobytes())
    print("wrote %s (%d bytes)" % (OUT, data.nbytes))

    # The slope's change from place to place: one more derivative of each wave (i 2 pi k across the tile).
    def curve(axis_k, unit):
        return np.fft.ifft2(-2.0 * np.pi * axis_k * unit * spectrum) * SIZE * SIZE / rms

    uu, uv, vv = curve(ku, along), curve(kv, along), curve(kv, across)
    layers = []
    for m in range(MOMENTS):
        turn = np.exp(-2j * np.pi * m / MOMENTS)  # real part of field * exp(-i swing), as the slope
        layers.append(np.stack([(uu * turn).real, (uv * turn).real, (vv * turn).real,
                                np.zeros((SIZE, SIZE))], axis=-1))
    curves = np.stack(layers, axis=0)
    print("largest curvature %.0f (CURVE_PEAK %.0f), root mean square %.0f"
          % (np.abs(curves).max(), CURVE_PEAK, np.sqrt(np.mean(curves[..., :3] ** 2))))
    data = np.clip(np.round((curves / CURVE_PEAK * 0.5 + 0.5) * 255.0), 0, 255).astype(np.uint8)
    OUT_CURVE.write_bytes(data.tobytes())
    print("wrote %s (%d bytes)" % (OUT_CURVE, data.nbytes))


if __name__ == "__main__":
    main()
