# Create Galore Shaders

Create Galore's own shader pack for Iris, built for DLSS and FSR from the ground up. Every program writes the
motion vectors, depth and render size that DLSS and FSR need, so they anti-alias the frame (DLAA) or upscale it
without patching anything. Effects are added one at a time, each built to be upscaled and to scale with the CPU.

**Work in progress.** It lives on its own branch, and is not in the modpack yet.

## What it does now (milestone 1)

- Minecraft 1.21.1's own look: the lightmap, block face shading, the two lights on mobs and held items, and the fog,
  all as vanilla's own shaders draw them. The pure anti-aliasing profile is exactly this, with DLSS or FSR on top.
- Motion vectors for everything that moves: the camera, mobs and block entities, the hand, Sable ships, Create's
  machines and contraptions (through Colorwheel), and Distant Horizons' terrain.
- Upscaling: with the Upscaling option on, the world renders at the Render Scale and DLSS or FSR bring it up to the
  window's size. Without DLSS or FSR, the frame is stretched up with a sharp filter instead.
- Distant Horizons: its terrain gets the same lighting and fog, and the fog moves out to its edge.

DLSS and FSR come from the Create Galore Bridge mod, which reads `shaders/create_galore_dlss.properties` to find
where they go. Without the Bridge the pack still runs, with no anti-aliasing.

## Layout

- `shaders/program/`: the shared programs. `gbuffers_kind.glsl` says what each world program draws and how vanilla
  shades it; `gbuffers.vsh/fsh` draw the world, `clrwl.*` Colorwheel's, `dh.*` Distant Horizons'.
- `shaders/lib/`: render size and jitter (`frame.glsl`), motion vectors (`motion.glsl`), vanilla's lighting and fog
  (`vanilla.glsl`), colour encodings and the options.
- `shaders/composite.fsh`: the DLSS point, which hands the frame over. `shaders/final.fsh`: the screen.
- The `gbuffers_*`, `clrwl_*` and `dh_*` files in `shaders/` only pick a program and include it.

## Build

```
shaderpack/build.sh            # -> shaderpack/dist/Create-Galore-Shaders.zip
```

Put the zip (or the `shaderpack` folder itself) in the game's `shaderpacks/` folder.

## License

GPL-3.0 (see `LICENSE`). Effects will be ported from Alpha Piscium (GPL-3.0), which is why the pack takes its
license; each ported file will say where it came from. No Alpha Piscium code is in the pack yet.
