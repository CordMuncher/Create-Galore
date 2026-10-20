#!/usr/bin/env python3
"""Writes the bait and fish-processing data from the tables below: the Create recipes for bait, the Cleaning Table's
crafting recipe and the rest, the item tags the Cleaning Table and the Live-Bait Bucket read, the weight-limited
versions of every fish cooking recipe in the pack, and fish oil's entry as Blaze Burner fuel.

    python3 tools/fishing-mod/gen_processing.py

Writes into src/main/resources/data/ and lists what it wrote in processing_files.txt, so files dropped from the
tables are removed on the next run. Edit the tables here, not the JSON.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).resolve().parent
DATA = HERE / "src/main/resources/data"
MANIFEST = HERE / "processing_files.txt"
M = "create_galore_fishing:"


def item(id_, **extra):
    return {"item": id_, **extra} if not id_.startswith("#") else {"tag": id_[1:], **extra}


def result(id_, count=1, chance=None):
    out = {"id": id_}
    if count != 1:
        out["count"] = count
    if chance is not None:
        out["chance"] = chance
    return out


def fluid(id_, amount):
    return {"type": "neoforge:single", "amount": amount, "fluid": id_}


def needs(*mods):
    """Load the file only when these mods are installed (its items come from them)."""
    return {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": m} for m in mods]} if mods else {}


def whole_fish(base):
    """The pack's weight-limited ingredient: matches `base` unless the fish is too big to use whole."""
    return {"type": M + "whole_fish", "base": base}


# ---- Create recipes: name -> (type, recipe body, mods it needs) ----
CREATE = {
    # Fan washing: dirt turns to mud and gives up worms; washing the mud again finds leeches and clay.
    "splashing/dirt": ("create:splashing", {
        "ingredients": [item("minecraft:dirt")],
        "results": [result("minecraft:mud"), result("aquaculture:worm", chance=0.2)]}, ["aquaculture"]),
    "splashing/coarse_dirt": ("create:splashing", {
        "ingredients": [item("minecraft:coarse_dirt")],
        "results": [result("minecraft:mud"), result("aquaculture:worm", chance=0.12)]}, ["aquaculture"]),
    "splashing/rooted_dirt": ("create:splashing", {
        "ingredients": [item("minecraft:rooted_dirt")],
        "results": [result("minecraft:mud"), result("aquaculture:worm", chance=0.3),
                    result("minecraft:hanging_roots", chance=0.25)]}, ["aquaculture"]),
    "splashing/mud": ("create:splashing", {
        "ingredients": [item("minecraft:mud")],
        "results": [result("minecraft:clay_ball", chance=0.5), result("aquaculture:leech", chance=0.15)]}, ["aquaculture"]),
    # Maggots: rotten flesh left in dirt.
    "mixing/maggots": ("create:mixing", {
        "ingredients": [item("minecraft:rotten_flesh"), item("minecraft:dirt")],
        "results": [result(M + "maggots", 4)]}, []),
    # Boilies: fish meal, flour and egg, boiled.
    "mixing/boilies": ("create:mixing", {
        "heat_requirement": "heated",
        "ingredients": [item(M + "fish_meal"), item("#c:flours/wheat"), item("#c:eggs"), fluid("minecraft:water", 250)],
        "results": [result(M + "boilies", 4)]}, []),
    # Chum: scraps mashed with fish meal.
    "mixing/chum": ("create:mixing", {
        "ingredients": [item(M + "fish_scraps"), item(M + "fish_scraps"), item(M + "fish_meal")],
        "results": [result(M + "chum", 2)]}, []),
    "milling/fish_meal_from_scraps": ("create:milling", {
        "ingredients": [item(M + "fish_scraps")], "processing_time": 100,
        "results": [result(M + "fish_meal"), result(M + "fish_meal", chance=0.25)]}, []),
    "milling/fish_meal_from_bones": ("create:milling", {
        "ingredients": [item("aquaculture:fish_bones")], "processing_time": 100,
        "results": [result(M + "fish_meal"), result("minecraft:bone_meal", chance=0.5)]}, ["aquaculture"]),
    # Fish oil: pressed from scraps, or from a whole small oily fish (what's left is scraps).
    "compacting/fish_oil_from_scraps": ("create:compacting", {
        "ingredients": [item(M + "fish_scraps")] * 4,
        "results": [{"amount": 100, "id": M + "fish_oil"}]}, []),
    "compacting/fish_oil_from_oily_fish": ("create:compacting", {
        "ingredients": [whole_fish(item("#" + M + "oily_fish"))],
        "results": [{"amount": 100, "id": M + "fish_oil"}, result(M + "fish_scraps")]}, []),
}

