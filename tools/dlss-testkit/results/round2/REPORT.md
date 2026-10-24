# DLAA round 2: FSR 3 on the RTX 4080 SUPER (stopped at run F, twice)

Run on Emre's PC on 2026-10-24 by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE` (`$T`),
following the round 2 section of `tools/dlss-testkit/PC-RUNBOOK.md`. Attempt 1 at 15:39 used the Bridge from `7d4787c`,
attempt 2 at 16:09 the one from `29249ed`. Both stopped in run F's `fsr3` mode, so runs G (FSR jitter sign),
H (FSR debug view) and I (FSR with Perfect Presentation) weren't started: all three need FSR running.

## Summary

- **Attempt 2 (`29249ed`): FSR 3 gets past the function check, but its `luma_pyramid` pass doesn't link on NVIDIA:**

  ```
  DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: FSR 3 pass luma_pyramid did not link: Compute info
  ------------
  (0) : error C7624: OpenGL does not allow greater than 8 image uniforms
  ```

  The runbook says to stop when a pass fails, so the round stopped here. The pass compiled; the link step rejected it.
- **Why:** NVIDIA's OpenGL allows 8 image uniforms per compute shader, and `luma_pyramid` declares 9:
  `rw_spd_global_atomic` (r32ui), `rw_frame_info`, `rw_spd_mip0` to `rw_spd_mip5` and `rw_farthest_depth_mip1`
  (`fsr3/fsr3upscaler/ffx_fsr3upscaler_callbacks_gl.h`, bindings 2–10 in `ffx_fsr3upscaler_luma_pyramid_pass.glsl`).
  Mesa in the cloud allows more, so it never showed. Counting image bindings in every pass, only `luma_pyramid` is over
  8; `shading_change_pyramid` has 7, `prepare_inputs` 5, the rest 3 or fewer. Getting it to 8 would be enough. One way
  is the SPD counter `rw_spd_global_atomic` as a buffer (an SSBO with an atomic uint) instead of an image. Whether the
  other passes link on NVIDIA isn't known yet, because the Bridge stops at the first failure.
- **Attempt 1 (`7d4787c`):** `FSR 3 needs OpenGL 4.5`, because Minecraft's context reports `OpenGL Version: 3.3.0
  NVIDIA 621.01` and the Bridge checked `OpenGL45`. `29249ed` replaced that check, and attempt 2 confirms it works here.
- **Checks that passed (both attempts):**
  - The NVIDIA App override is still off. NGX loads the pack's `nvngx_dlss.dll` 310.9.16, with title default preset K.
  - No DLSS set-up while quitting: `closed its DirectX device` comes right before `Stopping!`, with no new `ready` line.
  - The picture is 2560x1440 throughout, with `earlyWindowControl = false` from the start.
  - `photon-taa` and `dlaa-default` reproduce round 1.
- **Not tested** (they needed later runs): the GPU name on the NGX line with Perfect Presentation (run I), and the
  flying hold. No hold was needed, because the world kept round 1's terrain.

## Setup

Same PC, folder, tools and instance as round 1 (see `../REPORT.md`). Only the jars changed:

| | Attempt 1 (15:39) | Attempt 2 (16:09) |
| --- | --- | --- |
| Bridge `create-galore-bridge-1.89.0.jar` | `7d4787c`, 1,139,792 bytes | `29249ed`, 1,140,278 bytes (FSR checks the functions it calls) |
| Test kit `create-galore-dlss-testkit-1.0.0.jar` | `7d4787c`, 27,095 bytes | same |

OpenGL as the game logs it: `OpenGL Vendor: NVIDIA Corporation`, `OpenGL Renderer: NVIDIA GeForce RTX 4080
SUPER/PCIe/SSE2`, `OpenGL Version: 3.3.0 NVIDIA 621.01`. Checked before each attempt: `dlaa=true`,
`perfectPresentation=false`, `earlyWindowControl = false`, `tutorialStep:none`, Distant Horizons at radius 64 on 32
threads, DH database 224.9 MB (round 1's terrain, unchanged by either attempt).

## Run F, attempt 2 (`29249ed`)

Folder `$T\instance\screenshots\dlaa-test\20261024-161101`, outcome `run finished`, 243 shots, 2560x1440.
`"dlaaActive": false` for `photon-taa` (intended) and `fsr3` (FSR not available, `"engine": "fsr3"`).

```
Run 20261024-161101 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 9.09 | 1.17 / 2.34% | 3.77 / 3.07 | 6.33 |
| dlaa-default | yes | 8.94 | 0.14 / 0.09% | 2.60 / 2.17 | 4.24 |
| fsr3 | no | 8.82 | 1.21 / 2.53% | 3.79 / 3.08 | 6.31 |

