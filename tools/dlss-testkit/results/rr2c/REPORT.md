# Ray Reconstruction 2c: RR off no longer breaks frame generation, and Photon's settings switch right

A PC session on 2026-10-27, 22:45–23:30, run by a Claude Code session in `C:\Users\<user>\Documents\Claude\MC-CREATE`
(`$T`).
- **What it followed:** "Ray Reconstruction 2c" in `tools/dlss-testkit/PC-RUNBOOK.md` at `40a8daaf`, with Bridge
  1.126.0 (pack 0.6.3, the release candidate).
- **Part 1** (the probe) ran on its own, **part 2** with the player at the keyboard, and **part 3** (T2) on its own.
- NVIDIA's DLLs are the ones Ray Reconstruction 1 fetched. The NVIDIA app had no overrides on.

## Summary

- **2b's bug is fixed.** Turning RR off while DLSS frame generation runs now logs `DLAA: closed its DirectX 12 queue;
  NGX stays started on the device, which frame generation may share`, and frame generation runs on.
  - Part 2 switched RR 24 times (12 off, each logging that line), with frame generation on between many of them,
    including:
    - 88 s of play with RR off, F11 twice and the pause menu (the spot where 2b hung);
    - a pause 120 ms after frame generation came back.
  - There was **not one `[sl.dlss_g]` line** in the whole session, and no freeze or crash.
  - The player: "done no crash at any point".
- **The probe catches the bug outside the game.**
  - The new `dll-d3d12-beside` passes all 3 rounds.
  - With 2b's `cg_ngx.dll`, it crashes in round 1, when the other NGX user runs its DLSS after `cg_ngx.dll` closed.
    That's an access violation in NVIDIA's driver rather than an NGX error code, but it is 2b's bug.
- **Photon's settings switch with RR exactly as described.** The toasts, Photon's profile and the two settings files
  were right at every step:
  - Ultra the first time;
  - the player's own changes under RR after that;
  - the player's settings back with RR off;
  - the RR file gone once it matches Ultra.
  - The player: "Everything worked as expected. All my settings were maintained and whatnot."
- **Step 3, with frame generation, all at Photon Ultra** (the player's choice: "that's how I would actually play"):

  | setting | rendered fps | shown fps |
  | --- | --- | --- |
  | RR Performance | 106 | ~212 |
  | RR off, DLSS Quality | 112 | ~224 |
  | RR Balanced | 99 | ~197 |

  His pick: "Out of the 3 I would play rr balanced but in my own world probably rr quality since the 4080 super has
  the headroom and it looks a hair better".
- **Part 3, T2 (frame generation on):** every switch from RR to DLSS went through without a `[sl.dlss_g]` line.
  - Frame generation shows 2 frames per rendered one in every mode. It costs 18–29% of the rendered frames (about
    2 ms of GPU time each), so the screen gets 41–65% more frames than T1 (2b, no frame generation).
  - **RR Performance with frame generation doesn't quite reach DLSS Quality with frame generation:** 243 against 268
    shown per second at `shore`, 238 against 253 at `canopy` (6–9% fewer).

## Set-up

- **Branch:** `claude/project-thread-u2wmhh` at `40a8daaf`.
- **Jars:** `create-galore-bridge-1.126.0.jar` (3,293,422 bytes) and the test kit (56,850 bytes, unchanged).
- **Backups:** everything the runbook lists, copied to `$T\rr2c\*.before`. All match 2b's backups (the originals).
- **Photon's settings files:** neither `config\create_galore_bridge-photon-own.txt` nor
  `config\create_galore_bridge-photon-rr.txt` existed before.
- **PresentMon helper for part 3:**
  - `$T\profiling\scripts\rr2c-pm.ps1` (2b's, writing to `$T\rr2c`, for T2 only);
  - elevated at 22:48 (the player clicked the prompt).

## Part 1: the probe

`cg_ngx.dll`: the new one is 237,568 bytes; 2b's, from `d79d2cf5`, is 238,080 bytes. Both runs took about 27 s.

**New `cg_ngx.dll`** (`summary.txt`, whole; the paths are under `$T`):

```
rr-probe: DLSS Ray Reconstruction alone, nvngx DLLs from $T\rr2c\new
d3d11-dlss               OK: ran 3 frames; output red mean 0.4977 over 2544x1401, 0 pixels not finite
d3d11-game               CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFECCE0EA2, reading address 0000000000000034 ($T\rr2c\new\nvngx_dlssd.dll)
d3d11-no-hit-distance    CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFECCE0EA2, reading address 0000000000000034 ($T\rr2c\new\nvngx_dlssd.dll)
d3d11-roughness-apart    CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFECCE0EA2, reading address 0000000000000034 ($T\rr2c\new\nvngx_dlssd.dll)
d3d11-own-parameters     CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFECCE0EA2, reading address 0000000000000034 ($T\rr2c\new\nvngx_dlssd.dll)
d3d11-even-size          CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFECCE0EA2, reading address 0000000000000034 ($T\rr2c\new\nvngx_dlssd.dll)
d3d11-level-11-1         CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFECCE0EA2, reading address 0000000000000034 ($T\rr2c\new\nvngx_dlssd.dll)
d3d12-dlss               OK: ran 3 frames; output red mean 0.4977 over 2544x1401, 0 pixels not finite
d3d12-game               OK: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
dll-d3d12-dlss           OK: ran 3 frames; output red mean 0.4982 over 2544x1401, 0 pixels not finite
dll-d3d12-game           OK: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
dll-d3d12-beside         OK: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
```

**2b's `cg_ngx.dll`** (`summary.txt`): the same as the new one down to `dll-d3d12-game`, then:

```
dll-d3d12-game           OK: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
dll-d3d12-beside         CRASHED at: round 1: the other NGX user evaluates its DLSS; code 0xC0000005 at 00007FF8970AAB58, reading address 0000023C5558E714 (C:\WINDOWS\System32\DriverStore\FileRepository\nvmdi.inf_amd64_71355298a494368d\_nvngx.dll)
```

**`dll-d3d12-beside.txt`, new**: its own steps, without NGX's log lines.

```
variant: dll-d3d12-beside (the same, closed and opened again 3 times beside another NGX user's DLSS on the same device)
step: load cg_ngx.dll
info: cg_ngx.dll ABI 4
step: device
info: adapter NVIDIA GeForce RTX 4080 SUPER
step: the other NGX user: init
info: DLSS available 1, init result 0x00000001, needs a newer driver 0 (minimum 470.0)
step: NGX init
info: Ray Reconstruction available 1, init result 0x00000001, needs a newer driver 0 (minimum 537.2)
step: shared textures
step: the other NGX user: create DLSS and evaluate
step: create Ray Reconstruction, preset F
step: evaluate frame 1
step: evaluate frame 2
step: evaluate frame 3
step: read back
result: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
step: round 1: cg_ngx.dll closes
step: round 1: the other NGX user evaluates its DLSS
step: round 1: the other NGX user makes its DLSS again
info: round 1: the other NGX user's DLSS ran on after cg_ngx.dll closed, and was made again
step: round 2: cg_ngx.dll opens again
step: create Ray Reconstruction, preset F
step: evaluate frame 1
step: evaluate frame 2
step: evaluate frame 3
step: read back
result: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
step: round 2: cg_ngx.dll closes
step: round 2: the other NGX user evaluates its DLSS
step: round 2: the other NGX user makes its DLSS again
info: round 2: the other NGX user's DLSS ran on after cg_ngx.dll closed, and was made again
step: round 3: cg_ngx.dll opens again
step: create Ray Reconstruction, preset F
step: evaluate frame 1
step: evaluate frame 2
step: evaluate frame 3
step: read back
result: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
step: round 3: cg_ngx.dll closes
step: round 3: the other NGX user evaluates its DLSS
step: round 3: the other NGX user makes its DLSS again
info: round 3: the other NGX user's DLSS ran on after cg_ngx.dll closed, and was made again
step: release
```

**`dll-d3d12-beside.txt`, 2b's**:

```
variant: dll-d3d12-beside (the same, closed and opened again 3 times beside another NGX user's DLSS on the same device)
step: load cg_ngx.dll
info: cg_ngx.dll ABI 4
step: device
info: adapter NVIDIA GeForce RTX 4080 SUPER
step: the other NGX user: init
info: DLSS available 1, init result 0x00000001, needs a newer driver 0 (minimum 470.0)
step: NGX init
info: Ray Reconstruction available 1, init result 0x00000001, needs a newer driver 0 (minimum 537.2)
step: shared textures
step: the other NGX user: create DLSS and evaluate
step: create Ray Reconstruction, preset F
step: evaluate frame 1
step: evaluate frame 2
step: evaluate frame 3
step: read back
result: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
step: round 1: cg_ngx.dll closes
step: round 1: the other NGX user evaluates its DLSS
crash: code 0xC0000005 at 00007FF8970AAB58, reading address 0000023C5558E714 (C:\WINDOWS\System32\DriverStore\FileRepository\nvmdi.inf_amd64_71355298a494368d\_nvngx.dll)
```

- **New:** as the runbook expects. The six `d3d11-` RR variants crash as in Ray Reconstruction 2, the rest are `OK`,
  and `dll-d3d12-beside` is `OK`. Each of its 3 rounds ends with `the other NGX user's DLSS ran on after cg_ngx.dll
  closed, and was made again`.
- **2b's:** fails at `round 1: the other NGX user evaluates its DLSS`.
  - The runbook expected an NGX code such as `0xBAD00004`. Here it's a crash instead: an access violation inside
    the driver's `_nvngx.dll`, reading a freed address.
  - Either way it's the bug: once `cg_ngx.dll` shut NGX down, the other user's DLSS was gone.

## Part 2: frame generation and Photon's settings

- **Settings as written:**
  - `dlaa=dlss`, `dlaaPreset=default`, `dlaaRayReconstruction=false`, `perfectPresentation=true`, `presenter=dx12`,
    `reflex=on`, `gpuPacing=off`, `frameGen=dlss`, `frameGenType=dlss`, `fpsCounter=detailed`;
  - windowed, 260 fps cap, no vsync;
  - the release DLL in the instance.
- **Render resolution** set to Performance in the pack's options file (`TAAU=true`, `TAAU_RENDER_SCALE=0.50`), as in
  2b. Game launched at 22:49.
- **Photon's profile** is from Iris's own log line (`Profile: ...`) at every switch, and from what the player saw in the
  menu.
- **Photon's options file and the two settings files** were copied at each step. Below, each shows its options
  (without the date line); "empty" means the file held only its date line.

### a. Start, RR off

- **Start-up:**
  - `Frame generation: Streamline says NVIDIA DLSS Frame Generation runs on this card` (22:49:40.236);
  - `Frame generation: ready in the DirectX 12 presenter on NVIDIA GeForce RTX 4080 SUPER, on; Reflex goes through
    Streamline` (22:49:42.223);
  - `Reflex: started through Streamline, as DLSS Frame Generation needs (...)` (22:49:53.960).
- **In `dlaa-test`:**
  - `NGX ready on ... (its own DirectX 11 device; ...)` (22:51:07.725);
  - `DLAA: DLSS running at 1280x720 to 2560x1440 (Performance, 50%), preset K, the pack's exposure` (22:51:10.437);
  - `Frame generation: off, a second present in one game frame` (22:51:10.806), then **`Frame generation: running, 2
    frames shown per rendered one (most it can generate: 1, smallest size 100)`** (22:51:13.657).
- **Photon:** `Profile: high (+2 options changed by user)`. High is Photon's default; the 2 are the render
  resolution options.
- **`[sl.dlss_g]`:** 0.

### b. RR on: Photon goes to Ultra

- **The log:**
  - `DLAA: Ray Reconstruction on: Photon's settings switched (Photon: Ultra for Ray Reconstruction)` (22:52:15.795);
  - `DLAA: DLSS starts again on DirectX 12, for Ray Reconstruction` (22:52:22.385);
  - `DLAA: DLSS Ray Reconstruction asked for (experimental): preset F, exposed colour, left noisy for it: rough
    reflections, soft shadows` (22:52:22.519). It says "experimental" now, not "dev build";
  - `DLAA: DLSS Ray Reconstruction running at 1280x720 to 2560x1440 (Performance, 50%), preset F, exposed colour`
    (22:52:23.389);
  - frame generation on when the menu closed (22:52:30.373).
- **Photon:** `Profile: ultra (+2 options changed by user)`.
- **`[sl.dlss_g]`:** 0.
- **Files:**
  - pack options: `AIR_FOG_COLORED_LIGHT_SHAFTS=true`, `BLOCK_ENTITY_SHADOWS=true`, `COLORED_LIGHTS=true`, `INFO=3`,
    `TAAU=true`, `TAAU_RENDER_SCALE=0.50`, `WATER_CAUSTICS=true`;
  - `photon-own.txt`: empty (the player's own set was Photon's defaults; the render resolution isn't part of it);
  - `photon-rr.txt`: none.

### c. A change under RR

- **The player turned Water Caustics off** in Photon's settings. The shaders reloaded at 22:53:36.
- **Photon:** `Profile: Custom (+6 options changed by user)`.
- **`[sl.dlss_g]`:** 0.
- **Files:**
  - pack options: as b without `WATER_CAUSTICS=true`;
  - `photon-own.txt`: empty;
  - `photon-rr.txt`: none yet. It is written when RR goes off.

### d. RR off: 2b's bug case

- **The log:**
  - `DLAA: Ray Reconstruction off: Photon's settings switched (Photon: your settings are back)` (22:53:55.559);
  - `DLAA: DLSS starts again on DirectX 11` (22:53:56.894);
  - **`DLAA: closed its DirectX 12 queue; NGX stays started on the device, which frame generation may share`**
    (22:53:56.909);
  - `DLAA: DLSS running at 1280x720 to 2560x1440 (Performance, 50%), preset K, the pack's exposure` (22:53:58.132).
- **Photon:** `Profile: high (+2 options changed by user)`, what a showed.
- **Files:**
  - pack options: `TAAU=true`, `TAAU_RENDER_SCALE=0.50` (the player's own again);
  - `photon-own.txt`: empty;
  - `photon-rr.txt`: `AIR_FOG_COLORED_LIGHT_SHAFTS=true`, `BLOCK_ENTITY_SHADOWS=true`, `COLORED_LIGHTS=true`,
    `INFO=3` (Ultra without Water Caustics).
- **The long play came a little later,** after the switches below. RR went off at 22:58:54.539, and frame generation
  ran from 22:58:57.615:
  - **88 s of play with RR off and frame generation on.** In 2b, the errors began 17 ms after frame generation came
    on;
  - **F11 twice** (`window is resizing` at 22:59:53.262 and 22:59:55.344). Frame generation was back on within 33–44
    ms each time;
  - **the pause menu** at 23:00:25.718, with the world saving (`Saving and pausing game...`). That's where 2b hung.
    The game stayed up.
  - **`[sl.dlss_g]`:** 0.
- The player: "I did step D and nothing odd happened. And for the frame generation numbers, I do see the expected double."

### e. RR on again: your RR settings

- **The log:**
  - `DLAA: Ray Reconstruction on: Photon's settings switched (Photon: your Ray Reconstruction settings)`
    (22:54:34.359);
  - `RR running at ...` (22:54:36.357).
- **Photon:** `Profile: Custom (+6 options changed by user)`, with c's change.
- **`[sl.dlss_g]`:** 0.
- **Files** (copied at 22:54:50, while RR was on):
  - pack options: c's set;
  - `photon-own.txt`: empty;
  - `photon-rr.txt`: as in d.

### f. Switching

The player switched RR much more than the 4 times asked:
- **In the Upscaling tab without leaving it,** 10 times, about every 4 s (22:54:42–22:55:13).
  - Each switch was running again in about 1.5 s.
  - The toast and Photon's profile followed every time: Custom (+6) with RR, high (+2) without.
  - Frame generation was off throughout (a screen was open), so these test the switching and Photon's settings,
    not 2b's bug.
- **Through the world, with frame generation on in between:**

  | RR | switched at | running | frame generation on |
  | --- | --- | --- | --- |
  | off | 22:56:45.110 | 22:56:47.373 | 22:56:48.030 for 1.6 s |
  | on | 22:56:53.946 | 22:56:55.964 | 22:56:56.489 |
  | off | 22:57:03.649 | 22:57:06.189 | 22:57:06.892 for 2.4 s |
  | on | 22:57:13.108 | 22:57:15.025 | 22:57:17.027 for 7.8 s |
  | off | 22:57:29.026 | 22:57:31.439 | 22:57:32.122 for 8.8 s |
  | on | 22:57:44.804 | 22:57:46.764 | 22:57:47.359 |
  | off (d's long play) | 22:58:54.539 | 22:58:56.872 | 22:58:57.615 for 88 s |
  | off (pause case) | 23:06:53.669 | 23:06:57.121 | 23:06:57.783, pause at 23:06:57.903 |

- **The pause right after RR off:** frame generation came on and, 120 ms later, the player was in the pause menu with the
  world saving. The game stayed up. Frame generation ran again after it (23:07:39–23:07:59).
  - The player: "I turned it off and then I flicked the pause menu up as fast as possible."
- **F11 with RR on:** 4 times (22:56:25–22:56:31). RR started again at each window size, and frame generation was
  back within 35 ms.
- **`[sl.dlss_g]`:** 0. No freeze.

### g. Back to Ultra

- **The player picked Ultra** in Photon's settings with RR on: `Profile: ultra (+2 options changed by user)`
  (22:55:25.528).
- **RR off** (22:55:35.399): `photon-rr.txt` was gone.
- **RR on** (22:55:40.348): the toast `Photon: Ultra for Ray Reconstruction` again.
- **Files** (copied just after that RR on):
  - pack options: Ultra (as b);
  - `photon-own.txt`: empty;
  - `photon-rr.txt`: none.

**The whole of part 2:**
- **0 `[sl.dlss_g]` lines,** and no WARN or ERROR of the Bridge's own.
- **Streamline's own warnings:**
  - 11 × `Frame rate over 100.00ms, reseting frame timer` (after loads and switches);
  - 8 × `FC feedback: 1` (window resizes);
  - one each of `Invalid backbuffer resource extent` and `unsupported alignment` at the start.
- **Streamline's log:** 991 lines, with no `[error]`.
- The player: "So far, I noticed nothing off in anything I did. Everything worked as expected. All my settings were
  maintained and whatnot." At the end: "done no crash at any point".

### Ray Reconstruction 1's step 3: with frame generation

- **Set-up:** at the lakeshore, standing still, switching in the Upscaling tab. The game's counter gives rendered
  fps; the NVIDIA overlay gives shown fps.
- **Photon at Ultra for all three.** The player set his own Photon settings to Ultra too (with RR off, 23:02:15). The
  Bridge kept them as his own, as it should.
  - His reason: "ever since frame generation became part of the stable build, there's no reason on a 4080 Super to
    not push Photon as high as it goes, because frame generation will make it smooth anyway. So I'm putting both on
    Ultra because that's how I would actually play."
  - So the three readings are like for like. The runbook expected the DLSS one at the player's own settings.

| setting | log | rendered fps | shown fps (overlay) | Photon |
| --- | --- | --- | --- | --- |
| RR Performance | `RR running at 1280x720 to 2560x1440 (Performance, 50%)` (23:01:22.885) | 106 | ~212 ("double that") | Ultra |
| RR off, DLSS Quality | `DLSS running at 1707x960 to 2560x1440 (Quality, 67%), preset K` (23:02:13.258) | 112 | ~224 ("2x that") | Ultra |
| RR Balanced | `RR running at 1485x835 to 2560x1440 (Balanced, 58%)` (23:02:55.328) | 99 | ~197 | Ultra |

- **The player's theory from Ray Reconstruction 1** was that RR Performance with frame generation can match DLSS Quality.
  It comes close: 106 against 112 rendered, about 5% short. At Ultra the GPU does more besides RR, so the gap is
  smaller than T1's 7% at High.
- **His pick:** "Out of the 3 I would play rr balanced but in my own world probably rr quality since the 4080 super has
  the headroom and it looks a hair better, on a smaller monitor I could not tell the difference".

## Part 3: timed run with frame generation (T2)

- **Launch:** Ray Reconstruction 1's step 4, launch 2: `frameGen=dlss`, `frameGenType=dlss`, run `RR1`, the release
  DLL.
- **Same set-up as 2b's T1:** windowed at 2544x1401, Photon's defaults (High) with the render resolution options.
- **PresentMon** wrote to `$T
r2c\T2.csv` from a second elevated helper.
- **Run:** `20261027-231737-RR1`, 23:17:37–23:22:20.
  - 8 modes at `shore`, then at `canopy`.
  - PresentMon caught 53,813 frames inside the run: two presents per rendered frame.
- **The first attempt was thrown away.** The player's monitor went to sleep during the `canopy` pass. He: "the monitor went
  to sleep in the dlss performance second pass, and the screen is no longer full screen ... The tests from then on
  need to rerun".
  - I closed that game at 23:15:04 and ran the whole of T2 again.
  - Its `shore` pass and the RR-to-DLSS switch at the start of `canopy` had already run without a `[sl.dlss_g]` line.
  - Its files are kept on the PC as `T2-sleep.*`.
- **The log:**
  - all 10 RR plays logged `DLAA: DLSS Ray Reconstruction running at ...`, and all 6 DLSS plays `DLAA: DLSS
    running at ...`;
  - `Frame generation: running, 2 frames shown per rendered one` at the start;
  - `DLAA: closed its DirectX 12 queue; NGX stays started on the device, which frame generation may share` at each
    switch from RR to DLSS;
  - **0 `[sl.dlss_g]` lines,** and no WARN or ERROR of the Bridge's own.

**T1 (2b, no frame generation) against T2 (frame generation), over the camera paths:**
- Rendered fps is the test kit's frames over the paths' time.
- "Shown per s" is the frames that reached the screen each second (PresentMon's display changes).
- "Shown every" is the time between two shown frames.
- `frametimes.py` pools two launches with the same mode names, and its "frame ms" counts both presents of a
  frame-generated pair. So I ran it on T2 alone (`T2-frametimes.txt` on the PC) and made this table from the same
  data:

| mode | viewpoint | T1 rendered fps | T1 shown per s | T1 shown every ms (median / p10 / p90) | T2 rendered fps | T2 shown per s | T2 shown every ms (median / p10 / p90) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `dlaa-default` | shore | 149 | 149 | 6.06 / 4.81 / 9.19 | 112 | 224 | 4.40 / 3.04 / 5.84 |
| `dlaa-default` | canopy | 142 | 142 | 6.63 / 5.71 / 9.07 | 109 | 218 | 4.56 / 3.65 / 5.38 |
| `dlaa-rr-F` | shore | 97 | 97 | 10.35 / 10.23 / 10.50 | 78 | 156 | 6.38 / 6.15 / 6.66 |
| `dlaa-rr-F` | canopy | 91 | 91 | 10.96 / 10.40 / 11.10 | 75 | 151 | 6.67 / 6.41 / 6.82 |
| `q-dlss-K` | shore | 180 | 180 | 5.28 / 3.78 / 7.68 | 134 | 268 | 3.94 / 1.96 / 5.27 |
| `q-dlss-K` | canopy | 173 | 173 | 5.59 / 4.78 / 7.17 | 127 | 253 | 3.99 / 2.86 / 4.98 |
| `q-rr-F` | shore | 141 | 141 | 7.04 / 6.86 / 7.30 | 106 | 212 | 4.72 / 4.51 / 4.93 |
| `q-rr-F` | canopy | 135 | 135 | 7.42 / 7.07 / 7.62 | 102 | 204 | 4.93 / 4.71 / 5.04 |
| `bal-rr-F` | shore | 155 | 155 | 6.42 / 6.18 / 6.81 | 114 | 227 | 4.42 / 3.58 / 5.51 |
| `bal-rr-F` | canopy | 148 | 148 | 6.77 / 6.43 / 6.97 | 110 | 220 | 4.58 / 4.33 / 4.73 |
| `perf-dlss-K` | shore | 192 | 192 | 4.88 / 3.34 / 7.35 | 143 | 285 | 3.68 / 1.80 / 4.90 |
| `perf-dlss-K` | canopy | 186 | 186 | 5.14 / 4.04 / 6.95 | 132 | 263 | 3.93 / 1.98 / 5.15 |
| `perf-rr-F` | shore | 172 | 172 | 5.73 / 5.16 / 6.33 | 122 | 243 | 4.09 / 3.11 / 5.14 |
| `perf-rr-F` | canopy | 165 | 165 | 6.07 / 5.65 / 6.47 | 119 | 238 | 4.25 / 3.76 / 4.79 |
| `ultra-rr-F` | shore | 197 | 197 | 4.85 / 3.44 / 6.84 | 139 | 278 | 3.60 / 2.31 / 4.64 |
| `ultra-rr-F` | canopy | 190 | 190 | 5.12 / 4.21 / 6.49 | 134 | 268 | 3.73 / 2.75 / 4.44 |

- **Frame generation shows exactly 2 frames per rendered one** in every mode.
- **What it costs:** 18–29% of the rendered frames, about 1.8–2.5 ms of GPU time per frame.
  - With RR: 2.3–2.5 ms;
  - with DLSS: 1.8–2.2 ms.
  - RR and DLSS-G share the DirectX 12 device now, which may explain the small extra.
- **What it adds:** the screen gets 41–65% more frames than T1.
  - The most at full-resolution RR (+61% / +65%), where the frame is long and frame generation's cost is the smallest
    share of it.
  - The least at Performance and Ultra Performance (+41–49%).
- **RR Performance with frame generation against DLSS Quality with frame generation:**
  - shown per s: 243 against 268 at `shore`, 238 against 253 at `canopy` (6–9% fewer);
  - rendered fps: 122 against 134 and 119 against 127.
  - So, as without frame generation, RR Performance comes close to DLSS Quality but doesn't reach it. RR Balanced
    (227 / 220 shown) is between the two.
- **Pacing:** RR's shown frames are more even.
  - `q-rr-F`'s shown intervals are 4.72 / 4.51 / 4.93 ms, against `q-dlss-K`'s 3.94 / 1.96 / 5.27.
  - The pair ratio at `shore` (`T2-frametimes.txt`) is 1.07 / 1.38 for `q-rr-F`, against 1.45 / 2.74 for `q-dlss-K`.
  - With DLSS, the CPU often sets the pace (the GPU-bound share is lower), so frames arrive less evenly. This was
    the same in T1.
- **Against step 3's readings at Ultra** (106 / 112 / 99 rendered): T2 at High renders 122 / 134 / 114 for RR
  Performance, DLSS Quality and RR Balanced at `shore`. Step 3 read a counter standing still at the lakeshore, so this
  is only rough. Ultra seems to cost about 13–16%, and the order of the three is the same.

## Choices that aren't in the runbook

- **Render resolution set in the pack's options file** for part 2 (`TAAU=true`, `TAAU_RENDER_SCALE=0.50`), as in
  2b, instead of in the Upscaling tab.
- **Step d's long play came after e, f and g.** The player ran through b–g quickly first, mostly inside the menu. I then
  asked for d's 2 minutes, F11 and pause, and for f's pause case, with frame generation on. Both are above with their
  times.
- **Step 3 at Photon Ultra for all three readings**, by the player's choice (above).
- **T2 at Photon's defaults (High),** like 2b's T1, at the player's choice.
  - I put the pack's options file back to `TAAU=true`, `TAAU_RENDER_SCALE=0.50`.
  - I removed `photon-own.txt` (the player's Ultra; it wasn't there before the session) before T2, instead of leaving
    "Photon's settings as part 2 left them".
  - It was set windowed again (`fullscreen:false`), as T1 ran; the player's F11 presses had left it full screen.
- **T2 was run twice.** The first attempt was closed at 23:15:04, after the player's monitor went to sleep during it, and
  run again whole from 23:16, at the player's word ("You can kill and restart from there").
  - The new run needed a second elevated PresentMon helper; the player clicked the prompt.
  - Closing the first one mid-run left the test kit's settings for its current mode in the Bridge's settings file.
    I put back T2's settings (checked with `cmp`) before the rerun.
- **T2's table** uses the test kit's own frame count for rendered fps, and PresentMon's display changes for shown
  frames. `frametimes.py`'s "frame ms" isn't meaningful with frame generation (it counts both presents).

## Afterwards

- **Every file from `$T
r2c\*.before` was put back and checked with `cmp`:**
  - `create_galore_bridge-display.properties`, `options.txt`;
  - `DistantHorizons.toml`, `iris.properties`, `fml.toml`, `sodium-options.json`;
  - the shader pack's options file.
- **Neither Photon settings file is left** (`config\create_galore_bridge-photon-own.txt`,
  `...-photon-rr.txt`). Neither was there before. Their contents at each step are above, and copies are in
  `$T
r2ciles`.
- **NVIDIA's `nvngx_dlssd.dll` was removed from the instance,** after a `cmp` against the release copy in `$T
r1`. The
  test kit's json was deleted, and there was no start guard file.
- **The PresentMon helper** finished after T2 on its own.
- **The instance keeps** Bridge 1.126.0. Ray Reconstruction is off in the restored settings.
- **On the PC in `$T
r2c`:**
  - the session notes and the probe outputs (with NGX's logs);
  - part 2's game log and Streamline's log (not committed: they name the user folder);
  - `T2.csv` and the first attempt's `T2-sleep.csv`, with logs;
  - `T2-frametimes.txt` and the T1-against-T2 table.
  - The test kit's run is `instance\screenshots\dlaa-test61027-231737-RR1`.
  - NVIDIA's DLLs stay in `$T
r1`.

**What comes of it:** the runbook's condition holds. Part 2 passed: RR off beside frame generation no longer breaks
it, through switching, F11 and the pause menu, and Photon's settings switch as designed. So this build is the release
candidate for Ray Reconstruction in pack 0.6.3, as an experimental setting.
