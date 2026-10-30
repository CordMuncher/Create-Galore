# Milestone 2 on the RTX 4080 SUPER: things that move

PC session on 2026-10-24, 21:50–22:54, by a Claude Code session in `C:\Users\<user>\Documents\Claude\MC-CREATE` (`$T`),
following "Milestone 2, PC session: things that move" in `tools/dlss-testkit/PC-RUNBOOK.md` at `956c9e4`, with
Bridge 1.91.0 and the test kit from that commit. Automated runs J–N, then about 45 minutes by eye with the player.

**The player's game server ran during the whole session** (his call; the test game had the X3D CCD). Frame times in this
report are noisy and are not used for any conclusion.

## Summary

- **Everything ran clean.** Runs J–N all finished (`run finished`). No `Could not compile shader`,
  `Failed to create shader rendering pipeline`, `this Colorwheel's shaders are not the ones the Bridge knows`,
  `Missing elements in vertex` or `DLAA: entity motion attribute at`, and no `[Colorwheel] ... broken shaders` in chat,
  in any run or in the by-eye session. The first launch remade the Photon copy:
  `DLAA: made shaderpacks/Photon v1.3a + DLAA from photon_v1.3a + Clrwl_1.0.5.zip`. FSR 3.1 started in every FSR mode
  (`ready` / `running at 2560x1440`). Create Submarine's vortex shader, which failed to compile in rounds 1 and 2, no
  longer shows any error.
- **The motion vectors are exact on NVIDIA (mvcheck, runs L, M, N).** In every row of every table, k = 1 matches best.
  Compared with no motion vectors (k = 0), the difference left over drops by:

  | What | Less difference at k = 1 than at k = 0 |
  | --- | --- |
  | Leaves at the canopy, waving fast (L) | 55–65 % |
  | Moving scene with the ship turning 20°/tick (L) | 54–67 % |
  | Moving scene with the ship still (M) | 72–78 % |
  | Panda, chest, armor stand, bell, villager, spinning sword (M boxes) | 75–97 % (item with sub-pixel motion: 58–93 %) |
  | Moving scene with the hand (N) | 65–82 % |
  | First-person hand, sword swinging (N box) | 85–93 %, including the two swing frames at a median 462 and 508 px/frame |

  The cloud found 55–68 % overall.
- **The masks cut FSR's smear by 23–41 % (ghostcheck, run K, at 2560x1440).** That's the same range as the cloud's
  30–45 % at 1280x720. FSR's `off` without and with masks:

  | Box | Without masks | With masks | Cut |
  | --- | --- | --- | --- |
  | Lava pool | 10.3 | 6.1 | 41 % |
  | Lava fall | 8.7 | 5.1 | 41 % |
  | Portal | 6.6 | 4.1 | 38 % |
  | Smoke | 4.3 | 2.8 | 35 % |
  | Fire | 8.0 | 5.6 | 30 % |
  | Campfire | 7.3 | 5.1 | 30 % |
  | Waterfall | 3.0 | 2.3 | 23 % |

  **DLSS, without any mask, is lower than FSR with masks on every box**: portal 3.0, fire 3.3, campfire 4.0, lava fall
  3.8, lava pool 4.4, smoke 2.1, waterfall 1.7. Its `kept` is 0.08–0.23, against FSR's 0.16–0.31 and 0.24–0.47 without
  masks. On the still bricks under the rain (the normal anti-aliasing level) DLSS is at 1.5, FSR 1.8, FSR without
  masks 2.8.
