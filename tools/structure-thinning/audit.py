#!/usr/bin/env python3
"""Holistic check of all four layers at once: pack scans against vanilla scans of the same seeds and squares.

Each layer is judged the way its own thread judged it, so the audit and the layer threads agree:
- surface: structures standing on land, per km² of land, against 2x vanilla's surface structures on land per km²
  of land (mineshafts, the Ice Maze terrain, Alex's Caves' ocean trench and Berries and Cherries plants left out);
- underground: per km² of the square, against 3x vanilla's underground without mineshafts (mineshafts left out);
- underwater: per km² of ocean, against 3x vanilla's underwater per km² of ocean (Ice Maze spirals left out);
- sky: every sky structure, against 1x vanilla's surface count on the same square (vanilla has no sky).
Then, per layer: each kind's count with rare-by-design kinds marked (they should stay under the ordinary median),
and how even the biome families are (pooled over the seeds).

With --pool V1,V2,... the pooled pack is also judged against vanilla pooled over those (and the paired) vanilla
scans: Terralith and Tectonic give the pack different biomes from vanilla on the same seed, so the pairing is loose,
and vanilla's own surface rate swings with its biome mix from seed to seed.

Usage: audit.py [--pool VANILLA_SCAN,...] PACK_SCAN:VANILLA_SCAN [PACK_SCAN:VANILLA_SCAN ...]
"""
import csv, math, os, re, statistics, sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from biome_check import family  # noqa: E402

KEEP = {
    "surface": [r"mineshaft", r"^aquamirae:surface", r"^alexscaves:ocean_trench", r"^berries_and_cherries:"],
    # Alex's Caves' cave-biome terrain (acid pits carve the Toxic Caves, like the ocean trench carves the Abyssal Chasm).
    "underground": [r"mineshaft", r"^alexscaves:(acid_pit|dino_bowl|ferrocave|forlorn_canyon|cake_cave|donut_arch|soda_bottle|volcano)$"],
    "underwater": [r"^aquamirae:surface/(spiral|arch)$"],
    "sky": [],
}
# Rare by design (the layer threads' decisions files): these keep a lower rate on purpose.
RARE = {
    "surface": [r"^dungeons_arise:(?!small_blimp|heavenly|fishing_hut|wishing_well|jungle_tree_house|bathhouse|abandoned_temple|lighthouse)", r"^mowziesmobs:", r"^cataclysm:", r"^irons_spellbooks:",
                r"^create_ltab:(thecastle|kingscastle|grot)", r"mansion", r"monument"],
    "underground": [r"^cataclysm:", r"^irons_spellbooks:", r"^betterdungeons:(skeleton|zombie)", r"^betterstrongholds:",
                    r"^minecraft:(stronghold|ancient_city)", r"monster_room|dungeon_base", r"undead_crypt|illager_hideout",
                    r"^alexscaves:(acid_pit|abyssal_ruins)"],
    "underwater": [r"^mos:", r"^hopo:(sunken_city|fossil)", r"conduit", r"^towns_and_towers:.*wreck", r"^cr_aero_structures:.*boat",
                   r"^create_ltab:(flooded|oil)", r"monument"],
    "sky": [r"^mss:.*(arena|volcano|red_sand|large_tower|small_tower)", r"^create_sky_village:sky", r"steampunk_airship",
            r"^dungeons_arise:heavenly"],
}
TARGET = {"surface": ("surface", 2.0), "underground": ("underground", 3.0), "underwater": ("underwater", 3.0), "sky": ("surface", 1.0)}
HA = {"cold_trench", "coral_reef", "deep_coral_reef", "deep_warm_ocean", "frozen_trench", "lukewarm_trench", "red_meadow",
      "seagrass_bed", "trench", "tropical_deep_coral_reef", "warm_trench"}


def is_ocean(b):
    ns, p = b.split(":")
    return "ocean" in p or b == "aquamirae:ice_maze" or (ns == "hybrid_aquatic" and p in HA)


def tsv(p):
    return list(csv.DictReader(open(p, newline=""), delimiter="\t"))


def hit(pats, s):
    return any(re.search(p, s["structure"]) or re.search(p, s["set"]) for p in pats)


def counted(layer, s, by_land):
    if s["layer"] != layer or hit(KEEP[layer], s):
        return False
    return not by_land or not is_ocean(s["biome"])


def ci(n):
    return n - 1.96 * math.sqrt(n), n + 1.96 * math.sqrt(n)


