#!/usr/bin/env python3
"""Copy Tide's and Tide Extra Compatibility's assets and data into this mod's resources.

Re-runnable, like port_tide.py: every file it wrote last time is listed in port/ported_resources.txt and removed
first, so a new Tide version or a changed exclusion list gives a clean result.

    port_resources.py <tide.jar> <tide-extra-compatibility.jar> <tide source tree> <resources dir>

What is left out (see README.md, "Ported Tide content"):
- whatever belongs to the ids the port doesn't register (the 12 species the pack hands to Aquaculture and Hybrid
  Aquatic, Tide's tier rods and lines, the Angling Table, fish bones and fish slices), found by comparing Tide's
  registration code with ours: their models, textures, recipes, entity loot, spawns, fish files and recipe
  advancements. Tags keep their other entries. Loot tables and recipes that only mention one of them stay: the
  aliases (compat/TideAliases.java) send those ids to their replacements.
- Tide's rod, minigame and Angling Table resources, its bait and rod-accessory data (our tackle data replaces
  them), OptiFine models, and data for the Fabric-only mods and the compat Tide dropped (Stardew Fishing,
  Fishing Real, Trinkets, Visual Workbench).
"""
import json
import pathlib
import re
import sys
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
MANIFEST = HERE / "ported_resources.txt"
OURS = HERE.parent / "src/main/java/dev/creategalore/fishing/tide/registries"

SKIP_PREFIXES = [
    "assets/minecraft/optifine/",
    "assets/tide/textures/gui/fishing/",
    "assets/tide/textures/gui/sprites/fishing/",
    "assets/tide/textures/gui/sprites/bait/",
    "assets/tide/textures/gui/container/angling_table",
    "assets/tide/textures/entity/fishing_hook/",
    "data/fishingreal/",
    "data/stardew_fishing/",
    "data/trinkets/",
    "data/visualworkbench/",
    "data/tide/rod_accessories/",
    "data/tide/bait/",
]
# Single files: recipes whose result the alias targets already have recipes for.
SKIP_FILES = {
    "data/tide/recipe/bone_meal_from_fish_bone.json",
    "data/farmersdelight/recipe/cutting/fish_slice_cutting.json",
}
# Tide's fish cooking recipes: gen_processing.py writes weight-limited versions of them.
SKIP_FILES |= {f"data/tide/recipe/{food}_{how}.json" for food in ("cooked_fish", "large_cooked_fish", "small_cooked_fish")
               for how in ("smelting", "smoking", "campfire_cooking")}
KEEP_ROOTS = ("assets/", "data/")
# Names this mod changes in Tide's English lang (the other locales keep Tide's).
LANG_OVERRIDES = {
    "assets/tide/lang/en_us.json": {"block.tide.fish_display": "Trophy Mount"},
}


def registered(root):
    """(kind, path) of every id a TideItems/TideBlocks/TideFish/... source tree registers under tide."""
    out = set()
    for name, kind in [("TideItems.java", "item"), ("TideFish.java", "item"), ("TideBlocks.java", "block"),
                       ("TideEntityTypes.java", "entity"), ("TideBlockEntities.java", "block_entity")]:
        text = (pathlib.Path(root) / name).read_text(encoding="utf-8")
        out |= {(kind, m) for m in re.findall(r'register\("([a-z0-9_]+)"', text)}
        for m in re.findall(r'new Builder\("([a-z0-9_]+)"\)', text):
            out |= {("item", m), ("item", m + "_bucket"), ("item", m + "_spawn_egg"), ("entity", m)}
    return out


def removed_ids(tide_src):
    orig = registered(pathlib.Path(tide_src) / "src/main/java/com/li64/tide/registries")
    return {path for _, path in orig - registered(OURS)}


def mentions(text, ids):
    return {m for m in re.findall(r'"#?tide:([a-z0-9_/]+)"', text) if m in ids}


def own_id(path):
    """The id a model, texture, loot table, recipe or fish file is for (its file name), else None."""
    stem = pathlib.PurePosixPath(path).stem
    return stem[:-len("_eyes")] if stem.endswith("_eyes") else stem


def filter_tag(text, ids):
    tag = json.loads(text)
    def keep(v):
        ref = v if isinstance(v, str) else v.get("id", "")
        return ref.lstrip("#").removeprefix("tide:") not in ids or not ref.lstrip("#").startswith("tide:")
    tag["values"] = [v for v in tag.get("values", []) if keep(v)]
    return json.dumps(tag, indent=2) + "\n"


def merge_tag(path, content):
    ours = json.loads(path.read_text(encoding="utf-8"))
    values = ours.setdefault("values", [])
    for v in json.loads(content).get("values", []):
        if v not in values:
            values.append(v)
    path.write_text(json.dumps(ours, indent=2) + "\n", encoding="utf-8")


