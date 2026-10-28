# Create Galore Shaders

Create Galore's own shader pack for Iris, built for DLSS and FSR from the ground up. Every program writes the
motion vectors, depth and render size that DLSS and FSR need, so they anti-alias the frame (DLAA) or upscale it
without patching anything. Effects are added one at a time, each built to be upscaled and to scale with the CPU.

**Work in progress.** It lives on its own branch, and is not in the modpack yet.

## What it does now

Five presets: **Pure AA** is Minecraft's own look with only DLSS or FSR on top; **Low**, **Default** (what a fresh
install starts on), **High** and **Ultra** light the overworld itself, with more detail on the same lean core the
higher they go, and light the Nether and the End by their own light (see The Nether and the End below). Modded dimensions keep Minecraft's own look in all
of them; no dimension but the overworld ever gets the overworld's sky.

Sky and light (milestone 2, the Lighting option):

- A physically based sky: Sébastien Hillaire's atmosphere as Alpha Piscium fits it to the real air, remade every
  frame, with the sun, the moon's phases and the stars.
- The world lit in real units by the sun or the moon (their colour and strength from the air they shine through),
  the sky all around, and the blocks' light levels. Torches and every modded light light the world through
  Minecraft's light levels, so they work without any list of blocks.
- Shadows from a steady shadow map: not warped, and held on a grid fixed in the world as you move. Options say what
  casts them (mobs, block entities, you, Create's machines and contraptions) and how far they reach.
- Glass reflects the sky; Distant Horizons' terrain is lit the same way past the shadows' reach, and its clouds and
  beacon beams are lit as clouds and glow.
- Exposure metered after DLSS, adapting like eyes (fast to darken, slower to brighten), and AgX tone mapping with
  Alpha Piscium's look after DLSS, at the window's size (see Exposure and colour below for what alpha 4 adds).

Clouds, weather and the End (milestone 3, the Clouds and End Sky options):

- Volumetric cumulus a kilometre up (height, thickness, coverage, density and wind are options), lit by the sun or
  the moon through the air and by the sky around them, with the light they scatter inside themselves. They are
  ray marched at a fraction of the render size, a different point each frame, and built up over frames, so DLSS
  and FSR see a steady image. Cirrus drifts high above them. The cumulus shade the ground as they pass, and from a
  plane or a mountain above them you look down onto their tops. Four quality levels set the cost.
- Weather: rain and thunder close the cloud cover and grey the sky, rain and snow haze the distance where they
  fall, wet ground darkens and reflects the sky, and lightning lights the sky and the clouds.
- The End: a nebula across a starry void, in place of Minecraft's (and BetterEnd's) sky, fading into the End's own
  fog at the horizon (alpha 5 lights the End too, see below).

Light and water (milestone 4, the Coloured Light, Bounce Light and Water Ripples options; all three need Shadows):

- Coloured light: every light lights the world in its own colour, soul lanterns blue, redstone red, sea lanterns
  cyan, and every modded light the colour of its own texture, so they need no list of blocks. The shadow pass
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
  Minecraft's sky light gives it, so closed rooms and caves stay dark. Past the shadows' reach the sun still goes by
  Minecraft's sky light, so a floating island far off keeps its shadow, lit by the sky around it instead of black.
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

Light in the air (alpha 4, the Light in the Air option; the shafts need Shadows):

- The air between you and everything you see catches the light: shafts of sunlight through leaves, windows, cave
  mouths and the gaps between clouds, haze that turns far terrain bluer and paler with distance (thicker at
  sunrise and in the rain), dust a sunbeam shows up in rooms and caves, and a glow in each lamp's own colour around
  it, from the coloured light's volume. It is the sky's own air, so the land fades into the horizon the way the
  sky does, and the shafts carry on against the sky out to the shadow map's reach.
- A volume of cells fixed to the camera, as Unreal's, Frostbite's and Assassin's Creed IV's volumetric fog is:
  each cell works out its light at a point that moves inside it every frame and blends it with what that place in
  the world held last frame, so the volume settles into a still, noise-free image for DLSS and FSR. Every surface,
  the clouds, the water and what lies under it then take the sum of the air in front of them at their own depth.

