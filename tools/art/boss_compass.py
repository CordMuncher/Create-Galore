"""Draws the boss compass casings (16x16 overlays) and writes their item models for the bridge mod.

Each compass is vanilla's compass with a coloured casing laid over it as a second layer, so the needle (vanilla's
compass_00..31 frames, picked by the "angle" property) still turns. Usage, from this folder:
    python3 boss_compass.py ../bridge-mod/src/main/resources/assets/create_galore_bridge
"""
import json, math, os, sys
from PIL import Image

out = sys.argv[1]
# The casing of vanilla's compass (the pixels that never change between needle frames), # = casing.
MASK = """
................
................
.....######.....
...##########...
..############..
.####.......###.
.###.........##.
.##.............
.###........###.
.####......####.
.##############.
..############..
...##########...
.....######.....
................
................
""".split()
# Row 7 keeps its two right-hand casing pixels (the left side is the needle's resting gap).
MASK[7] = ".##..........##."

# Per boss: casing base and highlight colours, a casing pattern, and decorations drawn in the free space around it
# (x, y, colour). Everything sits outside the needle window, so vanilla's needle still shows and turns.
GOLD, GOLD_D, BONE, IVORY, IVORY_D = (255, 214, 90), (196, 146, 40), (232, 226, 206), (240, 230, 200), (176, 160, 128)
COMPASSES = {
    # Naga: emerald scales, a serpent head on top with yellow eyes, its tail curling round the bottom.
    "naga_compass": {
        "base": (30, 104, 52), "hi": (120, 206, 110), "pattern": "scales",
        "deco": [(6, 0, (46, 140, 64)), (7, 0, (70, 170, 80)), (8, 0, (70, 170, 80)), (9, 0, (46, 140, 64)),
                 (5, 1, (34, 112, 52)), (6, 1, (250, 226, 60)), (7, 1, (60, 156, 72)), (8, 1, (60, 156, 72)),
                 (9, 1, (250, 226, 60)), (10, 1, (34, 112, 52)),
                 (8, 14, (46, 140, 64)), (9, 14, (34, 112, 52)), (10, 14, (46, 140, 64)), (10, 13, (70, 170, 80)),
                 (11, 13, (34, 112, 52)), (6, 14, (34, 112, 52)), (5, 15, (46, 140, 64))],
    },
    # Lich: deep purple robe, a gold crown with a gem, bone-white hour marks.
    "lich_compass": {
        "base": (70, 36, 112), "hi": (168, 118, 220), "pattern": "plain",
        "deco": [(5, 0, GOLD), (7, 0, GOLD), (8, 0, GOLD), (10, 0, GOLD),
                 (5, 1, GOLD_D), (6, 1, GOLD), (7, 1, (120, 220, 255)), (8, 1, (80, 170, 230)), (9, 1, GOLD), (10, 1, GOLD_D),
                 (7, 2, GOLD_D), (8, 2, GOLD_D),
                 (1, 7, BONE), (14, 7, BONE), (7, 13, BONE), (8, 13, BONE), (7, 12, (60, 60, 60)), (8, 12, BONE)],
    },
    # Minoshroom: mossy brown hide, ivory bull horns, red mushroom caps with white spots.
    "minoshroom_compass": {
        "base": (88, 60, 40), "hi": (170, 128, 92), "pattern": "plain",
        "deco": [(2, 3, IVORY_D), (2, 2, IVORY), (1, 1, IVORY), (1, 0, IVORY_D), (3, 2, IVORY_D), (2, 1, IVORY_D),
                 (3, 1, IVORY), (4, 2, IVORY_D), (0, 0, IVORY_D),
                 (13, 3, IVORY_D), (13, 2, IVORY), (14, 1, IVORY), (14, 0, IVORY_D), (12, 2, IVORY_D), (13, 1, IVORY_D),
                 (12, 1, IVORY), (11, 2, IVORY_D), (15, 0, IVORY_D),
                 (3, 10, (206, 36, 36)), (4, 10, (230, 60, 50)), (4, 11, (250, 250, 250)),
                 (11, 11, (206, 36, 36)), (12, 10, (230, 60, 50)), (11, 10, (250, 250, 250)),
                 (7, 13, (206, 36, 36)), (8, 13, (230, 60, 50)), (8, 12, (250, 250, 250)), (7, 12, (206, 36, 36))],
    },
    # Hydra: scorched red casing glowing orange, flames licking up over the top.
    "hydra_compass": {
        "base": (110, 24, 12), "hi": (250, 120, 30), "pattern": "embers",
        "deco": [(4, 2, (240, 90, 20)), (4, 1, (255, 170, 40)), (6, 1, (240, 90, 20)), (6, 0, (255, 220, 90)),
                 (7, 1, (255, 170, 40)), (8, 0, (255, 236, 140)), (8, 1, (255, 200, 60)), (9, 1, (240, 90, 20)),
                 (10, 0, (255, 170, 40)), (11, 1, (255, 200, 60)), (11, 2, (240, 90, 20)), (12, 3, (255, 170, 40)),
                 (3, 3, (255, 200, 60)),
                 (7, 13, (255, 236, 120)), (8, 13, (255, 200, 60)), (7, 12, (255, 200, 60)), (8, 12, (240, 120, 30))],
    },
}


def shade(spec, x, y):
    base, hi = spec["base"], spec["hi"]
    # Light from the top left, like vanilla's casing; darker rim on the outer edge.
    d = math.hypot(x - 7.5, y - 7.5)
    light = 0.5 + 0.5 * ((7.5 - x) + (7.5 - y)) / 10.6
    outer = d > 6.3
    t = max(0.0, min(1.0, light * (0.55 if outer else 1.0)))
    if spec["pattern"] == "scales" and (x + 2 * y) % 4 == 0:
        t = min(1.0, t + 0.35)  # a bright scale edge every few pixels
    if spec["pattern"] == "embers" and (x * 7 + y * 3) % 5 == 0 and not outer:
        t = 1.0  # glowing cracks
    c = tuple(int(b + (h - b) * t) for b, h in zip(base, hi))
    if outer and (x + y) % 2 == 0:
        c = tuple(int(v * 0.8) for v in c)
    return c + (255,)


for name, spec in COMPASSES.items():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(MASK):
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = shade(spec, x, y)
    for x, y, c in spec["deco"]:
        px[x, y] = c + (255,)
    tex = os.path.join(out, "textures/item", name + ".png")
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    img.save(tex)

    models = os.path.join(out, "models/item")
    os.makedirs(models, exist_ok=True)

    def frame(n):
        return {"parent": "minecraft:item/generated",
                "textures": {"layer0": "minecraft:item/compass_%02d" % n, "layer1": "create_galore_bridge:item/" + name}}

    # Same thresholds as vanilla's compass.json: frame 16 at 0, then 17..31, 0..15, and back to 16.
    overrides = [{"predicate": {"angle": 0.0}, "model": "create_galore_bridge:item/" + name}]
    for i in range(1, 32):
        n = (16 + i) % 32
        overrides.append({"predicate": {"angle": (2 * i - 1) / 64}, "model": "create_galore_bridge:item/%s_%02d" % (name, n)})
        with open(os.path.join(models, "%s_%02d.json" % (name, n)), "w") as f:
            json.dump(frame(n), f, indent=2)
            f.write("\n")
    overrides.append({"predicate": {"angle": 63 / 64}, "model": "create_galore_bridge:item/" + name})
    main = frame(16)
    main["overrides"] = overrides
    with open(os.path.join(models, name + ".json"), "w") as f:
        json.dump(main, f, indent=2)
        f.write("\n")
print("wrote", ", ".join(COMPASSES))
