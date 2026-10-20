# Create Galore Fishing (maintainer notes)

The pack's own fishing mod, mod id `create_galore_fishing`. It replaces Tide 2 with realistic-lite fishing:
tackle you assemble and see, a fight with drag, tension and a bending rod, real fish weights, bait and
processing tied into Create, and rod holders / reel drives for boats. The full design and the milestone plan
are in [DESIGN.md](DESIGN.md); [INTEGRATION.md](INTEGRATION.md) lists what the pack's docs and Field Guide need
(kept up to date per milestone, finished at handover).

## Build

```
tools/fishing-mod/build.sh /path/to/server     # a folder made by server/install-server.sh
cd pack && packwiz refresh                     # updates the jar's hash in index.toml
```

Same approach as the Bridge: plain `javac` against the installed server's jars (Mojang names, no Gradle, no
refmaps), plus a NeoForge client install cached in `~/.cache/create-galore-bridge` for the client classes. The
jar lands in `pack/mods/create-galore-fishing-<version>.jar`; the version comes from `neoforge.mods.toml`.

## Test

- `/cgfish selftest <suite|all>` (op, works from the server console) runs headless checks and writes one
  PASS/FAIL line per check to `create_galore_fishing/selftest-<suite>.txt` in the server folder.
- At start: `JDK_JAVA_OPTIONS="-Dcgfish.selftest=all -Dcgfish.exit=true" ./run.sh nogui` runs every suite once
  the server is up and stops it, for scripts.
- Migration from Tide (`migration/`): on a server that still has Tide and the previous build of this mod, make a
  world, run `migration/make_fixture.py <tide-src> <world>`, then in the console `function cgfish_migration:fill`
  and, with a player online, `execute as <player> run function cgfish_migration:player`, `save-all`. Count it with
  `migration/census.py count <world> before.json`, switch the server to this build without Tide, boot (the fixture
  chunks are force-loaded), let the player join, run `/cgfish selftest aliases` (writes the alias table),
  `save-all`, count again into `after.json`, and `census.py compare before.json after.json
  create_galore_fishing/aliases.tsv`: it lists anything lost. Last run: 0 of 496 Tide ids lost (394 items, Tide's
  blocks and block entities, its fish mobs, journal progress).

## Layout

`src/main/java/dev/creategalore/fishing/`:

| Package | What |
|---|---|
| (root) | `CreateGaloreFishing`, the entry point |
| `command` | `/cgfish` |
| `selftest` | the self-test runner; feature areas add suites with `SelfTest.suite` |
| `client` | client-only code, only reached through `FishingClient.init` on the client (rod and fish rendering, HUD, screens, sounds) |
| `cast` | the hook entity, bites, the fight in the world, catch and release |
| `tackle` | rod parts, their data, the rod item and the Tackle Bench |
| `net` | payloads |
| `mixin` | our mixins: accessors (`EntityAccessor`, `FallingBlockEntityAccessor`, `PathNavigationAccessor`, in place of Tide's access transformer), `PlayerModelHoldMixin` (the hold-up pose), `ItemStackRodSwapMixin` (vanilla and Alloyed rods become ours) |
| `sim` | the fight model: pure Java, no game classes, shared by the game and the Monte Carlo runner |
| `fish` | species data: loads Tide-format fish files plus our extension files, fills in defaults |
| `bait` | bait kinds and diets, live bait and the Live-Bait Bucket, chum, worms from hoeing |
| `process` | the Cleaning Table and its yields, the weight-limited fish ingredient, the `processing` self-test |
| `aquarium` | Aquarium Tanks: the multiblock (`AquariumTanks`), its contents and breeding (`AquariumBlockEntity`), what right-clicks do (`AquariumUse`), the `aquarium` self-test |
| `machine` | rod machines: `MountedRod` (what every machine shares), `MachineAngler` (the fake player a machine fishes as), the Rod Holder, the Basic / Geared Reel Drives (`ReelDriveBlockEntity`, a Create kinetic block) and the Fuel Reel Drive, with `ReelDrive` (what the drives share), their value boxes and goggles readout, and the `machines` self-test |
| `compat` | the move off Tide: `TideAliases` (old ids to their replacements), `TideRodConversion` (Tide-rigged rods to our parts), `RodSwap`, and the Tide self-tests; `IntegratedFarmingNets` (Fishing Net catches); `LiquidFuels` (what the Fuel Reel Drive burns); `SableShips` (machines on ships) |
| `tide` | Tide 2, ported (MPL-2.0): its 90 fish and their mobs, the journal, Fish Display, satchel, crates, gadgets and extras, its fish conditions and data, with its own mixins in `tide/mixin` |

More packages arrive with each milestone (see DESIGN.md).

## Fish data

Every fish comes from a Tide-format file, `data/<namespace>/fishing/fish/**.json` (where and when it bites, its
sizes in cm, its journal page); the pack's own overrides of these keep working. Our values (fight, weight, diet)
are in `data/<namespace>/fishing/species/**.json`; anything missing is worked out by `fish/SpeciesDefaults.java`
from Tide's minigame numbers and the fish's name.

Hand-tuned values live in one table, `species.csv`. Edit it and run `python3 tools/fishing-mod/gen_species.py`,
which rewrites the extension files under `src/main/resources/data/create_galore_fishing/fishing/species/`. It
fixes sizes Tide Extra Compatibility got wrong (a metre-long bluegill) and tunes the famous fighters.

## Ported Tide content

Tide 2 is not in the pack any more; this mod carries what players had from it, under Tide's own ids (namespace
`tide`), so worlds made with Tide load unchanged. The two can't be installed together (`incompatible` in
`neoforge.mods.toml`).

