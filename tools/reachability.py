"""Finds crafts the pack's recipe changes left behind.

Usage: python3 -I tools/reachability.py <datacheck/recipes.tsv> <mod recipe folder> [pack datapack data folder]

recipes.tsv comes from the Data Check mod (/datacheck on a test server, tools/datacheck-mod): every recipe that
loaded, with what it makes and what each slot takes, every item, and every item a loot table can give.
The mod recipe folder holds the mods' own recipe files (tools/extract_mod_data.py <mods> <out> writes them to
<out>/recipes), so we know what each mod meant to be makeable.

Prints:
  UNIFIED  a duplicate the pack unified away: nothing makes it, it converts to the canonical item. Fine.
  ORPHAN   an item its own mod gives a recipe, that nothing makes any more and no loot table gives.
  STUCK    an item that still has recipes, but every one needs an orphan, a unified duplicate or another stuck
           item in some slot, so nobody can make it.
"""
import json, os, sys
from collections import defaultdict


def load_runtime(path):
    recipes, items, loot = [], set(), set()
    for line in open(path, encoding='utf-8'):
        f = line.rstrip('\n').split('\t')
        if f[0] == 'ITEM':
            items.add(f[1]); continue
        if f[0] == 'LOOT':
            loot.add(f[1]); continue
        rid, rtype, outs, ins = (f + ['', '', '', ''])[:4]
        slots = [frozenset(s.split('|')) for s in ins.split(';') if s]
        recipes.append((rid, rtype, set(o for o in outs.split(',') if o), slots))
    return recipes, items, loot


RESULT_KEYS = ('result', 'results', 'output', 'outputs')
SKIP_KEYS = ('sequence', 'transitional_item', 'transitionalItem')  # a sequenced assembly's half-made steps


def result_ids(node, inside=False, acc=None):
    acc = set() if acc is None else acc
    if isinstance(node, dict):
        for k, v in node.items():
            if k in SKIP_KEYS:
                continue
            if k in RESULT_KEYS:
                result_ids(v, True, acc)
            elif inside and k in ('id', 'item') and isinstance(v, str):
                acc.add(v)
            else:
                result_ids(v, inside, acc)
    elif isinstance(node, list):
        for v in node:
            result_ids(v, inside, acc)
    elif inside and isinstance(node, str) and ':' in node:
        acc.add(node)
    return acc


def conditional(d):
    """Recipes that only load with some other mod, or when some tag has items: the pack may not meet that."""
    return bool(d.get('neoforge:conditions') or d.get('conditions'))


# Items the pack turned off on purpose (see CHANGELOG), so they aren't reported as orphans.
OFF_ON_PURPOSE = {
    'animalhusbandry:fried_egg': "Farmer's Delight's fried egg is the pack's",
    'irons_spellbooks:portal_frame': 'travel rules: no portals',
    'irons_spellbooks:hither_thither_wand': 'travel rules: no portal wand',
    'smog:aerometer': "Pollution of the Realms' replaces it", 'smog:respirator': "Pollution of the Realms' replaces it",
    'smog:air_scrubber': 'Advanced Chimneys replace it', 'smog:flue': 'Advanced Chimneys replace it',
}
OFF_PREFIXES = ('aquaculture:',)  # Aquaculture's rods, hooks, line, bobber, tackle box: the fishing mod replaces them


def mod_intended(folder):
    """Items each mod's own recipes (in that item's own namespace) make."""
    made = defaultdict(set)
    for root, _, files in os.walk(folder):
        for fn in files:
            if not fn.endswith('.json'):
                continue
            p = os.path.join(root, fn)
            try:
                d = json.load(open(p, encoding='utf-8'))
            except Exception:
                continue
            if not isinstance(d, dict) or conditional(d):
                continue
            rid = os.path.relpath(p, folder)
            ns = rid.split(os.sep)[0]
            for i in result_ids(d):
                if i.split(':')[0] == ns:
                    made[i].add(rid)
    return made


def recipe_json(rid, mod_folder, pack_folder):
    """The file a loaded recipe came from: the pack's datapack wins over the mod's own."""
    ns, path = rid.split(':', 1)
    for p in ([os.path.join(pack_folder, ns, 'recipe', path + '.json')] if pack_folder else []) + \
             [os.path.join(mod_folder, ns, path + '.json')]:
        if os.path.exists(p):
            try:
                return json.load(open(p, encoding='utf-8'))
            except Exception:
                return None
    return None


def main():
    recipes, items, loot = load_runtime(sys.argv[1])
    intended = mod_intended(sys.argv[2])
    pack = sys.argv[3] if len(sys.argv) > 3 else None
    # The dump reads outputs off the recipe objects; side outputs some mods keep elsewhere come from the files.
    for r in recipes:
        d = recipe_json(r[0], sys.argv[2], pack)
        if isinstance(d, dict):
            r[2].update(i for i in result_ids(d) if i in items)

    producers = defaultdict(list)
    for r in recipes:
        for o in r[2]:
            producers[o].append(r)
    # create_galore:unify/<old>_to_<new>: the old item is a duplicate the pack folded into the canonical one.
    unified = {i for r in recipes if r[0].startswith('create_galore:unify/') for s in r[3] for i in s}

    orphans = {i for i in items if not producers[i] and i in intended and i not in loot}
    dead = orphans | {i for i in unified if not producers[i]}
    # An item is stuck when every recipe that makes it has a slot only dead items fit. Repeat until nothing changes.
    changed = True
    while changed:
        changed = False
        for i in items:
            if i in dead or i in loot or not producers[i]:
                continue
            if all(any(s <= dead for s in r[3]) for r in producers[i]):
                dead.add(i); changed = True

    for i in sorted(unified & dead):
        print('UNIFIED', i, sep='\t')
    for i in sorted(orphans - unified):
        if i in OFF_ON_PURPOSE or i.startswith(OFF_PREFIXES):
            continue
        print('ORPHAN', i, 'mod recipe(s): ' + ', '.join(sorted(intended[i])[:3]), sep='\t')
    for i in sorted(dead - orphans - unified):
        why = sorted({'|'.join(sorted(s)) for r in producers[i] for s in r[3] if s <= dead})
        print('STUCK', i, 'needs ' + ' / '.join(why[:3]), sep='\t')


if __name__ == '__main__':
    main()
