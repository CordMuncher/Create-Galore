# DLAA round 2: FSR 3 next to DLAA (RTX 4080 SUPER)

Run on Emre's PC on 2026-10-24, 16:22–16:33, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`), following the round 2 section of `tools/dlss-testkit/PC-RUNBOOK.md` with the Bridge from `3dadca5`. Runs F, G,
H and I all finished. Two earlier attempts stopped at run F; see [Earlier attempts](#earlier-attempts).

## Summary

- **FSR 3.1 runs on the 4080.** Every pass compiled and linked with NVIDIA's compiler. Each run with an `fsr3` mode has
  the three lines the runbook asks for:
  - `DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)`
  - `DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`
  - `DLAA: FSR 3.1 running at 2560x1440`

  None of the runs has `FSR 3 not available`, `DLAA: stopped`, `did not compile` or `did not link`. There are no
  OpenGL debug messages at all once FSR runs. `"dlaaActive": true` for every FSR mode.
- **FSR against DLAA and Photon's TAA (run F):** on these three viewpoints FSR's numbers are close to DLAA's and far
  from Photon's TAA.
  - Shimmer on the still path: FSR 0.15 / 0.07 / 0.20 levels, DLAA 0.14 / 0.05 / 0.22, TAA 1.17 / 0.76 / 1.55 (shore /
    glass / canopy).
  - Share of pixels changing by more than 8 levels: FSR is higher at the canopy (0.24 % against DLAA's 0.13 %).
  - Residue two frames after the first turn (70 v 99): FSR slightly lower (2.57 / 1.27 / 1.63 against 2.61 / 1.36 /
    1.70). After the turn back (110 v 159): slightly higher (2.31 / 1.03 / 4.25 against 2.18 / 1.01 / 4.00).
  - Edge contrast: FSR is higher (4.48 / 3.20 / 5.89 against 4.23 / 3.06 / 5.49). As `metrics.py` now says, that alone
    doesn't mean sharper.

  To the eye, in the crops and at 3× zoom: FSR and DLAA are both clean, without the grain Photon's TAA has. FSR's texel
  edges (iron bars, leaf outlines) are a touch crisper. I saw no FSR artifacts in the still frames or in the first frame
  after the flick (f070), where both look alike. This is a few frames, not a verdict; Emre judges the look in
  `compare.html`.
- **Frame time:** FSR 8.76–8.83 ms, DLAA 8.75–8.93 ms, Photon's TAA 8.86–9.10 ms (run F). That's within noise of each
  other. The game runs at about 110 fps and seems limited by something other than the GPU, so these frame times don't
  show what each technique costs the GPU.
- **FSR jitter sign (run G):** the unflipped sign is the steadiest (0.14 / 0.07 % against 0.16–0.19 for the flips;
  flipping both is worst), as the cloud found.
- **FSR debug view (run H):** works on NVIDIA. FSR's picture sits in the middle with its buffers around it. The motion
  panel is flat grey with the camera still and shows the same four-quadrant pattern as the Bridge's motion view during
  the flick; the masks fill in during motion.
- **Perfect Presentation (run I):** the NGX line now names the GPU:
  `NGX ready on NVIDIA GeForce RTX 4080 SUPER (Perfect Presentation's DirectX device)`. FSR runs in OpenGL with Perfect
  Presentation showing the frames, with the same numbers as run F within noise. It came up on the first start, since
  `earlyWindowControl = false` was set beforehand.
- **No setup while quitting:** in all four runs there's no new `ready` line between the last `... running` line and
  `Stopping!`.

## Setup

Same PC, folder, tools and instance as round 1 (see `../REPORT.md`), with:

| | |
| --- | --- |
| Bridge | `pack/mods/create-galore-bridge-1.89.0.jar` from `3dadca5`, 1,140,705 bytes |
| Test kit | `tools/dlss-testkit/dist/create-galore-dlss-testkit-1.0.0.jar` from `7d4787c`, 27,095 bytes |
| OpenGL | `OpenGL Version: 3.3.0 NVIDIA 621.01` (what Minecraft's context reports), driver 621.01 |
| Picture | 2560x1440 in every run |

Checked before the runs: `dlaa=true`, `perfectPresentation=false` (`true` for run I only), `earlyWindowControl =
false`, `tutorialStep:none`, Distant Horizons at radius 64 on 32 threads, DH database 224.9 MB (round 1's terrain, so no
hold; unchanged by the runs). The NVIDIA App's DLSS override is still off: NGX loads the pack's `nvngx_dlss.dll`
310.9.16 and uses title default preset K for `dlaa-default`.

## Runs

| Run | Folder (`$T\instance\screenshots\dlaa-test\`) | Modes | Outcome | `dlaaActive: false` | Shots |
| --- | --- | --- | --- | --- | --- |
| F | `20261024-162410` | photon-taa, dlaa-default, fsr3 | run finished | photon-taa | 243 |
| G | `20261024-162723` | fsr3, fsr3-flip-x, fsr3-flip-y, fsr3-flip-xy (quick) | run finished | none | 84 |
| H | `20261024-162957` | fsr3-debug (quick) | run finished | none | 21 |
| I | `20261024-163215` | dlaa-default, fsr3 (Perfect Presentation) | run finished | none | 162 |

### F: Photon TAA, DLAA, FSR 3

```
Run 20261024-162410 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 9.1 | 1.17 / 2.35% | 3.76 / 3.06 | 6.33 |
| dlaa-default | yes | 8.93 | 0.14 / 0.10% | 2.61 / 2.18 | 4.23 |
| fsr3 | yes | 8.83 | 0.15 / 0.11% | 2.57 / 2.31 | 4.48 |

#### glass

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.96 | 0.76 / 1.45% | 2.65 / 1.85 | 4.33 |
| dlaa-default | yes | 8.78 | 0.05 / 0.00% | 1.36 / 1.01 | 3.06 |
| fsr3 | yes | 8.76 | 0.07 / 0.01% | 1.27 / 1.03 | 3.20 |

#### canopy

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.86 | 1.55 / 3.86% | 2.80 / 7.09 | 8.78 |
| dlaa-default | yes | 8.75 | 0.22 / 0.13% | 1.70 / 4.00 | 5.49 |
| fsr3 | yes | 8.79 | 0.20 / 0.24% | 1.63 / 4.25 | 5.89 |

(`dlaa-default` here is DLSS preset K. The column header says "DLAA ran" for any engine in the Bridge's DLAA slot.)

### G: FSR jitter sign

```
Run 20261024-162723 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| fsr3 | yes | 9.21 | 0.14 / 0.07% | 2.59 / 2.33 | 4.46 |
| fsr3-flip-x | yes | 8.93 | 0.16 / 0.12% | 2.55 / 2.35 | 4.26 |
| fsr3-flip-y | yes | 8.9 | 0.18 / 0.17% | 2.56 / 2.34 | 4.26 |
| fsr3-flip-xy | yes | 8.83 | 0.19 / 0.18% | 2.64 / 2.42 | 4.07 |

### H: FSR debug view

```
Run 20261024-162957 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| fsr3-debug | yes | 9.32 | 0.19 / 0.37% | 1.75 / 1.03 | 2.60 |

The numbers describe the debug layout, not a picture to judge. The layout has a top strip, which is dark when still
and lights green in parts during the flick. Below it is FSR's own output, and at the bottom three panels: motion
(grey when still, coloured quadrants in the flick) and two red mask panels.

### I: Perfect Presentation

```
Run 20261024-163215 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| dlaa-default | yes | 9.06 | 0.13 / 0.04% | 2.62 / 2.17 | 4.21 |
| fsr3 | yes | 8.96 | 0.15 / 0.11% | 2.56 / 2.31 | 4.48 |

#### glass

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| dlaa-default | yes | 8.83 | 0.05 / 0.01% | 1.31 / 1.04 | 3.07 |
| fsr3 | yes | 8.92 | 0.07 / 0.01% | 1.26 / 1.03 | 3.20 |

#### canopy

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| dlaa-default | yes | 8.82 | 0.20 / 0.12% | 1.52 / 4.73 | 5.47 |
| fsr3 | yes | 8.94 | 0.20 / 0.24% | 1.63 / 4.25 | 5.89 |

FSR's rows match run F almost exactly. `dlaa-default`'s canopy residue moved more between runs (1.70 / 4.00 in F,
1.52 / 4.73 here). That's a hint of the run-to-run noise in that column, worth keeping in mind when comparing FSR's
4.25 against DLAA's 4.00.

## DLAA log lines

Each launch also relays NGX's log into the game log as `DLAA: NGX:`. The only lines from it shown here are the DLL it
loaded and the preset, identical in all four launches:
`feature dlss snippet: ...\instance\create-galore-dlss/nvngx_dlss.dll version: 310.9.16` and
`(DLAA) App hint Preset Unspecified or Overridden, using title default Preset K`. No NGX warnings.

<details>
<summary>Run F (16:22)</summary>

```
16:22:47 Perfect Presentation: off (config/create_galore_bridge-display.properties)
16:23:34.098 DLAA: on for this shader pack, preset default
16:23:46.962 DLAA: on for this shader pack, preset default
16:23:56.737 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
16:23:58.634 DLAA: DLSS running at 2560x1440, preset default
16:24:12.472 DLAA test: mode photon-taa
16:24:42.079 DLAA: on for this shader pack, preset default
16:24:42.095 DLAA test: mode dlaa-default
16:25:03.798 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
16:25:03.813 DLAA test: mode fsr3
16:25:05.116 DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
16:25:05.255 DLAA: FSR 3.1 running at 2560x1440
16:25:25.617 DLAA test: run finished; screenshots in C:\Users\coolm\Documents\Claude\MC-CREATE\instance\screenshots\dlaa-test\20261024-162410
16:25:28.723 DLAA: closed its DirectX device
16:25:28.733 Stopping!
```
</details>

<details>
<summary>Run G (16:25)</summary>

```
16:25:57 Perfect Presentation: off (config/create_galore_bridge-display.properties)
16:26:44.254 DLAA: on for this shader pack, preset default
16:26:57.190 DLAA: on for this shader pack, preset default
16:27:07.260 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
16:27:08.964 DLAA: DLSS running at 2560x1440, preset default
16:27:24.992 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
16:27:25.016 DLAA test: mode fsr3
16:27:25.050 DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
16:27:25.249 DLAA: FSR 3.1 running at 2560x1440
16:27:39.732 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing), jitter sign -1,1
16:27:46.815 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing), jitter sign 1,-1
16:27:53.918 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing), jitter sign -1,-1
16:27:59.677 DLAA test: run finished; screenshots in C:\Users\coolm\Documents\Claude\MC-CREATE\instance\screenshots\dlaa-test\20261024-162723
16:28:02.806 DLAA: closed its DirectX device
16:28:02.826 Stopping!
```
</details>

<details>
<summary>Run H (16:28)</summary>

```
16:28:34 Perfect Presentation: off (config/create_galore_bridge-display.properties)
16:29:20.318 DLAA: on for this shader pack, preset default
16:29:33.241 DLAA: on for this shader pack, preset default
16:29:43.566 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
16:29:45.605 DLAA: DLSS running at 2560x1440, preset default
16:29:58.699 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing) (FSR's debug view)
16:29:58.727 DLAA test: mode fsr3-debug
16:29:58.766 DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
16:29:58.954 DLAA: FSR 3.1 running at 2560x1440
16:30:11.329 DLAA test: run finished; screenshots in C:\Users\coolm\Documents\Claude\MC-CREATE\instance\screenshots\dlaa-test\20261024-162957
16:30:14.435 DLAA: closed its DirectX device
16:30:14.445 Stopping!
```
</details>

<details>
<summary>Run I (16:30, Perfect Presentation)</summary>

```
16:30:53 Perfect Presentation: on, the game window will be shown through a DXGI swap chain
16:31:01.662 Perfect Presentation: NeoForge's OpenGL version check no longer reads the game window
16:31:01.662 Perfect Presentation: DXGI swap chain ready (OpenGL on a hidden context)
16:31:38.601 DLAA: on for this shader pack, preset default
16:31:39.619 Perfect Presentation: swap chain wait timed out
16:31:52.348 DLAA: on for this shader pack, preset default
16:32:01.985 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (Perfect Presentation's DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
16:32:04.042 DLAA: DLSS running at 2560x1440, preset default
16:32:16.851 DLAA: on for this shader pack, preset default
16:32:16.883 DLAA test: mode dlaa-default
16:32:46.454 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
16:32:46.470 DLAA test: mode fsr3
16:32:46.523 DLAA: FSR 3.1 ready on NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2
16:32:47.010 DLAA: FSR 3.1 running at 2560x1440
16:33:07.911 DLAA test: run finished; screenshots in C:\Users\coolm\Documents\Claude\MC-CREATE\instance\screenshots\dlaa-test\20261024-163215
16:33:10.966 Stopping!
```
</details>

## Other things seen

- **Perfect Presentation's `swap chain wait timed out`** came once during loading in run I, as in round 1's run D, and
  not again.
- **Create Submarine's vortex shader** still doesn't compile under Iris on NVIDIA (every launch, as in round 1).

## Crops

512x512 from run F's settled still frame (f119) for `photon-taa`, `dlaa-default` and `fsr3`, under `results/round2/F/`,
2.8 MB in all. They cover the same spots as round 1 (`shore-fence-glass`, `glass-panes`, `canopy-leaves`), with the
corners moved by 1.3 % because round 1's frames were 2560x1421: (1021,146), (753,233), (1908,243).

## Earlier attempts

Both earlier attempts ran run F to the end, but FSR fell back to Photon's TAA, so G–I weren't started. Their logs and
metrics are in `$T\round2-logs\`.

| Attempt | Bridge | Stopped at | Line |
| --- | --- | --- | --- |
| 1 (15:39) | `7d4787c` | FSR start | `DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: FSR 3 needs OpenGL 4.5`. Minecraft's context reports `3.3.0 NVIDIA 621.01`, and the check used `GLCapabilities.OpenGL45`. Fixed in `29249ed` (check the functions FSR calls). |
| 2 (16:09) | `29249ed` | `luma_pyramid` link | `FSR 3 pass luma_pyramid did not link: Compute info` / `(0) : error C7624: OpenGL does not allow greater than 8 image uniforms`. The pass declared 9 images (NVIDIA counts declared ones, used or not). Fixed in `3dadca5` (only the SPD mip it uses, 4 images). |
