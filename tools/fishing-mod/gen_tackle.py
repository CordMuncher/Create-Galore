#!/usr/bin/env python3
"""Writes the tackle data files from the table below (one place to balance rods, reels, lines, hooks and baits).

Output: src/main/resources/data/create_galore_fishing/fishing/tackle/<namespace>/<path>.json, one per item. Files
this script made before whose entries are gone are deleted. Entries for other mods' items only load when that
mod is installed. Run after editing, then rebuild:

    python3 tools/fishing-mod/gen_tackle.py

Units: kgf for loads and drag, metres for lengths, m/s for speeds, grams for lure/float/sinker weights.
Hook sizes are indexes: 0 #12, 1 #10, 2 #8, 3 #6, 4 #4, 5 #2, 6 #1, 7 1/0, 8 2/0, 9 4/0, 10 6/0, 11 8/0.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "src/main/resources/data/create_galore_fishing/fishing/tackle"
MARK = "_generated"
M = "create_galore_fishing:"

RODS = {
    # name: length, max_load, action, sections, lure_min, lure_max, extra
    "bamboo_pole": (4.0, 4, "slow", 3, 1, 15, {"reel": False, "fixed_line": 8, "color": "#c9b36b", "grip": "#a89050"}),
    "wooden_rod": (2.1, 5, "moderate", 2, 2, 20, {"color": "#8a6a45", "grip": "#5a3e26"}),
    "iron_rod": (2.4, 9, "fast", 3, 5, 30, {"color": "#b8b8b8", "grip": "#c9a26b"}),
    "steel_rod": (3.0, 16, "fast", 5, 8, 45, {"color": "#4a5560", "grip": "#2b2b2e", "telescopic": True}),
    "boat_rod": (2.1, 45, "moderate", 2, 30, 200, {"color": "#2f3b4a", "grip": "#262626"}),
    "netherite_rod": (2.7, 30, "fast", 4, 10, 80, {"lava": True, "color": "#4a3f43", "grip": "#3a2a2e"}),
}
REELS = {
    # name: max_drag, drag_steps, retrieve, power, smooth, capacity, style, color
    "copper_reel": (3, 8, 1.0, 6, 0.3, 100, "spinning", "#c87840"),
    "iron_reel": (6, 10, 1.2, 10, 0.5, 150, "spinning", "#a8a8a8"),
    "brass_reel": (10, 12, 1.4, 16, 0.85, 200, "spinning", "#d8b040"),
    "steel_baitcaster": (14, 12, 1.6, 22, 0.75, 180, "baitcaster", "#506070"),
    "trolling_reel": (30, 16, 1.8, 45, 0.8, 600, "trolling", "#c0a040"),
}
LINES = {
    # name: test, stretch, visibility, meters, color
    "string_line": (4, 0.7, 0.7, 100, "#e8e8e8"),
    "mono_line": (8, 0.5, 0.4, 150, "#d8e8d0"),
    "clear_line": (10, 0.3, 0.1, 150, "#e0f0ff"),
    "braided_line": (18, 0.1, 0.8, 200, "#3a6a3a"),
    "heavy_line": (50, 0.45, 0.5, 600, "#f0d070"),
}
LEADERS = {
    "clear_leader": (12, 0.1, False, "#e0f0ff"),
    "steel_leader": (60, 0.6, True, "#909aa0"),
}
HOOKS = {
    # item: size, hold, luck, effect
    M + "small_hook": (1, 1.0, 0, None),
    M + "medium_hook": (4, 1.0, 0, None),
    M + "large_hook": (7, 1.0, 0, None),
    M + "big_game_hook": (10, 1.0, 0, None),
    M + "golden_hook": (4, 0.9, 1, None),
    M + "netherite_hook": (7, 1.1, 0, "lava"),
    # Hybrid Aquatic's hooks keep what they did on Tide rods.
    "hybrid_aquatic:barbed_hook": (5, 1.3, 0, "barbed"),
    "hybrid_aquatic:glowing_hook": (4, 1.0, 0, "glowing"),
    "hybrid_aquatic:magnetic_hook": (4, 1.0, 1, "magnetic"),
    "hybrid_aquatic:creepermagnet_hook": (5, 1.0, 0, "creeper_magnet"),
    "hybrid_aquatic:ominous_hook": (8, 1.0, 0, "ominous"),
    # Tide's hooks, while Tide is still installed (they become this mod's items when Tide leaves).
    "tide:fishing_hook": (4, 1.0, 0, None),
    "tide:lavaproof_hook": (6, 1.0, 0, "lava"),
    "tide:fiery_hook": (5, 1.0, 0, "fiery"),
    "tide:permafrost_hook": (5, 1.0, 0, "permafrost"),
    "tide:twilight_hook": (5, 1.0, 0, "twilight"),
    "tide:void_hook": (6, 1.0, 0, "void"),
}
TERMINALS = {
    M + "float": {"kind": "float", "weight": 4, "color": "#d03030"},
    M + "sinker": {"kind": "sinker", "weight": 15},
    M + "spoon": {"kind": "lure", "bait": "spoon", "weight": 12, "dive": 1.0, "speed_min": 0.4, "speed_max": 2.5, "size": 1, "color": "#d0d0d0"},
    M + "spinner": {"kind": "lure", "bait": "spinner", "weight": 8, "dive": 0.6, "speed_min": 0.3, "speed_max": 1.8, "size": 0, "color": "#e0b040"},
    M + "crankbait": {"kind": "lure", "bait": "crankbait", "weight": 14, "dive": 2.0, "speed_min": 0.6, "speed_max": 3.0, "size": 1, "color": "#40a050"},
    M + "deep_crankbait": {"kind": "lure", "bait": "crankbait", "weight": 20, "dive": 5.0, "speed_min": 0.8, "speed_max": 3.5, "size": 2, "color": "#305080"},
    M + "jig": {"kind": "lure", "bait": "jig", "weight": 10, "dive": 3.0, "speed_min": 0.1, "speed_max": 1.0, "size": 1, "color": "#a04040"},
    M + "popper": {"kind": "lure", "bait": "topwater", "weight": 10, "dive": 0.0, "speed_min": 0.2, "speed_max": 1.5, "size": 1, "color": "#f0f0a0"},
    M + "soft_worm": {"kind": "lure", "bait": "soft_plastic", "weight": 6, "dive": 1.0, "speed_min": 0.1, "speed_max": 1.2, "size": 0, "color": "#8050a0"},
}
BAITS = {
    # item: bait kind, size (0 small, 1 medium, 2 large)
    "aquaculture:worm": ("worm", 0),
    "aquaculture:leech": ("leech", 1),
    "aquaculture:fish_fillet_raw": ("cut_bait", 1),
    "naturalist:caterpillar": ("insect", 0),
    "naturalist:butterfly": ("insect", 0),
    "create:dough": ("dough", 0),
    "minecraft:bread": ("dough", 0),
    "minecraft:rotten_flesh": ("cut_bait", 1),
    "minecraft:spider_eye": ("insect", 0),
    "minecraft:cod": ("cut_bait", 2),
    "minecraft:salmon": ("cut_bait", 2),
    "hybrid_aquatic:raw_fish_meat": ("cut_bait", 1),
    "hybrid_aquatic:raw_shrimp": ("crustacean", 0),
    "hybrid_aquatic:raw_crayfish": ("crustacean", 1),
    "hybrid_aquatic:raw_tentacle": ("squid", 2),
    "naturalist:crab_meat": ("crustacean", 1),
    # Tide's baits while Tide is installed.
    "tide:bait": ("worm", 0),
    "tide:incandescent_bait": ("lava_bait", 1),
    "tide:abyss_bait": ("void_bait", 1),
}


def write(item, data, written):
    ns, path = item.split(":")
    target = OUT / ns / f"{path}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    out = {"item": item, MARK: "from tools/fishing-mod/gen_tackle.py; edit that, not this file"}
    out.update(data)
    target.write_text(json.dumps(out, indent=2) + "\n")
    written.add(target)


def main():
    written = set()
    for name, (length, load, action, sections, lmin, lmax, extra) in RODS.items():
        write(M + name, {"kind": "rod", "length": length, "max_load": load, "action": action, "sections": sections,
                         "lure_min": lmin, "lure_max": lmax, **extra}, written)
    for name, (drag, steps, retrieve, power, smooth, capacity, style, color) in REELS.items():
        write(M + name, {"kind": "reel", "max_drag": drag, "drag_steps": steps, "retrieve": retrieve, "power": power,
                         "smooth": smooth, "capacity": capacity, "style": style, "color": color}, written)
    for name, (test, stretch, vis, meters, color) in LINES.items():
        write(M + name, {"kind": "line", "test": test, "stretch": stretch, "visibility": vis, "meters": meters,
                         "color": color}, written)
    for name, (test, vis, steel, color) in LEADERS.items():
        write(M + name, {"kind": "leader", "test": test, "visibility": vis, "steel": steel, "color": color}, written)
    for item, (size, hold, luck, effect) in HOOKS.items():
        data = {"kind": "hook", "size": size, "hold": hold, "luck": luck}
        if effect:
            data["effect"] = effect
        write(item, data, written)
    for item, data in TERMINALS.items():
        write(item, data, written)
    for item, (kind, size) in BAITS.items():
        write(item, {"kind": "bait", "bait": kind, "size": size}, written)
    removed = 0
    for old in OUT.rglob("*.json"):
        if old not in written and MARK in json.loads(old.read_text()):
            old.unlink()
            removed += 1
    print(f"wrote {len(written)} tackle files, removed {removed} stale ones")


if __name__ == "__main__":
    main()
