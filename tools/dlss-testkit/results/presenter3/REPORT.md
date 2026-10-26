# Presenter 3 on the RTX 4080 SUPER: DirectX 11 against DirectX 12 without capture tools

PC session on 2026-10-26, 13:47–14:15, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`). It followed "Presenter 3" in `tools/dlss-testkit/PC-RUNBOOK.md` at `7a2868fd`, with Bridge 1.114.0 (test build)
and the test kit from Presenter 2 (nothing to update).
- Four launches with `runs=P2`, alternating `presenter=dx11` and `presenter=dx12`, with no PresentMon and no nvidia-smi.
  Then, at Emre's suggestion, a fifth launch on DirectX 11 (see Method: Wallpaper Engine).
- DLAA at full resolution (`dlaa=dlss`, preset K), at 2560x1440.
- No crash. This is a test, not a fix: no code was changed.

## Summary

- **Is DirectX 11 back at about 170 / 155 fps? No.** Without any capture tool it ran `lat-base` at 157–162 / 140–141
  fps (shore / canopy) and `lat-reflex` at 162–165 / 140–143. That's the same as in Presenter 2 (160 / 140 and
  165 / 142), in all three DirectX 11 launches, with Wallpaper Engine's live wallpaper on (launch 1) and paused
  (launches 3 and 5).
- **Is DirectX 12 still at about 152 / 138? Yes.** `lat-base` 152.7 / 137.4 and 157.4 / 137.8 fps, `lat-reflex`
  158.8 / 139.0 and 156.2 / 138.7.
- **So the real gap is about 2–4%, not about 10%.** With the two launches of each presenter averaged, DirectX 12 is
  3.0% / 2.5% behind at `lat-base` and 3.4% / 1.7% at `lat-reflex`. That's 0.12–0.22 ms more per frame.
- **The capture tools didn't cost DirectX 11 any frames.** It ran the same without them. So the fifth launch the
  runbook asks for (DirectX 11 with nvidia-smi only) wasn't needed: its condition, DirectX 11 at least 5% faster than
  in Presenter 2, wasn't met.
- **Latency: DirectX 12 is 0.7–0.9 ms lower** than DirectX 11 at `lat-base` and `lat-reflex` in every launch. That
  matches Presenter 2. Its frames wait about 1 ms less in the GPU queue (3.5–4.2 / 5.1–5.2 ms against 4.5–5.2 / 6.1–6.2).
- **Reflex's report says `G-SYNC fullscreen` on both presenters**, in all five launches.
- **No `Perfect Presentation`, `Latency:` or `Reflex:` WARN or ERROR lines**, apart from the usual one-off
  `swap chain wait timed out` while the world loads.
- **Still open: why DirectX 11 runs 6–9% below its numbers up to Presenter 1** (170–171 / 154–156 fps at `lat-base`).
  This session rules out the capture tools, the live wallpaper and the PC's restart (Presenter 2 ran before it,
  Presenter 3 after, with the same result). Between Presenter 1 (11:48) and Presenter 2 (13:07) the build changed:
  Bridge 1.113.0 → 1.114.0 and the new test kit. Emre also played the test world on DirectX 12 for five minutes at the
  end of Presenter 1. See "Next step".

**In one line:** without any capture tool, DirectX 12 is 2–4% behind DirectX 11 today, with 0.8 ms less latency. But
DirectX 11 itself runs 6–9% below where it ran up to Presenter 1, and the tools aren't the reason.

## The gap

The kit's fps: the median of both plays per launch, then the mean of the two launches of each presenter. ms per frame
is 1000 / fps.

**The runbook's four launches** (DirectX 11: launches 1 and 3; DirectX 12: launches 2 and 4):

| mode | viewpoint | DirectX 11 fps (L1, L3) | DirectX 12 fps (L2, L4) | fps | ms per frame, DX11 → DX12 | latency ms, DX11 → DX12 |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | 159.8 (157.2, 162.4) | 155.0 (152.7, 157.4) | −3.0% | 6.26 → 6.45 (+0.19) | 12.7 → 12.0 (−0.8) |
| `lat-base` | canopy | 141.2 (141.2, 141.1) | 137.6 (137.4, 137.8) | −2.5% | 7.08 → 7.27 (+0.18) | 15.4 → 14.6 (−0.8) |
| `lat-reflex` | shore | 163.1 (163.9, 162.4) | 157.5 (158.8, 156.2) | −3.4% | 6.13 → 6.35 (+0.22) | 11.2 → 10.3 (−0.9) |
| `lat-reflex` | canopy | 141.2 (140.0, 142.5) | 138.8 (139.0, 138.7) | −1.7% | 7.08 → 7.20 (+0.12) | 13.2 → 12.3 (−0.9) |

**With the wallpaper paused only** (DirectX 11: launches 3 and 5, against the same DirectX 12 launches):

| mode | viewpoint | DirectX 11 fps (L3, L5) | DirectX 12 fps (L2, L4) | fps | ms per frame, DX11 → DX12 | latency ms, DX11 → DX12 |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | 161.2 (162.4, 160.1) | 155.0 | −3.9% | 6.20 → 6.45 (+0.25) | 12.7 → 12.0 (−0.8) |
| `lat-base` | canopy | 140.5 (141.1, 139.9) | 137.6 | −2.1% | 7.12 → 7.27 (+0.15) | 15.3 → 14.6 (−0.7) |
| `lat-reflex` | shore | 163.4 (162.4, 164.5) | 157.5 | −3.6% | 6.12 → 6.35 (+0.23) | 11.2 → 10.3 (−0.9) |
| `lat-reflex` | canopy | 142.1 (142.5, 141.7) | 138.8 | −2.3% | 7.04 → 7.20 (+0.17) | 13.2 → 12.3 (−0.9) |

**Against the earlier sessions** (kit fps at `lat-base`, shore / canopy):

| Session | Bridge | Capture tools | DirectX 11 | DirectX 12 |
| --- | --- | --- | --- | --- |
| Reflex 1, Pacer 1, Pacer 2 | 1.105.0–1.110.0 | none | 170–171 / 154–156 | – |
| Presenter 1 | 1.113.0 | none | 170 / 155 | 151 / 138 |
| Presenter 2 | 1.114.0 | PresentMon, nvidia-smi | 160 / 140 | 152.5–155.5 / 136.5–138 |
| Presenter 3 | 1.114.0 | none | 157–162 / 140–141 | 153–157 / 137–138 |

## Each mode, viewpoint and launch

From `latency.py` (input to GPU end). The plays are in run order, so the first value in brackets is the first play.

| mode | viewpoint | launch | fps: median (plays) | latency ms: median (plays) | GPU queue ms (plays) |
| --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | 1, DX11 | 157.2 (149, 165) | 12.6 (11.8, 13.5) | 4.5, 5.2 |
| `lat-base` | shore | 2, DX12 | 152.7 (144, 161) | 11.8 (11.0, 12.7) | 3.5, 4.2 |
| `lat-base` | shore | 3, DX11 | 162.4 (159, 166) | 12.8 (12.2, 13.5) | 5.1, 5.2 |
| `lat-base` | shore | 4, DX12 | 157.4 (154, 161) | 12.1 (11.4, 12.8) | 3.9, 4.2 |
| `lat-base` | shore | 5, DX11 | 160.1 (156, 165) | 12.6 (11.9, 13.3) | 5.0, 5.2 |
| `lat-base` | canopy | 1, DX11 | 141.2 (141, 142) | 15.5 (14.8, 16.1) | 6.2, 6.2 |
| `lat-base` | canopy | 2, DX12 | 137.4 (137, 138) | 14.5 (13.7, 15.3) | 5.1, 5.2 |
| `lat-base` | canopy | 3, DX11 | 141.1 (140, 142) | 15.4 (14.8, 16.0) | 6.2, 6.2 |
| `lat-base` | canopy | 4, DX12 | 137.8 (137, 139) | 14.7 (14.0, 15.3) | 5.1, 5.2 |
| `lat-base` | canopy | 5, DX11 | 139.9 (138, 142) | 15.1 (14.2, 16.0) | 6.1, 6.2 |
| `lat-reflex` | shore | 1, DX11 | 163.9 (162, 165) | 11.2 (11.2, 11.2) | 5.2, 5.2 |
| `lat-reflex` | shore | 2, DX12 | 158.8 (157, 161) | 10.3 (10.3, 10.3) | 4.1, 4.2 |
| `lat-reflex` | shore | 3, DX11 | 162.4 (159, 166) | 11.2 (11.2, 11.2) | 5.2, 5.2 |
| `lat-reflex` | shore | 4, DX12 | 156.2 (151, 161) | 10.4 (10.3, 10.4) | 4.1, 4.2 |
| `lat-reflex` | shore | 5, DX11 | 164.5 (164, 165) | 11.2 (11.2, 11.2) | 5.2, 5.2 |
| `lat-reflex` | canopy | 1, DX11 | 140.0 (138, 142) | 13.2 (13.2, 13.2) | 6.2, 6.2 |
| `lat-reflex` | canopy | 2, DX12 | 139.0 (139, 139) | 12.3 (12.3, 12.3) | 5.2, 5.2 |
| `lat-reflex` | canopy | 3, DX11 | 142.5 (142, 143) | 13.2 (13.2, 13.2) | 6.2, 6.2 |
| `lat-reflex` | canopy | 4, DX12 | 138.7 (138, 139) | 12.3 (12.3, 12.3) | 5.2, 5.2 |
| `lat-reflex` | canopy | 5, DX11 | 141.7 (142, 142) | 13.2 (13.2, 13.2) | 6.2, 6.2 |
| `lat-cap-default` (60 fps) | shore | 1, DX11 | 60 | 9.0 (9.5, 8.6) | −8.1, −8.6 |
| `lat-cap-default` (60 fps) | shore | 2, DX12 | 60 | **13.6 (14.0, 13.3)** | −3.7, −3.7 |
| `lat-cap-default` (60 fps) | shore | 3, DX11 | 60 | 9.1 (9.5, 8.7) | −7.9, −8.3 |
| `lat-cap-default` (60 fps) | shore | 4, DX12 | 60 | 9.0 (9.2, 8.8) | −8.1, −8.2 |
| `lat-cap-default` (60 fps) | shore | 5, DX11 | 60 | 9.2 (9.5, 9.0) | −7.4, −7.9 |
| `lat-cap-default` (60 fps) | canopy | 1, DX11 | 60 | 9.7 (9.8, 9.6) | −7.5, −7.7 |
| `lat-cap-default` (60 fps) | canopy | 2, DX12 | 60 | **14.6 (14.9, 14.3)** | −2.5, −2.7 |
| `lat-cap-default` (60 fps) | canopy | 3, DX11 | 60 | 9.5 (9.9, 9.1) | −7.5, −7.9 |
| `lat-cap-default` (60 fps) | canopy | 4, DX12 | 60 | 9.3 (9.4, 9.3) | −7.7, −7.8 |
| `lat-cap-default` (60 fps) | canopy | 5, DX11 | 60 | 9.9 (10.2, 9.6) | −6.8, −7.3 |

- **`lat-base`'s first play runs lower than its second in every launch** (144–159 fps at shore against 161–166). It's
  the run's first stretch, as in every earlier session. Both presenters have it.
- **Launch 2's `lat-cap-default` had 4–5 ms more latency** (13.6 / 14.6 ms, the GPU queue −3.7 / −2.5 ms instead of
  about −8) in both plays, 13:55:19–13:55:49 and 13:56:16–13:56:45. Launch 4, the same settings, was normal (9.0 /
  9.3 ms), as was every DirectX 12 capped play in Presenter 2. It came right after Emre paused Wallpaper Engine
  (about 13:54). Its cause is unknown and it didn't come back, so it's a one-off here. No capture tool was running to
  say more.

## Log lines

- **`Perfect Presentation:`** `DXGI swap chain ready on DirectX 11 (OpenGL on a hidden context)` in launches 1, 3 and
  5; `DirectX 12 on NVIDIA GeForce RTX 4080 SUPER (OpenGL node mask 1, tearing allowed, queue priority normal,
  GL_NVX_progress_fence available)` and `DXGI swap chain ready on DirectX 12 (OpenGL on a hidden context)` in
  launches 2 and 4. Each launch matches its presenter. No `DirectX 12 could not start`.
- **One WARN in each launch:** `Perfect Presentation: swap chain wait timed out`, while the world loaded, before the
  run, as in every Perfect Presentation launch since Reflex 1. The Bridge's lag watch logged its usual one freeze at
  world load per launch (11.6–12.1 s), and none during a run.
- **`Reflex:`** `started on Perfect Presentation's DirectX 11 device (NVIDIA NVAPI)` (launches 1, 3, 5) or
  `... DirectX 12 device` (2, 4). When switching on, in all five: `Reflex: low latency on, boost off, frame cap none;
  driver low latency on, last sleep 0 us, game Sleep not used, G-SYNC fullscreen` (`driver low latency off` at the first
  switch). The reports over the stretches also end in `G-SYNC fullscreen`.
