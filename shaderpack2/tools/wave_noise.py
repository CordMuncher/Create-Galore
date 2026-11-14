#!/usr/bin/env python3
"""Writes the water's wave pattern (lib/waves.glsl): shaders/textures/water_waves.dat, 128 x 128 RGBA8, repeating
seamlessly, made the same way each time (a fixed seed), so the file only changes when this script does.

One band of a wind sea: every wave between 4 and 16 crests across the tile (most of them round 8), running mostly
along +u (the wind) and spread about 45 degrees either side of it, at random phases, each as steep as the next (the
slopes of a real sea are spread evenly from long waves to short). What is stored is the surface's slope, in two
halves: rg its slope (d/du, d/dv) at the start of the band's swing, ba a quarter of a swing later. The surface at any
time is rg * cos(phase) + ba * sin(phase), which moves every wave in the band along its own way, crests forming and
dissolving as the waves pass through each other, and comes back to where it started after one swing. The shader lays
the same pattern down at several sizes (each twice as fine and swinging faster), which add up to the whole sea.

Values are slope / PEAK * 0.5 + 0.5; the slope's root mean square is 1 before that (the shader scales each band).

    python3 shaderpack2/tools/wave_noise.py
"""

from pathlib import Path

import numpy as np

OUT = Path(__file__).resolve().parent.parent / "shaders" / "textures" / "water_waves.dat"
SIZE = 128
MIDDLE = 8.0  # crests across the tile the band is strongest at
PEAK = 4.0    # the slope stored as 1 (in root mean squares): nothing reaches it


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


if __name__ == "__main__":
    main()
