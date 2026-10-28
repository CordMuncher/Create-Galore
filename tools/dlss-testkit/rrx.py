#!/usr/bin/env python3
"""The Photon RR research's numbers, crops and clips (needs numpy and Pillow; imageio and imageio-ffmpeg for clips).

python rrx.py table RUN [TIMES_RUN]
    A Markdown table per viewpoint, one row per mode, from a picture run (RRX1, RRX2) and optionally a timing run
    (RRX3) for the frame times:
    - raw: what the mode leaves raw for Ray Reconstruction (the Bridge's switches) and its Photon options;
    - frame ms: the timing run's average frame time on the still path (its second cycle: the kit keeps a mode's last),
      and the difference from rrx-shipped at the same render scale (what the switch costs or saves);
    - shimmer: with the camera still, the average change in brightness between consecutive frames (0-255 levels) and
      the share of pixels changing by more than 8 levels: the noise Ray Reconstruction lets through, frame to frame;
    - residue: after the flick, how far the picture still is from the settled one (as metrics.py);
    - vs shipped: the average difference of the settled frame from rrx-shipped's at the same render scale: how much
      the switch changes the picture at all (0: nothing visible). Only where nothing moves (dark, shore, canopy): at
      moving the machines stand elsewhere in each mode.
python rrx.py crops RUN OUT VIEWPOINT=X0,Y0,X1,Y1 [VIEWPOINT=... ...] [--modes a+b+c] [--zoom 2]
    For each box (pixels from the picture's top left), one JPG sheet of that crop in each mode (the still path's
    last frame), labelled, side by side in rows of four.
python rrx.py clips RUN OUT [--modes a+b+c] [--box X0,Y0,X1,Y1]
    From a clip run (RRX4): an MP4 per viewpoint and mode (60 fps, the 64 frames in a row), and one per viewpoint with
    the modes side by side (two columns, each half size, labelled). --box crops first.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


def luma(path):
    rgb = np.asarray(Image.open(path).convert("RGB"), dtype=np.float32)
    return rgb @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)


def load(run):
    run = Path(run)
    manifest = json.loads((run / "manifest.json").read_text())
    files = {(s["mode"], s["viewpoint"], s["path"], s["frame"]): run / s["file"] for s in manifest["shots"]}
    return run, manifest, files


def scale_of(mode):
    return mode.get("renderScale", 1.0)


def baseline_name(mode, modes):
    """rrx-shipped at the same render scale (rrx-dlaa-shipped, rrx-perf-shipped), if the run has it."""
    scale = scale_of(mode)
    for m in modes:
        if m["name"].endswith("shipped") and abs(scale_of(m) - scale) < 1e-3:
            return m["name"]
    return None


def frame_ms(times_manifest):
    """Mode -> viewpoint -> average frame ms on the still path (the last time the timing run played the mode)."""
    if times_manifest is None:
        return {}
    result = {}
    for mode in times_manifest["modes"]:
        result[mode["name"]] = mode.get("frameMs", {})
    return result


def raw_of(mode):
    rr = mode.get("rayReconstruction")
    raw = "-"
    if rr:
        for part in rr.split(","):
            if part.startswith("raw="):
                raw = part[4:]
    photon = mode.get("photon") or ""
    extra = ",".join(p for p in photon.split(",") if p and p != "ultra")
    return raw + (f" ({extra})" if extra else "")


def table(args):
    run, manifest, files = load(args[0])
    times = load(args[1])[1] if len(args) > 1 else None
    ms = frame_ms(times)
    modes = manifest["modes"]
    still = manifest["paths"]["still"]
    viewpoints = sorted({v for (_, v, _, _) in files}, key=lambda v: [x["name"] for x in manifest["viewpoints"]].index(v)
                        if v in [x["name"] for x in manifest["viewpoints"]] else 99)
    print(f"Run {manifest['run']} at {manifest['resolution'][0]}x{manifest['resolution'][1]}, {manifest['shaderPack']}"
          + (f"; frame times from {times['run']}" if times else "") + "\n")
    for viewpoint in viewpoints:
        cache = {}

        def shot(name, path, frame):
            key = (name, path, frame)
            if key not in cache:
                f = files.get((name, viewpoint, path, frame))
                cache[key] = luma(f) if f is not None and f.exists() else None
            return cache[key]

        rows = []
        for mode in modes:
            name = mode["name"]
            frames = [f for f in (shot(name, "still", i) for i in still) if f is not None]
            if not frames:
                continue
            diffs = [np.abs(b - a) for a, b in zip(frames, frames[1:])]
            shimmer = f"{np.mean([d.mean() for d in diffs]):.2f} / {100 * np.mean([(d > 8).mean() for d in diffs]):.1f}%" if diffs else "-"
            residues = []
            for moving, settled in ((70, 99), (110, 159)):
                a, b = shot(name, "flick", moving), shot(name, "flick", settled)
                if a is not None and b is not None:
                    residues.append(f"{np.abs(a - b).mean():.2f}")
            base = baseline_name(mode, modes)
            versus = "-"
            if base and base != name and viewpoint != "moving":
                b = shot(base, "still", still[-1])
                if b is not None and b.shape == frames[-1].shape:
                    versus = f"{np.abs(frames[-1] - b).mean():.2f}"
            t = ms.get(name, {}).get(viewpoint)
            tb = ms.get(base, {}).get(viewpoint) if base else None
            timing = "-" if t is None else f"{t:.2f}" + (f" ({t - tb:+.2f})" if tb is not None and base != name else "")
            rows.append(f"| {name} | {raw_of(mode)} | {timing} | {shimmer} | {' / '.join(residues) or '-'} | {versus} |")
        if rows:
            print(f"### {viewpoint}\n")
            print("| mode | raw (options) | frame ms (vs shipped) | shimmer (levels / >8) | residue | vs shipped |")
            print("| --- | --- | --- | --- | --- | --- |")
            print("\n".join(rows) + "\n")


def label(image, text):
    draw = ImageDraw.Draw(image)
    w = 8 + 7 * len(text)
    draw.rectangle([0, 0, w, 18], fill=(0, 0, 0))
    draw.text((4, 3), text, fill=(255, 255, 255))
    return image


def option(args, name, default=None):
    if name in args:
        i = args.index(name)
        value = args[i + 1]
        del args[i:i + 2]
        return value
    return default


def crops(args):
    modes_arg = option(args, "--modes")
    zoom = int(option(args, "--zoom", "2"))
    run, manifest, files = load(args[0])
    out = Path(args[1])
    out.mkdir(parents=True, exist_ok=True)
    names = modes_arg.split("+") if modes_arg else [m["name"] for m in manifest["modes"]]
    last = manifest["paths"]["still"][-1]
    for spec in args[2:]:
        viewpoint, box = spec.split("=", 1)
        x0, y0, x1, y1 = (int(v) for v in box.split(","))
        tiles = []
        for name in names:
            f = files.get((name, viewpoint, "still", last))
            if f is None or not f.exists():
                continue
            tile = Image.open(f).convert("RGB").crop((x0, y0, x1, y1))
            tile = tile.resize(((x1 - x0) * zoom, (y1 - y0) * zoom), Image.NEAREST)
            tiles.append(label(tile, name))
        if not tiles:
            print(f"{viewpoint}: no shots")
            continue
        columns = min(4, len(tiles))
        rows = (len(tiles) + columns - 1) // columns
        w, h = tiles[0].size
        sheet = Image.new("RGB", (columns * w + (columns - 1) * 4, rows * h + (rows - 1) * 4), (40, 40, 40))
        for i, tile in enumerate(tiles):
            sheet.paste(tile, ((i % columns) * (w + 4), (i // columns) * (h + 4)))
        target = out / f"{viewpoint}-{x0}-{y0}.jpg"
        sheet.save(target, quality=90)
        print(f"wrote {target}")


def clips(args):
    import imageio.v2 as imageio

    modes_arg = option(args, "--modes")
    box = option(args, "--box")
    run, manifest, files = load(args[0])
    out = Path(args[1])
    out.mkdir(parents=True, exist_ok=True)
    names = modes_arg.split("+") if modes_arg else [m["name"] for m in manifest["modes"]]
    frames = manifest["paths"]["clip"]
    viewpoints = sorted({v for (_, v, p, _) in files if p == "clip"})

    def frame(name, viewpoint, i):
        f = files.get((name, viewpoint, "clip", i))
        if f is None or not f.exists():
            return None
        image = Image.open(f).convert("RGB")
        if box:
            image = image.crop(tuple(int(v) for v in box.split(",")))
        # Even sizes for H.264.
        return image.crop((0, 0, image.width // 2 * 2, image.height // 2 * 2))

    for viewpoint in viewpoints:
        present = [n for n in names if frame(n, viewpoint, frames[0]) is not None]
        for name in present:
            target = out / f"{viewpoint}-{name}.mp4"
            with imageio.get_writer(target, fps=60, codec="libx264", quality=8, macro_block_size=2) as writer:
                for i in frames:
                    image = frame(name, viewpoint, i)
                    if image is not None:
                        writer.append_data(np.asarray(image))
            print(f"wrote {target}")
        if len(present) > 1:
            target = out / f"{viewpoint}-side-by-side.mp4"
            with imageio.get_writer(target, fps=60, codec="libx264", quality=8, macro_block_size=2) as writer:
                for i in frames:
                    tiles = [frame(n, viewpoint, i) for n in present]
                    tiles = [label(t.resize((t.width // 2 // 2 * 2, t.height // 2 // 2 * 2)), n) for t, n in zip(tiles, present) if t]
                    w, h = tiles[0].size
                    rows = (len(tiles) + 1) // 2
                    sheet = Image.new("RGB", (2 * w, rows * h))
                    for k, tile in enumerate(tiles):
                        sheet.paste(tile, ((k % 2) * w, (k // 2) * h))
                    writer.append_data(np.asarray(sheet))
            print(f"wrote {target}")


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("table", "crops", "clips"):
        print(__doc__)
        sys.exit(1)
    {"table": table, "crops": crops, "clips": clips}[sys.argv[1]](sys.argv[2:])


if __name__ == "__main__":
    main()
