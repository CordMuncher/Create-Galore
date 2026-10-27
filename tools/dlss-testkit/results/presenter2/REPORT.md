# Presenter 2 on the RTX 4080 SUPER: where DirectX 12 loses its 11%

PC session on 2026-10-26, 13:04–13:40, by a Claude Code session in `C:\Users\<user>\Documents\Claude\MC-CREATE`
(`$T`). It followed "Presenter 2" in `tools/dlss-testkit/PC-RUNBOOK.md` at `6b4bfa86`, with Bridge 1.114.0 (test build)
and the test kit from that commit.
- Three launches, each with nvidia-smi and PresentMon running beside the game:
  1. `presenter=dx11`, run `P2`;
  2. `presenter=dx12`, `presentTiming=true`, `presentCheck=true`, run `P2X`;
  3. `presenter=dx12`, `presentTiming=true`, `presentQueuePriority=high`, run `P2` (played twice: PresentMon missed
     the first try, see Method).
- DLAA at full resolution (`dlaa=dlss`, preset K), at 2560x1440.
- No crash. This is a test, not a fix: no code was changed.

## Summary

- **Present mode: the same on both.** PresentMon reports `Hardware Composed: Independent Flip` for 100% of the frames
  on DirectX 11 and DirectX 12, in every mode (`p-nvx` at canopy: 97–98%). That is a flip without the desktop
  compositor's copy, not `Composed: Flip`. No switch changes it.
- **Reflex's report says `G-SYNC fullscreen` on DirectX 12 now.** In launches 2 and 3, the same as on DirectX 11. In
  Presenter 1 it was missing on DirectX 12.
- **How much of the 11% is gone? Against today's DirectX 11, most of it. Against DirectX 11 as it ran in every
  earlier session, none.**
  - Today DirectX 12 runs `lat-base` at 152.5–155.5 / 136.5–138 fps against DirectX 11's 160 / 140 (shore / canopy),
    so −3 to −5% / −1 to −2.5%. PresentMon's frame times say −3.5% / −2%. `lat-reflex`: −3% / −2 to −2.5%.
  - But DirectX 12 runs as fast as in Presenter 1: 151 / 138 fps at `lat-base` there, and 155 / 139 at `lat-reflex`.
  - DirectX 11 is what moved. Today it ran 5–10% below its numbers in Presenter 1 and in the three sessions before
    (170–171 / 154–156 fps at `lat-base`, 173–176 / 155–157 at `lat-reflex`).
  - The 1.114.0 commit doesn't touch the DirectX 11 presenter. What's new today is the measuring: PresentMon and
    nvidia-smi ran in every launch. On DirectX 12, PresentMon costs nothing: launch 3's first try ran without it
    (its PresentMon had quit before the game started) and got the same fps. On DirectX 11 that wasn't tested.
  - So the smaller gap most likely comes from DirectX 11 measuring low today, not from DirectX 12 getting faster. One
    DirectX 11 launch without the capture tools would settle it (see "Open question").
- **The new default against waiting every frame (1.113.0's way): no difference in fps.**
  - PresentMon: `p-wait-always` 162 / 141 and 161 / 140 fps in its two plays; `lat-base` 158 / 140 and 161 / 140;
    `lat-reflex` 162 / 141 and 162 / 140. GPU busy per frame is 6.20–6.22 / 7.16 ms in both.
  - The timing lines are the same too: OpenGL waited in 0 frames by default and in every frame with
    `p-wait-always`, with the same hand-off times. The wait every frame costs nothing measurable here.
- **Latency on DirectX 12 is now lower than on DirectX 11.**
  - Clean plays: `lat-base` 11.0 / 13.7 ms (launch 2) and 10.9 / 13.6 ms (launch 3) against 11.8 / 14.5 ms, first
    plays compared. `lat-reflex`: 10.4 / 12.3 and 10.3 / 12.3 against 11.2 / 13.2. So −0.8 to −0.9 ms.
  - That's also about 1 ms below DirectX 12 in Presenter 1 (12.6 / 15.6 ms at `lat-base`, 11.6 / 13.3 at
    `lat-reflex`, medians of both plays; launch 3 here: 11.8 / 14.4 and 10.4 / 12.3).
  - The frames wait about 1 ms less in the GPU queue (`lat-base` queue 3.5–3.7 / 5.0–5.1 ms against 4.8 / 6.2 on
    DirectX 11).
  - Caveat: DirectX 11's latency is also 0.5–1.2 ms higher than in Presenter 1, in line with its lower fps.