- Code: `port/port_tide.py <tide-src> <out>` copies the chosen Tide packages (lists at its top), resolves Tide's
  Stonecutter comments for NeoForge 1.21.1, renames packages and adds the MPL notice. Its output was then fixed
  by hand onto our registries, network and fish data (Tide's rod, minigame, Angling Table, config and Fabric code
  are not ported); a re-run is for diffing against a new Tide version, not for overwriting.
- Assets and data: `port/port_resources.py <tide.jar> <tide-extra-compatibility.jar> <tide-src>
  src/main/resources` copies them from the jars, re-runnable (it removes what it wrote last time, listed in
  `port/ported_resources.txt`; `port/skipped_resources.txt` lists what it left out and why is in the script).
  It leaves out everything for the ids we don't register, trims those from tags, merges Tide's entries into tags
  we already have, renames the Fish Display to Trophy Mount in English and points "Hooked on Variety" at our rods.
- Ids we don't register (the 12 species the pack hands to Aquaculture and Hybrid Aquatic, Tide's tier rods and
  lines, the Angling Table, fish bones and slices) load as their replacements through `compat/TideAliases`.
- Tide's hooks, baits, bobbers (as floats) and special rods (with their perks, `TacklePart.Rod.perk`) have tackle
  data from `gen_tackle.py`. A rod rigged with Tide converts to our parts in a player's inventory or the bench.
- Fish data reaches clients with `net/FishDataSync` (the journal needs it); Tide's crate and junk/treasure tables
  are read by `tide/data/fishing/CatchTables` and used by the bite roll.
- Self-tests: `aliases`, `tide`, `crates`, `spawns`, `components`. `/cgfish crate <player>` makes the next bite a
  crate (where one fits the spot: crates need open water, as in Tide).

## Bait and processing

- Data comes from one table, `gen_processing.py` (run it after editing): the Create recipes for bait (fan washing
  dirt to mud and worms and mud to leeches and clay, maggots by mixing, boilies by heated mixing, chum, fish meal
  by milling, fish oil by compacting), the Cleaning Table and Live-Bait Bucket recipes, shark skin to leather, the
  item tags `baitfish`, `live_bait`, `roe_fish`, `oily_fish`, `sharks`, and fish oil's Blaze Burner entry in
  Create: Liquid Fuel's data map. The files it wrote are listed in `processing_files.txt`. Baits' tackle data
  (what each attracts) is in `gen_tackle.py` as before.
- Big fish can't be cooked whole: every fish cooking recipe in the pack (vanilla's, Alex's Caves', Aquamirae's,
  Better End's, Naturalist's, Tide's) is replaced under its own id by one whose ingredient is
  `create_galore_fishing:whole_fish` (`process/WholeFishIngredient`), which refuses a fish known to weigh 3 kg or
  more. The other mods are `ordering = "AFTER"` dependencies so our copies load over theirs, and the `processing`
  self-test fails for any fish cooking recipe that can take a big fish and isn't limited (a new mod's, say).
- The Cleaning Table (`process/CleaningTable*`): strokes and yields come from the fish's weight (`Cleaning`):
  fillets, Hybrid Aquatic's steaks from 3 kg, roe, Aquaculture's bones, scraps (more for oily fish), shark skin. By
  hand the parts go to the player; a Deployer (any fake player) leaves them in nine output slots for hoppers and
  funnels, and the table takes the next fish only once those are empty.