# ---- Vanilla-type recipes: name -> (recipe, mods it needs) ----
OTHER = {
    "crafting/cleaning_table": ({
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["SSS", "LAL", "L L"],
        "key": {"S": item("minecraft:smooth_stone_slab"), "L": item("#minecraft:logs"), "A": item("create:andesite_alloy")},
        "result": result(M + "cleaning_table")}, []),
    "crafting/aquarium_tank": ({
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["IGI", "G G", "IGI"],
        "key": {"I": item("#c:plates/iron"), "G": item("#c:glass_blocks/colorless")},
        "result": result(M + "aquarium_tank", 4)}, []),
    "crafting/live_bait_bucket": ({
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [item("minecraft:water_bucket"), item("minecraft:iron_bars")],
        "result": result(M + "live_bait_bucket")}, []),
    "smoking/leather_from_shark_skin": ({
        "type": "minecraft:smoking", "category": "misc", "cookingtime": 200, "experience": 0.2,
        "ingredient": item(M + "shark_skin"), "result": result("minecraft:leather")}, []),
    "campfire_cooking/leather_from_shark_skin": ({
        "type": "minecraft:campfire_cooking", "category": "misc", "cookingtime": 600, "experience": 0.2,
        "ingredient": item(M + "shark_skin"), "result": result("minecraft:leather")}, []),
}

# ---- Fish cooking recipes, made weight-limited (same ids, so they replace the originals) ----
# recipe id -> (type, ingredient, result, experience, cooking time, mod that owns it or None)
COOK_TYPES = {"smelting": "minecraft:smelting", "smoking": "minecraft:smoking", "campfire": "minecraft:campfire_cooking"}
COOKING = {}


def cooking(ids, ingredient, result_id, xp, times, mod):
    for kind, rid in ids.items():
        COOKING[rid] = (COOK_TYPES[kind], ingredient, result_id, xp, times[kind], mod)


VANILLA_TIMES = {"smelting": 200, "smoking": 100, "campfire": 600}
for fish in ("cod", "salmon"):
    cooking({"smelting": f"minecraft:cooked_{fish}", "smoking": f"minecraft:cooked_{fish}_from_smoking",
             "campfire": f"minecraft:cooked_{fish}_from_campfire_cooking"},
            item(f"minecraft:{fish}"), f"minecraft:cooked_{fish}", 0.35, VANILLA_TIMES, None)
for fish in ("lanternfish", "radgill", "tripodfish"):
    cooking({k: f"alexscaves:furnace/cooked_{fish}_{k}" for k in VANILLA_TIMES},
            item(f"alexscaves:{fish}"), f"alexscaves:cooked_{fish}", 0.15, VANILLA_TIMES, "alexscaves")
cooking({"smelting": "aquamirae:cooked_spinefish_from_smelting", "smoking": "aquamirae:cooked_spinefish_from_smoking",
         "campfire": "aquamirae:cooked_spinefish_from_campfire"},
        item("aquamirae:spinefish"), "aquamirae:cooked_spinefish", 1, {"smelting": 150, "smoking": 75, "campfire": 450},
        "aquamirae")
# Better End names its second campfire recipe "smoker"; both are campfire recipes.
COOKING["betterend:end_fish_smelting"] = ("minecraft:smelting", item("betterend:end_fish_raw"), "betterend:end_fish_cooked", 0, 200, "betterend")
COOKING["betterend:end_fish_campfire"] = ("minecraft:campfire_cooking", item("betterend:end_fish_raw"), "betterend:end_fish_cooked", 0, 600, "betterend")
COOKING["betterend:end_fish_smoker"] = ("minecraft:campfire_cooking", item("betterend:end_fish_raw"), "betterend:end_fish_cooked", 0, 100, "betterend")
for fish in ("bass", "catfish"):
    cooking({"smelting": f"naturalist:cooked_{fish}", "smoking": f"naturalist:cooked_{fish}_from_smoking",
             "campfire": f"naturalist:cooked_{fish}_from_campfire_cooking"},
            item(f"naturalist:{fish}"), f"naturalist:cooked_{fish}", 0.35, VANILLA_TIMES, "naturalist")
