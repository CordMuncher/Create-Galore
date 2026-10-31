# Vector

A shader pack for Iris lit by traced coloured light, written from the ground up for Create Galore. It hands DLSS and
FSR (through the Create Galore Bridge) everything they need, so they anti-alias or upscale it without patching. The
name is for its motion vectors: not only the camera's, but how each moving thing moved (Create's machines and
contraptions, ships, mobs, the hand), which is what lets DLSS and FSR keep them sharp. The Bridge's Upscaling &
Display screen picks the upscaler and the render resolution; the pack has no settings of its own for them.

**Work in progress: alpha 0.6.** It lives on its own branch and is not in the modpack yet. The first Create Galore
pack (`shaderpack/`) stays as a research build; the two share only the DLSS hand-off and the mods' plumbing
(Colorwheel, Distant Horizons), not their lighting.

## What it does

- **A copy of the world around you, fixed to the world.** The Create Galore Bridge keeps a box of blocks around you
  (128 x 96 x 128 by default) from the game's own block data: it reads a chunk when it loads and a block when it
  changes, never what the camera sees. Each block comes with its shape as it is drawn (up to twelve boxes, to the
  16th), its colour and its light. The box moves 8 blocks at a time and each block keeps its place in it, so a ray
  between two points gives the same answer wherever you stand. Plants and flat layers (carpets, snow, barnacles,
  pressure plates) let light through; stained glass and water tint it. Cut-out faces let light through their clear
  pixels and stop it at the rest: a trapdoor's windows, a grate's slots, the gaps between iron bars, the clear middle of
  glass inside its frame, the gaps in leaves. The Bridge (1.167.0 and later) reads each face's texture once into
  16 x 16 masks, and a ray reads the pixels it crosses, blurred as wide as its cone is there; with an older Bridge,
  clear glass and bars let all light through and leaves some. A distance field lets rays skip empty space. Without
  the Bridge, the pack lights with Minecraft's own light.
- **Coloured block light, traced.** Every light (a table of vanilla's lights with their colours and strengths, the
  same table for a light placed and the same light held, or any block Minecraft says gives light, coloured from its
  texture) is traced through the copy, so light stops at walls, passes beside a fence post or over a slab exactly as
  their shapes allow, and takes the colour of the glass it passes through. A part thinner than a light is wide there
  (a fence's rail in front of a torch's flame) hides only the share of the light it covers, so its shadow is soft and
  partial, never a hard cut. Light that only grazes an edge counts what lies on both sides of it, so no fine line gets
  through where two trapdoors meet at a corner or past the frame of a grate. Per block for everything, and per pixel
  near you for sharp shadows from lamps, sampled at a fraction of the pixels and filtered over frames. Which lights a
  spot gets is kept per 8-block patch of the world, never per part of the screen, so it doesn't change as you look
  around.
- **Bounce light.** Sunlight, skylight and block light bounce off the blocks around you and take on their colour
  (far off, every other frame).
- **Light that mixes like display colours**: the sun, the sky and the lamps add up the way the look we follow does,
  so sunlit and shaded parts, and a lamp's pool and the dark around it, stand well apart. Warm sun and blue shade,
  golden afternoons and evenings, nights lit faintly and only a little blue; rain takes the sun away and greys the sky.
- **Ambient occlusion** on screen for nooks and corners, from nearby walls only, over everything but mobs and the hand.
- **Hot texels glow**: a torch's flame, not its stick; lava, glowstone and froglights all over.
- **Lava lights its surroundings** wide and soft, one light for each 2 x 2 patch of it that shows, from the hot air
  just over it, so a pool sunk in a floor lights the floor round it; its glow bounces too.
- **The sun and the moon** cast steady shadows from a shadow map (no noise, so nothing flickers), coloured through
  glass and through water with its caustics, with a clean, slightly soft edge. Leaves let light through the outside of
  their clump, grass doesn't shade itself, and near the map's edge they fade into what Minecraft's sky light says.
