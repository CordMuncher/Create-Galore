#!/usr/bin/env python3
"""How much of the last frame DLSS or FSR leaves behind: python ghostcheck.py RUN_FOLDER [NAME=X0,Y0,X1,Y1 ...]
(needs numpy; Pillow for the crop sheets).

For things the motion vectors cannot follow (particles, rain, fire's and lava's animated textures, see-through things
over moving ones), the upscaler gets the motion of whatever is behind them, so the only clean answer is to show this
frame's picture there. This checks that, with the raw files the test kit saves with "dumpRaw": true in the modes that
run DLSS or FSR (MODE/raw/*-color.npy, -output.npy, -motion.npy, -exposure.npy for consecutive frames).

Colours are tone mapped first (times the pack's exposure, then x / (1 + x)), so bright and dark areas count alike.
For each pair of frames, per box (NAME=X0,Y0,X1,Y1 in pixels from the picture's top left; "all" is the whole frame):
- changed: the share of pixels where this frame's input differs from the last frame's input moved along the motion
  vectors by more than 0.05: what the motion vectors do not explain (things moving on their own without motion
  vectors, animated textures, the pack's own noise, and the jitter on edges).
- kept: on those pixels, how much of the last frame the output still shows, from 0 (all of this frame) to 1 (all of
  the last one): the output taken as a mix of this frame's input and the last frame's moved input. High means ghosting
  and smearing; near 0 means it follows the change (and anti-aliases less there).
- off: the average difference between output and input (0-255 levels): anti-aliasing on edges, plus ghosting.
Compare boxes on moving or animated things with a box on something still (its "kept" is the anti-aliasing's normal
level), and modes with each other.

Also writes RUN/ghostcheck/MODE-SEGMENT.png: for each box, the last frame's input and output side by side, three times
bigger.
"""
import re
import sys
from pathlib import Path

import numpy as np

THRESHOLD = 0.05


def bilinear(img, x, y):
    h, w = img.shape[:2]
    x0 = np.clip(np.floor(x).astype(int), 0, w - 2)
    y0 = np.clip(np.floor(y).astype(int), 0, h - 2)
    fx = np.clip(x - x0, 0.0, 1.0)[..., None]
    fy = np.clip(y - y0, 0.0, 1.0)[..., None]
    top = img[y0, x0] * (1 - fx) + img[y0, x0 + 1] * fx
    bottom = img[y0 + 1, x0] * (1 - fx) + img[y0 + 1, x0 + 1] * fx
    return top * (1 - fy) + bottom * fy


def tonemap(rgb, exposure):
    x = np.maximum(rgb * exposure, 0.0)
    return x / (1.0 + x)


def box_mask(shape, box):
    h, w = shape
    mask = np.zeros((h, w), dtype=bool)
    if box is None:
        mask[:] = True
    else:
        x0, y0, x1, y1 = box
        mask[max(h - y1, 0):max(h - y0, 0), x0:x1] = True  # the arrays are bottom row first
    return mask


def crop_sheet(path, boxes, now_in, now_out):
    try:
        from PIL import Image
    except ImportError:
        return
    tiles = []
    for name, box in boxes:
        if box is None:
            continue
        h = now_in.shape[0]
        x0, y0, x1, y1 = box
        rows = slice(max(h - y1, 0), max(h - y0, 0))
        pair = [np.flipud(img[rows, x0:x1]) for img in (now_in, now_out)]
        gap = np.ones((pair[0].shape[0], 4, 3), dtype=np.float32)
        tile = np.concatenate([pair[0], gap, pair[1]], axis=1)
        tiles.append((name, (np.clip(tile, 0, 1) * 255).astype(np.uint8)))
    if not tiles:
        return
    width = max(t.shape[1] for _, t in tiles) * 3
    height = sum(t.shape[0] * 3 + 6 for _, t in tiles)
    sheet = Image.new("RGB", (width, height), (40, 40, 40))
    y = 0
    for _, tile in tiles:
        img = Image.fromarray(tile).resize((tile.shape[1] * 3, tile.shape[0] * 3), Image.NEAREST)
        sheet.paste(img, (0, y))
        y += img.height + 6
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)


def main():
    run = Path(sys.argv[1])
    boxes = [("all", None)]
    for arg in sys.argv[2:]:
        name, _, numbers = arg.partition("=")
        boxes.append((name, tuple(int(v) for v in numbers.split(","))))
    for raw in sorted(run.glob("*/raw")):
        frames = {}
        for f in raw.glob("*-output.npy"):
            m = re.match(r"(.+)-f(\d+)$", f.name[: -len("-output.npy")])
            if m:
                frames.setdefault(m.group(1), []).append(int(m.group(2)))
        if not frames:
            continue
        mode = raw.parent.name
        print(f"## {mode}\n")
        for segment, numbers in sorted(frames.items()):
            numbers.sort()
            load = lambda n, kind: np.load(raw / f"{segment}-f{n:03d}-{kind}.npy").astype(np.float32)
            results = {name: [] for name, _ in boxes}
            last = None
            for a, b in zip(numbers, numbers[1:]):
                if b != a + 1:
                    continue
                exposure = float(load(b, "exposure").ravel()[0])
                then_in = tonemap(load(a, "color")[..., :3], exposure)
                now_in = tonemap(load(b, "color")[..., :3], exposure)
                now_out = tonemap(load(b, "output")[..., :3], exposure)
                motion = load(b, "motion")
                h, w = motion.shape[:2]
                ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
                px = xs + motion[..., 0]
                py = ys + motion[..., 1]
                onscreen = (px >= 0) & (px <= w - 1) & (py >= 0) & (py <= h - 1)
                prev = bilinear(then_in, px, py)
                change = prev - now_in
                changed = onscreen & (np.abs(change).max(-1) > THRESHOLD)
                # The output as now_in + kept * (prev - now_in), least squares per pixel.
                kept = np.clip(((now_out - now_in) * change).sum(-1) / np.maximum((change * change).sum(-1), 1e-6), 0.0, 1.0)
                off = 255 * np.abs(now_out - now_in).mean(-1)
                for name, box in boxes:
                    mask = box_mask((h, w), box)
                    c = changed & mask
                    results[name].append((c.sum() / max(mask.sum(), 1), kept[c].mean() if c.sum() >= 20 else float("nan"),
                                          off[mask].mean()))
                last = (now_in, now_out)
            print(f"### {segment}\n")
            print("| box | changed | kept | off |")
            print("| --- | --- | --- | --- |")
            for name, box in boxes:
                rows = np.array(results[name]) if results[name] else np.zeros((0, 3))
                if len(rows) == 0:
                    continue
                kept_values = rows[:, 1][~np.isnan(rows[:, 1])]
                kept_text = f"{kept_values.mean():.2f}" if len(kept_values) else "-"
                label = name if box is None else f"{name} {box}"
                print(f"| {label} | {100 * rows[:, 0].mean():.1f}% | {kept_text} | {rows[:, 2].mean():.1f} |")
            print()
            if last is not None:
                crop_sheet(run / "ghostcheck" / f"{mode}-{segment}.png", boxes, *last)


if __name__ == "__main__":
    main()
