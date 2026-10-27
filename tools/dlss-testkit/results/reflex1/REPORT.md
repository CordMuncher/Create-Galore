# Reflex 1 on the RTX 4080 SUPER: input latency with Reflex, a late input read and Sodium's render-ahead limit

PC session on 2026-10-25, 21:20–22:00, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`). It followed "Reflex 1" in `tools/dlss-testkit/PC-RUNBOOK.md` at `5016ab2`, with Bridge 1.105.0 (the test
build) and the test kit from that commit.
- Two launches: L1 with Perfect Presentation on, L2 with it off. Then a by-feel part with Emre.
- DLAA at full resolution (`dlaa=dlss`, preset K) throughout, at 2560x1440.
- Both runs finished, with no crash. This is a test, not a fix: no code was changed.

**What is measured:** the Bridge's latency meter, from the game last reading input to the GPU finishing that frame,
as the median over each 600-frame stretch held still. What the swap and the screen add comes on top, the same in every
mode. So the modes compare with each other, but this isn't click-to-photon.

## Summary

- **Reflex runs on Perfect Presentation's Direct3D 11 device.**
  - The log says `Latency test: NVIDIA Reflex started on Perfect Presentation's Direct3D 11 device`.
  - In every Reflex mode, the driver reports low latency on and uses the game's sleep.
  - Its own latency numbers sit next to the Bridge's, and its unit check is 1.00 everywhere.
  - There was no `Reflex unavailable` and no `Reflex stopped after an error`.