- **The switches**, against launch 2's `lat-base`:
  - `p-nvx`: 26% / 25% fewer frames, the lowest latency (8.5 / 9.3 ms), and the GPU only 69–72% busy.
  - `p-buffers3`: the same frame rate, 1.4 ms more latency.
  - `p-latency2`: the same frame rate, 8–9 ms more latency (one more frame queued). **And it sticks**: see the next
    point.
  - `presentQueuePriority=high` (launch 3): no measurable change in fps, latency or the timing lines.
- **`p-latency2` doesn't undo itself.** After it, every mode at frame latency 1 kept the extra queued frame for the
  rest of the launch (6–8 ms more latency, PresentMon's time until displayed 15 / 17.5 ms instead of 7 / 9), even
  though the hand-off line said `frame latency 1`. Its second play added another frame on top. Only the 60 fps cap
  hid it.
  - So launch 2's `p-wait-always` plays and the second plays of `lat-base`, `lat-reflex` and `p-buffers3` ran with the
    extra frame. Their fps is unaffected, but their latency doesn't count. The latency comparisons here use launch 2's
    first plays before `p-latency2`, and launch 3.
- **Timing lines:** OpenGL's hand-off takes 39 µs and Direct3D 12's draw 22 µs. From OpenGL done to the Direct3D 12
  draw starting takes **0.57 ms in every uncapped mode** (90th percentile at most 0.58 ms), 0.13 ms with the 60 fps
  cap, and 0.28 ms with NVX fences. It's under 0.2 ms only with the cap. High queue priority doesn't shorten it.
- **The picture is exact.** All 11 `Perfect Presentation check:` lines in launch 2 say `matches the game's frame
  exactly`: 2 at the start and 1 after each of the 9 switches. No `pixels differ`.

**In one line:** DirectX 12 shows frames the same way as DirectX 11, now gets `G-SYNC fullscreen` in Reflex's report,
and has about 1 ms less latency. Its frame rate hasn't changed since Presenter 1, and the new default doesn't change
it. The smaller gap today comes from DirectX 11 measuring 5–10% below its usual numbers.

## Present mode

PresentMon's most common present mode over each mode's camera paths, with its share (both plays):

| Launch | Modes | shore | canopy |
| --- | --- | --- | --- |
| 1, DirectX 11 | `lat-base`, `lat-reflex`, `lat-cap-default` | Hardware Composed: Independent Flip (100%) | Hardware Composed: Independent Flip (100%) |
| 2, DirectX 12 | `lat-base`, `lat-reflex`, `lat-cap-default`, `p-buffers3`, `p-latency2`, `p-wait-always` | Hardware Composed: Independent Flip (100%) | Hardware Composed: Independent Flip (100%) |
| 2, DirectX 12 | `p-nvx` | Hardware Composed: Independent Flip (100%) | Hardware Composed: Independent Flip (98%; 97% in the second play) |
| 3, DirectX 12, high priority | `lat-base`, `lat-reflex`, `lat-cap-default` | Hardware Composed: Independent Flip (100%) | Hardware Composed: Independent Flip (100%) |

## Each mode and viewpoint against launch 1

**The test kit's numbers** (`latency.py`). fps is the median of both plays. Latency is input to GPU end: medians of
both plays for launches 1 and 3, and first plays for all three. Launch 2's second plays (and both of `p-wait-always`'s)
ran with `p-latency2`'s extra frame, so its latency is first plays only, and `p-wait-always`'s doesn't count.

