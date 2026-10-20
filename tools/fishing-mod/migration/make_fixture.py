#!/usr/bin/env python3
"""Write the migration test's fixture datapack into a world made with Tide.

    make_fixture.py <tide source tree> <world folder>

It adds `datapacks/cgfish_migration` to the world. Then, with Tide still installed, from the server console:
    function cgfish_migration:fill                      (blocks, chests and fish around 0 150 0)
    execute as <player> run function cgfish_migration:player   (the player's own Tide things)
and `save-all`. `census.py count` before and after the switch shows whether anything was lost.

The fixture has every Tide item (one each, in chests), some with Tide's own data: a rod rigged at the Angling Table
(line, hook, bobber, bait) and enchanted and named, sized fish, a satchel with fish in it, a bucketed fish, a
shulker box with Tide items inside a chest. Also Tide's blocks (crates holding items, the Angling Table, Fish
Displays with a fish, jelly torch, lunar calendar, weather radio), an item frame with a fish, and persistent Tide
fish swimming in a pool (one of each kind the aliases handle: kept, handed to Aquaculture, folded into another).
"""
import json
import pathlib
import sys

Y = 150
RIGGED_ROD = ('tide:iron_fishing_rod[tide:fishing_line={id:"tide:iron_line",count:1},'
              'tide:fishing_hook={id:"tide:fiery_hook",count:1},tide:fishing_bobber={id:"tide:blue_bobber",count:1},'
              'tide:bait_contents=[{id:"tide:lucky_bait",count:20}],'
              'minecraft:enchantments={levels:{"minecraft:luck_of_the_sea":2}},'
              "minecraft:custom_name='\"Old Faithful\"',minecraft:damage=40]")
SPECIAL = [
    RIGGED_ROD,
    'tide:pike[tide:fish_length=95.0d]',
    'tide:carp[tide:fish_length=61.5d]',
    'tide:largemouth_bass[tide:fish_length=44.0d]',
    'tide:pike_bucket[minecraft:bucket_entity_data={FishLength:80.0d}]',
    'tide:fish_satchel[tide:satchel_contents=[{id:"tide:pike",count:1},{id:"tide:carp",count:2}]]',
    'minecraft:shulker_box[minecraft:container=[{slot:0,item:{id:"tide:tuna",count:1}},'
    '{slot:1,item:{id:"tide:fish_bone",count:3}},{slot:2,item:{id:"tide:echo_fishing_rod",count:1}}]]',
    'minecraft:fishing_rod[minecraft:enchantments={levels:{"minecraft:lure":3}}]',
]


def tide_items(tide_src):
    """Tide's item ids, read from its registration code (as port_resources.py does)."""
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent / "port"))
    from port_resources import registered
    ids = registered(pathlib.Path(tide_src) / "src/main/java/com/li64/tide/registries")
    return sorted("tide:" + path for kind, path in ids if kind == "item")


def fill(items):
    out = ["# Create Galore Fishing migration fixture (tools/fishing-mod/migration/make_fixture.py)",
           "forceload add -16 -16 63 63",
           f"fill -2 {Y - 1} -2 50 {Y - 1} 50 minecraft:stone",
           f"fill -2 {Y} -2 50 {Y + 3} 50 minecraft:air"]
    stacks = items + SPECIAL
    for i in range(0, len(stacks), 27):
        x, z = (i // 27) * 2, 0
        out.append(f"setblock {x} {Y} {z} minecraft:chest")
        for slot, stack in enumerate(stacks[i:i + 27]):
            out.append(f"item replace block {x} {Y} {z} container.{slot} with {stack}")
    # Tide's blocks.
    out += [f"setblock 0 {Y} 6 tide:wooden_crate",
            f"item replace block 0 {Y} 6 container.0 with tide:pike[tide:fish_length=70.0d]",
            f"item replace block 0 {Y} 6 container.1 with tide:fish_bone 5",
            f"setblock 2 {Y} 6 tide:obsidian_crate",
            f"setblock 4 {Y} 6 tide:purpur_crate",
            f"setblock 6 {Y} 6 tide:angling_table",
            f"setblock 8 {Y} 6 tide:jelly_torch",
            f"setblock 10 {Y} 6 tide:lunar_calendar",
            f"setblock 12 {Y} 6 tide:weather_radio",
            f"fill 0 {Y} 10 6 {Y + 2} 10 minecraft:stone_bricks",
            f'setblock 1 {Y + 1} 11 tide:fish_display[facing=south]{{fish:{{id:"tide:pike",count:1,components:{{"tide:fish_length":90.0d}}}}}}',
            f'setblock 5 {Y + 1} 11 tide:fish_display[facing=south]{{fish:{{id:"tide:carp",count:1}}}}',
            f'summon minecraft:item_frame 3 {Y + 1} 11 {{Facing:3b,Item:{{id:"tide:tuna",count:1}}}}']
    # A pool with persistent fish: a ported one, one handed to Aquaculture, one folded into Darkness Eater,
    # and one let out of a bucket with its length.
    out += [f"fill 20 {Y - 4} 20 30 {Y - 1} 30 minecraft:glass",
            f"fill 21 {Y - 3} 21 29 {Y - 1} 29 minecraft:water"]
    for i, (fish, extra) in enumerate([("tide:pike", ""), ("tide:sturgeon", ""), ("tide:carp", ""),
                                       ("tide:tuna", ""), ("tide:voidseeker", ""),
                                       ("tide:largemouth_bass", ",FromBucket:1b,FishLength:44.0d")]):
        out.append(f"summon {fish} {22 + i} {Y - 2} 25 {{PersistenceRequired:1b{extra}}}")
    return out


def player():
    return ["# Run as the fixture player.",
            "give @s " + RIGGED_ROD,
            "give @s tide:pike[tide:fish_length=101.0d]",
            "give @s tide:fishing_journal",
            "give @s tide:stone_fishing_rod",
            "give @s minecraft:fishing_rod",
            "item replace entity @s enderchest.0 with tide:midas_fishing_rod",
            "journal unlock all"]


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    items = tide_items(sys.argv[1])
    root = pathlib.Path(sys.argv[2]) / "datapacks" / "cgfish_migration"
    fn = root / "data" / "cgfish_migration" / "function"
    fn.mkdir(parents=True, exist_ok=True)
    (root / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "Migration fixture"}}))
    (fn / "fill.mcfunction").write_text("\n".join(fill(items)) + "\n")
    (fn / "player.mcfunction").write_text("\n".join(player()) + "\n")
    print(f"{len(items)} Tide items + {len(SPECIAL)} with data, into {root}")


if __name__ == "__main__":
    main()
