# Vector

A shader pack for Iris lit by traced coloured light, written from the ground up for Create Galore. It hands DLSS and
FSR (through the Create Galore Bridge) everything they need, so they anti-alias or upscale it without patching. The
name is for its motion vectors: not only the camera's, but how each moving thing moved (Create's machines and
contraptions, ships, mobs, the hand), which is what lets DLSS and FSR keep them sharp. The Bridge's Upscaling &
Display screen picks the upscaler and the render resolution; the pack has no settings of its own for them.

**Work in progress: alpha 0.4.** It lives on its own branch and is not in the modpack yet. The first Create Galore
pack (`shaderpack/`) stays as a research build; the two share only the DLSS hand-off and the mods' plumbing
(Colorwheel, Distant Horizons), not their lighting.

## What it does

- **A copy of the world around you, fixed to the world.** The Create Galore Bridge keeps a box of blocks around you
  (128 x 96 x 128 by default) from the game's own block data: it reads a chunk when it loads and a block when it
  changes, never what the camera sees. Each block comes with its real shape (up to eight boxes, rounded to eighths so
  a 16th's step casts no shadow), its colour and its light. The box moves 8 blocks at a time and each block keeps its
  place in it, so a ray between two points gives the same answer wherever you stand. Plants, flat layers (carpets,
  snow, barnacles, pressure plates) and clear glass let light through; stained glass and water tint it; leaves let
  some through. A distance field lets rays skip empty space. Without the Bridge, the pack lights with Minecraft's own
  light.
- **Coloured block light, traced.** Every light (a table of vanilla's lights with their colours and strengths, or any
  block Minecraft says gives light, coloured from its texture) is traced through the copy, so light stops at walls,
  passes beside a fence post or over a slab exactly as their shapes allow, and takes the colour of the glass it passes
  through. Per block for everything, and per pixel near you for sharp shadows from lamps, sampled at a fraction of the
  pixels and filtered over frames. Which lights a spot gets is kept per 8-block patch of the world, never per part of
  the screen, so it doesn't change as you look around.
- **Bounce light.** Sunlight, skylight and block light bounce off the blocks around you and take on their colour
  (far off, every other frame).
- **Light that mixes like display colours**: the sun, the sky and the lamps add up the way the look we follow does,
  so sunlit and shaded parts, and a lamp's pool and the dark around it, stand well apart. Warm sun and blue shade,
  golden afternoons and evenings, nights lit faintly and only a little blue; rain takes the sun away and greys the sky.
- **Ambient occlusion** on screen for nooks and corners, from nearby walls only, over everything but mobs and the hand.
- **Hot texels glow**: a torch's flame, not its stick; lava, glowstone and froglights all over.
- **The sun and the moon** cast steady shadows from a shadow map (no noise, so nothing flickers), coloured through
  glass and through water with its caustics, with a clean, slightly soft edge. Leaves let light through the outside of
  their clump, grass doesn't shade itself, and near the map's edge they fade into what Minecraft's sky light says.
- **The sky**: a physically based atmosphere with the sun, the moon and the stars.
- **Water**: its own colour, deeper the deeper it is, and waves crossing from three directions, longer ones faster.
- **Light in the air**: a coloured haze around lamps (strongest at night, underground and in the Nether), haze far
  off, water and lava around you.
- **Bloom**, with a bloom fog that softens far lights at dusk and night, in rain and underground.
- **A fixed exposure**: day, night and caves look as dark as they are; the tone curve eases highlights to white and
  deepens the mid-tones a little.
- **Create's machines and contraptions** (Colorwheel) and **Distant Horizons** are lit like the rest of the world.
- The Nether and the End have their own light and haze; the Nether's light takes its biome's colour, and the End's
  light casts shadows.
- **Highlights** from every nearby lamp on polished blocks, metal and machines; rough blocks keep a faint sheen.
- **DLSS and FSR** get the surface behind particles, so slow smoke doesn't turn into a shifted window onto the scene.
- **The bad frame mark**: every pass that hands the frame on replaces an infinite or invalid value with nothing (one
  would spread over the whole screen through the bloom) and a small square at the middle of the left edge shows where
  it was caught (option Bad Frame Mark).
- **Held lights** light the world in their own colour, from the hand that holds them; what you hold takes the colour
  of the lights around you. Glowing particles (flames, sparks, portal particles) glow, and the End portal shows its
  starfield and gives light.
- **The block outline and leads** are drawn into the scene before the lighting, so they show as in vanilla.

## Later milestones

Reflections; foam and calmer, more varied water; sun shafts; clouds; material maps; soft sun shadows traced through
the copy; moving Create contraptions and ships in the copy, and mobs and the player shading lamp light (now they only
cast sun shadows).

## Own denoisers and Ray Reconstruction

The pack doesn't use Ray Reconstruction yet. Where it smooths its own noisy light over frames or pixels, the code says
`OWN DENOISER` (`grep -rn "OWN DENOISER" shaderpack2/shaders`), so a Ray Reconstruction mode knows what to take
off:

- `program/pixel_light_filter.csh`: the lamp light traced per pixel, brought back up to every pixel over frames and
  from nearby samples. Off under Ray Reconstruction, which wants the raw samples; it also wants every pixel traced
  each frame (RR doesn't take one pixel in a square at a time).
- `program/gi.csh`: the bounce light, gathered per block over frames. A light store in the world rather than a
  filter on the screen, so it can likely stay.

## Building

    python3 shaderpack2/programs.py          # writes the program files Iris looks for (after adding or removing one)
    python3 shaderpack2/tools/compile_check.py  # compiles every program and option variant with glslangValidator
    shaderpack2/build.sh                     # zips the pack into shaderpack2/dist/

## How it is laid out

- `shaders/program/` holds every program; the files Iris looks for by name (`gbuffers_terrain.fsh`, `composite1.fsh`,
  ...) in `shaders/`, `shaders/world-1/` and `shaders/world1/` only set their defines and include one, and are
  written by `programs.py`.
- `shaders/lib/` holds what the programs share.
- The passes, in order: the air's tables (`setup`, `begin`), the frame's light (`begin2`), the voxel copy from the
  Bridge's block grid (`begin3`); the sun's shadow map (`shadow_*`); the distance field, the light per block and the
  bounce light (`shadowcomp1` to `3`); the world into the G-buffer; the light per pixel and its filter (`deferred`,
  `deferred1`); the lighting (`deferred2`); water, glass and particles lit as they are drawn; the light in the air
  (`composite`); the DLSS point (`composite1`); the bloom (`composite2` to `12`); the final pass.
- The pack keeps all its data in images, no storage buffers: Iris takes those only at bindings 0 to 8, and Flywheel
  (Create's machines through Colorwheel) binds its own at 0 to 9 every frame. Iris allows 16 custom images; the pack
  uses 15 (two of them the Bridge's block grid and palette), several of them holding more than one table side by side
  (see `shaders.properties`).
- `tools/compile_check.py` also checks what Iris refuses when it loads the pack: storage buffers, more than 16 images,
  compute dispatch sizes that aren't plain numbers, and images or samplers no `image.` line makes.

The pack is our own code; it contains no third-party code.
