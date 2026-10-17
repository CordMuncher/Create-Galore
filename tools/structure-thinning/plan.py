#!/usr/bin/env python3
"""Works out how far to thin each structure set of one layer so the pack hits Emre's density target.

Reads a plain pack scan and a vanilla scan (Structure Scan report folders, see tools/structscan-mod) and a
policy file, and writes plan.tsv (new spacing per set) and plan.md (the numbers, for people).

Targets (Emre, 2026-10-17), per km², against vanilla without mineshafts:
  surface 2x vanilla surface, underground 3x vanilla underground, underwater 3x vanilla underwater,
  sky 1x vanilla surface.
A set belongs to the layer most of its structures land in; only sets of the chosen layer are planned (one
thread per layer). Every structure kind of the layer gets an equal share of the target, so a mod with more
kinds gets more structures. A kind already rarer than its share keeps its rate, and what it leaves unused is
shared among the others ("water filling"). Spacing is per structure set, so a set with several kinds is
thinned by the average of what its kinds need. Density goes with 1 / spacing², so spacing grows by
1 / sqrt(factor); separation grows by the same amount.

Policy lines (first match on the set or one of its structures wins):
  keep <pattern>            left at its own rate and not counted toward the target
  own <pattern>             this set belongs to this layer even if most of its structures land in another
  scale <pattern> <factor>  this density factor instead of the planned one
  weight <pattern> <w>      this structure counts as w kinds when sharing out the target (biome balance)
  rare <pattern>            a structure meant to be rare (boss lair, big loot): thinned in proportion with the whole
                            layer (its count × target ÷ the layer's count now) and kept at most at half the share
                            of an ordinary kind, so it stays rarer than the rest (Emre, 2026-10-17)
  normalise land            judge the layer per km² of land (oceans left out, see biome_check.py) instead of per
                            km² of square: the pack has more ocean than vanilla, and how much ocean a square holds
                            swings a lot between seeds (needs biomes.tsv in both scans)
  reserve <x> <n>           leave n per km² of the target for this layer's structures from other layers' sets
                            (x is ignored, any word)
  level <layer> <n>         share per kind (structures in the scan) that another layer's thread uses: kinds of
                            that layer inside this layer's sets are thinned to it (sets shared between layers)

Usage: plan.py PACK_SCAN VANILLA_SCAN POLICY LAYER OUT_DIR [CSA_TOML]
CSA_TOML (only for scans made before the scan named Create Structures Arise's sets "(set of ...)"): that mod's
server config, so each of its structures is planned on its own with the spacing from that file.
"""
import csv, math, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from collections import defaultdict

LAYERS = ["surface", "underground", "underwater", "sky"]
TARGET = {"surface": ("surface", 2.0), "underground": ("underground", 3.0), "underwater": ("underwater", 3.0), "sky": ("surface", 1.0)}