- **No `Perfect Presentation`, `Latency:` or `Reflex:` WARN or ERROR lines** apart from the swap chain timeout.

## Next step: why DirectX 11 is slower than up to Presenter 1

- Up to Presenter 1, DirectX 11 ran `lat-base` at 170–171 / 154–156 fps in four sessions. Since Presenter 2 it runs at
  157–162 / 140–142, with or without capture tools, with or without the live wallpaper, and before and after the PC's
  restart. DirectX 12 hasn't moved (151 / 138 in Presenter 1, 153–157 / 137–138 now).
- What changed between Presenter 1 and Presenter 2:
  - the build: Bridge 1.113.0 → 1.114.0, and the test kit (55,328 → 56,152 B). 1.114.0's commit doesn't touch the
    DirectX 11 presenter, but it does change the display settings and the code around DirectX 12;
  - the test world: Emre played it on DirectX 12 for about five minutes at the end of Presenter 1, and two region files
    (`r.1.1.mca`, `r.2.1.mca`) were last written then, at 12:10. No new region file was made.
- One DirectX 11 launch with the 1.113.0 jar and the old test kit (both in the git history at `31bbd040`), on this
  same world, would tell them apart:
  - back at about 170 / 155: 1.114.0 or the new kit slowed DirectX 11, and against 1.113.0's DirectX 11 the gap is
    still about 10%;
  - still at about 160 / 141: the world or the PC changed, and today's 2–4% is the real gap.
