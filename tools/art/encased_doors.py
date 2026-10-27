"""Draws the cased doors and trapdoors (Andesite, Brass and Copper Casing) and writes their blockstates and models.

Each one is framed like Create's casing of the same kind (same colours, the bright inner bevel and the dark rim), with
the casing's own infill inside: andesite's upright planks, brass's dark cross planks, copper's grey plates. The door
has a riveted cross-rail where the halves meet, a porthole in the top half (tinted glass, see-through) and a small
redstone lamp, since only redstone opens it. The trapdoor is one casing panel with a porthole in the middle.
Usage, from this folder:
    python3 encased_doors.py ../bridge-mod/src/main/resources/assets/create_galore_bridge [preview.png]
"""
import json, math, os, random, sys
from PIL import Image

out = sys.argv[1]
preview = sys.argv[2] if len(sys.argv) > 2 else None

# Colours taken from Create's own casing textures.
CASINGS = {
    "andesite": {
        "rim": (0x40, 0x45, 0x43), "frame": [(0x60, 0x63, 0x5f), (0x68, 0x6c, 0x68)],
        "bevel": [(0x8f, 0x96, 0x91), (0x9a, 0xa4, 0x9d), (0xa8, 0xb3, 0xab)], "shade": (0x70, 0x75, 0x70),
        "fill": [(0x5a, 0x44, 0x24), (0x61, 0x4b, 0x2e), (0x70, 0x52, 0x2e), (0x7a, 0x5a, 0x34), (0x82, 0x61, 0x3a), (0x88, 0x65, 0x39)],
        "seam": (0x55, 0x3a, 0x1f), "planks": "upright", "rivet": (0xc4, 0xcc, 0xc6),
    },
    "brass": {
        "rim": (0x72, 0x47, 0x31), "frame": [(0x9e, 0x69, 0x47), (0xa8, 0x72, 0x4c)],
        "bevel": [(0xe4, 0xb7, 0x63), (0xf7, 0xcb, 0x6c), (0xff, 0xeb, 0x8c)], "shade": (0xce, 0xa0, 0x5a),
        "fill": [(0x44, 0x2b, 0x15), (0x49, 0x2f, 0x17), (0x4f, 0x32, 0x18), (0x53, 0x38, 0x1a), (0x5a, 0x3e, 0x1e)],
        "seam": (0x30, 0x1e, 0x0e), "planks": "across", "rivet": (0xff, 0xf4, 0xb8),
    },
    "copper": {
        "rim": (0x90, 0x49, 0x31), "frame": [(0xb2, 0x62, 0x47), (0xa7, 0x5a, 0x40)],
        "bevel": [(0xc8, 0x74, 0x56), (0xd6, 0x7b, 0x5b), (0xe3, 0x82, 0x6c)], "shade": (0xa7, 0x5a, 0x40),
        "fill": [(0x4f, 0x4f, 0x4f), (0x55, 0x54, 0x53), (0x5e, 0x5b, 0x5a), (0x67, 0x62, 0x5f), (0x72, 0x6c, 0x69)],
        "seam": (0x44, 0x41, 0x40), "planks": "upright", "rivet": (0xf0, 0xa0, 0x84),
    },
}
GLASS = [(0x9c, 0xc8, 0xd0, 120), (0xb4, 0xd8, 0xdc, 104), (0xe8, 0xf6, 0xf6, 190)]
LAMP = [(0x6a, 0x10, 0x0c), (0xc8, 0x22, 0x18), (0xff, 0x7a, 0x5a)]


