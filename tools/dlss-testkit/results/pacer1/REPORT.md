# Pacer 1 on the RTX 4080 SUPER: the GPU pacer for uncapped play, and a frame cap that waits before input

PC session on 2026-10-25/26, 22:50–00:15, by a Claude Code session in `C:\Users\<user>\Documents\Claude\MC-CREATE`
(`$T`). It followed "Pacer 1" in `tools/dlss-testkit/PC-RUNBOOK.md` at `dfdfd28`, with Bridge 1.107.0 (the test
build with the pacer) and the test kit from that commit.
- Launch 1 (L3) has Perfect Presentation on; it ran twice, see Method. Launch 2 (L4) has it off.
- DLAA at full resolution (`dlaa=dlss`, preset K) throughout, at 2560x1440.
- **The by-feel part was skipped**, at the player's call: "until it's all working".
- No crash. This is a test, not a fix: no code was changed.

**What is measured:** the Bridge's latency meter, as in Reflex 1, from the game last reading input to the GPU finishing
that frame. The value is the median over each 600-frame stretch held still. Each mode played twice, at `shore` and
`canopy`.

## Summary

- **Does the pacer bring the uncapped latency near 8.7 ms, and at what fps cost?**
  - With Perfect Presentation off, yes: 7.9 / 8.5 ms (shore / canopy, automatic target), against 16.0 / 18.8 ms
    as the game is. **But it costs 6% / 6% fps, more than the runbook's 3% limit, so by that rule it fails.**
  - With Perfect Presentation on, it does nothing. There was no GPU queue for it to remove, its waits stayed at 0, and
    every uncapped mode came out within 0.6 ms of the baseline.
- **Which target is best?**
  - The fixed 500 µs target gives the lowest latency (7.6 / 7.9 ms), but loses 20% / 16% fps.
  - The automatic target (measured at 1.6–2.1 ms) loses 6% / 6%, for 0.3–0.6 ms more latency.
  - The 2000 µs target only ran with Perfect Presentation on, where no target did anything. So it's open whether a
    larger target would keep within 3%.
- **Is the automatic target as good as the best fixed one?** It's the better trade of the two that could be told
  apart (against 500 µs): a third of the fps cost for 0.3–0.6 ms more latency. Neither keeps the frame rate within 3%.
- **Does Reflex add anything on top of the pacer?** Not measurably: `lat-reflex-pacer` = `lat-pacer` = `lat-reflex`
  (12.0 / 12.9 ms). But that was with Perfect Presentation on, where the pacer had nothing to do, and Reflex needs
  Perfect Presentation, so the two never met under a queue.
- **Does `lat-cap-start` match `lat-cap-reflex` or `lat-cap-late`?** It matches `lat-cap-reflex`.
  - With Perfect Presentation: 8.8 / 8.6 ms against Reflex's 8.9 / 8.7 ms. The game's limiter with the late read gives
    16.7 ms.
  - So the difference is where the wait sits, and any GPU gets it.
  - Without Perfect Presentation, the game's limiter with the late read is already as good (8.5 / 8.4 ms against
    8.4 / 8.3 ms).
- **Sodium's render-ahead 1 with the late read** (Reflex 1's best without Perfect Presentation) repeats Reflex 1's
  numbers: 11.2 / 12.6 ms at no fps cost. Adding the pacer on top gives the pacer's numbers, with the pacer's fps
  cost.
- **With Perfect Presentation on, this build runs about 30% slower than Reflex 1's.**
  - The baseline gave 116 / 113 fps (106–125 over the run), against 171 / 154 fps in Reflex 1 on Bridge 1.105.0, with
    the same PC and settings.
  - It repeated in both L3 runs. With Perfect Presentation off, the frame rate is the same as in Reflex 1 (174 / 157
    against 170 / 158 fps).
  - The cause isn't known; see "The frame rate with Perfect Presentation on".

## Launch 1: Perfect Presentation on (`20261025-235342-L3`)

Median input-to-GPU latency in ms, the two plays' range in brackets, then the change and the fps (with its change)
against `lat-base`:

