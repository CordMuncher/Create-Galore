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
| `bait` | bait kinds and diets |
| `compat` | the move off Tide: `TideAliases` (old ids to their replacements), `TideRodConversion` (Tide-rigged rods to our parts), `RodSwap`, and the Tide self-tests |
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
