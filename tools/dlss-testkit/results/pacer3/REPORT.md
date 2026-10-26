# Pacer 3 on the RTX 4080 SUPER: GPU pacing on DirectX 12

PC session on 2026-10-26, 16:47–17:10, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`). It followed "Pacer 3" in `tools/dlss-testkit/PC-RUNBOOK.md` at `93149df9`, with Bridge 1.116.0 (test build,
the HUD split off) and the test kit from that commit, right after HUD split 1 (`results/hud-split1/`).
- Four launches with `runs=L7`, alternating `presenter=dx11` and `presenter=dx12`, with no PresentMon and no
  nvidia-smi. Launch 1 had to be started twice, because the game window didn't cover the screen (see Method).
- DLAA at full resolution (`dlaa=dlss`, preset K), at 2560x1440.
- No crash. This is a test, not a fix: no code was changed.

## Summary

The fps changes below are against the same launch's `lat-base`, with the two launches of each presenter averaged.
`lat-base`'s first play, the run's first stretch, runs low in every launch (153–166 fps at shore against 170–176 in its
second play), most on DirectX 12. So the cross-check columns compare with `lat-reflex` and with `lat-base`'s second
play, which don't have that dip. They're the steadier yardstick and are given in brackets.

- **On, on DirectX 12: yes, it keeps the frame rate:** +4.2% / +0.4% (shore / canopy). Against the steady yardstick it's
  +1.0% / −0.1%. It cuts the latency from 11.3 / 13.8 to **9.6 / 10.8 ms**. On DirectX 11: +2.7% / +0.8% (0.0% /
  −0.7%), 12.0 / 14.4 → 9.7 / 10.6 ms.
  - But on DirectX 12, **On's pacer hardly waits**: 0.00 ms at shore, 0.22 ms at canopy, against 0.8 / 1.3 ms on DirectX 11.
    The frames already queue less than its target (3.8 / 4.3 ms queue against a 4.0 / 4.26 ms target). So on DirectX
    12 On is mostly the late input read.
- **The shortest target that keeps the frame rate:**
  - **DirectX 11:** about On's own (4000–4170 µs). 3000 µs costs 2.0% / 2.0% against `lat-base` and 4.6% / 3.5%
    against the steady yardstick.
  - **DirectX 12:** 3000 µs or shorter. 3000 µs keeps the frame rate (+3.1% / −0.1%; −0.1% / −0.6% steady) at 8.9 /
    9.7 ms.
  - So DirectX 12's shortest frame-keeping target is about 1 ms shorter than DirectX 11's, which is more than the
    runbook's 0.5 ms. Only 3000, 4000 and 5000 were measured, so how far below 3000 µs DirectX 12 still keeps the frame
    rate isn't known.
- **Lowest:** it costs much less on DirectX 12.
  - Fps: −1.2% / −3.3% (−4.3% / −3.8% steady), against −7.4% / −6.3% (−9.9% / −7.7%) on DirectX 11.
  - Latency: 8.4 / 8.9 ms on both.
  - Target swing: more on DirectX 12, 1708–2565 / 1717–2312 µs, against 1884–2132 / 1661–2205 µs on DirectX 11. That's
    much less than Presenter 1 saw on 1.113.0 (1.8–4.0 ms).
- **Reflex: about the same on both in frame rate** (+3.2% / +0.5% on DirectX 12, +2.7% / +1.6% on DirectX 11). Its
  latency is about 0.9 ms lower on DirectX 12: 9.6 / 11.0 against 10.5 / 11.9 ms.
- **The queue at `lat-base`: still about 1 ms shorter on DirectX 12**. Median 3.5–3.6 / 4.5 ms against 4.7–4.8 /
  5.5–5.6, and q10 2.8–2.9 / 3.9–4.0 against 3.9–4.0 / 4.7–4.9.
- **Frame rate, DirectX 12 against DirectX 11:** about 2–4% fewer frames. `lat-reflex` gets 168.6 / 152.3 against
  175.1 / 156.4 fps, and `lat-base`'s second plays 170.5 / 153 against 176 / 156.5. Presenter 3's cleaned world found
  0–3%.
- **The log** has every line the runbook asks for, on both presenters. Reflex says `G-SYNC fullscreen` on both. The
  only WARN is the usual `swap chain wait timed out` while the world loads.

**What comes of it** (the runbook's rule): DirectX 12's shortest frame-keeping target differs from DirectX 11's by about
1 ms, more than 0.5 ms. So by the rule, **On gets its own rule on DirectX 12** (`GpuPacer.keepingFrameRate` per
presenter). On DirectX 12, 3 ms (or a smaller share of the frame time) keeps the frame rate, where On's 4 ms doesn't
pace at all at this frame rate. **Lowest's cost isn't higher on DirectX 12** (it's lower), so its margin needn't change.

## Each mode, viewpoint and launch

From the run manifests (`latency.py` shows the same). fps and latency are medians over both plays, with the plays in
brackets; "vs lat-base" is against that launch's `lat-base`. The queue is the median and q10. With the pacer: its
target per play, its median wait, and how late the wait ended (median).

| mode | viewpoint | launch | fps (plays) | vs lat-base | latency ms median (plays) / p90 | queue / q10 ms | pacer target us (plays) | wait ms | late us |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | dx11 L1 | 170.1 (164, 176) | – | 12.0 (11.2, 12.9) / 12.7 | 4.7 / 3.9 | – | – | – |
| `lat-base` | shore | dx12 L2 | 162.1 (153, 171) | – | 11.2 (10.3, 12.0) / 12.0 | 3.5 / 2.9 | – | – | – |
| `lat-base` | shore | dx11 L3 | 170.9 (166, 176) | – | 12.0 (11.2, 12.8) / 12.7 | 4.8 / 4.0 | – | – | – |
| `lat-base` | shore | dx12 L4 | 164.5 (159, 170) | – | 11.3 (10.6, 12.1) / 12.1 | 3.6 / 2.8 | – | – | – |
| `lat-base` | canopy | dx11 L1 | 153.0 (150, 156) | – | 14.3 (13.3, 15.2) / 15.0 | 5.5 / 4.7 | – | – | – |
| `lat-base` | canopy | dx12 L2 | 151.4 (150, 153) | – | 13.7 (13.1, 14.4) / 14.3 | 4.5 / 3.9 | – | – | – |
| `lat-base` | canopy | dx11 L3 | 154.8 (153, 157) | – | 14.5 (13.9, 15.1) / 15.1 | 5.6 / 4.9 | – | – | – |
| `lat-base` | canopy | dx12 L4 | 151.7 (151, 153) | – | 13.9 (13.2, 14.5) / 14.4 | 4.5 / 4.0 | – | – | – |
| `lat-pacing-on` | shore | dx11 L1 | 174.8 (174, 176) | +2.7% | 9.8 (9.8, 9.7) / 9.9 | 4.1 / 3.7 | 4000, 4000 | 0.77 | 0 |
| `lat-pacing-on` | shore | dx12 L2 | 170.2 (170, 171) | +5.0% | 9.6 (9.6, 9.6) / 9.7 | 3.8 / 3.6 | 4000, 4000 | 0.00 | 0 |
| `lat-pacing-on` | shore | dx11 L3 | 175.4 (175, 176) | +2.7% | 9.7 (9.7, 9.7) / 9.9 | 4.1 / 3.8 | 4000, 4000 | 0.79 | 0 |
| `lat-pacing-on` | shore | dx12 L4 | 170.2 (170, 170) | +3.5% | 9.6 (9.6, 9.6) / 9.7 | 3.8 / 3.5 | 4000, 4000 | 0.00 | 0 |
| `lat-pacing-on` | canopy | dx11 L1 | 154.7 (155, 154) | +1.1% | 10.7 (10.6, 10.7) / 10.8 | 4.2 / 3.9 | 4154, 4169 | 1.33 | 0 |
| `lat-pacing-on` | canopy | dx12 L2 | 152.4 (152, 152) | +0.6% | 10.8 (10.8, 10.8) / 10.9 | 4.3 / 4.2 | 4259, 4256 | 0.23 | 0 |
| `lat-pacing-on` | canopy | dx11 L3 | 155.6 (155, 156) | +0.5% | 10.6 (10.6, 10.6) / 10.7 | 4.2 / 4.0 | 4152, 4146 | 1.34 | 0 |
| `lat-pacing-on` | canopy | dx12 L4 | 151.9 (152, 152) | +0.1% | 10.9 (10.8, 10.9) / 11.0 | 4.3 / 4.2 | 4265, 4266 | 0.22 | 0 |
| `lat-pacer` | shore | dx11 L1 | 159.0 (158, 160) | -6.6% | 8.4 (8.6, 8.2) / 9.2 | 2.3 / 1.1 | 2132, 1961 | 2.00 | 0 |
| `lat-pacer` | shore | dx12 L2 | 161.6 (160, 164) | -0.3% | 8.4 (8.6, 8.1) / 9.0 | 2.4 / 1.2 | 2398, 2307 | 1.27 | 0 |
| `lat-pacer` | shore | dx11 L3 | 156.7 (156, 158) | -8.3% | 8.4 (8.5, 8.3) / 8.9 | 2.2 / 0.9 | 2022, 1884 | 2.04 | 0 |
| `lat-pacer` | shore | dx12 L4 | 161.2 (159, 163) | -2.0% | 8.4 (8.6, 8.2) / 9.2 | 2.4 / 1.3 | 2565, 1708 | 1.19 | 0 |
| `lat-pacer` | canopy | dx11 L1 | 144.5 (143, 146) | -5.6% | 8.9 (9.1, 8.8) / 9.6 | 2.2 / 0.9 | 1702, 1661 | 2.79 | 0 |
| `lat-pacer` | canopy | dx12 L2 | 146.6 (145, 148) | -3.2% | 8.8 (9.0, 8.7) / 9.4 | 2.2 / 1.1 | 1743, 2064 | 2.17 | 0 |
| `lat-pacer` | canopy | dx11 L3 | 144.0 (142, 146) | -7.0% | 8.8 (8.9, 8.7) / 9.5 | 2.0 / 0.9 | 2205, 1714 | 2.87 | 0 |
| `lat-pacer` | canopy | dx12 L4 | 146.3 (145, 147) | -3.5% | 8.9 (9.0, 8.8) / 9.5 | 2.2 / 1.1 | 2312, 1717 | 2.06 | 0 |
| `lat-pacer-3000` | shore | dx11 L1 | 167.0 (169, 165) | -1.9% | 8.9 (8.9, 8.9) / 9.3 | 3.2 / 2.0 | 3000, 3000 | 1.50 | 0 |
| `lat-pacer-3000` | shore | dx12 L2 | 168.8 (169, 169) | +4.1% | 8.9 (8.9, 8.9) / 9.2 | 3.1 / 2.5 | 3000, 3000 | 0.60 | 0 |
| `lat-pacer-3000` | shore | dx11 L3 | 167.2 (169, 166) | -2.1% | 8.9 (9.0, 8.9) / 9.4 | 3.2 / 2.0 | 3000, 3000 | 1.48 | 0 |
| `lat-pacer-3000` | shore | dx12 L4 | 168.0 (169, 167) | +2.1% | 9.0 (9.0, 8.9) / 9.3 | 3.1 / 2.4 | 3000, 3000 | 0.60 | 0 |
| `lat-pacer-3000` | canopy | dx11 L1 | 150.4 (151, 150) | -1.7% | 9.7 (9.7, 9.7) / 10.1 | 3.2 / 2.0 | 3000, 3000 | 2.21 | 0 |
| `lat-pacer-3000` | canopy | dx12 L2 | 151.3 (151, 152) | -0.1% | 9.6 (9.7, 9.6) / 9.9 | 3.1 / 2.6 | 3000, 3000 | 1.33 | 0 |
| `lat-pacer-3000` | canopy | dx11 L3 | 151.2 (152, 150) | -2.4% | 9.6 (9.6, 9.6) / 10.1 | 3.2 / 2.0 | 3000, 3000 | 2.25 | 0 |
| `lat-pacer-3000` | canopy | dx12 L4 | 151.4 (151, 152) | -0.2% | 9.7 (9.7, 9.7) / 9.9 | 3.1 / 2.7 | 3000, 3000 | 1.34 | 0 |
| `lat-pacer-5000` | shore | dx11 L1 | 173.5 (171, 176) | +2.0% | 10.5 (10.5, 10.5) / 10.6 | 4.9 / 4.5 | 5000, 5000 | 0.00 | 0 |
| `lat-pacer-5000` | shore | dx12 L2 | 171.1 (171, 172) | +5.5% | 9.6 (9.6, 9.6) / 9.7 | 3.8 / 3.6 | 5000, 5000 | 0.00 | 0 |
| `lat-pacer-5000` | shore | dx11 L3 | 173.6 (171, 176) | +1.6% | 10.5 (10.5, 10.5) / 10.6 | 4.9 / 4.6 | 5000, 5000 | 0.00 | 0 |
| `lat-pacer-5000` | shore | dx12 L4 | 170.3 (170, 171) | +3.5% | 9.6 (9.6, 9.6) / 9.7 | 3.8 / 3.4 | 5000, 5000 | 0.00 | 0 |
| `lat-pacer-5000` | canopy | dx11 L1 | 153.6 (152, 155) | +0.4% | 11.4 (11.4, 11.4) / 11.6 | 5.0 / 4.7 | 5000, 5000 | 0.54 | 0 |
| `lat-pacer-5000` | canopy | dx12 L2 | 152.5 (152, 153) | +0.7% | 11.0 (11.0, 11.0) / 11.1 | 4.5 / 4.4 | 5000, 5000 | 0.00 | 0 |
| `lat-pacer-5000` | canopy | dx11 L3 | 153.6 (151, 156) | -0.8% | 11.4 (11.4, 11.4) / 11.5 | 5.0 / 4.2 | 5000, 5000 | 0.53 | 0 |
| `lat-pacer-5000` | canopy | dx12 L4 | 151.4 (150, 152) | -0.1% | 11.0 (11.0, 11.0) / 11.1 | 4.5 / 4.4 | 5000, 5000 | 0.00 | 0 |
| `lat-reflex` | shore | dx11 L1 | 175.0 (173, 176) | +2.8% | 10.5 (10.5, 10.5) / 10.6 | 4.8 / 4.4 | – | – | – |
| `lat-reflex` | shore | dx12 L2 | 169.0 (167, 171) | +4.3% | 9.5 (9.5, 9.6) / 9.7 | 3.7 / 3.3 | – | – | – |
| `lat-reflex` | shore | dx11 L3 | 175.2 (174, 176) | +2.5% | 10.5 (10.5, 10.5) / 10.6 | 4.8 / 4.4 | – | – | – |
| `lat-reflex` | shore | dx12 L4 | 168.2 (166, 170) | +2.2% | 9.6 (9.5, 9.6) / 9.8 | 3.7 / 3.1 | – | – | – |
| `lat-reflex` | canopy | dx11 L1 | 156.2 (156, 156) | +2.1% | 12.0 (11.9, 12.0) / 12.0 | 5.6 / 5.5 | – | – | – |
| `lat-reflex` | canopy | dx12 L2 | 152.4 (152, 153) | +0.6% | 11.0 (11.0, 11.0) / 11.1 | 4.5 / 4.3 | – | – | – |
| `lat-reflex` | canopy | dx11 L3 | 156.5 (156, 157) | +1.1% | 11.9 (11.9, 11.9) / 12.0 | 5.5 / 5.5 | – | – | – |
| `lat-reflex` | canopy | dx12 L4 | 152.2 (152, 152) | +0.3% | 11.1 (11.0, 11.1) / 11.1 | 4.5 / 4.4 | – | – | – |

## Per presenter, the two launches averaged

| mode | viewpoint | DX11 fps change | DX11 latency ms (vs lat-base) | DX12 fps change | DX12 latency ms (vs lat-base) | DX11 targets | DX12 targets |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | – | 12.0 (+0.0) | – | 11.3 (+0.0) | – | – |
| `lat-base` | canopy | – | 14.4 (+0.0) | – | 13.8 (+0.0) | – | – |
| `lat-pacing-on` | shore | +2.7% | 9.7 (-2.3) | +4.2% | 9.6 (-1.7) | 4000–4000 | 4000–4000 |
| `lat-pacing-on` | canopy | +0.8% | 10.6 (-3.8) | +0.4% | 10.8 (-2.9) | 4146–4169 | 4256–4266 |
| `lat-pacer` | shore | -7.4% | 8.4 (-3.6) | -1.2% | 8.4 (-2.9) | 1884–2132 | 1708–2565 |
| `lat-pacer` | canopy | -6.3% | 8.9 (-5.5) | -3.3% | 8.9 (-4.9) | 1661–2205 | 1717–2312 |
| `lat-pacer-3000` | shore | -2.0% | 8.9 (-3.1) | +3.1% | 8.9 (-2.3) | 3000–3000 | 3000–3000 |
| `lat-pacer-3000` | canopy | -2.0% | 9.6 (-4.8) | -0.1% | 9.7 (-4.1) | 3000–3000 | 3000–3000 |
| `lat-pacer-5000` | shore | +1.8% | 10.5 (-1.5) | +4.5% | 9.6 (-1.7) | 5000–5000 | 5000–5000 |
| `lat-pacer-5000` | canopy | -0.2% | 11.4 (-3.0) | +0.3% | 11.0 (-2.8) | 5000–5000 | 5000–5000 |
| `lat-reflex` | shore | +2.7% | 10.5 (-1.5) | +3.2% | 9.6 (-1.7) | – | – |
| `lat-reflex` | canopy | +1.6% | 11.9 (-2.5) | +0.5% | 11.0 (-2.8) | – | – |

**Cross-check against the steady yardstick:** each mode's fps against `lat-reflex` and against `lat-base`'s second play,
both without the first-play dip, the two launches averaged:

| mode | viewpoint | DX11 vs lat-reflex / vs lat-base 2nd play | DX12 vs lat-reflex / vs lat-base 2nd play |
| --- | --- | --- | --- |
| `lat-base` | shore | -2.6% / -3.1% | -3.1% / -4.2% |
| `lat-base` | canopy | -1.5% / -1.7% | -0.5% / -0.7% |
| `lat-pacing-on` | shore | +0.0% / -0.5% | +1.0% / -0.1% |
| `lat-pacing-on` | canopy | -0.7% / -0.9% | -0.1% / -0.3% |
| `lat-pacer` | shore | -9.9% / -10.3% | -4.3% / -5.3% |
| `lat-pacer` | canopy | -7.7% / -7.9% | -3.8% / -4.0% |
| `lat-pacer-3000` | shore | -4.6% / -5.0% | -0.1% / -1.2% |
| `lat-pacer-3000` | canopy | -3.5% / -3.7% | -0.6% / -0.8% |
| `lat-pacer-5000` | shore | -0.9% / -1.3% | +1.3% / +0.2% |
| `lat-pacer-5000` | canopy | -1.7% / -1.9% | -0.2% / -0.4% |
| `lat-reflex` | shore | +0.0% / -0.5% | +0.0% / -1.1% |
| `lat-reflex` | canopy | +0.0% / -0.2% | +0.0% / -0.2% |

- `lat-reflex` has no pacer and the same GPU throughput as `lat-base`, so it's a fair stand-in for `lat-base` without
  its first-play dip.
- On DirectX 11 only On and 5000 µs stay within about 2%. On DirectX 12, 3000 µs does too.

## Log lines

In all four launches:
- `Perfect Presentation: DXGI swap chain ready on DirectX 11 (OpenGL on a hidden context)` (launches 1 and 3) or
  `... on DirectX 12` (2 and 4), matching the launch. The manifest's `"presenter"` says `DirectX 11` or `DirectX 12` the
  same.
- `Latency: GPU pacer on, frames may queue on the GPU a share of the frame time that keeps the frame rate`, `... as much
  as they vary`, `... 3000 us on the GPU` and `... 5000 us on the GPU`; `GPU pacer off`; `input read again just before
  rendering on` / `off`; `the frame cap waits at the start of the frame, before input`; `waits use a high resolution
  timer`.
- `Reflex: started on Perfect Presentation's DirectX 11 device (NVIDIA NVAPI)` or `... DirectX 12 device`. When
  switching on: `Reflex: low latency on, boost off, frame cap none; driver low latency on, last sleep 0 us, game Sleep
  not used, G-SYNC fullscreen` (`driver low latency off` at the first switch). The reports over the stretches, on both
  presenters: `on (driver low latency on, last sleep 800–... us, game Sleep used, G-SYNC fullscreen)`.
