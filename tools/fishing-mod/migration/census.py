#!/usr/bin/env python3
"""Count every block, block entity, entity and item id in a Minecraft 1.21 world, with no game running.

    census.py count <world folder> <out.json>
    census.py compare <before.json> <after.json> <aliases.tsv>

`count` reads region/*.mca (block palettes and block entities with everything inside them), entities/*.mca and
playerdata/*.dat (inventories and ender chests), in the overworld only, and writes id counts per kind. Items are
found wherever an item stack is stored (chests, shulker boxes inside chests, frames, displays, satchels, rod parts),
so a Tide rod's line inside its components counts too.

`compare` maps both censuses through the alias table that `/cgfish selftest aliases` writes
(create_galore_fishing/aliases.tsv in the server folder) and lists every id whose count changed: the migration
test passes when nothing is lost. Items and block entities are compared by how many were found (stacks, not stack
sizes); Tide's blocks, its fish mobs and journal entries by presence and count where it applies. Terrain and
ordinary mobs, which change as chunks load, are left out.
"""
import gzip
import io
import json
import pathlib
import struct
import sys
import zlib
from collections import Counter


# ---- NBT ----

def read_nbt(data):
    """Parse an uncompressed NBT blob into Python values (compounds as dicts, lists and arrays as lists)."""
    buf = io.BytesIO(data)

    def u(fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(">" + fmt, buf.read(size))[0]

    def string():
        return buf.read(u("H")).decode("utf-8", "replace")

    def payload(tag):
        if tag == 1: return u("b")
        if tag == 2: return u("h")
        if tag == 3: return u("i")
        if tag == 4: return u("q")
        if tag == 5: return u("f")
        if tag == 6: return u("d")
        if tag == 7: return buf.read(u("i"))
        if tag == 8: return string()
        if tag == 9:
            inner, n = u("b"), u("i")
            return [payload(inner) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                t = u("b")
                if t == 0: return out
                name = string()
                out[name] = payload(t)
        if tag == 11:
            n = u("i")
            return list(struct.unpack(f">{n}i", buf.read(4 * n)))
        if tag == 12:
            n = u("i")
            return list(struct.unpack(f">{n}q", buf.read(8 * n)))
        raise ValueError(f"bad NBT tag {tag}")

    root = u("b")
    if root != 10:
        raise ValueError("NBT root is not a compound")
    string()
    return payload(10)


def region_chunks(path):
    """Every chunk's NBT in an Anvil region file."""
    data = path.read_bytes()
    if len(data) < 8192:
        return
    for i in range(1024):
        offset = int.from_bytes(data[i * 4:i * 4 + 3], "big")
        if offset == 0:
            continue
        start = offset * 4096
        length = int.from_bytes(data[start:start + 4], "big")
        kind = data[start + 4]
        body = data[start + 5:start + 4 + length]
        if kind == 1: raw = gzip.decompress(body)
        elif kind == 2: raw = zlib.decompress(body)
        elif kind == 3: raw = body
        else: continue  # external (.mcc) chunks: not used by small test worlds
        yield read_nbt(raw)


# ---- what to count ----

def items_in(value, counts):
    """Count every item stack inside a parsed NBT value (an item stack is a compound with id and count)."""
    if isinstance(value, dict):
        if isinstance(value.get("id"), str) and "count" in value:
            counts["item " + value["id"]] += 1
        for v in value.values():
            items_in(v, counts)
    elif isinstance(value, list):
        for v in value:
            items_in(v, counts)


def entity(e, counts):
    if isinstance(e.get("id"), str):
        counts["entity " + e["id"]] += 1
    for key, v in e.items():
        if key == "Passengers":
            for p in v: entity(p, counts)
        elif key != "id":
            items_in(v, counts)


def count(world):
    world = pathlib.Path(world)
    counts = Counter()
    for mca in sorted((world / "region").glob("*.mca")):
        for chunk in region_chunks(mca):
            for section in chunk.get("sections", []):
                # Blocks are counted once per chunk section they appear in (enough to see one vanish).
                for state in section.get("block_states", {}).get("palette", []):
                    counts["block " + state["Name"]] += 1
            for be in chunk.get("block_entities", []):
                counts["block_entity " + be.get("id", "?")] += 1
                for key, v in be.items():
                    if key not in ("id", "x", "y", "z"): items_in(v, counts)
    for mca in sorted((world / "entities").glob("*.mca")):
        for chunk in region_chunks(mca):
            for e in chunk.get("Entities", []):
                entity(e, counts)
    for dat in sorted((world / "playerdata").glob("*.dat")):
        player = read_nbt(gzip.decompress(dat.read_bytes()))
        for key in ("Inventory", "EnderItems"):
            items_in(player.get(key, []), counts)
        # Tide's journal progress (the player's persistent data): every fish unlocked in it.
        journal = player.get("NeoForgeData", {}).get("TidePlayerData", {})
        for entry in journal.get("fish_data", []):
            if entry.get("data", {}).get("is_unlocked"):
                counts["journal " + entry["fish"]] += 1
    return dict(sorted(counts.items()))


def load_aliases(path):
    aliases = {}
    for line in pathlib.Path(path).read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        kind, old, new = line.split("\t")
        aliases[(kind, old)] = new
    return aliases


def compare(before, after, aliases):
    before = json.loads(pathlib.Path(before).read_text())
    after = json.loads(pathlib.Path(after).read_text())
    def mapped(census, tide_keys=None):
        out = Counter()
        for key, n in census.items():
            kind, ident = key.split(" ", 1)
            new = f"{kind} {aliases.get(('item' if kind == 'journal' else kind, ident), ident)}"
            # Blocks and journal entries are about presence (two journal entries may now name one fish).
            out[new] = max(out[new], 1) if kind in ("block", "journal") else out[new] + n
            if tide_keys is not None and ident.startswith(("tide:", "create_galore_fishing:")):
                tide_keys.add(new)
        return out

    from_tide = set()
    before, after = mapped(before, from_tide), mapped(after)
    problems = 0
    for key in sorted(set(before) | set(after)):
        kind = key.split(" ", 1)[0]
        # Terrain blocks and wandering mobs come and go as chunks load; what came from Tide (or is ours) matters.
        if kind in ("block", "entity") and key not in from_tide:
            continue
        b, a = before.get(key, 0), after.get(key, 0)
        if b == a:
            continue
        if a < b:
            problems += 1
        print(f"{'LOST' if a < b else 'more'} {key}: before {b}, after {a} (both through the aliases)")
    print(f"{problems} ids lost; {len(from_tide)} ids from Tide or this mod checked")
    return problems


def main():
    if len(sys.argv) == 4 and sys.argv[1] == "count":
        result = count(sys.argv[2])
        pathlib.Path(sys.argv[3]).write_text(json.dumps(result, indent=1) + "\n")
        print(f"{len(result)} ids, {sum(v for k, v in result.items() if k.startswith('item '))} item stacks, "
              f"{sum(v for k, v in result.items() if k.startswith('entity '))} entities")
    elif len(sys.argv) == 5 and sys.argv[1] == "compare":
        sys.exit(1 if compare(sys.argv[2], sys.argv[3], load_aliases(sys.argv[4])) else 0)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