| mode | viewpoint | fps L1 → L2 → L3 | latency ms, both plays: L1 → L3 | latency ms, first plays: L1 → L2 → L3 | GPU queue ms, first plays: L1 → L2 → L3 |
| --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | 160 → 152.5 (−5%) → 155.5 (−3%) | 12.6 → 11.8 (−0.8) | 11.8 → 11.0 → 10.9 | 4.8 → 3.7 → 3.5 |
| `lat-base` | canopy | 140 → 136.5 (−2.5%) → 138 (−1%) | 15.2 → 14.4 (−0.8) | 14.5 → 13.7 → 13.6 | 6.2 → 5.1 → 5.0 |
| `lat-reflex` | shore | 165 → 160 (−3%) → 160 (−3%) | 11.3 → 10.4 (−0.9) | 11.2 → 10.4 → 10.3 | 5.2 → 4.2 → 4.2 |
| `lat-reflex` | canopy | 142 → 139 (−2%) → 138.5 (−2.5%) | 13.2 → 12.3 (−0.9) | 13.2 → 12.3 → 12.3 | 6.2 → 5.1 → 5.1 |
| `lat-cap-default` (60 fps) | shore | 60 → 60 → 60 | 9.4 → 9.1 (−0.3) | 9.6 → 9.0 → 9.5 | −7.9 → −8.1 → −7.9 |
| `lat-cap-default` (60 fps) | canopy | 60 → 60 → 60 | 9.7 → 9.5 (−0.2) | 10.2 → 9.8 → 9.9 | −7.4 → −7.4 → −7.5 |
| `p-buffers3` | shore | 160 → 158.5 (−1%) | – | 11.8 → 12.4 | 4.8 → 4.2 |
| `p-buffers3` | canopy | 140 → 138.5 (−1%) | – | 14.5 → 15.1 | 6.2 → 5.2 |
| `p-latency2` | shore | 160 → 161 (+1%) | – | 11.8 → 19.1 | 4.8 → 10.4 |
| `p-latency2` | canopy | 140 → 139 (−1%) | – | 14.5 → 22.4 | 6.2 → 12.3 |
| `p-nvx` | shore | 160 → 113.5 (−29%) | – | 11.8 → 8.5 | 4.8 → 0.0 |
| `p-nvx` | canopy | 140 → 102.5 (−27%) | – | 14.5 → 9.2 | 6.2 → 0.0 |
| `p-wait-always` | shore | 160 → 161 (+1%) | – | (11.8 → 19.1, with the extra frame) | (4.8 → 10.4) |
| `p-wait-always` | canopy | 140 → 140 (0%) | – | (14.5 → 22.6, with the extra frame) | (6.2 → 12.3) |

- The `p-` rows compare with launch 1's `lat-base`, since each `p-` mode is `lat-base` plus one switch.
- `lat-base`'s first play, the run's first stretch, runs lower than the other uncapped modes in every launch (151–155
  fps at shore against 159–166). That makes `lat-base`'s fps the noisiest number here; PresentMon's per-play numbers
  below don't have that dip.
- Second plays in launch 2, with the extra frame: `lat-base` 18.9 / 22.7 ms, `lat-reflex` 16.6 / 19.5, `p-buffers3`
  19.1 / 22.1, `p-latency2` 24.0 / 27.8, `p-wait-always` 24.0 / 27.8. Their queue: 10.4 / 12.3 ms (16.6 / 19.5 in the
  second `p-latency2` and `p-wait-always`).

**PresentMon and nvidia-smi** (`frametimes.py`, `gpustats.py`; medians over both plays). "Until displayed" is from
`Present()` to the frame on screen; the number in brackets is the first play only.

