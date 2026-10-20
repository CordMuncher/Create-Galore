#!/usr/bin/env python3
"""Writes the Aquarium Tank's block models and blockstate.

    python3 tools/fishing-mod/art/make_aquarium_models.py

A tank block is glass panes in a steel frame with a sand floor, open at the top. Its six boolean properties say
which faces join another block of the same tank; the multipart blockstate leaves out the glass there, the frame
bars along joined edges, the corner posts where the tank goes on, and the floor above the bottom layer, so a
tank of any size reads as one piece. Overlapping frame parts use the same texture with UVs taken from their
position in the block, so where two meet they draw the same pixels and don't flicker.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
ASSETS = HERE.parent / "src/main/resources/assets/create_galore_fishing"
NS = "create_galore_fishing:"
SIDES = ("north", "south", "east", "west")
FRAME = NS + "block/aquarium_frame"
GLASS = NS + "block/aquarium_glass"


def faces(texture, which=("north", "south", "east", "west", "up", "down")):
    return {f: {"texture": "#" + texture} for f in which}


def span(side, width):
    """(axis, lo, hi) of a slab `width` px thick against `side` of the block."""
    return {"north": ("z", 0, width), "south": ("z", 16 - width, 16), "west": ("x", 0, width),
            "east": ("x", 16 - width, 16), "down": ("y", 0, width), "up": ("y", 16 - width, 16)}[side]


def box(*spans):
    lo = {"x": 0, "y": 0, "z": 0}
    hi = {"x": 16, "y": 16, "z": 16}
    for axis, a, b in spans:
        lo[axis], hi[axis] = a, b
    return [lo["x"], lo["y"], lo["z"]], [hi["x"], hi["y"], hi["z"]]


def element(spans, texture, which=None):
    start, end = box(*spans)
    return {"from": start, "to": end, "faces": faces(texture, which or ("north", "south", "east", "west", "up", "down"))}


def glass(side):
    """A pane in the middle of the frame's thickness, drawn from both sides (it is seen from inside the tank too)."""
    axis, _, _ = span(side, 1)
    at = 1 if side in ("north", "west") else 15
    which = ("north", "south") if axis == "z" else ("east", "west")
    return element([(axis, at, at)], "glass", which)


PARTS = {}
for s in SIDES:
    PARTS[f"glass_{s}"] = ({s: False}, [glass(s)], "translucent")
    for e in ("up", "down"):
        PARTS[f"bar_{s}_{e}"] = ({s: False, e: False}, [element([span(s, 2), span(e, 2)], "frame")], "cutout")
for a, b in (("north", "east"), ("north", "west"), ("south", "east"), ("south", "west")):
    PARTS[f"post_{a}_{b}"] = ({a: False, b: False}, [element([span(a, 2), span(b, 2)], "frame")], "cutout")
PARTS["floor"] = ({"down": False}, [{"from": [0, 0, 0], "to": [16, 1, 16],
                                     "faces": {**faces("frame", ("north", "south", "east", "west", "down")),
                                               "up": {"texture": "#sand"}}}], "cutout")
TEXTURES = {"frame": FRAME, "glass": GLASS, "sand": "minecraft:block/sand", "particle": FRAME}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    multipart = []
    for name, (when, elements, render) in PARTS.items():
        write(ASSETS / f"models/block/aquarium/{name}.json",
              {"render_type": "minecraft:" + render, "textures": TEXTURES, "elements": elements})
        multipart.append({"when": {k: str(v).lower() for k, v in when.items()},
                          "apply": {"model": NS + f"block/aquarium/{name}"}})
    write(ASSETS / "blockstates/aquarium_tank.json", {"multipart": multipart})
    # The item is a tank of one block: every part.
    everything = [e for _, elements, _ in PARTS.values() for e in elements]
    write(ASSETS / "models/item/aquarium_tank.json",
          {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": TEXTURES,
           "elements": everything})
    print(f"wrote {len(PARTS)} aquarium parts")


if __name__ == "__main__":
    main()
