#!/usr/bin/env python3
"""Writes the Reel Drives' block models, item models and blockstates.

    python3 tools/fishing-mod/art/make_drive_models.py

Each drive is drawn facing north (the rod points north); the blockstate turns it for the other facings. The Basic and
Geared drives are Create casings with the shaft running east-west through them: a frame of casing with the gearbox
face set in on the two shaft sides, so the shaft (drawn by Create) shows at both openings, as on an encased shaft. The
Fuel drive is a copper casing with an exhaust stack at the back. All three carry a small iron bracket on top that the
rod sits in.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
ASSETS = HERE.parent / "src/main/resources/assets/create_galore_fishing"
NS = "create_galore_fishing:"
IRON = "create:block/industrial_iron_block"


def box(start, end, texture, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": start, "to": end, "faces": {f: {"texture": "#" + texture} for f in faces}}


BRACKET = [
    box([4, 16, 4], [12, 17, 12], "iron"),
    box([5, 17, 6], [6, 19, 10], "iron"),
    box([10, 17, 6], [11, 19, 10], "iron"),
]

# A casing frame around an east-west shaft, the gearbox face set in 0.95 px so the shaft's ends show.
SHAFT_CASING = [
    box([0, 0, 0], [16, 2, 16], "casing"),
    box([0, 14, 0], [16, 16, 16], "casing"),
    box([0, 2, 0], [16, 14, 2], "casing"),
    box([0, 2, 14], [16, 14, 16], "casing"),
    box([0.95, 2, 2], [15.05, 14, 14], "opening", ("east", "west")),
]
# Only in the item model: Create draws the shaft for the placed block.
SHAFT = [box([0, 6, 6], [16, 10, 10], "axis", ("north", "south", "up", "down")),
         box([0, 6, 6], [16, 10, 10], "axis_top", ("east", "west"))]

FUEL_BODY = [
    box([0, 0, 0], [16, 16, 16], "casing"),
    box([6, 16, 11], [10, 26, 15], "casing"),
    box([5.5, 26, 10.5], [10.5, 27, 15.5], "iron"),
]

DRIVES = {
    "reel_drive": (SHAFT_CASING, {"casing": "create:block/andesite_casing", "opening": "create:block/gearbox"}, True),
    "geared_reel_drive": (SHAFT_CASING, {"casing": "create:block/brass_casing", "opening": "create:block/brass_gearbox"}, True),
    "fuel_reel_drive": (FUEL_BODY, {"casing": "create:block/copper_casing"}, False),
}
ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    for name, (body, textures, shaft) in DRIVES.items():
        tex = {**textures, "iron": IRON, "particle": textures["casing"]}
        write(ASSETS / f"models/block/{name}.json", {"parent": "minecraft:block/block", "textures": tex, "elements": body + BRACKET})
        item_tex = {**tex, "axis": "create:block/axis", "axis_top": "create:block/axis_top"} if shaft else tex
        write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:block/block", "textures": item_tex,
                                                    "elements": body + BRACKET + (SHAFT if shaft else [])})
        write(ASSETS / f"blockstates/{name}.json", {"variants": {
            f"facing={f}": {"model": NS + f"block/{name}", **({"y": r} if r else {})} for f, r in ROTATION.items()}})
    print(f"wrote {len(DRIVES)} drives")


if __name__ == "__main__":
    main()
