#!/usr/bin/env python3
"""Checks motion vectors against the pictures: python mvcheck.py RUN_FOLDER [NAME=X0,Y0,X1,Y1 ...] (needs numpy).

Uses the raw DLSS inputs the test kit saves with "dumpRaw": true in a debug mode (MODE/raw/*-color.npy, -motion.npy,
-depth.npy, for consecutive frames). With Photon in the residual view the colour is the surfaces' own colour, which
has no frame-to-frame noise, so each frame should match the one before it once every pixel is moved back along its
motion vector. For each pair of frames this moves the earlier one back by the motion vectors scaled by k and prints the
average difference on the pixels that move: if the motion vectors are right, k = 1 matches best. A best k of -1 means
they point the wrong way, 0 that they are no better than none, 2 or 0.5 that they are too short or too long.
Each NAME=X0,Y0,X1,Y1 (pixels, from the picture's top left) adds a table for the moving pixels inside that box only,
to check one thing (a mob, the hand) apart from the rest.
"""
import re
import sys
from pathlib import Path

import numpy as np

SCALES = (-1.0, 0.0, 0.5, 1.0, 2.0)


def bilinear(img, x, y):
    h, w = img.shape[:2]
    x0 = np.clip(np.floor(x).astype(int), 0, w - 2)
    y0 = np.clip(np.floor(y).astype(int), 0, h - 2)
    fx = np.clip(x - x0, 0.0, 1.0)[..., None]
    fy = np.clip(y - y0, 0.0, 1.0)[..., None]
    top = img[y0, x0] * (1 - fx) + img[y0, x0 + 1] * fx
    bottom = img[y0 + 1, x0] * (1 - fx) + img[y0 + 1, x0 + 1] * fx
    return top * (1 - fy) + bottom * fy


def main():
    run = Path(sys.argv[1])
    boxes = [("all", None)]
    for arg in sys.argv[2:]:
        name, _, numbers = arg.partition("=")
        boxes.append((name, tuple(int(v) for v in numbers.split(","))))
    for raw in sorted(run.glob("*/raw")):
        frames = {}
        for f in raw.glob("*-color.npy"):
            name = f.name[: -len("-color.npy")]
            m = re.match(r"(.+)-f(\d+)$", name)
            if m:
                frames.setdefault(m.group(1), []).append(int(m.group(2)))
        print(f"## {raw.parent.name}\n")
        for (segment, numbers), (box_name, box) in ((f, b) for f in sorted(frames.items()) for b in boxes):
            numbers.sort()
            rows = []
            for a, b in zip(numbers, numbers[1:]):
                if b != a + 1:
                    continue
                load = lambda n, kind: np.load(raw / f"{segment}-f{n:03d}-{kind}.npy").astype(np.float32)
                then = load(a, "color")[..., :3]
                now = load(b, "color")[..., :3]
                motion = load(b, "motion")
                depth = load(b, "depth")[..., 0]
                h, w = depth.shape
                ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
                speed = np.hypot(motion[..., 0], motion[..., 1])
                moving = (speed > 0.25) & (depth > 0.0)
                if box is not None:
                    x0, y0, x1, y1 = box
                    inside_box = np.zeros_like(moving)
                    inside_box[max(h - y1, 0):max(h - y0, 0), x0:x1] = True  # bottom row first
                    moving &= inside_box
                if moving.sum() < 50:
                    rows.append(f"| {a}->{b} | {100 * moving.mean():.1f}% | - | " + " | ".join("-" for _ in SCALES) + " |")
                    continue
                cells = []
                for k in SCALES:
                    px = xs + k * motion[..., 0]
                    py = ys + k * motion[..., 1]
                    inside = moving & (px >= 0) & (px <= w - 1) & (py >= 0) & (py <= h - 1)
                    diff = np.abs(now - bilinear(then, px, py)).mean(-1)
                    cells.append(255 * diff[inside].mean())
                best = SCALES[int(np.argmin(cells))]
                rows.append(f"| {a}->{b} | {100 * moving.mean():.1f}% | {np.median(speed[moving]):.2f} | "
                            + " | ".join(f"**{c:.2f}**" if k == best else f"{c:.2f}" for k, c in zip(SCALES, cells)) + " |")
            print(f"### {segment}" + ("" if box is None else f", {box_name} {box}") + "\n")
            print("| frames | moving pixels | median px/frame | " + " | ".join(f"k={k:g}" for k in SCALES) + " |")
            print("| --- | --- | --- | " + " | ".join("---" for _ in SCALES) + " |")
            print("\n".join(rows) + "\n")


if __name__ == "__main__":
    main()