- `DLAA test: cleared the test scenes` at the start of each run.
- **One WARN per launch**, `Perfect Presentation: swap chain wait timed out`, while the world loaded. No other
  `Perfect Presentation`, `Latency:` or `Reflex:` WARN or ERROR line.
- Launches 1 and 2 have one extra `... 5000 us on the GPU` line at start-up, from the display file the aborted first try
  left behind (see Method).

## The PC's settings (looked at only, nothing changed)

Read before launch 1, the same as in Presenter 3:
- **NVIDIA Control Panel, global:** Low Latency Mode Off, Max Frame Rate Off, Vertical sync "Use the 3D application
  setting", Monitor Technology G-SYNC Compatible, Power management mode Normal, Vulkan/OpenGL present method Auto.
- **G-SYNC:** enabled, for full screen mode only.
- **Monitor:** 2560x1440 (native) at 240 Hz (Windows reports 239, at most 240).

## Method

**Update.** Done once for both tests (HUD split 1 first), as the runbook's Pacer 3 Update says:
- `93149df9`;
- `create-galore-bridge-1.116.0.jar` (1,408,236 B) and the test kit jar (55,751 B);
- no `present...` test keys in the display settings.

**Settings, and what they were before.** All back at the end, checked byte for byte against copies taken first.
- `create_galore_bridge-display.properties`: `dlaa=true` → `dlaa=dlss`; `perfectPresentation=false` → `true`; added
  `gpuPacing=off` and `presenter=dx11` or `dx12`, set before each launch. No other test keys.