| mode | viewpoint | frame ms (fps) L1 → L2 → L3 | GPU busy ms L1 → L2 → L3 | GPU wait ms L1 → L2 → L3 | until displayed ms L1 → L2 → L3 | GPU % and MHz L1 → L2 → L3 |
| --- | --- | --- | --- | --- | --- | --- |
| `lat-base` | shore | 6.03 (166) → 6.25 (160) → 6.26 (160) | 6.06 → 6.22 → 6.20 | 0.01 → 0.01 → 0.01 | 7.39 (6.46) → 7.96 (6.84) → 7.66 (6.74) | 98% 2745 → 98% 2745 → 97% 2745 |
| `lat-base` | canopy | 6.96 (144) → 7.14 (140) → 7.12 (140) | 7.02 → 7.16 → 7.15 | 0.01 → 0.01 → 0.01 | 9.11 (8.34) → 11.61 (8.67) → 9.43 (8.66) | 99% 2745 → 99% 2745 → 99% 2745 |
| `lat-reflex` | shore | 5.98 (167) → 6.18 (162) → 6.18 (162) | 6.03 → 6.21 → 6.19 | 0.01 → 0.01 → 0.01 | 8.01 (7.64) → 9.89 (8.18) → 8.30 (7.91) | 99% 2745 → 99% 2745 → 99% 2745 |
| `lat-reflex` | canopy | 6.95 (144) → 7.11 (141) → 7.12 (140) | 7.00 → 7.16 → 7.15 | 0.01 → 0.01 → 0.01 | 9.54 (9.17) → 10.55 (9.53) → 9.83 (9.47) | 99% 2745 → 99% 2745 → 99% 2745 |
| `lat-cap-default` | shore | 16.94 → 16.47 → 16.46 | 6.87 → 6.82 → 6.88 | 10.34 → 10.10 → 10.09 | 4.87 → 4.96 → 4.97 | 40% 2475 → 39% 2385 → 40% 2340 |
| `lat-cap-default` | canopy | 16.91 → 16.53 → 16.53 | 7.37 → 7.14 → 7.14 | 9.53 → 9.68 → 9.75 | 4.64 → 5.07 → 5.02 | 42% 2760 → 41% 2745 → 41% 2745 |
| `p-buffers3` | shore | 6.03 → 6.21 (161) | 6.06 → 6.21 | 0.01 → 0.01 | 7.39 (6.46) → 9.04 (8.38) | 98% → 99%, 2745 |
| `p-buffers3` | canopy | 6.96 → 7.12 (140) | 7.02 → 7.16 | 0.01 → 0.01 | 9.11 (8.34) → 10.83 (10.09) | 99% → 99%, 2745 |
| `p-latency2` | shore | 6.03 → 6.21 (161) | 6.06 → 6.21 | 0.01 → 0.01 | 7.39 (6.46) → 15.46 (14.94) | 98% → 99%, 2745 |
| `p-latency2` | canopy | 6.96 → 7.16 (140) | 7.02 → 7.16 | 0.01 → 0.01 | 9.11 (8.34) → 18.20 (17.40) | 99% → 99%, 2745 |
| `p-nvx` | shore | 6.03 → 8.37 (119) | 6.06 → 0.24\* | 0.01 → 0.09\* | 7.39 → 0.38\* | 98% → 69%, 2745 |
| `p-nvx` | canopy | 6.96 → 9.12 (110) | 7.02 → 0.24\* | 0.01 → 0.09\* | 9.11 → 0.37\* | 99% → 72%, 2745 |
| `p-wait-always` | shore | 6.03 → 6.21 (161) | 6.06 → 6.20 | 0.01 → 0.01 | 7.39 → 16.40 (15.05)† | 98% → 99%, 2745 |
| `p-wait-always` | canopy | 6.96 → 7.14 (140) | 7.02 → 7.16 | 0.01 → 0.01 | 9.11 → 18.19 (17.54)† | 99% → 99%, 2745 |

\* With NVX fences PresentMon only sees Direct3D 12's own work as the frame's GPU time, not OpenGL's, so these three
columns aren't comparable for `p-nvx`. nvidia-smi's 69–72% is the real picture: the GPU idles about 30% of the time.
† With `p-latency2`'s extra frame.

- **GPU busy per frame is 0.13–0.18 ms higher on DirectX 12** (6.20–6.22 / 7.15–7.16 against 6.06 / 7.02 ms at
  `lat-base`), about the size of the fps gap today. The hand-off (39 µs) and Direct3D 12's draw (22 µs) account for
  about 60 µs of it.
- **The GPU waits about nothing per frame in any uncapped mode** (0.01 ms), on both presenters. A hand-off that stalled
  the GPU would show here; none does.
- **nvidia-smi sees the GPU equally busy on both presenters** (97–99% at 2745 MHz in every uncapped mode). The lower
  GPU use the player saw in Presenter 1 doesn't show today.
- **Time until displayed is 0.3–0.5 ms longer on DirectX 12** in the clean first plays (`lat-base`: 6.84 / 8.67 in
  launch 2 and 6.74 / 8.66 in launch 3, against 6.46 / 8.34 ms). The frames reach the GPU about 0.8 ms sooner but
  take a bit longer to reach the screen.

**PresentMon per play** (fps from the median frame time; the order is the run's order):

| play | L1 DirectX 11 | L2 DirectX 12 | L3 DirectX 12, high priority |
| --- | --- | --- | --- |
| `lat-base` 1 | 164 / 143 | 158 / 140 | 157 / 141 |
| `lat-reflex` 1 | 167 / 143 | 162 / 141 | 161 / 140 |
| `lat-base` 2 | 167 / 144 | 161 / 140 | 161 / 140 |
| `lat-reflex` 2 | 167 / 144 | 162 / 140 | 162 / 141 |
| `p-buffers3` 1, 2 | – | 161 / 141, 161 / 140 | – |
| `p-latency2` 1, 2 | – | 162 / 141, 161 / 140 | – |
| `p-nvx` 1, 2 | – | 119 / 110, 120 / 110 | – |
| `p-wait-always` 1, 2 | – | 162 / 141, 161 / 140 | – |

## The new default

- **Against launch 1 (DirectX 11):** launch 2's `lat-base` is −5% / −2.5% in the kit's fps and −3.5% / −2% in
  PresentMon's frame times. Its latency is 0.8 / 0.8 ms lower (first plays: 11.0 / 13.7 against 11.8 / 14.5 ms).
- **Against launch 2's `p-wait-always` (the 1.113.0 way):** the same frame rate. PresentMon: 162 / 141 and 161 / 140
  fps against `lat-base`'s 158 / 140 and 161 / 140 (and `lat-reflex`'s 162 / 141 and 162 / 140). GPU busy 6.20 / 7.16
  against 6.22 / 7.16 ms. The kit's fps: 161 / 140 against `lat-base`'s 152.5 / 136.5 and `lat-reflex`'s 160 / 139.
  The latency can't be compared: both `p-wait-always` plays ran with `p-latency2`'s extra frame (19.1 / 22.6 ms, the
  same as `p-latency2` itself).
