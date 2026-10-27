# Pacer 2 on the RTX 4080 SUPER: the pacer again, with the queue read without a flush

PC session on 2026-10-26, 09:20–09:55, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`). It followed "Pacer 2" in `tools/dlss-testkit/PC-RUNBOOK.md` at `d3247d0`, with Bridge 1.110.0 (test build)
and the test kit from that commit.
- Two launches: L5 with Perfect Presentation on, L6 with it off. No by-feel part.
- DLAA at full resolution (`dlaa=dlss`, preset K), at 2560x1440.
- No crash. This is a test, not a fix: no code was changed.

## First check: the frame rate with Perfect Presentation on is back

| `lat-base`, Perfect Presentation on, shore / canopy | fps |
| --- | --- |
| Reflex 1 (Bridge 1.105.0) | 171 / 154 |
| Pacer 1 (Bridge 1.107.0) | 116 / 113 |
| **Pacer 2 (Bridge 1.110.0)** | **171 / 156** (plays: 165 and 176 / 155 and 157) |

- **The flush was the cause.** Without it, the baseline is back at Reflex 1's level in frame rate and in latency
  (11.9 / 14.5 ms, against 11.9 / 14.3 ms in Reflex 1).
- Without Perfect Presentation, the frame rate is as before (175 / 158 fps).

## Summary

- **With Perfect Presentation on, is there a queue now?** Yes: 4.6 / 5.5 ms at the baseline (q10 3.8 / 5.0 ms).
- **Does the pacer bring the latency down, and at what fps cost?**
  - The automatic target cuts it by 3.5 / 5.6 ms, to 8.4 / 8.9 ms, for −6% / −6% fps.
  - 2000 µs gives about the same (8.2 / 8.9 ms) for −7% / −8%.
- **Which target keeps the frame rate within 3%, and at what latency?**
  - With Perfect Presentation, **4000 µs**: 9.8 / 10.5 ms (−2.1 / −4.0 ms against the baseline, and −0.7 / −1.4 ms
    beyond the late read alone), at +3% / 0% fps.
  - Without it, **5000 µs**: 10.5 / 11.3 ms (−3.9 / −7.3 ms), at +1% / −1% fps. That's 0.5 / 1.2 ms below Reflex 1's
    best free option, render-ahead 1 with the late read (11.0 / 12.5 ms).
  - 3000 µs without Perfect Presentation is just outside the limit: 8.6 / 9.2 ms at −4% / −4%.
- **Does Reflex add anything on top of the pacer?** No: `lat-reflex-pacer` 8.4 / 8.8 ms against `lat-pacer`'s
  8.4 / 8.9 ms, at the same fps. Reflex alone equals the late read (10.5 / 11.9 ms), as in Reflex 1.
- **Does `lat-cap-default` give about Pacer 1's `lat-cap-start` (8.8 / 8.6 ms)?** Yes: 8.9 / 8.7 ms, with a 60 fps
  cap and no test switches. The frame cap now waits at the frame's start by default.
- **The pacer's waits are accurate:** they end 0 µs late at the median, and at most 31 µs late at the 90th percentile.

## Launch 1: Perfect Presentation on (`20261026-093413-L5`)

Median input-to-GPU latency in ms, the two plays' range in brackets, then the change and the fps (with its change)
against `lat-base`:

| mode | shore ms (repeats) | change | fps (change) | canopy ms (repeats) | change | fps (change) |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 11.9 (11.0–12.8) | – | 171 | 14.5 (14.0–15.0) | – | 156 |
| `lat-late` | 10.5 (10.5–10.5) | −1.4 | 175 (+3%) | 11.9 (11.9–11.9) | −2.6 | 157 (+0%) |
| `lat-reflex` | 10.5 (10.5–10.5) | −1.4 | 175 (+3%) | 11.9 (11.9–11.9) | −2.6 | 157 (+0%) |
| `lat-pacer` (automatic) | 8.4 (8.2–8.6) | −3.5 | 160 (−6%) | 8.9 (8.8–9.0) | −5.6 | 147 (−6%) |
| `lat-pacer-2000` | 8.2 (8.2–8.3) | −3.7 | 158 (−7%) | 8.9 (8.9–8.9) | −5.6 | 143 (−8%) |
| `lat-pacer-4000` | **9.8** (9.7–9.8) | −2.1 | **175 (+3%)** | **10.5** (10.4–10.5) | −4.0 | **156 (−0%)** |
| `lat-reflex-pacer` | 8.4 (8.2–8.5) | −3.5 | 158 (−8%) | 8.8 (8.8–8.9) | −5.7 | 147 (−6%) |
| `lat-cap-default` (60 fps) | 8.9 (8.9–8.9) | −3.0 | 60 | 8.7 (8.7–8.7) | −5.8 | 60 |

| mode | queue median / q10 ms, shore | canopy | pacer wait ms, shore / canopy | late median / p90 µs, shore | canopy | target µs, shore | canopy |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 4.6 / 3.8 | 5.5 / 5.0 | – | – | – | – | – |
| `lat-late` | 4.8 / 4.4 | 5.6 / 5.5 | – | – | – | – | – |
| `lat-reflex` | 4.8 / 4.3 | 5.5 / 5.5 | – | – | – | – | – |
| `lat-pacer` | 2.4 / 1.0 | 2.3 / 1.0 | 2.0 / 2.8 | 0 / 0 | 0 / 0 | 1669, 1939 | 2134, 2063 |
| `lat-pacer-2000` | 2.2 / 0.6 | 2.2 / 0.3 | 2.2 / 2.8 | 0 / 0 | 0 / 0 | 2000 | 2000 |
| `lat-pacer-4000` | 4.1 / 3.6 | 4.1 / 3.8 | 0.8 / 1.5 | 0 / 0 | 0 / 31 | 4000 | 4000 |
| `lat-reflex-pacer` | 2.2 / 0.9 | 2.2 / 0.9 | 2.1 / 2.9 | 0 / 0 | 0 / 0 | 1953, 2254 | 2395, 2368 |
| `lat-cap-default` | −8.0 / −10.0 | −8.2 / −10.1 | – | – | – | – | – |

- The automatic target settles at 1.7–2.4 ms. That's three times the frames' variation.
- At 4000 µs the queue stays at about 4.1 ms, and the pacer waits only 0.8–1.5 ms per frame: just enough to take the
  queue's top off without the GPU ever running short.
- With the 60 fps cap the queue is negative (−8 ms): the GPU waits for each frame, as expected under a cap.
- **The driver's report in the Reflex modes:** low latency on, the game's sleep used, `G-SYNC fullscreen`, unit check
  1.00.
  - `lat-reflex`: driver input to GPU end 11.1 / 12.5 ms, against the Bridge's 10.5 / 11.9 ms.
  - `lat-reflex-pacer`: 8.7–9.0 / 9.3–9.5 ms against 8.2–8.5 / 8.8–8.9 ms.
  - The driver sees 0.0 ms of GPU work, as before.
  - Full rows: [latency-L5.txt](latency-L5.txt).

## Launch 2: Perfect Presentation off (`20261026-094018-L6`)

| mode | shore ms (repeats) | change | fps (change) | canopy ms (repeats) | change | fps (change) |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 14.4 (12.2–16.5) | – | 175 | 18.6 (18.6–18.7) | – | 158 |
| `lat-ra1-late` (render-ahead 1 + late read) | 11.0 (11.0–11.1) | −3.4 | 177 (+1%) | 12.5 (12.5–12.5) | −6.2 | 159 (+0%) |
| `lat-pacer` (automatic) | 8.2 (8.1–8.4) | −6.1 | 165 (−6%) | 8.9 (8.7–9.2) | −9.7 | 150 (−5%) |
| `lat-pacer-3000` | 8.6 (8.6–8.6) | −5.8 | 168 (−4%) | 9.2 (9.2–9.3) | −9.4 | 151 (−4%) |
| `lat-pacer-5000` | **10.5** (10.4–10.6) | −3.9 | **177 (+1%)** | **11.3** (11.3–11.3) | −7.3 | **157 (−1%)** |
| `lat-ra1-pacer` (render-ahead 1 + automatic pacer) | 8.2 (8.1–8.3) | −6.2 | 165 (−6%) | 8.9 (8.9–9.0) | −9.7 | 149 (−6%) |

| mode | queue median / q10 ms, shore | canopy | pacer wait ms, shore / canopy | late median / p90 µs, shore | canopy | target µs, shore | canopy |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 8.5 / 6.3 | 12.3 / 9.9 | – | – | – | – | – |
| `lat-ra1-late` | 5.5 / 3.5 | 6.2 / 4.5 | – | – | – | – | – |
| `lat-pacer` | 2.4 / 0.9 | 2.4 / 0.9 | 1.8 / 2.6 | 0 / 6 | 0 / 0 | 2482, 2301 | 2241, 1888 |
| `lat-pacer-3000` | 2.9 / 1.1 | 2.8 / 0.6 | 1.7 / 2.7 | 0 / 6 | 0 / 0 | 3000 | 3000 |
| `lat-pacer-5000` | 4.9 / 3.1 | 5.0 / 3.0 | 1.7 / 2.7 | 0 / 3 | 0 / 0 | 5000 | 5000 |
| `lat-ra1-pacer` | 2.4 / 0.5 | 2.4 / 0.6 | 1.9 / 2.6 | 0 / 3 | 0 / 0 | 2247, 2099 | 2551, 1745 |

**The shore's baseline is unsteady again in its first play**, the run's first stretch: 12.2 ms, then 16.5 ms. The
same happened in Reflex 1 and Pacer 1. Against the steady second play (16.5 ms), the shore's changes are:
render-ahead 1 with the late read −5.5, automatic pacer −8.3, 3000 µs −7.9, 5000 µs −6.0, render-ahead 1 with the
pacer −8.3 ms. That's the same order as at the canopy.

- **Without Perfect Presentation, the pacer does the same job as render-ahead 1 and more.** Render-ahead 1 on its own
  halves the queue (to 5.5 / 6.2 ms) at no cost. The 5000 µs pacer holds it at about 5 ms by waiting instead, which
  also takes the late read's place, for 0.5 / 1.2 ms less latency.
- **Render-ahead 1 adds nothing under the automatic pacer**: 8.2 / 8.9 ms either way.
- Full rows: [latency-L6.txt](latency-L6.txt).

## Log lines

**`Latency:` (L5):**
- `Latency: GPU pacer on, frames may queue on the GPU as much as they vary` (4×)
- `Latency: GPU pacer on, frames may queue 2000 us on the GPU` (2×) and `... 4000 us ...` (2×)
- `Latency: GPU pacer off` (2×)
- `Latency: the frame cap waits at the start of the frame, before input` (3×) and
  `Latency: the frame cap waits in the game's limiter` (2×)