- `config\DistantHorizons.toml`: `numberOfThreads` 32 → 2.
- `config\fml.toml`: `earlyWindowControl = false` was already so.
- `options.txt` and Sodium's options: as before. HUD split 1's frame cap change had been undone before Pacer 3.
- The kit's config was deleted at the end.

**Launches.** As in step 4, with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and
`--jvm-arg=-Dcreate_galore_dlss_testkit.runs=L7`:

| Launch | `presenter` | Run | Window | Time |
| --- | --- | --- | --- | --- |
| 1, first try | `dx11` | `20261026-164920-L7`, stopped | client at (8, 31): title bar on screen | 16:47:47–16:51:36 |
| 1 | `dx11` | `20261026-165346-L7`: 24 plays, 20,860 frames | right from the start | 16:53:46–16:56:17 |
| 2 | `dx12` | `20261026-165837-L7` | moved into place at 16:57:36 | 16:58:37–17:01:08 |
| 3 | `dx11` | `20261026-170313-L7` | right from the start | 17:03:14–17:05:44 |
| 4 | `dx12` | `20261026-170736-L7` | moved into place at 17:06:36 (it was briefly 2560x1421) | 17:07:36–17:10:08 |

- L7 plays its modes in the order `lat-base`, `lat-reflex`, `lat-pacer`, `lat-pacer-3000`, `lat-pacer-5000`,
  `lat-pacing-on`, each at shore and then canopy, and the whole list twice.
