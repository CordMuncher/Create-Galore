#!/usr/bin/env python3
"""Full-size shots of our pack from a shader compare run, as JPEGs small enough to commit: every settled shot of
every pass of the packs named (default: ours) at the run's own size, and the arrival frames of its defaults pass at
960 px wide. Several run folders make one set.
Usage: commit_shots.py OUT_FOLDER RUN_FOLDER [RUN_FOLDER...] [--pack ours]   (Pillow)
Writes OUT_FOLDER/<pack>-<pass>/<scene>.jpg and OUT_FOLDER/<pack>-defaults/arrive/<scene>-<ms>ms.jpg."""
import json, pathlib, sys
from PIL import Image

args = sys.argv[1:]
packs = ["ours"]
if "--pack" in args:
    i = args.index("--pack")
    packs = args[i + 1].split("+")
    del args[i:i + 2]
out = pathlib.Path(args[0])
for run in map(pathlib.Path, args[1:]):
    manifest = json.loads((run / "manifest.json").read_text())
    modes = {m["name"]: m for m in manifest["modes"]}
    for s in manifest["shots"]:
        mode = modes.get(s["mode"])
        src = run / s["file"]
        if mode is None or mode["pack"] not in packs or not src.exists():
            continue
        with Image.open(src) as im:
            im = im.convert("RGB")
            if s.get("path", "still") == "arrive":
                if mode["pass"] != "defaults":
                    continue
                dest = out / s["mode"] / "arrive" / (pathlib.Path(s["file"]).stem + ".jpg")
                im = im.resize((960, round(960 * im.height / im.width)), Image.LANCZOS)
                quality = 80
            else:
                dest = out / s["mode"] / (s["viewpoint"] + ".jpg")
                quality = 92
            dest.parent.mkdir(parents=True, exist_ok=True)
            im.save(dest, quality=quality, optimize=True)
            print("wrote", dest)
