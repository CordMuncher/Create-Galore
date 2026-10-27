#!/usr/bin/env python3
"""Works out how the eye's rods weigh the frame's colours, for the night vision (shaders/lib/night_vision.glsl).

As Ghost of Tsushima builds its matrix from linear Rec.709 to the eye's receptors (Patry, SIGGRAPH 2021 Advances in
Real-Time Rendering): each primary's reflectance spectrum (Smits 1999, the spectra that make up linear sRGB
colours) lit by D65 and seen through a receptor's response. The rods' response is CIE 1951's scotopic luminous
efficiency, in scotopic lumens against photopic ones (1700 / 683); the cones' are Smith and Pokorny's (L + M is the
photopic luminous efficiency), here only to check the spectra: L + M should come out close to Rec.709's luminance.

Prints the rods' weights of linear Rec.709 red, green and blue, scaled so that white weighs 1 (so a grey keeps its
brightness and only colours move), and the D65 white's scotopic over photopic luminance.

    python3 shaderpack/tools/rod_weights.py

Needs colour-science (pip install colour-science).
"""
import warnings

import numpy as np

warnings.filterwarnings("ignore")
import colour  # noqa: E402
from colour.colorimetry import MSDS_CMFS_STANDARD_OBSERVER, SDS_ILLUMINANTS, SDS_LEFS_SCOTOPIC, SpectralShape  # noqa: E402
from colour.recovery import RGB_to_sd_Smits1999  # noqa: E402

shape = SpectralShape(380, 780, 5)
x, y, z = MSDS_CMFS_STANDARD_OBSERVER["CIE 1931 2 Degree Standard Observer"].copy().align(shape).values.T
cone_l = 0.15514 * x + 0.54312 * y - 0.03286 * z
cone_m = -0.15514 * x + 0.45684 * y + 0.03286 * z
rod = SDS_LEFS_SCOTOPIC["CIE 1951 Scotopic Standard Observer"].copy().align(shape).values * (1700.0 / 683.0)
d65 = SDS_ILLUMINANTS["D65"].copy().align(shape).values
primaries = [np.clip(RGB_to_sd_Smits1999(rgb).copy().align(shape).values, 0.0, 1.0) for rgb in np.eye(3)]


def response(curve):
    return np.array([np.sum(curve * d65 * p) for p in primaries])


photopic = response(cone_l) + response(cone_m)
scotopic = response(rod)
scale = photopic.sum()
np.set_printoptions(precision=5, suppress=True)
print("L + M of red, green, blue:", photopic / scale, "(Rec.709 luminance: 0.2126 0.7152 0.0722)")
print("rods of red, green, blue: ", scotopic / scotopic.sum(), "(white 1)")
print("D65 scotopic / photopic:  ", scotopic.sum() / scale)
print("colour-science", colour.__version__)