- A run takes about 2.5 minutes, not the runbook's "about 11".
- Before every launch: no PresentMon or nvidia-smi process, no Codex service, no other Java; total CPU about 2% where measured.
  Wallpaper Engine was paused.

**What didn't go to plan:**
1. **The game window didn't cover the screen.**
   - In launch 1's first try, Emre saw the window's title bar and buttons, which he'd never seen in a test before. The
     window stood at (0, 0), so the 2560x1440 picture was shifted 8 px right and 31 px down, past the screen's edge. In
     all the earlier sessions it sat at (−8, −31), covering the screen exactly (Presenter 1's DirectX 12 loading-screen
     screenshot shows no title bar).
   - `options.txt` is the same file as before (`fullscreen:false`), and the Bridge's changes since Presenter 3 don't
     touch the window. Why Windows placed it so on some launches today isn't known. The PC had restarted at 16:04.
   - With G-SYNC set to full screen only, that wouldn't have been comparable, so Emre said: "Yeah, you can kill it and
     fix it ... G-sync won't hold because it's not full screen."
   - I closed the game normally at 16:51:36 and started each launch through a small script
     (`$T\profiling\scripts\pacer3-run.ps1`). It launches the game as before, and while the game loads it moves the
     window, without activating it, so that the picture starts at (0, 0). It checks again when the run starts. Every
     run started with the picture at (0, 0), 2560x1440, and the game in front.
   - The window came up wrong in launches 2 and 4 too, about 13 seconds after it opened, and was moved into place
     before their runs. In launch 4 it was briefly 2560x1421 and came back to 2560x1440.