def framed(c, w, h, seed):
    """A casing panel w x h: dark rim, frame, bright bevel (lit top-left, shaded bottom-right) and infill."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                col = c["frame"][rnd.random() < 0.25]
                if x == w - 1 or y == h - 1:
                    col = c["rim"]
            elif x == 1 or y == 1:
                col = rnd.choice(c["bevel"])
            elif x == w - 2 or y == h - 2:
                col = c["shade"]
            else:
                col = infill(c, x - 2, y - 2, rnd)
            px[x, y] = col + (255,) if len(col) == 3 else col
    return img


def infill(c, x, y, rnd):
    # Planks three or four wide with a dark seam between, each plank a little lighter or darker as a whole.
    k = x if c["planks"] == "upright" else y
    if k % 4 == 3:
        return c["seam"]
    plank = k // 4
    base = (plank * 2 + 1) % len(c["fill"])
    return c["fill"][max(0, min(len(c["fill"]) - 1, base + rnd.choice((-1, 0, 0, 1))))]


def rail(img, c, y0, seed):
    """A riveted cross-rail two pixels high across the panel at row y0."""
    rnd = random.Random(seed)
    px = img.load()
    w = img.size[0]
    for x in range(1, w - 1):
        px[x, y0] = rnd.choice(c["bevel"]) + (255,)
        px[x, y0 + 1] = c["frame"][0] + (255,)
    px[1, y0 + 1] = c["shade"] + (255,)
    for x in (3, w - 4):
        px[x, y0] = c["rivet"] + (255,)
        px[x, y0 + 1] = c["rim"] + (255,)


def porthole(img, c, cx, cy, r):
    """A round window: casing ring with four rivets and tinted glass you can see through, with a glint."""
    px = img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r - 1.0:
                g = GLASS[0] if (x + y) % 5 else GLASS[1]
                # Glint: a short streak across the upper-left of the glass.
                u, v = x + 0.5 - cx, y + 0.5 - cy
                if -r * 0.95 <= u + v <= -r * 0.45 and abs(u - v) < 1.6:
                    g = GLASS[2]
                px[x, y] = g
            elif d <= r + 0.15:
                lit = (x + 0.5 - cx) + (y + 0.5 - cy) < 0
                px[x, y] = (c["bevel"][1] if lit else c["frame"][0]) + (255,)
            elif d <= r + 1.05:
                px[x, y] = c["rim"] + (255,)
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        x, y = int(cx + dx * (r + 0.6) - 0.5 + (dx == 0) * 0), int(cy + dy * (r + 0.6) - 0.5)
        if 0 <= x < img.size[0] and 0 <= y < img.size[1]:
            px[x, y] = c["rivet"] + (255,)


def lamp(img, x, y):
    px = img.load()
    px[x, y], px[x + 1, y] = LAMP[2] + (255,), LAMP[1] + (255,)
    px[x, y + 1], px[x + 1, y + 1] = LAMP[1] + (255,), LAMP[0] + (255,)


def door(name, c, seed):
    img = framed(c, 16, 32, seed)
    rail(img, c, 15, seed + 1)
    rail(img, c, 25, seed + 2)
    porthole(img, c, 8.0, 7.5, 4.0)
    lamp(img, 11, 19)
    return img


def trapdoor(name, c, seed):
    img = framed(c, 16, 16, seed + 7)
    porthole(img, c, 8.0, 8.0, 4.0)
    px = img.load()
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[x, y] = c["rivet"] + (255,)
        px[x + 1, y + 1] = c["rim"] + (255,)
    return img


tex = os.path.join(out, "textures/block")
models = os.path.join(out, "models/block")
states = os.path.join(out, "blockstates")
for d in (tex, models, states):
    os.makedirs(d, exist_ok=True)

DOOR_PARTS = ["bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
              "top_left", "top_left_open", "top_right", "top_right_open"]
TRAP_PARTS = ["bottom", "top", "open"]
FACE_Y = {"east": 0, "south": 90, "west": 180, "north": 270}
TRAP_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def door_variants(name):
    # The same as vanilla's iron door blockstate: an open door turns a quarter away from its hinge.
    variants = {}
    for facing, y in FACE_Y.items():
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                for open_ in ("false", "true"):
                    part = ("bottom" if half == "lower" else "top") + "_" + hinge + ("_open" if open_ == "true" else "")
                    rot = (y + (90 if hinge == "left" else 270)) % 360 if open_ == "true" else y
                    v = {"model": f"create_galore_bridge:block/{name}_{part}"}
                    if rot:
                        v["y"] = rot
                    variants[f"facing={facing},half={half},hinge={hinge},open={open_}"] = v
    return {"variants": variants}


def trap_variants(name):
    # The same as vanilla's iron trapdoor blockstate: shut it lies flat, open it turns to the side it hinges on.
    variants = {}
    for facing, y in TRAP_Y.items():
        for half in ("bottom", "top"):
            for open_ in ("false", "true"):
                v = {"model": f"create_galore_bridge:block/{name}_{'open' if open_ == 'true' else half}"}
                if open_ == "true" and y:
                    v["y"] = y
                variants[f"facing={facing},half={half},open={open_}"] = v
    return {"variants": variants}


sheet = []
for i, (cname, c) in enumerate(CASINGS.items()):
    dname, tname = f"{cname}_encased_door", f"{cname}_encased_trapdoor"
    d = door(dname, c, 100 + i * 10)
    d.crop((0, 0, 16, 16)).save(os.path.join(tex, f"{dname}_top.png"))
    d.crop((0, 16, 16, 32)).save(os.path.join(tex, f"{dname}_bottom.png"))
    t = trapdoor(tname, c, 200 + i * 10)
    t.save(os.path.join(tex, f"{tname}.png"))
    sheet.append((d, t))
    for part in DOOR_PARTS:
        write(os.path.join(models, f"{dname}_{part}.json"), {
            "parent": f"minecraft:block/door_{part}", "render_type": "minecraft:translucent",
            "textures": {"bottom": f"create_galore_bridge:block/{dname}_bottom", "top": f"create_galore_bridge:block/{dname}_top"}})
    for part in TRAP_PARTS:
        write(os.path.join(models, f"{tname}_{part}.json"), {
            "parent": f"minecraft:block/template_trapdoor_{part}", "render_type": "minecraft:translucent",
            "textures": {"texture": f"create_galore_bridge:block/{tname}"}})
    write(os.path.join(states, f"{dname}.json"), door_variants(dname))
    write(os.path.join(states, f"{tname}.json"), trap_variants(tname))

if preview:
    s = 8
    img = Image.new("RGBA", (len(sheet) * 36 * s, 34 * s), (34, 36, 40, 255))
    for i, (d, t) in enumerate(sheet):
        img.alpha_composite(d.resize((16 * s, 32 * s), Image.NEAREST), ((i * 36 + 1) * s, s))
        img.alpha_composite(t.resize((16 * s, 16 * s), Image.NEAREST), ((i * 36 + 19) * s, s))
    img.save(preview)
