# Vector

A shader pack for Iris lit by traced coloured light, written from the ground up for Create Galore. It hands DLSS and
FSR (through the Create Galore Bridge) everything they need, so they anti-alias or upscale it without patching. The
name is for its motion vectors: not only the camera's, but how each moving thing moved (Create's machines and
contraptions, ships, mobs, the hand), which is what lets DLSS and FSR keep them sharp. The Bridge's Upscaling &
Display screen picks the upscaler and the render resolution; the pack has no settings of its own for them.

**Work in progress: alpha 0.8.** It lives on its own branch and is not in the modpack yet. The first Create Galore
pack (`shaderpack/`) stays as a research build; the two share only the DLSS hand-off and the mods' plumbing
(Colorwheel, Distant Horizons), not their lighting.

## What it does

- **A copy of the world around you, fixed to the world.** The Create Galore Bridge keeps a box of blocks around you
  (128 x 96 x 128 by default) from the game's own block data: it reads a chunk when it loads and a block when it
  changes, never what the camera sees. Each block comes with its shape as it is drawn (up to twelve boxes, to the
  16th), its colour and its light. The box moves 8 blocks at a time and each block keeps its place in it, so a ray
  between two points gives the same answer wherever you stand. Plants and flat layers (carpets, snow, barnacles,
  pressure plates) let light through; stained glass tints it, and water dims it red first by how far it goes through
  it, as it does the sun's. Cut-out faces let light through their clear
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
  around. A block's light is traced again only where something within about 24 blocks of it changed (a block
  placed or broken, a lamp lit or put out, a portal in view), and every 64 to 128 frames anyway; a lamp whose light
  can't show on a pixel is skipped there.
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
  their clump, and grass doesn't shade itself. Past the map, out to about 1000 blocks and in Distant Horizons'
  terrain too, mountains shade mountains and forests their floors: far sun shadows from the ground's height, one per
  4 x 4 blocks, worked out a slice per frame and kept, softer further off; a far ridge also shades the valley you
  stand in at sunset. The map's last quarter fades into them (and into what Minecraft's sky light says), so its edge
  never shows as a line. The heights come from Distant Horizons through the Bridge 1.175.0 (pack 0.6.65) or later,
  which reads only what changed; without them the map fades into the sky light alone.
- **The sky**: a physically based atmosphere, pushed to the look we follow: a deep blue overhead by day paling to a
  light blue horizon that never burns out to white, an orange glow round the setting sun and a pink belt across from
  it, a deep blue night that is no darker at dusk and dawn (when the moon is still low) than at midnight. Under the
  horizon, past the edge of the land that is drawn, the sky goes on in the horizon's own colour, so no dark band shows
  there. Stars by the thousand in their own colours, steady (no twinkle) and at least a pixel and a half wide so the
  upscaler keeps them: the brightest come out first at dusk, and a bright moon hides the faint ones and the Milky Way,
  which has dark dust lanes along it. The sun, round, with a tight glow. The moon about twice Minecraft's size
  (option Moon Size), its surface from NASA's maps of the Moon: its seas and craters, lit from the side its phase
  says so the craters stand out along the line between light and dark, a cool silver (about a tenth bluer than red,
  as the sun is a warm white), with the Earth's faint light on its dark part and a halo of the moonlight the air
  scatters, half as bright about ten degrees out. The sun and the moon set behind the ground, also where it isn't
  drawn yet. The far haze takes the sky's colour. Sunlight on the land turns warmer at
  dawn and dusk. Rain closes the sky into an even grey, a little brighter overhead than low down, thickens the far
  haze, and hides the sun, the moon and the stars.
- **Clouds**: a layer of real clouds drawn as a volume, their bases gathering round Y 230 (option Cloud Height),
  always above the pollution's smog, so they never hide what goes on below them. The bases rise and fall over a
  kilometre or two and from cloud to cloud (15 blocks under the layer's height to 20 over it), so the layer never
  reads as one flat floor. A slow weather map says where the sky is clear blue, scattered, broken or nearly closed,
  and what kind of cloud is there: flat stratocumulus sheets 20 to 30 blocks deep with gaps, fair-weather cumulus 45
  to 70 tall, or big cumulus towering up to 150 where the cover is thick; rain closes it into one grey deck. They
  gather in groups with clear sky between rather than spreading out evenly. Their shapes are heaps of round lumps
  from a baked noise volume: a cloud is where that noise is over a level which rises with height, slowly at first and
  then faster, so a cumulus stands on a wide flat base and narrows into a dome or a tower, and a sheet keeps its
  width up to a flat top. A cloud thickens inwards from its outline (soft, see-through edges, a solid heart), and
  round billows are worn into every side of it (cauliflower), finer up close. Far above, thin streaks and soft
  ripples (cirrus and cirrocumulus) in places. Lit by the sun through the cloud between, the light scattered again
  and again inside: a top in the sun is white, its shaded side and the folds between its billows grey, the base of a
  thick cloud dark, a thin edge towards the sun silver, and the sun burns through thin cloud. The sky lights them
  from above and the ground from below; gold and pink at dusk and dawn, moonlit at night; they fade into the sky's
  colour far off and reach 5000 blocks. Seen from above, they stay in front of the land's haze, the water and glass.
  Each pixel's look crosses the layer in even steps that lengthen with distance, from a start that moves a little
  each frame; detail too fine to show at a distance gives way to its average, so nothing far off flickers. The
  game's clock moves them (they don't jump, they are where they were when you reload, and with the daylight cycle
  stopped they stop too) at about a block a second (option Cloud Speed), the wind's direction wandering over days.
  Every pixel is worked out every frame at half the render size each way and smoothed over a few frames, never a
  fraction of the pixels per frame (which makes clouds crawl in motion); where last frame's can't be kept (a cloud's
  edge on the move, the sky a turn just brought in) the looks round the pixel stand in for it, so no pattern shows
  while you turn; DLSS gets their own motion. Their shadows cross the land and the sea (option Cloud Shadows) and
  hold still while you climb or drop. Option Realistic Clouds off gives Minecraft's clouds, lit orange and pink at
  dusk and dawn.
