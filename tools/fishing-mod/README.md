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
| `client` | client-only code, only reached through `FishingClient.init` on the client |

More packages arrive with each milestone (see DESIGN.md).

## Licences

Our own code is MIT (`LICENSE`). Code, models, textures and data ported from Tide 2 keep Tide's MPL-2.0
(`LICENSE-MPL-2.0`), live under `dev/creategalore/fishing/tide/` and their own asset folders, and carry the MPL
notice at the top of each file. Both licence files are copied into the jar's `META-INF`.
