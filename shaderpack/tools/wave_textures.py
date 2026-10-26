#!/usr/bin/env python3
"""Makes the water's wave textures (shaders/textures/wave_noise.bin, wave_curl.bin) from Alpha Piscium's
noise_5.bin and water_hf_curl.bin (its shaders/textures/), at the sizes the pack needs: both are smooth, so half the
noise's size and a quarter of the curl's lose nothing the waves show (their spectra are empty past there).

    python3 tools/wave_textures.py ALPHA_PISCIUM/shaders/textures
"""
import sys
from pathlib import Path

import numpy as np


def box_down(a, factor):
    h, w = a.shape[:2]
    return a.reshape(h // factor, factor, w // factor, factor, *a.shape[2:]).mean(axis=(1, 3))


def main():
    src = Path(sys.argv[1])
    out = Path(__file__).resolve().parent.parent / "shaders" / "textures"

    noise = np.fromfile(src / "noise_5.bin", dtype="<u2").reshape(2048, 2048).astype(np.float64)
    noise = box_down(noise, 2)
    np.round(noise).clip(0, 65535).astype("<u2").tofile(out / "wave_noise.bin")

    curl = np.fromfile(src / "water_hf_curl.bin", dtype="<i2").reshape(512, 512, 2).astype(np.float64)
    curl = box_down(curl, 4)
    np.round(curl).clip(-32767, 32767).astype("<i2").tofile(out / "wave_curl.bin")


if __name__ == "__main__":
    main()
