#!/usr/bin/env python3
"""How even one layer's structures are across biome families, now and after a plan.

Groups the ground biomes into families (ocean, forest, plains, snowy, mountains...) by name, then compares
each family's structures per km² with the layer's average, for the scan as it is and with plan.tsv's
density factors applied. Prints which sets crowd a family the most.

Several scans can be pooled: SCAN_DIR may be a comma-separated list. For the surface the ratio is also given
against the land average (oceans left out), since surface structures stand on land.

Usage: biome_check.py SCAN_DIR[,SCAN_DIR...] PLAN_TSV|- LAYER [POLICY]   (POLICY: its keep lines are left out)
"""
import csv, os, re, sys
from collections import defaultdict

# First match wins.
FAMILIES = [
    ("ocean", r"ocean|trench|reef|mirage_isles"),
    ("beach", r"beach|shore|tide_pools"),
    ("river", r"river"),
    ("swamp", r"swamp|marsh"),
    ("mushroom", r"mushroom"),
    ("jungle", r"jungle|bamboo|tropical"),
    ("desert/badlands", r"desert|badlands|mesa"),
    ("savanna/shrubland", r"savanna|shrub|steppe|brushland"),
    ("peaks/cliffs", r"peaks|cliffs|mountains|highlands|slopes|caldera|volcanic|yellowstone|canyon|basalt|windswept_gravelly"),
    ("snowy", r"snowy|frozen|ice|wintry|siberian|alpine|grove$|^minecraft:grove"),
    ("forest/taiga", r"forest|taiga|grove|rainforest|windswept"),
    ("plains/meadow", r"plains|meadow|valley|clearing|shield|lowlands"),
    ("sky islands", r"skylands"),
]


def family(b):
    for name, rx in FAMILIES:
        if re.search(rx, b):
            return name
    return "other"


def tsv(p):
    return list(csv.DictReader(open(p, newline=""), delimiter="\t"))


def main():
    scan, plan, layer = sys.argv[1:4]
    keep = []
    if len(sys.argv) > 4:
        for raw in open(sys.argv[4]):
            p = raw.split("#", 1)[0].split()
            if len(p) >= 2 and p[0] == "keep":
                keep.append(re.compile("^" + re.escape(p[1]).replace(r"\*", ".*") + "$"))
    factor = {r["set"]: float(r["factor"]) for r in tsv(plan)} if plan != "-" else {}
    area = defaultdict(float)
    for d in scan.split(","):
        for r in tsv(os.path.join(d, "biomes.tsv")):
            area[family(r["biome"])] += float(r["ground_km2"])
    now, planned = defaultdict(float), defaultdict(float)
    by_set = defaultdict(lambda: defaultdict(float))
    for s in (s for d in scan.split(",") for s in tsv(os.path.join(d, "starts.tsv"))):
        if s["layer"] != layer:
            continue
        sid = s["set"]
        if sid.startswith("("):
            sid = "create_structures_arise:" + s["structure"].split(":", 1)[1]
        if any(k.match(sid) or k.match(s["structure"]) for k in keep):
            continue
        fam = family(s["biome"])
        f = factor.get(sid, 1.0)
        now[fam] += 1
        planned[fam] += f
        by_set[fam][sid] += f
    tot_area = sum(area.values())
    avg_now, avg_plan = sum(now.values()) / tot_area, sum(planned.values()) / tot_area
    land_area = tot_area - area["ocean"]
    land_plan = (sum(planned.values()) - planned["ocean"]) / land_area
    print(f"{layer}: average now {avg_now:.2f} per km², planned {avg_plan:.2f}; land (no ocean) planned {land_plan:.2f}\n")
    print(f"| Family | km² | Now per km² | Ratio | Planned per km² | Ratio | Ratio to land | Biggest planned sets |\n|---|---:|---:|---:|---:|---:|---:|---|")
    for fam in sorted(area, key=lambda f: -(planned[f] / area[f] if area[f] else 0)):
        a = area[fam]
        if a < 0.5:
            continue
        top = sorted(by_set[fam].items(), key=lambda kv: -kv[1])[:4]
        tops = ", ".join(f"{k.split(':')[1]} {v / a:.2f}" for k, v in top)
        print(f"| {fam} | {a:.1f} | {now[fam] / a:.2f} | {now[fam] / a / avg_now:.1f}× | {planned[fam] / a:.2f} | {planned[fam] / a / avg_plan:.1f}× | {planned[fam] / a / land_plan:.1f}× | {tops} |")


if __name__ == "__main__":
    main()
