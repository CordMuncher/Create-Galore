# DLAA round 1: results (RTX 4080 SUPER)

Run on Emre's PC on 2026-10-24, 13:56–14:58, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`), following `tools/dlss-testkit/PC-RUNBOOK.md` at `3e7a2ea`. All five runs finished. The decisions Emre made
during the session are listed under [Changes from the runbook](#changes-from-the-runbook).

## Summary

- **Step 4 checks passed.** The pack copy was made from Colorwheel's zip, NGX came up on the RTX 4080 SUPER, and DLSS ran.
  No `DLAA: stopped`, `DLAA: not available` or `Failed to create shader rendering pipeline` in any launch.
- **All runs A–E: outcome `run finished`.** `"dlaaActive": false` only for `photon-taa` (A and D), as intended. Every
  DLAA, sign-flip and debug mode ran with DLAA active.
- **DLAA against Photon's TAA (run A):** 7–15× less shimmer on the still path, 29–48 % less residue after the flick, and
  no frame-time cost (8.7–8.9 ms against 8.9–9.3 ms; the game ran at about 110 fps and was probably not GPU-bound).
  To the eye, DLAA has no grain or crawling on glass, bars, fences or leaves, but texel detail is visibly softer than
  Photon's TAA. Photon's TAA is sharpened by CAS, which the patched pack skips while DLSS runs. Edge contrast is lower
  by about a third.
- **Presets:** `default` resolves to K (`App hint Preset Unspecified or Overridden, using title default Preset K`). On
  every viewpoint, `default` and K give the same numbers and are the best on shimmer and residue. L and M have 2–5× the
  shimmer of K, slightly more residue and the same frame time. NGX applied each requested preset
  (`Using App hint Preset K/L/M`).
- **Jitter sign (run B):** the current sign (1,1) is the steadiest and sharpest. Flipping X or Y adds shimmer, and
  flipping both adds the most. The margins are small, but the order is the expected one.
- **DLSS inputs (run C)** look right. Motion is exactly zero with the camera still. It points the right way in the flick
  (back-turn is opposite), the pan and the strafe, and in the strafe it scales with distance. Depth covers Distant
  Horizons terrain.
- **Perfect Presentation (run D):** `NGX ready on Perfect Presentation's device (Perfect Presentation's DirectX
  device)`. The numbers are within a few hundredths of run A's. It needs two starts (see below).
- **Developer DLL (run E):** loaded from `$T\dlss-dev` through `create_galore_bridge.dlss.dllDir` and draws NVIDIA's
  watermark. **The watermark is upside down** (vertically flipped, left-to-right order intact), so DLSS sees the frame
  bottom-up (OpenGL row order). Runs B and C suggest the Y conventions are consistent anyway, but this deserves a look.
  Only the watermark appears, without the extra debug text the runbook expected.
- **Not DLAA:** the first launch of run A crashed in Cold Sweat / Cold Sweat Altitude while ticking the player. The
  relaunch was fine (details below).

## Setup

| | |
| --- | --- |
| GPU, driver | NVIDIA GeForce RTX 4080 SUPER, driver 32.0.16.2101 (621.01, 2026-10-19); monitor 2560x1440 at 239 Hz |
| Java | Temurin 21.0.12.1 (zip in `$T\tools\jdk21`) |
| Launcher | uv 0.13.5 (`uv tool run --python 3.13 portablemc@5.0.6`; uv used the Python 3.13 already installed, nothing was added to `tools\uv-python`) |
| Game | Minecraft 1.21.1, NeoForge 21.1.262, offline as `CGTester` |
| Pack | packwiz from `claude/project-thread-u2wmhh/pack/pack.toml` (235 mods) + `create-galore-dlss-testkit-1.0.0.jar` from `3e7a2ea` (before the FSR 3.1 modes of `1b3734c`) |
| Bridge, shaders | create-galore-bridge 1.89.0; Iris pack `Photon v1.3a + DLAA`, made from `photon_v1.3a + Clrwl_1.0.5.zip` |
| DLSS | pack `nvngx_dlss.dll` 310.9.16 (A–D); developer DLL 310.9.16 from `NVIDIA/DLSS@9981c63` (E) |
| Distant Horizons | 3.3.3, radius 64 chunks, 32 threads (changed for the test, see below), `FEATURES` / `SURFACE_THEN_CHUNKS` |
| Game settings | render distance 12, `pauseOnLostFocus:false`, `fullscreen:false`, `tutorialStep:none`, `--resolution 2560x1440`, `-Xmx10G -Xms4G` |
| Picture size | 2560x1421 in A–C (window fitted to the screen), 2560x1440 in D and E (see below) |

## Changes from the runbook

Emre decided each of these during the session:

1. **NVIDIA App DLSS override turned off.** At the first launch, NGX used the NVIDIA App's global override instead of
   the pack's DLL (`Feature dlss override enabled`, snippet
   `C:\ProgramData\NVIDIA\NGX\models\dlss\versions\20318480\files/160_E658700.bin version: 310.9.16`,
   `(DLAA) Using DRS Overridden Preset K`, `Override Reported: ModelPreset, Value: 11, Requested: Yes, Applied: Yes, Is Latest: Yes`).
   No per-app profile matched this Java (`NvAPI_DRS_FindApplicationByName -166`), so it came from the Global profile.
   It would have forced preset K for every mode and replaced the developer DLL. Emre switched it off in the NVIDIA App
   during the first hold. From the second launch on, NGX loads `instance\create-galore-dlss/nvngx_dlss.dll` (`APP_NAME = default`)
   and honours the app's preset. Only the step-4 launch ran with the override.
