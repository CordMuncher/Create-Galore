# Presenter 1 on the RTX 4080 SUPER: Perfect Presentation on DirectX 12

PC session on 2026-10-26, 11:35–12:20, by a Claude Code session in `C:\Users\<user>\Documents\Claude\MC-CREATE`
(`$T`). It followed "Presenter 1" in `tools/dlss-testkit/PC-RUNBOOK.md` at `31bbd040`, with Bridge 1.113.0 (test build)
and the test kit from that commit.
- Two launches with `runs=P1`: Perfect Presentation on, `presenter=dx11` and then `presenter=dx12` with
  `presentCheck=true`.
- Then, at the player's request, a short third launch on DirectX 12 for him to play, look and Alt-Tab.
- DLAA at full resolution (`dlaa=dlss`, preset K), at 2560x1440.
- No crash. This is a test, not a fix: no code was changed.

## Summary

- **DirectX 12 started.** The log has:
  - `Perfect Presentation: DirectX 12 on NVIDIA GeForce RTX 4080 SUPER (OpenGL node mask 1, tearing allowed)`
  - `Perfect Presentation: DXGI swap chain ready on DirectX 12 (OpenGL on a hidden context)`
  - `Reflex: started on Perfect Presentation's DirectX 12 device (NVIDIA NVAPI)`
  - No `DirectX 12 could not start`. `cg_present-4d0b98989df8.dll` was unpacked into `$T\instance\create-galore-dlss`.
- **The picture is exact.** Both `Perfect Presentation check:` lines say
  `DirectX 12 back buffer 2560x1440 matches the game's frame exactly`, on the menu and later in the world. The same two
  came again in the third launch.
- **Same frame rate (within 2%)? No.** DirectX 12 runs about **11% slower** in every uncapped mode: 151 / 138 fps
  against 170 / 155 fps at the baseline (shore / canopy).
- **Same latency (within 0.5 ms)? No.** It's 0.3–1.5 ms higher in every mode, first plays compared.
- **Does Reflex start and confirm low latency on DirectX 12?** Yes. It started on the DirectX 12 device, and the driver
  reports low latency on and the game's sleep used, with unit check 1.00.
  - But its report no longer says `G-SYNC fullscreen`, which it does on DirectX 11 in the same run.
  - So G-SYNC may not engage on the DirectX 12 swap chain.