- That isn't in the runbook, so it wasn't run.

## Method

**Update.** None, as the runbook says: the repo is at `7a2868fd` (after `4f0d9385`), and the instance has
`create-galore-bridge-1.114.0.jar` (1,390,369 B) and the test kit jar (56,152 B).

**Settings, and what they were before.** All are back at the end, checked byte for byte against copies taken before
the first launch.
- `create_galore_bridge-display.properties`: `dlaa=true` → `dlaa=dlss`; `perfectPresentation=false` → `true`;
  `presenter=dx11` or `presenter=dx12` added, switched before each launch. No other test keys. 1.114.0 rewrites the
  file with its comments and kept `presenter`. The file was put back as it was, so `presenter` is gone.
- `config\DistantHorizons.toml`: `numberOfThreads` 32 → 2.
- `config\fml.toml`: `earlyWindowControl = false` was already so.
- `options.txt` and Sodium's options: not touched, and unchanged.
- The kit's config didn't exist. The kit wrote its defaults, and it was deleted again at the end.

**Launches.** As in step 4, with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and
`--jvm-arg=-Dcreate_galore_dlss_testkit.runs=P2`, through the profiling launch script without its PresentMon option
(it then only launches the game):

| Launch | Presenter | Wallpaper Engine | Run | Time |
| --- | --- | --- | --- | --- |
| 1 | `dx11` | live | `20261026-135053-P2`: 12 plays, 10,740 frames | 13:50:53–13:52:50 |
| 2 | `dx12` | paused from about the run's start | `20261026-135446-P2` | 13:54:46–13:56:45 |
| 3 | `dx11` | paused | `20261026-135855-P2` | 13:58:55–14:00:52 |
| 4 | `dx12` | paused | `20261026-140256-P2` | 14:02:56–14:04:54 |
| 5 (extra, Emre) | `dx11` | paused | `20261026-140704-P2` | 14:07:04–14:09:01 |

