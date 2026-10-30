# DLSS-G 1 on the RTX 4080 SUPER: DLSS Frame Generation, first run

PC session on 2026-10-26, 18:28–19:20, by a Claude Code session in `C:\Users\<user>\Documents\Claude\MC-CREATE`
(`$T`), with the player at the keyboard for part A. It followed "DLSS-G 1" in `tools/dlss-testkit/PC-RUNBOOK.md` at
`55b9b8b5`, with Bridge 1.117.0 (test build) and the test kit from that commit.
- **Part A:** one launch without `autorun`, Perfect Presentation on DirectX 12, Photon v1.3a + DLAA with DLSS at full
  resolution (preset K), 2560x1440, `frameGen=dlss`, `frameGenCheck=true`.
- **Part B:** two launches with `runs=G1`, `frameGen=dlss` then `frameGen=off`.
- **The test kit crashed** in part B's first launch, on a NaN from frame generation's latency report. With the player's
  authorization I fixed it on the test side, rebuilt the kit and ran part B again (see "The test kit fix"). The Bridge
  and the game code weren't changed.

## Summary

- **Frame generation runs.** Streamline 2.14.16, NVIDIA's `nvngx_dlssg.dll` 310.9.16 (taken from NVIDIA's own
  folder), the DirectX 12 presenter and Reflex through Streamline all came up in the order the runbook asks for. It
  showed **2 frames per rendered one**. The NVIDIA overlay showed 120 fps where the game rendered 60 (with the player's 120
  fps cap), and **212–226 fps uncapped**, where the game rendered 109–112.
- **It looks right.** In the player's words: "It's looking really nice. I've moved stuff around and no HUD is ghosting or
  doubling." The artifacts are "no more than what I would expect in a AAA game like Cyberpunk". Foliage garbles a
  little when flicking fast, "extremely minor".
  - **One HUD miss:** the chat's command suggestions (the autocomplete list above the chat) aren't marked as HUD, so
    frame generation interpolates them as if they moved when they appear. The player: not a blocker. He suggests fixing it
    together with FSR's frame generation.
  - **Smoothness, uncapped and without the checks:** "pretty absurd". Frame generation evens out Minecraft's jittery CPU
    frame times, so the pacing feels better than the number alone. He noticed no input delay at over 100 rendered fps.
  - **His verdict:** "If we don't notice any other problems in the automated tests, I would say that this is fine to
    release as is."
- **The game side checks out.**
  - 269 checks; 268 `copy ok`. The one `COPY WRONG` was check 1, on the very frame the world appeared, with the camera
    not yet set (fov 0).
  - Of the camera verdicts: 51 `camera ok` (median error 0.01–0.03 px), 212 `too little motion to judge`, and **5
    `CAMERA DISAGREES`**. Each of those 5 was the first check right after a shader reload (F8 for the views, and once
    after a resize), with motion vectors of about 70,000–75,000 px where the camera said 0. So **for a moment after a
    reload the motion target holds garbage**. Frame generation may build one wrong frame from it; the player didn't notice
    one. Worth a look on the Bridge side.
- **Resizing, maximising, minimising, Alt-Tab, a Chrome tab dragged over the game: it kept working.** Frame generation
  went off for each resize and on again about 0.1 s later. The player saw about half a second of the picture resolving after a
  resize, which looks like DLSS rebuilding its history.
  - Streamline logged errors meanwhile: 357 `RSYNC: Flip queue is empty!!!` in the game log around the minimise, and
    16,658 in Streamline's own log.
  - The Bridge warned 4 times `Frame generation was on when the swap chain resized: it is turned off one frame before
    a resize`.
  - Frame generation didn't log an "off" while the game was minimised.
- **The four views show what they should**, upright and filling the window. One note on `alpha`: while moving, the player saw
  "the world's ghost briefly reappear". The generated in-between frames come from the real frame, not from the view,
  so that's a property of the debug view, not of normal play.
