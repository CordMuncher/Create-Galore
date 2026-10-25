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

Keep `$T\instance`, its world and its settings as they are. Check before the runs: `config\create_galore_bridge-display.properties`
has `dlaa=true` and `perfectPresentation=false`, and `nvngx_dlss.dll` (310.9.16) is still in
`$T\instance\create-galore-dlss`. The kit's config gets the new modes added on the first run. Each mode sets
Photon's `TAAU` and `TAAU_RENDER_SCALE` itself, and the run puts the pack's options back when it ends.

### Runs

Write each argument as `--jvm-arg=-D...=...` (`...` = `create_galore_dlss_testkit`), as in step 5.

| Run | Extra JVM arguments |
| --- | --- |
| U1 | `...autorun=dlaa-test` `...modes=perf-taau+perf-dlss-K+perf-dlss-M+perf-dlss-L+perf-fsr3+dlaa-default` |
| U2 | `...autorun=dlaa-test` `...modes=ultra-taau+ultra-dlss-K+ultra-dlss-M+ultra-dlss-L+ultra-fsr3` |
| U3 | `...autorun=dlaa-test` `...modes=bal-taau+bal-dlss-K+bal-dlss-M+bal-dlss-L+bal-fsr3` |
| U4 | `...autorun=dlaa-test` `...modes=q-taau+q-dlss-K+q-fsr3` |
| U5 | `...autorun=dlaa-test` `...modes=perf-taau+perf-dlss-K+perf-dlss-M+perf-fsr3` `...viewpoints=moving` `...paths=brief` |
| U6 | `...autorun=dlaa-test` `...modes=perf-dlss-K+perf-dlss-K-bias+perf-fsr3+perf-fsr3-bias` `...quick=true` |
| U7 | `...autorun=dlaa-test` `...modes=perf-debug-color+perf-debug-motion` `...quick=true` |

For U5 use the moving-scene settings from the milestone 2 session (`sceneTickRate` 20, `showHand` true).

The log of each run must have, for each upscaling mode:

- `DLAA: on for this shader pack, engine dlss, DLSS preset K, upscaling from 50% of the window` (the preset and the
  scale of that mode; `engine fsr3` for the FSR modes);
- `DLAA: DLSS running at 1280x720 to 2560x1440 (Performance, 50%), preset K, the pack's exposure` (M and L say
  `DLSS meters the exposure`), or `DLAA: FSR 3.1 running at 1280x720 to 2560x1440 (Performance, 50%)`;

and no `DLAA: stopped`, `DLSS could not start` or `Failed to create shader rendering pipeline`. The `X-taau` modes
have no DLAA lines of their own: Photon's TAAU runs. If a DLSS mode stops, copy the whole `DLAA:` line and the end
of `create-galore-dlss\nvsdk_ngx.log` and go on with the other runs.

What to look at:

- U1 to U4: each upscaler against `X-taau` at the same scale: sharpness of textures, leaves and fences, shimmer on
  the still path, smearing after the flick, ghosting behind moving things. Frame times come from "Frame rate"
  below; `frameMs` in `manifest.json` is only rough here, as saving screenshots holds up frames. The 4080 is an Ada
  card, where L and M run fast; NVIDIA says they cost much more on RTX 20 and 30 cards (no FP8), so their frame times
  here say nothing about those.
- U5: things that move at Performance.
- U6: NVIDIA's full mip bias (one more mip level of detail than the render scale's) against the scale's own: sharper
  textures, or more shimmer and moiré on the blocks' pixel art?
- U7: what DLSS gets, the render-sized frame stretched over the window: the whole view, no black or doubled edges,
  motion vectors as in milestone 2.

### By eye, with Emre

Open `compare.html` on the U1 to U4 run folders and go through them with Emre: each upscaler against `X-taau` at one
scale at a time. He judges the look; write down what he says.

### Frame rate (PresentMon only)

Frame rate only, cheaply: no Nsight, no Java Flight Recorder. Run it after U1 to U7, on a quiet PC as in profiling 1
(Emre closes everything else; note what stayed open).
- The setup from "Profiling 1", Setup: `enableVsync:false`, `maxFps:260` (unlimited), `numberOfThreads = 2` in
  `DistantHorizons.toml`, `perfectPresentation=false`, and `"screenshots": false` in the kit's config.
- PresentMon as in profiling 1 (the same build in `$T\tools`): attach to the game's own `java.exe`, elevated, with
  `--process_id <pid> --output_file $T\profiling\F1.csv --qpc_time_ms --timed 600 --terminate_after_timed
  --stop_existing_session --no_console_stats`. If the game's `run finished` line comes after PresentMon stopped, run
  it again with a longer `--timed`.

| Run | Extra JVM arguments |
| --- | --- |
| F1 | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+fsr3+perf-taau+perf-dlss-K+perf-dlss-M+perf-dlss-L+perf-fsr3+ultra-taau+ultra-dlss-K+ultra-dlss-M+ultra-dlss-L+ultra-fsr3` `...viewpoints=shore+canopy` |
| F2 | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+fsr3+q-taau+q-dlss-K+q-fsr3+bal-taau+bal-dlss-K+bal-dlss-M+bal-dlss-L+bal-fsr3` `...viewpoints=shore+canopy` |

- `frametimes.py` on each run folder with its CSV, as in profiling 1's Analysis.
- The native modes are the baselines each upscaler is measured against: Photon's TAA (`photon-taa`), DLAA
  (`dlaa-default`) and FSR 3.1 at full resolution (`fsr3`). They also line up with profiling 1 (shore 5.43, 6.20 and
  6.17 ms, canopy 5.87, 7.31 and 7.20 ms, in `results\profiling-1\REPORT.md`). If they are within about 0.8 ms of
  those, the runs compare; if not, say so next to the tables.
- The PC was GPU-bound with DLAA, but Photon's TAA was about half CPU-bound at about 5–7 ms a frame. So expect the
  low scales to run into the CPU: report the share of GPU-bound frames per mode, as profiling 1 did.
- Put the settings back afterwards as profiling 1 did ("Afterwards" in its report), and `"screenshots": true`.

### Results

`metrics.py` for each run folder. Write `$T\repo\tools\dlss-testkit\results\upscale1\REPORT.md`: the metrics tables
per scale, the frame rate tables of F1 and F2 (median and p90 frame time, fps, GPU-bound share, per scale against
`X-taau` and the three native modes), the `DLAA:` lines, anything that looked wrong, and Emre's verdicts; crops of the same spots
as round 1 (about 512x512, from the settled still frame) for each mode of U1 under `results\upscale1\U1\`, a few MB
in all. Commit and push on the DLAA branch.