def main():
    args = sys.argv[1:]
    pool = []
    if args and args[0] == "--pool":
        pool, args = args[1].split(","), args[2:]
    pairs = [a.split(":") for a in args]
    data = []
    for pack, van in pairs:
        pb, vb = tsv(os.path.join(pack, "biomes.tsv")), tsv(os.path.join(van, "biomes.tsv"))
        data.append(dict(name=os.path.basename(pack), ps=tsv(os.path.join(pack, "starts.tsv")), vs=tsv(os.path.join(van, "starts.tsv")),
                         pb=pb, area=sum(float(r["ground_km2"]) for r in pb),
                         ocean=sum(float(r["ground_km2"]) for r in pb if is_ocean(r["biome"])),
                         varea=sum(float(r["ground_km2"]) for r in vb),
                         vocean=sum(float(r["ground_km2"]) for r in vb if is_ocean(r["biome"]))))
    print("## Layers against Emre's targets\n")
    print("| Layer | Judged per | Seed | Pack | Pack per unit | Vanilla per unit | Target per unit | Pack ÷ target |\n|---|---|---|---:|---:|---:|---:|---:|")
    verdict = {}
    for layer in ("surface", "underground", "underwater", "sky"):
        vlayer, mult = TARGET[layer]
        by_land = layer == "surface"
        unit = {"surface": "km² land", "underground": "km²", "underwater": "km² ocean", "sky": "same square"}[layer]
        N = T = 0.0
        for d in data:
            n = sum(1 for s in d["ps"] if counted(layer, s, by_land))
            v = sum(1 for s in d["vs"] if s["layer"] == vlayer and "mineshaft" not in s["structure"]
                    and not (by_land and is_ocean(s["biome"])))
            if layer == "surface":
                pn, vn = d["area"] - d["ocean"], d["varea"] - d["vocean"]
            elif layer == "underwater":
                pn, vn = d["ocean"], d["vocean"]
            elif layer == "underground":
                pn, vn = d["area"], d["varea"]
            else:
                pn, vn = 1.0, 1.0
            t = mult * v / vn * pn
            N += n; T += t
            print(f"| {layer} | {unit} | {d['name'].split('-')[-2] if d['name'].count('-') >= 2 else d['name']} | {n} | {n / pn:.2f} | {v / vn:.2f} | {mult * v / vn:.2f} | {n / t:.2f}× |")
        lo, hi = ci(N)
        verdict[layer] = (N, T)
        print(f"| **{layer}** | | **pooled** | **{N:.0f}** | | | target {T:.0f} | **{N / T:.2f}×** (95% {lo / T:.2f}–{hi / T:.2f}) |")
    print()
    for layer, (n, t) in verdict.items():
        bar = 0.15 if layer == "sky" else 0.10
        need = 170 if layer == "sky" else 385
        ok = abs(n / t - 1) <= bar and n >= need
        print(f"- {layer}: {n:.0f} structures, {n / t - 1:+.1%} against target, bar ±{bar:.0%} with {need}+ → {'PASS' if ok else 'FAIL'}")

    if pool:
        vans = {v for _, v in pairs} | set(pool)
        vr = defaultdict(float)
        for v in vans:
            vs, vb = tsv(os.path.join(v, "starts.tsv")), tsv(os.path.join(v, "biomes.tsv"))
            a = sum(float(r["ground_km2"]) for r in vb)
            o = sum(float(r["ground_km2"]) for r in vb if is_ocean(r["biome"]))
            vr["area"] += a; vr["ocean"] += o
            vr["surface_land"] += sum(1 for x in vs if x["layer"] == "surface" and "mineshaft" not in x["structure"] and not is_ocean(x["biome"]))
            vr["surface"] += sum(1 for x in vs if x["layer"] == "surface" and "mineshaft" not in x["structure"])
            vr["underground"] += sum(1 for x in vs if x["layer"] == "underground" and "mineshaft" not in x["structure"])
            vr["underwater"] += sum(1 for x in vs if x["layer"] == "underwater")
            vr["per_seed_land"] = vr.get("per_seed_land", "") + f" {sum(1 for x in vs if x['layer'] == 'surface' and 'mineshaft' not in x['structure'] and not is_ocean(x['biome'])) / (a - o):.2f}"
        rate = {"surface": vr["surface_land"] / (vr["area"] - vr["ocean"]), "underground": vr["underground"] / vr["area"],
                "underwater": vr["underwater"] / vr["ocean"], "sky": vr["surface"] / vr["area"]}
        print(f"\n## Against vanilla pooled over {len(vans)} seeds ({vr['area']:.0f} km²)\n")
        print(f"Vanilla surface on land per km² of land, seed by seed:{vr['per_seed_land']}\n")
        print("| Layer | Pack | Pack per unit | Vanilla pooled per unit | Target per unit | Pack ÷ target |\n|---|---:|---:|---:|---:|---:|")
        for layer in ("surface", "underground", "underwater", "sky"):
            by_land = layer == "surface"
            n = sum(1 for d in data for x in d["ps"] if counted(layer, x, by_land))
            u = {"surface": sum(d["area"] - d["ocean"] for d in data), "underground": sum(d["area"] for d in data),
                 "underwater": sum(d["ocean"] for d in data), "sky": sum(d["area"] for d in data)}[layer]
            t = TARGET[layer][1] * rate[layer]
            lo, hi = ci(n)
            print(f"| {layer} | {n} | {n / u:.2f} | {rate[layer]:.2f} | {t:.2f} | **{n / u / t:.2f}×** (95% {lo / u / t:.2f}–{hi / u / t:.2f}) |")

    # Who lands where: structures of each layer by the layer most of their set lands in.
    print("\n## Spill between layers (pooled)\n")
    owner = defaultdict(Counter)
    for d in data:
        for s in d["ps"]:
            owner[s["set"]][s["layer"]] += 1
    home = {k: max(c, key=c.get) for k, c in owner.items()}
    for layer in ("surface", "underground", "underwater", "sky"):
        spill = Counter(s["set"] for d in data for s in d["ps"] if s["layer"] == layer and home[s["set"]] != layer and not hit(KEEP[layer], s))
        tot = sum(1 for d in data for s in d["ps"] if s["layer"] == layer and not hit(KEEP[layer], s))
        top = ", ".join(f"{k} {v}" for k, v in spill.most_common(5))
        print(f"- {layer}: {sum(spill.values())} of {tot} from other layers' sets ({top})")
    pp = sum(1 for d in data for s in d["ps"] if s["layer"] == "underwater" and "ruined_portal" in s["structure"])
    vp = sum(1 for d in data for s in d["vs"] if s["layer"] == "underwater" and "ruined_portal" in s["structure"])
    po, vo = sum(d["ocean"] for d in data), sum(d["vocean"] for d in data)
    print(f"- ruined portals under water: pack {pp} ({pp / po:.2f} per ocean km²), vanilla {vp} ({vp / vo:.2f} per ocean km²)")

    print("\n## Rare kinds stay rarer (pooled counts per kind, the layer's own sets only)\n")
    for layer in ("surface", "underground", "underwater", "sky"):
        c = Counter(s["structure"] for d in data for s in d["ps"] if counted(layer, s, False) and home[s["set"]] == layer)
        rare = {k for k in c if hit(RARE[layer], {"structure": k, "set": ""})}
        ordinary = sorted(v for k, v in c.items() if k not in rare)
        med = statistics.median(ordinary) if ordinary else 0
        over = [(k, v) for k, v in c.items() if k in rare and v > med]
        print(f"- {layer}: {len(c)} kinds found; ordinary median {med:g}, rare kinds' median "
              f"{statistics.median([c[k] for k in rare]) if rare else 0:g} ({len(rare)} rare kinds); "
              f"rare kinds above the ordinary median: {', '.join(f'{k} {v}' for k, v in sorted(over, key=lambda x: -x[1])) or 'none'}")
        top = c.most_common(6)
        print(f"  - most common: {', '.join(f'{k} {v}' for k, v in top)}")

    print("\n## Biome families (pooled; ratio to the layer's average over land, or over ocean for underwater)\n")
    area, carea = defaultdict(float), defaultdict(float)
    for d in data:
        for r in d["pb"]:
            area[family(r["biome"])] += float(r["ground_km2"])
            carea[family(r["biome"])] += float(r["cave_km2"])
    for layer in ("surface", "underground", "underwater", "sky"):
        cnt = Counter(family(s["biome"]) for d in data for s in d["ps"] if counted(layer, s, False))
        A = carea if layer == "underground" else area
        if layer == "underwater":  # ocean biomes one by one instead of families
            ob = defaultdict(float)
            for d in data:
                for r in d["pb"]:
                    if is_ocean(r["biome"]):
                        ob[r["biome"]] += float(r["ground_km2"])
            oc = Counter(s["biome"] for d in data for s in d["ps"] if counted(layer, s, False))
            big = [b for b in ob if ob[b] >= 0.5]
            base = sum(oc[b] for b in big) / sum(ob[b] for b in big)
            print(f"- underwater by ocean biome (average {base:.2f}/km² of ocean): "
                  + ", ".join(f"{b.split(':')[1]} {oc[b] / ob[b] / base:.1f}×" for b in sorted(big, key=lambda b: -oc[b] / ob[b])))
            continue
        else:
            fams = [f for f in A if f != "ocean" and A[f] >= 1.0] if layer == "surface" else [f for f in A if A[f] >= 1.0]
        base = sum(cnt[f] for f in fams) / sum(A[f] for f in fams)
        row = ", ".join(f"{f} {cnt[f] / A[f] / base:.1f}×" for f in sorted(fams, key=lambda f: -cnt[f] / A[f]))
        print(f"- {layer} (average {base:.2f}/km²): {row}")


if __name__ == "__main__":
    main()