- **Water** (its own options screen):
  - **Waves**: a wind sea in seven sizes, from a swell with crests about 14 blocks apart down to ripples a fifth of
    a block apart, each half the size of the one before. Every size is the same baked pattern of real wind waves
    (running down the wind and about 45 degrees either side of it), laid down at its own scale and swung at its own
    rate, so crests travel, catch each other up and dissolve instead of sliding by as a fixed picture. Their slopes
    are those of a real sea in a fresh breeze (about 7 degrees on open water), so the surface has relief you can
    see, the sky in it is broken by the waves and the sun lays a path of glitter on it. The wind is the clouds'
    wind. The longest waves only build where there is open water up-wind (a pond stays rippled but calm, a sea
    rolls), and stretches hundreds of blocks across run calmer or choppier. The pattern repeats every 1024 blocks
    and every hour exactly, so it never jumps. Waves too fine for a pixel to show become roughness that spreads the
    sun's glint and the reflection instead of flickering. Rain roughens the small waves and drops rings on open
    water (option Rain Rings); walking into water sends a ring out from you.
  - **Wakes**: you, other players, mobs, boats and Sable's ships push the water aside as they go, so ripples spread
    from them and trail a V behind whatever moves, narrower the faster it goes; a still one leaves the water flat.
    A ship's wake grows with the water its hull pushes aside and its band with its draft, so a laden hull throws a
    big wake and a skiff a small one, and a pointed bow or stern cuts the water to a tip. Worked out 64 blocks round
    you, fixed to the world, so a wake stays where it was made. The Bridge 1.175.0 (pack 0.6.65) or later lists what
    moves through the water, and 1.176.0 (pack 0.6.66) or later how big and heavy a ship is in it; with an older one
    there are no wakes, or ships' are a mob's.
  - **Reflections** (option Water Reflections): Low the sky and its clouds; Medium what is on screen, then the sky;
    High the world itself traced through the block copy with its blocks' textures (the Bridge hands over the block
    atlas and each block's face maps), lit as the world is lit, so the reflection is the same from wherever you look
    and holds what is off screen; what the copy doesn't hold as solid blocks (mobs, Create's machines, Sable's ships,
    plants, lamps) comes from the screen where it is nearer. One look per pixel at every quality; Ultra
    reaches further. The sky and clouds water mirrors come from a map of the cloud layer as it looks from sea level
    (256 directions round, 96 up, finer towards the horizon), worked out a part each frame; each mirrored look
    reads it towards where it meets the clouds, so every stretch of water mirrors the clouds over it, they hold
    still on it as you climb or drop, and nothing in it flickers; rough and far water read a soft copy. Seen from
    under the surface, a reflection reaches only as far as the water lets light through (48 blocks in all). The textures need the Bridge 1.174.0 (pack 0.6.64) or later; with an older one a
    reflected block shows its average colour.
  - **Colour**: light is absorbed red first, so water deepens from clear turquoise over sand to blue, tinted by the
    biome's water colour; a floor fades into the water's own colour within about 15 blocks of water, and deep water
    is a clear blue body in the sun rather than black. With a low sun in front of you, the faces of the waves turned
    your way glow with the light coming through them. What lies under the water is bent by the waves (option
    Refraction), never pulling nearer things in. Seen from under the surface, the sky shows only through Snell's
    window and the rest is a mirror.
  - **Caustics**: the waves focus sunlight into a moving net of bright lines on what lies under them (option
    Caustics): for each spot the light is followed back up to where it came through the surface, and how much the
    waves gather or spread it there comes from the surface's own curvature (baked beside the waves), so the lines
    are as smooth as the waves and hold still from frame to frame. A fine net in the shallows, wider and softer
    with depth as the small ripples blur out; the brightest lines roll off softly and the net loses its contrast
    with depth. Once per 2 x 2 pixels. The sun reaches under water along the water in the block copy, so a dock's
    shade stays on the floor under it.
  - **Falling water**: the side of a fall or a stream's edge is white water, pale in the water's own colour with the
    game's own streaks running down it, as thick as the fall is (what stands far behind a thin fall doesn't make
    it a deep one, and a sea behind it shows as sea); a side behind a pane (the sea through a window, a tank) stays
    clear.
  - **Under water**: the water's own fog, light shafts from the sun shaded by what stands over it (option Light
    Shafts), and the surface seen from below.
  - Distant Horizons' water uses the same waves, colour, floor and sky with clouds, so a sea looks the same across
    the edge of the loaded chunks, and takes over from Minecraft's pixel by pixel over the last 16 blocks before it
    (both judge a pixel by the same spot on the water, so none is left out by both).
