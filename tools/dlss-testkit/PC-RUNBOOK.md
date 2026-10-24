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

- `$T\instance\config\create_galore_bridge-display.properties`: `dlaa=true`, `perfectPresentation=false`.
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


## Milestone 2, PC run 1: things that move

Same PC, test folder and tools as rounds 1 and 2, nothing new to install. Start it only when Emre says so. Photon's
copy now gives DLSS the motion of things that move by themselves: waving leaves and grass, Sable ships, and Create's
machines, contraptions and trains (through Colorwheel). The cloud checked that this motion is exact; this run checks
it on the 4080 with NVIDIA's shader compiler and real DLSS, and Emre judges the look by eye, riding a ship.

### Update

Keep `$T\instance` and `saves\dlaa-test` (the far terrain stays). The two jars change (the Bridge is now 1.91.0, with
pack 0.4.75's fixes; nothing else in the pack changed, so the old Bridge jar goes and the new one comes in), and the
test kit's config is written fresh, since it needs the new `moving` viewpoint:

```powershell
git -C $T\repo pull
Remove-Item $T\instance\mods\create-galore-bridge-*.jar
Copy-Item $T\repo\pack\mods\create-galore-bridge-1.91.0.jar $T\instance\mods\ -Force
Copy-Item $T\repo\tools\dlss-testkit\dist\create-galore-dlss-testkit-1.0.0.jar $T\instance\mods\ -Force
Remove-Item $T\instance\config\create_galore_dlss_testkit.json
```

Check before the runs, as in round 2: `dlaa=true` and `perfectPresentation=false` in
`config\create_galore_bridge-display.properties`, `earlyWindowControl = false`, `tutorialStep:none`, the Distant
Horizons settings from step 3, and the NVIDIA App's DLSS override still off.

The first launch remakes the Photon copy (the patch changed): its log has `DLAA: made shaderpacks/Photon v1.3a +
DLAA from photon_v1.3a + Clrwl_1.0.5.zip`. Every run's log must have no `Could not compile shader`, no `this
Colorwheel's shaders are not the ones the Bridge knows`, and the chat no `[Colorwheel] Current shader pack contains
broken shaders`. If any of those shows, stop and report the lines around it.

The `moving` viewpoint looks out over the lake from the shore. When a run first gets there, the test kit builds a
moving scene over the water (a creative motor turning shafts, a cogwheel, a large cogwheel and a fan; a second motor
turning a bearing with a glued cross; a small Sable ship that turns and sways) and removes it when the run ends.

### Runs

The launch command from step 4, with these JVM arguments (written as in step 5):

| Run | Before it | Extra JVM arguments |
| --- | --- | --- |
| J | `perfectPresentation=false` | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+dlaa-camera+fsr3` `-Dcreate_galore_dlss_testkit.viewpoints=moving` |
| K | the config edit below | `...autorun=dlaa-test` `...modes=debug-motion+debug-residual` `...viewpoints=canopy+moving` |

`dlaa-camera` is DLAA with only the camera's motion (as before this milestone), so J shows what the new motion
vectors change. Before K, set these in `config\create_galore_dlss_testkit.json` (written by run J): `"paths":
["brief"]`, `"packSecondsPerFrame": 1.0`, `"dumpRaw": true`, `"shipTurnDegreesPerTick": 20.0`, `"shipSwayPerTick":
0.5`. K saves DLSS's exact inputs for 8 frames in a row (`debug-residual\raw\*.npy`, about 200 MB); leaves and the
ship move fast so each frame moves them by a few pixels. After K, delete the config file again.

### By eye, with Emre (about 15 minutes)

Launch without the `autorun` argument, open the `dlaa-test` world, and give Emre these chat commands:

- `/dlaatest scene build`: puts the moving scene up over the lake, at the game's normal speed. The ship turns
  slowly; stand on it to ride it.
- `/dlaatest mode dlaa-default`, `/dlaatest mode dlaa-camera`, `/dlaatest mode photon-taa`, `/dlaatest mode fsr3`:
  switch anti-aliasing (the shaders reload, a few seconds).
- `/dlaatest scene remove` when done, then `/dlaatest mode dlaa-default`.

Emre looks at the cogs, the fan, the bearing's cross and the ship, from the shore and riding the ship, in each mode.
If he wants, he builds a short train in creative there and rides it too. Write down what he says, especially about
the ship's glass block and anything else that smears or trails.

### Results

```powershell
& $T\tools\uv\uv.exe run --python 3.13 --with pillow --with numpy $T\repo\tools\dlss-testkit\metrics.py <run J folder>
& $T\tools\uv\uv.exe run --python 3.13 --with numpy $T\repo\tools\dlss-testkit\mvcheck.py <run K folder>
```

`mvcheck.py` moves each frame back along the motion vectors scaled by k; with exact motion vectors k = 1 matches best
on every pair of frames (the cloud: 55 to 68% less difference than with none). Write
`$T\repo\tools\dlss-testkit\results\m2-run1\REPORT.md`: both outputs, the `DLAA:` log lines, any Colorwheel or shader
lines, frame times of J's modes, and Emre's notes from the session. Add crops (about 512x512) around the cogwheels,
the bearing's cross and the ship from J's flick and still frames for all four modes under `results\m2-run1\J\`, a few
MB in all. Commit and push on the DLAA branch (no `raw\` files).

## Milestone 2, PC run 2: entities

Same PC, test folder and tools, nothing new to install. Start it only when Emre says so, after run 1. Photon's copy
now also gives DLSS the motion of what the game draws vertex by vertex: mobs and players with their armor and held
items, GeckoLib models, dropped items, block entities (chest lids, bells) and the first-person hand. The Bridge adds an
attribute to Iris's entity vertex format with each vertex's position last frame. The cloud checked it exact for a
walking villager, an armor stand in iron armor with a sword, a GeckoLib red panda, a spinning dropped sword, a chest
opening and closing, a ringing bell and the swinging hand.

### Update

Same as run 1's update: pull, swap the Bridge jar (`Remove-Item` the old one first), copy the kit jar, delete the kit's
config. The first launch remakes the Photon copy again (patch 5). The same log checks as run 1, plus: no `Missing
elements in vertex` anywhere (a crash), and no `DLAA: entity motion attribute at` warning.

The moving scene now also has, in front: a villager and an armor stand walking in circles, a red panda (Critters and
Companions) walking in a circle at the front left, a diamond sword spinning in the air, a bell that keeps ringing and
a chest that keeps opening and closing. The test world is peaceful, so the armor is on an armor stand.

### Runs

| Run | Before it | Extra JVM arguments |
| --- | --- | --- |
| L | `perfectPresentation=false` | `...autorun=dlaa-test` `...modes=photon-taa+dlaa-default+dlaa-camera+fsr3` `...viewpoints=moving` |
| M | the config edit below | `...autorun=dlaa-test` `...modes=debug-motion+debug-residual` `...viewpoints=moving` |
| N | `"showHand": true` too | `...autorun=dlaa-test` `...modes=debug-residual` `...viewpoints=moving` |

Before M, set in `config\create_galore_dlss_testkit.json` (written by run L): `"paths": ["brief"]`,
`"packSecondsPerFrame": 1.0`, `"dumpRaw": true`, `"shipTurnDegreesPerTick": 0.0` and `"shipSwayPerTick": 0.0` (the
ship stands still, so the entities are easier to tell apart; at 20 ticks a second and 2560x1440 the walkers still
move a few pixels per frame). Before N, add `"showHand": true`: the hand shows (with the HUD) holding a sword and
swings. After N, delete the config file again.

Compare L's frame times of `dlaa-default` and `dlaa-camera`: the difference is what working out the entities' motion
costs the CPU (the cloud cannot measure it).

### By eye, with Emre (about 15 minutes)

As in run 1: `/dlaatest scene build`, then switch modes with `/dlaatest mode ...` and look at the walkers, the panda,
the spinning sword, the chest and the bell, from the shore and close up. Then Emre swings his own sword and walks and
looks around holding things (a sword, a block, a map), and if he wants spawns a few animals with spawn eggs and lets
them wander (afterwards `/kill @e[type=!player,distance=..64]`). Write down what he says, especially anything that
smears, trails or flickers on entities or the hand.

### Results

```powershell
& $T\tools\uv\uv.exe run --python 3.13 --with pillow --with numpy $T\repo\tools\dlss-testkit\metrics.py <run L folder>
& $T\tools\uv\uv.exe run --python 3.13 --with numpy $T\repo\tools\dlss-testkit\mvcheck.py <run M folder> panda=0,880,640,1400 chest=340,760,620,1080 stand=1800,940,2400,1400 bell=2160,880,2320,1040 villager=940,660,1140,1020 item=1360,680,1540,880
& $T\tools\uv\uv.exe run --python 3.13 --with numpy $T\repo\tools\dlss-testkit\mvcheck.py <run N folder> hand=1280,720,2560,1440
```

The boxes are for a 2560x1440 window (scale them for another size); each is one thing's moving pixels, checked apart
from the rest. With exact motion vectors k = 1 matches best in every table (sub-pixel motion, like the bell's quiet
end, is too small to tell). Write `$T\repo\tools\dlss-testkit\results\m2-run2\REPORT.md` as for run 1 (outputs, log
lines, frame times, Emre's notes) with crops around the walkers, the panda, the chest and the hand from L under
`results\m2-run2\L\`. Commit and push on the DLAA branch (no `raw\` files).
