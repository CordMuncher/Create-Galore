#!/usr/bin/env python3
"""Rebuild the Elytra switch from the mod jars:
  config/paxi/datapacks/create-galore-no-elytra     (always shipped, takes Elytra out)
  config/paxi/optional/create-galore-with-elytra    (opt-in: moved into datapacks/, it loads after
                                                     no-elytra and puts every original file back)
Usage: python3 tools/build_no_elytra.py <folder with the pack's mod jars> <minecraft 1.21.1 client.jar>
Needs nbtlib (pip install nbtlib). Run it again after updating Lootr, BetterEnd, Dungeons and Taverns,
Incendium or Create Jetpack, then boot-test. The README explains the switch."""
import io, json, os, re, sys, glob, zipfile, gzip
import nbtlib

JARS, CLIENT = sys.argv[1], sys.argv[2]
PAXI = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'pack/config/paxi')
OUT = os.path.join(PAXI, 'datapacks/create-galore-no-elytra')
BACK = os.path.join(PAXI, 'optional/create-galore-with-elytra')

def jar(pattern):
    hits = glob.glob(os.path.join(JARS, pattern))
    if len(hits) != 1: sys.exit(f'expected one jar for {pattern}, found {hits}')
    return zipfile.ZipFile(hits[0])

def write(path, data, root=None):
    p = os.path.join(root or OUT, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'wb' if isinstance(data, bytes) else 'w') as f: f.write(data)

def write_json(path, obj, root=None): write(path, json.dumps(obj, indent=2) + '\n', root)

def keep(z, path):
    """Put the mod's (or vanilla's) original file into the with-Elytra pack."""
    write(path, z.read(path), BACK)

SHELL = {"type": "minecraft:item", "name": "minecraft:shulker_shell",
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]}

def swap_elytra(table):
    """Replace every Elytra entry in a loot table with 1-2 Shulker Shells (same weight)."""
    n = 0
    for pool in table['pools']:
        for i, e in enumerate(pool['entries']):
            if e.get('name') == 'minecraft:elytra':
                pool['entries'][i] = dict(SHELL, **({'weight': e['weight']} if 'weight' in e else {})); n += 1
    return n

# 1. End City ships: drop the "Elytra" data marker, so neither vanilla nor Lootr spawns the Elytra item frame.
keep(zipfile.ZipFile(CLIENT), 'data/minecraft/structure/end_city/ship.nbt')
f = nbtlib.File.parse(io.BytesIO(gzip.decompress(zipfile.ZipFile(CLIENT).read('data/minecraft/structure/end_city/ship.nbt'))))
pal = f['palette']
air = next((i for i, s in enumerate(pal) if s['Name'] == 'minecraft:air'), None)
if air is None: pal.append(nbtlib.Compound({'Name': nbtlib.String('minecraft:air')})); air = len(pal) - 1
marks = [b for b in f['blocks'] if 'nbt' in b and b['nbt'].get('metadata') == 'Elytra']
assert len(marks) == 1, marks
marks[0]['state'] = nbtlib.Int(air); del marks[0]['nbt']
buf = io.BytesIO(); f.write(buf); write('data/minecraft/structure/end_city/ship.nbt', gzip.compress(buf.getvalue()))

# 2. Loot tables that hand out Elytra.
for pattern, paths in {
    'BetterEnd-*.jar': ['data/betterend/loot_table/chests/end_village_bonus_loot.json'],
    'dungeons-and-taverns-*.jar': [f'data/nova_structures/loot_table/chests/end_castle/vault_{v}.json' for v in ('brigattine', 'galleon', 'slope')],
}.items():
    z = jar(pattern)
    for p in paths:
        keep(z, p); t = json.loads(z.read(p)); assert swap_elytra(t), p; write_json(p, t)
# Lootr's per-player Elytra chest (only used if a ship still had the marker): same swap.
z = jar('lootr-*.jar'); p = 'data/lootr/loot_table/chests/elytra.json'
keep(z, p); t = json.loads(z.read(p)); swap_elytra(t); write_json(p, t)

# 3. Incendium Piglin Paratroopers wear Elytra and drop them 8.5% of the time. Keep the wings, drop chance 0.
z = jar('Incendium_*.jar')
p = 'data/incendium/structure/castle/mobs/shock_troop.nbt'; keep(z, p)
f = nbtlib.File.parse(io.BytesIO(gzip.decompress(z.read(p))))
for e in f['entities']:
    if any(str(a.get('id')) == 'minecraft:elytra' for a in e['nbt']['ArmorItems']):
        e['nbt']['ArmorDropChances'][2] = nbtlib.Float(0.0)
buf = io.BytesIO(); f.write(buf); write(p, gzip.compress(buf.getvalue()))
p = 'data/incendium/function/castle/utils/action/alert.mcfunction'; keep(z, p)
txt = z.read(p).decode()
txt2 = txt.replace('{FallFlying:1b,', '{FallFlying:1b,ArmorDropChances:[0.085f,0.085f,0.0f,0.085f],')
assert txt2.count('ArmorDropChances') == 4, 'Incendium alert.mcfunction changed'
write(p, txt2)
p = 'data/incendium/function/castle/paratrooper/init.mcfunction'; keep(z, p)
txt = z.read(p).decode()
write(p, txt.rstrip('\n') + '\n\n# Create Galore: paratrooper Elytra never drop\ndata modify entity @s ArmorDropChances[2] set value 0.0f\n')

# 4. Create Jetpack: its recipes eat an Elytra. Use a Shulker Shell instead (same End City tier).
z = jar('create_jetpack-*.jar')
for name in ('jetpack', 'netherite_jetpack'):
    p = f'data/create_jetpack/recipe/{name}.json'
    keep(z, p); r = json.loads(z.read(p)); assert r['key']['E'] == {'item': 'minecraft:elytra'}, p
    r['key']['E'] = {'item': 'minecraft:shulker_shell'}; write_json(p, r)

# 5. The safety net in data/create_galore is hand-written in no-elytra; the with-Elytra pack switches it off.
write_json('data/create_galore/advancement/elytra_picked_up.json',
           {"criteria": {"never": {"trigger": "minecraft:impossible"}}}, BACK)
write('data/create_galore/function/no_elytra.mcfunction', '# Elytra are allowed: nothing to do.\n', BACK)
write_json('pack.mcmeta', {"pack": {"pack_format": 48, "description":
    "Create Galore: Elytra back on. Move this folder into config/paxi/datapacks to undo create-galore-no-elytra."}}, BACK)

print('rebuilt', os.path.normpath(OUT), 'and', os.path.normpath(BACK))
