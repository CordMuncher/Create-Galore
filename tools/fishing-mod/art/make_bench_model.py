#!/usr/bin/env python3
"""Writes the Tackle Bench's block model.

    python3 tools/fishing-mod/art/make_bench_model.py

Run make_textures.py first: the small parts are textured from its tackle_bench_parts.png, by the cell names in
BENCH_CELLS. The bench is drawn facing north (the side the player stands at); the blockstate turns it.

A spruce workbench in Create's style: an andesite-cased top, log legs on andesite feet, a shelf with a tackle box
and spare blanks. On the top, as real 3D parts: a rod lying across it on its guides (its tip overhangs the side), a
reel standing on its foot, a spool of line, a vise clamping a blank being wrapped, and a tin of floats and sinkers.
Everything stays inside the block's own space except the rod tip and the vise handle, which reach a few pixels past
the edge; the hitbox (TackleBenchBlock.SHAPE) is the table alone, so nothing about the block's footprint changes.
"""
import json
import pathlib

from make_textures import BENCH_CELLS

HERE = pathlib.Path(__file__).resolve().parent
ASSETS = HERE.parent / "src/main/resources/assets/create_galore_fishing"
NS = "create_galore_fishing:"
ALL = ("north", "south", "east", "west", "up", "down")
CELL = {name: i for i, (name, _, _) in enumerate(BENCH_CELLS)}


