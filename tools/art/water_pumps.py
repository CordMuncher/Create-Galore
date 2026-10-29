"""Draws the Iron, Gold and Diamond Water Pumps and writes their blockstates, block and item models.

A riveted box of the pump's metal. Each side has a round water intake: a dark throat behind a grille of bars, with a
faint teal of water inside, and a pipe flange ringing it. The top is the outlet a Filter Frame sits on: a square
flange round a grated opening. The bottom is a plain riveted plate. Shading is lit from the top left like Create's.
Usage, from this folder:
    python3 water_pumps.py ../bridge-mod/src/main/resources/assets/create_galore_bridge [preview.png]
"""
import json, math, os, random, sys
from PIL import Image

out = sys.argv[1]
preview = sys.argv[2] if len(sys.argv) > 2 else None

# Light to dark, roughly the vanilla iron, gold and diamond blocks.
METALS = {
    "iron": [(0xf2, 0xf2, 0xf2), (0xdc, 0xdc, 0xdc), (0xc5, 0xc5, 0xc5), (0xa8, 0xa8, 0xa8), (0x84, 0x84, 0x84), (0x5e, 0x5e, 0x5e), (0x3c, 0x3c, 0x3c)],
    "gold": [(0xff, 0xff, 0xb5), (0xfd, 0xf5, 0x5f), (0xf5, 0xcc, 0x27), (0xe9, 0xb1, 0x15), (0xc0, 0x86, 0x0f), (0x8c, 0x5a, 0x0a), (0x52, 0x33, 0x08)],
    "diamond": [(0xd5, 0xff, 0xf6), (0xa1, 0xfb, 0xe8), (0x74, 0xf2, 0xdf), (0x4a, 0xed, 0xd9), (0x2b, 0xc7, 0xac), (0x1a, 0x8c, 0x7c), (0x0e, 0x4f, 0x47)],
}
THROAT = [(0x0e, 0x16, 0x1c), (0x14, 0x24, 0x2c), (0x1c, 0x3a, 0x44), (0x2a, 0x56, 0x60)]
BAR = [(0x2a, 0x2c, 0x2e), (0x46, 0x4a, 0x4c), (0x5e, 0x62, 0x64)]


def plate(m, seed):
    """A 16 x 16 metal plate: dark rim, bright top-left bevel, shaded bottom-right, brushed middle and corner rivets."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                col = m[6] if x == 15 or y == 15 else m[5]
            elif x == 1 or y == 1:
                col = m[rnd.choice((0, 1, 1))]
            elif x == 14 or y == 14:
                col = m[4]
            else:
                # Brushed: each row a shade, with a little noise.
                col = m[2 + ((y * 7 + seed) % 3 == 0) + (rnd.random() < 0.12)]
            px[x, y] = col + (255,)
    for cx, cy in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[cx, cy] = m[0] + (255,)
        px[cx + 1 if cx < 8 else cx - 1, cy + 1 if cy < 8 else cy - 1] = m[5] + (255,)
    return img


def intake(img, m, seed):
    """A round grilled water intake in the middle of a side, ringed by a flange."""
    rnd = random.Random(seed)
    px = img.load()
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if d <= 3.3:
                # Throat: dark water behind a grille of light bars every other row.
                if y % 2 == 0:
                    col = BAR[2] if x < c else BAR[1]
                else:
                    col = THROAT[2 + (d > 2.4)] if rnd.random() < 0.5 else THROAT[1 + (d > 2.4)]
            elif d <= 3.9:
                col = m[6]
            elif d <= 5.0:
                lit = (x - c) + (y - c) < 0
                col = (m[0] if d <= 4.4 else m[1]) if lit else (m[4] if d <= 4.4 else m[5])
            elif d <= 5.6:
                col = m[6]
            else:
                continue
            px[x, y] = col + (255,)


def outlet(img, m, seed):
    """The top: a square flange round a grated opening the Filter Frame sits over."""
    rnd = random.Random(seed)
    px = img.load()
    for y in range(3, 13):
        for x in range(3, 13):
            if x in (3, 12) or y in (3, 12):
                col = m[6]
            elif x in (4, 11) or y in (4, 11):
                col = m[1] if (x == 4 or y == 4) else m[5]
            else:
                col = BAR[1 + (rnd.random() < 0.3)] if (x + y) % 3 == 0 else THROAT[rnd.choice((0, 0, 1, 2))]
            px[x, y] = col + (255,)


def textures(tier):
    m = METALS[tier]
    seed = sum(map(ord, tier))
    side = plate(m, seed)
    intake(side, m, seed + 1)
    top = plate(m, seed + 2)
    outlet(top, m, seed + 3)
    bottom = plate(m, seed + 4)
    return side, top, bottom


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


faces = []
for tier in METALS:
    name = f"{tier}_water_pump"
    side, top, bottom = textures(tier)
    tex = os.path.join(out, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    side.save(os.path.join(tex, f"{name}_side.png"))
    top.save(os.path.join(tex, f"{name}_top.png"))
    bottom.save(os.path.join(tex, f"{name}_bottom.png"))
    write_json(os.path.join(out, "models", "block", f"{name}.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {f: f"create_galore_bridge:block/{name}_{f}" for f in ("side", "top", "bottom")},
    })
    write_json(os.path.join(out, "models", "item", f"{name}.json"), {"parent": f"create_galore_bridge:block/{name}"})
    write_json(os.path.join(out, "blockstates", f"{name}.json"), {"variants": {"": {"model": f"create_galore_bridge:block/{name}"}}})
    faces.append((side, top, bottom))

if preview:
    sheet = Image.new("RGBA", (16 * 3 * 3 + 8 * 2, 16), (0, 0, 0, 0))
    for i, row in enumerate(faces):
        for j, face in enumerate(row):
            sheet.paste(face, (i * 56 + j * 16, 0))
    sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(preview)