2. **Distant Horizons radius 256 → 64 and the "I paid for the whole CPU" thread preset**, in the test instance's
   `config/DistantHorizons.toml` only (the pack's config is unchanged). At radius 256 on 2 threads, filling the terrain
   would have taken hours. In DH 3.3.3 the preset isn't stored; it sets `numberOfThreads` to 100 % of logical CPUs (32
   here) and `threadRunTimeRatio` to 1.0, which is what the file now says. The far terrain therefore reaches about 1,000
   blocks, not 4,000. The runbook on the branch has since added the same two settings (`18ad134`, `d1e1f64`).
3. **Terrain made over three holds instead of one:**

   | Hold | DH settings | Time | DH database at the end |
   | --- | --- | --- | --- |
   | 1 (step 4) | 256 chunks, 2 threads | 14:04–14:14, closed early | 20.6 MB, still growing ~1.3 MB/min |
   | 2 | 64 chunks, 32 threads | 14:17–14:32 (full 900 s) | 213 MB, DH chat: `16,016 left` |
   | 3 | same | 14:34–14:38, closed early | 229.5 MB, last progress line 14:35:13, unchanged for 60 s |

   Emre turned on DH's `showGenerationProgress = "CHAT"` during hold 2. It stayed on for the runs, where chat is
   hidden. DH counts chunk area, not work, so its "left" figure drops in jumps; it started near 65,000 for radius 64,
   probably because the far LOD tiles snap to a grid wider than the radius. The database barely moved during the runs
   (224.78 MB before A, 224.89 MB after E).
4. **`tutorialStep:none`** added to `options.txt` before run A. Without it, the "Move with W, A, S and D" popup (drawn
   even with the HUD hidden) would have been in the top right of every screenshot, since the camera never moves by key.