P2 plays `lat-base`, `lat-reflex` and `lat-cap-default`, each at shore and then canopy, and the whole list twice.

**What was running.**
- Before every launch: no `PresentMon*` process apart from the PresentMon service (in Presenter 2's list too), no
  `nvidia-smi`, no other Java. Total CPU 1.9–4.2%. Before launches 3 to 5, no process used more than 2% of the GPU's
  3D engine.
- `logman query -ets` lists no trace sessions at all without administrator rights, so it can't show whether one is
  left. No PresentMon has run since Presenter 2's last one stopped at its time limit, and the PC restarted since.
- The rest, as in Presenter 2: the Claude app, FanControl, the NVIDIA overlay, NZXT CAM, PowerToys, Razer's app engine
  and elevation service, the Windows Package Manager server, and a Settings window. No game server, no other game or
  launcher.

**What didn't go to plan:**
1. **The PC had restarted** between Presenter 2 and this session (shut down 13:41:51, up again 13:44:50). Two programs
   came back with it:
   - **The ChatGPT/Codex sandbox service** (`codex-windows-sandbox-service`, 13 MB), which wasn't in Presenter 2's
     list. I saw it before launch 1. Emre said it shouldn't be on and force-closed it during launch 1's loading, before
     its run (gone by 13:49:42). Its Windows service is still set to start automatically.
   - **Wallpaper Engine** (`wallpaper64`, five `webwallpaper64`, `wallpaperservice32`). My process check in Presenter
     2 and before launch 1 didn't catch it. Emre said its live wallpaper had not been paused "in these last tests"
     (Presenter 2 and launch 1 here), and paused it at about 13:54. Its processes kept running, but used no
     measurable GPU at any check from then on.
2. **So a fifth launch was added**: DirectX 11 with the wallpaper paused, at Emre's suggestion, to have two DirectX 11
   launches without it. DirectX 11 ran the same with and without the wallpaper (launch 1 against 3 and 5), so the
   runbook's four launches stand as they are, and the gap is shown both ways above.
3. **Launch 2's capped mode** had 4–5 ms more latency than launch 4's (see above), right after the wallpaper was paused.

No crash, and no new crash log. The only `hs_err` file in the instance is the old one from Upscaling 1.

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261026-135053-P2`, `20261026-135446-P2`, `20261026-135855-P2`,
  `20261026-140256-P2` and `20261026-140704-P2`.
- In `$T\presenter3\`: the game logs `L1-latest.log` to `L5-latest.log`, `latency.md` (all five runs), `tables.md`, this
  session's notes, the settings copies, the display settings file as 1.114.0 left it and the kit config it wrote.