- **The sky**: a physically based atmosphere, pushed to the look we follow: a deep blue overhead by day paling to a
  bright, slightly hazy horizon, an orange glow round the setting sun and a pink belt across from it, a deep blue
  night. Stars by the thousand in their own colours, steady (no twinkle) and at least a pixel and a half wide so the
  upscaler keeps them: the brightest come out first at dusk, and a bright moon hides the faint ones and the Milky Way,
  which has dark dust lanes along it. The sun, round, with a tight glow. The moon about twice Minecraft's size
  (option Moon Size), its surface from NASA's maps of the Moon: its seas and craters, lit from the side its phase
  says so the craters stand out along the line between light and dark, a cool silver-blue, with the Earth's faint
  light on its dark part and a soft halo. The far haze takes the sky's colour. Sunlight on the land turns warmer at
  dawn and dusk. Rain greys the sky and hides the sun, the moon and the stars.
- **Clouds**: a layer of real clouds drawn as a volume, their bases gathering round Y 230 (option Cloud Height),
  always above the pollution's smog, so they never hide what goes on below them. The bases rise and fall over a
  kilometre or two and from cloud to cloud (15 blocks under the layer's height to 20 over it), so the layer never
  reads as one flat floor. A slow weather map says where the sky is clear blue, scattered, broken or nearly closed,
  and what kind of cloud is there: flat stratocumulus sheets 12 to 24 blocks deep with gaps, small fair-weather
  cumulus 24 to 40 tall with domed tops, or big cumulus towering up to 95 where the cover is thick; rain closes it
  into one grey deck. Flat underneath, ragged outlines, round cauliflower billows on top and swirled wisps under the
  base, a frayed edge about a block across up close (for planes), small loose scraps round the bigger ones; the cores
  are solid and only the edges thin out. Far above, thin streaks and soft ripples (cirrus and cirrocumulus) in places.
  Lit by the sun through the cloud between (white tops, shade in the crevices and on the far side, silver edges round
  the sun, which burns through thin parts, and with the sun behind you the darker "powder" look of real cumulus) and
  by the sky from above (grey-blue undersides), gold and pink at dusk and dawn, moonlit at night; they fade into the
  sky's colour far off and reach 5000 blocks. The look along each pixel crosses empty air in coarse steps and goes
  back over the stretch where it found cloud in fine ones, so their edges hold still from frame to frame, and detail
  too fine to show at a distance gives way to its average so nothing far off flickers. The game's clock moves them
  (they don't jump, they are where they were when you reload, and with the daylight cycle stopped they stop too) at
  about a block a second (option Cloud Speed), the wind's direction wandering over days. Every pixel is worked out
  every frame at half the render size each way and smoothed over a few frames, never a fraction of the pixels per
  frame (which makes clouds crawl in motion); DLSS gets their own motion. Their shadows cross the land (option Cloud
  Shadows). Option Realistic Clouds off gives Minecraft's clouds, lit orange and pink at dusk and dawn.
- **Water**: its own colour, deeper the deeper it is, and waves crossing from three directions, longer ones faster.
- **Light in the air**: a coloured haze around lamps (strongest at night, underground and in the Nether), haze far
  off, water and lava around you.
- **Bloom**, with a bloom fog that softens far lights at dusk and night, in rain and underground; big bright patches
  (lava, fire, glowstone, the sun) bloom more, twice as much in the Nether.
- **A fixed exposure**: day, night and caves look as dark as they are; the tone curve eases highlights to white and
  deepens the mid-tones a little.
- **Create's machines and contraptions** (Colorwheel) and **Distant Horizons** are lit like the rest of the world.
- **The Nether**: its light and its haze take its biome's colour, the haze as thick as the biome's fog says (basalt
  deltas' ash and the soul sand valley's the thickest, the warped forest the clearest), thicker under the ceiling
  and lit orange from below where you look out over the lava sea.
- **The End**: a violet sky with a slow drifting nebula of purple and pink clouds with green wisps, stars that
  twinkle, and a small pink-white sun whose pink-lavender light casts the islands' shadows; the shade is violet,
  and a violet haze lies low round the islands.
- **Highlights** from every nearby lamp on polished blocks, metal and machines; rough blocks keep a faint sheen.
- **DLSS and FSR** get the surface behind particles, so slow smoke doesn't turn into a shifted window onto the scene.
- **The bad frame mark**: every pass that hands the frame on replaces an infinite or invalid value with nothing (one
  would spread over the whole screen through the bloom) and a small square at the middle of the left edge shows where
  it was caught (option Bad Frame Mark).
- **Held lights** light the world in their own colour, from the hand that holds them; what you hold takes the colour
  of the lights around you, and your own body casts no shadow on your hands or what they hold. Glowing particles
  (flames, sparks, portal particles) glow, and the End portal shows its starfield and gives light. All particles are
  drawn after the lighting (`particles.ordering = AFTER`), behind your hand. Iris on NeoForge ignores that setting,
  and the Bridge (1.166.0 and later) carries it out. With an older Bridge, flames show over your hand.
- **The block outline and leads** are drawn into the scene before the lighting, so they show as in vanilla.

## Later milestones

Reflections; foam and calmer, more varied water; sun shafts; material maps; soft sun shadows traced through
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
- `program/clouds.csh`: the clouds, kept over frames to smooth away where each look into them starts. Off under Ray
  Reconstruction, which wants the raw looks.

## Building

    python3 shaderpack2/programs.py          # writes the program files Iris looks for (after adding or removing one)
    python3 shaderpack2/tools/compile_check.py  # compiles every program and option variant with glslangValidator
    shaderpack2/build.sh                     # zips the pack into shaderpack2/dist/
    python3 shaderpack2/tools/cloud_noise.py # remakes the clouds' noise (shaders/textures/cloud_noise.dat)
    python3 shaderpack2/tools/moon_map.py    # remakes the moon's surface (shaders/textures/moon.png) from NASA's maps
    python3 shaderpack2/tools/light_tester/light_tester.py SCENE.py OUT.png  # the block light without Minecraft

`tools/light_tester/` runs the pack's own block light programs (the voxel copy, the distance field, the light per
block and the light per pixel) on a scene of blocks written in Python, in a stand-in for Iris, and draws it at any
resolution in under a minute on the CPU: for problems too fine or too slow to chase in the game. See the script's
notes for its options; `scene_leaks.py` and `scene_regress.py` are examples.

## How it is laid out

- `shaders/program/` holds every program; the files Iris looks for by name (`gbuffers_terrain.fsh`, `composite1.fsh`,
  ...) in `shaders/`, `shaders/world-1/` and `shaders/world1/` only set their defines and include one, and are
  written by `programs.py`.
- `shaders/lib/` holds what the programs share.
- The passes, in order: the air's tables (`setup`, `begin`), the frame's light (`begin2`), the voxel copy from the
  Bridge's block grid (`begin3`); the sun's shadow map (`shadow_*`); the distance field, the light per block and the
  bounce light (`shadowcomp1` to `3`); the world into the G-buffer; the light per pixel and its filter (`deferred`,
  `deferred1`); the clouds (`deferred2`, the Overworld only); the lighting (`deferred3`); water, glass and particles lit as they are drawn; the light in the air
  (`composite`); the DLSS point (`composite1`); the bloom (`composite2` to `12`); the final pass.
- The pack keeps all its data in images, no storage buffers: Iris takes those only at bindings 0 to 8, and Flywheel
  (Create's machines through Colorwheel) binds its own at 0 to 9 every frame. Iris allows 16 custom images; the pack
  uses all 16 (two of them the Bridge's block grid and palette), several of them holding more than one table side by side
  (see `shaders.properties`).
- `tools/compile_check.py` also checks what Iris refuses when it loads the pack: storage buffers, more than 16 images,
  compute dispatch sizes that aren't plain numbers, and images or samplers no `image.` line makes.

The pack is our own code; it contains no third-party code.

## Credits

The moon's surface (`shaders/textures/moon.png`) is made from NASA's CGI Moon Kit (the Lunar Reconnaissance Orbiter's
colour mosaic and elevation map, https://svs.gsfc.nasa.gov/4720/), in the public domain. Credit: NASA's Scientific
Visualization Studio.