2. **The aborted first try left the kit's last mode in the display settings file**: `dlaa=true`, `gpuPacer=true`,
   `gpuPacing=on`, `latePoll=true`, `pacerTargetUs=5000` and `frameCapAtStart=false`, from `lat-pacer-5000`.
   - The kit undoes them only when a run ends. Launch 1's retry and launch 2 started from that file.
   - The manifests show every mode sets its own Reflex, late read, pacer, frame-keeping and cap state (`lat-base`: all
     off), so the measured modes aren't affected. Only the loading screen had the pacer on.
   - Before launches 3 and 4 the file was set back to the runbook's settings.
3. **`lat-base`'s first-play dip** makes "against `lat-base`" read 1–4% better than it is at shore, most on DirectX 12.
   That's why the steady yardstick is given beside it.

No crash, and no new crash log.

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261026-165346-L7`, `20261026-165837-L7`, `20261026-170313-L7`,
  `20261026-170736-L7`, and the stopped `20261026-164920-L7`.
- In `$T\pacer3\`:
  - the game logs `L1-latest.log` to `L4-latest.log` and the window logs `L1-window.txt` to `L4-window.txt`;
  - the aborted try's log;
  - `latency.md`, `tables.md` and `cross.md`;
  - the settings copies, and the display file as the kit left it.
- The session notes are in `$T\hud-split1\SESSION-NOTES.md`.
