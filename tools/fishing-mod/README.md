# Create Galore Fishing (maintainer notes)

The pack's own fishing mod, mod id `create_galore_fishing`. It replaces Tide 2 with realistic-lite fishing:
tackle you assemble and see, a fight with drag, tension and a bending rod, real fish weights, bait and
processing tied into Create, and rod holders / reel drives for boats. The full design and the milestone plan
are in [DESIGN.md](DESIGN.md); [INTEGRATION.md](INTEGRATION.md) (written at handover) lists what the pack's
docs and Field Guide need.

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
| `mixin` | `EntityAccessor` (display fish told they're in water), `PlayerModelHoldMixin` (the hold-up pose) |
| `sim` | the fight model: pure Java, no game classes, shared by the game and the Monte Carlo runner |
| `fish` | species data: loads Tide-format fish files plus our extension files, fills in defaults |
| `bait` | bait kinds and diets |
| `tide` | code ported from Tide 2 (MPL-2.0): fish conditions, modifiers, the fish data record |

More packages arrive with each milestone (see DESIGN.md).

## Fish data

Every fish comes from a Tide-format file, `data/<namespace>/fishing/fish/**.json` (where and when it bites, its
sizes in cm, its journal page); the pack's own overrides of these keep working. Our values (fight, weight, diet)
are in `data/<namespace>/fishing/species/**.json`; anything missing is worked out by `fish/SpeciesDefaults.java`
from Tide's minigame numbers and the fish's name.

Hand-tuned values live in one table, `species.csv`. Edit it and run `python3 tools/fishing-mod/gen_species.py`,
which rewrites the extension files under `src/main/resources/data/create_galore_fishing/fishing/species/`. It
fixes sizes Tide Extra Compatibility got wrong (a metre-long bluegill) and tunes the famous fighters.

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
- Tuning without restarts: start the client with `-Dcgfish.debug=true` for `/cgfishc rodpose first|third ...`,
  `/cgfishc rodload <load>|off` and `/cgfishc holdpose ...` (see `client/DebugCommands`), and model-size logging.
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
(`LICENSE-MPL-2.0`), live under `dev/creategalore/fishing/tide/` and their own asset folders, and carry the MPL
notice at the top of each file. Both licence files are copied into the jar's `META-INF`.
