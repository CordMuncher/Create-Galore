#!/usr/bin/env python3
"""Final check for one layer: pack scans against vanilla scans of the same seeds.

Counts this layer's structures in each pack scan, leaving out the policy's keep lines, and splits them into
the layer's own sets (those listed in the baseline scan as belonging to the layer) and other layers' sets.
For the surface and the sky only structures standing over land are judged, per km² of land, against
vanilla's land structures per km² of land on the same seeds (2× for the surface, 1× for the sky): how much
ocean a square holds swings a lot between seeds, and the pack has more ocean than vanilla. Structures out on
the sea (ships, oil rigs, ocean villages) are reported on their own, since vanilla has almost none to compare
with. Other layers are judged per km² of the whole square. Prints a 95% Poisson interval for the pooled count.

Usage: final_check.py LAYER POLICY BASELINE_PACK_SCAN PACK_SCAN:VANILLA_SCAN [PACK_SCAN:VANILLA_SCAN ...]
"""
import csv, math, os, re, sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from biome_check import family  # noqa: E402

LAYERS = ["surface", "underground", "underwater", "sky"]
TARGET = {"surface": ("surface", 2.0), "underground": ("underground", 3.0), "underwater": ("underwater", 3.0), "sky": ("surface", 1.0)}


def tsv(p):
    return list(csv.DictReader(open(p, newline=""), delimiter="\t"))


def sid(s):
    m = re.match(r"\(set of (.+)\)$", s["set"])
    return m.group(1) if m else s["set"]


def main():
    layer, pol, base = sys.argv[1:4]
    pairs = [a.split(":") for a in sys.argv[4:]]
    keep, reserve = [], 0.0
    for raw in open(pol):
        p = raw.split("#", 1)[0].split()
        if len(p) >= 2 and p[0] == "keep":
            keep.append(re.compile("^" + re.escape(p[1]).replace(r"\*", ".*") + "$"))
        if len(p) >= 3 and p[0] == "reserve":
            reserve += float(p[2])
    kept = lambda k, st: any(r.match(k) or r.match(st) for r in keep)
    # The layer's own sets: most of their structures land in it in the baseline scan.
    by = defaultdict(Counter)
    for s in tsv(os.path.join(base, "starts.tsv")):
        by[sid(s)][s["layer"]] += 1
    own = {k for k, c in by.items() if max(c, key=c.get) == layer}
    extra = {"surface": {"dungeons_arise:major_structures", "cr_aero_structures:cr_aero_structures"}}.get(layer, set())
    own |= extra
    tot = Counter()
    rows = []
    for pack, van in pairs:
        area = sum(float(r["ground_km2"]) for r in tsv(os.path.join(pack, "biomes.tsv")))
        land = sum(float(r["ground_km2"]) for r in tsv(os.path.join(pack, "biomes.tsv")) if family(r["biome"]) != "ocean")
        varea = sum(float(r["ground_km2"]) for r in tsv(os.path.join(van, "biomes.tsv")))
        vland = sum(float(r["ground_km2"]) for r in tsv(os.path.join(van, "biomes.tsv")) if family(r["biome"]) != "ocean")
        by_land = layer in ("surface", "sky")
        on_land = lambda s: family(s["biome"]) != "ocean"
        c = Counter()
        for s in tsv(os.path.join(pack, "starts.tsv")):
            if s["layer"] != layer:
                continue
            k = sid(s)
            if by_land and not on_land(s):
                c["sea"] += 0 if kept(k, s["structure"]) else 1
                continue
            c["kept" if kept(k, s["structure"]) else "own" if k in own else "other"] += 1
        vbase = TARGET[layer][0]
        v = sum(1 for s in tsv(os.path.join(van, "starts.tsv")) if s["layer"] == vbase and "mineshaft" not in s["structure"]
                and (not by_land or on_land(s)))
        mult = TARGET[layer][1]
        norm, vnorm = (land, vland) if by_land else (area, varea)
        rows.append((os.path.basename(pack), area, land, c, v, varea, vland))
        tot["own"] += c["own"]; tot["other"] += c["other"]; tot["kept"] += c["kept"]; tot["sea"] += c["sea"]; tot["sea_area"] += area - land
        tot["norm"] += norm; tot["area"] += area
        tot["target_own"] += (mult * v / vnorm - reserve * area / norm) * norm
        tot["target_all"] += mult * v / vnorm * norm
        print(f"{os.path.basename(pack)}: {area:.0f} km², land {land:.1f}; {layer}: {'on land: ' if by_land else ''}own sets {c['own']}, other layers' sets {c['other']}, kept {c['kept']}"
              f"{'; out at sea (not judged): ' + str(c['sea']) if by_land else ''}; "
              f"vanilla {vbase} {v} in {varea:.0f} km² (land {vland:.1f}) = {v / vnorm:.2f} per km² of {'land' if norm is land else 'square'}")
    n, t = tot["own"], tot["target_own"]
    lo, hi = n - 1.96 * math.sqrt(n), n + 1.96 * math.sqrt(n)
    print(f"\nPooled: own sets {n} structures against a target of {t:.0f} ({n / t:.2f}×, 95% interval {lo / t:.2f}–{hi / t:.2f}×)")
    print(f"Own sets per km²: {n / tot['area']:.2f} of the square, {n / tot['norm']:.2f} per km² of the normalising area")
    if tot["sea"]:
        print(f"Out at sea (ships, oil rigs, ocean villages; not judged): {tot['sea']}, {tot['sea'] / tot['sea_area']:.2f} per km² of ocean")
    print(f"Whole layer without kept sets (other threads' sets as they are on this server): {n + tot['other']} against {tot['target_all']:.0f} "
          f"({(n + tot['other']) / tot['target_all']:.2f}×)")


if __name__ == "__main__":
    main()
