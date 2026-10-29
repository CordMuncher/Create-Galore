# Vector

A shader pack for Iris lit by traced coloured light, written from the ground up for Create Galore. It hands DLSS and
FSR (through the Create Galore Bridge) everything they need, so they anti-alias or upscale it without patching. The
name is for its motion vectors: not only the camera's, but how each moving thing moved (Create's machines and
contraptions, ships, mobs, the hand), which is what lets DLSS and FSR keep them sharp.

**Work in progress: build 2.** It lives on its own branch and is not in the modpack yet. The first Create Galore
pack (`shaderpack/`) stays as a research build; the two share only the DLSS hand-off and the mods' plumbing
(Colorwheel, Distant Horizons), not their lighting.

## What it does

- **A voxel copy of the world around you**, made every frame during the shadow pass: six levels of detail (the finest
  near you), with see-through cells for glass and water, each block's average colour, and a distance field that lets
  rays skip empty space.
- **Coloured block light, traced.** Every light (a table of vanilla's lights with their colours and strengths, or any
  block Minecraft says gives light, coloured from its texture) is traced through the voxel copy, so light stops at
  walls and takes the colour of the glass it passes through. Per block for everything, and per pixel near you for
  sharp shadows from lamps, sampled at a fraction of the pixels and filtered over frames. Partial blocks (paths,
  slabs, fences, Create's parts) start their rays outside their own shape, so they light like full blocks.
- **Bounce light.** Sunlight, skylight and block light bounce off the blocks around you and take on their colour
  (far off, every other frame).
- **Light that mixes like display colours**: the sun, the sky and the lamps add up the way the look we follow does,
  so sunlit and shaded parts, and a lamp's pool and the dark around it, stand well apart. Warm sun and blue shade,
  golden afternoons and evenings, nights lit faintly and only a little blue; rain takes the sun away and greys the sky.
- **Ambient occlusion** on screen for nooks and corners, from nearby walls only, over everything but mobs and the hand.
- **Hot texels glow**: a torch's flame, not its stick; lava, glowstone and froglights all over.
- **The sun and the moon** cast steady shadows from a shadow map (no noise, so nothing flickers), coloured through
  glass and through water with its caustics.
- **The sky**: a physically based atmosphere with the sun, the moon and the stars.
- **Water**: its own colour, deeper the deeper it is, and waves crossing from three directions, longer ones faster.
- **Light in the air**: a coloured haze around lamps (strongest at night, underground and in the Nether), haze far
  off, water and lava around you.
- **Bloom**, with a bloom fog that softens far lights at dusk and night, in rain and underground.
- **A fixed exposure**: day, night and caves look as dark as they are; the tone curve eases highlights to white and
  deepens the mid-tones a little.
- **Create's machines and contraptions** (Colorwheel) and **Distant Horizons** are lit like the rest of the world.
- The Nether and the End have their own light and haze.

## Later milestones

Reflections; foam and calmer, more varied water; sun shafts; clouds; material maps; Create contraptions in the voxel
copy (now they only cast sun shadows).

## Building

    python3 shaderpack2/programs.py          # writes the program files Iris looks for (after adding or removing one)
    python3 shaderpack2/tools/compile_check.py  # compiles every program and option variant with glslangValidator
    shaderpack2/build.sh                     # zips the pack into shaderpack2/dist/

## How it is laid out

- `shaders/program/` holds every program; the files Iris looks for by name (`gbuffers_terrain.fsh`, `composite1.fsh`,
  ...) in `shaders/`, `shaders/world-1/` and `shaders/world1/` only set their defines and include one, and are
  written by `programs.py`.
- `shaders/lib/` holds what the programs share.
- The passes, in order: the air's tables (`setup`, `begin`), the frame's light (`begin2`); the shadow pass, which also
  makes the voxel copy (`shadow_*`, with a geometry stage); the distance field, the light per block and the bounce
  light (`shadowcomp1` to `3`); the world into the G-buffer; the light per pixel and its filter (`deferred`,
  `deferred1`); the lighting (`deferred2`); water, glass and particles lit as they are drawn; the light in the air
  (`composite`); the DLSS point (`composite1`); the bloom (`composite2` to `12`); the final pass.
- The pack keeps all its data in images, no storage buffers: Iris takes those only at bindings 0 to 8, and Flywheel
  (Create's machines through Colorwheel) binds its own at 0 to 9 every frame. Iris allows 16 custom images; the pack
  uses 13, several of them holding more than one table side by side (see `shaders.properties`).
- `tools/compile_check.py` also checks what Iris refuses when it loads the pack: storage buffers, more than 16 images,
  compute dispatch sizes that aren't plain numbers, and images or samplers no `image.` line makes.

The pack is our own code; it contains no third-party code.