- Live bait: bait loaded from the Live-Bait Bucket (a bundle that only takes `#create_galore_fishing:live_bait`)
  carries the `live_bait` component on the rod; a baitfish then counts as a live fish rather than cut bait. Taking
  bait off the rod strips it.
- Chum (`bait/Chum`): thrown (or dispensed) into water, bites within 8 blocks come up to 1.75x faster for three
  minutes; `/cgfish hook` shows the factor. Kept in memory, not saved.
- Fishing Nets (Create: Integrated Farming): `compat/IntegratedFarmingNets` registers a water and a lava catch
  provider where Tide's was, so nets bring up this mod's fish (small ones favoured) about half the time.

## Aquarium Tanks

- Tank blocks that touch and fill a box (up to 8 on each side) are one tank; the box's lowest corner is the
  controller and holds the water, the fish (as fish items with their length) and the fish meal. `AquariumTanks`
  forms tanks a tick after a block is placed (its block entity exists by then) and splits them when one breaks:
  what's left joins back up if it still fills a box and takes the contents, else the fish spill out. The six side
  properties say which faces join the same tank; the multipart model (`art/make_aquarium_models.py`) leaves out
  glass and frame there.
- A fish needs room by its length (`AquariumBlockEntity.space`) and a tank longer than itself, and only goes in once
  the tank is at least half full of water. Water comes by bucket or pipe (fluid capability).
- Two or more of one `#create_galore_fishing:baitfish` species with fish meal raise a young one every two minutes.
  Funnels and hoppers feed fish meal into slot 0 and take spare baitfish (the tank keeps two of each kind).
- Fish swim client-side only (`client/AquariumSwim`), drawn as their mob's model at true length with the water by
  `client/AquariumRenderer`; the server keeps the list.

## Rod machines

