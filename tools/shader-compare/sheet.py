#!/usr/bin/env python3
"""Contact sheets of a shader compare run: one JPEG per scene, each pack a row (its defaults, then its highest
preset), small enough to commit. Several run folders (a run split in parts) make one set of sheets.
Also one arrive-SCENE.jpg per scene when the run saved arrival frames: a row per pack and pass, the frames from
the first 0.1, 0.5, 1 and 2 seconds after arriving, then the settled shot (the jitter shows as the rows differ).
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
passes = [p for p in ("defaults", "highest") if any(m["pass"] == p for m in manifest["modes"])]  # a run of one pass: one column
width, height = manifest.get("resolution", [1920, 1080])
tile_h = round(tile_w * height / width)
label_h = 20
shots = {(s["mode"], s["viewpoint"]): s["file"] for s in manifest["shots"] if s.get("path", "still") == "still"}
arrivals = {}
for s in manifest["shots"]:
    if s.get("path") == "arrive":
        arrivals.setdefault((s["mode"], s["viewpoint"]), []).append((s["frame"], s["file"]))
scenes = []
for s in manifest["shots"]:
    if s.get("path", "still") == "still" and s["viewpoint"] not in scenes:
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
                text = " ".join(filter(None, (mode.get("title") or pack, mode.get("version")))) + " · " + pass_
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

# Arrival strips.
strip_w = 256
strip_h = round(strip_w * height / width)
for scene in scenes:
    rows = [(name, sorted(frames)) for (name, sc), frames in arrivals.items() if sc == scene]
    if not rows:
        continue
    sheet = Image.new("RGB", (strip_w * 5, (strip_h + label_h) * len(rows)), (24, 24, 24))
    draw = ImageDraw.Draw(sheet)
    for row, (name, frames) in enumerate(rows):
        y = row * (strip_h + label_h)
        draw.text((4, y + 3), name + "   " + "   ".join(str(ms) + " ms" for ms, _ in frames) + "   settled", fill=(230, 230, 230), font=font)
        files = [f for _, f in frames] + [shots.get((name, scene))]
        for col, file in enumerate(files):
            if file and pathlib.Path(file).exists():
                with Image.open(file) as im:
                    sheet.paste(im.convert("RGB").resize((strip_w, strip_h), Image.LANCZOS), (col * strip_w, y + label_h))
    sheet.save(out / ("arrive-" + scene + ".jpg"), quality=75, optimize=True)
    print("wrote", out / ("arrive-" + scene + ".jpg"))