5. **Closing the game myself**, through a normal window close (Minecraft saves), to end holds early and to restart run D.
6. **Computer use:** Emre allowed the session to look at the game window with computer use whenever needed. It was used
   only for screenshots (where the player was during the hold, DH's chat counter), never to click or type in the game.
7. **Two runs started twice:** run A (the first start crashed at world load, see below) and run D (the first start
   came up without Perfect Presentation and was closed before the world loaded).

## Step 4 log lines

First launch (override still on):

```
[14:03:03] [main/INFO]: DLAA: made shaderpacks/Photon v1.3a + DLAA from photon_v1.3a.zip
14:03:49.037 DLAA: made shaderpacks/Photon v1.3a + DLAA from photon_v1.3a + Clrwl_1.0.5.zip
14:04:05.955 DLAA: on for this shader pack, preset default
14:04:32.115 DLAA: on for this shader pack, preset default
14:04:44.079 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:04:45.646 DLAA: DLSS running at 2560x1421, preset default
14:14:18.955 DLAA: closed its DirectX device
14:14:20.647 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:14:20.702 DLAA: DLSS running at 2560x1421, preset default
```

The first copy is made from the plain zip because Colorwheel Patcher makes its zip only during mod setup (14:03:32).
The Bridge's refresh then remade the copy from Colorwheel's zip, as `PhotonPatcher` describes. Later launches found the
copy current and didn't remake it.

## Runs

| Run | Folder (`$T\instance\screenshots\dlaa-test\`) | Modes | Outcome | `dlaaActive: false` | Shots | Size |
| --- | --- | --- | --- | --- | --- | --- |
| A | `20261024-144214` | photon-taa, dlaa-default, dlaa-K, dlaa-L, dlaa-M | run finished | photon-taa | 405 | 2560x1421 |
| B | `20261024-144644` | dlaa-default, sign-flip-x, sign-flip-y, sign-flip-xy (quick) | run finished | none | 84 | 2560x1421 |
| C | `20261024-144934` | debug-motion, debug-depth, debug-color | run finished | none | 243 | 2560x1421 |
| D | `20261024-145407` | photon-taa, dlaa-default, dlaa-K (Perfect Presentation) | run finished | photon-taa | 243 | 2560x1440 |
| E | `20261024-145722` | dlaa-default (developer DLL, quick) | run finished | none | 21 | 2560x1440 |

Each mode took about 20 s (900 warm-up frames for the first mode, then about 110 fps). The full screenshots stay on the
PC for `compare.html`; the logs of every launch are in `$T\round1-logs\`.

### A: Photon TAA against DLAA presets

```
Run 20261024-144214 at 2560x1421, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.97 | 1.18 / 2.38% | 3.74 / 3.09 | 6.37 |
| dlaa-default | yes | 8.72 | 0.14 / 0.10% | 2.62 / 2.19 | 4.26 |
| dlaa-K | yes | 8.76 | 0.14 / 0.10% | 2.62 / 2.19 | 4.26 |
| dlaa-L | yes | 9.2 | 0.34 / 0.19% | 2.82 / 2.37 | 4.35 |
| dlaa-M | yes | 9.07 | 0.40 / 0.19% | 2.89 / 2.43 | 4.37 |

#### glass

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 9.27 | 0.77 / 1.48% | 2.67 / 1.90 | 4.40 |
| dlaa-default | yes | 8.86 | 0.05 / 0.00% | 1.38 / 1.04 | 3.10 |
| dlaa-K | yes | 8.77 | 0.05 / 0.00% | 1.38 / 1.04 | 3.10 |
| dlaa-L | yes | 8.88 | 0.22 / 0.10% | 1.56 / 1.18 | 3.22 |
| dlaa-M | yes | 8.82 | 0.24 / 0.11% | 1.66 / 1.23 | 3.24 |

#### canopy

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.92 | 1.55 / 3.86% | 2.82 / 7.09 | 8.88 |
| dlaa-default | yes | 8.84 | 0.22 / 0.14% | 1.71 / 3.98 | 5.56 |
| dlaa-K | yes | 8.75 | 0.22 / 0.14% | 1.71 / 3.98 | 5.56 |
| dlaa-L | yes | 8.95 | 0.48 / 0.46% | 2.12 / 4.97 | 6.06 |
| dlaa-M | yes | 8.84 | 0.57 / 0.61% | 2.21 / 5.04 | 6.14 |

`dlaa-default` and `dlaa-K` agree to two decimals but aren't byte-identical, so both crops are kept. After
`photon-taa`, `dlaa-default` reused the DLSS feature made at startup (no new `DLSS running` line), and the manifest
confirms it ran.

### B: jitter sign

```
Run 20261024-144644 at 2560x1421, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| dlaa-default | yes | 9.03 | 0.13 / 0.05% | 2.68 / 2.19 | 4.21 |
| sign-flip-x | yes | 8.85 | 0.19 / 0.19% | 2.69 / 2.31 | 4.19 |
| sign-flip-y | yes | 8.79 | 0.20 / 0.24% | 2.78 / 2.27 | 4.18 |
| sign-flip-xy | yes | 8.81 | 0.30 / 0.48% | 2.95 / 2.44 | 4.11 |

### C: DLSS inputs

```
Run 20261024-144934 at 2560x1421, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

| viewpoint | mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- | --- |
| shore | debug-motion | yes | 8.96 | 0.00 / 0.00% | 0.00 / 0.00 | 0.00 |
| shore | debug-depth | yes | 8.74 | 0.08 / 0.23% | 0.19 / 0.11 | 1.03 |
| shore | debug-color | yes | 8.63 | 3.20 / 11.02% | 4.10 / 3.87 | 5.99 |
| glass | debug-motion | yes | 8.7 | 0.00 / 0.00% | 0.00 / 0.00 | 0.00 |
| glass | debug-depth | yes | 8.71 | 0.18 / 0.45% | 0.31 / 0.25 | 1.16 |
| glass | debug-color | yes | 8.63 | 1.94 / 5.29% | 2.52 / 2.55 | 4.13 |
| canopy | debug-motion | yes | 8.73 | 0.00 / 0.00% | 0.00 / 0.00 | 0.00 |
| canopy | debug-depth | yes | 8.74 | 0.37 / 1.03% | 0.39 / 0.84 | 1.67 |
| canopy | debug-color | yes | 8.7 | 4.14 / 13.14% | 3.22 / 7.13 | 8.98 |

(metrics.py prints one table per viewpoint; they are merged here.) The 0.00 rows for `debug-motion` are correct, not
empty pictures. Those metrics only use still and settled frames, and the motion view is pure black there. The frames
taken while the camera moves light up:

| debug-motion shot | mean R / G / B | |
| --- | --- | --- |
| shore-still-f119, shore-flick-f059 | 0 / 0 / 0 | camera still |
| shore-flick-f064, f068 | 231 / 98 / 104, 233 / 90 / 96 | turning away: saturated horizontal motion, four quadrants (perspective) |
| shore-flick-f104 | 0 / 103 / 210 | turning back: the opposite channel |
| shore-pan-f050 = f100 | 244 / 50 / 60 | constant pan speed, same picture at both frames |
| shore-strafe-f050, f100 | 242 / 0 / 0 | sideways move: horizontal only, the scene's outline visible (nearer moves more) |

`debug-depth` covers Distant Horizons terrain at the canopy viewpoint (grey hills against a black sky). `debug-color`
is the jittered input, hence its high shimmer.

### D: Perfect Presentation

```
Run 20261024-145407 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 9.02 | 1.17 / 2.35% | 3.78 / 3.07 | 6.30 |
| dlaa-default | yes | 8.83 | 0.14 / 0.10% | 2.60 / 2.17 | 4.21 |
| dlaa-K | yes | 8.76 | 0.14 / 0.10% | 2.60 / 2.17 | 4.21 |

#### glass

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 9.17 | 0.74 / 1.42% | 2.66 / 1.82 | 4.29 |
| dlaa-default | yes | 8.77 | 0.05 / 0.00% | 1.37 / 0.98 | 3.04 |
| dlaa-K | yes | 8.68 | 0.05 / 0.00% | 1.37 / 0.98 | 3.04 |

#### canopy

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.95 | 1.55 / 3.87% | 2.81 / 7.09 | 8.78 |
| dlaa-default | yes | 8.78 | 0.22 / 0.13% | 1.70 / 4.00 | 5.49 |
| dlaa-K | yes | 8.8 | 0.22 / 0.13% | 1.70 / 4.00 | 5.49 |

The first start with `perfectPresentation=true` logged
`Perfect Presentation: NeoForge's early loading window already made an OpenGL game window. Turned earlyWindowControl off in config/fml.toml; Perfect Presentation takes effect after a restart.`
It was closed before the world loaded, and the second start had Perfect Presentation on:

```
14:52:42 Perfect Presentation: on, the game window will be shown through a DXGI swap chain
14:52:50.990 Perfect Presentation: DXGI swap chain ready (OpenGL on a hidden context)
14:53:30.823 Perfect Presentation: swap chain wait timed out
14:53:54.373 DLAA: NGX ready on Perfect Presentation's device (Perfect Presentation's DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
```

The one `swap chain wait timed out` came during loading and didn't repeat.

### E: developer DLL

```
Run 20261024-145722 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

#### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| dlaa-default | yes | 9.16 | 0.13 / 0.04% | 2.67 / 2.18 | 4.34 |

NGX's own log: `Build v310.9.16 CL 38955375`,
`Loaded from path "C:\Users\coolm\Documents\Claude\MC-CREATE\dlss-dev/nvngx_dlss.dll" by java.exe`, on a D3D11 device.
The watermark `DLSS SDK - DO NOT DISTRIBUTE - CONTACT NVIDIA TO OBTAIN DLLS FOR YOUR TITLE` is in the top right,
vertically flipped (`results/E/watermark-strip-dlaa-default.png`). No other debug text is drawn. The developer DLL's
other on-screen output may need switching on; this round didn't try.

## DLAA log lines

Every Bridge `DLAA:` and `Perfect Presentation:` line from each launch. The Bridge also relays NGX's own log into
the game log as `DLAA: NGX:` (200–480 lines per launch). Of those, only the preset lines are shown here; the rest are
DLL search, config section names and feature cache lookups, with no warnings or errors among them.

<details>
<summary>Hold 2 (14:15, override off from here on)</summary>

```
14:16:16.921 DLAA: on for this shader pack, preset default
14:16:33.277 DLAA: on for this shader pack, preset default
14:16:43.210 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:16:44.998 DLAA: DLSS running at 2560x1421, preset default
  NGX: feature dlss snippet: C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss/nvngx_dlss.dll version: 310.9.16
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
```

The end of this launch's log rolled off (the game keeps seven logs); it was closed by the hold at 14:31:55.
</details>

<details>
<summary>Hold 3 (14:32)</summary>

```
14:32:36 Perfect Presentation: off (config/create_galore_bridge-display.properties)
14:33:28.689 DLAA: on for this shader pack, preset default
14:33:48.204 DLAA: on for this shader pack, preset default
14:33:58.539 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:34:01.066 DLAA: DLSS running at 2560x1421, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
14:37:46.505 DLAA: closed its DirectX device
14:37:48.099 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:37:48.154 DLAA: DLSS running at 2560x1421, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
```
</details>

<details>
<summary>Run A, first start (14:38, crashed at world load)</summary>

```
14:38:27 Perfect Presentation: off (config/create_galore_bridge-display.properties)
14:39:14.448 DLAA: on for this shader pack, preset default
14:39:27.379 DLAA: on for this shader pack, preset default
14:39:27.850 [Server thread/FATAL] Preparing crash report ... crash-2026-10-24_14.39.27-server.txt
14:39:30.683 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:39:31.890 [Render thread/FATAL] Preparing crash report ... crash-2026-10-24_14.39.31-client.txt
```
</details>

<details>
<summary>Run A (14:40)</summary>

```
14:40:53 Perfect Presentation: off (config/create_galore_bridge-display.properties)
14:41:38.773 DLAA: on for this shader pack, preset default
14:41:51.589 DLAA: on for this shader pack, preset default
14:42:01.417 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:42:03.404 DLAA: DLSS running at 2560x1421, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
14:42:16.454 DLAA test: mode photon-taa
14:42:45.843 DLAA: on for this shader pack, preset default
14:43:07.611 DLAA: on for this shader pack, preset K
14:43:07.768 DLAA: DLSS running at 2560x1421, preset K
  NGX: (DLAA) Using App hint Preset K
14:43:29.232 DLAA: on for this shader pack, preset L
14:43:29.382 DLAA: DLSS running at 2560x1421, preset L
  NGX: (DLAA) Using App hint Preset L
14:43:51.164 DLAA: on for this shader pack, preset M
14:43:51.309 DLAA: DLSS running at 2560x1421, preset M
  NGX: (DLAA) Using App hint Preset M
14:44:14.857 DLAA: closed its DirectX device
14:44:16.519 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:44:16.553 DLAA: DLSS running at 2560x1421, preset M
  NGX: (DLAA) Using App hint Preset M
```
</details>

<details>
<summary>Run B (14:45)</summary>

```
14:45:22 Perfect Presentation: off (config/create_galore_bridge-display.properties)
14:46:08.627 DLAA: on for this shader pack, preset default
14:46:21.572 DLAA: on for this shader pack, preset default
14:46:32.511 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:46:34.029 DLAA: DLSS running at 2560x1421, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
14:46:45.984 DLAA: on for this shader pack, preset default
14:47:01.079 DLAA: on for this shader pack, preset default, jitter sign -1,1
14:47:08.175 DLAA: on for this shader pack, preset default, jitter sign 1,-1
14:47:15.300 DLAA: on for this shader pack, preset default, jitter sign -1,-1
14:47:24.122 DLAA: closed its DirectX device
14:47:25.730 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:47:25.776 DLAA: DLSS running at 2560x1421, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
```
</details>

<details>
<summary>Run C (14:48)</summary>

```
14:48:11 Perfect Presentation: off (config/create_galore_bridge-display.properties)
14:48:57.869 DLAA: on for this shader pack, preset default
14:49:10.627 DLAA: on for this shader pack, preset default
14:49:20.508 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:49:22.742 DLAA: DLSS running at 2560x1421, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
14:49:35.611 DLAA: on for this shader pack, preset default (debug view motion, DLSS itself not used)
14:50:04.298 DLAA: on for this shader pack, preset default (debug view depth, DLSS itself not used)
14:50:25.795 DLAA: on for this shader pack, preset default (debug view color, DLSS itself not used)
14:50:49.185 DLAA: closed its DirectX device
```
</details>

<details>
<summary>Run D, first start (14:51) and second start (14:52)</summary>

```
14:51:20 Perfect Presentation: NeoForge's early loading window already made an OpenGL game window. Turned earlyWindowControl off in config/fml.toml; Perfect Presentation takes effect after a restart.
14:52:06.819 DLAA: on for this shader pack, preset default
(closed at 14:52:18, before the world loaded)

14:52:42 Perfect Presentation: on, the game window will be shown through a DXGI swap chain
14:52:50.989 Perfect Presentation: NeoForge's OpenGL version check no longer reads the game window
14:52:50.990 Perfect Presentation: DXGI swap chain ready (OpenGL on a hidden context)
14:53:29.806 DLAA: on for this shader pack, preset default
14:53:30.823 Perfect Presentation: swap chain wait timed out
14:53:43.790 DLAA: on for this shader pack, preset default
14:53:54.373 DLAA: NGX ready on Perfect Presentation's device (Perfect Presentation's DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:53:56.230 DLAA: DLSS running at 2560x1440, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
14:54:38.741 DLAA: on for this shader pack, preset default
14:55:00.399 DLAA: on for this shader pack, preset K
14:55:00.560 DLAA: DLSS running at 2560x1440, preset K
  NGX: (DLAA) Using App hint Preset K
14:55:25.329 DLAA: NGX ready on Perfect Presentation's device (Perfect Presentation's DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
14:55:25.379 DLAA: DLSS running at 2560x1440, preset K
  NGX: (DLAA) Using App hint Preset K
```
</details>

<details>
<summary>Run E (14:55)</summary>

```
14:56:01 Perfect Presentation: off (config/create_galore_bridge-display.properties)
14:56:47.747 DLAA: on for this shader pack, preset default
14:57:00.768 DLAA: on for this shader pack, preset default
14:57:11.275 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\dlss-dev
14:57:13.233 DLAA: DLSS running at 2560x1440, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
14:57:23.775 DLAA: on for this shader pack, preset default
14:57:40.516 DLAA: closed its DirectX device
14:57:42.094 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\dlss-dev
14:57:42.148 DLAA: DLSS running at 2560x1440, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
```
</details>

**NGX warnings:** none. NGX's own logs contain the usual DLL search misses
(`failed to load NGXCore: 126 (...\tools\jdk21\bin\_nvngx.dll)` before it finds the driver store) and cache misses for
features the game doesn't use. The `[dlss_override]`, `[sl_*_override_0]` section names and the
`Override shared memory was opened/mapped` lines appear even with the NVIDIA App override off. NGX itself also updated
the access list of `C:\ProgramData\NVIDIA\NGX\models` on the first launch (`updated access control list for NGX cache`),
as it does for any DLSS game.

## Other things seen

- **NGX comes back up while the game quits.** In every launch whose last mode used DLSS, `closed its DirectX device`
  is followed 1.5 s later by a new `NGX ready` and `DLSS running` (with the last mode's preset), right before
  `Stopping!`. In run D, NGX is set up again on Perfect Presentation's device. Run C, whose last mode is a debug view,
  closes without that. It looks like leaving the world brings the DLAA pipeline back for a last frame. It's harmless,
  but it is an extra NGX init and DLSS feature at every quit.
- **Cold Sweat crash** (run A, first start, 14:39:27): `NullPointerException: Ticking player` in
  `AbstractTempCap.serializeModifiers` ← `Temperature.updateModifiers` ← coldsweat_altitude 0.8.1
  `ColdSweatCompat.removeAltitudeModifier` ← `AltitudeTemperatureManager.onPlayerTick`, Cold Sweat 2.4.4. The player
  was at 1029.5, 59, 160.5. The client crash at 14:39:31 is the follow-on (integrated server gone). The same world
  loaded fine before (hold 3) and after (run A again and all later runs). In hold 2, Emre had flown up to y 176 and back
  down, which may have left an altitude modifier in the saved player data. It's a pack issue worth a look, because a
  player on a server could hit it too. The crash reports are in `$T\round1-logs\A-crash1\`.
- **Perfect Presentation needs two starts** after switching it on: the first start only turns off NeoForge's early
  loading window (`earlyWindowControl = false` in `config/fml.toml`). From then on the window keeps the full
  `--resolution`. That's why D and E are 2560x1440 while A–C are 2560x1421 (with the early window on, the window was
  fitted to the screen). Each run compares only within itself, so the numbers aren't affected.
- **NGX ready line in Perfect Presentation mode** names "Perfect Presentation's device" where the GPU name normally is:
  `NGX ready on Perfect Presentation's device (Perfect Presentation's DirectX device)`. It's cosmetic.
- **The hold doesn't keep the player flying.** `-Dcreate_galore_dlss_testkit.hold` teleports to the first viewpoint
  in creative, but without flying, so the player sinks into the lake (to y 59). The runs themselves pin the camera
  (flying, frozen ticks); the shore shot is above the water.
- **Create Submarine's vortex shader doesn't compile under Iris on NVIDIA** (every launch): `Couldn't load the Vortex
  pixel light shader`, `create_submarine:vortex_pixel.fsh: 0(113) : error C0000: syntax error, unexpected '=', expecting ';'`.
- **Twilight Forest is referenced but not in the pack:** recipe errors for `create_galore:boss_compass/*` and
  `create_galore:space/stellar_core` (`Unknown registry key ... twilightforest:naga_scale` etc.). DH also can't read
  `twilightforest:canopy_chest` in chunk section [80, -4, -2] of the test world, far from the scene.

## Crops

512x512 from the settled still frame (f119), same spot in every mode, 6.9 MB in all:

| Folder | Crops | Shows |
| --- | --- | --- |
| `results/A/` | `shore-fence-glass-*`, `glass-panes-*`, `canopy-leaves-*` × 5 modes | fence, chain, clear and coloured glass; glass panes and iron bars; leaf edges against far terrain and sky |
| `results/B/` | `shore-fence-glass-*` × 4 modes | the same spot with each jitter sign |
| `results/C/` | `shore-pan-*` × 3 views | the debug views at pan frame 50 (motion is black on still frames) |
| `results/D/` | `shore-fence-glass-*` × 3 modes | through Perfect Presentation (2560x1440 framing) |
| `results/E/` | `shore-fence-glass-dlaa-default`, `watermark-strip-dlaa-default` | developer DLL; the upside-down watermark (720x70) |

In the crops, Photon's TAA keeps crisp texel edges but has grain and speckle on glass, bars and leaves. DLAA is
clean, but texel edges are softer. L and M look close to K at this size; the metrics separate them.

## For round 2

- Look at the row order DLSS gets (the upside-down watermark). If the Y flip is intended, a note in the Bridge would
  save the next person the question.
- If the softer look is unwanted, running Photon's CAS after DLSS is the obvious knob to try.
- Runbook: check the NVIDIA App's DLSS override first; add `tutorialStep:none`; start run D twice, or set
  `earlyWindowControl = false` before A so that all runs have the same picture size. (DH radius 64 and 32 threads are
  in it now; from scratch the hold takes about 15–20 minutes with them.)
- This PC's `instance\saves\dlaa-test` now has the DH database (229.5 MB for radius 64), so a later round here can skip
  the hold. It's too big for git (GitHub's 100 MB file limit) if it should travel with the world zip.