- **Part B: the GPU pacer beside Reflex keeps the frame rate and shortens the latency, with frame generation and
  without.**
  - **With frame generation:** +1.6% / −0.1% rendered fps (shore / canopy), 12.4 / 14.0 → 11.9 / 13.5 ms. It doesn't
    get in Reflex's way.
  - **Without:** +2.7% / −0.2%, 9.6 / 11.0 → 8.9 / 9.9 ms. That matches Pacer 3's 3000 µs result, which is the target
    On now uses on DirectX 12.
  - No `Streamline reports` line in any mode.
- **What frame generation costs:** at `lat-reflex` the game renders **28% fewer frames** (117.9 / 109.3 against 164.6 /
  152.4 fps). That's about 2.6 ms more GPU time per rendered frame, so the screen gets about 236 / 219 frames, **43%
  more**.
  - The kit's latency (input to the rendered frame's GPU end) is +2.8 / +3.0 ms.
  - The driver's "frame start to GPU end" is about +10 ms (21.5 / 24.0 against 11.6 / 14.3), which includes the frame
    held back for interpolation.
- **Logged problems** (none stopped the game):
  - Streamline error `Setting different 'common' constants multiple times within the same frame is NOT allowed!`, with
    the Bridge's `slSetConstants: eErrorDuplicatedConstants`. This came once in part A when the chat first opened, and
    twice in part B's launch 1 during loading.
  - Streamline warnings: `Invalid backbuffer resource extent`, `unsupported alignment ... overridden to 0`, `Repeated
    slDLSSGSetOptions() call`, `FC feedback: 1` (10×), `throttleFlipQueue` (2×, loading), and `Frame rate over 100.00ms,
    reseting frame timer` (23× in part A, 8× in part B, at loading and at each mode switch's shader reload).

**In one line:** DLSS Frame Generation works on the RTX 4080, looks right apart from the chat's command suggestions,
roughly doubles the frames shown, and the player would release it. The GPU pacer helps beside Reflex with it on. Left for
the developer: the motion vectors right after a shader reload, the duplicated constants, and the flip-queue errors
around a minimise.

## Before anything: what NVIDIA needs

- **Hardware-accelerated GPU scheduling: On.**
  - The registry value the runbook reads (`HwSchMode`) doesn't exist on this PC, which means Windows' default.
  - Following the runbook, I stopped and asked the player. His screenshot of Settings → System → Display → Graphics shows
    "Hardware-accelerated GPU scheduling: On" (and Variable refresh rate On, Optimizations for windowed games On).
- **Windows:** 10.0.26200 (build 26200, newer than 19041).
- **NVIDIA Control Panel, Vertical sync:** "Use the 3D application setting". I read it at 16:47 and the player confirmed it
  unchanged.
- **The card:** RTX 4080 SUPER, driver 32.0.16.2101 (NVIDIA 621.1 in Streamline's log).
- **nvngx_dlssg.dll 310.9.16**, which the Bridge copied at start from NVIDIA's folder:
  `C:\ProgramData\NVIDIA\NGX\models\dlssg\versions\20318480\files\160_E658700.bin`. The Streamline SDK download wasn't
  needed.

## Part A: does it run, and does it look right

### Frame generation's log lines

Every `Frame generation` line that isn't a check (the counts are over the launch):

```
Frame generation: NVIDIA DLSS Frame Generation through Streamline 2.14.16 from $T\instance\create-galore-dlss\streamline-2.14.16; nvngx_dlssg.dll 310.9.16, copied from C:\ProgramData\NVIDIA\NGX\models\dlssg\versions\20318480\files\160_E658700.bin
Frame generation: ready in the DirectX 12 presenter on NVIDIA GeForce RTX 4080 SUPER; Reflex goes through Streamline
Frame generation: view off, checks every 5 s
Frame generation: off, no world                                           (2×: menu at start, quitting)
Frame generation: running, 2 frames shown per rendered one (most it can generate: 1, smallest size 100)
Frame generation: on, 1 generated per rendered frame, the HUD apart      (13×)
Frame generation: off, a screen is open                                   (10×: inventory, pause, video settings)
Frame generation: off, the window is resizing                             (4×)
Frame generation: DirectX 12: slSetConstants: eErrorDuplicatedConstants   (WARN, 1×, when the chat first opened)
Frame generation: view hudless / alpha / depth / motion, checks every 5 s (step 3)
Frame generation: view off, checks off                                    (step 4, at the player's request)
```

- **Reflex**, as the runbook expects: `Reflex: on, though set to off: frame generation needs it`, then `Reflex: started
  through Streamline, as frame generation needs (Streamline, low latency available, latency report available)`.
- With the player's own 120 fps cap: `Reflex: low latency on, boost off, frame cap 120 fps (Reflex's); Streamline, ...`.
  Uncapped: `frame cap none`.
- **The `(native)` lines:** Streamline's own log, passed through.
  - Most are `[info]`, and many were dropped by the Bridge's limit (1,694 `log lines dropped` notices, mostly at the
    resizes).
  - The warnings and errors among them are in the summary.
  - At the start Streamline said `RSYNC: GSYNC FS OFF is 1, VSync enabled is 1, DRS VSync mode is App-Controlled,
    fullscreen iFlip is 0`.
- The chat doesn't turn frame generation off. Other screens do, and it came back on each time.

### The checks

- **269 checks**, every 5 s. 268 `copy ok`.
- **The one that isn't**, check 1, on the frame the world first appeared:
  `COPY WRONG: hudless 0, UI alpha 0, depth 0, motion 3686400, outside 0 pixels (first: motion at (0, 0)); camera reset
  this frame`. Its camera line said `camera fov 0.0 deg, aspect 0.0`. From check 2 on, every copy was right.
- **The camera lines** (check 1 and every 12th) read fov 70° (77° and 73.4° while sprinting or flying), aspect 1.778,
  render 1.778, near 0.05, far 768.
- **The camera verdicts:** 51 `camera ok` (motion vectors against the camera off by 0.01–0.03 px median), 212 `too
  little motion to judge`, and 5 `CAMERA DISAGREES`:

| Check | Time | Just before | Verdict |
| --- | --- | --- | --- |
| 120 | 18:44:12 | the shader pipeline rebuilt at 18:44:09, after the first resize | off by 73,001 px median, motion 73,001 px, camera says 0 |
| 165 | 18:47:54 | F8 for `frameGenView=hudless` (pipeline rebuilt 18:47:53) | off by 70,594 px |
| 184 | 18:49:28 | F8 for `alpha` | off by 75,212 px |
| 202 | 18:50:58 | F8 for `depth` | off by 74,047 px |
| 213 | 18:51:52 | F8 for `motion` | off by 75,228 px |

Each is the first check after a shader reload, which also resets the check timer, so it falls on the first frame or two
afterwards. The motion vectors there are huge while the camera stood still. It looks like the motion target isn't
written yet on the first frame of a new pipeline. Every later check was fine again.

### Step 1: by eye (the player played for about 15 minutes)

- **The picture:** no ghosting or doubling of the HUD, hotbar, chat or crosshair. It "can only see that frame
  generation is on" if you're sensitive to artifacts, and the artifacts are "no more than ... Cyberpunk". Foliage, when
  flicking fast and staring at it, "can garble occasionally, but it's extremely minor".
- **The chat's command suggestions:** "they're the only things that get garbled as they move ... as they appear. Frame
  generation thinks they moved up and generates fake in between frames." They aren't marked as HUD. The player doesn't count
  it as a blocker for a first release.
- **The frame rate:** with his 120 fps cap the counter showed 60 and the NVIDIA overlay 120. "it feels like 120 FPS
  visually, and I can't really notice input delay at 60 FPS".
- **Hitches:** a regular hitch every ~5 s was the checks (as the runbook expects). Occasional random ones only when
  flying or flicking into a new direction for the first time ("Minecraft being Minecraft"). Streamline's 100 ms warning
  fired only in the first seconds after joining.
- The log's off/on lines matched the menus, the pause screen and the video settings.

### Step 2: resize

- The player left full screen, dragged the window to random sizes, went back to full screen, then minimised it, opened the
  taskbar and Windows search, dragged a Chrome tab over the game, clicked back and maximised again. "It worked
  perfectly ... it kept working."
- About half a second of the picture resolving after a resize, "more DLSS building up its history than anything
  related to frame generation".
- The log: four times `off, the window is resizing` and `on` again 0.1 s later, each with the Bridge's WARN `Frame
  generation was on when the swap chain resized: it is turned off one frame before a resize`.
- Around the minimise came 357 Streamline errors `RSYNC: Flip queue is empty!!!` (16,658 in Streamline's own log).
  Frame generation stayed "on" while minimised.

### Step 3: the views

`frameGenView` added to the settings file and F8 each time; the log confirmed each view. The player's F2 screenshots, taken
while turning (F2 saves the rendered frame, not the generated ones):

| View | What it should show | What it showed | Screenshot |
| --- | --- | --- | --- |
| `hudless` | the world with no HUD | the world, upright and filling the window, no HUD (the hand and shield are the world's), while flicking and while panning | [view-hudless.jpg](view-hudless.jpg) |
| `alpha` | white under the HUD, black elsewhere | exactly that, pure greyscale. The player: "While I'm moving, it's like the world's ghost briefly reappears, but when stationary, it's back to just black and white" (the generated frames carry the real world) | [view-alpha.png](view-alpha.png) |
| `depth` | near bright, far dark, sky black | the hand and shield nearly white, trees and grass grey, far terrain darker, the sky black | [view-depth.jpg](view-depth.jpg) |
| `motion` | black when still; right red, left blue, down green, up blue-ish | turning right: red (yellow and magenta toward the corners), the hand black; moving down: green (average RGB 7/218/131); moving up: blue (54/0/219); black when still; waving leaves show as coloured blobs | [turning right](view-motion-turning-right.jpg), [moving down](view-motion-looking-down.jpg), [moving up](view-motion-looking-up.jpg) |

### Step 4: the frame rate

Standing still looking at the lake. The player's readings, from the frame counter (rendered frames) and the NVIDIA app's
overlay (frames shown):

| Settings | Counter (rendered) | NVIDIA overlay (shown) |
| --- | --- | --- |
| the player's own: Max Framerate 120, VSync on (checks on) | 60 fps | 120 fps |
| Uncapped, VSync on, checks off | 109 fps, 9.2 ms; 1% low 69, 0.1% low 52 (about 70 / 56 after settling) | 212–217 fps |
| Uncapped, VSync off, checks off | 111–112 fps, GPU almost fully used | about 220–226 fps |

- Frame generation doubles what the screen gets. VSync made only a marginal difference.
- The player: "the smoothness is pretty absurd". The power draw was the highest of these runs but within the card's limit.
  "I've never seen Minecraft shaders run at this high level of an FPS, at this visual quality."
- **On his server**, where he's CPU-limited at about 100 fps with the GPU half idle, he expects it to be close to a free
  win.
- Turning the checks off and uncapping were the player's choices, outside the runbook, to judge smoothness without the
  checks' hitch.

## Part B: Reflex alone against Reflex with the GPU pacer, frame generation on and off

From the run manifests (`latency.py` shows the same).
- fps is rendered frames (with frame generation the screen gets twice that). The values are medians of the two plays,
  with the plays in brackets.
- The driver's report is Reflex's own over its last 64 frames. With frame generation its "input to GPU end" has no
  value (NaN, the cause of the crash; see below); its "frame start to GPU end" has.
- With frame generation, Reflex is on in `lat-pacing-on` too.

| mode | viewpoint | launch | fps rendered (plays) | vs `lat-reflex` | latency ms: median (plays) / p90 | queue / q10 ms | driver: input→GPU end / frame start→GPU end ms | pacer target µs (plays) / wait ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `lat-reflex` | shore | FG on | 117.9 (118, 118) | – | 12.4 (12.9, 11.9) / 14.5 | 3.9 / 1.2 | – / 21.5 | – |
| `lat-reflex` | shore | FG off | 164.6 (159, 170) | – | 9.6 (9.5, 9.6) / 9.8 | 3.6 / 2.8 | 10.1 / 11.6 | – |
| `lat-reflex` | canopy | FG on | 109.3 (109, 110) | – | 14.0 (14.6, 13.4) / 16.1 | 5.0 / 2.0 | – / 24.0 | – |
| `lat-reflex` | canopy | FG off | 152.4 (152, 153) | – | 11.0 (11.0, 11.0) / 11.0 | 4.4 / 4.1 | 11.6 / 14.3 | – |
| `lat-reflex-pacing-on` | shore | FG on | 119.8 (120, 120) | +1.6% | 11.9 (12.0, 11.7) / 14.5 | 3.5 / 1.8 | – / 21.6 | 4122, 4133 / 0.00 |
| `lat-reflex-pacing-on` | shore | FG off | 169.0 (168, 170) | +2.7% | 8.9 (9.0, 8.9) / 9.2 | 3.1 / 2.5 | 9.5 / 11.7 | 3000, 3000 / 0.58 |
| `lat-reflex-pacing-on` | canopy | FG on | 109.2 (109, 109) | −0.1% | 13.5 (13.6, 13.3) / 15.8 | 4.5 / 1.6 | – / 23.7 | 4515, 4504 / 0.54 |
| `lat-reflex-pacing-on` | canopy | FG off | 152.2 (152, 152) | −0.2% | 9.9 (9.9, 9.9) / 10.0 | 3.3 / 3.0 | 10.5 / 13.8 | 3261, 3269 / 1.08 |
| `lat-pacing-on` | shore | FG on | 119.6 (119, 120) | +1.4% | 12.0 (12.2, 11.7) / 14.4 | 3.7 / 2.1 | – / 21.6 | 4123, 4131 / 0.04 |
| `lat-pacing-on` | shore | FG off | 164.9 (165, 165) | +0.2% | 9.0 (9.1, 8.9) / 9.5 | 3.1 / 2.3 | – | 3000, 3000 / 0.51 |
| `lat-pacing-on` | canopy | FG on | 109.3 (109, 109) | 0.0% | 13.5 (13.8, 13.2) / 15.8 | 4.5 / 1.8 | – / 23.6 | 4515, 4498 / 0.53 |
| `lat-pacing-on` | canopy | FG off | 149.2 (151, 147) | −2.1% | 9.9 (9.9, 9.9) / 10.2 | 3.4 / 1.4 | – | 3262, 3264 / 1.06 |

The driver's GPU frame time per rendered frame: 8.3–8.5 / 9.0–9.3 ms with frame generation, 5.8 / 6.5 ms without.

- **With frame generation:** the pacer beside Reflex keeps the frame rate (+1.6% / −0.1% against `lat-reflex`) and
  lowers the latency by 0.5 / 0.5 ms (12.4 / 14.0 → 11.9 / 13.5).
  - It targets 4.1 / 4.5 ms and waits little (0.0–0.5 ms), so it doesn't get in Reflex's way.
  - `lat-pacing-on`, where Reflex is on anyway, gives the same: 12.0 / 13.5 ms, +1.4% / 0.0%.
  - No `Streamline reports` line in any mode.
- **Without:** the pacer beside Reflex keeps the frame rate (+2.7% / −0.2%) and lowers the latency by 0.7 / 1.1 ms (9.6
  / 11.0 → 8.9 / 9.9), at the 3000 / 3262 µs target On now uses on DirectX 12.
  - Pacer 3 measured the same: `lat-reflex` 9.6 / 11.0 ms at 168.6 / 152.3 fps, and the 3000 µs pacer 8.9 / 9.7 ms
    keeping the frame rate.
  - Presenter 3's cleaned world measured `lat-reflex` at 9.5 / 11.0 ms and 170.3 / 152.8 fps.
  - `lat-pacing-on` without Reflex: 9.0 / 9.9 ms at +0.2% / −2.1%.
- **Frame generation's cost**, `lat-reflex` with it against without:
  - rendered 117.9 / 109.3 against 164.6 / 152.4 fps, **−28% / −28%**, or about 2.6 ms more GPU time per rendered
    frame;
  - shown about 236 / 219 frames, **+43% / +43%**;
  - the kit's latency **+2.8 / +3.0 ms** (12.4 / 14.0 against 9.6 / 11.0);
  - the driver's frame start to GPU end **+9.9 / +9.7 ms** (21.5 / 24.0 against 11.6 / 14.3). That's closer to what
    the player feels, since it includes the rendered frame held back while the generated one is shown first.
- **The log:**
  - Launch 1 has `Frame generation: running, 2 frames shown per rendered one ...`, `Reflex: started through
    Streamline` and `Latency: GPU pacer on, frames may queue on the GPU a share of the frame time that keeps the frame
    rate`.
  - Launch 2 has no `Frame generation` line, `Reflex: started on Perfect Presentation's DirectX 12 device (NVIDIA
    NVAPI)`, and the same pacer line.
  - WARN/ERROR: launch 1 has the Bridge's `slSetConstants: eErrorDuplicatedConstants` twice during loading (with
    Streamline's "common constants" error). Launch 2 has only the usual one-off `swap chain wait timed out`.
  - Streamline's 100 ms warning came at each mode switch's shader reload, not during measurement.

## Streamline's own log

`$T\instance\create-galore-dlss\streamline-logs\sl.log`. The warning and error lines only, counted, with times and
thread ids left out (the file names his user folder, so it stays on the PC):

| Line | Part A | Part B, launch 1 |
| --- | --- | --- |
| `[error] rsync.cpp:250[notifyFrameFlipped] RSYNC: Flip queue is empty!!!` | 16,658 | – |
| `[warn] dlfgPresent.cpp:1323[presentCommon] Frame rate over 100.00ms, reseting frame timer` | 23 | 8 |
| `[warn] flipMetering.cpp:182[setFlipConfig] FC feedback: 1` | 10 | – |
| `[warn] swapChainData.cpp:535[throttleFlipQueue] throttleFlipQueue: no flip-queue slot freed within 30 ms for App frame N (low frame rate or present backpressure).` | 2 | – |
| `[error] commonInterface.h:576[set] Setting different 'common' constants multiple times within the same frame is NOT allowed!` | 1 | 2 |
| `[warn] dlfgPresent.cpp:421[sanitizeResourceExtent] Invalid backbuffer resource extent, IF optionally specified by the client! Either extent not provided or one of the ext...` | 1 | 1 |
| `[warn] defines.h:323[set] Repeated slDLSSGSetOptions() call for the frame N. A redundant call or a race condition with Present().` | 1 | 1 |
| `[warn] d3d12.cpp:2533[cloneResource] One or more tagged resources use an unsupported alignment that is being overridden to 0 (e.g. resource 'nv.sl.dlss_g.clone.dlfg-outpu...` | 1 | 1 |

## The test kit fix (authorized by the player)

- **What crashed:** part B's first launch (run `20261026-190645-G1`), 19 s in, when the first `lat-reflex` stretch
  ended. Minecraft's crash report:
  `java.lang.IllegalArgumentException: NaN is not a valid double value as per JSON specification`, at
  `dev.creategalore.dlsstestkit.Capture.recordLatency(Capture.java:1062)`.
  - The kit writes the Bridge's latency window as JSON. With frame generation, two of its numbers have no value:
    `driverInputToGpuMs` and `driverInputToGpuP90Ms`, Reflex's input to GPU end through Streamline.
  - During the emergency save the kit hit a second error, `IllegalStateException: Trying to return to in-game GUI
    during disconnection` in `Capture.beforeFrame`.
  - There's no `hs_err` file, since the JVM itself didn't crash. The crash report, the log's last 100 lines and the
    Streamline log are on the PC.
- **The player's words:** "Since this is an obvious test side fix, I authorize you to just fix it and rerun the test that
  crashed and the other tests that are waiting and just mention that in your report."
- **The fix:** `Capture.recordLatency` now leaves a number out of the manifest when it isn't finite, and logs which
  one (`DLAA test: lat-reflex at shore: driverInputToGpuMs has no value (NaN), left out`). It's committed with this
  report.
- **The build:** plain `javac` (JDK 21.0.12) and `jar`, as `build.sh` does, against the game's own NeoForge, Minecraft
  and library jars and the Iris jar. Against the branch's jar, only `Capture.class` (and the jar manifest's
  "Created-By") differs; the other 16 classes are byte-for-byte the same. The rebuilt jar replaces
  `dist/create-galore-dlss-testkit-1.0.0.jar`.