- **Light in the air**: a coloured haze around lamps (strongest at night, underground and in the Nether), haze far
  off (as thick as the air along the look, so from high up the land below is hazed no more than it should be), water
  and lava around you.
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

Foam; material maps and block reflections (reusing the water's reflection
trace); soft sun shadows traced through the copy; moving Create contraptions and ships in the copy, and mobs and the player shading lamp light (now they only
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
    python3 shaderpack2/tools/cloud_noise.py # remakes the clouds' noise and shapes (shaders/textures/cloud_noise.dat, cloud_shape.dat)
    python3 shaderpack2/tools/wave_noise.py  # remakes the waves and their curvature (shaders/textures/water_waves.dat, water_curve.dat)
    python3 shaderpack2/tools/moon_map.py    # remakes the moon's surface (shaders/textures/moon.png) from NASA's maps
    python3 shaderpack2/tools/light_tester/light_tester.py SCENE.py OUT.png  # the block light without Minecraft

`tools/light_tester/` runs the pack's own block light programs (the voxel copy, the distance field, the light per
block and the light per pixel) on a scene of blocks written in Python, in a stand-in for Iris, and draws it at any
resolution in under a minute on the CPU: for problems too fine or too slow to chase in the game. See the script's
notes for its options; `scene_leaks.py` and `scene_regress.py` are examples.

`tools/view_tester/view_tester.py` draws the sky, the clouds and the open sea the same way (the pack's own sky,
cloud and water code in a stand-in for Iris) in a second or two a view, at any time of day, height and weather:
`python3 shaderpack2/tools/view_tester/view_tester.py OUT.png name:time,yaw,pitch[,height[,rain[,fov[,day]]]]`;
`ROOT=` an older copy of `shaders/` draws the same views with it, for a before and after.

`tools/rig/` runs the real game without a screen or a graphics card (a slim client under Xvfb, drawn on the CPU at
about a frame a second, so slow but exact): `setup.sh` once, `launch.sh WORLD`, `sync.sh` to copy the pack in
(`FREEZE=` holds the water's clock for stills), `view.sh OUT.png X Y Z YAW PITCH` for a picture from a spot,
`motion.sh` for pictures while the camera turns or flies, `cmd.sh` and `key.sh` for commands and keys. `setup.sh`
copies the modpack's own settings for every mod (`pack/config`), so the mods behave as they do in the pack: on its
own defaults Winds & Weather draws local rain the pack has switched off. Weather for a picture: `aeroweather set
TYPE INTENSITY TICKS RADIUS` holds the mod's weather round the player (`aeroweather query` reads it back), and
`weather clear` stops the mod's rain regions from starting the game's rain for as long as it runs. The Bridge's
cost meter (F10) works in it, so a change's cost per pass can be compared before and after. The bad frame mark
(`lib/bad_frame.glsl`, a small square at the middle of the left edge) counts frames, so at the rig's pace it stays
up for about two and a half minutes: look for it in every picture, a red one means a pass wrote an infinite or
not-a-number value into the lit scene.

`tools/gpu_stats.py` says what a graphics card's compiler makes of each compute program (registers, spills,
instructions), without a card.

## How it is laid out

- `shaders/program/` holds every program; the files Iris looks for by name (`gbuffers_terrain.fsh`, `composite1.fsh`,
  ...) in `shaders/`, `shaders/world-1/` and `shaders/world1/` only set their defines and include one, and are
  written by `programs.py`.
- `shaders/lib/` holds what the programs share.
- The passes, in order: the air's tables (`setup`, `begin`), the frame's light (`begin2`), the voxel copy from the
  Bridge's block grid (`begin3`), the wakes (`begin4`), the far sun shadows (`begin5`); the sun's shadow map
  (`shadow_*`); the distance field, the light per block and the
  bounce light (`shadowcomp1` to `3`); the world into the G-buffer; the light per pixel and its filter (`deferred`,
  `deferred1`); the caustics (`deferred2`); the clouds (`deferred3`, the Overworld only); the lighting (`deferred4`); water, glass and particles lit as they are drawn; the water's
  reflection and the sun's shafts in it (`composite_a`, `composite_b`); the light in the air (`composite1`); the DLSS
  point (`composite2`); the bloom (`composite3` to `13`); the final pass.
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