Materials (alpha 4, the Resource Pack Materials and Parallax options):

- PBR resource packs in LabPBR's format (SPBR, and most others): every pixel of a texture gets its own facing,
  crevices, smoothness and reflectance, the eight measured metals and metals in their own colour, porosity for the
  rain, and emission. Blocks the resource pack has no maps for (Create's, for now) keep the pack's own materials.
  The texture's frame on a surface comes from how its texture coordinates change across the screen (Schüler), so
  any block or mob takes the maps without a list.
- Every surface reflects: a faint, broad sheen at a glancing angle on most things, sharp on polished stone and what
  the maps make smooth, with the sun's glint, less in crevices and corners. A normal map's fine bumps are folded
  into the roughness where they change faster than a pixel (Tokuyoshi and Kaplanyan's specular antialiasing), so
  they don't sparkle under DLSS.
- The texture's own crevices darken the light all around, and shade it from the sun the way Call of Duty WWII's
  micro-shadowing does.
- Parallax: blocks get the relief the height maps hold, followed a texture pixel at a time as if every pixel were a
  column of its own height, so a 16x texture's pixels stand out as sharp-edged steps (with their sides lit and
  shaded as faces of their own) and the sun casts their shadows across them. The motion vectors follow the point
  the relief shows, not the face it's drawn on.

Light from the screen (alpha 4, the Ambient Occlusion and Screen Bounce Light options):

- The ambient occlusion gives everything the screen shows a thickness (Therrien, Levesque and Gilet's visibility
  bitmask over GTAO's slices), so the light all around passes behind a fence post or a lamp post instead of a dark
  halo spreading behind it, and what blocks that light bounces its own onto the surface instead, from last frame's
  picture: a red floor tints the white walls around it, sunlit ground lights the underside of a ledge, at the
  screen's resolution on top of the bounce light's blocks. A lamp's own glow isn't counted again (the block light
  already brings it).
- Every surface's sheen reflects the light all around as it sees it, the way Unreal's Lumen does for rough
  surfaces: the sky where it's open, the blocks' light and the bounce light, and the surfaces close around it where
  they block those. Smooth surfaces (polished stone, quartz, what a resource pack makes smooth) also mirror what the
  screen shows, blurred as wide as their roughness spreads the reflection over the way there, so it stays sharp
  where something meets the floor and blurs further off. Metal has no colour under its sheen: all its light is what
  it reflects, in its own colour.
- The screen's light is blended over about eight frames before DLSS or FSR see it, held within what this frame
  shows around each pixel so it doesn't trail.

Sharper shadows (alpha 4, the Contact Shadows option and the High preset):

- The shadow map's texels stay put on the world as you walk. Iris moves the sun's camera with you in two-block
  steps along the world's axes, which seen from the sun are no whole number of texels, so every shadow's edge was
  drawn a fraction of a texel over at each step and crawled; the pack shifts the sun's view by that fraction, the
  way games snap their shadow cascades to the light's texel grid.
- Contact shadows are now screen-space shadows as Bend Studio built them for Days Gone (Graham Aldridge, SIGGRAPH
  2023): each pixel follows the screen's depth a pixel at a time towards the sun, up to 60 pixels on Ultra, sharp
  for the first few and averaged further out, with no noise for DLSS or FSR to clean up. Grass, a fence's rails and
  a block's edge up close shadow what lies behind them as sharply as the screen shows them. The code is the pack's
  own; the technique is theirs.
- The High preset's shadow map is 4096 texels across (as Ultra's), twice as sharp as before.

Exposure and colour (alpha 4, the Light screen's Local Exposure and Night Vision options, and the Colour screen):

- The exposure meters the light falling on things rather than what they reflect, as Ghost of Tsushima's does: every
  lit surface counts as if it were an 18% grey card, so a snowfield and a basalt cave under the same sun come out
  equally bright (white snow, black basalt) instead of the meter darkening the snow to grey. The sky, water and
  what glows count as they are. Of what it meters, the darkest and brightest tenth are left out (Unreal's
  histogram), so a lamp or a patch of sky in a dark room doesn't drag the rest down.
- Night vision: in dim light the eye's rods take over from its cones (the Purkinje shift), so moonlit land loses
  its colour and turns a cool blue-grey, red goes darker than green and blue, and a lamp's light keeps its colour
  while the dark around it doesn't. Each pixel goes over by its own brightness, by Hunt's rod sensitivity as
  Krawczyk, Myszkowski and Seidel's tone mapping uses it (2005: half at 0.04 cd/m², a tenth at 0.36), with the
  rods' weights worked out from CIE 1951's scotopic curve (`tools/rod_weights.py`). The moon and the stars are
  drawn brighter than real ones so nights can be seen by; the part of a pixel's light they give (by its light
  levels: the sky's against the blocks') counts at its real brightness.
- Local exposure: a bright window or cave mouth in a darker room is drawn in towards the room's brightness, its
  detail kept, so it doesn't burn out to white while the room stays as dark as it is (Ghost of Tsushima's
  bilateral grid, after Chen, Paris and Durand). Only what is brighter than the frame's average is drawn in; night
  stays night.
