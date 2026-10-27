#!/usr/bin/env python3
"""Contact sheets of a shader compare run: one JPEG per scene, each pack a row (its defaults, then its highest
preset), small enough to commit. Several run folders (a run split in parts) make one set of sheets.
Usage: sheet.py OUT_FOLDER RUN_FOLDER [RUN_FOLDER...]   (Pillow)"""
import json, pathlib, sys
from PIL import Image, ImageDraw, ImageFont

out = pathlib.Path(sys.argv[1])
runs = [pathlib.Path(a) for a in sys.argv[2:]]
tile_w = 384
manifest = {"modes": [], "shots": []}
for run in runs:
    m = json.loads((run / "manifest.json").read_text())
    manifest["resolution"] = m.get("resolution", [1920, 1080])
    manifest["modes"] += m["modes"]
    manifest["shots"] += [dict(s, file=str(run / s["file"])) for s in m["shots"]]
out.mkdir(parents=True, exist_ok=True)
try:
    font = ImageFont.load_default(size=14)
except TypeError:
    font = ImageFont.load_default()

modes = {m["name"]: m for m in manifest["modes"]}
packs = []
for m in manifest["modes"]:
    if m["pack"] not in packs:
        packs.append(m["pack"])
passes = ["defaults", "highest"]
width, height = manifest.get("resolution", [1920, 1080])
tile_h = round(tile_w * height / width)
label_h = 20
shots = {(s["mode"], s["viewpoint"]): s["file"] for s in manifest["shots"]}
scenes = []
for s in manifest["shots"]:
    if s["viewpoint"] not in scenes:
        scenes.append(s["viewpoint"])

for scene in scenes:
    sheet = Image.new("RGB", (tile_w * len(passes), (tile_h + label_h) * len(packs)), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    for row, pack in enumerate(packs):
        for col, pass_ in enumerate(passes):
            name = pack + "-" + pass_
            x, y = col * tile_w, row * (tile_h + label_h)
            mode = modes.get(name)
            if mode is None:
                text = pack + " (no presets)" if pass_ == "highest" else pack + " (not run)"
            else:
                text = "%s %s · %s" % (mode.get("title") or pack, mode.get("version") or "", pass_)
                if pass_ == "highest" and mode.get("profile"):
                    text += " (" + mode["profile"] + ")"
                if mode.get("loaded") is False:
                    text += " · FAILED"
            draw.text((x + 4, y + 3), text, fill=(230, 230, 230), font=font)
            file = shots.get((name, scene))
            if file and pathlib.Path(file).exists():
                with Image.open(file) as im:
                    sheet.paste(im.convert("RGB").resize((tile_w, tile_h), Image.LANCZOS), (x, y + label_h))
    sheet.save(out / (scene + ".jpg"), quality=80, optimize=True)
    print("wrote", out / (scene + ".jpg"))
