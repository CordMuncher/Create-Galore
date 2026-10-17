#!/usr/bin/env python3
"""Writes a plan.tsv (from plan.py) into the pack's config files.

Each mod takes its structure spacing from a different config:
  - most mods: Cristel Lib's config/cristellib/<mod>/structure_placement_config.json5
  - vanilla: config/vanilla_structures/placement_structure_config.json5 (also Cristel Lib)
  - Towns and Towers: config/towns_and_towers/structure_rarity_new.json5
  - Aquamirae's pirate builds: config/obscuria/aquamirae-common.toml
  - Create Structures Arise: config/create_structures_arise-server.toml
A file the pack doesn't ship yet is first copied from a server that has run once (its generated defaults).
Values are scaled from what the config holds (config × planned / scanned spacing), not copied from the scan,
because some mods turn the config number into a different real spacing (Moog's structures use about 1.66×
the config). So run it once on configs that still hold the values the plan's scan was made with.

Usage: apply.py PLAN_TSV PACK_CONFIG_DIR SERVER_CONFIG_DIR
"""
import csv, os, re, shutil, sys

CRISTEL_FOLDER = {"hopo": "hopour", "igcah": "mr_create_hangars", "nova_structures": "mr_dungeons_andtaverns"}


def target(set_id):
    ns, path = set_id.split(":", 1)
    if ns == "minecraft":
        return "vanilla_structures/placement_structure_config.json5", "json5", path
    if ns == "towns_and_towers":
        return "towns_and_towers/structure_rarity_new.json5", "json5", path
    if ns == "aquamirae" and path.startswith("pirate_"):
        return "obscuria/aquamirae-common.toml", "aquamirae", path
    if ns == "create_structures_arise":
        return "create_structures_arise-server.toml", "csa", path
    return f"cristellib/{CRISTEL_FOLDER.get(ns, ns)}/structure_placement_config.json5", "json5", path


def scaled(block, sp_rx, sep_rx, k):
    """Multiplies the spacing and separation numbers in block by k (separation stays below spacing)."""
    sp = int(re.search(sp_rx, block).group(2))
    new_sp = max(2, round(sp * k))
    sep = int(re.search(sep_rx, block).group(2))
    new_sep = min(new_sp - 1, round(sep * k))
    block = re.sub(sp_rx, lambda g: g.group(1) + str(new_sp), block)
    block = re.sub(sep_rx, lambda g: g.group(1) + str(new_sep), block)
    return block, new_sp, new_sep


def set_json5(text, key, k):
    m = re.search(r'"' + re.escape(key) + r'"\s*:\s*\{', text)
    if not m:
        raise KeyError(key)
    end = text.index("}", m.end())
    block, sp, sep = scaled(text[m.end():end], r'("spacing"\s*:\s*)(\d+)', r'("separation"\s*:\s*)(\d+)', k)
    return text[:m.end()] + block + text[end:], sp, sep


def set_toml_keys(text, spacing_key, separation_key, k):
    text, sp, sep = scaled(text, r"(?m)^(\s*" + re.escape(spacing_key) + r"\s*=\s*)(\d+)",
                           r"(?m)^(\s*" + re.escape(separation_key) + r"\s*=\s*)(\d+)", k)
    return text, sp, sep


def set_csa(text, key, k):
    m = re.search(r"(?m)^\s*\[structures\." + re.escape(key) + r"\]\s*$", text)
    if not m:
        raise KeyError(key)
    nxt = re.search(r"(?m)^\s*\[", text[m.end():])
    end = m.end() + (nxt.start() if nxt else len(text) - m.end())
    block, sp, sep = scaled(text[m.end():end], r"(?m)^(\s*spacing\s*=\s*)(\d+)", r"(?m)^(\s*separation\s*=\s*)(\d+)", k)
    return text[:m.end()] + block + text[end:], sp, sep


def main():
    plan, pack_cfg, server_cfg = sys.argv[1:4]
    rows = list(csv.DictReader(open(plan, newline=""), delimiter="\t"))
    files = {}
    for r in rows:
        rel, kind, key = target(r["set"])
        path = os.path.join(pack_cfg, rel)
        if path not in files:
            if not os.path.exists(path):
                os.makedirs(os.path.dirname(path), exist_ok=True)
                shutil.copy(os.path.join(server_cfg, rel), path)
            files[path] = open(path).read()
        k = int(r["new_spacing"]) / int(r["spacing"])
        if kind == "json5":
            files[path], sp, sep = set_json5(files[path], key, k)
        elif kind == "aquamirae":
            files[path], sp, sep = set_toml_keys(files[path], key + "_placement_spacing", key + "_placement_separation", k)
        else:
            files[path], sp, sep = set_csa(files[path], key, k)
        print(f"{r['set']}: spacing x{k:.2f}, config now {sp}/{sep} ({rel})")
    for path, text in files.items():
        open(path, "w").write(text)


if __name__ == "__main__":
    main()