| mode | shore ms (repeats) | change | fps (change) | canopy ms (repeats) | change | fps (change) |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 12.5 (12.2–12.9) | – | 116 | 13.3 (13.0–13.6) | – | 113 |
| `lat-late` | 12.2 (12.0–12.4) | −0.4 | 119 (+3%) | 13.1 (12.9–13.2) | −0.3 | 117 (+3%) |
| `lat-pacer` (automatic target) | 12.0 (11.9–12.0) | −0.6 | 123 (+7%) | 12.9 (12.9–12.9) | −0.4 | 120 (+6%) |
| `lat-pacer-500` | 12.0 (12.0–12.1) | −0.5 | 124 (+7%) | 12.9 (12.8–12.9) | −0.4 | 120 (+6%) |
| `lat-pacer-2000` | 12.0 (12.0–12.0) | −0.5 | 125 (+8%) | 12.9 (12.9–12.9) | −0.4 | 121 (+6%) |
| `lat-reflex` | 12.1 (12.0–12.2) | −0.4 | 121 (+4%) | 13.0 (12.9–13.1) | −0.3 | 118 (+4%) |
| `lat-reflex-pacer` | 12.0 (12.0–12.0) | −0.6 | 124 (+7%) | 12.9 (12.9–12.9) | −0.4 | 121 (+6%) |
| `lat-cap-late` (60 fps, game's limiter, late read) | 16.7 (16.7–16.7) | +4.2 | 60 | 16.7 (16.7–16.7) | +3.4 | 60 |
| `lat-cap-start` (60 fps, wait at the frame's start, late read) | **8.8** (8.8–8.9) | −3.7 | 60 | **8.6** (8.6–8.7) | −4.7 | 60 |
| `lat-cap-reflex` (60 fps by Reflex, late read) | 8.9 (8.9–9.0) | −3.6 | 60 | 8.7 (8.7–8.7) | −4.7 | 60 |

**The frame rate rises during the run.**
- `lat-base`'s first play, the run's first stretch, ran at 106–108 fps. Its second play ran at 121–123 fps.
- So the `+3%` to `+8%` against `lat-base` is the start of the run, not a gain from the modes.
- Against `lat-late` (117–119 fps), the pacer modes are at most 3–5% faster, and Reflex the same.

GPU queue, gap and pacer, as medians over the two plays:

| mode | queue ms, shore / canopy | GPU gap median / p90 ms, shore | canopy | pacer wait ms | late µs | target µs |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base`, `lat-late`, `lat-reflex` | −0.1 / −0.1 | 0.0 / 0.0 | 0.0 / 0.0 | – | – | – |
| `lat-pacer` | −0.1 / −0.1 | 0.0 / 0.0 | 0.0 / 0.0 | 0.0 | 0 | 250 (automatic, its floor) |
| `lat-pacer-500` | −0.1 / −0.1 | 0.0 / 0.0 | 0.0 / 0.0 | 0.0 | 0 | 500 |
| `lat-pacer-2000` | −0.1 / −0.1 | 0.0 / 0.0 | 0.0 / 0.0 | 0.0 | 0 | 2000 |
| `lat-reflex-pacer` | −0.1 / −0.1 | 0.0 / 0.0 | 0.0 / 0.0 | 0.0 | 0 | 250 |
| `lat-cap-late` | −0.1 / −0.2 | 0.0 / 1.5 | 0.0 / 1.5 | – | – | – |
| `lat-cap-start` | −0.1 / −0.1 | 8.0 / 8.9 | 8.2 / 9.6 | – | – | – |
| `lat-cap-reflex` | −0.1 / −0.1 | 8.0 / 9.0 | 8.2 / 9.7 | – | – | – |

- **No mode had a queue.** Each frame's rendering never waited on the GPU (−0.1 ms), and uncapped, the GPU never idled
  between frames (gap 0.0).
- So frames reached the GPU just as it got free. This looks like Perfect Presentation's swap chain already pacing the
  frames.
- The pacer correctly waited 0 µs at every target, and so changed nothing. Its automatic target sat at its floor,
  250 µs: the frames' arrival hardly varied.
- With the game's 60 fps limiter, the GPU shows no idle gap either (median 0, p90 1.5 ms). With the cap at the frame's
  start, or Reflex's, it idles about 8 ms between frames.
- The GPU's lag (frame end on the CPU to the GPU finishing it) is 3.9–4.9 ms uncapped. With Reflex 1's build it was
  5–9 ms.

**The driver's report in the Reflex modes:** low latency on, the game's sleep used, `G-SYNC fullscreen`, unit check
1.00 everywhere.
- `lat-reflex`: driver input to GPU end 8.2–9.0 ms, against the Bridge's 12.0–13.1 ms.
- `lat-reflex-pacer`: 7.8–8.5 ms against 12.0–12.9 ms.
- `lat-cap-reflex`: 8.5–8.8 ms against 8.7–9.0 ms.
- The driver again sees 0.0 ms of GPU work (it only sees its own Direct3D 11 device).
- Full rows: [latency-L3.txt](latency-L3.txt).

## Launch 2: Perfect Presentation off (`20261026-000237-L4`)

| mode | shore ms (repeats) | change | fps (change) | canopy ms (repeats) | change | fps (change) |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 16.0 (15.3–16.8) | – | 174 | 18.8 (18.8–18.9) | – | 157 |
| `lat-late` | 16.6 (16.5–16.7) | +0.6 | 176 (+1%) | 18.8 (18.8–18.8) | −0.0 | 157 (+0%) |
| `lat-ra1-late` (Sodium render-ahead 1 + late read) | 11.2 (11.2–11.2) | −4.8 | 172 (−1%) | 12.6 (12.6–12.6) | −6.2 | 157 (+0%) |
| `lat-pacer` (automatic target) | **7.9** (7.8–7.9) | −8.2 | **163 (−6%)** | **8.5** (8.3–8.7) | −10.3 | **148 (−6%)** |
| `lat-ra1-pacer` (render-ahead 1 + pacer) | 7.8 (7.7–7.9) | −8.2 | 163 (−6%) | 8.4 (8.3–8.4) | −10.5 | 149 (−5%) |
| `lat-pacer-500` | 7.6 (7.5–7.7) | −8.4 | 139 (−20%) | 7.9 (7.9–8.0) | −10.9 | 133 (−16%) |
| `lat-cap-late` (60 fps, game's limiter, late read) | 8.5 (8.3–8.8) | −7.5 | 60 | 8.4 (8.2–8.6) | −10.4 | 60 |
| `lat-cap-start` | 8.4 (8.3–8.4) | −7.7 | 60 | 8.3 (8.3–8.3) | −10.5 | 60 |

The shore's baseline again has an unsteady first play (15.3 ms, against 16.8 ms in the repeat), as in Reflex 1. The
canopy is steady.

| mode | queue ms, shore / canopy | GPU gap median / p90 ms, shore | canopy | pacer wait ms, shore / canopy | late µs | target µs, shore | canopy |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | 10.1 / 12.4 | 0.0 / 0.0 | 0.0 / 0.0 | – | – | – | – |
| `lat-late` | 11.0 / 12.5 | 0.0 / 0.0 | 0.0 / 0.0 | – | – | – | – |
| `lat-ra1-late` | 5.6 / 6.3 | 0.0 / 0.0 | 0.0 / 0.0 | – | – | – | – |
| `lat-pacer` | 1.9 / 1.9 | 0.0 / 0.0 | 0.0 / 0.0 | 2.0 / 2.8 | 0 / 0 | 1581, 1604 | 1677, 1667 |
| `lat-ra1-pacer` | 1.9 / 1.8 | 0.0 / 0.0 | 0.0 / 0.0 | 2.0 / 2.9 | 0 / 0 | 2077, 1616 | 1815, 1938 |
| `lat-pacer-500` | 0.6 / 0.7 | 0.0 / 0.8 | 0.0 / 0.8 | 3.0 / 3.7 | 0 / 0 | 500 | 500 |
| `lat-cap-late` | −0.0 / −0.0 | 8.2 / 10.1 | 8.3 / 10.3 | – | – | – | – |
| `lat-cap-start` | 0.1 / 0.1 | 8.6 / 9.9 | 8.4 / 10.3 | – | – | – | – |

- **Without Perfect Presentation, frames queue 10–12.5 ms on the GPU** (Sodium's render-ahead of 3), which is most of
  the latency.
- **The pacer cuts that queue to about 1.9 ms with the automatic target.** Its wait is 2.0–2.9 ms per frame, it never
  ended late (0 µs), and its target settled at 1.6–2.1 ms (three times the frames' variation).
- **With 500 µs it cuts the queue to 0.6–0.7 ms.** The GPU then starts to run dry at times (gap p90 0.8 ms), which
  fits the large fps loss.
- **The fps loss with the automatic target (6%) comes without the GPU idling** (gap 0.0 at median and p90). So this
  looks like the GPU doing less per second when it's fed just in time, not starving. It may be clocks or the timestamp
  flush; this capture can't tell.
- **The late read alone does nothing without Perfect Presentation**, as in Reflex 1: the queue stays the same.
- Full rows: [latency-L4.txt](latency-L4.txt).

## The frame rate with Perfect Presentation on

| | Reflex 1 (Bridge 1.105.0) | Pacer 1 (Bridge 1.107.0) |
| --- | --- | --- |
| `lat-base`, Perfect Presentation on, shore / canopy | 171 / 154 fps (L1) | 116 / 113 fps (L3 rerun); 118 / 112 fps (first L3) |
| `lat-base`, Perfect Presentation off | 170 / 158 fps (L2) | 174 / 157 fps (L4) |

- Same PC, same settings, the same quiet state, and the game window in front.
- Only Perfect Presentation's frame rate dropped, by about a third, and it did in both L3 runs.
- In L3, the GPU never idles between frames (gap 0.0), yet frames never wait for it (queue −0.1 ms).
- The code that changed between the two builds:
  - the pacer's GPU timestamp, flushed at each frame's rendering start, which feeds the `queue` column in every mode;
  - the Reflex setting on the DLAA screen (0.5.2);
  - `StartGuard`.
- Which of them, if any, costs this, isn't known. Re-running Reflex 1's L1 on this build, or this build with the
  timestamp off, would tell.

## Log lines

**`Latency:` (L3 and L4):**
- `Latency: GPU pacer on, frames may queue on the GPU as much as they vary` (4× per launch)
- `Latency: GPU pacer on, frames may queue 500 us on the GPU` (2×)
- `Latency: GPU pacer on, frames may queue 2000 us on the GPU` (2×, L3 only)
- `Latency: GPU pacer off` (2×)
- `Latency: the frame cap waits at the start of the frame, before input` (2×)
- `Latency: the frame cap waits in the game's limiter` (1×)
- `Latency: waits use a high resolution timer` (1×, at the first wait)
- `Latency: input read again just before rendering on` (2×) and `... off` (1×)

**`Reflex:` (L3):**
- `Reflex: started on Perfect Presentation's Direct3D 11 device (NVIDIA NVAPI)`
- `Reflex: on` (6×) and `Reflex: off` (6×)
- `Reflex: low latency on, boost off, frame cap none; driver low latency on, last sleep 0 us, game Sleep not used, G-SYNC fullscreen`
  (3×)
- The same with `driver low latency off` (1×, the first switch on; the mode's report then says on)
- `Reflex: low latency on, boost off, frame cap 60 fps (Reflex's); driver low latency on, last sleep 0 us, game Sleep not used, G-SYNC fullscreen`
  (2×)
- These lines are written at the moment of switching. The reports over each stretch say the game's sleep was used.

**No `Latency:` or `Reflex:` WARN or ERROR lines** in either launch.

**`Perfect Presentation:`:**
- L3:
  - `on, the game window will be shown through a DXGI swap chain`
  - `NeoForge's OpenGL version check no longer reads the game window`
  - `DXGI swap chain ready (OpenGL on a hidden context)`
  - one WARN, `swap chain wait timed out`, while the world loaded, before the run
- L4: `off (config/create_galore_bridge-display.properties)`

**`DLAA:`:**
- `NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device; OpenGL on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2), nvngx_dlss.dll 310.9.16 from $T\instance\create-galore-dlss`
- `DLSS running at 2560x1440 (full resolution), preset K, the pack's exposure`
- `on for this shader pack, engine dlss, DLSS preset K (set to default), full resolution` at each reload (22× in L3,
  18× in L4)
- `closed its DirectX device` at each quit
- No `DLAA: stopped`, `could not start` or `Failed to create shader rendering pipeline`

## The PC's settings (looked at only, nothing changed)

Read again before launch 1. Unchanged from Reflex 1:
- **NVIDIA Control Panel, global:**
  - Low Latency Mode Off
  - Max Frame Rate Off
  - Vertical sync "Use the 3D application setting"
  - Monitor Technology G-SYNC Compatible
  - Power management mode Normal
  - Triple buffering Off; Vulkan/OpenGL present method Auto
- **G-SYNC:** enabled for full screen mode only.
- **Program profiles:** none for `java.exe` in Reflex 1, so the global settings apply. Not re-checked this time.
- **Monitor:** 2560x1440 at 240 Hz (Windows reports 239, at most 240), 10 bpc, DisplayPort.

## Method

**Update.**
- `git fetch` and `checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh` (`dfdfd28`). The
  branch had been restarted from main, with Reflex 1's report already in it.
- `create-galore-bridge-1.105.0.jar` removed; `create-galore-bridge-1.107.0.jar` and the test kit jar (55,064 B)
  copied in.
- `nvngx_dlss.dll` 310.9.16.0 checked.

**Settings, and what they were before.** All are back at the end, checked byte for byte against copies taken before
the session.
- `create_galore_bridge-display.properties`:
  - `dlaa=true` → `dlaa=dlss`;
  - `perfectPresentation=false` → `true` (L3), `false` (L4).
  - The 1.107.0 Bridge rewrote this file with comments and added `reflex=off`. It was put back as it was.
- `config\DistantHorizons.toml`: `numberOfThreads` 32 → 2.
- `config\fml.toml`: `earlyWindowControl = false` was already so.
- Not touched, and unchanged after each launch: `options.txt`, and Sodium's options (render-ahead 3).
  - The by-feel part's Unlimited/vsync-off change wasn't needed, since that part didn't run.
- The kit's config didn't exist. The kit wrote its defaults, and it was deleted again at the end.

**Launches.** As in step 4, with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test`:

| Launch | Perfect Presentation | `runs=` | Run | Time |
| --- | --- | --- | --- | --- |
| 1, first try | on | `L3` | `20261025-234304-L3`: 20 plays, 34,260 frames | 23:43:04–23:49:51 |
| 1 (used) | on | `L3` | `20261025-235342-L3`: 20 plays, 34,260 frames | 23:53:42–00:00:27 |
| 2 | off | `L4` | `20261026-000237-L4`: 16 plays, 27,540 frames | 00:02:37–00:07:08 |

**What was open.**
- No game server, and no other game or launcher.
- Still running in the background: the Claude app, FanControl, the NVIDIA overlay, the PresentMon service, Razer's
  elevation service, and a Settings window.

**What didn't go to plan:**
1. **Launch 1 ran twice.**
   - After the first L3, the frame rate looked too low and the pacer had waited 0 everywhere. I took a screenshot of
     the plain wallpaper for the lock screen; it was the player's desktop with the taskbar hidden.
   - So the run was repeated with the player at the PC and the game window clicked into.
   - The repeat matched the first run within 0.5 ms and a few fps in every mode, so the low frame rate and the idle
     pacer are real, not from focus or a lock screen.
   - The used numbers are the repeat's. The first try's are in [latency-L3-first-try.txt](latency-L3-first-try.txt).
2. **The game's log rolled over at midnight in the middle of the L3 repeat.** The full log was put back together from
   the archived part for the line counts above.
3. **The by-feel part didn't run.** The player: "let's skip the by eye for now until it's all working".

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261025-234304-L3`, `20261025-235342-L3` and
  `20261026-000237-L4`. They hold `manifest.json` only.
- The game logs, this session's notes, the settings copies, and the display settings file as 1.107.0 left it:
  `$T\pacer1\`.
