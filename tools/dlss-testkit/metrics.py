#!/usr/bin/env python3
"""Numbers for a DLAA test run: python metrics.py RUN_FOLDER (needs Pillow and numpy).

Prints a Markdown table per viewpoint, one row per mode:
- frame ms: average frame time on the still path (from the manifest).
- shimmer: with the camera still, the average change in brightness between consecutive frames (0-255 levels) and the
  share of pixels that change by more than 8 levels. Leaves wave the same way in every mode, so only the difference
  between modes is the anti-aliasing flickering. Lower is steadier.
- residue: two frames after the flick stops, how far the picture still is from the settled one at the same pose
  (frames 70 vs 99, and 110 vs 159 after turning back). Lower means less smear and ghosting left over.
- edge contrast: average brightness gradient of the settled still frame. Higher isn't sharper on its own: aliasing,
  grain and sharpening halos (Photon's CAS) raise it too, so it only means something next to the pictures. In round
  1, Photon's TAA scored about a third higher than DLAA from exactly those, while DLAA was the cleaner image.

Then, for the residual debug modes, a table per viewpoint of how far each frame is from the last frame moved along the
motion vectors: average brightness (0-255) and the share of pixels above 16, per camera path. Black is right. The
pack's own noise and newly uncovered areas always leave some, so compare debug-residual (everything's motion) with
debug-residual-camera (the camera's only): the gap is what the moving things' own motion vectors fix.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def luma(path):
    rgb = np.asarray(Image.open(path).convert("RGB"), dtype=np.float32)
    return rgb @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)


def main():
    run = Path(sys.argv[1])
    manifest = json.loads((run / "manifest.json").read_text())
    files = {(s["mode"], s["viewpoint"], s["path"], s["frame"]): run / s["file"] for s in manifest["shots"]}
    viewpoints = [v["name"] for v in manifest["viewpoints"]]
    still = manifest["paths"]["still"]
    print(f"Run {manifest['run']} at {manifest['resolution'][0]}x{manifest['resolution'][1]}, {manifest['renderer']}, "
          f"{manifest['shaderPack']} ({manifest['outcome']})\n")
    for viewpoint in viewpoints:
        rows = []
        for mode in manifest["modes"]:
            name = mode["name"]

            def shot(path, frame):
                f = files.get((name, viewpoint, path, frame))
                return luma(f) if f is not None and f.exists() else None

            frames = [shot("still", f) for f in still]
            frames = [f for f in frames if f is not None]
            if not frames:
                continue
            diffs = [np.abs(b - a) for a, b in zip(frames, frames[1:])]
            shimmer = f"{np.mean([d.mean() for d in diffs]):.2f} / {100 * np.mean([(d > 8).mean() for d in diffs]):.2f}%" if diffs else "-"
            residues = []
            for moving, settled in ((70, 99), (110, 159)):
                a, b = shot("flick", moving), shot("flick", settled)
                if a is not None and b is not None:
                    residues.append(f"{np.abs(a - b).mean():.2f}")
            last = frames[-1]
            edges = np.hypot(np.diff(last, axis=1)[:-1, :], np.diff(last, axis=0)[:, :-1]).mean()
            ms = mode.get("frameMs", {}).get(viewpoint)
            ran = "yes" if mode.get("dlaaActive") else "no"
            rows.append(f"| {name} | {ran} | {ms if ms is not None else '-'} | {shimmer} | {' / '.join(residues) or '-'} | {edges:.2f} |")
        if rows:
            print(f"### {viewpoint}\n")
            print("| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |")
            print("| --- | --- | --- | --- | --- | --- |")
            print("\n".join(rows) + "\n")

    paths = list(manifest["paths"])
    for viewpoint in viewpoints:
        rows = []
        for mode in manifest["modes"]:
            if mode.get("debugView") != "residual":
                continue
            cells = []
            for path in paths:
                shots = [luma(f) for (m, v, p, _), f in sorted(files.items()) if (m, v, p) == (mode["name"], viewpoint, path) and f.exists()]
                cells.append(f"{np.mean([s.mean() for s in shots]):.2f} / {100 * np.mean([(s > 16).mean() for s in shots]):.2f}%" if shots else "-")
            if any(c != "-" for c in cells):
                motion = "everything" if mode.get("objectMotion", True) else "camera only"
                rows.append(f"| {mode['name']} | {motion} | " + " | ".join(cells) + " |")
        if rows:
            print(f"### {viewpoint}: residual (level / >16)\n")
            print("| mode | motion vectors | " + " | ".join(paths) + " |")
            print("| --- | --- | " + " | ".join("---" for _ in paths) + " |")
            print("\n".join(rows) + "\n")


if __name__ == "__main__":
    main()