- **By eye, the player's verdict (details in [the player's notes](#emres-notes)):**
  - **Motion:** `dlaa-default` shows no motion degradation, "pretty perfect", no trails or ghosts on anything moving.
  - **Camera-only motion:** `dlaa-camera` looks the same while standing still. While moving around moving things,
    textures go soft ("like you need glasses"), and shadows in the gear notches boil clearly. So the new motion vectors
    fix both.
  - **Photon's TAA:** "a pretty clear step down". Jaggies remain, shadows freak out, water reflections are dithered,
    distant trees crawl, and static textures fizzle indefinitely.
  - **FSR with masks:** about 95 % of the time better than Photon's TAA. A bit more trailing in reflections and shadows
    on fast flicks than DLSS; slightly less clear but much more stable than TAA. He'd ship it as the default without an
    NVIDIA GPU.
  - **Effects without masks:** under DLSS every effect except rain looks flawless and native ("if you didn't tell me
    they were not masked, I would not know"). Rain is the worst case: DLSS struggles there, but only if you go
    looking. FSR with masks matches DLSS on every effect except rain, where it keeps the rain clearer but more
    pixelated and less of a piece with the rest of the image. FSR without masks is "the worst of both worlds", and
    Photon's TAA looks like it.
  - **Conclusion:** "use DLSS if you have an NVIDIA GPU. If you don't, use FSR, don't look back."
- **Found along the way (not anti-aliasing):**
  - **Particles over the hand:** small particles are drawn on top of the first-person hand and held items in **every**
    mode (DLSS, FSR with and without masks, Photon's TAA). That covers the water sparkles, rain splash droplets,
    campfire smoke and portal particles; the rain streaks themselves are fine. The player thinks it's the shader pack's
    interaction with some particle effects. Phone photos in `$T\round-m2-photos\`.
  - **Rain in fog:** rain looks thinner in fog at the horizon in every mode, so it's the pack's own look.
  - **Shadows up close:** they fizzle for about half a second up close (Photon's lighting). The motion vectors make it
    much calmer on Create's gears. Something to look at when tuning Photon.

## Setup and changes

| | |
| --- | --- |
| Bridge | `pack/mods/create-galore-bridge-1.91.0.jar` (1,179,952 bytes), replacing 1.89.0 |
| Test kit | `tools/dlss-testkit/dist/create-galore-dlss-testkit-1.0.0.jar` from `956c9e4` (37,471 bytes) |
| Instance | as in rounds 1–2: `dlaa=true`, `perfectPresentation=false`, `earlyWindowControl = false`, `tutorialStep:none`, Distant Horizons radius 64 on 32 threads, DH database from round 1 (224.9 MB, unchanged) |
| NVIDIA App override | still off: NGX loads `instance\create-galore-dlss\nvngx_dlss.dll` 310.9.16 |
| Picture | 2560x1440, driver 621.01 |

As the runbook says, `config\create_galore_dlss_testkit.json` was deleted before J and edited between runs:
1. Before K: `"paths": ["brief"]`, `"dumpRaw": true`.
2. Before L: also `"packSecondsPerFrame": 1.0`, `"shipTurnDegreesPerTick": 20.0`, `"shipSwayPerTick": 0.5`.
3. Before M: ship turn and sway `0.0`.
4. Before N: `"showHand": true`.

It was deleted after N. The by-eye session's `/dlaatest mode` commands wrote it again, so it was deleted once more at
the end.

## Runs

| Run | Folder (`$T\instance\screenshots\dlaa-test\`) | Viewpoint | Modes | Outcome | Shots |
| --- | --- | --- | --- | --- | --- |
| J | `20261024-215254` | moving | photon-taa, dlaa-default, dlaa-camera, fsr3 | run finished | 108 |
| K | `20261024-215617` | effects | photon-taa, dlaa-default, fsr3, fsr3-nomask, debug-mask | run finished | 40 |
| L | `20261024-215922` | canopy, moving | debug-motion, debug-residual | run finished | 32 |
| M | `20261024-220144` | moving | debug-motion, debug-residual | run finished | 16 |
| N | `20261024-220401` | moving | debug-residual | run finished | 8 |

`"dlaaActive"` is filled in only on the `still` path, so K–N (which use `brief`) leave it empty. J has it false for
`photon-taa` and true for the other three. The raw dumps (0.5–1 GB per mode) were used for ghostcheck and mvcheck,
then deleted.

## Script outputs

### J: metrics.py

```
Run 20261024-215254 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### moving

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 10.3 | 2.17 / 4.95% | 11.45 / 13.23 | 5.52 |
| dlaa-default | yes | 10.61 | 2.09 / 4.83% | 10.35 / 15.28 | 3.83 |
| dlaa-camera | yes | 9.73 | 1.54 / 2.82% | 9.90 / 15.61 | 3.77 |
| fsr3 | yes | 9.65 | 1.26 / 2.69% | 8.59 / 16.36 | 3.91 |

No conclusions from the frame times (game server running). At this viewpoint the machines, ship and entities keep
moving during the still path and the flick, so shimmer and residue here also count the scene's own motion. Lower
isn't simply better: a mode that blurs moving things changes less from frame to frame. The player saw that blur in
`dlaa-camera` by eye.

### K: ghostcheck.py

Boxes as in the runbook (2560x1440).

#### dlaa-default

| box | changed | kept | off |
| --- | --- | --- | --- |
| all | 31.7% | 0.11 | 1.4 |
| portal (330, 760, 590, 1070) | 53.6% | 0.15 | 3.0 |
| fire (780, 920, 1050, 1090) | 75.8% | 0.08 | 3.3 |
| campfire (1110, 930, 1240, 1090) | 49.5% | 0.18 | 4.0 |
| lavafall (1440, 510, 1590, 820) | 52.6% | 0.19 | 3.8 |
| lavapool (1400, 810, 1870, 990) | 46.9% | 0.23 | 4.4 |
| waterfall (1930, 410, 2150, 980) | 49.6% | 0.10 | 1.7 |
| smoke (1950, 920, 2130, 1050) | 56.0% | 0.09 | 2.1 |
| sky (0, 0, 2560, 400) | 20.8% | 0.13 | 0.8 |
| bricks (600, 1160, 1280, 1300) | 28.3% | 0.13 | 1.5 |

#### fsr3

| box | changed | kept | off |
| --- | --- | --- | --- |
| all | 30.3% | 0.20 | 1.8 |
| portal (330, 760, 590, 1070) | 55.1% | 0.27 | 4.1 |
| fire (780, 920, 1050, 1090) | 70.8% | 0.17 | 5.6 |
| campfire (1110, 930, 1240, 1090) | 44.5% | 0.29 | 5.1 |
| lavafall (1440, 510, 1590, 820) | 55.1% | 0.25 | 5.1 |
| lavapool (1400, 810, 1870, 990) | 49.8% | 0.31 | 6.1 |
| waterfall (1930, 410, 2150, 980) | 46.3% | 0.19 | 2.3 |
| smoke (1950, 920, 2130, 1050) | 59.1% | 0.16 | 2.8 |
| sky (0, 0, 2560, 400) | 19.8% | 0.18 | 0.9 |
| bricks (600, 1160, 1280, 1300) | 28.0% | 0.22 | 1.8 |

#### fsr3-nomask

| box | changed | kept | off |
| --- | --- | --- | --- |
| all | 31.3% | 0.29 | 2.6 |
| portal (330, 760, 590, 1070) | 53.1% | 0.42 | 6.6 |
| fire (780, 920, 1050, 1090) | 70.7% | 0.26 | 8.0 |
| campfire (1110, 930, 1240, 1090) | 44.4% | 0.41 | 7.3 |
| lavafall (1440, 510, 1590, 820) | 52.7% | 0.41 | 8.7 |
| lavapool (1400, 810, 1870, 990) | 53.6% | 0.47 | 10.3 |
| waterfall (1930, 410, 2150, 980) | 46.0% | 0.25 | 3.0 |
| smoke (1950, 920, 2130, 1050) | 57.2% | 0.24 | 4.3 |
| sky (0, 0, 2560, 400) | 20.8% | 0.34 | 1.4 |
| bricks (600, 1160, 1280, 1300) | 29.0% | 0.34 | 2.8 |

### L: mvcheck.py

#### debug-residual, canopy-brief

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 40.4% | 2.91 | 12.12 | 7.98 | 5.14 | **2.85** | 7.96 |
| 33->34 | 40.7% | 2.66 | 12.17 | 8.36 | 5.41 | **2.89** | 8.28 |
| 34->35 | 40.6% | 2.53 | 11.43 | 7.83 | 5.09 | **2.85** | 8.29 |
| 35->36 | 40.3% | 2.00 | 10.09 | 6.55 | 4.30 | **2.71** | 6.81 |
| 36->37 | 40.6% | 2.24 | 10.52 | 7.09 | 4.62 | **2.79** | 7.14 |
| 37->38 | 40.2% | 2.38 | 10.60 | 7.11 | 4.72 | **2.82** | 7.47 |
| 38->39 | 39.9% | 1.95 | 9.57 | 6.33 | 4.29 | **2.84** | 6.55 |

#### debug-residual, moving-brief (ship turning 20°/tick, swaying 0.5/tick)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 14.5% | 10.29 | 28.37 | 20.06 | 16.61 | **9.13** | 25.37 |
| 33->34 | 14.6% | 11.63 | 30.39 | 20.94 | 16.65 | **7.93** | 25.32 |
| 34->35 | 14.3% | 14.58 | 32.04 | 22.51 | 17.12 | **7.43** | 25.70 |
| 35->36 | 13.9% | 12.76 | 33.56 | 22.54 | 18.61 | **8.46** | 25.73 |
| 36->37 | 14.5% | 13.99 | 33.12 | 22.12 | 18.18 | **9.98** | 25.95 |
| 37->38 | 15.0% | 10.92 | 31.50 | 21.68 | 17.23 | **8.59** | 25.12 |
| 38->39 | 15.1% | 6.77 | 29.01 | 18.53 | 14.08 | **7.92** | 23.01 |

### M: mvcheck.py (ship still)

#### debug-residual, moving-brief

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 17.4% | 14.54 | 29.48 | 20.39 | 15.07 | **5.08** | 22.07 |
| 33->34 | 17.0% | 13.00 | 29.08 | 19.93 | 15.14 | **5.06** | 21.77 |
| 34->35 | 16.9% | 11.78 | 26.68 | 18.17 | 13.73 | **4.84** | 20.48 |
| 35->36 | 16.7% | 12.75 | 28.12 | 19.39 | 14.51 | **5.04** | 21.29 |
| 36->37 | 16.7% | 5.73 | 25.41 | 16.43 | 12.18 | **4.54** | 17.82 |
| 37->38 | 16.7% | 12.62 | 28.70 | 20.48 | 15.53 | **4.68** | 22.09 |
| 38->39 | 16.8% | 13.38 | 28.51 | 20.41 | 16.04 | **4.53** | 22.77 |

#### panda (0, 880, 640, 1400)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 1.2% | 19.08 | 45.58 | 39.01 | 27.00 | **1.87** | 37.30 |
| 33->34 | 1.1% | 20.80 | 53.57 | 44.30 | 30.16 | **2.65** | 43.29 |
| 34->35 | 1.2% | 16.25 | 51.14 | 43.30 | 30.25 | **2.15** | 42.47 |
| 35->36 | 1.5% | 19.15 | 52.41 | 50.98 | 36.98 | **6.03** | 45.35 |
| 36->37 | 1.3% | 21.53 | 56.01 | 44.31 | 31.55 | **3.11** | 39.36 |
| 37->38 | 1.1% | 29.44 | 59.64 | 53.21 | 43.64 | **7.50** | 45.97 |
| 38->39 | 1.2% | 36.01 | 57.15 | 51.19 | 43.53 | **3.89** | 48.31 |

#### chest (340, 760, 620, 1080)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 0.6% | 11.53 | 31.10 | 25.46 | 16.46 | **3.88** | 28.89 |
| 33->34 | 0.6% | 24.81 | 39.87 | 33.87 | 27.32 | **2.78** | 34.96 |
| 34->35 | 0.5% | 47.04 | 44.23 | 40.51 | 36.65 | **3.16** | 46.85 |
| 35->36 | 0.5% | 79.69 | 48.28 | 55.07 | 45.69 | **12.29** | 47.37 |
| 36->37 | 0.4% | 43.28 | 54.49 | 40.04 | 36.34 | **1.79** | 37.45 |
| 37->38 | 0.5% | 68.68 | 58.63 | 53.56 | 46.00 | **13.56** | 42.34 |
| 38->39 | 0.5% | 34.08 | 49.37 | 40.13 | 34.45 | **4.93** | 45.08 |

#### stand (1800, 940, 2400, 1400)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 2.6% | 21.36 | 35.52 | 26.06 | 17.09 | **1.28** | 24.45 |
| 33->34 | 2.4% | 27.67 | 38.11 | 26.50 | 18.16 | **1.72** | 26.48 |
| 34->35 | 2.3% | 14.14 | 26.62 | 20.51 | 12.45 | **1.19** | 21.07 |
| 35->36 | 2.1% | 15.11 | 26.16 | 19.70 | 12.41 | **1.32** | 21.77 |
| 36->37 | 2.0% | 6.53 | 17.37 | 10.70 | 5.76 | **1.20** | 12.50 |
| 37->38 | 1.8% | 18.55 | 24.80 | 21.51 | 15.09 | **1.28** | 24.11 |
| 38->39 | 1.6% | 19.33 | 26.38 | 22.73 | 17.66 | **2.23** | 25.11 |

#### bell (2160, 880, 2320, 1040)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 0.7% | 22.64 | 23.40 | 18.01 | 11.22 | **0.62** | 16.49 |
| 33->34 | 0.7% | 33.83 | 24.09 | 17.50 | 12.53 | **0.75** | 17.76 |
| 34->35 | 0.7% | 13.91 | 17.29 | 13.03 | 7.64 | **0.62** | 12.71 |
| 35->36 | 0.7% | 15.62 | 17.31 | 13.41 | 7.84 | **0.68** | 14.06 |
| 36->37 | 0.7% | 7.26 | 11.91 | 7.05 | 3.90 | **0.76** | 7.54 |
| 37->38 | 0.7% | 15.00 | 17.53 | 15.40 | 8.34 | **0.68** | 15.61 |
| 38->39 | 0.7% | 15.97 | 16.58 | 15.45 | 9.04 | **0.73** | 16.69 |

#### villager (940, 660, 1140, 1020)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 0.3% | 18.30 | 22.65 | 17.90 | 12.51 | **2.91** | 41.28 |
| 33->34 | 0.1% | 15.74 | 16.79 | 12.60 | 9.13 | **2.55** | 30.77 |
| 34->35 | 0.1% | 11.56 | 17.47 | 14.35 | 9.50 | **1.64** | 20.28 |
| 35->36 | 0.1% | 12.85 | 14.24 | 14.34 | 8.98 | **1.42** | 18.19 |
| 36->37 | 0.1% | 11.66 | 21.68 | 16.60 | 10.48 | **1.52** | 17.06 |
| 37->38 | 0.1% | 11.82 | 12.37 | 11.85 | 8.70 | **1.11** | 13.03 |
| 38->39 | 0.1% | 12.96 | 12.82 | 13.38 | 7.83 | **1.01** | 13.15 |

#### item (1360, 680, 1540, 880)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 0.2% | 31.80 | 20.69 | 14.09 | 12.21 | **5.52** | 64.71 |
| 33->34 | 0.1% | 2.80 | 36.97 | 24.42 | 16.85 | **5.94** | 56.70 |
| 34->35 | 0.1% | 0.88 | 22.33 | 11.03 | 7.87 | **4.59** | 17.63 |
| 35->36 | 0.1% | 1.35 | 44.06 | 33.04 | 24.91 | **4.95** | 13.38 |
| 36->37 | 0.2% | 33.41 | 64.25 | 47.23 | 27.25 | **3.12** | 16.81 |
| 37->38 | 0.3% | 37.83 | 59.49 | 28.80 | 15.77 | **2.25** | 11.68 |
| 38->39 | 0.4% | 38.03 | 49.61 | 24.37 | 15.37 | **2.95** | 24.28 |

The chest's two higher k = 1 rows (12.29, 13.56) are the frames where the lid moves fastest (median 69–80 px/frame);
k = 1 is still the best by far there.

### N: mvcheck.py (hand shown, sword swinging)

#### debug-residual, moving-brief

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 16.4% | 12.01 | 34.39 | 27.84 | 23.59 | **5.69** | 28.33 |
| 33->34 | 23.4% | 23.79 | 28.72 | 45.37 | 40.87 | **8.02** | 46.16 |
| 34->35 | 14.6% | 5.80 | 23.43 | 15.22 | 11.65 | **5.26** | 18.09 |
| 35->36 | 17.4% | 11.95 | 33.41 | 33.02 | 28.27 | **6.36** | 20.61 |
| 36->37 | 15.6% | 11.52 | 30.08 | 24.04 | 18.10 | **8.26** | 27.51 |
| 37->38 | 15.8% | 11.45 | 33.96 | 26.81 | 23.53 | **5.84** | 27.72 |
| 38->39 | 23.0% | 23.37 | 30.85 | 46.32 | 40.55 | **8.69** | 46.83 |

#### hand (1280, 720, 2560, 1440)

| frames | moving pixels | median px/frame | k=-1 | k=0 | k=0.5 | k=1 | k=2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 32->33 | 5.5% | 34.75 | 59.47 | 54.51 | 47.46 | **4.44** | 46.62 |
| 33->34 | 11.2% | 461.98 | 42.69 | 70.08 | 65.15 | **9.32** | 69.76 |
| 34->35 | 3.8% | 10.87 | 28.75 | 18.98 | 12.50 | **2.49** | 20.20 |
| 35->36 | 6.2% | 24.81 | 50.98 | 59.61 | 51.28 | **5.81** | 29.08 |
| 36->37 | 4.6% | 23.69 | 41.38 | 37.86 | 23.41 | **4.44** | 38.65 |
| 37->38 | 4.4% | 24.50 | 60.10 | 54.07 | 50.22 | **3.98** | 50.64 |
| 38->39 | 10.0% | 508.00 | 45.92 | 74.02 | 68.08 | **11.39** | 75.85 |

## the player's notes

The by-eye session ran from 22:07 to 22:53. I started the game without autorun and opened `dlaa-test` (computer use).
Then I switched modes and scenes with the runbook's `/dlaatest` commands, typed into the game's chat, while the player
played and watched. The moving scene was built with `/dlaatest scene build` at normal speed. The effects scene was
built with `/dlaatest scene build effects`. The first build at 22:33 still had the moving scene up, because a doubled
slash made the `remove` fail, so it was taken down and the effects scene rebuilt clean at 22:35. The rain is
Minecraft's normal rain at full level (`weather rain`, thunder off); the test kit only skips the fade-in. The player found
it very heavy.

### Moving scene

**dlaa-default.** The player rode the ship and looked from close up and far away.
- "Looks pretty perfect": no trails, ghosts or anything on objects as they move, from a distance or close up.
  Maybe something with his nose against the screen, "but that's just DLSS working".
- Textures and edges stay clean in motion; transparencies look as expected.
- The only thing he noticed: very close to a shadow, it fizzles for about half a second while it settles
  ("boil is too aggressive"). He thinks it's Photon's lighting, not DLSS, and not motion-related; something for tuning.

**dlaa-camera** (camera motion only, as before this milestone). An obvious difference, quick to see.
- Standing still: basically the same as dlaa-default.
- Moving around moving things, e.g. orbiting the spinning cross: a little more trailing, but mainly texture detail
  goes soft. "It's like you need glasses": a haze when both the camera and the object move, not a double image. As
  if DLSS trades clarity for blur because it doesn't know what's happening. Not there in dlaa-default.
- Shadows in the notches of the turning gear boil much more. Photon's lighting is unstable close up, especially on
  sub-block notches like Create's gears. With the motion vectors you only notice it if you stare, so the motion
  vectors help the shadows too.

**photon-taa.** "A pretty clear step down", enough for a double take.
- It goes for sharpness (sharpening) over full anti-aliasing: jagged edges stay on diagonals, which DLSS removed.
- Shadows "freak out", even looked at straight on.
- Most visible on the water's screen-space reflections: anything over the water reflects as a dithered fizzle
  ("pixel hole, pixel hole"), not as an identifiable reflection. With the same input, DLSS clearly cleaned this up.
- Distant tree edges crawl and never settle, overly sharp.
- Static textures fizzle indefinitely; with DLSS they settle after about half a second.
- Moving things: no clearer, a bit worse, very boily.
- On par with other packs' TAA, but not close to DLSS. dlaa-camera and dlaa-default are closer to each other than
  TAA is to either. No upside he could see.

**fsr3.** A clear step up over Photon's TAA, but not as clear a win as DLSS.
- It reduces boil and fizzle almost as much as DLSS. Water reflections that TAA made incomprehensible are clear.
- DLSS "dares to make sense of the noise" and commits better.
- FSR shows a bit more ghosting and trailing in reflections and shadows on fast camera flicks.
- About 95 % of the time better than TAA: slightly less clear, significantly more stable. He'd be comfortable
  shipping it as the default without an NVIDIA GPU.

### Effects scene

**dlaa-default** (no masks).
- Every effect except rain looks flawless: portal, fire, campfire smoke, lava fall and pool, waterfall. "If you
  didn't tell me they were not masked, I would not know."
- Rain is brutal and DLSS visibly struggles behind it if you pixel-peep (for example a block edge 12 chunks away with
  a drop passing through it), but not destructively. On land, walking around, it looks perfect. None of the other
  particles struggle, even with rain over them.
- "Stellar": everything except rain looks native, and rain is the pathological case but above playable.

**fsr3** (with masks). "A very clear one."
- Every effect except rain looks like it did with DLSS; he couldn't tell them apart.
- Rain has a different character. DLSS "goes for it" and smooths the rain, so it isn't jagged at all, but it smooths
  the whole screen a bit to account for it. FSR trusts the current frame where the rain is (masked), so the rain looks
  pixelated by comparison, with some jaggies, and the whole screen looks a little more pixelated. That would be bad in
  a AAA game, but Minecraft hides it well.
- For rain FSR is better on clarity and worse at being of a piece with the rest of the image. DLSS gives a more
  uniform picture with slightly more blur. Fine with either; he prefers DLSS.

**fsr3-nomask.**
- With masks is "a clear, not even close" win. Without them FSR is softer than DLSS and less clear than FSR with
  masks: "the worst of both worlds", soft and pixelated at the same time.

**photon-taa.**
- Looks like FSR without masks. In rain the whole screen is pixelated and soft at once: the picture softens, then the
  sharpening pulls detail back and leaves sharpening artifacts.
- It's there as the ground truth, what the pack intends, rather than as an option.
- "Use DLSS if you have an NVIDIA GPU. If you don't, use FSR, don't look back."

### Across modes

- **Particles over the hand:** first seen in fsr3 over the water, then checked in every mode. Small particles show on
  top of the first-person hand and held items with DLSS, FSR with and without masks, and Photon's TAA: water
  sparkles, rain splash droplets, campfire smoke, portal particles. The rain streaks themselves don't. Not an
  anti-aliasing issue; the player thinks it's the pack's shader interacting with some particle effects. Phone photos:
  `$T\round-m2-photos\fsr3-pink-dots-1.jpg`, `fsr3-pink-dots-2.jpg`, `fsr3-nomask-particles-over-hand.jpg`.
- **Rain in fog:** rain is less visible in fog at the horizon in every mode (DLSS, FSR, Photon's TAA). It's the pack's
  look.

## Log lines

The first launch (run J) had the only Photon remake. NGX loaded the pack's DLL in every launch. FSR started in J, K
and the by-eye session.

<details>
<summary>DLAA lines, runs J–N and the by-eye session</summary>

```
J  21:51:21 Perfect Presentation: off (config/create_galore_bridge-display.properties)
J  21:51:22 DLAA: made shaderpacks/Photon v1.3a + DLAA from photon_v1.3a + Clrwl_1.0.5.zip
J  21:52:42.199 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\<user>\Documents\Claude\MC-CREATE\instance\create-galore-dlss
J  21:52:44.602 DLAA: DLSS running at 2560x1440, preset default
J  21:53:18.127 DLAA: on for this shader pack, preset default                     (dlaa-default)
J  21:53:28.955 DLAA: on for this shader pack, preset default                     (dlaa-camera)
J  21:53:39.406 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
J  21:53:40.422 DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
J  21:53:40.548 DLAA: FSR 3.1 running at 2560x1440
J  21:53:52.678 DLAA: closed its DirectX device
K  21:56:06.189 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), ...
K  21:56:35.220 DLAA: on for this shader pack, preset default                     (dlaa-default)
K  21:56:41.433 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
K  21:56:41.476 DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
K  21:56:41.599 DLAA: FSR 3.1 running at 2560x1440
K  21:56:47.596 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)   (fsr3-nomask)
K  21:56:53.339 DLAA: on for this shader pack, preset default (debug view mask, DLSS itself not used)
K  21:57:00.842 DLAA: closed its DirectX device
L  21:59:10.342 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), ...
L  21:59:25.715 DLAA: on for this shader pack, preset default (debug view motion, DLSS itself not used)
L  21:59:41.810 DLAA: on for this shader pack, preset default (debug view residual, DLSS itself not used)
L  21:59:51.695 DLAA: closed its DirectX device
M  22:01:31.630 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), ...
M  22:01:47.958 DLAA: on for this shader pack, preset default (debug view motion, DLSS itself not used)
M  22:02:03.020 DLAA: on for this shader pack, preset default (debug view residual, DLSS itself not used)
M  22:02:10.939 DLAA: closed its DirectX device
N  22:03:50.638 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), ...
N  22:04:05.387 DLAA: on for this shader pack, preset default (debug view residual, DLSS itself not used)
N  22:04:23.387 DLAA: closed its DirectX device
by eye 22:08:34.681 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), ...
by eye 22:08:36.882 DLAA: DLSS running at 2560x1440, preset default
by eye 22:25:05.312 DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
by eye 22:25:05.899 DLAA: FSR 3.1 running at 2560x1440
by eye 22:53:42.069 DLAA: closed its DirectX device
```

Each launch also has two `DLAA: on for this shader pack, preset default` lines while loading, and the test kit's
`DLAA test:` lines (run, scene built, modes, finished). The full log of every launch is in `$T\round-m2-logs\` on the
PC.
</details>

**Colorwheel:** only INFO lines in every launch (Colorwheel Patcher listing the packs, `Bliss... already patched with
version 1.0.5`, `Created/Deleted pipeline data for ... IrisRenderingPipeline`). No warnings, no errors.
**Shaders:** no `ShaderCompileException` and no `Could not compile shader` in any launch.

## Also seen

- **One frame of K, `dlaa-default` f039 (of 40 saved frames in K), has two large solid blue rectangles** (top centre and
  right). They're not in any other frame or mode, and **not in that frame's raw DLSS input or output** (checked on the
  `raw` dumps). So they were drawn after DLSS, or the dump is from a neighbouring frame. The player reads them as raindrops
  that came right up to the camera in that one frame. Nothing like it showed in the by-eye session. The crops avoid
  those spots.
- `dlaa-camera` scores lower shimmer than `dlaa-default` in J's table, but that matches the softness the player saw: blurred
  moving things change less between frames.

## Crops

From the settled still frames (J: `moving-still-f119`; K: `effects-brief-f039`), 512x512, same spot in every mode.

| Folder | Crops | Modes |
| --- | --- | --- |
| `results/m2/J/` | `cogwheels` (with the fan), `bearing-cross`, `ship`, `walker-villager`, `walker-armor-stand`, `panda`, `chest` | photon-taa, dlaa-default, dlaa-camera, fsr3 |
| `results/m2/K/` | `portal`, `fire`, `lava` (fall and pool) | photon-taa, dlaa-default, fsr3, fsr3-nomask, debug-mask |
| `results/m2/K/ghostcheck/` | `dlaa-default-`, `fsr3-`, `fsr3-nomask-effects-brief.png` | input and output side by side per box |

The ghostcheck sheets are `ghostcheck.py`'s sheets at 1:1. The script draws them 3× bigger with nearest-neighbour
(15372x7164, 4+ MB each); every third pixel gives the same pixels at 5124x2388. The crops come to about 21 MB in all,
more than the runbook's estimate: rain-covered frames compress poorly in lossless PNG.

On the PC: the full screenshots in `$T\instance\screenshots\dlaa-test\`, the logs of every launch in
`$T\round-m2-logs\`, and the by-eye notes as written during the session in `$T\round-m2-byeye-notes.md`.