- **Part B was then run again** with the fixed kit: launch 1 (frame generation on, run `20261026-191303-G1`) and
  launch 2 (off, `20261026-191649-G1`). Every launch 1 stretch left out those two driver numbers. Launch 2 left out
  nothing.
- The crash also left the kit's mode settings in the display settings file. That was reset before the rerun.
- **For the Bridge:** with frame generation on, Reflex's "input to GPU end" through Streamline has no value. The other
  numbers in its report do.

## Method

**Update.** As the runbook says:
- `55b9b8b5`;
- `create-galore-bridge-1.116.0.jar` removed and `create-galore-bridge-1.117.0.jar` (2,764,899 B) copied in;
- the test kit jar (55,876 B; later the fixed build, see above).

**Settings, and what they were before.** All back at the end, checked byte for byte against copies taken first.
- `create_galore_bridge-display.properties`:
  - `dlaa=true` → `dlaa=dlss`; `perfectPresentation=false` → `true`;
  - added `presenter=dx12`, `fpsCounter=detailed`, `gpuPacing=off`, `reflex=off`, `frameGen=dlss` and `frameGenCheck=true`;
  - `frameGenView=...` in step 3;
  - for part B, no `frameGenCheck` or `frameGenView`, and `frameGen=off` in launch 2.
  - The file was put back as it was, so none of these is left.
