# DLAA test kit

A test tool for the Bridge's DLAA (NVIDIA DLSS at full resolution in place of Photon's TAA). It is never part of the
pack: it goes only into a separate test instance.

- `src/`: a small client mod. In the DLAA test world, `/dlaatest run` flies the camera along fixed paths at fixed
  viewpoints and saves screenshots at the same frames for every anti-aliasing mode. The paths count frames, not
  seconds, and the shader pack's clock and frame counter count the same frames during a run, so the waving leaves,
  the exposure and the jitter repeat from mode to mode.
- `compare.html`: the side-by-side viewer. Open it in a browser, pick the run folder
  (`screenshots/dlaa-test/<run>` in the test instance), then compare any two modes with a split slider, side by side,
  flip (Space) or a difference view, zoomed and panned together. The slider at the bottom scrubs through the frames of
  a path (the flick path has 13, the still path 8 in a row).

## Build

```
tools/bridge-mod/build.sh /path/to/server      # once, caches the client jars
tools/dlss-testkit/build.sh /path/to/server    # -> tools/dlss-testkit/build/create-galore-dlss-testkit-1.0.0.jar
```

## Run

1. Copy the jar into the test instance's `mods/` (`dist/` has a built one), and unzip `world/dlaa-test.zip` into its
   `saves/`: the test world (seed 20261024, the scene on the lakeshore near 1029 64 162, terrain generated for render
   distance 12 around it).
2. Pick the "Photon v1.3a + DLAA" shader pack once (the Bridge makes it when `dlaa=true` in
   `config/create_galore_bridge-display.properties`).
3. Open the world and type `/dlaatest run` (or `/dlaatest run photon-taa+dlaa-K` for some modes only). Keep the
   window visible (it needn't have focus) until the chat says the run finished; `/dlaatest stop` ends it early.

Or start the game with `-Dcreate_galore_dlss_testkit.autorun=dlaa-test`: it opens the world, runs, and closes. Adding
`-Dcreate_galore_dlss_testkit.hold=SECONDS` only stands at the first viewpoint that long and closes, so chunks and
Distant Horizons' far terrain exist before the first run.

`config/create_galore_dlss_testkit.json` (written on the first run) lists the modes, viewpoints and paths. A mode is
`{"name", "dlaa", "preset"}` with an optional `"debugView": "motion" | "depth" | "color" | "mask"`, which shows that DLSS input
instead of DLSS (any GPU), and an optional `"jitterSign": "-1,1"` that flips the jitter handed to DLSS (the
`sign-flip-*` modes check which way is right). `"engine": "fsr3"` runs AMD FSR 3.1 in DLSS's place (OpenGL compute,
any GPU, so the cloud rig can run it for real): the `fsr3` mode, its own `fsr3-flip-*` jitter checks and `fsr3-debug`
(`"debugView": "fsr3"`, FSR's own view of its buffers). Modes added to the kit later are added to an older config
file's list. `/dlaatest where` prints the camera for adding a viewpoint. A run puts the Bridge's display settings back
when it ends, and the DLAA pack's own options too.

Upscaling: `"renderScale": 0.5` on a mode turns on Photon's TAAU at that render scale (the kit queues Photon's
`TAAU` and `TAAU_RENDER_SCALE` options and reloads), so DLSS or FSR upscale from it, or with `"dlaa": false`
Photon's own TAAU does, at the same render size (the Bridge rounds it to whole pixels for both). The default modes
compare, at each standard scale (`q` 67%, `bal` 58%, `perf` 50%, `ultra` 33%), `X-taau` (Photon's TAAU), `X-dlss-K`,
`X-dlss-M`, `X-dlss-L` and `X-fsr3`. `"mipBias": -1` adds that to the textures' mip bias while DLSS or FSR runs
(NVIDIA's guide asks for one more mip level of detail than the render scale's own; `perf-dlss-K-bias`,
`perf-fsr3-bias`), and `perf-debug-color` and `perf-debug-motion` show DLSS's render-sized inputs stretched over
the window. `-Dcreate_galore_dlss_testkit.paths=brief` runs only those paths.

Several runs in one game: `-Dcreate_galore_dlss_testkit.runs=F1+F2+U1` plays the config's named runs (`"runs"`: each
with its modes, viewpoints, paths, quick, and `screenshots` and `showHand` for that run) one after another without a
restart, each into its own folder (`<time>-<run>`); the built-in ones are Upscaling 1's (`PC-RUNBOOK.md`).
`-Dcreate_galore_dlss_testkit.byEye=perf-dlss-K` keeps the game open after the last run for looking by eye: the moving
scene at normal speed, the player at its viewpoint, the shaders in that mode. The scene stays in the world when the game
closes; the next game's first run (or `hold`) takes every test scene away before it starts, so one left beside the
shore and canopy viewpoints can't cost a run frames (it cost Presenter 3's first launches 4-11%).

`metrics.py RUN_FOLDER` (Pillow and numpy) prints, per viewpoint and mode, the frame time, the shimmer between the
still path's last 8 frames, the smear left two frames after the flick, and the edge contrast.
`PC-RUNBOOK.md` is the recipe for an RTX PC: round 1 (DLAA) and round 2 (FSR 3 next to DLAA), and milestone 2's
PC session (things that move, rain, fire and the portal).

### Things that move

