# DLAA rounds 1 and 2 on the PC: runbook

For the Claude Code session on Emre's PC (a local session he opens in the test folder, or Remote Control). Emre
approved the plan in the "DLAA round 1: PC test plan" doc and the tools in this runbook on 2026-10-24; this file is
the how. Rules that come before anything here:

- Work only inside `C:\Users\coolm\Documents\Claude\MC-CREATE` (the test folder). Never open, read or change his
  Prism instance, pack install, worlds, server files or anything else outside that folder. Nothing installed
  system-wide, no PATH or registry changes.
- No need to ask before each download, install or launch in this runbook. Ask Emre before any tool or step that is
  not in it.
- The game logs in offline as `CGTester`; never use his accounts.
- Nothing is merged or released; results go to the DLAA branch only (`claude/project-thread-u2wmhh`).

PowerShell, in the test folder: `$T = "C:\Users\coolm\Documents\Claude\MC-CREATE"`.

## 1. Tools

```powershell
New-Item -ItemType Directory $T\tools, $T\mc, $T\instance -Force
git clone --branch claude/project-thread-u2wmhh --single-branch https://github.com/emresahin0906-code/Create-Galore.git $T\repo
# Java 21 (Temurin JDK zip) -> $T\tools\jdk21
Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $T\tools\jdk21.zip
Expand-Archive $T\tools\jdk21.zip $T\tools\jdk21-unpacked; Move-Item (Get-ChildItem $T\tools\jdk21-unpacked)[0].FullName $T\tools\jdk21
# uv (standalone, brings its own Python) -> $T\tools\uv
Invoke-WebRequest "https://github.com/astral-sh/uv/releases/latest/download/uv-x86_64-pc-windows-msvc.zip" -OutFile $T\tools\uv.zip
Expand-Archive $T\tools\uv.zip $T\tools\uv
$env:UV_CACHE_DIR = "$T\tools\uv-cache"; $env:UV_TOOL_DIR = "$T\tools\uv-tools"; $env:UV_PYTHON_INSTALL_DIR = "$T\tools\uv-python"
```

Set those three `UV_*` variables in every new shell (they keep uv's files in the test folder).

## 2. Game and pack

```powershell
$java = "$T\tools\jdk21\bin\java.exe"
# Installs Minecraft 1.21.1 + NeoForge 21.1.262 under $T\mc, without starting it (--dry).
& $T\tools\uv\uv.exe tool run --python 3.13 portablemc@5.0.6 --main-dir $T\mc start --dry --jvm $java --mc-dir $T\instance neoforge::21.1.262
# The pack from the DLAA branch, client side, into $T\instance.
Invoke-WebRequest "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar" -OutFile $T\instance\packwiz-installer-bootstrap.jar
Push-Location $T\instance
& $java -jar packwiz-installer-bootstrap.jar -g -s client "https://raw.githubusercontent.com/emresahin0906-code/Create-Galore/claude/project-thread-u2wmhh/pack/pack.toml"
Pop-Location
```

The pack no longer downloads `nvngx_dlss.dll` (players bring their own, since pack 0.4.80), and the installer
deletes the copy an earlier round got from it. Put NVIDIA's own copy back (the DLSS SDK the Bridge was tested with,
310.9.16) if `$T\instance\create-galore-dlss\nvngx_dlss.dll` is missing:

```powershell
New-Item -ItemType Directory -Force $T\instance\create-galore-dlss | Out-Null
Invoke-WebRequest "https://raw.githubusercontent.com/NVIDIA/DLSS/9981c6350dc58c0ce21f024fd65e31e851c31689/lib/Windows_x86_64/rel/nvngx_dlss.dll" -OutFile $T\instance\create-galore-dlss\nvngx_dlss.dll
```

Check: `$T\instance\create-galore-dlss\nvngx_dlss.dll` exists, and `$T\instance\mods` has `create-galore-bridge-*.jar`.
If the installer stops on a CurseForge-only file, take it from the repo's `pack/` folder (same as a normal install).

## 3. Test kit, world, settings

```powershell
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
Expand-Archive $T\repo\tools\dlss-testkit\world\dlaa-test.zip $T\instance\saves   # -> saves\dlaa-test
```

If `$T\instance\saves\dlaa-test` is already there from an earlier round, keep it: it has the far terrain (Distant
Horizons' database, about 230 MB for radius 64), so the hold in step 4 can be skipped.

Ask Emre to check that the NVIDIA App's DLSS override is off (Graphics, Global settings, DLSS Override - Model
Presets). It is his setting, outside the test folder, so he changes it, not the session. With it on, NGX uses the
driver's own DLSS and its forced preset for every mode, and the developer DLL in run E is never loaded. Check after
the first launch: `$T\instance\create-galore-dlss\nvsdk_ngx.log` has no `Feature dlss override enabled`.