- `Latency: waits use a high resolution timer` (1×)
- `Latency: input read again just before rendering on` (2×) and `... off` (2×)

**`Latency:` (L6):** the same, with `... queue 3000 us ...` (2×) and `... queue 5000 us ...` (2×) instead of 2000 and
4000.

**`Reflex:` (L5):**
- `Reflex: started on Perfect Presentation's Direct3D 11 device (NVIDIA NVAPI)`
- `Reflex: on` (4×) and `Reflex: off` (4×)
- `Reflex: low latency on, boost off, frame cap none; driver low latency on, last sleep 0 us, game Sleep not used, G-SYNC fullscreen`
  (3×), and once the same with `driver low latency off` (the first switch on)
- These lines are written at the moment of switching. The reports over each stretch say low latency on and the game's
  sleep used.

**No `Latency:` or `Reflex:` WARN or ERROR lines** in either launch.

**`Perfect Presentation:`:**
- L5: `on, the game window will be shown through a DXGI swap chain`, `NeoForge's OpenGL version check no longer reads
  the game window`, `DXGI swap chain ready (OpenGL on a hidden context)`, and one WARN, `swap chain wait timed out`,
  while loading, before the run
- L6: `off (config/create_galore_bridge-display.properties)`

**`DLAA:`:**
- `NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device; OpenGL on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2), nvngx_dlss.dll 310.9.16 from $T\instance\create-galore-dlss`
- `DLSS running at 2560x1440 (full resolution), preset K, the pack's exposure`
- `on for this shader pack, engine dlss, DLSS preset K (set to default), full resolution` at each reload (18× in L5,
  14× in L6)