def all_fishing_rods(adv):
    """Tide's "Hooked on Variety": its criteria keep their names (so earned progress stays) and point at the rods
    they load as now (through the aliases); our Bamboo Pole and Boat Rod join them."""
    ours = {"stone": "wooden_rod", "iron": "iron_rod", "golden": "iron_rod", "crystal": "steel_rod",
            "diamond": "steel_rod", "netherite": "netherite_rod", "bamboo": "bamboo_pole", "boat": "boat_rod"}
    for name, rod in ours.items():
        adv["criteria"][name] = {"conditions": {"items": [{"items": "create_galore_fishing:" + rod}]},
                                 "trigger": "minecraft:inventory_changed"}
    adv["requirements"] = [[name] for name in adv["criteria"]]
    return adv


JSON_PATCHES = {"data/tide/advancement/all_fishing_rods.json": all_fishing_rods}


def decide(path, data, ids):
    """(action, content): action is 'copy', 'skip' or 'edit' with new content."""
    if path in SKIP_FILES or any(path.startswith(p) for p in SKIP_PREFIXES):
        return "skip", None
    if path in JSON_PATCHES:
        patched = JSON_PATCHES[path](json.loads(data.decode("utf-8")))
        return "edit", (json.dumps(patched, indent=2) + "\n").encode("utf-8")
    if path in LANG_OVERRIDES:
        lang = json.loads(data.decode("utf-8"))
        lang.update(LANG_OVERRIDES[path])
        return "edit", (json.dumps(lang, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
    if path.startswith(("assets/tide/models/", "assets/tide/textures/", "assets/tide/blockstates/")):
        # A removed item's own files, and its variants (stone_fishing_rod_cast, angling_table_top, carp_eyes).
        stem = own_id(path)
        if any(stem == i or stem.startswith(i + "_") for i in ids):
            return "skip", None
        return "copy", data
    if not path.endswith(".json") or not path.startswith("data/"):
        return "copy", data
    if own_id(path) in ids and "/loot_table/blocks/" in path:
        return "skip", None
    text = data.decode("utf-8")
    hits = mentions(text, ids)
    if not hits:
        return "copy", data
    if "/tags/" in path:
        return "edit", filter_tag(text, ids).encode("utf-8")
    body = json.loads(text)
    if "/fishing/fish/" in path and body.get("fish", "").removeprefix("tide:") in ids:
        return "skip", None
    if "/loot_table/entities/" in path or "/biome_modifier/" in path:
        return "skip", None
    if "/recipe/" in path or "/advancement/recipes/" in path:
        result = body.get("result", {})
        result = result if isinstance(result, str) else result.get("id", result.get("item", ""))
        if result.removeprefix("tide:") in ids or "/advancement/recipes/" in path and \
                any(h in path for h in hits):
            return "skip", None
    return "copy", data


def manifest_header(dest):
    try:
        where = dest.resolve().relative_to(HERE.parent)
    except ValueError:
        where = dest.resolve()
    return f"# files copied into {where.as_posix()}"


def main():
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    tide_jar, tec_jar, tide_src, dest = sys.argv[1:]
    dest = pathlib.Path(dest)
    ids = removed_ids(tide_src)
    # The manifest only applies to the folder it was written for (a trial run elsewhere must not delete our files).
    lines = MANIFEST.read_text().splitlines() if MANIFEST.exists() else []
    old = set(lines[1:]) if lines and lines[0] == manifest_header(dest) else set()
    for line in old:
        (dest / line).unlink(missing_ok=True)
    written, skipped, edited, merged = {}, [], [], []
    for jar in [tide_jar, tec_jar]:
        with zipfile.ZipFile(jar) as z:
            for info in z.infolist():
                name = info.filename
                if info.is_dir() or not name.startswith(KEEP_ROOTS):
                    continue
                action, content = decide(name, z.read(name), ids)
                if action == "skip":
                    skipped.append(name)
                    continue
                if action == "edit":
                    edited.append(name)
                if name in written and written[name] != jar:
                    print(f"note: {name} from {pathlib.Path(jar).name} replaces {pathlib.Path(written[name]).name}'s")
                out = dest / name
                if out.exists() and name not in written and "/tags/" in name:
                    # A tag this mod has its own file for: add Tide's entries to ours (idempotent), keep ours.
                    merge_tag(out, content)
                    merged.append(name)
                    continue
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_bytes(content)
                written[name] = jar
    MANIFEST.write_text("\n".join([manifest_header(dest)] + sorted(written)) + "\n")
    (HERE / "skipped_resources.txt").write_text("\n".join(sorted(skipped)) + "\n")
    print(f"removed ids: {len(ids)}; copied {len(written)} files ({len(edited)} edited), skipped {len(skipped)}, "
          f"merged into our tags: {', '.join(merged) or 'none'}")


if __name__ == "__main__":
    main()