- Every machine fishes as its own `MachineAngler` (a fake player that owns the hook and holds the machine's rod), so
  bites, the fight and other mods' hooks work as for a player; the hook asks the machine (`RodMount`) what a
  player's hands would do. The hook syncs the machine's position and goes to clients without an owner (its
  `getAddEntityPacket` / `recreateFromPacket`), and clients draw the line from where the machine's renderer drew the
  tip. Catches credit the player who set the rod in; triggers and stats are never fired on the fake player.
- `MountedRod`: a rod goes in by right-click (a cast line goes with it and keeps its bite or fish), an empty hand
  takes it out (the line, bite or fight go to the player) or takes a fish hanging at the tip. A landed catch banks
  half the hand XP (`MACHINE_XP`), paid out each time a rod goes in or out; the catch goes into an inventory below
  or beside the machine (never another rod machine: a row of drives would feed each other's bait slots), else the
  first fish hangs at the tip for 30 s. A fish put on a Create belt is marked alive (`cast/AliveFish`).
- Rod Holder: holds, sounds the alarm, pulses redstone on a bite (comparator: 15 on a bite, else the line's
  tension), keeps a bite five seconds so someone can take the rod and strike.
- Reel drives (`ReelDrive`): the Basic drive only cranks, at a speed from the shaft (32 RPM is the reel's own
  speed), and keeps cranking while the drag slips (the reel wears fast). The Geared and Fuel drives have the
  automatic `sim/Gearbox`, never crank while the drag slips, strike a bite themselves, and cast: Wait (the
  default) casts once and holds, Cast winds the rig back slowly and casts again. They re-bait from a bait slot
  (bait or a Live-Bait Bucket; hoppers and funnels reach it) before each cast and don't cast a bait rig without
  bait. Value boxes on the back face: mode, cast distance, top gear (each is its own behaviour type with its own
  save key and network id, `DriveSetting`, since Create keeps one behaviour per type).
- Torque against drag: a shaft drive's stress is `ReelDriveBlockEntity.impact` (1 SU/RPM idle, plus 4 SU/RPM per kgf
  of tension times the gear while cranking against a fish), set live but at most every half second and only on a
  tenth's change. What it can wind against is what the network can spare (`torqueAtOne`). A drag a fish can lean
  on that is more than the rotation can turn overstresses the network until the fish eases off: on a water wheel
  (8 RPM, 256 SU) a 6 kg drag never stalls, a 10 kg one does with the Basic drive, not with the Geared drive's
  lowest gear. The Fuel drive's engine has a fixed rating (12 kgf in gear 1) and burns furnace fuel from its slot
  or a liquid fuel (Create: Liquid Fuel's map, `compat/LiquidFuels`) from its 4 B tank, a tenth while waiting and
  all of it at full load, only while a rod is in with a line out or one to cast.
- Ships (`compat/SableShips`): a machine's block entity on a Sable ship lives in the ship's plot, so its tip, cast
  direction and angler position are projected into the world through Sable's companion library. A machine's rig
  can't get further from the tip than the line it was set with, so a moving ship tows it; a towed lure dives to
  its depth and works when the speed suits it, and the bite pool follows the water it passes.
- Test tools: `/cgfish machine <pos>` (hook state and, for a drive, gear, what it can wind against, cranking,
  stalled), `... bite [<species> <cm>]` (the next bite), `... hook <species> <cm>` (a fish on at once, struck: how
  to put a fish on a Basic drive in a test).

## Visuals

- Rods in the hands are drawn by `client/RodItemRenderer` + `client/RodModel` (boxes, no model files): the rod's
  item model is wrapped so the hands get the custom renderer and everything else keeps the sprite. Placement in the
  hand is the `FIRST_PERSON` / `THIRD_PERSON` arrays; the bend, telescopic extension and crank are worked out per
  frame from the hook's synced state. The renderer records where each tip was drawn and the line starts there.
  The bend curls the blank toward the line, more of the way the heavier the load (rods take huge bends), so the
  angle between rod and line decides how deep it goes; with a fish on the rod is lifted `CastHook.ROD_LIFT`
  degrees, and the fight's rod-angle maths uses the same lift.
- Fish (on the line, held up, in the catch screen) are drawn by `client/FishView` as their own mob's model where
  the item has a mob of the same id, sized from the model's measured extent to the fish's real length, and fattened
  to its weight if the model is slim.
- Fish items (every mod's) are drawn as their mob's model too (`client/FishItems`, `FishItemRenderer`): held,
  dropped, in item frames, on heads, at their catch's length within limits per view (the `FIRST_PERSON` ...
  `FIXED` arrays). Which items, and which mob draws each, is read at startup from the fish files this mod ships
  plus same-id water mobs; items another mod already renders itself are skipped. Inventory icons are the model
  drawn once into a 64 px texture with a dark rim (`FishIcons`), baked a few per frame and rebuilt on resource
  reload. Anything that can't be drawn (no world, a mob that throws, dropped fish beyond 16 blocks) is the sprite.
  Trophies and uniques get a star in the slot and the rare / epic name colour.
- A fish on a Cleaning Table is drawn the same way (`client/CleaningTableRenderer`), lying on its side, with the
  strokes done shown while the player looks at it.
- Tuning without restarts: start the client with `-Dcgfish.debug=true` for `/cgfishc rodpose first|third ...`,
  `/cgfishc rodload <load>|off`, `/cgfishc holdpose ...` and `/cgfishc fishpose first|third|ground|fixed|head ...`
  (see `client/DebugCommands`), plus model-size logging and the list of fish items at startup.
- `/cgfish hold <player> <species> <cm>` puts a fish of that size in a player's hands (catch screen and all).

## Balancing

- `/cgfish simulate <species|all> [fights]` runs the real fight code against every rig preset with a careful and a
  careless angler and writes `create_galore_fishing/simulate.csv`: landing, snap, bite-off, rod, hook-throw and
  spool rates, fight length, average weight and reel wear.
- `/cgfish simulate export` writes `species.csv` for `tools/fishing-mod/sim.sh`, which runs the same Monte Carlo
  without a game (fixed seeds, so runs repeat).
- Catch XP is `cast/Catches.xp`: a base by rarity, times weight (absolute and against the species' usual), size
  class and fight effort; release by hand adds 25% (`CatchHolding.RELEASE_BONUS`), machines get half.
- The `fight` self-test suite checks a few bands (cod and bass land on starter gear, tuna mostly don't, but do on
  boat gear).

## Licences

Our own code is MIT (`LICENSE`). Code, models, textures and data ported from Tide 2 keep Tide's MPL-2.0
(`LICENSE-MPL-2.0`), live under `dev/creategalore/fishing/tide/` and the `tide` asset and data folders, and the
code carries the MPL notice at the top of each file. Tide Extra Compatibility's data is MIT
(`LICENSE-TIDE-EXTRA-COMPATIBILITY`). `NOTICE` sums it up; all four are copied into the jar's `META-INF`.