def tsv(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def props(path):
    out = {}
    for line in open(path):
        if "=" in line and not line.startswith("#"):
            k, v = line.strip().split("=", 1)
            out[k] = v
    return out


def policy(path):
    rules = []
    for raw in open(path):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        p = line.split()
        rx = re.compile("^" + re.escape(p[1]).replace(r"\*", ".*") + "$")
        why = raw.split("#", 1)[1].strip() if "#" in raw else ""
        rules.append((p[0], rx, float(p[2]) if len(p) > 2 else 1.0, why))
    return rules


def match(rules, kinds, set_id, structures):
    for kind, rx, f, why in rules:
        if kind in kinds and (rx.match(set_id) or any(rx.match(s) for s in structures)):
            return kind, f, why
    return None, 1.0, ""


def csa_spacing(path):
    """{structure path: (spacing, separation)} from Create Structures Arise's server toml."""
    out, cur = {}, None
    for line in open(path):
        m = re.match(r"\s*\[structures\.([a-z0-9_]+)\]", line)
        if m:
            cur = m.group(1)
            out[cur] = [0, 0]
            continue
        m = re.match(r"\s*(spacing|separation)\s*=\s*(\d+)", line)
        if m and cur:
            out[cur][0 if m.group(1) == "spacing" else 1] = int(m.group(2))
    return {k: tuple(v) for k, v in out.items()}


def main():
    pack, van, pol, layer, out = sys.argv[1:6]
    csa = csa_spacing(sys.argv[6]) if len(sys.argv) > 6 else {}
    rules = policy(pol)
    fixed = {r[1].pattern.strip("^$"): r[2] for r in rules if r[0] == "level"}
    area = float(props(os.path.join(pack, "scan.properties"))["area_km2"])
    varea = float(props(os.path.join(van, "scan.properties"))["area_km2"])

    vcount = defaultdict(float)
    for r in tsv(os.path.join(van, "structures.tsv")):
        if "mineshaft" not in r["structure"]:
            for l in LAYERS:
                vcount[l] += int(r[l])
    vdens = {l: vcount[l] / varea for l in LAYERS}
    target_km2 = vdens[TARGET[layer][0]] * TARGET[layer][1]
    per_land = any(r[0] == "normalise" for r in policy(pol))
    norm, vnorm = area, varea
    if per_land:
        from biome_check import family
        land = lambda d: sum(float(r["ground_km2"]) for r in tsv(os.path.join(d, "biomes.tsv")) if family(r["biome"]) != "ocean")
        norm, vnorm = land(pack), land(van)
        target_km2 = vcount[TARGET[layer][0]] / vnorm * TARGET[layer][1]

    # Sets: name -> {spacing, separation, structures: [structure rows]}. CSA's unnamed sets split per structure.
    def set_id(sid):
        # Create Structures Arise's sets have no registry name; the scan calls them "(set of <its structure>)".
        m = re.match(r"\(set of (.+)\)$", sid)
        return m.group(1) if m else sid

    sets = {}
    for r in tsv(os.path.join(pack, "sets.tsv")):
        r["set"] = set_id(r["set"])
        if not r["set"].startswith("("):
            sets[r["set"]] = {"type": r["type"], "spacing": int(r["spacing"]), "separation": int(r["separation"]),
                              "names": [n.split("×")[0] for n in r["structures"].split()], "rows": []}
    for s in tsv(os.path.join(pack, "structures.tsv")):
        sid = s["set"] = set_id(s["set"])
        if sid.startswith("("):
            path = s["structure"].split(":", 1)[1]
            sp = csa.get(path, (-1, -1))
            sid = "create_structures_arise:" + path
            sets.setdefault(sid, {"type": "create_structures_arise (own config)", "spacing": sp[0], "separation": sp[1],
                                  "names": [s["structure"]], "rows": []})
        if sid in sets:
            sets[sid]["rows"].append(s)

    for sid, st in sets.items():
        tot = defaultdict(int)
        for s in st["rows"]:
            for l in LAYERS:
                tot[l] += int(s[l])
        st["by_layer"] = tot
        st["placed"] = sum(tot.values())
        st["layer"] = max(LAYERS, key=lambda l: tot[l]) if st["placed"] else None
        st["policy"] = match(rules, ("keep", "scale"), sid, st["names"])

    owned = [r[1] for r in rules if r[0] == "own"]
    mine = {sid: st for sid, st in sets.items() if st["layer"] == layer or any(rx.match(sid) for rx in owned)}
    counted = {sid: st for sid, st in mine.items() if st["policy"][0] != "keep"}
    reserve = sum(r[2] for r in rules if r[0] == "reserve")
    target = (target_km2 - reserve) * norm

    def rare(s):
        return match(rules, ("rare",), s["set"], [s["structure"]])[0] == "rare"

    now_counted_layer = sum(st["by_layer"][layer] for st in counted.values())
    g = target / now_counted_layer if now_counted_layer else 1.0

    def weight(s):
        k, w, _ = match(rules, ("weight",), s["set"], [s["structure"]])
        return w if k else 1.0

    def factors(level):
        f = {}
        for sid, st in counted.items():
            kind, forced, _ = st["policy"]
            if kind == "scale":
                f[sid] = forced
                continue
            # A kind whose main layer is another one gets that layer's share (if known), else stays as it is.
            have = sum(int(s["count"]) for s in st["rows"])
            want = sum((min(int(s["count"]) * g, 0.5 * level * weight(s)) if rare(s) else min(int(s["count"]), level * weight(s)))
                       if s["main_layer"] == layer
                       else min(int(s["count"]), fixed.get(s["main_layer"], float("inf"))) for s in st["rows"])
            f[sid] = min(1.0, want / have) if have else 1.0
        return f

    def total(f):
        return sum(st["by_layer"][layer] * f[sid] for sid, st in counted.items())

    lo, hi = 0.0, float(max([st["placed"] for st in counted.values()] + [1]))
    for _ in range(80):
        mid = (lo + hi) / 2
        if total(factors(mid)) > target:
            hi = mid
        else:
            lo = mid
    level = lo
    f = factors(level)

    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "plan.tsv"), "w") as w:
        w.write("set\ttype\tfactor\tspacing\tseparation\tnew_spacing\tnew_separation\tplaced_" + layer + "\tplanned_" + layer + "\n")
        for sid in sorted(counted):
            st = counted[sid]
            if f[sid] >= 0.97 or st["spacing"] <= 0:
                continue
            k = 1 / math.sqrt(f[sid])
            sp = max(2, round(st["spacing"] * k))
            sep = min(sp - 1, round(st["separation"] * k))
            w.write(f"{sid}\t{st['type']}\t{f[sid]:.4f}\t{st['spacing']}\t{st['separation']}\t{sp}\t{sep}\t"
                    f"{st['by_layer'][layer]}\t{st['by_layer'][layer] * (st['spacing'] / sp) ** 2:.1f}\n")

    now_all = sum(st["by_layer"][layer] for st in mine.values())
    now_counted = sum(st["by_layer"][layer] for st in counted.values())
    with open(os.path.join(out, "plan.md"), "w") as w:
        w.write(f"# Thinning plan: {layer}\n\nPack scan `{os.path.basename(pack.rstrip('/'))}` ({area:.0f} km²), vanilla `{os.path.basename(van.rstrip('/'))}` "
                f"(vanilla {TARGET[layer][0]} without mineshafts: {vdens[TARGET[layer][0]]:.2f} per km²).\n\n")
        if per_land:
            w.write(f"- Judged per km² of land: {norm:.1f} km² of land in the pack scan, {vnorm:.1f} in the vanilla one\n")
        w.write(f"- Target: {target_km2:.2f} per km² ({TARGET[layer][1]:g}× vanilla {TARGET[layer][0]}), "
                f"{reserve:.2f} of it left for other layers' sets, so {target_km2 - reserve:.2f} for this layer's sets\n")
        w.write(f"- This layer's sets now: {now_all / norm:.2f} per km²{' of land' if per_land else ''}, of which counted (not kept): {now_counted / norm:.2f}\n")
        w.write(f"- Planned (counted sets): {total(f) / norm:.2f} per km²{' of land' if per_land else ''}; share per kind: {level:.1f} in the scan\n")
        w.write(f"- Rare kinds are thinned to {g:.3f}× their count, at most {0.5 * level:.1f} in the scan\n")
        w.write(f"- Kinds found in counted sets: {sum(1 for st in counted.values() for s in st['rows'] if int(s[layer]) > 0)}\n\n")
        w.write("Kept (own rate, not counted):\n\n")
        for sid in sorted(mine):
            if mine[sid]["policy"][0] == "keep":
                w.write(f"- `{sid}` ({mine[sid]['by_layer'][layer]} in the scan): {mine[sid]['policy'][2]}\n")
        w.write(f"\n| Set | {layer.capitalize()} now | Kinds | Factor | Spacing now → planned |\n|---|---:|---:|---:|---|\n")
        for sid in sorted(counted, key=lambda s: -counted[s]["by_layer"][layer]):
            st = counted[sid]
            k = 1 / math.sqrt(f[sid]) if f[sid] > 0 else 0
            sp = st["spacing"]
            new = (max(2, round(sp * k)) if f[sid] < 0.97 else sp) if sp > 0 else "-"
            w.write(f"| {sid} | {st['by_layer'][layer]} | {len(st['rows'])} | {f[sid]:.3f} | {sp} → {new} |\n")
    print(open(os.path.join(out, "plan.md")).read()[:2500])


if __name__ == "__main__":
    main()