- **Does GPU pacing On still cost no frames?** Yes on DirectX 11: 173 / 156 fps against the baseline's 170 / 155.
  - On DirectX 12 its clean first play cost none either (154 / 140 against the baseline's 147 / 138).
  - Its second play was disturbed by my blocked Alt-Tab attempt (see Method) and doesn't count.
- **Anything different on screen?**
  - The player played the third launch: "I alt-tabbed around and everything worked." No flicker, and the colours are right.
  - But "I could tell that my frame rate was lower and my GPU utilization was also lower ... there is definitely a
    regression in performance that is not noise. It's very noticeable." He didn't notice more stuttering.
  - My one screenshot during launch 2, of the loading screen, is upright with the right colours:
    [dx12-loading-screen.jpg](dx12-loading-screen.jpg).

**In one line:** DirectX 12 is correct (pixel-exact, no flicker, Alt-Tab works, Reflex runs), but it costs about 11% fps
and about 1 ms of latency against DirectX 11. Its Reflex report also no longer mentions G-SYNC.

## DirectX 12 against DirectX 11, per mode and viewpoint

- **Launch 1** is `20261026-114844-P1` (DirectX 11); **launch 2** is `20261026-115354-P1` (DirectX 12).
- The first columns are medians over both plays, launch 1 → launch 2.
- The "first plays" column leaves out the second plays. The second plays of `lat-cap-default` and `lat-pacing-on` on
  DirectX 12 were disturbed (see Method), so for those two modes only the first plays count.

| mode | viewpoint | latency ms (change) | fps (change) | first plays only: latency ms / fps | queue median / q10 ms | pacer target µs / wait ms |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | 12.1 → 12.6 (+0.5) | 170 → 151 (−11%) | 11.2 → 11.5 / 165 → 147 (−11%) | 4.7 / 3.9 → 4.8 / 3.8 | – |
| `lat-base` | canopy | 14.6 → 15.6 (+1.1) | 155 → 138 (−11%) | 13.8 → 14.6 / 154 → 138 (−10%) | 5.6 / 5.0 → 5.8 / 4.9 | – |
| `lat-pacing-on` | shore | 9.8 → 12.4 (+2.6)\* | 173 → 137 (−21%)\* | 9.8 → 10.9 / 173 → 154 (−11%) | 4.1 / 3.6 → 4.9 / 4.3 | 4000, 4000 / 0.8 → 4299, 5430\* / 1.1 |
| `lat-pacing-on` | canopy | 10.6 → 13.7 (+3.0)\* | 156 → 123 (−21%)\* | 10.6 → 12.0 / 156 → 140 (−10%) | 4.2 / 4.0 → 5.4 / 4.9 | 4156, 4161 / 1.4 → 4824, 6010\* / 1.4 |
| `lat-pacer` (Lowest, automatic) | shore | 8.5 → 9.4 (+0.9) | 159 → 137 (−14%) | 8.7 → 9.5 / 157 → 139 (−11%) | 2.4 / 0.9 → 2.4 / 0.6 | 2294, 1812 / 2.0 → 3021, 3950 / 1.9 |
| `lat-pacer` (Lowest, automatic) | canopy | 8.9 → 9.9 (+0.9) | 144 → 130 (−10%) | 9.1 → 10.0 / 143 → 129 (−10%) | 2.2 / 0.7 → 2.3 / 1.1 | 2033, 645 / 2.8 → 1771, 2206 / 2.8 |
| `lat-reflex` | shore | 10.6 → 11.6 (+1.0) | 173 → 155 (−11%) | 10.6 → 11.5 / 172 → 152 (−12%) | 4.9 / 4.3 → 5.1 / 4.2 | – |
| `lat-reflex` | canopy | 12.0 → 13.3 (+1.4) | 156 → 139 (−11%) | 11.9 → 13.4 / 155 → 138 (−11%) | 5.6 / 5.5 → 5.9 / 5.4 | – |
| `lat-cap-default` (60 fps) | shore | 9.1 → 10.6 (+1.5)\* | 60 → 60 | 9.2 → 9.9 / 60 → 60 | −7.8 / −9.9 → −6.5 / −8.9 | – |
| `lat-cap-default` (60 fps) | canopy | 8.8 → 11.2 (+2.4)\* | 60 → 60 | 8.8 → 10.1 / 60 → 60 | −8.1 / −10.0 → −5.9 / −8.2 | – |

\* includes a disturbed second play; see the first plays.

- **The fps loss is the same in every uncapped mode (−10% to −12%)**, with Reflex, with the pacer and without either.
  So it's the DirectX 12 path itself, not one of the modes.
- **The GPU queue is the same on both** (about 4.8 / 5.8 ms at the baseline). So the GPU is fed alike, but it finishes
  fewer frames per second. The player saw lower GPU utilization too.
  - This looks like time lost at the handover each frame (OpenGL to Direct3D 12 through the imported textures and
    fences), during which the GPU waits. That's a reading, not measured here.
- **The pacing targets:**
  - GPU pacing On sets its target as a share of the frame time: 4000–4160 µs on DirectX 11, 4300–4820 µs in DirectX
    12's clean plays, since its frames are longer.
  - The automatic target (Lowest) varies more on DirectX 12 (1770–3950 µs) than on DirectX 11 (650–2290 µs).
- **With the 60 fps cap**, DirectX 12's first plays are 0.7 / 1.3 ms above DirectX 11's, and the GPU waits less for
  each frame (queue −6.5 / −5.9 against −7.8 / −8.1 ms).
- Full rows, with the driver's report in the Reflex mode: [latency-dx11.txt](latency-dx11.txt),
  [latency-dx12.txt](latency-dx12.txt).

## Log lines

**`Perfect Presentation:`**
- **Launch 1 (DirectX 11):**
  - `Perfect Presentation: on, the game window will be shown through a DXGI swap chain`
  - `Perfect Presentation: NeoForge's OpenGL version check no longer reads the game window`
  - `Perfect Presentation: DXGI swap chain ready on DirectX 11 (OpenGL on a hidden context)`
- **Launch 2 (DirectX 12):**
  - `Perfect Presentation: on, the game window will be shown through a DXGI swap chain`
  - `Perfect Presentation: DirectX 12 on NVIDIA GeForce RTX 4080 SUPER (OpenGL node mask 1, tearing allowed)`
  - `Perfect Presentation: NeoForge's OpenGL version check no longer reads the game window`
  - `Perfect Presentation: DXGI swap chain ready on DirectX 12 (OpenGL on a hidden context)`
  - `Perfect Presentation check: DirectX 12 back buffer 2560x1440 matches the game's frame exactly` at 11:53:08, on
    the menu
  - the same at 11:54:36, in the world
- **Both:** one WARN, `Perfect Presentation: swap chain wait timed out`, while the world loaded, before the run. It's
  the same one-off as in every Perfect Presentation launch since Reflex 1.
- **Third launch (DirectX 12, free play):** the same DirectX 12 lines, and both checks matched exactly (12:05:44 on
  the menu, 12:07:22 in the world).

**`Reflex:`**
- `Reflex: started on Perfect Presentation's DirectX 11 device (NVIDIA NVAPI)` (launch 1), and
  `... DirectX 12 device (NVIDIA NVAPI)` (launch 2)
- `Reflex: on` / `Reflex: off` (2× each)
- When switching on:
  - Launch 1: `Reflex: low latency on, boost off, frame cap none; driver low latency on, last sleep 0 us, game Sleep not used, G-SYNC fullscreen`
    (and once `driver low latency off` at the first switch).
  - Launch 2: the same lines without `, G-SYNC fullscreen`.
- The reports over the stretches say:
  - DirectX 11: `driver low latency on, last sleep 800–3192 us, game Sleep used, G-SYNC fullscreen`.
  - DirectX 12: `driver low latency on, last sleep 800–2785 us, game Sleep used`, again without G-SYNC.
  - The driver's input to GPU end is 11.1–13.9 ms on DirectX 12 (11.2–12.6 ms on DirectX 11). Its unit check is 1.00
    on both.

**`Latency:`** (both launches, the same set)
- `Latency: GPU pacer on, frames may queue on the GPU a share of the frame time that keeps the frame rate` (2×,
  GPU pacing On)
- `Latency: GPU pacer on, frames may queue on the GPU as much as they vary` (2×, Lowest)
- `Latency: GPU pacer off` (3×)
- `Latency: the frame cap waits at the start of the frame, before input` (3×), `... in the game's limiter` (2×)
- `Latency: waits use a high resolution timer` (1×)
- `Latency: input read again just before rendering on` (4×) and `... off` (3×)

**No `Latency:`, `Reflex:` or `Perfect Presentation` WARN or ERROR lines** apart from the one-off swap chain timeout.

**`DLAA:`:**
- `NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device; ...)`
- `DLSS running at 2560x1440 (full resolution), preset K, the pack's exposure`
- `on for this shader pack ...` (12× per launch)
- `closed its DirectX device` at each quit

## The PC's settings (looked at only, nothing changed)

Read again before launch 1. Unchanged from Reflex 1 and the Pacer tests:
- **NVIDIA Control Panel, global:** Low Latency Mode Off, Max Frame Rate Off, Vertical sync "Use the 3D application
  setting", Monitor Technology G-SYNC Compatible, Power management mode Normal.
- **G-SYNC:** enabled, for full screen mode only.
- **Monitor:** 2560x1440 at 240 Hz (Windows reports 239, at most 240).

## Method

**Update.**
- `git fetch` and `checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh` (`31bbd040`).
- `create-galore-bridge-1.110.0.jar` removed; `create-galore-bridge-1.113.0.jar` and the test kit jar (55,328 B)
  copied in.
- `nvngx_dlss.dll` 310.9.16.0 checked.

**Settings, and what they were before.** All are back at the end, checked byte for byte against copies taken before
the session. `presenter` and `presentCheck` are gone again.
- `create_galore_bridge-display.properties`:
  - `dlaa=true` → `dlaa=dlss`;
  - `perfectPresentation=false` → `true`;
  - `presenter=dx11` added (launch 1), then `presenter=dx12` and `presentCheck=true` (launch 2 and the third launch).
  - The new Bridge rewrites this file with its new settings (frame counter, `gpuPacing=off`, `reflex=off`) but kept
    `presenter`. The file was put back as it was.
- `config\DistantHorizons.toml`: `numberOfThreads` 32 → 2.
- `config\fml.toml`: `earlyWindowControl = false` was already so.
- `options.txt` and Sodium's options: not touched, and unchanged.
- The kit's config didn't exist. The kit wrote its defaults, and it was deleted again at the end.

**Launches.** As in step 4, with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test`:

| Launch | Presenter | `-D...` | Run | Time |
| --- | --- | --- | --- | --- |
| 1 | `dx11` | `runs=P1` | `20261026-114844-P1`: 10 plays (5 modes twice), 17,460 frames | 11:48:44–11:51:25 |
| 2 | `dx12`, `presentCheck=true` | `runs=P1` | `20261026-115354-P1`: 10 plays, 17,460 frames | 11:53:54–11:56:50 |
| 3 (extra, the player) | `dx12`, `presentCheck=true` | `modes=lat-base`, `viewpoints=moving`, `paths=still`, `byEye=lat-base` | one short run, then free play | 12:05–12:10:56 |

- P1 plays the modes in the order `lat-base`, `lat-reflex`, `lat-pacer`, `lat-cap-default`, `lat-pacing-on`, not in
  the runbook table's order.
- The third launch uses the player's own `options.txt` (vsync on, 120 fps cap), with Reflex and pacing off.

**What was open.**
- No game server, and no other game or launcher.
- Still running in the background: the Claude app, FanControl, the NVIDIA overlay, NZXT CAM (its helper at about 12% of
  one core), PowerToys, Razer's app engine and elevation service, the PresentMon service, the Windows Package Manager
  server, and a Settings window.

**What didn't go to plan:**
1. **The Alt-Tab in launch 2 didn't happen.**
   - The player asked me to keep computer use out of the run apart from short moments: one screenshot early, and the
     Alt-Tab at the end. I took one screenshot while the game loaded.
   - For the Alt-Tab I aimed at the last mode's unmeasured warm-up. I took that to be `lat-cap-default`, from the
     runbook table, but the run actually ends with `lat-pacing-on`.
   - At 11:56:08–11:56:30 every key press was blocked by the computer-use tool. It took an invisible Windows input
     window (`TextInputHost`) for the front window. No key reached the game.
   - But bringing the game window to the front twice, as part of those attempts, fell into the second play of
     `lat-cap-default` (11:56:12–11:56:34). That play's latency is higher than its first (11.2 / 12.4 against
     9.9 / 10.1 ms).
   - The next play, the second `lat-pacing-on` (11:56:34–11:56:50), dropped to 119 / 107 fps. Its target crept to
     5.4 / 6.0 ms.
   - So both second plays are marked and left out of the comparison. Launch 1, without any computer use, has steady
     repeats in both modes.
2. **The visual check and the Alt-Tab were done in the extra third launch instead.** The player played on DirectX 12 there,
   Alt-Tabbed around, looked, and closed the game himself. His words are in the summary.

No crash, and no new crash log. The only `hs_err` file in the instance is the old one from Upscaling 1.

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261026-114844-P1`, `20261026-115354-P1` and `20261026-120627`
  (the third launch's short run).
- The game logs, this session's notes, the settings copies, and the display settings file as 1.113.0 left it:
  `$T\presenter1\`.