# Tide's (ported with this mod): cooked fish by size, from its cookable tags.
for food in ("cooked_fish", "large_cooked_fish", "small_cooked_fish"):
    for kind, suffix, xp in (("smelting", "smelting", 0.1), ("smoking", "smoking", 0.1), ("campfire", "campfire_cooking", 0.35)):
        COOKING[f"tide:{food}_{suffix}"] = (COOK_TYPES[kind], item(f"#tide:cookable/{food}"), f"tide:{food}", xp,
                                            VANILLA_TIMES[kind], None)

# ---- Item tags (create_galore_fishing:...); every entry optional so a missing mod costs nothing ----
TAGS = {
    # Small fish that live in a Live-Bait Bucket and go on a hook alive.
    "baitfish": ["aquaculture:minnow", "tide:anchovy", "tide:guppy", "hybrid_aquatic:danio", "hybrid_aquatic:tetra",
                 "hybrid_aquatic:goldfish", "aquaculture:goldfish", "aquaculture:bluegill", "aquaculture:atlantic_herring",
                 "hybrid_aquatic:herring"],
    "live_bait": ["#create_galore_fishing:baitfish", "aquaculture:leech"],
    # Fish that may carry roe at the Cleaning Table.
    "roe_fish": ["tide:sturgeon", "minecraft:salmon", "aquaculture:pink_salmon", "aquaculture:atlantic_herring",
                 "hybrid_aquatic:herring", "aquaculture:carp", "hybrid_aquatic:carp"],
    # Oily fish: more scraps at the table, and fish oil when pressed whole.
    "oily_fish": ["minecraft:salmon", "aquaculture:pink_salmon", "aquaculture:atlantic_herring", "hybrid_aquatic:herring",
                  "hybrid_aquatic:mackerel", "aquaculture:tuna", "hybrid_aquatic:tuna", "tide:anchovy",
                  "aquaculture:rainbow_trout", "aquaculture:brown_trout", "tide:brook_trout", "hybrid_aquatic:trout"],
    # Sharks give their skin (leather) at the table.
    "sharks": ["tide:bull_shark", "tide:great_white_shark", "tide:sand_tiger_shark", "tide:shadow_shark"],
}

# Fish oil burns in a Blaze Burner like Create Crafts & Additions' seed oil (Create: Liquid Fuel's data map).
LIQUID_FUEL = {M + "fish_oil": {"burnTime": 10, "superHeat": False, "amountConsumedPerTick": 2}}


def write(path, data, written):
    target = DATA / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, indent=2) + "\n")
    written.add(str(target.relative_to(HERE)))


def main():
    written = set()
    for name, (kind, body, mods) in CREATE.items():
        write(f"create_galore_fishing/recipe/{name}.json", {**needs(*mods), "type": kind, **body}, written)
    for name, (body, mods) in OTHER.items():
        write(f"create_galore_fishing/recipe/{name}.json", {**needs(*mods), **body}, written)
    for rid, (kind, ingredient, result_id, xp, time, mod) in COOKING.items():
        ns, path = rid.split(":")
        recipe = {**needs(*([mod] if mod else [])), "type": kind, "category": "food", "cookingtime": time,
                  "experience": xp, "ingredient": whole_fish(ingredient), "result": result(result_id)}
        write(f"{ns}/recipe/{path}.json", recipe, written)
    for name, entries in TAGS.items():
        values = [{"id": e, "required": False} for e in entries]
        write(f"create_galore_fishing/tags/item/{name}.json", {"replace": False, "values": values}, written)
    write("createliquidfuel/data_maps/fluid/liquid_fuel.json", {"values": LIQUID_FUEL}, written)

    removed = 0
    if MANIFEST.exists():
        for old in MANIFEST.read_text().splitlines():
            if old and not old.startswith("#") and old not in written and (HERE / old).exists():
                (HERE / old).unlink()
                removed += 1
    MANIFEST.write_text("# files written by gen_processing.py\n" + "\n".join(sorted(written)) + "\n")
    print(f"wrote {len(written)} processing files ({len(COOKING)} cooking recipes), removed {removed} stale ones")


if __name__ == "__main__":
    main()
