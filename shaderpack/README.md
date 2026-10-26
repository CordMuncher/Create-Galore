# Create Galore Shaders

Create Galore's own shader pack for Iris, built for DLSS and FSR from the ground up. Every program writes the
motion vectors, depth and render size that DLSS and FSR need, so they anti-alias the frame (DLAA) or upscale it
without patching anything. Effects are added one at a time, each built to be upscaled and to scale with the CPU.

**Work in progress.** It lives on its own branch, and is not in the modpack yet.

## What it does now

Two profiles: **Pure AA** is Minecraft's own look with only DLSS or FSR on top; **Sky** (the default) lights the
overworld itself and gives the End a sky of its own. The Nether and modded dimensions keep Minecraft's own look in
both; no dimension but the overworld ever gets the overworld's sky.

Sky and light (milestone 2, the Lighting option):

- A physically based sky: Sébastien Hillaire's atmosphere as Alpha Piscium fits it to the real air, remade every
  frame, with the sun, the moon's phases and the stars.
- The world lit in real units by the sun or the moon (their colour and strength from the air they shine through),
  the sky all around, and the blocks' light levels. Torches and every modded light light the world through
  Minecraft's light levels, so they work without any list of blocks.
- Shadows from a steady shadow map: not warped, snapped to whole steps as you move, filtered the same way every
  frame, so DLSS and FSR see the same shadow from one frame to the next. Options say what casts them (mobs, block
  entities, you, Create's machines and contraptions) and how far they reach.
- Water and glass reflect the sky; Distant Horizons' terrain is lit the same way past the shadows' reach, and its
  clouds and beacon beams are lit as clouds and glow.
- Exposure metered after DLSS, adapting like eyes (fast to darken, slower to brighten), and AgX tone mapping with
  Alpha Piscium's look after DLSS, at the window's size.

Clouds, weather and the End (milestone 3, the Clouds and End Sky options):

- Volumetric cumulus a kilometre up (height, thickness, coverage, density and wind are options), lit by the sun or
  the moon through the air and by the sky around them, with the light they scatter inside themselves. They are
  ray marched at a fraction of the render size, a different point each frame, and built up over frames, so DLSS
  and FSR see a steady image. Cirrus drifts high above them. The cumulus shade the ground as they pass, and from a
  plane or a mountain above them you look down onto their tops. Four quality levels set the cost.
- Weather: rain and thunder close the cloud cover and grey the sky, rain and snow haze the distance where they
  fall, wet ground darkens and reflects the sky, and lightning lights the sky and the clouds.
- The End: a nebula across a starry void, in place of Minecraft's (and BetterEnd's) sky, fading into the End's own
  fog at the horizon. The End keeps Minecraft's own light for now.

Light and water (milestone 4, the Coloured Light, Bounce Light and Water Ripples options; all three need Shadows):

- Coloured light: every light lights the world in its own colour, soul lanterns blue, redstone red, sea lanterns
  cool white, and every modded light the colour of its own texture, so they need no list of blocks. The shadow pass
  marks the lights, solid blocks and stained glass in a volume of blocks around you, and the light spreads through
  it a block a frame, mixing where lights meet and tinted by glass. It only colours the light: how bright it is
  stays Minecraft's light level, so it never reaches further or leaks where Minecraft's light doesn't.
- Bounce light: sunlight bounces off the world in the colour of what it falls on. The shadow map keeps the colour
  and facing of every surface the sun lights, each one sends its reflected light into the blocks in front of it,
  and it spreads through the same volume, stopped by solid blocks (which send some of it back) and dimmed by leaves:
  a room lights up from its sunlit floor, a red carpet tints the walls around it. The light is kept apart by which
  way the surface it left faces, and a surface takes none from surfaces facing its own way, so a sunlit floor never
  lights itself. Checked against a radiosity solution of a few rooms, it lands within about 2 times of it. Nothing
  in it is random, so there is no noise for DLSS or FSR to smear.
- Water: small wind waves on every water surface, and ripples where you, mobs, boats, Sable ships and Create's
  water wheels move through it, with wakes behind them. The ripples are a wave simulation on a grid around you,
  stepped 60 times a second whatever the frame rate; the shadow pass finds what reaches into the water. Both only
  bend the surface's light, so the water itself never moves and its motion vectors stay exact.