- `closed its DirectX device` at each quit
- No `DLAA: stopped`, `could not start` or `Failed to create shader rendering pipeline`

## The PC's settings (looked at only, nothing changed)

Read again before launch 1. Unchanged from Reflex 1 and Pacer 1:
- **NVIDIA Control Panel, global:** Low Latency Mode Off, Max Frame Rate Off, Vertical sync "Use the 3D application
  setting", Monitor Technology G-SYNC Compatible, Power management mode Normal.
- **G-SYNC:** enabled, for full screen mode only.
- **Monitor:** 2560x1440 at 240 Hz (Windows reports 239, at most 240).

## Method

**Update.**
- `git fetch` and `checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh` (`d3247d0`).
- `create-galore-bridge-1.107.0.jar` removed; `create-galore-bridge-1.110.0.jar` and the test kit jar (55,213 B)
  copied in.
- `nvngx_dlss.dll` 310.9.16.0 checked.

**Settings, and what they were before.** All are back at the end, checked byte for byte against copies taken before
the session.
- `create_galore_bridge-display.properties`:
  - `dlaa=true` → `dlaa=dlss`;
  - `perfectPresentation=false` → `true` (L5), `false` (L6).
  - The new Bridge rewrites this file with comments. It was put back as it was.
- `config\DistantHorizons.toml`: `numberOfThreads` 32 → 2.
- `config\fml.toml`: `earlyWindowControl = false` was already so.
- `options.txt` and Sodium's options: not touched, and unchanged after each launch.
- The kit's config didn't exist. The kit wrote its defaults, and it was deleted again at the end.

**Launches.** As in step 4, with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test`:

| Launch | Perfect Presentation | `runs=` | Run | Time |
| --- | --- | --- | --- | --- |
| 1 | on | `L5` | `20261026-093413-L5`: 16 plays (8 modes twice), 27,540 frames | 09:34:13–09:38:08 |
| 2 | off | `L6` | `20261026-094018-L6`: 12 plays (6 modes twice), 20,820 frames | 09:40:18–09:42:48 |

**What was open.**
- No game server, and no other game or launcher.
- Still running in the background: the Claude app, FanControl, the NVIDIA overlay, NZXT CAM (its helper at about 17% of
  one core), PowerToys, Razer's app engine and elevation service, the PresentMon service, the Windows Package Manager
  server, and a Settings window.

**Everything went to plan.** No crash, and no new crash log. The only `hs_err` file in the instance is the old one
from Upscaling 1.

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261026-093413-L5` and `20261026-094018-L6`. They hold
  `manifest.json` only.
- The game logs, this session's notes, the settings copies, and the display settings file as 1.110.0 left it:
  `$T\pacer2\`.