- **How much of the 11% is gone:**
  - Against today's DirectX 11, DirectX 12 is 1–5% behind instead of 11%.
  - Against DirectX 11 as it ran in Presenter 1 (170 / 155 fps at `lat-base`, 173 / 156 at `lat-reflex`), today's
    DirectX 12 is still 8.5–10% / 11–12% behind at `lat-base` and 8% / 11% at `lat-reflex`. DirectX 12's own numbers
    are the same as in Presenter 1 (151 / 138 and 155 / 139 there).
  - The new default isn't what changed anything: `p-wait-always` gets the same frame rate.

## Each switch

Against launch 2's `lat-base` (kit fps 152.5 / 136.5, PresentMon 160 / 140; latency, first play, 11.0 / 13.7 ms):
- `p-nvx`: 113.5 / 102.5 fps (−26% / −25%), 8.5 / 9.2 ms (−2.5 / −4.5); GPU 69–72% busy; draw start 0.28 ms after
  OpenGL is done.
- `p-buffers3`: 158.5 / 138.5 fps (+4% / +1.5%; PresentMon 161 / 140, the same), 12.4 / 15.1 ms (+1.4 / +1.4).
- `p-latency2`: 161 / 139 fps (+6% / +2%; PresentMon 161 / 140, the same), 19.1 / 22.4 ms (+8.1 / +8.7), and the
  extra frame stays after it.
- `presentQueuePriority=high` (launch 3 against launch 2, first plays): `lat-base` 151 / 137 fps and 10.9 / 13.6 ms
  against 152 / 133 and 11.0 / 13.7 (−0.1 / −0.1 ms); `lat-reflex` 159 / 138 and 10.3 / 12.3 against 159 / 139 and
  10.4 / 12.3. PresentMon: 160 / 140 fps at `lat-base` and 162 / 140–141 at `lat-reflex` in both launches. No change,
  and no change in the timing lines either.

## The timing lines

`Perfect Presentation timing` lines, grouped by mode with the kit's `DLAA test: mode` lines: a line covers the time
since the one before, and the Bridge logs one at each mode change, so the line right after a mode line belongs to the
mode before it. The median is the frame-weighted mean of the lines' medians, with the range of the lines' medians in
brackets; the 90th percentile is the highest of the lines'. All in µs. Each play has about 1,680 timed frames in two
or three lines (about 11 s uncapped, 28 s with the cap).

| launch | mode | play | OpenGL hand-off: median / p90 | OpenGL done → DX12 draw start | DX12 draw | OpenGL waited | clocks matched within |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2 | `lat-base` | 1, 2 | 39 / 40 | 566 (562–571) / 574; 567 (558–568) / 572 | 22 / 23 | 0 frames | ≤ 17 µs |
| 2 | `lat-reflex` | 1, 2 | 39 / 40 | 567 (557–568) / 570; 568 (558–569) / 572 | 22 / 23 | 0 | ≤ 46 |
| 2 | `lat-cap-default` | 1, 2 | 43 (39–45) / 46 | 126 (119–130) / 146; 125 (118–128) / 143 | 16 / 17 | 0 | ≤ 88 |
| 2 | `p-buffers3` | 1, 2 | 39 / 40 | 566 (557–568) / 570; 568 (558–569) / 573 | 22 / 23 | 0 | ≤ 13 |
| 2 | `p-latency2` | 1, 2 | 39 / 40 | 568 (558–569) / 573; 570 (558–571) / 575 | 22 / 23 | 0 in play 1; 1,336 of 1,680 (80%) in play 2 | ≤ 9 |
| 2 | `p-nvx` | 1, 2 | 39 / 40 | 281 (280–282) / 360; 279 (279–280) / 351 | 15 / 15 | 0 | ≤ 15 |
| 2 | `p-wait-always` | 1, 2 | 39 / 40 | 567 (558–568) / 572; 571 / 574 | 22 / 23 | every frame (1,680 and 1,513) | ≤ 8 |
| 3 | `lat-base` | 1, 2 | 39 / 40 | 569 (565–573) / 576; 569 (561–570) / 574 | 22 / 23 | 0 | ≤ 41 |
| 3 | `lat-reflex` | 1, 2 | 39 / 40 | 571 (563–572) / 575; 570 (560–571) / 575 | 22 / 23 | 0 | ≤ 36 |
| 3 | `lat-cap-default` | 1, 2 | 43–45 / 46 | 128 (121–132) / 148; 133 (132–134) / 149 | 16–17 / 17 | 0 | ≤ 40 |