From milestone 1, in both profiles:

- Motion vectors for everything that moves: the camera, mobs and block entities, the hand, Sable ships, Create's
  machines and contraptions (through Colorwheel), and Distant Horizons' terrain.
- Upscaling: with the Upscaling option on, the world renders at the Render Scale and DLSS or FSR bring it up to the
  window's size. Without DLSS or FSR, the frame is stretched up with a sharp filter instead.
- Distant Horizons: its terrain gets the same lighting and fog, and the fog moves out to its edge.

DLSS and FSR come from the Create Galore Bridge mod, which reads `shaders/create_galore_dlss.properties` to find
where they go. Without the Bridge the pack still runs, with no anti-aliasing.

## Layout

- `shaders/program/`: the shared programs. `gbuffers_kind.glsl` says what each world program draws and how vanilla
  shades it; `gbuffers.vsh/fsh` draw the world, `clrwl.*` Colorwheel's, `dh.*` Distant Horizons'. The lit overworld
  adds `air.csh` (the sky's tables), `frame_globals.csh` (the frame's light), `cloud_ambient.csh`,
  `clouds_march.csh` and `clouds_gather.csh` (the cumulus), `deferred.fsh` (the lighting), `exposure.csh`,
  `shadow.*`, `light_bounce.csh` and `light_flood.csh` (the coloured and bounce light) and `water_sim.csh` (the
  ripples); the End adds `end_sky.fsh`.
- `shaders/lib/`: render size and jitter (`frame.glsl`), motion vectors (`motion.glsl`), vanilla's lighting and fog
  (`vanilla.glsl`), colour encodings and the options; for the lit overworld the air (`atmosphere.glsl`), the sky
  (`sky.glsl`, `celestial.glsl`, `stars.glsl`), the clouds (`clouds.glsl`, `noise.glsl`), the G-buffer
  (`gbuffer.glsl`), the lighting (`lighting.glsl`, `lit.glsl`, `shadow.glsl`, `light_volume.glsl`), the water
  (`water.glsl`), the values its passes share
  (`globals.glsl`) and the tone mapping (`tonemap.glsl`); for the End its sky (`end_sky.glsl`).
- `shaders/textures/`: the clouds' noise textures and phase functions.
- `shaders/composite.fsh`: the DLSS point, which hands the frame over. `shaders/final.fsh`: the screen.
- The `gbuffers_*`, `clrwl_*` and `dh_*` files in `shaders/` only pick a program and include it.
- `shaders/world0/` (the overworld) and `shaders/world1/` (the End): each dimension's programs. Most are copies
  that `dimensions.sh` writes (run it after adding or removing a program); each dimension's own passes live only
  there. Dimensions without a folder use the programs in `shaders/`.

## Build

```
shaderpack/build.sh            # -> shaderpack/dist/Create-Galore-Shaders.zip
```

Put the zip (or the `shaderpack` folder itself) in the game's `shaderpacks/` folder.

## License

GPL-3.0 (see `LICENSE`). Effects are ported from Alpha Piscium (GPL-3.0, alphapiscium.org), which is why the pack
takes its license; each ported file says where it came from. So far: the air and its tables
(`lib/atmosphere.glsl`, `program/air.csh`, after Hillaire's reference code, MIT), the moon's albedo and tint
(`lib/celestial.glsl`), the tone mapping (`lib/tonemap.glsl`, AgX after Troy Sobotka, with the MIT AgX code
Alpha Piscium credits), and the clouds with their textures (`lib/clouds.glsl`, `lib/noise.glsl`,
`program/cloud_ambient.csh`, `program/clouds_march.csh`, `program/clouds_gather.csh`, `shaders/textures/`, all
from Alpha Piscium; the hashes after Jarzynski and Olano, the gradient noise after Inigo Quilez, MIT).

The clouds' isotropic multiple scattering is from **HanPi Volume Cloud** by AshenOneArt (MIT, with attribution):
https://github.com/AshenOneArt/HPVolumeCloud. Its license is in `THIRD-PARTY-NOTICES.md`, which ships in the zip.