- The `moving` viewpoint looks at a scene the kit builds over the lake when a run that goes there starts, and takes
  away when the run ends (it sits in air the test world leaves empty, so the world file never changes): creative
  motors turning shafts, cogwheels and a fan, a bearing turning a glued cross of wool, a small Sable ship that turns
  and sways every tick, and entities: a villager, an armor stand in iron armor and a red panda (GeckoLib) walking in
  circles, a dropped sword spinning, a bell ringing and a chest opening and closing. `"showHand": true` shows the hand too (and
  the HUD with it), holding a sword that swings. While the camera is there the world ticks at `sceneTickRate` (20 on a real PC, 1 on slow software
  rendering, so each frame moves about as much as in a normal game); `shipTurnDegreesPerTick` and `shipSwayPerTick`
  set how fast the ship moves. `-Dcreate_galore_dlss_testkit.viewpoints=moving` runs only some viewpoints, and the
  `brief` path (40 frames standing still, the last 8 saved) is enough for a moving scene.
- `"objectMotion": false` on a mode hands DLSS camera motion only, as before milestone 2 (`dlaa-camera`), to see what
  the objects' own motion vectors change.
- `"debugView": "residual"` (`debug-residual`, `debug-residual-camera`) shows how far each pixel is from the frame
  before once it is moved back along its motion vector: black where the motion vectors are right. With Photon it
  compares the surfaces' own colour, as the lit picture has a new dither pattern every frame.
- `"dumpRaw": true` also saves DLSS's raw inputs (colour, motion, depth as `.npy`) for each saved frame of a mode that
  runs the Bridge (and outside the debug views its output and the exposure), and `mvcheck.py RUN_FOLDER` (numpy) checks
  the residual view's: for each pair of frames it moves the earlier one back along the motion vectors scaled by -1, 0,
  0.5, 1 and 2 and prints the difference on the moving pixels, so right motion vectors match best at 1.
  `NAME=X0,Y0,X1,Y1` boxes after the folder add a table each for one thing's pixels. Sub-pixel motion is too small to
  measure on blocky textures: for this check set `packSecondsPerFrame` (how far the shader pack's clock moves per
  frame, 1/60 normally) to 1.0 and the ship faster.
- The `effects` viewpoint looks at a second scene the kit builds over the lake west of the first, for things the
  motion vectors cannot follow: a nether portal, fire on netherrack, a campfire's smoke, a lava pool fed by a lava
  fall, a water fall, and rain (on at full strength while the camera is there, off again when it leaves). The pack's
  wet look fades out slowly after rain, so run this viewpoint on its own (`viewpoints=effects`).
  `ghostcheck.py RUN_FOLDER [NAME=X0,Y0,X1,Y1 ...]` (numpy, Pillow) checks them: with `dumpRaw`, the modes that run DLSS
  or FSR also save its output, and for each pair of frames it measures, where the picture changed in a way the motion
  vectors do not explain, how much of the last frame the output still shows (`kept`, 0 to 1), per box. It also writes
  input and output crops of each box side by side into `RUN_FOLDER/ghostcheck/`. `"showHand": true` shows the hand
  there too, holding a sword still in front of the particles.
- FSR gets two masks from the patched pack for these: where to trust this frame over its history (particles, rain,
  snow and the portal swirl, in red) and where a texture animates under still motion vectors (fire, soul fire and lava,
  in green). `debug-mask` shows them (any GPU). `"reactiveMask": false` on a mode (`fsr3-nomask`) runs FSR without
  them, to compare. DLSS gets no mask: NVIDIA's current models ignore it and its guide says not to send one.
- Outside a run, `/dlaatest scene build` puts the moving scene up at the game's own tick rate to look at, walk around
  and ride the ship (`/dlaatest scene build effects` the effects scene, with rain), `/dlaatest mode NAME` switches the
  Bridge to one of the config's modes on the spot (`photon-taa`, `dlaa-default`, `dlaa-camera`, `fsr3`, ...), and
  `/dlaatest scene remove` takes the scenes away again, also any an earlier game left standing.

Each run folder has `manifest.json`: the modes (with the frame time on the still path and whether DLAA really ran),
the viewpoints, the capture frames of each path, every screenshot, and the timeline (when each part of the run
started and ended).

### Profiling

- `"screenshots": false` saves no screenshots, since saving one holds up its frame.
- `frametimes.py RUN_FOLDER [PRESENTMON.csv] ...` (standard library) puts each frame of a PresentMon capture (taken
  with `--qpc_time_ms`) in the part of the run it fell in. Per viewpoint and mode, it prints the frame time, CPU and
  GPU busy time, and how often the GPU set the pace. Several runs are pooled.
- `cpuprofile.py RUN_FOLDER RECORDING.jfr` splits a Java Flight Recorder recording's render thread samples by mode,
  for the share of the Bridge's DLAA and motion vector code.

`PC-RUNBOOK.md`'s "Profiling 1" is the brief for the PC session.

### Input latency

- The `lat-*` modes switch the Bridge's latency tests (testing only, in the display properties: `reflex` off, markers,
  on or boost, which needs Perfect Presentation; `latePoll`, input read again just before rendering), Sodium's CPU
  render-ahead limit and the frame cap (vsync off). The `latency` path holds still for 600 frames while the Bridge
  measures from the game last reading input to the GPU finishing the frame; `manifest.json` gets those numbers per
  mode and viewpoint, one entry each time the mode played (runs L1 and L2 play each mode twice).
- `latency.py RUN_FOLDER ...` (standard library) prints them as tables, with each mode's change against the first.

`PC-RUNBOOK.md`'s "Reflex 1" is the brief for the PC session.