- **OpenGL done to Direct3D 12 draw start is under 0.2 ms only with the 60 fps cap** (125–133 µs), when the GPU has
  time to spare. In every uncapped mode it's 0.57 ms with EXT fences and 0.28 ms with NVX fences. High queue priority
  leaves it at 0.57 ms.
- **OpenGL still waited** only with `p-wait-always` (every frame, as asked) and in the second `p-latency2` play (80% of
  frames, with two extra frames queued). By default: 0 frames in every mode of both launches.
- **The clocks matched** within 3–88 µs, and within 46 µs apart from one line.
- A line that reaches past the run's end is left out: so the second `p-wait-always` play has one line (1,513 frames)
  and launch 3's second `lat-cap-default` two (1,200 frames).
- Before the run, while the world loaded, the lines were different (hand-off 289–296 µs, draw start 559–608 µs, draw
  181–183 µs). They are left out too.

## Reflex's report

- **Launch 1 (DirectX 11):** `driver low latency on, last sleep 800–3147 us, game Sleep used, G-SYNC fullscreen`.
  When switching on: `Reflex: low latency on, boost off, frame cap none; driver low latency on, last sleep 0 us, game
  Sleep not used, G-SYNC fullscreen` (`driver low latency off` at the first switch).
- **Launch 2 (DirectX 12):** the same, with `G-SYNC fullscreen`: last sleep 800–3527 µs.
- **Launch 3 (DirectX 12, high priority):** the same, with `G-SYNC fullscreen`: last sleep 800 µs.
- Driver input to GPU end: 11.9 / 13.8 ms (launch 1), 11.0 / 12.9 (launch 2's first plays; 17.3 / 20.1 in the second,
  with the extra frame), 10.9–11.0 / 12.9 (launch 3). Unit check 1.00 in all three.

## Log lines

**`Perfect Presentation:`**
- All three: `on, the game window will be shown through a DXGI swap chain`.
- Launch 1: `NeoForge's OpenGL version check no longer reads the game window`, `DXGI swap chain ready on DirectX 11
  (OpenGL on a hidden context)`.
- Launches 2 and 3: `DirectX 12 on NVIDIA GeForce RTX 4080 SUPER (OpenGL node mask 1, tearing allowed, queue priority
  normal, GL_NVX_progress_fence available)` (launch 3: `queue priority high`), the version check line, and `DXGI swap
  chain ready on DirectX 12 (OpenGL on a hidden context)`. No `DirectX 12 could not start`.
- `cg_present-e6f69df4098f.dll` (helper version 2) was unpacked into `$T\instance\create-galore-dlss`, next to
  1.113.0's `cg_present-4d0b98989df8.dll`.
- **Hand-off lines, launch 2:** `DirectX 12 hand-off: OpenGL waits when needed, EXT fences, frame latency 1, 2 buffers`
  at the start. Then, as each mode started, in both passes: `... frame latency 1, 3 buffers` (`p-buffers3`),
  `... frame latency 2, 2 buffers` (`p-latency2`), `... NVX fences, frame latency 1, 2 buffers` (`p-nvx`),
  `OpenGL waits every frame, EXT fences, frame latency 1, 2 buffers` (`p-wait-always`), and back to the default for the
  second pass's `lat-base`. 10 lines, one for each of the 9 switches.
- **Check lines, launch 2**, all `DirectX 12 back buffer 2560x1440 matches the game's frame exactly (<hand-off>)`:
  13:11:17 and 13:12:49 at the start; then 13:13:09, 13:13:21, 13:13:35, 13:13:51, 13:14:04, 13:14:59, 13:15:12,
  13:15:25 and 13:15:41, each about 2 s (300 frames) after a switch. Launches 1 and 3 had `presentCheck` off.