def cell_uv(name):
    """UV of a parts cell (16 UV units span the 32 px texture, so a 4 px cell is 2 units), pulled in a hair so the
    edges never sample the neighbouring cell."""
    i = CELL[name]
    u, v = (i % 8) * 2, (i // 8) * 2
    return [u + 0.05, v + 0.05, u + 1.95, v + 1.95]


def part(start, end, cell, faces=ALL, rotation=None):
    """A small part textured with one parts cell on every face."""
    e = {"from": start, "to": end, "faces": {f: {"texture": "#parts", "uv": cell_uv(cell)} for f in faces}}
    if rotation:
        e["rotation"] = rotation
    return e


def block(start, end, texture, faces=ALL, uv=None):
    """A piece textured with a full block texture; UVs follow the piece's position unless given per face."""
    e = {"from": start, "to": end, "faces": {f: {"texture": "#" + texture} for f in faces}}
    for f, u in (uv or {}).items():
        e["faces"][f]["uv"] = u
    return e


def disc(axis, centre, radius, lo, hi, cell, rotation=None):
    """A round part as Minecraft draws round things: a plus of two boxes and a square between, so the outline steps
    like a pixel circle. `axis` is the disc's axis, `lo`..`hi` its extent along it."""
    out = []
    for a, b in ((radius, radius * 0.55), (radius * 0.55, radius), (radius * 0.85, radius * 0.85)):
        s, e = list(centre), list(centre)
        i = "xyz".index(axis)
        others = [k for k in range(3) if k != i]
        s[i], e[i] = lo, hi
        s[others[0]], e[others[0]] = centre[others[0]] - a, centre[others[0]] + a
        s[others[1]], e[others[1]] = centre[others[1]] - b, centre[others[1]] + b
        out.append(part([round(v, 3) for v in s], [round(v, 3) for v in e], cell, rotation=rotation))
    return out


# --- the bench itself --------------------------------------------------------------------------------------------

LEGS = [(1, 1), (12, 1), (1, 12), (12, 12)]
BODY = [
    # Top: Create's andesite casing on top (planks framed in andesite alloy), spruce at the edges.
    block([0, 13, 0], [16, 16, 16], "planks", ("north", "south", "east", "west", "down")),
    block([0, 13, 0], [16, 16, 16], "casing", ("up",)),
    # Apron under the top, set in.
    block([1.5, 11, 1.5], [14.5, 13, 14.5], "log", ("north", "south", "east", "west"),
          {f: [0, 2, 13, 4] for f in ("north", "south", "east", "west")}),
    # Shelf.
    block([2, 3, 2], [14, 5, 14], "planks"),
]
for x, z in LEGS:
    BODY.append(block([x, 1, z], [x + 3, 11, z + 3], "log", ("north", "south", "east", "west"),
                      {f: [0, 3, 3, 13] for f in ("north", "south", "east", "west")}))
    # Andesite alloy feet and corner brackets under the top.
    BODY.append(block([x - 0.5, 0, z - 0.5], [x + 3.5, 1, z + 3.5], "alloy", ALL,
                      {**{f: [2, 13, 6, 14] for f in ("north", "south", "east", "west")}, "up": [2, 2, 6, 6],
                       "down": [2, 2, 6, 6]}))
    BODY.append(block([x - 0.25, 11, z - 0.25], [x + 3.25, 13, z + 3.25], "alloy", ("north", "south", "east", "west", "down"),
                      {**{f: [0, 0, 4, 2] for f in ("north", "south", "east", "west")}, "down": [1, 1, 5, 5]}))

# --- on the shelf: a tackle box and two spare blanks ----------------------------------------------------------

SHELF = [
    part([3, 5, 4.5], [10, 8, 10.5], "box_red"),
    part([2.9, 7.6, 4.4], [10.1, 8.0, 10.6], "dark_wood"),          # the lid's rim
    part([6, 6.2, 4.2], [7, 7.6, 4.5], "brass"),                     # latch
    part([5, 8, 7.1], [5.5, 9, 7.9], "brass"), part([7.5, 8, 7.1], [8, 9, 7.9], "brass"),
    part([5, 9, 7.1], [8, 9.5, 7.9], "dark_wood"),                   # handle
    part([1, 5, 11.1], [15, 5.6, 11.7], "blank_green"),
    part([1.5, 5, 12.3 - 1.4], [14.5, 5.8, 12.3 - 0.6], "blank"),
]

# --- on the top --------------------------------------------------------------------------------------------------

TOP = 16


def rod():
    """A two-handed rod lying on its guides, butt at the back left, the tip past the east edge near the front. Built along x around
    z=11 and turned 22.5 degrees about one origin, so every part stays lined up."""
    rot = {"origin": [8, TOP, 11], "axis": "y", "angle": 22.5}
    z, y = 11, TOP + 0.75      # the blank's centre line

    def seg(x0, x1, d, cell, faces=ALL):
        return part([x0, y - d / 2, z - d / 2], [x1, y + d / 2, z + d / 2], cell, faces, rot)

    out = [seg(-1.5, -0.9, 1.3, "rubber"), seg(-0.9, 4, 1.3, "cork"), seg(4, 4.6, 1.2, "brass"),
           seg(4.6, 6.8, 0.9, "gunmetal"), seg(6.8, 7.4, 1.1, "brass"), seg(7.4, 9.4, 1.1, "cork"),
           seg(9.4, 9.9, 0.8, "wrap"), seg(9.9, 15.5, 0.7, "blank"), seg(15.5, 21.5, 0.5, "blank"),
           seg(21.5, 21.9, 0.5, "iron")]
    # Line guides: a thread wrap on the blank and an open ring standing under it (the rod rests on them).
    for x, size in ((11.5, 1.3), (14.5, 1.1), (17.3, 0.9), (19.8, 0.75)):
        out.append(seg(x - 0.3, x + 0.3, 0.8, "wrap"))
        out.append(part([x - 0.05, TOP, z - size / 2], [x + 0.05, TOP + size, z + size / 2], "ring",
                        ("east", "west"), rot))
        out.append(part([x - 0.15, TOP + size - 0.05, z - 0.1], [x + 0.15, y, z + 0.1], "iron", ALL, rot))
    return out


def reel():
    """A reel standing on its foot at the front left: two brass side plates, the spool of green line between, the
    crank and its wooden knob on the west side."""
    cx, cy, cz, r = 4.4, TOP + 2.5, 4.2, 1.9
    out = disc("x", (cx, cy, cz), r, 3.2, 3.6, "brass") + disc("x", (cx, cy, cz), r, 5.2, 5.6, "brass")
    out += disc("x", (cx, cy, cz), r - 0.5, 3.6, 5.2, "green_line")
    out += disc("x", (cx, cy, cz), 0.55, 2.9, 3.2, "gunmetal")
    out += [part([3.0, TOP, cz - 0.9], [5.8, TOP + 0.35, cz + 0.9], "brass"),            # foot
            part([4.0, TOP + 0.35, cz - 0.4], [4.8, cy - r + 0.2, cz + 0.4], "gunmetal"),  # stem
            # Pillars joining the side plates at the back and top, as on a fly reel's cage.
            part([3.6, cy - 0.3, cz + r - 0.45], [5.2, cy + 0.3, cz + r - 0.05], "gunmetal"),
            part([3.6, cy + r - 0.45, cz - 0.3], [5.2, cy + r - 0.05, cz + 0.3], "gunmetal"),
            part([2.55, cy - 0.3, cz - 0.3], [2.9, cy + 1.6, cz + 0.3], "brass"),          # crank arm
            part([1.75, cy + 1.2, cz - 0.4], [2.55, cy + 2.0, cz + 0.4], "dark_wood")]     # knob
    return out


def spool():
    """A wooden spool of line standing on end, front centre."""
    c = (8.3, 0, 3.0)
    return (disc("y", c, 1.6, TOP, TOP + 0.5, "spool_wood") + disc("y", c, 1.25, TOP + 0.5, TOP + 2.3, "line")
            + disc("y", c, 1.6, TOP + 2.3, TOP + 2.8, "spool_wood") + disc("y", c, 0.4, TOP + 2.8, TOP + 3.0, "dark_wood"))


def vise():
    """A small andesite vise clamped to the front right edge, holding a blank with a fresh thread wrap."""
    x0, x1 = 12.0, 15.2
    out = [
        block([x0, TOP, 1.0], [x1, TOP + 0.8, 5.5], "alloy", ALL, {f: [2, 2, 6, 3] for f in ALL}),
        block([x0, TOP + 0.8, 4.6], [x1, TOP + 3.0, 5.4], "alloy", ALL, {f: [2, 3, 6, 6] for f in ALL}),
        block([x0, TOP + 0.8, 1.2], [x1, TOP + 3.0, 2.0], "alloy", ALL, {f: [2, 3, 6, 6] for f in ALL}),
        part([x0 + 0.4, TOP + 1.7, 4.3], [x1 - 0.4, TOP + 2.9, 4.6], "brass"),        # jaw pads
        part([x0 + 0.4, TOP + 1.7, 2.0], [x1 - 0.4, TOP + 2.9, 2.3], "brass"),
        part([x0 + 0.4, TOP + 1.0, 2.0], [x0 + 1.0, TOP + 1.6, 4.5], "iron"),       # guide rails
        part([x1 - 1.0, TOP + 1.0, 2.0], [x1 - 0.4, TOP + 1.6, 4.5], "iron"),
        part([13.35, TOP + 1.6, -1.0], [13.85, TOP + 2.1, 1.2], "iron"),              # screw
        part([12.2, TOP + 1.65, -1.0], [15.0, TOP + 2.05, -0.6], "iron"),             # T handle
        part([11.7, TOP + 1.5, -1.15], [12.2, TOP + 2.2, -0.45], "dark_wood"),
        part([15.0, TOP + 1.5, -1.15], [15.5, TOP + 2.2, -0.45], "dark_wood"),
        # The clamp under the bench's front edge.
        block([13.0, 12.2, -0.6], [14.2, TOP + 0.8, 0], "alloy", ALL, {f: [6, 2, 7, 8] for f in ALL}),
        block([13.0, 12.2, -0.6], [14.2, 12.8, 1.5], "alloy", ALL, {f: [6, 2, 7, 3] for f in ALL}),
        part([13.35, 12.8, 0.9], [13.85, 13.0, 1.4], "iron"),
        # The blank in the jaws, and its wrap.
        part([10.4, TOP + 2.0, 3.0], [16.8, TOP + 2.6, 3.6], "blank_green"),
        part([10.8, TOP + 1.9, 2.9], [11.4, TOP + 2.7, 3.7], "wrap"),
    ]
    return out


def tin():
    """An open brass tin at the back right with two floats and three lead sinkers."""
    x0, z0, x1, z1, h = 11.3, 11.6, 15.6, 15.4, 1.4
    out = [part([x0, TOP, z0], [x1, TOP + 0.3, z1], "brass"),
           part([x0, TOP, z0], [x1, TOP + h, z0 + 0.3], "brass"), part([x0, TOP, z1 - 0.3], [x1, TOP + h, z1], "brass"),
           part([x0, TOP, z0], [x0 + 0.3, TOP + h, z1], "brass"), part([x1 - 0.3, TOP, z0], [x1, TOP + h, z1], "brass")]
    for z in (12.4, 13.8):
        out += [part([11.9, TOP + 0.3, z - 0.45], [13.4, TOP + 1.2, z + 0.45], "red"),
                part([13.4, TOP + 0.4, z - 0.4], [14.4, TOP + 1.1, z + 0.4], "white"),
                part([14.4, TOP + 0.6, z - 0.15], [15.1, TOP + 0.9, z + 0.15], "red")]
    for x in (12.2, 13.4, 14.5):
        out.append(part([x, TOP + 0.3, 14.4], [x + 0.8, TOP + 1.0, 15.0], "lead"))
    return out


def hooks():
    """Two loose hooks between the reel and the spool: shank, bend and point."""
    out = []
    for x, z in ((7.0, 6.2), (8.4, 7.0)):
        out += [part([x, TOP, z], [x + 0.3, TOP + 0.25, z + 2.0], "iron"),
                part([x, TOP, z + 2.0], [x + 1.0, TOP + 0.25, z + 2.3], "iron"),
                part([x + 0.7, TOP, z + 1.3], [x + 1.0, TOP + 0.25, z + 2.0], "iron"),
                part([x - 0.1, TOP, z - 0.3], [x + 0.4, TOP + 0.3, z], "iron")]          # the eye
    return out


def main():
    textures = {"particle": "minecraft:block/spruce_planks", "planks": "minecraft:block/spruce_planks",
                "log": "minecraft:block/stripped_spruce_log", "casing": "create:block/andesite_casing",
                "alloy": "create:block/andesite_block", "parts": NS + "block/tackle_bench_parts"}
    model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures,
             "elements": BODY + SHELF + rod() + reel() + spool() + vise() + tin() + hooks(),
             # The rod tip reaches past the block; shrink the icon a little so it fits the slot.
             "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.58, 0.58, 0.58]}}}
    path = ASSETS / "models/block/tackle_bench.json"
    path.write_text(json.dumps(model, indent=1) + "\n")
    print(f"wrote {path.name}: {len(model['elements'])} elements")


if __name__ == "__main__":
    main()