- Colour: white balance by colour temperature and tint (the chosen light shown as white, adapted in Bradford's cone
  space), each primary's hue and saturation (as Alpha Piscium has them), and a mixer of hue, saturation and
  lightness for eight bands of hue, as photo editors have. All of them change nothing at their defaults.
- Depth of field (off until switched on, its own screen): what lies nearer or further than the focus blurs as through
  a camera's lens of the chosen focal length and f-number, focused on what the middle of the view shows (moving
  there in about a third of a second, as eyes refocus) or at a set distance. It runs after DLSS or FSR, as NVIDIA's
  DLSS guide places it, and gives the same picture from frame to frame where nothing moves, as nothing after the
  upscaler smooths noise away: at half the window's size, each pixel gathers what spreads over it along a fixed
  spiral, the far blur from behind the focus and the near blur from in front of it, which spreads out over what is
  sharp behind it (as Call of Duty: Advanced Warfare's, Jimenez 2014). The hand stays sharp.

Water, light and sky from the first full comparison (alpha 5, and the Lamp Shape, Moon Size and Milky Way options):

- Water to the horizon: the waves are read at the detail a pixel can hold, the way a texture's mipmaps are, and
  what a pixel can no longer show becomes roughness (as Bruneton, Neyret and Holzschuch's ocean goes from waves to
  normals to a rough surface), so the swell and the sun's glitter reach the horizon and a rough sea seen from above
  shows a broad, broken glitter path instead of a mirror's disc. The sky the water mirrors is averaged over the
  slopes too small to see. Distant Horizons' water takes the same waves, depth colour, Fresnel and glitter, with no
  seam where it begins.
- The shore: the shadow pass marks the top of every water column within 128 blocks, and two passes blur how much
  of the ground around each one isn't open water, closely (for the foam) and widely (for how open the water is).
  Foam gathers in corners and round the far side of a pillar and thins out along a straight wall, laps in and out
  every few seconds, drifts with the waves, is lit by their slope and goes on under the surface as bubbles; a pool
  or a pond gets small waves and open water the full swell; the water against a wall is a little darker, and the
  wall above it is wet where the waves lap.
- Under water: coastal-sea clarity, so a wall ten blocks off is clearly hazier than one close by, and bright shafts
  under the waves moving with them beside the shadows' dark ones. The caustics fade out by 48 blocks off, where
  they would only sparkle on the seagrass.
- Lamps: the coloured light carries the way back to the lamp it came from, so close to a lamp its light falls off
  as a real lamp's does and lights most what faces it (Lamp Shape), and a crate or a pillar between a lamp and a
  wall casts a shadow on it (a march through the volume's blocks from each point to its lamp). Light colours read
  from textures come out deeper, and sea lanterns cyan.
- Rooms: where the sky grid's rays find that a point sees little of the sky (only through a window), its sky light
  may now go darker than Minecraft's, which spreads a window's light through the whole room; out in the open the
  rule that it never does still holds. The exposure aims two stops lower in the dark (rooms, caves, night), so dark
  places look dark and a lamp's pool of light stands out, local exposure keeps more of a bright window's detail,
  and the screen's ambient occlusion reaches further, so a room's corners are darker.
- Bright light keeps its colour: a whole glowing face (glowstone, lava, a sea lantern) is held lower than a flame,
  lava glows by its texture (the bright streaks fully, the crust a little), and bright, deep colours keep their hue
  through the tone curve longer before going white. The bloom spreads less, more in rain and fog. The colour look
  adds less saturation, so oak reads as wood and leaves as foliage.
- The air: past the shadows' reach it is worked out along each pixel's own ray, with the same light as the sky's
  table, so far land fades into the haze as the horizon over it does, smoothly, with no cells showing; the volume's
  slices all go to the air near you, finer for it, and rooms and caves hold more dust, so a window's beam shows.
  Night vision tints only the moon's and the stars' share of a pixel, so a lamp's dim glow in the air goes grey
  rather than blue.
- Trees: leaves vary a little in colour from tree to tree and block to block, inner and lower leaves see less of
  the sky, and seen against the sun the side of a crown away from it glows with what comes through.
- Materials: the sides and floors of the relief's grooves see only the sky through the groove's opening (the height
  field's own occlusion, from how deep a point sits below the pixels around it), so mortar no longer lights up blue;
  far off, the bumps the mipmaps average away become roughness, so the relief's sheen holds up.
- The night sky: the moon is drawn three times its real size (its light stays the real size's), its face and phase
  below white, as an eye adapted to it sees it, with a faint glow; about 15000 stars, twinkling near the horizon;
  and the Milky Way, a faint band split by its dust lanes, which a full moon's sky drowns and a dark night shows.
- Rain falls as short, faint dashes of varied brightness that take the light around them; under heavy rain the
  exposure sits half a stop lower (most of a stop in a thunderstorm) and the distance hazes sooner.
- Clean arrivals: a teleport, a respawn or a sharp turn counts as a camera cut, and the exposure goes straight to
  its new level and the air's history starts afresh, instead of the picture settling over the next second.

The rebuild, build 1 (alpha 5 grew one effect on top of another; the rebuild sets each preset a frame-time budget
and cuts Default to a lean core, the Sky Visibility, Tree Crowns, Shore Foam, Caustics and Rain Wetness options):

- Budgets at 2560 by 1440 with DLAA on an RTX 4080 SUPER, without frame generation: Low 180 fps, Default 110, High
  85, Ultra 65, with the 1% lows above half the average. The Create Galore Bridge's cost meter shows each pass's GPU
  time against them.
- Default drops the screen's bounce light (High and Ultra keep it), the relief (until it stops wrapping), and three
  quarters of the light in the air's cells (about 0.9 million at 1440p; Unreal's volumetric fog has half a million
  at 1080p at its High setting). Reflections, the shadow filter and the ambient occlusion take a step down; High and
  Ultra keep alpha 5's.
- The sky never lights what Minecraft says it can't: where Minecraft's sky light is 0 (a closed room, a cave) no sun,
  sky light, bounced sunlight or sunlit air gets in, whatever the shadow map's seams let through. The sky a point
  sees may still lift Minecraft's sky light under something high, but only where that is above 0.
- The sky's rays no longer pass through solid ground. The shadow pass sees a block only by its faces, and Minecraft
  draws none between two solid blocks, so the sky grid had the rock around a cave a block thick, and a ray that got
  into it could pass through the inside of a hill. A pass now fills in the ground from which way the blocks' faces
  point (`program/sky_fill.csh`): under a face up it goes down to a face down (a cave's roof) or the grid's bottom.
- Slabs, stairs and closed doors and trapdoors stop the bounce light and the coloured light as a wall does, so a
  roof of stairs is a roof; fences, panes and iron bars still let them past, and carpets and pressure plates lie
  under them.
- How much of the sky a point sees is worked out a block at a time in the light volume (`program/sky_visibility.csh`):
  24 fixed rays over the sky from the middle of each block through the sky grid, a sixteenth of the blocks each
  frame, read smoothly between blocks for each surface's facing, as Unity's probe volumes keep the sky's visibility.
  It replaces the three to eight rays per pixel each frame, so it no longer flickers in rooms and costs far less.
- Dim lamp light keeps its colour: the eyes' night view (Night Vision) takes only the moon's and the stars' share of
  the light, so a cave lit by torches no longer fades to a grey-blue as it gets darker.
- A switch for every effect: Sky Visibility, Tree Crowns, Shore Foam, Caustics and Rain Wetness are new options, and
  Bloom or Local Exposure at 0 skips their passes.

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

## The Nether and the End

From alpha 5 the Nether and the End go through the same passes as the overworld (their programs in `world-1/` and
`world1/`), with everything that comes from the sun, the moon and the sky swapped for their own light
(`lib/nether.glsl`, `lib/end.glsl`): the coloured light, the light in the air, the ambient occlusion and the screen's
bounce light, reflections, the bloom, exposure and tone mapping all work there as they do in the overworld. Neither
has sky light (Minecraft's is 0 everywhere in them), so a surface's light all around goes by how open it is, from the
sky visibility's rays: in the Nether those that get 32 blocks away, so a fortress's halls and a tunnel are darker than a
cavern; in the End those that get out from under the islands, so an End city's rooms are dim inside.

- The Nether has no sun or moon. The lava and every lamp light it in their own colours through the light volume. Its
  air is thick (Haze), in the colour of each biome's own fog (dark red in the wastes, teal in the soul sand valley,
  grey ash in the basalt deltas, thicker there and in the forests), glowing faintly by itself and orange over the
  lava seas, and the lamps and the lava glow in it. Far terrain fades into that air instead of Minecraft's short fog,
  which only hides the end of the world. The bloom spreads wider in the thick air. The air over lava shimmers (Heat
  Shimmer), drawn in the water pass before the frame goes to DLSS, so frame generation finds nothing moved after it.
- The End has one still, pale light high above (where Minecraft's fixed noon puts the sun), so every island, pillar
  and tower casts a shadow, and the light bounces off the islands. The void glows a dim violet all around, more from
  below, lighting the islands' undersides. Haze lies between the islands, lit into shafts where the light passes the
  pillars and the islands, and thickens into a mist over the void below; the nebula and the stars lie beyond it.
  Options: Light from Above and Haze.

## Own denoisers and Ray Reconstruction

DLSS and FSR upscale; they don't denoise (NVIDIA's DLSS guide), so the effects that take a few noisy samples per
pixel smooth them first where the upscaler alone would leave them grainy. DLSS Ray Reconstruction is a denoiser
itself and wants the raw samples, so a Ray Reconstruction mode turns these off. Each one is marked `OWN DENOISER`
in its code, so `grep -rn "OWN DENOISER" shaders` lists them all:

- The clouds (`program/clouds_gather.csh`): one ray per few pixels each frame, built up over up to 16 frames with
  Lanczos filters. Ray Reconstruction's guide rules out rays on only some pixels each frame (checkerboarding), so
  the clouds would either trace every pixel in that mode or stay out of it (drawn as the sky behind the world).
- The light in the air (`program/air_volume.csh`): each cell of the volume blends 90% of last frame's. It stays in
  every mode: the volume is a few cells per hundred pixels, read smoothly, which Ray Reconstruction never sees as
  per-pixel noise.
- The light from the screen (`program/deferred.fsh`, `screen_light_settled`): the ambient occlusion and the screen's
  bounce light (`program/screen_light.fsh`), blended over about eight frames, held within the pixels around.

What else changes for Ray Reconstruction:

- Noise Pattern must be white noise (1): its guide rules out screen-space dither patterns.
- The reflections and anything else that reads last frame's picture would read a noisy one: the guide asks for
  that to be denoised first (Ray Reconstruction's own output, or a light TAA pass).

## Layout

- `shaders/program/`: the shared programs. `gbuffers_kind.glsl` says what each world program draws and how vanilla
  shades it; `gbuffers.vsh/fsh` draw the world, `clrwl.*` Colorwheel's, `dh.*` Distant Horizons'. The lit overworld
  adds `air.csh` (the sky's tables), `air_volume.csh` (the light in the air), `frame_globals.csh` (the frame's light),
  `cloud_ambient.csh`, `clouds_march.csh` and `clouds_gather.csh` (the cumulus), `screen_light.fsh` (the ambient
  occlusion and the screen's bounce light), `sky_fill.csh` (the inside of the ground, for the sky's rays),
  `sky_visibility.csh` (the sky each block sees), `deferred.fsh` (the
  lighting), `exposure.csh`,
  `local_exposure.csh`, `dof.csh` (the depth of field), `shadow.*`, `light_bounce.csh` and `light_flood.csh` (the
  coloured and bounce light), `water_sim.csh` (the ripples), `water_body.fsh` (the water, after the translucent
  things) and `bloom.csh`; the End adds `end_sky.fsh` (its sky in Minecraft's own look).
- `shaders/lib/`: render size and jitter (`frame.glsl`), motion vectors (`motion.glsl`), vanilla's lighting and fog
  (`vanilla.glsl`), colour encodings and the options; for the lit overworld the air (`atmosphere.glsl`, and the light
  in it, `air.glsl`), the sky (`sky.glsl`, `celestial.glsl`, `stars.glsl`), the clouds (`clouds.glsl`, `noise.glsl`),
  the G-buffer (`gbuffer.glsl`), the materials (`material.glsl`, `material_maps.glsl`), the lighting (`lighting.glsl`,
  `light_levels.glsl`, `lit.glsl`, `shadow.glsl`, `light_volume.glsl`), the water (`water.glsl`, `water_body.glsl`,
  `medium.glsl`), reflections (`reflection.glsl`), the weather and what rain does (`weather.glsl`, `wet.glsl`), the
  bloom (`bloom.glsl`), the values its passes share (`globals.glsl`), night vision (`night_vision.glsl`), the local
  exposure (`local_exposure.glsl`), the tone mapping (`tonemap.glsl`), the colour controls (`grade.glsl`) and the
  depth of field (`dof.glsl`); the Nether's light and air and the heat shimmer (`nether.glsl`, `heat.glsl`), the
  End's (`end.glsl`) and its sky (`end_sky.glsl`).
- `shaders/textures/`: the clouds' noise textures and phase functions, and the water's wave textures.
- `shaders/composite1.fsh`: the DLSS point, which hands the frame over. `shaders/final.fsh`: the screen.
- The `gbuffers_*`, `clrwl_*` and `dh_*` files in `shaders/` only pick a program and include it.
- `shaders/world0/` (the overworld), `shaders/world-1/` (the Nether) and `shaders/world1/` (the End): each
  dimension's programs. Most are copies that `dimensions.sh` writes (run it after adding or removing a program); the
  overworld's own passes live in `world0/`, and the Nether and the End get copies of them too. Dimensions without a
  folder use the programs in `shaders/`.
- `tools/wave_textures.py`: makes the wave textures from Alpha Piscium's (see License).
- `tools/rod_weights.py`: works out how the eye's rods weigh red, green and blue, for the night vision (needs
  colour-science).
- `tools/bounce_check.py`: runs the bounce light's flood on a few small rooms and compares it with radiosity, to
  check a change to its settings before trying it in game (needs numpy).
- `tools/compile_check.py`: compiles every program in every option variant that changes its code with
  glslangValidator, the way Iris puts it together; `--samplers` also counts each program's samplers, as Iris
  refuses the whole pack when one program wants more texture units than the GPU has (run it after adding one).

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
https://github.com/GameTechDev/XeGTAO), with Therrien, Levesque and Gilet's visibility bitmask (2023) over its
slices; XeGTAO's license is in `THIRD-PARTY-NOTICES.md` too.

The clouds' isotropic multiple scattering is from **HanPi Volume Cloud** by AshenOneArt (MIT, with attribution):
https://github.com/AshenOneArt/HPVolumeCloud. Its license is in `THIRD-PARTY-NOTICES.md`, which ships in the zip.