Before the first launch (create a file if it isn't there; the game adds the keys it misses):

- `$T\instance\config\create_galore_bridge-display.properties`: `dlaa=true`, `perfectPresentation=false`. (`dlaa=true`
  still means DLSS, with `dlaaEngine` picking FSR 3 for the test kit's FSR modes; players pick auto, dlss, fsr3 or off.)
- `$T\instance\config\iris.properties`: `enableShaders=true`, `shaderPack=Photon v1.3a + DLAA`. (The copy is made
  on the first launch; Iris takes it from then on.)
- `$T\instance\options.txt` (the pack installs it): set or add `renderDistance:12`, `pauseOnLostFocus:false`,
  `fullscreen:false`, `tutorialStep:none` (the "Move with W, A, S and D" popup is drawn even with the HUD hidden and
  would sit in the corner of every shot, since the camera never moves by key).
- `$T\instance\config\fml.toml`: `earlyWindowControl = false`. Perfect Presentation needs NeoForge's early loading
  window off: with it on, the first start with Perfect Presentation only turns it off and has to be closed and
  started again. It also lets the window keep the full `--resolution` (with the early window, it is fitted to the
  screen, 2560x1421 in round 1), so every run has the same picture size.
- `$T\instance\config\DistantHorizons.toml` (the pack installs it): `lodChunkRenderDistanceRadius = 64` in place of
  the pack's 256 (Emre's call, 2026-10-24: far terrain still in every shot, built in minutes instead of hours; the
  setting is the player's choice in game anyway). Every run and mode uses the same 64. Also `numberOfThreads = 32`
  under `[common.multiThreading]` in place of the pack's 2, so the far terrain builds on all of the 9950X3D's
  threads (test folder only; it changes how fast it builds, not what it shows).
- Window: Emre's monitor size if smaller than 2560x1440, else `--resolution 2560x1440`. Ask him or read it with
  `Get-CimInstance Win32_VideoController | Select CurrentHorizontalResolution, CurrentVerticalResolution`.

## 4. First launch (checks only)

```powershell
& $T\tools\uv\uv.exe tool run --python 3.13 portablemc@5.0.6 --main-dir $T\mc start --jvm $java --mc-dir $T\instance `
  -u CGTester --resolution 2560x1440 --jvm-arg=-Xmx10G --jvm-arg=-Xms4G neoforge::21.1.262
```

Add `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test --jvm-arg=-Dcreate_galore_dlss_testkit.hold=900`: the
game opens the test world, flies at the first viewpoint for 15 minutes and closes. That makes the chunks and
Distant Horizons' far terrain (radius 64, see step 3) once, so every mode later sees the same distance. From scratch
it takes about 15 to 20 minutes with those settings. If the Distant Horizons database in the world folder
(`DistantHorizons.sqlite` under `saves\dlaa-test`) still grew in the last minutes, hold again; if it stopped growing
early, a shorter hold is fine next time. Then
`$T\instance\logs\latest.log` must have:

- `DLAA: made shaderpacks/Photon v1.3a + DLAA from photon_v1.3a + Clrwl_1.0.5.zip` (on this or the launch before)
- `DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device)`
- `DLAA: DLSS running at WIDTHxHEIGHT, preset default`

and no `DLAA: stopped`, `DLAA: not available` or `Failed to create shader rendering pipeline`. If anything differs,
stop and report the DLAA lines and the end of `$T\instance\create-galore-dlss\nvsdk_ngx.log` (NGX's own log).

## 5. The runs

Each run: add the JVM arguments below to the launch command from step 4. The game opens the world, captures and
closes itself. Keep its window visible; it does not need focus.

| Run | Before it | Extra JVM arguments |
| --- | --- | --- |
| A | `perfectPresentation=false` | `-Dcreate_galore_dlss_testkit.autorun=dlaa-test` `-Dcreate_galore_dlss_testkit.modes=photon-taa+dlaa-default+dlaa-K+dlaa-L+dlaa-M` |
| B | same | `...autorun=dlaa-test` `...modes=dlaa-default+sign-flip-x+sign-flip-y+sign-flip-xy` `-Dcreate_galore_dlss_testkit.quick=true` |
| C | same | `...autorun=dlaa-test` `...modes=debug-motion+debug-depth+debug-color` |
| D | `perfectPresentation=true` | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+dlaa-K` |
| E | `perfectPresentation=false`; the DLSS developer DLL in `$T\dlss-dev\nvngx_dlss.dll` | `...autorun=dlaa-test` `...modes=dlaa-default` `...quick=true` `-Dcreate_galore_bridge.dlss.dllDir=$T\dlss-dev` |

Write each argument as `--jvm-arg=-D...=...` (`...` = `create_galore_dlss_testkit`). The modes are the test kit's
defaults, listed in `$T\instance\config\create_galore_dlss_testkit.json` after the first run.

Run D: the log must say `(Perfect Presentation's DirectX device)` on the NGX ready line. Run E: the developer DLL of
the same release (310.9.16) draws NVIDIA's watermark and debug text over the picture:

```powershell
New-Item -ItemType Directory $T\dlss-dev -Force
Invoke-WebRequest "https://raw.githubusercontent.com/NVIDIA/DLSS/9981c6350dc58c0ce21f024fd65e31e851c31689/lib/Windows_x86_64/dev/nvngx_dlss.dll" -OutFile $T\dlss-dev\nvngx_dlss.dll
```

## 6. Results

For each run folder in `$T\instance\screenshots\dlaa-test\`:

```powershell
& $T\tools\uv\uv.exe run --python 3.13 --with pillow --with numpy $T\repo\tools\dlss-testkit\metrics.py <run folder>
```

Write `$T\repo\tools\dlss-testkit\results\REPORT.md`: the metrics tables, each run's `outcome` and the modes with
`"dlaaActive": false` from `manifest.json`, every `DLAA:` log line, any NGX warnings, and anything that looked wrong.
Add a few crops (about 512x512, PNG) of leaves, glass and fence edges, same spot in every mode, under
`results\<run>\`: a few MB in all, never whole screenshots. Commit them on the DLAA branch and push. If pushing
isn't possible from this PC, tell Emre, so he can paste `REPORT.md` into the DLAA thread. Emre looks at the full
screenshots with `$T\repo\tools\dlss-testkit\compare.html`.

## Round 2: FSR 3 next to DLAA

Same PC, same test folder, same tools as round 1, nothing new to install. Start it only when Emre says so. The
Bridge can run AMD FSR 3.1 (OpenGL compute, any GPU) in DLSS's place, as a test engine; round 2 measures it on the
4080 next to DLAA and Photon's TAA. It is only a test of how good FSR 3 looks here: never assume it matches DLSS, and
report what the numbers and crops show.

### Update

Keep `$T\instance` and its `saves\dlaa-test` as they are (they have round 1's far terrain, so no hold is needed).
Only the two jars change:

```powershell
git -C $T\repo pull
Copy-Item $T\repo\pack\mods\create-galore-bridge-1.89.0.jar $T\instance\mods\ -Force
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
```

Check before the runs: `config\create_galore_bridge-display.properties` has `dlaa=true` and
`perfectPresentation=false`, `config\fml.toml` has `earlyWindowControl = false`, `options.txt` has
`tutorialStep:none`, the Distant Horizons settings from step 3 are unchanged, and the NVIDIA App's DLSS override is
still off. The test kit sets `dlaaEngine` itself for each mode and puts the settings back after each run. Its config
file from round 1 gets the `fsr3*` modes added on the first run.

### Runs

The launch command from step 4 with these JVM arguments (written as in step 5):

| Run | Before it | Extra JVM arguments |
| --- | --- | --- |
| F | `perfectPresentation=false` | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+fsr3` |
| G | same | `...autorun=dlaa-test` `...modes=fsr3+fsr3-flip-x+fsr3-flip-y+fsr3-flip-xy` `...quick=true` |
| H | same | `...autorun=dlaa-test` `...modes=fsr3-debug` `...quick=true` |
| I | `perfectPresentation=true` | `...autorun=dlaa-test` `...modes=dlaa-default+fsr3` |

The log of each run with an `fsr3` mode must have:

- `DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)`
- `DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER...` (the GPU's OpenGL name)
- `DLAA: FSR 3.1 running at WIDTHxHEIGHT`

and no `DLAA: FSR 3 not available`, `DLAA: stopped` or `did not compile`. FSR's shaders were only ever compiled by
Mesa in the cloud, so NVIDIA's compiler is new to them: if a pass fails, copy the whole `DLAA:` line (it carries the
compiler's message) and stop there. G repeats the cloud's jitter-sign check on real hardware (the cloud found the
unflipped sign steadiest, as with DLSS). H is FSR's own debug view of its inputs and buffers, to compare with the
cloud's. In I, FSR runs in OpenGL as before, with Perfect Presentation showing the frames.

Also check that the game no longer sets DLSS or FSR up again while quitting: after the last `DLAA: ... running` line
of a run, no new `ready` line before `Stopping!`.

### Results

`metrics.py` for each run folder, as in step 6. Write `$T\repo\tools\dlss-testkit\results\round2\REPORT.md`
(metrics tables, `DLAA:` lines, the frame time of `fsr3` against `dlaa-default` and `photon-taa`, anything that
looked wrong) and crops of the same spots as round 1 (`shore-fence-glass`, `glass-panes`, `canopy-leaves`, about
512x512, from the settled still frame) for `photon-taa`, `dlaa-default` and `fsr3` under `results\round2\F\`, a
few MB in all. Commit and push on the DLAA branch. Emre judges the look himself in `compare.html`.


## Milestone 2, PC session: things that move

One session in place of the two runs planned before (Emre, 2026-10-24). Same PC, test folder and tools as rounds 1
and 2, nothing new to install. Start it only when Emre says so. Photon's copy now gives DLSS the motion of things
that move by themselves:

- waving leaves and grass;
- Sable ships;
- Create's machines, contraptions and trains (through Colorwheel);
- what the game draws vertex by vertex: mobs and players with their armor and held items, GeckoLib models, dropped
  items, block entities (chest lids, bells) and the first-person hand. The Bridge adds an attribute with each
  vertex's position last frame to Iris's entity vertex format.

Some things have no motion that the motion vectors could follow: particles, rain and snow, fire, lava and the
portal's swirl. DLSS gets nothing extra for them. NVIDIA's current models ignore the "bias current colour" mask, and
NVIDIA's guide says not to send one. FSR gets two masks from the patched pack. The red one marks where to trust this
frame over FSR's history (particles, rain, the portal). The green one marks where a texture animates under still
motion vectors (fire and lava).

The cloud checked that the motion vectors are exact, and that the masks cut FSR's smear on fire, lava and the
portal. This session checks all of it on the 4080 with NVIDIA's shader compiler and real DLSS, and Emre judges the
look by eye. Is DLSS without masks as clean as FSR with them?

**Frame times don't count in this session.** Emre may host his game server meanwhile, pinned to the CCD without the 3D
V-Cache, with the test game on the X3D CCD. That's fine for pictures, but frame times get noisy. CPU costs, such as
what working out the entities' motion costs, are measured later in a profiling pass on a quiet PC. Note in the
report whether the server ran.

### Update

Keep `$T\instance` and `saves\dlaa-test` (the far terrain stays). Swap the two jars and delete the test kit's config,
so it is written fresh with the new viewpoints and modes:

```powershell
git -C $T\repo pull
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-1.91.0.jar $T\instance\mods\ -Force
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
Remove-Item $T\instance\config\create_galore_dlss_testkit.json
```

Check before the runs, as in round 2:

- `dlaa=true` and `perfectPresentation=false` in `config\create_galore_bridge-display.properties`;
- `earlyWindowControl = false` and `tutorialStep:none`;
- the Distant Horizons settings from step 3;
- the NVIDIA App's DLSS override still off.

The first launch remakes the Photon copy (the patch changed): its log has `DLAA: made shaderpacks/Photon v1.3a +
DLAA from photon_v1.3a + Clrwl_1.0.5.zip`. Every run's log must have none of these:

- `Could not compile shader` or `Failed to create shader rendering pipeline`;
- `this Colorwheel's shaders are not the ones the Bridge knows`;
- `Missing elements in vertex` (a crash);
- `DLAA: entity motion attribute at` (a warning).

The chat must not show `[Colorwheel] Current shader pack contains broken shaders`. If any of these shows, stop and
report the lines around it.

The test kit builds two scenes over the lake when a run that goes to their viewpoint starts, and removes them when
the run ends, so the world file never changes:

- `moving`, looking out from the shore:
  - a creative motor turning shafts, a cogwheel, a large cogwheel and a fan;
  - a second motor turning a bearing with a glued cross;
  - a small Sable ship that turns and sways;
  - in front, a villager and an armor stand in iron armor walking in circles, and a red panda (Critters and
    Companions) walking a circle at the front left;
  - a diamond sword spinning in the air, a bell that keeps ringing, and a chest that keeps opening and closing.
- `effects`, west of it:
  - a nether portal;
  - fire on netherrack;
  - a campfire;
  - a lava pool fed by a lava fall;
  - a water fall into the lake;
  - rain at full strength from the first frame while the camera is there. The pack's wet look fades out slowly
    after rain, so the effects viewpoint gets a run of its own.

The test world is peaceful, so the armor is on an armor stand.

### Runs

The launch command from step 4, with these JVM arguments (written as in step 5):

| Run | Before it | Extra JVM arguments |
| --- | --- | --- |
| J | `perfectPresentation=false` | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+dlaa-camera+fsr3` `-Dcreate_galore_dlss_testkit.viewpoints=moving` |
| K | config edit 1 | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+fsr3+fsr3-nomask+debug-mask` `...viewpoints=effects` |
| L | config edit 2 | `...autorun=dlaa-test` `...modes=debug-motion+debug-residual` `...viewpoints=canopy+moving` |
| M | config edit 3 | `...autorun=dlaa-test` `...modes=debug-motion+debug-residual` `...viewpoints=moving` |
| N | config edit 4 | `...autorun=dlaa-test` `...modes=debug-residual` `...viewpoints=moving` |

The modes:

- `dlaa-camera` is DLAA with only the camera's motion, as before this milestone. J shows what the new motion vectors
  change.
- `fsr3-nomask` is FSR without its masks.
- `debug-mask` shows the masks themselves: red is reactive, green is composition.

The config edits go into `config\create_galore_dlss_testkit.json`, which run J writes:

1. Before K: `"paths": ["brief"]` and `"dumpRaw": true`. K saves DLSS's and FSR's inputs and outputs for the last 8
   frames (`MODE\raw\*.npy`, about 0.6 GB per mode at 2560x1440).
2. Before L: also `"packSecondsPerFrame": 1.0`, `"shipTurnDegreesPerTick": 20.0` and `"shipSwayPerTick": 0.5`. Leaves
   and the ship move fast, so each frame moves them by a few pixels.
3. Before M: `"shipTurnDegreesPerTick": 0.0` and `"shipSwayPerTick": 0.0`. The ship stands still, so the entities are
   easier to tell apart. At 20 ticks a second and 2560x1440, the walkers still move a few pixels per frame.
4. Before N: `"showHand": true`. The hand shows, with the HUD, holding a sword, and swings.

After N, delete the config file again.

### By eye, with Emre (about 25 minutes)

Launch without the `autorun` argument and open the `dlaa-test` world. Switch anti-aliasing with these commands (the
shaders reload, which takes a few seconds):

- `/dlaatest mode dlaa-default`
- `/dlaatest mode dlaa-camera`
- `/dlaatest mode photon-taa`
- `/dlaatest mode fsr3`
- `/dlaatest mode fsr3-nomask`

1. Moving things, about 15 minutes. `/dlaatest scene build` puts the moving scene up over the lake at the game's
   normal speed.
   - Emre looks at the cogs, the fan, the bearing's cross, the ship, the walkers, the panda, the spinning sword, the
     chest and the bell. He watches from the shore, close up and riding the ship (stand on it), in `dlaa-default`,
     `dlaa-camera`, `photon-taa` and `fsr3`.
   - If he wants, he builds a short train in creative and rides it.
   - Then he swings his own sword, and walks and looks around holding things (a sword, a block, a map).
   - If he wants, he spawns a few animals with spawn eggs and lets them wander. Afterwards, run
     `/kill @e[type=!player,distance=..64]`.
   - `/dlaatest scene remove` when done.
2. Rain, fire and the portal, about 10 minutes. `/dlaatest scene build effects` puts up the effects scene and starts
   the rain.
   - Emre looks at the portal, the fire, the campfire's smoke, the lava fall and pool, the water fall and the rain.
     He stands still and turns the camera, in `dlaa-default`, `fsr3`, `fsr3-nomask` and `photon-taa`.
   - `/dlaatest scene remove` takes the scene away and stops the rain.

Then `/dlaatest mode dlaa-default`. Write down what he says, especially:

- anything that smears, trails or flickers: the ship's glass block, the entities, the hand;
- which mode keeps the fire, lava, portal and particles clean;
- whether rain fades out or leaves streaks;
- whether anything shimmers more under rain.

### Results

```powershell
$py = "$T\tools\uv\uv.exe"
& $py run --python 3.13 --with pillow --with numpy $T\repo\tools\dlss-testkit\metrics.py <run J folder>
& $py run --python 3.13 --with pillow --with numpy $T\repo\tools\dlss-testkit\ghostcheck.py <run K folder> portal=330,760,590,1070 fire=780,920,1050,1090 campfire=1110,930,1240,1090 lavafall=1440,510,1590,820 lavapool=1400,810,1870,990 waterfall=1930,410,2150,980 smoke=1950,920,2130,1050 sky=0,0,2560,400 bricks=600,1160,1280,1300
& $py run --python 3.13 --with numpy $T\repo\tools\dlss-testkit\mvcheck.py <run L folder>
& $py run --python 3.13 --with numpy $T\repo\tools\dlss-testkit\mvcheck.py <run M folder> panda=0,880,640,1400 chest=340,760,620,1080 stand=1800,940,2400,1400 bell=2160,880,2320,1040 villager=940,660,1140,1020 item=1360,680,1540,880
& $py run --python 3.13 --with numpy $T\repo\tools\dlss-testkit\mvcheck.py <run N folder> hand=1280,720,2560,1440
```

The boxes are for a 2560x1440 window. Scale them for another size. Each box is one thing's pixels, checked apart from
the rest.

- `mvcheck.py` moves each frame back along the motion vectors, scaled by k. With exact motion vectors, k = 1 matches
  best in every table. The cloud found 55 to 68% less difference than with no motion vectors. Sub-pixel motion, like
  the bell's quiet end, is too small to tell.
- `ghostcheck.py` finds the pixels the motion vectors cannot explain and shows how much of the last frame the output
  still shows there: `kept` from 0 to 1, and `off` in levels from this frame's input. Lower on the portal, fire,
  lava and smoke means less smear. `bricks` is the still platform under the rain, the reference for normal
  anti-aliasing. Compare `dlaa-default` with `fsr3` and `fsr3-nomask`. In the cloud at 1280x720,
  the masks cut FSR's `off` by about 30 to 45% on those boxes: lava pool from 16.0 to 9.3, portal from 11.3 to 6.6,
  fire from 14.2 to 9.5.

Write `$T\repo\tools\dlss-testkit\results\m2\REPORT.md` with:

- the script outputs, without conclusions from J's frame times;
- the `DLAA:` log lines, and any Colorwheel or shader lines;
- whether the game server ran meanwhile;
- Emre's notes from the session.

Add crops of about 512x512 from the still frames, for each mode:

- under `results\m2\J\`: the cogwheels, the bearing's cross, the ship, the walkers, the panda and the chest;
- under `results\m2\K\`: the portal, the fire and the lava, plus K's `ghostcheck\*.png` sheets.

That comes to a few MB in all. Commit and push on the DLAA branch, without any `raw\` files, then delete the `raw\`
folders.

## Profiling 1: where the time goes

A brief for the PC session, not a fixed recipe. Emre asked on 2026-10-24 that the PC session runs this on its own:
- it sets up the profiling tools;
- it takes the captures below, plus any others the data calls for (say why);
- it analyzes them and writes the report.

It **does not fix anything** and changes no code. The report says where the time goes and what causes it, with the
data to back it up. What to do about it is decided in the DLAA thread. Start only when Emre says so.

### Rules

- **As at the top of this file:**
  - work in the test folder only;
  - leave Emre's own install, worlds and server untouched;
  - ask Emre before anything not covered here.
- **Installs:** the profilers below may be installed normally, system-wide where they come as installers (Emre's
  OK, 2026-10-24). Prefer portable builds. Administrator prompts are Emre's to click.
- **A quiet PC:** no game server and no other game or launcher. Close what can be closed, and note what stayed open.
- **The NVIDIA Control Panel:** its power setting stays as Emre has it ("Prefer maximum performance" off, the
  baseline). If Perfect Presentation shows the GPU clocking down, report it. Ask Emre before measuring with that
  setting changed for the test `java.exe`, as it is his setting.
- **Raw captures stay on the PC** in `$T\profiling`: Nsight reports, JFR recordings and PresentMon CSVs. They hold
  paths with the user name. The repo gets the report, small tables and cropped screenshots of timelines.

### The questions

1. **Frame time per mode.** What is the frame time of Photon's TAA (`photon-taa`), DLSS (`dlaa-default`) and FSR 3.1
   (`fsr3`)? Is each mode held back by the CPU or the GPU? The PC was expected to be CPU-bound, so work that only
   costs GPU time may be free here, but not on a GPU-bound laptop.
2. **What the Bridge's work costs.**
   - On the GPU: copying the inputs, DLSS or each FSR pass, writing the result back.
   - On the CPU: working out the motion of mobs, the hand, machines and ships (`dlaa-default` against `dlaa-camera`,
     which leaves it out), and anything else of the Bridge's on the render thread.
3. **Where a frame's time goes overall.**
   - The render thread's hot paths, by owner:
     - the Bridge;
     - Photon's programs (through Iris);
     - Iris and Sodium;
     - Flywheel and Colorwheel, and Create;
     - Distant Horizons;
     - other mods;
     - Minecraft;
     - the graphics driver.
   - The GPU's biggest passes: shadow, the world's programs, Photon's deferred and composite passes, DLSS or FSR.
4. **Perfect Presentation.** What does it change? Its DirectX copy, interop waits, GPU clocks, frame pacing.

### Tools

- **PresentMon 2.x, console build:** frame times and the CPU and GPU busy time of every frame.
  - Download `PresentMon-2.*-x64.exe` from https://github.com/GameTechDev/PresentMon/releases, not the overlay app's
    installer.
  - It needs administrator rights.
  - Use `--qpc_time_ms`: then its times use the clock of the test kit's timeline.
- **NVIDIA Nsight Systems:** the timeline of CPU threads, OpenGL, the GPU queue and presents together.
  - Trace OpenGL with its GPU workload, OpenGL annotations, WDDM, CPU sampling and GPU metrics. **Not DirectX 11:**
    its interception crashed the game where the Bridge makes its DirectX device (profiling 1), so DLSS shows as a gap
    in the OpenGL queue. Record with `--delay`/`--duration` around the run only; a full-length trace grew past what
    the importer could load.
  - It should follow `java.exe` started by portablemc as a child process. If it doesn't, start `java.exe` directly
    with the command line portablemc uses.
- **NVIDIA Nsight Graphics, optional:** GPU Trace of single frames, if Nsight Systems doesn't split the GPU time
  finely enough.
- **Java Flight Recorder:** already in the JDK (`$T\tools\jdk21\bin\jfr.exe`) for the CPU.
  - Start it with `$env:JDK_JAVA_OPTIONS = "-XX:StartFlightRecording=filename=$T\profiling\P4.jfr,settings=profile,jdk.ExecutionSample#period=1ms,jdk.NativeMethodSample#period=1ms"`,
    set in the shell before the launch, because portablemc can't pass a JVM argument with commas. The recording is
    written when the game closes. Clear it afterwards (`Remove-Item Env:JDK_JAVA_OPTIONS`), so the other captures
    run without it.
  - `jfr view hot-methods` and `cpuprofile.py` read it. In the recording the render thread is called `main` (its
    name before Minecraft renames it); `cpuprofile.py` knows.
  - spark is in the pack too, if an in-game view helps.
- If NVIDIA's downloads need a developer account login, ask Emre.

### Setup

- `git -C $T\repo pull`. Copy the Bridge jar again (`Remove-Item` the old one first), then the test kit jar.
  - This Bridge marks its DLAA work for GPU profilers with `-Dcreate_galore_bridge.dlss.markers=true` (OpenGL debug
    groups: `Create Galore DLAA: copy inputs`, `...: evaluate (DirectX)` for DLSS, `...: FSR <pass>` for each FSR
    pass, `...: write back`). Without the flag nothing changes.
  - This test kit writes a `timeline` into `manifest.json`: when each part of a run started and ended. The times are
    in ms on the performance counter, PresentMon's `--qpc_time_ms`, and in epoch ms.
- `options.txt`: `enableVsync:false` and `maxFps:260`. Note the old values, and put them back at the end.
- `config\DistantHorizons.toml`: `numberOfThreads = 2` (the pack's own value), and back to 32 at the end.
- `config\create_galore_dlss_testkit.json`: write it fresh, with `"screenshots": false`. Saving a screenshot holds
  up its frame; the file with only `{"screenshots": false}` is enough.
- `perfectPresentation=false`, except where a capture says on.

### Captures

These are the minimum. Each is the launch from step 4 with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test`,
`--jvm-arg=-Dcreate_galore_dlss_testkit.modes=<modes>` and `--jvm-arg=-Dcreate_galore_dlss_testkit.viewpoints=<viewpoints>`
from the table, so every mode sees the same frames:

| Capture | Perfect Presentation | Modes | Viewpoints | Tool |
| --- | --- | --- | --- | --- |
| P1, P2, P3 (the same run three times, for the noise) | off | `photon-taa+dlaa-default+dlaa-camera+fsr3` | `shore+glass+canopy+moving` | PresentMon |
| P4 | off | `photon-taa+dlaa-default+dlaa-camera` | `moving` | Java Flight Recorder |
| P5 | on | `photon-taa+dlaa-default+fsr3` | `shore+canopy` | PresentMon |
| N1 | off | `photon-taa+dlaa-default+fsr3`, with `...markers=true` | `canopy+moving` | Nsight Systems, CPU sampling on |
| N2 | on | `dlaa-default`, with `...markers=true` | `canopy` | Nsight Systems |

- PresentMon: portablemc first runs a short-lived `java.exe`, so don't capture by process name. Wait for the game's
  own `java.exe` (a few seconds after launch), then attach to it: `--process_id <pid> --output_file
  $T\profiling\P1.csv --qpc_time_ms --timed 240 --terminate_after_timed --stop_existing_session
  --no_console_stats` (`--timed` long enough for the run). Admin rights: `Start-Process -Verb RunAs`. PresentMon
  2.6 calls the start column `CPUStartQPCTimeInMs`; `frametimes.py` reads it.
- Keep the Nsight captures to a run each, as their files grow fast. Shorter paths (`"paths": ["still", "flick"]`)
  are fine there; say so.

### Analysis

```powershell
$py = "$T\tools\uv\uv.exe"
$shots = "$T\instance\screenshots\dlaa-test"
& $py run --python 3.13 $T\repo\tools\dlss-testkit\frametimes.py $shots\<P1> $T\profiling\P1.csv $shots\<P2> $T\profiling\P2.csv $shots\<P3> $T\profiling\P3.csv
& $py run --python 3.13 $T\repo\tools\dlss-testkit\frametimes.py $shots\<P5> $T\profiling\P5.csv
& $py run --python 3.13 $T\repo\tools\dlss-testkit\cpuprofile.py $shots\<P4> $T\profiling\P4.jfr $T\tools\jdk21\bin\jfr.exe
```

- `frametimes.py`: per viewpoint and mode, the median and p90 frame time, the spread across P1 to P3, CPU and GPU
  busy time, and the share of GPU-bound frames. If it says the clocks don't line up, report that with the CSV's
  first lines.
- `cpuprofile.py`: per mode, the render thread's share in the Bridge's DLAA code and its motion vector code, the
  packages and the busiest methods.
- From Nsight, per mode, the GPU ms per frame for:
  - each `Create Galore DLAA` group;
  - DLSS's DirectX work;
  - the rest of the frame, split as far as the capture allows;
  - the wait for the interop handover and the present.
- Code injected by mixins shows up inside Minecraft's methods. Name the mod where the handler or mixin class shows
  it.

### Report

Write `$T\repo\tools\dlss-testkit\results\profiling-1\REPORT.md`, then commit and push on the DLAA branch:

- **Summary:** the four questions answered with numbers, and how sure each answer is (noise, repeats).
- **Hot paths:** the top render thread paths and GPU passes. For each, give:
  - what it is;
  - its share or ms;
  - who it belongs to;
  - whether it changes with the mode;
  - whether it's caused by the Bridge, by something else in the pack, or by the driver or hardware.
  Leave out fixes. Say "this looks like" where it's a reading, not a measurement.
- **Method:**
  - tool versions and commands;
  - the settings above and their old values;
  - the driver and the Windows power plan (`powercfg /getactivescheme`);
  - what was open;
  - every `DLAA:` log line;
  - anything that didn't go to plan.
- **Data:** small tables (CSV summaries) and cropped timeline screenshots under `results\profiling-1\`. List where
  the raw captures are on the PC.


## Upscaling 1: DLSS and FSR from a lower render resolution

Start only when Emre says so. Same PC, test folder and tools as before. The Bridge now upscales through Photon's own
TAAU path: with Photon's TAAU on, the frame renders at a lower size and DLSS or FSR 3.1 make the window-sized picture
from it. The Bridge rounds the render size to whole pixels, and Photon's own TAAU renders the same size, so every
mode at one scale gets the same internal resolution. **Compare each upscaler with Photon's TAAU at the same scale, not
with native TAA** (Emre, 2026-10-25). Nothing of this is merged until Emre has tested it.

### Update

```powershell
git -C $T\repo pull
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-1.96.0.jar $T\instance\mods\
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
```

Keep `$T\instance`, its world and its settings as they are, apart from the Setup below. Check before the launch:
`config\create_galore_bridge-display.properties` has `dlaa=true` and `perfectPresentation=false`, and
`nvngx_dlss.dll` (310.9.16) is still in `$T\instance\create-galore-dlss`. The kit's config gets the new modes added
on the first run. Each mode sets Photon's `TAAU` and `TAAU_RENDER_SCALE` itself, and each run puts the pack's options
back when it ends.

### Setup

One launch does every run: the kit switches modes, render scales and engines in place (each switch only reloads the
shaders), and with `...runs=` it plays several runs one after another in the same game, so the game starts once. The
settings below are set once, before that launch, and put back after it.
- The setup from "Profiling 1", Setup: `options.txt` with `enableVsync:false` and `maxFps:260` (unlimited),
  `numberOfThreads = 2` in `config\DistantHorizons.toml`, `perfectPresentation=false`. Note the old values.
- The kit's config (`config\create_galore_dlss_testkit.json`): no `"screenshots": false` (each run sets it itself), and
  `sceneTickRate` 20 as in the milestone 2 session. The runs F1 to U7 are built into the kit and get added to an
  existing config.
- A quiet PC for the first part, F1 and F2, as in profiling 1: Emre closes everything else; note what stayed open.

### The launch

Write each argument as `--jvm-arg=-D...=...` (`...` = `create_galore_dlss_testkit`), as in step 5:

`...autorun=dlaa-test` `...runs=F1+F2+U1+U2+U3+U4+U5+U6+U7` `...byEye=perf-dlss-K`

| Run | What | Modes | Viewpoints, paths |
| --- | --- | --- | --- |
| F1 | frame rate, no screenshots | `photon-taa+dlaa-default+fsr3`, then per scale (`perf`, `ultra`) `X-taau+X-dlss-K+X-dlss-M+X-dlss-L+X-fsr3` | `shore+canopy`, all paths |
| F2 | frame rate, no screenshots | `photon-taa+dlaa-default+fsr3+q-taau+q-dlss-K+q-fsr3+bal-taau+bal-dlss-K+bal-dlss-M+bal-dlss-L+bal-fsr3` | `shore+canopy`, all paths |
| U1 | pictures, Performance | `perf-taau+perf-dlss-K+perf-dlss-M+perf-dlss-L+perf-fsr3+dlaa-default` | all |
| U2 | pictures, Ultra Performance | `ultra-taau+ultra-dlss-K+ultra-dlss-M+ultra-dlss-L+ultra-fsr3` | all |
| U3 | pictures, Balanced | `bal-taau+bal-dlss-K+bal-dlss-M+bal-dlss-L+bal-fsr3` | all |
| U4 | pictures, Quality | `q-taau+q-dlss-K+q-fsr3` | all |
| U5 | things that move, with the hand | `perf-taau+perf-dlss-K+perf-dlss-M+perf-fsr3` | `moving`, `brief` |
| U6 | mip bias | `perf-dlss-K+perf-dlss-K-bias+perf-fsr3+perf-fsr3-bias` | quick |
| U7 | what DLSS gets | `perf-debug-color+perf-debug-motion` | quick |

- Each run gets its own folder, `screenshots\dlaa-test\<time>-<run>`, and the chat says `run ... (N more after it)`.
  After U7 the game stays open for "By eye, with Emre" below (that's `...byEye`; without it the game closes).
- PresentMon, for F1 and F2: as in profiling 1 (the same build in `$T\tools`), attached to the game's own `java.exe`,
  elevated: `--process_id <pid> --output_file $T\profiling\F.csv --qpc_time_ms --timed 1800 --terminate_after_timed
  --stop_existing_session --no_console_stats`. Its capture only needs to cover F1 and F2; it can stop during the U
  runs. If F2's `run finished` line comes after PresentMon stopped, measure F2 again on its own later
  (`...runs=F2`) with a longer `--timed`.
- If the game stops part way (a crash, or DLSS takes it down), start it again with only the runs that hadn't
  finished, for example `...runs=U4+U5+U6+U7` (and `...byEye`). Report what happened.
- Afterwards, put the settings back as profiling 1 did ("Afterwards" in its report).

The log must have, for each upscaling mode:

- `DLAA: on for this shader pack, engine dlss, DLSS preset K, upscaling from 50% of the window` (the preset and the
  scale of that mode; `engine fsr3` for the FSR modes);
- `DLAA: DLSS running at 1280x720 to 2560x1440 (Performance, 50%), preset K, the pack's exposure` (M and L say
  `DLSS meters the exposure`), or `DLAA: FSR 3.1 running at 1280x720 to 2560x1440 (Performance, 50%)`;

and no `DLAA: stopped`, `DLSS could not start` or `Failed to create shader rendering pipeline`. The `X-taau` modes
have no DLAA lines of their own: Photon's TAAU runs. If a DLSS mode stops, copy the whole `DLAA:` line and the end
of `create-galore-dlss\nvsdk_ngx.log`.

### Frame rate (F1 and F2)

Frame rate only, cheaply: PresentMon, no Nsight, no Java Flight Recorder.
- `frametimes.py` on the F1 and F2 folders with `F.csv` (one CSV serves both: it lines frames up with each run's
  timeline), as in profiling 1's Analysis.
- The native modes are the baselines each upscaler is measured against: Photon's TAA (`photon-taa`), DLAA
  (`dlaa-default`) and FSR 3.1 at full resolution (`fsr3`). They also line up with profiling 1 (shore 5.43, 6.20 and
  6.17 ms, canopy 5.87, 7.31 and 7.20 ms, in `results\profiling-1\REPORT.md`). If they are within about 0.8 ms of
  those, the runs compare; if not, say so next to the tables.
- The PC was GPU-bound with DLAA, but Photon's TAA was about half CPU-bound at about 5–7 ms a frame. So expect the
  low scales to run into the CPU: report the share of GPU-bound frames per mode, as profiling 1 did.
- Profiling 1's open point: DLSS's p90 was 0.2–0.8 ms above FSR's, and under Nsight the write back after DLSS
  sometimes blocked for about 3.9 ms. Compare the p90 of each DLSS mode with the FSR mode at the same scale (and
  `dlaa-default` with `fsr3`), and say whether the gap is still there without a tracer.
- The 4080 is an Ada card, where L and M run fast; NVIDIA says they cost much more on RTX 20 and 30 cards (no FP8), so
  their frame times here say nothing about those.

### What to look at

- U1 to U4: each upscaler against `X-taau` at the same scale: sharpness of textures, leaves and fences, shimmer on
  the still path, smearing after the flick, ghosting behind moving things. (`frameMs` in their manifests is rough,
  as saving screenshots holds up frames; the frame rates come from F1 and F2.)
- U5: things that move at Performance, and the hand.
- U6: NVIDIA's full mip bias (one more mip level of detail than the render scale's) against the scale's own: sharper
  textures, or more shimmer and moiré on the blocks' pixel art?