- Shader pack: Photon v1.3a + DLAA was selected. `earlyWindowControl = false` was already so.
- `options.txt`: the player's own (120 fps cap, VSync on) in part A, until he uncapped it in step 4. It was put back before
  part B, and the kit sets its own cap and VSync in its runs.
- **Distant Horizons:** 32 threads in part A, as the runbook doesn't change it. 2 threads in part B, my choice, to match
  Pacer 3 and Presenter 3, the latency runs part B is compared with.

**The launches.**

| Launch | Settings | Run | Time |
| --- | --- | --- | --- |
| A | `frameGen=dlss`, `frameGenCheck=true` | free play, the player | 18:32:40–19:04:17 |
| B1, first try | `frameGen=dlss` | `20261026-190645-G1`, crashed | 19:05:08–19:07:04 |
| B1 | `frameGen=dlss` | `20261026-191303-G1`: 12 plays, 10,780 frames | 19:13:03–19:14:48 |
| B2 | `frameGen=off` | `20261026-191649-G1`: 12 plays | 19:16:49–19:18:09 |

- G1 plays `lat-reflex`, `lat-pacing-on` and `lat-reflex-pacing-on` (in that order, not the runbook table's), each at
  shore and then canopy, and the list twice.
- **The window:** part B's launches went through the window script from Pacer 3 (approved by the player then). The game
  window landed at (0, 0) with its title bar on screen, 2560x1421, in all three part B launches. The script moved it to
  (−8, −31) before the run. Each run started with the picture at (0, 0), 2560x1440, and the game in front. In part A it
  came up right without help.
- **Quiet PC:** no PresentMon, nvidia-smi or other Java. The ChatGPT/Codex apps are uninstalled now. Wallpaper Engine
  was paused, CPU under 2%, and nothing else used the GPU.

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261026-191303-G1` and `20261026-191649-G1` (and the crashed
  `20261026-190645-G1`).
- In `$T\dlssg1\`:
  - the game logs (`partA-latest.log`, `partB-L1-latest.log`, `partB-L2-latest.log`) and the window logs;
  - the crash report and the log's last 100 lines before it;
  - the Streamline logs (`sl-partA.log`, `sl-partB-L1.log`, `sl-partB-L1-rerun.log`);
  - all of the player's F2 screenshots as PNG (`shots\`);
  - `latency.md` and `tables.md`;
  - the settings copies, and the branch's kit jar beside the fixed one;
  - this session's notes.