- **Uncapped (L1, Perfect Presentation on, GPU-bound at about 150–175 fps):**
  - Reflex lowers the latency by 1.3–1.4 ms at the shore and 2.3 ms at the canopy. That's exactly what the late input
    read does on its own, so in these numbers, Reflex's sleep adds nothing beyond the late read.
  - Boost is the same. Markers alone change nothing (+0.5–0.6 ms, inside the baseline's repeat spread of 1.4–1.6 ms).
  - The frame rate doesn't move in any of these (−1% to +3%).
- **Capped at 60 fps, the limiter decides everything:**
  - The game's own limiter is the worst setting of the test: 29.2 ms with Perfect Presentation, 20.9 ms without.
  - The late read cuts that to 16.7 ms with Perfect Presentation and 8.5–9.1 ms without.
  - **Reflex's own limiter (L1) gives the lowest latency measured: 8.7–8.8 ms.** That is 3.1–5.6 ms below the uncapped
    baseline, and 20 ms below the game's own 60 fps cap.
- **Without Perfect Presentation (L2), the late read alone does nothing** (−0.1 ms at the canopy). Sodium's
  render-ahead limit is the knob there:
  - Limit 1 lowers the latency by 3.3 ms at the canopy, at no cost.
  - Limit 0 lowers it by 6.5 ms, but costs 24–25% of the frame rate.
  - **Limit 1 with the late read lowers it by 6.2 ms at no cost**: limit 0's gain without its cost.
  - The shore's baseline is unsteady in L2 (repeats 11.0 and 16.6 ms; see below). Against its steady second repeat,
    the order is the same.
- **Perfect Presentation on its own lowers the latency.** The baseline at the canopy is 14.3 ms with it and 18.8 ms
  without, and the GPU trails the CPU by 7–9 ms instead of 14–16 ms.
- **By feel (Emre):**
  - The late read is "very close to placebo", like a game's raw input option: quick corrections after overshooting
    respond faster.
  - Reflex is "kind of crazy". It feels exactly like Reflex in games that build it in, more responsive than Minecraft
    without shaders at 240 fps. He'd keep it on even without frame generation.
  - Going back, the baseline "feels slow ... that classic sluggish feel", obvious next to Reflex.
  - His Reflex mode ran with his own 120 fps cap, which Reflex's limiter took over. That's the same setup as
    `lat-cap-reflex`, which measured the lowest latency of all. So his impression and the numbers agree.

## Latency per mode

Bridge's meter: median input-to-GPU time in ms, with the two repeats' range in brackets. The changes and fps are against
`lat-base` at the same viewpoint. Full output of `latency.py`, with the 90th percentiles, the GPU's lag and the driver's
report: [latency-L1.txt](latency-L1.txt), [latency-L2.txt](latency-L2.txt).

### L1: Perfect Presentation on (`20261025-212948-L1`)

| mode | shore, ms (repeats) | change | fps (change) | canopy, ms (repeats) | change | fps (change) |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` (as it is) | 11.9 (11.1–12.7) | – | 171 | 14.3 (13.6–15.0) | – | 154 |
| `lat-late` (late input read) | 10.5 (10.5–10.5) | −1.4 | 174 (+2%) | 12.0 (12.0–12.1) | −2.3 | 155 (+0%) |
| `lat-markers` (Reflex markers only) | 12.5 (12.2–12.7) | +0.6 | 173 (+2%) | 14.8 (14.6–15.1) | +0.5 | 155 (+0%) |
| `lat-reflex` (low latency + late read) | 10.5 (10.5–10.6) | −1.3 | 176 (+3%) | 12.1 (12.0–12.1) | −2.3 | 155 (+1%) |
| `lat-boost` (the same + boost) | 10.5 (10.5–10.6) | −1.4 | 173 (+1%) | 12.0 (12.0–12.1) | −2.3 | 152 (−1%) |
| `lat-cap` (60 fps, game's limiter) | 29.2 (29.1–29.2) | +17.3 | 60 | 29.3 (29.3–29.3) | +15.0 | 60 |
| `lat-cap-late` (+ late read) | 16.7 (16.7–16.7) | +4.8 | 60 | 16.7 (16.7–16.7) | +2.4 | 59 |
| `lat-cap-reflex` (60 fps by Reflex, + late read) | **8.8** (8.8–8.8) | −3.1 | 60 | **8.7** (8.7–8.7) | −5.6 | 60 |

The 90th percentiles follow the medians, within 0.1–1.2 ms. The GPU's lag (frame end on the CPU to the GPU finishing
it) is 5–9 ms uncapped and about 12.5 ms with the game's 60 fps cap, but only 4.8 ms with Reflex's.

### L2: Perfect Presentation off (`20261025-213710-L2`)

| mode | shore, ms (repeats) | change | fps (change) | canopy, ms (repeats) | change | fps (change) |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 13.8 (11.0–16.6) | – | 170 | 18.8 (18.7–18.8) | – | 158 |
| `lat-late` | 16.5 (16.3–16.7) | +2.7 | 177 (+4%) | 18.7 (18.7–18.8) | −0.1 | 158 (+0%) |
| `lat-ra1` (Sodium render-ahead 1) | 13.0 (12.9–13.0) | −0.8 | 178 (+5%) | 15.4 (15.4–15.5) | −3.3 | 158 (+0%) |
| `lat-ra0` (render-ahead 0) | 11.3 (11.3–11.3) | −2.5 | 127 (−25%) | 12.3 (12.3–12.3) | −6.5 | 120 (−24%) |
| `lat-ra1-late` (1 + late read) | 11.1 (11.1–11.1) | −2.7 | 175 (+3%) | 12.6 (12.5–12.6) | −6.2 | 158 (+0%) |
| `lat-cap` (60 fps, game's limiter) | 20.9 (20.8–21.0) | +7.1 | 60 | 20.9 (20.9–20.9) | +2.1 | 60 |
| `lat-cap-late` | 9.1 (8.9–9.3) | −4.7 | 60 | 8.5 (8.5–8.6) | −10.2 | 60 |

**The shore's baseline in L2 is unsteady.**
- Its first play, the run's first stretch, measured 11.0 ms at 162 fps with the GPU only 4.7 ms behind. The repeat
  measured 16.6 ms at 178 fps with the GPU 13.2 ms behind.
- So the shore's L2 changes against the median (13.8) aren't reliable.
- Against the steady second repeat (16.6 ms), the shore agrees with the canopy: late read −0.1, render-ahead 1 −3.6,
  render-ahead 0 −5.3, render-ahead 1 with the late read −5.5, game's cap +4.3, cap with the late read −7.5 ms.
- L1's shore baseline shows the same pattern, more mildly (11.1 then 12.7 ms).

**Without Perfect Presentation, the GPU trails 13–16 ms behind** (Sodium's default render-ahead of 3). Reading input
later doesn't shorten that queue, which fits the late read doing nothing on its own there. Render-ahead 1 halves the
lag (7.5–9.3 ms), and 0 cuts it to 3.8–4.5 ms but leaves the GPU waiting for the CPU, hence the lost fps.

## Reflex: what the driver says

From the manifests, per Reflex mode, driver report on the last 64 frames:

| mode | driver | sleep (last, µs) | driver input to GPU end, ms (shore / canopy) | Bridge's (shore / canopy) | unit check |
| --- | --- | --- | --- | --- | --- |
| `lat-markers` | low latency **off**, game Sleep used, G-SYNC fullscreen | 0 | 11.1 / 12.6–12.7 | 12.5 / 14.8 | 1.00 |
| `lat-reflex` | low latency **on**, game Sleep used, G-SYNC fullscreen | 800–3153 | 11.1–11.2 / 12.6–12.7 | 10.5 / 12.1 | 1.00 |
| `lat-boost` | low latency **on**, game Sleep used, G-SYNC fullscreen | 3058–3175 | 11.1 / 12.6–12.7 | 10.5 / 12.0 | 1.00 |
| `lat-cap-reflex` | low latency **on**, game Sleep used, G-SYNC fullscreen | 16666 (its 60 fps limiter) | 8.9–9.2 / 8.8–9.0 | 8.8 / 8.7 | 1.00 |

- **The driver says low latency is on and that it uses the game's sleep**, in every Reflex mode.
  - When `lat-cap-reflex` switched on, its first log line said `game Sleep not used` (with `last sleep 0 us`). Its
    report over the stretch then says used, with a 16.7 ms sleep, which is the 60 fps cap.
- **The driver's numbers sit next to the Bridge's.**
  - With Reflex's cap they match within 0.4 ms.
  - Uncapped they differ by 0.6 ms one way with Reflex on, and by 1.4–2.2 ms the other way with markers only. This looks
    like the driver timing from its input marker, not from the game's last input read, so the late read moves the
    Bridge's start but not the driver's.
- **The driver sees no GPU work: "GPU active" is 0.0 ms of a 5.7–16.7 ms frame.** It only sees its own Direct3D 11
  device, while the frame is rendered in OpenGL. This looks like why Reflex's sleep can't shorten the OpenGL queue
  uncapped (the same gain as the late read alone), while its frame limiter, which only needs the CPU's timing, works
  well.
- **The unit check is 1.00 in every row.** The driver's times are microseconds, as expected.

## Log lines

The folder is shortened to `$T`. Counts are per launch.

**`Latency test:` (L1):**
- `Latency test: NVIDIA Reflex started on Perfect Presentation's Direct3D 11 device` (once, at the first Reflex mode)
- `Latency test: Reflex markers` (2×), `Latency test: Reflex on` (4×), `Latency test: Reflex boost` (2×),
  `Latency test: Reflex off` (3×)
- `Latency test: input read again just before rendering on` (6×) and `... off` (5×)
- `Latency test: Reflex low latency off, boost off, frame cap none; driver low latency off, last sleep 0 us, game Sleep not used, G-SYNC fullscreen`
  (2×, markers)
- `Latency test: Reflex low latency on, boost off, frame cap none; driver low latency on, last sleep 0 us, game Sleep used, G-SYNC fullscreen`
  (2×)
- `Latency test: Reflex low latency on, boost on, frame cap none; driver low latency on, last sleep 2072 us, game Sleep used, G-SYNC fullscreen`
  and `... last sleep 3149 us ...`
- `Latency test: Reflex low latency on, boost off, frame cap 60 fps (Reflex's); driver low latency on, last sleep 0 us, game Sleep not used`
  (the first time without `, G-SYNC fullscreen`, the second time with it)
- `Latency test: Reflex low latency on, boost off, frame cap 120 fps (Reflex's); driver low latency on, last sleep 16666 us, game Sleep used, G-SYNC fullscreen`.
  This came right after `run finished`: Reflex stayed on, now with `options.txt`'s 120 fps cap, for the 7 s until
  the game closed.

**`Latency test:` (L2):** only the late read on (6×) and off (5×). No Reflex without Perfect Presentation, as
intended.

**`Perfect Presentation:`:**
- L1 and the by-feel launch:
  - `Perfect Presentation: on, the game window will be shown through a DXGI swap chain`
  - `Perfect Presentation: NeoForge's OpenGL version check no longer reads the game window`
  - `Perfect Presentation: DXGI swap chain ready (OpenGL on a hidden context)`
  - `Perfect Presentation: swap chain wait timed out` (a WARN, once per launch, during the world's loading, 44 s after
    the swap chain was ready and before any run)
- L2: `Perfect Presentation: off (config/create_galore_bridge-display.properties)`

**`DLAA:`:**
- `DLAA: made shaderpacks/Photon v1.3a + DLAA from photon_v1.3a + Clrwl_1.0.5.zip` (L1 only; the new Bridge remade
  the copy)
- `DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device; OpenGL on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2), nvngx_dlss.dll 310.9.16 from $T\instance\create-galore-dlss`.
  This is also with Perfect Presentation on. In profiling 1 (Bridge 1.91.0), DLSS used Perfect Presentation's device
  then.
- `DLAA: DLSS running at 2560x1440 (full resolution), preset K, the pack's exposure`
- `DLAA: on for this shader pack, engine dlss, DLSS preset K (set to default), full resolution`, at each shader reload
  (18× in L1, 16× in L2, 7× in the by-feel launch)
- `DLAA: closed its DirectX device` at each quit
- 225 `DLAA: NGX:` lines per launch (NGX's own log)
- No `DLAA: stopped`, `could not start` or `Failed to create shader rendering pipeline`

## The PC's settings (looked at only, nothing changed)

**NVIDIA Control Panel, Manage 3D settings, global:**
- Low Latency Mode **Off**
- Max Frame Rate **Off**; Background Application Max Frame Rate Off
- Vertical sync **Use the 3D application setting**
- Monitor Technology **G-SYNC Compatible**
- Power management mode **Normal**
- Also: triple buffering Off, OpenGL rendering GPU Auto-select, Vulkan/OpenGL present method Auto, preferred refresh
  rate Highest available, threaded optimization On.

**Program settings:**
- **There's no profile for `java.exe`, which the test game runs.** So the global settings apply.
- A `javaw.exe` profile exists (for the Java runtime of Emre's own launcher). It sets only power management to Normal,
  and everything else to "Use global setting".

**Set up G-SYNC:** G-SYNC / G-SYNC Compatible **enabled, for full screen mode** (not windowed). The driver reported
`G-SYNC fullscreen` in the Reflex modes.

**Monitor:** ASUS XG27AQDMES over DisplayPort, 2560x1440 at **240 Hz** (Windows reports 239, at most 240), 10 bpc.

So no control panel frame cap and no Ultra Low Latency Mode change what the modes mean.

## Emre's notes (by feel)

At the moving scene with Perfect Presentation on, 21:49–21:58. His words, tidied and grouped:

**`lat-base` (the game as it is):** "This is already how I played the game. I already know how it feels." He went
on quickly.

**`lat-late` (late input read):**
- "Very close to placebo, so I'm not going to overclaim."
- "You know how games like Overwatch have an option called raw input, where it reads your mouse more directly? That's
  how it feels."
- He doesn't notice it in the flicks. But flicking onto a mob and then quickly correcting the overshoot, "it feels
  like it responds to that faster".
- It probably improves aim marginally. How much that matters in Minecraft is arguable, but "there's definitely an
  improvement which will help for future stuff like frame generation".

**`lat-reflex` (Reflex low latency + late read; with his 120 fps cap, taken over by Reflex's limiter):**
- "Kind of crazy. It feels exactly like how I know Reflex to feel from every game that natively integrates it. You
  really can tell."
- Shaders always made Minecraft feel less snappy to him. Without them it wasn't always smoother, but it was more
  responsive.
- "This now makes Minecraft with shaders feel even more responsive than Minecraft without shaders at 240 FPS", though
  that still has smoother motion.
- The average person might not tell. With his time in competitive games, "it is a big difference. It definitely
  helps. I would have it on even without frame generation."

**Back to `lat-base`:**
- "Yes, you can tell. And it's way more obvious than I thought it would be. The baseline feels slow ... that classic
  sluggish feel."
- "If you really jitter your arm around really quickly ... it's a bit behind you. You have to wait for it to catch up."
- "On its own, it's not that bad, but next to Reflex, it's very obvious. Reflex definitely helps here."

## Method

**Update.**
- `git fetch` and `checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh` (`5016ab2`).
- `create-galore-bridge-1.96.0.jar` removed; `create-galore-bridge-1.105.0.jar` and the test kit jar (54,240 B)
  copied in.
- `nvngx_dlss.dll` 310.9.16.0 checked.

**Settings, and what they were before.** All are back at the end, checked byte for byte against copies taken before
the session.
- `create_galore_bridge-display.properties`:
  - `dlaa=true` → `dlaa=dlss`;
  - `perfectPresentation=false` → `true` (L1, by feel), `false` (L2).
- `config\fml.toml`: `earlyWindowControl = false` was already so.
- `config\DistantHorizons.toml`: `numberOfThreads` 32 → 2.
- `options.txt` and Sodium's `cpu_render_ahead_limit` (3): not touched. Each run put back the frame cap, vsync and
  render-ahead it had set, so both were unchanged after each launch.
- The kit's config didn't exist. The kit wrote its defaults, and it was deleted again at the end.

**Launches.** As in step 4, with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test`:

| Launch | Perfect Presentation | `-D...` | Run | Time |
| --- | --- | --- | --- | --- |
| 1 | on | `runs=L1` | `20261025-212948-L1`: 16 plays (8 modes twice), shore and canopy, 27,540 frames | 21:29:48–21:34:50 |
| 2 | off | `runs=L2` | `20261025-213710-L2`: 14 plays (7 modes twice), 24,180 frames | 21:37:10–21:41:21 |
| by feel | on | `modes=lat-base`, `viewpoints=moving`, `paths=still`, `byEye=lat-base` | `20261025-214908`: one short run, then by eye | 21:49:08–21:58:26 |

**What was open.**
- No game server, and no other game (Emre: quiet).
- Still running in the background: the Claude app, FanControl, the NVIDIA overlay, the PresentMon service, Razer's
  elevation service, a Settings window, and **Steam (idle)**.
- The game window stayed in front during the runs.

**What didn't go to plan, or went beyond the runbook:**
1. **The by-feel launch as the runbook writes it started the full default test.** The runbook says
   `...autorun=dlaa-test` and `...byEye=lat-base` instead of `...runs=`.
   - Without a run list, the kit's autorun plays its default run, and `byEye` only starts after a run ends. The run
     `20261025-214427` had 56 modes, 5 viewpoints and 210,140 frames.
   - The chat couldn't be opened while it ran, neither by Emre nor by me, so `/dlaatest stop` couldn't be typed.
   - The game was closed by ending its process (21:47). That run's folder is partial and unused.
   - The game was then relaunched with one short run first (`modes=lat-base`, `viewpoints=moving`, `paths=still`),
     after which `byEye` started as intended.
   - The killed run had left `dlaa=true`, `dlaaPreset=K` in the display settings. They were put back from the copy
     before the relaunch.
2. **Emre typed `/dlaatest mode lat-reflex` and `/dlaatest mode lat-base` himself.** At those points the game wouldn't
   take keyboard focus from the Claude window. I typed `lat-late`.
3. **The by-feel part runs with Emre's own `options.txt`: vsync on and a 120 fps cap**, as the runbook says (only
   Reflex and the late read switch). In `lat-reflex`, Reflex's limiter took over the 120 fps cap
   (`frame cap 120 fps (Reflex's)`).
4. **A crash log from the previous session**, `instance\hs_err_pid2480.log`, was found while checking for crash
   logs. It isn't from this test and isn't committed.
   - It is from Upscaling 1, when that game was quit (14:16:57). The Bridge was 1.96.0, the error an access violation
     in `dev.creategalore.bridge.dlss.DlssPass$Texture.close()`. It was missed then, because the launcher reported a
     normal exit.
   - This session's quits (L1, L2 and the by-feel launch, with Bridge 1.105.0) were clean: `DLAA: closed its
     DirectX device`, and no new crash log.

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261025-212948-L1`, `-213710-L2` and `20261025-214908` (by
  feel), and the partial `20261025-214427`. They hold `manifest.json` only.
- The three game logs, this session's notes, the settings copies and the kit's config as it wrote it:
  `$T\reflex1\`.