- U7: what DLSS gets, the render-sized frame stretched over the window: the whole view, no black or doubled edges,
  motion vectors as in milestone 2.

### By eye, with Emre (Performance, about 30 minutes)

Emre asked for this (2026-10-25): numbers alone don't show how much the upscaling changes the look. It works like
milestone 2's "By eye, with Emre": while Emre plays and watches, the session types the `/dlaatest` commands into the
game's chat (computer use). Only the moving scene; no rain or effects scene.

When U7 ends, the game stays open: the moving scene goes up over the lake at the game's normal speed, Emre stands at
its viewpoint (flying, so he doesn't drop into the lake), and a moment later the shaders switch to `perf-dlss-K`. The
chat says `now perf-dlss-K (the shaders reloaded); by eye now`. Tell Emre it's his turn.

1. **`perf-dlss-K`** (DLSS preset K at Performance, 50%): already on. Emre walks, flies and rides the ship (stand on
   it). He looks at the cogs, the fan, the bearing's cross, the ship, the walkers, the panda, the spinning sword, the
   chest and the bell, from the shore, close up and riding; he swings his own sword and holds things. If he wants, he
   builds a short train and rides it, or spawns a few animals (`/kill @e[type=!player,distance=..64]` afterwards).
2. When he says next: **`/dlaatest mode perf-fsr3`** (FSR 3.1 at Performance).
3. When he says next: **`/dlaatest mode perf-taau`** (Photon's own TAAU at Performance).
4. Then he concludes, or asks for more. Any mode switches the same way, `/dlaatest mode NAME`:
   - at a scale: `q-` (Quality, 67%), `bal-` (Balanced, 58%), `perf-` (Performance, 50%), `ultra-` (Ultra Performance,
     33%), followed by `dlss-K`, `dlss-M`, `dlss-L`, `fsr3` or `taau`, for example `q-dlss-K` or `ultra-dlss-L`;
   - at full resolution: `dlaa-default` (DLAA, K), `dlaa-M`, `dlaa-L`, `fsr3`, `photon-taa`;
   - another render scale (say 80%): Esc, Options, Video Settings, Shader Packs, the `DLAA...` button: Render
     resolution `Custom`, then the slider. Leaving the screen applies it. The next `/dlaatest mode` sets its own scale
     again.
5. When he's done: `/dlaatest scene remove`, then `/dlaatest mode dlaa-default` (Photon's TAAU goes off again), and
   close the game. Then put back the settings from Setup.

At every mode, write down what Emre says with the mode's name, as close to his words as you can, especially:
- how much sharper or softer each looks than the others, and than DLAA at full resolution if he asks for it;
- anything that smears, trails, ghosts or flickers: the ship's glass block, the entities, the hand, leaves;
- shimmer on fences, leaves and far terrain while standing still;
- whether he'd play at that setting.

If he wants to look at the saved pictures too: `compare.html` on the U1 to U4 folders, each upscaler against `X-taau`
at one scale at a time.

### Results

`metrics.py` for each run folder. Write `$T\repo\tools\dlss-testkit\results\upscale1\REPORT.md`: the metrics tables
per scale, the frame rate tables of F1 and F2 (median and p90 frame time, fps, GPU-bound share, per scale against
`X-taau` and the three native modes), the `DLAA:` lines, anything that looked wrong, and Emre's by-eye notes per mode
("Emre's notes", as in milestone 2's report); crops of the same spots
as round 1 (about 512x512, from the settled still frame) for each mode of U1 under `results\upscale1\U1\`, a few MB
in all. Commit and push on the DLAA branch.

## Crash 1: DLSS restarting at a new render size with Perfect Presentation on

Start only when Emre says so. Same PC, test folder and tools as before; about 40 minutes, three launches, no by-eye
part, no PresentMon. Frame times don't count here (his server may run).

**What happened.** In Emre's own game on 0.4.82 (2026-10-25 15:41), switching the preset from K to L and the render
resolution from Native to Performance at once crashed the game: `EXCEPTION_ACCESS_VIOLATION` in `nvwgf2umx.dll`
(NVIDIA's DirectX 11 driver), under `wglDXUnregisterObjectNV`, from `DlssPass.closeTextures`. That's DLSS's old textures
being freed for the new render size, on the first frame after the shaders reloaded. Perfect Presentation was on, so DLSS
ran on Perfect Presentation's DirectX device. Every earlier restart at a new size (dozens in Upscaling 1) ran with
Perfect Presentation off. After restarting, he switched sizes twice more without a crash, so it doesn't happen every time.

**What 1.98.0 changes.** DLSS opens a DirectX 11 device of its own also with Perfect Presentation on (the NGX ready line
says `(its own DirectX device; ...)`). On a restart it releases the DLSS feature before its textures go, and waits for
the GPU to finish with them. The crash guard (`dlss-starting.txt`) now covers the textures being swapped as well.

**The run.** `C1` is built into the kit (it gets added to an existing kit config). It plays `dlaa-default`, `q-dlss-K`,
`bal-dlss-M`, `perf-dlss-L` and `ultra-dlss-K` 12 times over: 60 DLSS restarts, each one at a new render size and most
with a new preset, at `shore` with the `brief` path and no screenshots. It runs about 10 minutes.

### Settings (put back afterwards)

- `config\create_galore_bridge-display.properties`: `dlaa=dlss`, `perfectPresentation=true` for launches 1 and 2,
  `false` for launch 3. Note the old values.
- `config\fml.toml`: `earlyWindowControl = false`, as in step 3. Perfect Presentation needs it off.
- `nvngx_dlss.dll` 310.9.16 is still in `$T\instance\create-galore-dlss`.
- Before each launch, delete `$T\instance\create-galore-dlss\dlss-starting.txt` and `dlss-crashed.txt` if they are
  there. Otherwise a crash in the launch before keeps DLSS off.

### Launches

Write each argument as `--jvm-arg=-D...=...` (`...` = `create_galore_dlss_testkit`): `...autorun=dlaa-test` and
`...runs=` as below.

1. **The old Bridge, Perfect Presentation on, `...runs=C1`.** This shows the crash happens in the kit at all.
   ```powershell
   Remove-Item $T\instance\mods\create-galore-bridge-*.jar
   Invoke-WebRequest -Uri https://raw.githubusercontent.com/emresahin0906-code/Create-Galore/553b0ba7c02526bba2f02e06abae9626de0384a7/pack/mods/create-galore-bridge-1.97.0.jar -OutFile $T\instance\mods\create-galore-bridge-1.97.0.jar
   ```
   1.97.0 is 0.4.83's Bridge, which has the same DLSS code as 0.4.82's. A crash is expected. When it comes, copy
   `$T\instance\hs_err_pid*.log` to `$T\crash1\` and note the time and how many `DLAA: DLSS running at` lines the log
   has. If all 60 restarts finish without a crash, say so: the kit then doesn't reproduce it, and launch 2 proves less.
2. **The new Bridge, Perfect Presentation on, `...runs=C1+C1`** (120 restarts).
   ```powershell
   git -C $T\repo pull
   Remove-Item $T\instance\mods\create-galore-bridge-*.jar
   Copy-Item $T\repo\pack\mods\create-galore-bridge-1.98.0.jar $T\instance\mods\
   Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
   ```
   It must finish both runs. The log must have `(its own DirectX device; OpenGL on ...)` on the NGX ready line, and 120
   `DLAA: DLSS running at` lines, with presets K, K, M, L, K in turn. It must have no `DLAA: stopped`, no `the GPU did not
   finish` and no `hs_err_pid*.log`.
3. **The new Bridge, Perfect Presentation off, `...runs=C1`.** The same checks for 60 restarts.

If launch 2 or 3 crashes, stop: copy the `hs_err_pid*.log` and the log, and report.

### Results

Write `$T\repo\tools\dlss-testkit\results\crash1\REPORT.md`. For each launch, give the Bridge version, whether Perfect
Presentation was on, the restarts done (count of `DLSS running at` lines), and the crash time if there was one. For a
crash, give the `hs_err` file's top frames, its Java frames and `siginfo`. Include the NGX ready line, and anything
else that looked wrong. Don't commit the hs_err files: they name his user folder. Commit and push on the DLAA branch.

## Reflex 1: input latency with Reflex, a late input read and Sodium's render-ahead limit

Start only when Emre says so. Same PC, test folder and tools as before; about 45 minutes, two launches and an
optional by-feel part. **Frame times count here** (latency follows the GPU's load): a quiet PC as in profiling 1, no
game server. Reflex ships in pack 0.5.2 (Bridge 1.106.0) as an experimental NVIDIA Reflex setting without waiting for
this run (Emre, 2026-10-25 20:29); this run measures what it does and decides what changes next.
**Done 2026-10-25 with Bridge 1.105.0** (L1, L2 and by feel): [results/reflex1/REPORT.md](results/reflex1/REPORT.md).
Step 4 (the settings screen) is for a later session with 1.106.0 or newer.

**What it tests.** The game renders with OpenGL, which NVIDIA Reflex has no API for. With Perfect Presentation on, the
frames go to the window through a Direct3D 11 swap chain, and the Bridge can run Reflex (NVAPI) on that D3D11 device:
the driver's sleep at the start of each frame, then input, plus markers for where each frame's game work, rendering
and present are. Whether that lowers the latency of the OpenGL frames behind it is the open question. Two things that
need no NVIDIA run alongside:
- **Late input read** (`latePoll`): Minecraft reads input while presenting the frame before; anything it waits for
  after that (Sodium's render-ahead wait, Perfect Presentation's swap chain wait, the game's frame cap) ages it. The
  Bridge can read input again just before the mouse turns the camera.
- **Sodium's CPU render-ahead limit** (`cpu_render_ahead_limit`, 3): how many frames the CPU may get ahead of the
  GPU. 1 or 0 should lower latency and may cost frame rate.

**How it's measured.** The Bridge's latency meter: from the game last reading input to the GPU finishing that frame
(an OpenGL timestamp, matched to the CPU clock every second), median and 90th percentile, on any GPU. The swap and the
screen add the same on top in every mode, so the modes compare; this is not click-to-photon. With Reflex markers on,
the driver's own report of its last 64 frames goes next to it.

### Update

The DLAA branch was restarted from main after 0.5.1, so fetch it instead of pulling:

```powershell
git -C $T\repo fetch origin
git -C $T\repo checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-*.jar $T\instance\mods\
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
```

The Bridge there is 1.105.0 (the test build) or 1.106.0 (the 0.5.2 release with the NVIDIA Reflex setting); either
works the same here. Reflex and the late read change nothing unless the display settings ask (`reflex`, plus
`latePoll` and `latencyLog` for testing; the late read is on with `reflex=on` or `boost` unless `latePoll` says
otherwise); the kit's latency modes set those themselves. In 1.106.0 the log lines start with `Reflex:` and
`Latency:` instead of `Latency test:`.

### Settings (note the old values, put them back afterwards)

- `config\create_galore_bridge-display.properties`: `dlaa=dlss` (DLAA at full resolution, as Emre plays),
  `perfectPresentation=true` for launch 1, `false` for launch 2. `nvngx_dlss.dll` 310.9.16 still in
  `$T\instance\create-galore-dlss`.
- `config\fml.toml`: `earlyWindowControl = false` (Perfect Presentation needs it).
- `config\DistantHorizons.toml`: `numberOfThreads = 2`, as in profiling 1.
- `options.txt`: nothing. Each latency mode sets the frame cap (unlimited, or 60) and turns vsync off itself, and
  sets Sodium's render-ahead limit; the run puts all three back when it ends.
- The kit's config: nothing; the runs L1 and L2 are built in (added to an existing config) and save no screenshots.
- **Only look, don't change:** the NVIDIA Control Panel's 3D settings for `java.exe` / `javaw.exe` and the global
  ones: Low Latency Mode, Max Frame Rate, Vertical sync, G-SYNC, Power management mode. And the monitor's refresh
  rate (Windows display settings). Write all of them in the report; a control panel Low Latency Mode of Ultra or a
  frame cap there changes what the modes mean.

### Launches

The launch from step 4 (2560x1440), with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and `...runs=`
as below. The game window stays in front with focus during the runs (fullscreen behaves differently from a window in
the background; F11 if it opened windowed is fine).

1. **Perfect Presentation on, `...runs=L1`** (about 12 minutes). Modes, each played twice, at `shore` and `canopy`,
   600 frames held still each:

   | Mode | What |
   | --- | --- |
   | `lat-base` | the game as it is (Reflex off, frame cap off) |
   | `lat-late` | input read again just before rendering |
   | `lat-markers` | Reflex markers only (the driver's report, nothing changed) |
   | `lat-reflex` | Reflex low latency (sleep, then input) + late read |
   | `lat-boost` | the same with Reflex boost (GPU clocks held up) |
   | `lat-cap` | 60 fps cap, the game's own limiter |
   | `lat-cap-late` | 60 fps cap, game's limiter, late read |
   | `lat-cap-reflex` | 60 fps cap by Reflex instead of the game's limiter, low latency on, late read |

   The log must have `Latency test: NVIDIA Reflex started on Perfect Presentation's Direct3D 11 device` (1.106.0:
   `Reflex: started on Perfect Presentation's Direct3D 11 device`) and, in the Reflex modes, `... low latency on,
   ...; driver low latency on, ...`. If it says Reflex is unavailable or can't run, or `Reflex stopped after an
   error`, copy that line and go on (the other modes still count). Also check for `Perfect Presentation: DXGI swap chain ready`.
2. **Perfect Presentation off, `...runs=L2`** (about 10 minutes): `lat-base`, `lat-late`, `lat-ra1` and `lat-ra0`
   (Sodium's render-ahead limit 1 and 0), `lat-ra1-late`, `lat-cap`, `lat-cap-late`.
3. **By feel, optional (about 10 minutes, with Emre).** Launch 1's settings (Perfect Presentation on) with
   `...autorun=dlaa-test`, one short run first (`...modes=lat-base`, `...viewpoints=moving`, `...paths=still`; without
   a run list autorun plays the whole default test, and the chat can't be opened during it) and `...byEye=lat-base`:
   after that run the game stays open at
   the moving scene. Emre flicks the camera around and builds a little. The session switches with
   `/dlaatest mode lat-late`, `/dlaatest mode lat-reflex` and back to `/dlaatest mode lat-base` when he says next
   (this only switches Reflex and the late read; the frame cap and Sodium stay as `options.txt` and Sodium have
   them). Write down what he says per mode, especially whether the camera feels more direct, and whether he can tell
   at all.

4. **The settings screen (1.106.0 only, 5 minutes).** Start with `perfectPresentation=false` and `reflex=off`, no kit
   autorun. On the DLAA... screen pick NVIDIA Reflex: On. Perfect Presentation must switch to ON by itself and the
   text beside Reflex say `Runs after you restart the game.`; Done shows a "Restart the game" notice. Check
   `config\fml.toml` now has `earlyWindowControl = false` and the display properties `reflex=on` and
   `perfectPresentation=true`. Restart once: the screen must say `Running: the driver confirms it.` (its tooltip has
   the driver's line). Turning Perfect Presentation OFF must set Reflex to Off. Put the settings back afterwards.

If a launch crashes, copy `hs_err_pid*.log` and the log's last 100 lines, and report.

### Results

`python latency.py <L1 folder> <L2 folder>` (`screenshots\dlaa-test\<time>-L1` and `-L2`). Write
`$T\repo\tools\dlss-testkit\results\reflex1\REPORT.md`:
- its tables, and per launch the change of each mode's median latency and of its fps against `lat-base`, at both
  viewpoints, with how far the two repeats of a mode differ;
- the `Latency test:` lines and the `Perfect Presentation:` and `DLAA:` lines;
- the control panel settings and refresh rate from Settings, what stayed open on the PC;
- for the Reflex modes: whether the driver says low latency is on and uses the game's sleep, the driver's numbers
  next to the Bridge's, and the unit check (about 1 expected);
- Emre's by-feel notes, if that part ran;
- the settings screen check (step 4): what each step showed.

Commit and push on the DLAA branch. Don't commit hs_err files (they name his user folder).

## Pacer 1: the GPU pacer for uncapped play, and a frame cap that waits before input

Start only when Emre says so. Same PC, test folder, tools and settings as Reflex 1; about 40 minutes, two launches
and an optional by-feel part. **Frame times count here**: a quiet PC as in profiling 1, no game server.

**What it tests.** Reflex 1 found that uncapped, Reflex's sleep adds nothing beyond the late input read: the driver
sees no OpenGL work, so it can't keep the GPU's queue short. Emre plays uncapped with G-SYNC. Two things, both on
any GPU, that don't need the driver:
- **GPU pacer** (`gpuPacer`, `pacerTargetUs`): the Bridge flushes a GPU timestamp where each frame's rendering starts
  and reads how long that start waited on the GPU behind earlier frames (the queue). It then waits just before
  rendering (before the late read) by about that much, so the frame reaches the GPU about when the GPU gets to it.
  The target is how long a frame may still queue, so the GPU never runs dry when a frame takes a little longer on
  the CPU. By default it sets itself to three times how much the frames' arrival varies from one frame to the next
  (at least 250 us); `pacerTargetUs` fixes it instead (the kit tries 500 and 2000 us). The wait is a slowly learned
  steady part plus a part that follows the error, so it shouldn't swing. Expected: the uncapped latency moves toward
  Reflex 1's capped best (8.7 ms) at the same frame rate. **A frame rate drop of more than about 3% is a failure**,
  whatever the latency.
- **Frame cap at the frame's start** (`frameCapAtStart`): the Max Framerate cap waits at the start of the frame,
  before input, as Reflex's limiter does, instead of in the game's limiter after the frame. Reflex 1: the game's
  60 fps cap with the late read was 16.7 ms with Perfect Presentation, Reflex's 8.7 ms. This shows whether the
  difference is where the wait sits (then any GPU gets it) or something only the driver does.

The waits use a high resolution Windows timer and spin only the last half millisecond; the log says
`Latency: waits use a high resolution timer`. `latency.py` shows how late the pacer's waits end (`late`, us).

### Update

```powershell
git -C $T\repo fetch origin
git -C $T\repo checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-*.jar $T\instance\mods\
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
```

The Bridge there is 1.107.0, a test build with the pacer (not released). The pacer and the frame cap change nothing
unless the display settings ask (testing only, not written by the game); the kit's modes set them.

### Settings

As Reflex 1 (note the old values, put them back afterwards): `dlaa=dlss`, `perfectPresentation=true` for launch 1
and `false` for launch 2, `earlyWindowControl = false`, Distant Horizons `numberOfThreads = 2`. Look at the NVIDIA
Control Panel (Low Latency Mode, Max Frame Rate, Vertical sync, G-SYNC, Power management) and the refresh rate again
and write them down; change nothing.

### Launches

The launch from step 4 (2560x1440), `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and `...runs=` as
below, the window in front with focus.

1. **Perfect Presentation on, `...runs=L3`** (about 15 minutes). Each mode twice, at `shore` and `canopy`:

   | Mode | What |
   | --- | --- |
   | `lat-base` | the game as it is |
   | `lat-late` | late input read |
   | `lat-pacer` | GPU pacer (target set from the jitter) + late read |
   | `lat-pacer-500` | the same, target fixed at 500 us |
   | `lat-pacer-2000` | the same, 2000 us |
   | `lat-reflex` | Reflex on + late read (as 0.5.2 ships it) |
   | `lat-reflex-pacer` | Reflex on + GPU pacer (target from the jitter) + late read |
   | `lat-cap-late` | 60 fps cap, the game's limiter, late read |
   | `lat-cap-start` | 60 fps cap waiting at the frame's start, late read (no Reflex) |
   | `lat-cap-reflex` | 60 fps cap by Reflex, late read |

   The log must have `Latency: GPU pacer on, frames may queue on the GPU as much as they vary`,
   `Latency: GPU pacer on, frames may queue 500 us on the GPU` (and 2000), `Latency: the frame cap waits at the start of the frame, before input`, `Latency: waits use a high resolution
   timer` and `Reflex: started on Perfect Presentation's Direct3D 11 device`. Copy any `Latency:` or `Reflex:` WARN or
   ERROR line.
2. **Perfect Presentation off, `...runs=L4`** (about 12 minutes): `lat-base`, `lat-late`, `lat-ra1-late` (Sodium's
   render-ahead 1, Reflex 1's best without Perfect Presentation), `lat-pacer`, `lat-ra1-pacer` (render-ahead 1 +
   pacer), `lat-pacer-500`, `lat-cap-late`, `lat-cap-start`.
3. **By feel, optional (about 10 minutes, with Emre).** Launch 1's settings, uncapped as Emre plays: before the
   launch set `maxFps:260` (Unlimited) and `enableVsync:false` in the test instance's `options.txt` (note the old
   values, put them back afterwards). One short run first (`...modes=lat-base`, `...viewpoints=moving`,
   `...paths=still`) and `...byEye=lat-base`. Switch with `/dlaatest mode lat-reflex` (0.5.2's Reflex), then
   `/dlaatest mode lat-reflex-pacer`, then `/dlaatest mode lat-pacer` (no Reflex), and back to `lat-base`, when he
   says next. Write down what he says per mode: whether the pacer feels more direct than 0.5.2's Reflex, and whether
   anything stutters or feels uneven (that would be the pacer's wait swinging).

If a launch crashes, copy `hs_err_pid*.log` and the log's last 100 lines, and report.

### Results

`python latency.py <L3 folder> <L4 folder>`. Write `$T\repo\tools\dlss-testkit\results\pacer1\REPORT.md`:
- per launch, each mode's median latency, its change and its fps change against `lat-base` at both viewpoints, the
  repeats' spread, and the new columns: the GPU queue, the GPU's time between frames (median and 90%), and with the
  pacer its wait, how late the wait ended and the target it used (`pacerTargetUs` in the manifest's latency
  windows: the automatic one as measured);
- in one line each: does the pacer bring the uncapped latency near 8.7 ms, at what fps cost; which target is best;
  is the automatic one as good as the best fixed one; does Reflex add anything on top of the pacer; does `lat-cap-start` match `lat-cap-reflex` or `lat-cap-late`;
- the `Latency:` and `Reflex:` lines, the control panel settings and refresh rate;
- Emre's by-feel notes, if that part ran.

Commit and push on the DLAA branch. Don't commit hs_err files (they name his user folder).

## Pacer 2: the pacer again, with the queue read without a flush

Start only when Emre says so. Same PC, test folder, tools and settings as Pacer 1; about 25 minutes, two launches, no
by-feel part. **Frame times count here**: a quiet PC, no game server.

**What it tests.** Pacer 1 (`results/pacer1/REPORT.md`) found two things:
- With Perfect Presentation on, every mode ran about 30% slower than in Reflex 1, with no GPU queue at all, so the
  pacer had nothing to do. The likely cause was the Bridge's own measurement: a GPU timestamp flushed at the start of
  each frame's rendering, which made the CPU wait for the GPU's previous frame. Bridge 1.110.0 has no flush there: it
  reads the queue as the GPU's finish of the frame before minus the CPU's start of rendering (below 0, the GPU waited).
- Without Perfect Presentation, the pacer halved the latency (16.0 / 18.8 ms to 7.9 / 8.5 ms) but cost 6% fps with its
  automatic target (about 2 ms). Longer targets should cost less: this run tries 3000 and 5000 us.

It also checks that the frame cap now waits at the frame's start by default (`lat-cap-default`: a 60 fps cap with no
test switches), which Pacer 1 showed matches Reflex's cap.

**First check, before anything else in the results:** `lat-base` with Perfect Presentation on should be back near
Reflex 1's 171 / 154 fps (shore / canopy). If it is still near Pacer 1's 116 / 113 fps, say so at the top of the
report: the flush wasn't the cause.

### Update

```powershell
git -C $T\repo fetch origin
git -C $T\repo checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-*.jar $T\instance\mods\
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
```

The Bridge there is 1.110.0, a test build (not released). It also has the new frame counter and the display settings
screen in two tabs; neither changes anything unless picked on that screen.

### Settings

As Pacer 1 (note the old values, put them back afterwards): `dlaa=dlss`, `perfectPresentation=true` for launch 1
and `false` for launch 2, `earlyWindowControl = false`, Distant Horizons `numberOfThreads = 2`. Look at the NVIDIA
Control Panel and the refresh rate again and write them down; change nothing.

### Launches

The launch from step 4 (2560x1440), `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and `...runs=` as
below, the window in front with focus.

1. **Perfect Presentation on, `...runs=L5`** (about 13 minutes). Each mode twice, at `shore` and `canopy`:

   | Mode | What |
   | --- | --- |
   | `lat-base` | the game as it is |
   | `lat-late` | late input read |
   | `lat-reflex` | Reflex on + late read (as 0.5.2 ships it) |
   | `lat-pacer` | GPU pacer (automatic target) + late read |
   | `lat-pacer-2000` | the same, target 2000 us |
   | `lat-pacer-4000` | the same, target 4000 us |
   | `lat-reflex-pacer` | Reflex on + GPU pacer (automatic) + late read |
   | `lat-cap-default` | 60 fps cap, nothing else switched: the Bridge's default cap at the frame's start |

   The log must have `Latency: the frame cap waits at the start of the frame, before input`,
   `Latency: GPU pacer on, frames may queue on the GPU as much as they vary`, `... 2000 us ...`, `... 4000 us ...`,
   `Latency: waits use a high resolution timer` and `Reflex: started on Perfect Presentation's Direct3D 11 device`.
   Copy any `Latency:` or `Reflex:` WARN or ERROR line.
2. **Perfect Presentation off, `...runs=L6`** (about 10 minutes): `lat-base`, `lat-ra1-late`, `lat-pacer`,
   `lat-pacer-3000`, `lat-pacer-5000`, `lat-ra1-pacer`.

If a launch crashes, copy `hs_err_pid*.log` and the log's last 100 lines, and report.

### Results

`python latency.py <L5 folder> <L6 folder>`. Write `$T\repo\tools\dlss-testkit\results\pacer2\REPORT.md`:
- first, the `lat-base` frame rate with Perfect Presentation on against Reflex 1 (171 / 154) and Pacer 1 (116 / 113);
- per launch, each mode's median latency, its change and its fps change against `lat-base` at both viewpoints, the
  repeats' spread, the queue (median and `q10`), and with the pacer its wait, how late the wait ended and its target;
- in one line each: with Perfect Presentation on, is there a queue now, and does the pacer bring the latency down at
  what fps cost; which target keeps the frame rate within 3% (with and without Perfect Presentation) and at what
  latency; does Reflex add anything on top of the pacer; does `lat-cap-default` give about Pacer 1's `lat-cap-start`
  (8.8 / 8.6 ms);
- the `Latency:` and `Reflex:` lines, the control panel settings and refresh rate.

Commit and push on the DLAA branch. Don't commit hs_err files (they name his user folder).

## Presenter 1: Perfect Presentation on DirectX 12

Start only when Emre says so. Same PC, test folder, tools and settings as Pacer 2; about 25 minutes, two launches, no
by-feel part. **Frame times count here**: a quiet PC, no game server.

**What it tests.** Bridge 1.113.0 can show the game through a Direct3D 12 swap chain (`presenter=dx12`, testing only): the same swap chain, frame
latency and blit as today's Direct3D 11 path, with OpenGL handing each frame over through textures and fences it
imports from Direct3D 12 (`GL_EXT_memory_object_win32`, `GL_EXT_semaphore_win32`). It should look and feel exactly
like `presenter=dx11` (today's path, still the default). This run compares the two: frame rate, input latency, Reflex
and GPU pacing on each, and with `presentCheck=true` the DirectX 12 back buffer against the game's own frame, pixel by
pixel.

### Update

```powershell
git -C $T\repo fetch origin
git -C $T\repo checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-*.jar $T\instance\mods\
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
```

The Bridge there is 1.113.0, a test build (not released). It unpacks `cg_present-<hash>.dll` into
`$T\instance\create-galore-dlss` the first time DirectX 12 starts.

### Settings

As Pacer 2 (note the old values, put them back afterwards): `dlaa=dlss`, `perfectPresentation=true` for both launches,
`earlyWindowControl = false`, Distant Horizons `numberOfThreads = 2`. In `config\create_galore_bridge-display.properties`
add `presenter=dx11` for launch 1 and `presenter=dx12` plus `presentCheck=true` for launch 2 (remove all three
afterwards; the game never writes them). Look at the NVIDIA Control Panel and the refresh rate again and write them
down; change nothing.

### Launches

The launch from step 4 (2560x1440), `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and
`--jvm-arg=-Dcreate_galore_dlss_testkit.runs=P1`, the window in front with focus. P1 plays each mode twice, at `shore`
and `canopy` (about 11 minutes):

| Mode | What |
| --- | --- |
| `lat-base` | the game as it is |
| `lat-pacing-on` | GPU pacing On, as players pick it (a share of the frame time) |
| `lat-pacer` | GPU pacing Lowest (the automatic target) |
| `lat-reflex` | Reflex on + late read |
| `lat-cap-default` | 60 fps cap, nothing else switched |

1. **`presenter=dx11`.** The log must have `Perfect Presentation: DXGI swap chain ready on DirectX 11` and
   `Reflex: started on Perfect Presentation's DirectX 11 device`.
2. **`presenter=dx12`, `presentCheck=true`.** The log must have `Perfect Presentation: DirectX 12 on <the card>`,
   `Perfect Presentation: DXGI swap chain ready on DirectX 12` and `Reflex: started on Perfect Presentation's
   DirectX 12 device`, and two `Perfect Presentation check:` lines (about 10 seconds in, on a menu, and later in the
   world). If it says `DirectX 12 could not start, using DirectX 11`, copy that line and the exception under it, and
   stop: the rest would only repeat launch 1. Also look at the game while it runs: the picture right way up, the
   colours as in launch 1, no flicker, no black frames, and Alt-Tab out and back once at the end.

If a launch crashes, copy `hs_err_pid*.log` and the log's last 100 lines, and report.

### Results

`python latency.py <launch 1 folder> <launch 2 folder>`. Write `$T\repo\tools\dlss-testkit\results\presenter1\REPORT.md`:
- whether DirectX 12 started, the `Perfect Presentation:` lines and both `Perfect Presentation check:` lines (exact
  match, or how many pixels differ and by how much);
- per mode and viewpoint, DirectX 12 against DirectX 11: median latency, fps, the queue (median and `q10`), and with
  the pacer its target and wait;
- in one line each: same frame rate (within 2%)? same latency (within 0.5 ms)? does Reflex start and confirm low
  latency on DirectX 12? does GPU pacing On still cost no frames? anything that looked different on screen;
- the `Latency:`, `Reflex:` and `Perfect Presentation` WARN or ERROR lines, the control panel settings and refresh rate.

Commit and push on the DLAA branch. Don't commit hs_err files (they name his user folder).

## Presenter 2: where DirectX 12 loses its 11%

*Done (`results\presenter2\REPORT.md`). Bridge 1.115.0 dropped the switches below (none helped) and the kit dropped
`P2X` and the `p-` modes; `P2` stays. Kept as the record of the run.*

Start only when Emre says so. Same PC, test folder, tools and settings as Presenter 1; about 35 minutes, three
launches, no by-feel part. **Frame times count here**: a quiet PC, no game server. PresentMon needs administrator
rights, so each launch shows Emre one Windows prompt (UAC) to click; tell him before each one.

**What it tests.** Presenter 1 (`results\presenter1\REPORT.md`): DirectX 12 showed the game pixel-exact, but about 11%
fewer frames in every uncapped mode, 0.3–1.5 ms more input latency and lower GPU use, and Reflex's report lost
`G-SYNC fullscreen`. Bridge 1.114.0 (test build) changes one thing by default and adds switches to find the rest:
- **New default:** OpenGL waits for Direct3D 12 on the GPU, before writing a texture again, only when the CPU sees that
  texture's last draw isn't finished (three frames back it almost always is). 1.113.0 waited every frame, which may
  cost a trip through Windows' GPU scheduler each time.
- **Switches** (the test kit's `p-` modes set them live, on top of `lat-base`): `p-wait-always` (the GPU wait every frame,
  as 1.113.0), `p-nvx` (NVIDIA's `GL_NVX_progress_fence` on the same fences, without EXT_semaphore's texture hand-over),
  `p-buffers3` (3 swap chain buffers), `p-latency2` (frame latency 2). At startup only: `presentQueuePriority=high`.
- **Timing:** with `presentTiming=true` the log gets a `Perfect Presentation timing` line at each mode change and every
  10 seconds: GPU timestamps of OpenGL's hand-off (its wait, if any, and the copy), from OpenGL done to Direct3D 12's
  draw starting, and the draw itself.
- **PresentMon** says how Windows shows each frame (`Hardware: Independent Flip` is what G-SYNC and tearing need;
  `Composed: Flip` means the desktop compositor copies every frame) and how long the GPU was busy and idle per frame.
  **nvidia-smi** samples the GPU's utilization and clocks.

### Update

```powershell
git -C $T\repo fetch origin
git -C $T\repo checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-*.jar $T\instance\mods\
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
New-Item -ItemType Directory -Force $T\presenter2 | Out-Null
```

The Bridge there is 1.114.0, a test build (not released). Its `cg_present.dll` is a new build (helper version 2), so
it unpacks under a new name in `$T\instance\create-galore-dlss`.

### Settings

As Presenter 1 (note the old values, put them back afterwards): `dlaa=dlss`, `perfectPresentation=true`,
`earlyWindowControl = false`, Distant Horizons `numberOfThreads = 2`. In `config\create_galore_bridge-display.properties`:

| Launch | Add | Run |
| --- | --- | --- |
| 1 | `presenter=dx11` | `P2` |
| 2 | `presenter=dx12`, `presentTiming=true`, `presentCheck=true` | `P2X` |
| 3 | `presenter=dx12`, `presentTiming=true`, `presentQueuePriority=high` | `P2` |

Remove all of them afterwards (the game never writes them). Look at the NVIDIA Control Panel, the G-SYNC setting and the
refresh rate again and write them down; change nothing.

### Launches

The launch from step 4 (2560x1440), `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and
`--jvm-arg=-Dcreate_galore_dlss_testkit.runs=<run>`, the window in front with focus. Each run plays its modes twice, at
`shore` and `canopy`. P2 is `lat-base`, `lat-reflex` and `lat-cap-default` (about 2 minutes after the world loads); P2X
is `lat-base`, `p-wait-always`, `p-nvx`, `p-buffers3`, `p-latency2`, `lat-reflex` and `lat-cap-default` (about 5).

For each launch `<n>`:
1. **nvidia-smi**, started just before the launch and stopped after the game closes:
   ```powershell
   $smi = Start-Process nvidia-smi -ArgumentList '--query-gpu=timestamp,utilization.gpu,clocks.gr,clocks.mem,power.draw,pstate','--format=csv,nounits','-lms','250','-f',"$T\presenter2\L<n>-gpu.csv" -WindowStyle Hidden -PassThru
   # ... after the game has closed:
   Stop-Process -Id $smi.Id
   ```
2. **PresentMon** (the console build in `$T\tools`, as in profiling 1), attached to the game's own `java.exe` once it
   has started (a few seconds after launch; portablemc's first `java.exe` is short-lived), elevated with
   `Start-Process -Verb RunAs` (Emre clicks the prompt): `--process_id <pid> --output_file $T\presenter2\L<n>.csv
   --qpc_time_ms --timed 900 --terminate_after_timed --terminate_on_proc_exit --stop_existing_session
   --no_console_stats`. Check the CSV grows.
3. The log must have `Perfect Presentation: DXGI swap chain ready on DirectX 11` (launch 1) or `... on DirectX 12`
   (launches 2 and 3) and `Reflex: started on Perfect Presentation's DirectX <11 or 12> device`. On DirectX 12 also
   `Perfect Presentation: DirectX 12 on <the card> (..., queue priority <normal or high>, GL_NVX_progress_fence
   <available or not available>)`. If it says `DirectX 12 could not start, using DirectX 11`, copy that line and the
   exception under it, and stop.
4. Launch 2 must have a `Perfect Presentation: DirectX 12 hand-off:` line for each switch as its mode starts, and a
   `Perfect Presentation check:` line two times at the start and once 300 frames after each switch: each should say
   `matches the game's frame exactly`. A `pixels differ` line is a finding: copy it whole.

If a launch crashes, copy `hs_err_pid*.log` and the log's last 100 lines, and report.

### Results

```powershell
$py = "$T\tools\uv\uv.exe"
$shots = "$T\instance\screenshots\dlaa-test"
& $py run --python 3.13 $T\repo\tools\dlss-testkit\latency.py $shots\<L1 run> $shots\<L2 run> $shots\<L3 run>
foreach ($n in 1, 2, 3) {   # one launch at a time: frametimes.py would pool the launches' same-named modes
  & $py run --python 3.13 $T\repo\tools\dlss-testkit\frametimes.py $shots\<L$n run> $T\presenter2\L$n.csv
}
& $py run --python 3.13 $T\repo\tools\dlss-testkit\gpustats.py $shots\<L1 run> $T\presenter2\L1-gpu.csv $shots\<L2 run> $T\presenter2\L2-gpu.csv $shots\<L3 run> $T\presenter2\L3-gpu.csv
```

Write `$T\repo\tools\dlss-testkit\results\presenter2\REPORT.md`:
- **Present mode** per launch and mode (PresentMon's most common, with its share). Does DirectX 12 get `Hardware:
  Independent Flip` like DirectX 11? If not, which mode, and does any switch change it?
- **Per mode and viewpoint**, each launch against launch 1 (DirectX 11): fps, input latency (median), PresentMon's GPU
  busy and GPU wait per frame and time until displayed, and nvidia-smi's GPU utilization and graphics clock.
- **The new default:** launch 2's `lat-base` against launch 1's `lat-base` and against launch 2's `p-wait-always` (the
  1.113.0 way). How much of the 11% is gone?
- **Each switch** (`p-nvx`, `p-buffers3`, `p-latency2`, and launch 3's high priority): fps and latency against launch 2's
  `lat-base`, in one line each.
- **The timing lines**, per mode (match them to the `DLAA test: mode` lines by time): OpenGL hand-off, OpenGL done to
  DirectX 12 draw start and the draw, medians and 90th percentiles, how often OpenGL still waited, and the clocks'
  match. Is OpenGL done to draw start under 0.2 ms in any mode?
- **Reflex's report** on each launch: does it say `G-SYNC fullscreen`?
- The `Perfect Presentation`, `Latency:` and `Reflex:` WARN or ERROR lines, every `Perfect Presentation check:` line,
  the control panel settings, G-SYNC setting and refresh rate.

Keep the PresentMon and nvidia-smi CSVs in `$T\presenter2` (they stay on the PC). Commit the report and push on the DLAA
branch. Don't commit hs_err files (they name his user folder).

## Presenter 3: DirectX 11 against DirectX 12 without capture tools

Start only when Emre says so. Same PC, test folder, tools and settings as Presenter 2, but **no PresentMon and no
nvidia-smi**: no administrator prompts. About 20 minutes, four launches, no by-feel part. **Frame times count here**: a
quiet PC, no game server, nothing else started.

**What it tests.** In Presenter 2 (`results\presenter2\REPORT.md`) DirectX 12 ran exactly as in Presenter 1 (about
152 / 138 fps at `lat-base`), but DirectX 11 ran 5–10% below every earlier session (160 / 140 against 170–171 /
154–156). The only new thing was the capture tools. DirectX 12 was shown not to care about PresentMon; DirectX 11
wasn't. So the real gap is either today's 1–5% (if the PC changed) or still about 10% (if the tools slow DirectX 11
only). Alternating the two presenters with no tools settles it.

### Update

Nothing to update: Bridge 1.114.0 and the test kit from Presenter 2 stay (check `git -C $T\repo log -1` is at or after
`4f0d9385`, and the instance's mods have `create-galore-bridge-1.114.0.jar`).

### Settings

As Presenter 2 (note the old values, put them back afterwards): `dlaa=dlss`, `perfectPresentation=true`,
`earlyWindowControl = false`, Distant Horizons `numberOfThreads = 2`. In `config\create_galore_bridge-display.properties`
only `presenter=dx11` or `presenter=dx12`, nothing else of the test keys (no `presentTiming`, `presentCheck` or
`presentQueuePriority`). Remove `presenter` afterwards.

### Launches

The launch from step 4 (2560x1440), `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and
`--jvm-arg=-Dcreate_galore_dlss_testkit.runs=P2`, the window in front with focus, in this order:

| Launch | Presenter |
| --- | --- |
| 1 | `dx11` |
| 2 | `dx12` |
| 3 | `dx11` |
| 4 | `dx12` |

- Check that nothing else is running that wasn't in Presenter 2's list (PresentMon and nvidia-smi must not be: look for
  `PresentMon*` and `nvidia-smi` processes, and a leftover `PresentMon` ETW session with `logman query -ets`; if one is
  left, say so, and stop it only with Emre's OK).
- The log must have `DXGI swap chain ready on DirectX 11` or `... on DirectX 12` to match the launch.
- **Only if** the DirectX 11 launches come out at least 5% faster than Presenter 2's DirectX 11 (160 / 140 fps at
  `lat-base`, 165 / 142 at `lat-reflex`): a fifth launch, `dx11` with only nvidia-smi running (as in Presenter 2's step
  1, no PresentMon, no prompt), to tell which tool cost the frames.

If a launch crashes, copy `hs_err_pid*.log` and the log's last 100 lines, and report.

### Results

`latency.py` on all four (or five) run folders. Write `$T\repo\tools\dlss-testkit\results\presenter3\REPORT.md`:
- per mode and viewpoint and launch: fps and latency (median, and first plays), the queue;
- the gap: DirectX 12 against DirectX 11, the two launches of each averaged, for `lat-base` and `lat-reflex`, in fps
  and in ms per frame;
- in one line each: is DirectX 11 back at about 170 / 155 (Presenter 1 and earlier)? Is DirectX 12 still at about 152 /
  138? So is the real gap about 10% or about 2–4%? With the fifth launch: did nvidia-smi alone cost DirectX 11 frames?
- the `Perfect Presentation`, `Latency:` and `Reflex:` WARN or ERROR lines, and whether Reflex's report says `G-SYNC
  fullscreen` on both.

Commit and push on the DLAA branch. Don't commit hs_err files (they name his user folder).

## Pacer 3: GPU pacing on DirectX 12

Start only when Emre says so. It isn't urgent: it's for the day DirectX 12 becomes Perfect Presentation's default.
Same PC, test folder and settings as Presenter 3, **no PresentMon and no nvidia-smi** (no administrator prompts).
About 55 minutes, four launches, no by-feel part. **Frame times count here**: a quiet PC, no game server, nothing else
started.

**What it tests.** GPU pacing (the Display tab's GPU pacing, `gpuPacing`) was tuned on DirectX 11 in Pacer 2
(`results\pacer2\REPORT.md`):
- **On** lets a frame queue on the GPU for 65% of the frame time, at least 4 ms, with Perfect Presentation (80%, at
  least 5 ms, without). On the RTX 4080 at about 165 fps that is about 4 ms. It cost no frames there (+3 / 0%) and
  cut the latency from 11.9 to 9.8 ms.
- **Lowest** sets the target from how much the frames vary (about 1–2.5 ms). It cut the latency to 8.4 ms for about
  6% fewer frames.

DirectX 12 (Bridge 1.115.0, pack 0.5.9, the Display tab's DirectX setting) hands each frame to Direct3D 12 after
OpenGL finishes it, so the GPU gets a little more work per frame and the frames queue a little differently. Presenter 3
measured about 1 ms less queue on DirectX 12 at `lat-base`, and Presenter 1 (before the 1.114.0 fix) saw Lowest's
target swing more on DirectX 12 (1.8–4.0 ms against 0.6–2.3). This run measures the same targets on both presenters,
alternating, to tell whether On's rule and Lowest still fit on DirectX 12 or need their own numbers there.

### Update

```powershell
git -C $T\repo fetch origin
git -C $T\repo checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-*.jar $T\instance\mods\
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
```

The Bridge is 1.115.0 (released in pack 0.5.9). Its test keys `presentGlWait`, `presentSync`, `presentFrameLatency`,
`presentBuffers` and `presentQueuePriority` are gone: if any is still in the test folder's display settings, remove
it. The kit's first run in each game clears any test scene an earlier game left in the world (the log says
`cleared the test scenes`); there's no need to remove scenes by hand.

### Settings

As Presenter 3 (note the old values, put them back afterwards): `dlaa=dlss`, `perfectPresentation=true`,
`earlyWindowControl = false`, Distant Horizons `numberOfThreads = 2`, `gpuPacing=off` (the kit's modes switch the
pacer themselves). The API is the setting `presenter=dx11` or `presenter=dx12` in
`config\create_galore_bridge-display.properties` (the Display tab's DirectX). Set it in the file before each launch, as
the table says; no other test keys. Look at the NVIDIA Control Panel and the refresh rate again and write them down;
change nothing.

### Launches

The launch from step 4 (2560x1440), `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and
`--jvm-arg=-Dcreate_galore_dlss_testkit.runs=L7`, the window in front with focus, in this order:

| Launch | `presenter` |
| --- | --- |
| 1 | `dx11` |
| 2 | `dx12` |
| 3 | `dx11` |
| 4 | `dx12` |

L7 plays each mode twice at `shore` and `canopy` (about 11 minutes):

| Mode | What |
| --- | --- |
| `lat-base` | the game as it is |
| `lat-pacing-on` | GPU pacing On (65% of the frame time, at least 4 ms) + late read |
| `lat-pacer` | GPU pacing Lowest (the automatic target) + late read |
| `lat-pacer-3000` | the pacer at 3000 us + late read |
| `lat-pacer-5000` | the pacer at 5000 us + late read |
| `lat-reflex` | Reflex on + late read, for comparison |

- The log must have `DXGI swap chain ready on DirectX 11` or `... on DirectX 12` to match the launch, and the run's
  `manifest.json` has `"presenter"` with the same.
- It must also have `Latency: GPU pacer on, frames may queue on the GPU a share of the frame time that keeps the frame
  rate`, `... as much as they vary`, `... 3000 us on the GPU`, `... 5000 us on the GPU` and `Reflex: started on
  Perfect Presentation's DirectX 12 device` (or `DirectX 11`). Copy any `Perfect Presentation`, `Latency:` or `Reflex:` WARN or ERROR line.
- Check that PresentMon and nvidia-smi aren't running, as in Presenter 3.

If a launch crashes, copy `hs_err_pid*.log` and the log's last 100 lines, and report.

### Results

`python latency.py` on the four run folders (each folder's header line names its presenter). Write
`$T\repo\tools\dlss-testkit\results\pacer3\REPORT.md`:
- per mode, viewpoint and launch: fps and its change against that launch's `lat-base`, latency (median and 90th
  percentile), the queue (median and `q10`), and with the pacer its target, wait and how late the wait ended;
- per presenter, the two launches averaged: for each pacer mode the fps change and the latency against `lat-base`;
- in one line each, for DirectX 12 against DirectX 11:
  - **On:** does it still keep the frame rate (within about 2% of `lat-base`) on DirectX 12, and at what latency? Which
    of 3000, On (about 4000) and 5000 is the shortest target that keeps the frame rate on each presenter?
  - **Lowest:** its fps cost and latency on each, and how much its target swings (the target column over the plays);
  - **Reflex:** about the same on both, as in Presenter 3?
  - **the queue at `lat-base`:** still about 1 ms shorter on DirectX 12?
- the `Perfect Presentation`, `Latency:` and `Reflex:` lines, the control panel settings and refresh rate.

What comes of it: if DirectX 12's shortest frame-keeping target differs from DirectX 11's by more than about 0.5 ms,
On gets its own rule on DirectX 12 (`GpuPacer.keepingFrameRate` per presenter); if Lowest's cost there is clearly
higher, its margin gets looked at. Otherwise nothing changes.

Commit and push on the DLAA branch. Don't commit hs_err files (they name his user folder).
