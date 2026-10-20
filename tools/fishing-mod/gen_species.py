#!/usr/bin/env python3
"""Turns species.csv into species extension files for the mod.

Each row becomes src/main/resources/data/create_galore_fishing/fishing/species/<namespace>/<path>.json; empty cells
are left out so the mod's defaults apply. Files this script made before but whose rows are gone get deleted, so the
CSV stays the single source. Run it after editing the CSV, then rebuild the mod:

    python3 tools/fishing-mod/gen_species.py
"""
import csv
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "src/main/resources/data/create_galore_fishing/fishing/species"
MARK = "_generated"  # files carrying this key were written by this script


def num(v):
    f = float(v)
    return int(f) if f.is_integer() else f


def row_to_json(row):
    data = {"fish": row["fish"], MARK: "from tools/fishing-mod/species.csv; edit that, not this file"}
    if row["typical_low"]:
        data["size"] = {"typical_low_cm": num(row["typical_low"]), "typical_high_cm": num(row["typical_high"]),
                        "record_high_cm": num(row["record"])}
    if row["a"]:
        data["length_weight"] = {"a": num(row["a"]), "b": 3}
    fight = {}
    for col, key in (("per_kg", "per_kg"), ("stamina", "stamina"), ("speed", "speed"), ("runs", "runs"),
                     ("jumps", "jumps"), ("charges", "charges")):
        if row[col]:
            fight[key] = num(row[col])
    if row["profile"]:
        fight["profile"] = row["profile"]
    if row["toothy"]:
        fight["toothy"] = row["toothy"].lower() == "true"
    if fight:
        data["fight"] = fight
    if row["feeding"]:
        data["feeding"] = row["feeding"]
    if row["wary"]:
        data["wary"] = num(row["wary"])
    return data


def main():
    lines = [l for l in (HERE / "species.csv").read_text().splitlines() if l.strip() and not l.startswith("#")]
    rows = list(csv.DictReader(lines))
    written = set()
    for row in rows:
        ns, path = row["fish"].split(":")
        if row["typical_low"] and not (row["typical_high"] and row["record"]):
            sys.exit(f"{row['fish']}: give all three sizes or none")
        target = OUT / ns / f"{path}.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(row_to_json(row), indent=2) + "\n")
        written.add(target)
    removed = 0
    for old in OUT.rglob("*.json"):
        if old not in written and MARK in json.loads(old.read_text()):
            old.unlink()
            removed += 1
    print(f"wrote {len(written)} species files, removed {removed} stale ones")


if __name__ == "__main__":
    main()