### glass

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.83 | 0.75 / 1.43% | 2.71 / 1.84 | 4.32 |
| dlaa-default | yes | 8.76 | 0.05 / 0.00% | 1.40 / 1.02 | 3.05 |
| fsr3 | no | 8.66 | 0.74 / 1.37% | 2.69 / 1.75 | 4.37 |

### canopy

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.77 | 1.55 / 3.86% | 2.80 / 7.09 | 8.77 |
| dlaa-default | yes | 8.9 | 0.22 / 0.13% | 1.69 / 4.00 | 5.49 |
| fsr3 | no | 8.66 | 1.54 / 3.79% | 2.81 / 6.57 | 8.88 |

The `fsr3` rows are Photon's own TAA (the fallback the log names) and say nothing about FSR. There is no FSR frame
time and no FSR picture, so no crops (round 1 has the `photon-taa` and `dlaa-default` crops of the same spots).

```
16:09:39 Perfect Presentation: off (config/create_galore_bridge-display.properties)
16:10:25.047 DLAA: on for this shader pack, preset default
16:10:37.768 DLAA: on for this shader pack, preset default
16:10:47.595 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
  NGX: feature dlss snippet: C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss/nvngx_dlss.dll version: 310.9.16
16:10:49.202 DLAA: DLSS running at 2560x1440, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
16:11:03.383 DLAA test: mode photon-taa
16:11:33.052 DLAA: on for this shader pack, preset default
16:11:33.071 DLAA test: mode dlaa-default
16:11:54.766 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
16:11:54.779 DLAA test: mode fsr3
16:11:54.841 DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: FSR 3 pass luma_pyramid did not link: Compute info
------------
(0) : error C7624: OpenGL does not allow greater than 8 image uniforms
16:12:15.170 DLAA test: run finished; screenshots in C:\Users\coolm\Documents\Claude\MC-CREATE\instance\screenshots\dlaa-test\20261024-161101
16:12:18.260 DLAA: closed its DirectX device
16:12:18.268 Stopping!
```

No NGX warnings, no `DLAA: stopped`, no `did not compile`. The `GL_INVALID_VALUE ... Invalid texture format` debug
messages at startup were already in round 1 and come before the Bridge's DLAA lines.

## Run F, attempt 1 (`7d4787c`)

Folder `$T\instance\screenshots\dlaa-test\20261024-154042`, outcome `run finished`, 243 shots, 2560x1440, same modes
and the same picture as attempt 2. The `fsr3` mode again fell back to Photon's TAA, this time because of the version
check:

```
15:41:35.051 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
15:41:35.062 DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: FSR 3 needs OpenGL 4.5
```

Its numbers (shore: photon-taa 1.17 / 2.36 %, dlaa-default 0.14 / 0.10 %, fsr3 1.21 / 2.55 %; glass and canopy alike)
are within a few hundredths of attempt 2 and are kept in `$T\round2-logs\metrics-F-attempt1.md`.

## To finish round 2

With `luma_pyramid` at 8 image uniforms or fewer (and a rebuilt Bridge jar on the branch), runs F–I take about 15
minutes on this PC with the instance as it is: copy the new Bridge jar, then run F, G, H and I as the runbook lists
them. If another pass then fails on NVIDIA, its `DLAA:` line will name it. The logs of both attempts are in
`$T\round2-logs\`.
