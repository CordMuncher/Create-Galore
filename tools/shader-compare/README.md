# Shader compare

A test tool for comparing how shader packs look, scene by scene: the same vanilla-block scenes photographed with each
pack, at its defaults and at its highest preset, all with the SPBR resource pack. It is never part of the pack: it
goes only into a separate test instance (`PC-RUNBOOK.md`). It compares looks only; nothing is taken from the other
packs.

- `make_config.py` writes `create_galore_shader_compare.json`: the packs, each pinned to one file on Modrinth with
  its SHA-512, the options that make it read SPBR's maps (`pbr`), and its highest preset's options (`highest`,
  worked out from the pack's own `profile.*` lines as Iris applies them); and the scenes. Run it again after changing
  the pack list or the scenes (it downloads the packs into the folder it is given, to read their presets).
- `src/`: the capture mod. It keeps every scene's chunks loaded, puts the scenes up, then for each pass and pack loads
  the pack with that pass's options, visits each scene (time of day, weather, the camera held still), waits for the
  picture to settle (exposure, clouds, temporal filters) and saves a screenshot. Iris's own settings and every pack's
  options file go back as they were at the end.
- A survey before the packs turns scenes' cameras away from floating islands and airships; at each arrival frames are
  saved 0.1, 0.5, 1 and 2 seconds in (the jitter); End or a `shader-compare.stop` file in the game folder stops a run.
- `sheet.py`: contact sheets of a run, one JPEG per scene with every pack side by side, and arrival strips.
- `commit_shots.py`: full-size JPEGs of our pack's shots, small enough to commit.
- The side-by-side viewer is the DLAA test kit's: open `tools/dlss-testkit/compare.html`, pick the run folder, and
  pick any two packs (its "modes") to compare with a split slider, side by side, flip or a difference view.

## Packs

Five packs that span the range, picked by the owner, and ours. Complementary goes in once, as Reimagined (the one the
modpack ships; Unbound is the same pack underneath). The first run (`results/compare-1`) had thirteen: the ten most
downloaded on Modrinth in October 2026, Insanity, Alpha Piscium and ours. Iteration RP is never included.

| Pack | Version | Materials switches (`pbr`) | Highest preset |
| --- | --- | --- | --- |
| Create Galore Shaders | this branch | on by default | ULTRA |
| Complementary Reimagined | r5.9.3 | `RP_MODE=3` (labPBR) | COMPLEMENTARY |
| BSL | 10.1.8 | `ADVANCED_MATERIALS`, `MATERIAL_FORMAT=1`, `PARALLAX` | ULTRA |
| Photon | v1.4 | `NORMAL_MAPPING`, `SPECULAR_MAPPING`, `TEXTURE_FORMAT_LAB`, `POM` | ultra |
| Bliss | 2.1.2 | `Specular_Reflections`, `POM`, `MATERIAL_AO` | none (its profiles pick a tone curve) |
| Rethinking Voxels | r0.1-beta9 | `RP_MODE=3` | ULTRA |

Photon is its newest release, not the v1.3a the modpack's DLAA copy is made from. A pack with no quality presets has
no second pass. Ours runs at full resolution with DLSS as its anti-aliasing (DLAA), the others with their own.

## Scenes

In the DLAA test world (`tools/dlss-testkit/world/dlaa-test.zip`), vanilla blocks only, morning to night: the land at
noon, trees in the morning sun and towards the rising sun, far terrain in haze, the lake from above, a shallow pond
from above and from just over its surface, under water in a glass tank, SPBR's materials on walls and metal blocks
(and close along a wall), a window's beam in a dark room, light bouncing in a white room, lamps in the shade by day, a
cave lit by torches and lava, one lantern in a closed room at night, coloured lights, lanterns on chains with chains
and iron bars in front of lights, sunlight through stained glass, three coloured torches' shadows, sunset, the land and the sky at
night, the End, the Nether (a safe spot near 0 0, found on the first visit and kept), and rain last (the packs' wet
look lingers; it falls for three minutes before its shot). Trees at sunrise stand on a grass platform high in the open
so the low sun is in view; the coloured lights sit in a row along one wall so all six are in the picture. Each scene's `about` in the JSON says what it is for.

## Build

```
tools/bridge-mod/build.sh /path/to/server       # once, caches the client jars
tools/shader-compare/build.sh /path/to/server   # -> tools/shader-compare/build/create-galore-shader-compare-1.0.0.jar
```

`dist/` has a built one.

## Run

Put the jar in the test instance's `mods/`, `create_galore_shader_compare.json` in its `config/`, the packs in
`shaderpacks/` (ours as a folder named `Create-Galore-Shaders`) and SPBR on top of the resource packs. Then open the
test world and type `/shadercompare run` (or `/shadercompare run ours+photon` for some packs only), or start the game
with `-Dcreate_galore_shader_compare.autorun=dlaa-test`: it opens the world, runs and closes.
`-Dcreate_galore_shader_compare.packs=a+b`, `.passes=defaults` and `.scenes=a+b` run only those;
`.stay=true` keeps the game open after the run. Keep the window visible until the run finishes.

Screenshots go to `screenshots/shader-compare/<run>/<pack>-<pass>/<scene>.png`, with `manifest.json` (what ran,
each pack's options, whether it loaded, and the spots the `find` scenes chose). `/shadercompare where` prints the
camera as a scene's JSON, for adding scenes.