- **One WARN in each launch**, `Perfect Presentation: swap chain wait timed out`, while the world loaded, before the
  run: the same one-off as in every Perfect Presentation launch since Reflex 1.

**`Reflex:`** `started on Perfect Presentation's DirectX 11 device (NVIDIA NVAPI)` (launch 1) and `... DirectX 12
device (NVIDIA NVAPI)` (launches 2 and 3); `Reflex: on` / `Reflex: off` 2× each; the reports above.

**`Latency:`** the same set in all three launches: `the frame cap waits at the start of the frame, before input` (3×),
`... in the game's limiter` (2×; 3× in launch 2), `waits use a high resolution timer` (1×), `input read again just
before rendering on` / `off` (2× each).

**No `Perfect Presentation`, `Latency:` or `Reflex:` WARN or ERROR lines** apart from the one-off swap chain timeout.
DLSS ran the same in all three: `DLSS running at 2560x1440 (full resolution), preset K, the pack's exposure`.

## The PC's settings (looked at only, nothing changed)

Read again before launch 1. Unchanged from Presenter 1:
- **NVIDIA Control Panel, global:** Low Latency Mode Off, Max Frame Rate Off, Vertical sync "Use the 3D application
  setting", Monitor Technology G-SYNC Compatible, Power management mode Normal.
- **G-SYNC:** enabled, for full screen mode only.
- **Monitor:** 2560x1440 at 240 Hz (Windows reports 239, at most 240).

## Open question: why DirectX 11 ran slower today

- Today's DirectX 11 `lat-base`: 160 / 140 fps (kit), 166 / 144 (PresentMon), the GPU 98–99% busy at 2745 MHz.
- Earlier sessions with Perfect Presentation on DirectX 11, kit fps at `lat-base`: 171 / 156 (Pacer 2, this morning),
  170 / 155 (Presenter 1, 1.113.0), 171 / 154 (Pacer 1), 171 / 154 (Reflex 1).
- The 1.114.0 commit changes only the DirectX 12 presenter, its helper DLL, the display settings and the test kit.
- New today: PresentMon (an ETW trace, elevated) and nvidia-smi (every 250 ms) ran in every launch. On DirectX 12 they
  cost nothing measurable: launch 3's first try, without PresentMon, got the same numbers (`lat-base` 152–161 /
  137–139 fps, 11.8 / 14.3 ms; `lat-reflex` 159–161 / 138–140, 10.3 / 12.3). On DirectX 11 that's untested.
- One DirectX 11 launch with `runs=P2` and no capture tools (about 4 minutes, no administrator prompt) would tell. If it
  gets back to about 170 / 155, the tools cost DirectX 11 frames and DirectX 12 is still about 10% behind. If it stays
  at about 160 / 140, the PC changed and today's 1–5% gap is the real one. It isn't in the runbook, so it wasn't run.

## Method

**Update.**
- `git fetch` and `checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh` (`6b4bfa86`).
- `create-galore-bridge-1.113.0.jar` removed; `create-galore-bridge-1.114.0.jar` (1,390,369 B) and the test kit jar
  (56,152 B) copied in. `nvngx_dlss.dll` 310.9.16.

**Settings, and what they were before.** All are back at the end, checked byte for byte against copies taken before
the session.
- `create_galore_bridge-display.properties`: `dlaa=true` → `dlaa=dlss`; `perfectPresentation=false` → `true`; plus
  each launch's lines from the runbook table. 1.114.0 rewrites the file with its comments and kept the added lines.
  The file was put back as it was, so `presenter`, `presentTiming`, `presentCheck` and `presentQueuePriority` are
  gone.
- `config\DistantHorizons.toml`: `numberOfThreads` 32 → 2.
- `config\fml.toml`: `earlyWindowControl = false` was already so.
- `options.txt` and Sodium's options: not touched, and unchanged.
- The kit's config didn't exist. The kit wrote its defaults, and it was deleted again at the end.

**Launches.** As in step 4, with `--jvm-arg=-Dcreate_galore_dlss_testkit.autorun=dlaa-test` and the run below:

| Launch | Display settings added | Run | Time |
| --- | --- | --- | --- |
| 1 | `presenter=dx11` | `20261026-130740-P2`: 12 plays (3 modes, 2 viewpoints, twice), 10,740 frames | 13:07:40–13:09:37 |
| 2 | `presenter=dx12`, `presentTiming=true`, `presentCheck=true` | `20261026-131202-P2X`: 28 plays (7 modes), 24,180 frames | 13:12:02–13:15:51 |
| 3, first try | `presenter=dx12`, `presentTiming=true`, `presentQueuePriority=high` | `20261026-131806-P2`, no PresentMon data | 13:18:06–13:20:04 |
| 3 | the same | `20261026-132241-P2`: 12 plays, 10,740 frames | 13:22:41–13:24:39 |

- P2X plays its modes in the order `lat-base`, `lat-reflex`, `lat-cap-default`, `p-buffers3`, `p-latency2`, `p-nvx`,
  `p-wait-always`, not the runbook's. Each mode plays shore, then canopy, and the whole list runs twice.
- **The capture tools**, per launch, as in the runbook's steps 1 and 2, done by a small wrapper script
  (`$T\profiling\scripts\presenter-run.ps1`): nvidia-smi started just before the launch (250 ms samples) and stopped
  after the game closed; PresentMon 2.6.0 (console build in `$T\tools`) attached to the game's own `java.exe`,
  elevated, with the runbook's options. The player clicked 4 administrator prompts (launches 1, 2, 3's first try, 3). Every
  CSV grew during its run.
- **Results** with `latency.py` (all three runs), `frametimes.py` (one launch at a time) and `gpustats.py`, as in the
  runbook. Two small scripts of my own (on the PC, not committed) split PresentMon's numbers per play and grouped the
  timing lines per mode. `gpustats.py` stopped at the last line of each nvidia-smi CSV, which was cut off when
  nvidia-smi was stopped, so it read copies without that line (`L<n>-gpu-rows.csv`).

**What was open.**
- No game server, and no other game or launcher.
- Still running in the background: the Claude app, FanControl, the NVIDIA overlay, NZXT CAM, PowerToys, Razer's app
  engine and elevation service, the PresentMon service, the Windows Package Manager server, and a Settings window.

**What didn't go to plan:**
1. **Launch 3's first try recorded no PresentMon data.**
   - The wrapper took the first `java.exe` from the test folder that had run for 8 seconds. That was portablemc's
     short-lived one (pid 676), not the game, and PresentMon (`--terminate_on_proc_exit`) quit when it exited.
   - The run itself (`20261026-131806-P2`) is complete, with nvidia-smi and the game log, so it's used once: as
     DirectX 12 without PresentMon.
   - The wrapper now takes only the `java.exe` that owns the game window. Launch 3 was run again, with one more
     administrator prompt.
2. **PresentMon didn't stop when the game closed**, although it was started with `--terminate_on_proc_exit`.
   - Launch 1's and launch 2's were stopped by the next launch's `--stop_existing_session`.
   - Launch 3's ran to its 15-minute limit (13:36:33), and its CSV was read after that.
   - Every play has its full frames (600 per viewpoint and play; 621 in one `p-nvx` play).
3. **`p-latency2` left an extra queued frame** for the rest of launch 2 (see Summary). That spoiled the latency of
   `p-wait-always` and of launch 2's second plays.
4. **PresentMon warned `1614 overflowed present events detected`** in launch 2, before `Started recording`, while it
   took over the session launch 1's PresentMon had left running. The plays have all their frames.
5. **nvidia-smi lost its last seconds** when it was stopped: launch 1's CSV ends 13:09:30, 7 s before the run did,
   and launch 3's 2 s before. So `lat-cap-default` at canopy has 50 samples in launch 1 and 69 in launch 3, against 78
   in launch 2.

No crash, and no new crash log. The only `hs_err` file in the instance is the old one from Upscaling 1.

## Raw data, on the PC only

- Run folders: `$T\instance\screenshots\dlaa-test\20261026-130740-P2`, `20261026-131202-P2X`, `20261026-131806-P2`
  (launch 3's first try) and `20261026-132241-P2`.
- In `$T\presenter2\`: the PresentMon CSVs `L1.csv`, `L2.csv`, `L3.csv` and their logs; the nvidia-smi CSVs
  `L<n>-gpu.csv` (and `L3-try1-gpu.csv`); the game logs `L<n>-latest.log` and `L3-try1-latest.log`; the script output
  (`latency.md`, `frametimes-L<n>.md`, `ftplays-L<n>.md`, `gpustats.md`, `timing-L2.md`, `timing-L3.md`); this
  session's notes; the settings copies; the display settings file as 1.114.0 left it; and the kit config it wrote.
