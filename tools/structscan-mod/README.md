# Structure Scan

A tuning tool for the pack, not part of it (players never get it). It finds every structure in an area
straight from the seed, without generating any chunks, and reports how dense they are: per layer
(surface, underground, underwater, sky), per mod and per structure, compared with a vanilla baseline.

## How it works

Where a structure can go is pure maths on the seed: each structure set's placement grid (spacing,
separation, salt), its frequency and its exclusion zones. Only then does the game check the spot
(biome, terrain height, jigsaw layout), and those checks read the noise and the biome source, not real
chunks. The scanner lists every chunk the placement maths picks in the area, then runs the game's own
structure-start step (`ChunkGenerator.createStructures`) on a throwaway chunk for each one. Every mod's
changes to placement and to the checks (Lithostitched, YUNG's, Tectonic terrain, biome tags...) apply,
because it is the same code real world generation runs. Nothing is generated, lit, decorated or saved.

Most of the time goes into the terrain and biome checks some structures make (Mowzie's Wroughtnaut
chamber, Alex's Caves' cave biome searches, Aquamirae). While a scan runs, a small mixin remembers terrain
heights, terrain columns and climate samples, so the same spot is never computed twice; the answers are
the generator's own, it just isn't asked twice. Candidate chunks are handed out in row order so each thread
reuses its neighbours' samples. On the 4-core test box a 16 km² pack scan takes about 245 s, or 167 s when it
isn't the first scan since the server started (the first one also loads every structure template). So put
several what-ifs in one start: `--rules none+a.txt+b.txt`.

Two mods aren't fully repeatable on their own: Mowzie's monastery layout and Moog's wanderer raft came out
slightly different between two identical scans (1 structure in 2,350). Real world generation has the same
wobble; it doesn't move the density numbers.

`--verify` checks it against reality: afterwards it takes the chunks near the centre through the real
structure-starts step and compares what they hold with what the scan predicted.

## Use

```sh
tools/structscan-mod/build.sh /path/to/server          # once: builds the jar against an installed server
tools/structscan-mod/scan.sh /path/to/server --seed 12345 --radius 4000 --label pack
```

`scan.sh` starts the server once on a throwaway world (`structscan-world`), scans, stops, and puts
`server.properties` and `mods/` back as they were. Reports land in
`<server>/structscan/<time>-<label>-<dimension>/`:

| File | What |
|---|---|
| `summary.md` | Per layer density, nearest-neighbour distances, how many you'd see from a random spot, the vanilla comparison with Emre's targets, per mod, per structure, per structure set |
| `sets.tsv` | Every structure set: placement type, spacing, separation, frequency, candidates, placed per layer, its structures |
| `mods.tsv` | Per mod: structure kinds it can place, how many it placed per layer, share of structures vs share of kinds |
| `structures.tsv` | Per structure: count, main layer, per layer counts |
| `starts.tsv` | Every structure found, with position, height range, ground and water height, layer, biome |
| `biomes.tsv` | Per biome: area at the ground and at y -20 (cave biomes), structures per layer, density |
| `scan.properties` | Totals, so the scan can be another one's baseline |
| `verify.txt` | With `--verify`: predicted vs real structure starts |

Options: `--radius B` (square ±B blocks around 0,0), `--dims overworld+the_nether`, `--baseline DIR`,
`--rules FILE` (or `none+a.txt+b.txt` for several runs in one start), `--verify B`. Copy a vanilla scan's folder to `<server>/structscan/baseline/` to have
every later scan compare against it automatically.

**Vanilla baseline**: a bare NeoForge 21.1.259 server with only this jar in `mods/`, same seed. It takes
about 85 s for 144 km².

Biomes: each structure gets the biome at the middle of its bounding box (at the ground for the ones that pick
their height later). Biome areas come from a grid of samples every 64 blocks, at the ground and at y -20, so
underground structures are compared with how much of the caves a biome fills. The summary's "By biome" table
shows which biomes are crowded or empty compared with the whole square.

Layers are judged at the middle of each structure's bounding box: sky starts 16+ blocks above the ground or
water, underground tops out more than 4 blocks under the ground or sea floor, underwater stands in 3+ blocks
of water below its surface, surface is the rest. Shipwrecks, ocean ruins, igloos and buried treasure only
pick their height when placed, so they're judged by where they stand (water or land; treasure is underground).

## What-if rules

`--rules FILE` (or `/structscan whatif` with `config/structscan-rules.txt`) rescales structure sets for the
scan only, without touching the world or the pack:

```
# <structure set or structure pattern> <spacing scale> [frequency scale]; first match wins, * is a wildcard
dungeons_arise:* 2
aquamirae:* 3
minecraft:* 1
* 1.5
```

Spacing and separation are multiplied (separation stays below spacing). Random-spread sets can be rescaled,
including mods' own variants (Aquamirae's configurable one is rescaled as a plain random spread; the real
change would go through Aquamirae's config). Create Structures Arise swaps in its own placements when the
world loads, so rules don't reach its sets; the report marks them "not applied". Exclusion zones keep
pointing at the original sets.

Rule of thumb for planning: a set's density goes with 1 / spacing² (twice the spacing, a quarter of the
structures), times the frequency. So the placed counts in `sets.tsv` give a quick estimate for any
spacing; a what-if scan then confirms it.

## In game

With the jar in a server's `mods/` (op level 2): `/structscan run <radius> [label]` scans around you in
the background, `/structscan whatif <radius> [label]` does the same with `config/structscan-rules.txt`.
It works on an existing world too: it only reads the seed.
