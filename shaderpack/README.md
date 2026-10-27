# Create Galore Shaders

Create Galore's own shader pack for Iris, built for DLSS and FSR from the ground up. Every program writes the
motion vectors, depth and render size that DLSS and FSR need, so they anti-alias the frame (DLAA) or upscale it
without patching anything. Effects are added one at a time, each built to be upscaled and to scale with the CPU.

**Work in progress.** It lives on its own branch, and is not in the modpack yet.

## What it does now

Five presets: **Pure AA** is Minecraft's own look with only DLSS or FSR on top; **Low**, **Medium**, **High** (the
default) and **Ultra** light the overworld itself, with more of the effects the higher they go, and give the End a
sky of its own. The Nether and modded dimensions keep Minecraft's own look in all of them; no dimension but the
overworld ever gets the overworld's sky.

Sky and light (milestone 2, the Lighting option):

- A physically based sky: Sébastien Hillaire's atmosphere as Alpha Piscium fits it to the real air, remade every
  frame, with the sun, the moon's phases and the stars.
- The world lit in real units by the sun or the moon (their colour and strength from the air they shine through),
  the sky all around, and the blocks' light levels. Torches and every modded light light the world through
  Minecraft's light levels, so they work without any list of blocks.
- Shadows from a steady shadow map: not warped, and snapped to whole steps as you move. Options say what casts them
  (mobs, block entities, you, Create's machines and contraptions) and how far they reach.
- Glass reflects the sky; Distant Horizons' terrain is lit the same way past the shadows' reach, and its clouds and
  beacon beams are lit as clouds and glow.
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

Bugs and lighting (alpha 2):

- Soft shadows that stay sharp where a thing meets its shadow and soften the farther the light falls past it (a
  search of the shadow map for how far away the caster is, then a filter that wide), with the taps turned for each
  pixel and frame for DLSS and FSR to even out. Contact shadows from the screen's depth catch what the shadow map is
  too coarse for: grass, feet, a fence's rails.
- No more sunlight in sealed rooms: Iris left parts of the world out of the shadow map that it judged unable to
  shadow what you see (and out of the coloured and bounce light's volume with them); the pack has it draw everything
  in range.
- Water drawn as a body, after everything translucent: it takes the red out of the light first, then the green, and
  scatters a little of it, lit where the water is (shafts of sunlight through the shadow map, the sky from above,
  the coloured light), so the far water stays as bright as the light there wherever you are. From below, the sky
  shows through a round window over you and the surface mirrors the water around it; from above it reflects the
  sky and glints in the sun. Water draws into the shadow map, so the light knows how deep a point lies.
- Light through leaves: a tree's far side gets the light its leaves let through, greener for it.
- Blocks that give light glow where their texture is bright, modded ones too; by day the blocks' light holds up
  in the shade, so a lantern under a roof lights it. Bloom after DLSS, a pyramid of blurs at the window's size.
- Ambient occlusion: Minecraft's smooth lighting corners darken only the light all around now, not the sun's, with
  ground-truth ambient occlusion from the screen's depth on top (more directions and steps the higher the preset).
  Under a roof, a floor gets the sky's light in the colour of the low sky around it rather than the deep blue
  overhead.
- How much of the sky a point sees: Minecraft's sky light only knows how far a point is from open sky, not how high
  what covers it is, so under a floating island or a tall roof it goes as dark as a cave. Within 128 blocks of you,
  the shadow pass marks every solid block and leaf in a grid, and a few rays from each covered point go out through
  it towards the sky, a block at a time close by and in coarser copies of the grid further out, as Unreal traces
  its sky occlusion through a copy of the world around the camera. Further out, and on Distant Horizons' terrain,
  the screen shows how high the open air above a point reaches. A point never gets less of the sky's light than
  Minecraft's sky light gives it, so closed rooms and caves stay dark.
- Light Style: coloured lights brought towards torchlight's brightness to the eye (blue and purple the most, as
  they look dimmest) and deepened.
- The eyes adapt faster: about a second into a dark room.

Water, rain and reflections (alpha 3, the Reflections and Noise Pattern options):

- Waves that never repeat: Alpha Piscium's layers of cellular noise, each turned by the golden angle from the last
  and all warped by a slow curl field, the full swell at sea and less of it on lakes, rivers and swamps. Up close
  the crests hide what lies behind them (parallax).
- Water from above: what lies under it seen along the ray the surface bends, the world mirrored where the screen
  shows it (screen-space reflections) and the sky elsewhere, the sun's glint, and foam where the water meets the
  shore or something churns it.
- Caustics: the waves gather the sunlight into a net of bright lines on what lies under them, sharp in a block or
  two of water and broad and faint deeper down, with coloured edges. Their brightness follows the area ratio of
  the bent light (the method Evan Wallace's WebGL water made popular), through ripples finer than the waves.
- Rain: the film of water on everything shines while it falls and dries soon after; what it soaked into darkens
  and deepens in colour by what it is made of (sand and soil the most, metal hardly, after Sébastien Lagarde's wet
  surfaces) and stays damp for a while longer. Puddles fill the dips of a fixed pattern on flat ground under the
  open sky over the first half minute of rain, mirror the world with raindrop rings on them, and shrink back into
  their deepest spots over about four minutes after. Rain streaks are pale and clear, and the air glows softly
  around lights in the rain.
- Metal blocks reflect the sky in their own colour, and polished stone and quartz mirror the world faintly.
- Trees: leaves are shaded by the shape of their whole crown, the way stylised games shade foliage from a smooth
  shape around it rather than from each leaf card: the pack counts the leaves around every block near you (with
  Coloured Light or Bounce Light on) and lights each leaf the way the crown's outside faces there, so a tree reads
  as one round mass with clumps in it, sunlit on one side and shaded on the other, and darker deep inside. Less
  light gets through the crown, the screen's ambient occlusion reaches the leaves, and the light all around them
  leans more on which way they face (after Ghost of Tsushima's stylised lighting).
- Noise Pattern: the noise that soft shadows, ambient occlusion and reflections take their few samples by, either
  a smooth pattern made for TAA or white noise (what NVIDIA asks Ray Reconstruction to be given), for comparing
  which one DLSS resolves more calmly.

From milestone 1, in every preset:

- Motion vectors for everything that moves: the camera, mobs and block entities, the hand, Sable ships, Create's
  machines and contraptions (through Colorwheel), and Distant Horizons' terrain.
- Upscaling: with the Upscaling option on, the world renders at the Render Scale and DLSS or FSR bring it up to the
  window's size. Without DLSS or FSR, the frame is stretched up with a sharp filter instead.
- Distant Horizons: its terrain gets the same lighting and fog, and the fog moves out to its edge.

DLSS and FSR come from the Create Galore Bridge mod, which reads `shaders/create_galore_dlss.properties` to find
where they go. Without the Bridge the pack still runs, with no anti-aliasing. The Bridge in Create Galore 0.5.12 and
later also keeps water and stained glass in the shadow map while Aeronautics' levitite is in view (before it, the
levitite's shader left Iris drawing the next terrain as tessellation patches, which the driver refused), so the
caustics, the ripples and the light's depth under water need that version near ships.

## Layout

- `shaders/program/`: the shared programs. `gbuffers_kind.glsl` says what each world program draws and how vanilla
  shades it; `gbuffers.vsh/fsh` draw the world, `clrwl.*` Colorwheel's, `dh.*` Distant Horizons'. The lit overworld
  adds `air.csh` (the sky's tables), `frame_globals.csh` (the frame's light), `cloud_ambient.csh`,
  `clouds_march.csh` and `clouds_gather.csh` (the cumulus), `deferred.fsh` (the lighting), `exposure.csh`,
  `shadow.*`, `light_bounce.csh` and `light_flood.csh` (the coloured and bounce light), `water_sim.csh` (the
  ripples), `water_body.fsh` (the water, after the translucent things) and `bloom.csh`; the End adds `end_sky.fsh`.
- `shaders/lib/`: render size and jitter (`frame.glsl`), motion vectors (`motion.glsl`), vanilla's lighting and fog
  (`vanilla.glsl`), colour encodings and the options; for the lit overworld the air (`atmosphere.glsl`), the sky
  (`sky.glsl`, `celestial.glsl`, `stars.glsl`), the clouds (`clouds.glsl`, `noise.glsl`), the G-buffer
  (`gbuffer.glsl`), the lighting (`lighting.glsl`, `lit.glsl`, `shadow.glsl`, `light_volume.glsl`), the water
  (`water.glsl`, `water_body.glsl`, `medium.glsl`), reflections (`reflection.glsl`), the weather and what rain does
  (`weather.glsl`, `wet.glsl`), the bloom (`bloom.glsl`), the values its passes share
  (`globals.glsl`) and the tone mapping (`tonemap.glsl`); for the End its sky (`end_sky.glsl`).
- `shaders/textures/`: the clouds' noise textures and phase functions, and the water's wave textures.
- `shaders/composite1.fsh`: the DLSS point, which hands the frame over. `shaders/final.fsh`: the screen.
- The `gbuffers_*`, `clrwl_*` and `dh_*` files in `shaders/` only pick a program and include it.
- `shaders/world0/` (the overworld) and `shaders/world1/` (the End): each dimension's programs. Most are copies
  that `dimensions.sh` writes (run it after adding or removing a program); each dimension's own passes live only
  there. Dimensions without a folder use the programs in `shaders/`.
- `tools/wave_textures.py`: makes the wave textures from Alpha Piscium's (see License).
- `tools/bounce_check.py`: runs the bounce light's flood on a few small rooms and compares it with radiosity, to
  check a change to its settings before trying it in game (needs numpy).

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
from Alpha Piscium; the hashes after Jarzynski and Olano, the gradient noise after Inigo Quilez, MIT), and the
water's waves with their textures (`lib/water.glsl`; `shaders/textures/wave_noise.bin` and `wave_curl.bin`, made
from Alpha Piscium's `noise_5.bin` and `water_hf_curl.bin` by `tools/wave_textures.py`).

The ambient occlusion follows Jimenez et al.'s ground-truth ambient occlusion as Intel's XeGTAO lays it out (MIT,
https://github.com/GameTechDev/XeGTAO); its license is in `THIRD-PARTY-NOTICES.md` too.

The clouds' isotropic multiple scattering is from **HanPi Volume Cloud** by AshenOneArt (MIT, with attribution):
https://github.com/AshenOneArt/HPVolumeCloud. Its license is in `THIRD-PARTY-NOTICES.md`, which ships in the zip.
