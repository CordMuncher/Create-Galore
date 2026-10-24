# DLAA round 2: FSR 3 on the RTX 4080 SUPER (stopped at run F)

Run on Emre's PC on 2026-10-24, 15:35–15:45, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`), following the round 2 section of `tools/dlss-testkit/PC-RUNBOOK.md` at `7d4787c`.

## Summary

- **FSR 3 didn't start on the 4080, so the round stopped after run F.** In the `fsr3` mode the Bridge logged
  `DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: FSR 3 needs OpenGL 4.5`, one of the
  runbook's stop conditions. Runs G (FSR jitter sign), H (FSR debug view) and I (FSR with Perfect Presentation) all
  need FSR running, so they weren't started. No FSR shader was compiled, so nothing about NVIDIA's compiler is known yet.
- **Cause:** Minecraft's OpenGL context reports version 3.3 on NVIDIA (`OpenGL Version: 3.3.0 NVIDIA 621.01`), and the
  Bridge requires `GL.getCapabilities().OpenGL45` (`tools/bridge-mod/.../dlss/Dlss.java:355`), which LWJGL derives from
  that reported version. NVIDIA reports the version the game asked for; Mesa (the cloud rig) reports its newest core
  version for the same request, which is why it passed there. The GPU and driver do support 4.6.
- **4.5 functions work in this context anyway:** the Bridge's DLSS path already calls the GL45C functions
  `glCreateTextures`, `glTextureParameteri` (`DlssDevice.java:183–207`) and `glGetTextureLevelParameteri`
  (`DlssPass.java:147–159`) on it, every launch, without error. Iris also compiles Photon's GLSL 4.x shaders here. So the check, not the hardware, is what stops FSR. Checking
  for the functions FSR uses (for example LWJGL's `caps.glDispatchCompute`, `caps.glCreateTextures`,
  `caps.glBindTextureUnit` being non-zero, or the matching `ARB_*` extensions) instead of `OpenGL45` would let it try.
  Whether NVIDIA's compiler then accepts FSR's GLSL is the open question the runbook already flags.
- **Checks that passed:** the NVIDIA App override is still off (NGX loads the pack's `nvngx_dlss.dll` 310.9.16, title
  default preset K). The game no longer sets DLSS up again while quitting (`closed its DirectX device`, then
  `Stopping!`, with no new `ready` line). With `earlyWindowControl = false` from the start, the picture is 2560x1440.
- **Not tested** (they needed runs that didn't happen): the GPU name on the NGX line with Perfect Presentation (run I)
  and the flying hold (no hold was needed; the world kept round 1's terrain).

## Setup

Same PC, folder, tools and instance as round 1 (see `../REPORT.md`), with the two jars from `7d4787c`:

| | |
| --- | --- |
| Bridge | `pack/mods/create-galore-bridge-1.89.0.jar`, 1,139,792 bytes (FSR 3 engine, quit fix) |
| Test kit | `tools/dlss-testkit/dist/create-galore-dlss-testkit-1.0.0.jar`, 27,095 bytes (`fsr3*` modes, flying hold) |
| OpenGL | `OpenGL Vendor: NVIDIA Corporation`, `OpenGL Renderer: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2`, `OpenGL Version: 3.3.0 NVIDIA 621.01`; Iris: `GPU: NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2 (Supports OpenGL 3.3.0 NVIDIA 621.01)` |

Checked before run F: `dlaa=true`, `perfectPresentation=false`, `earlyWindowControl = false`, `tutorialStep:none`,
Distant Horizons at radius 64 on 32 threads, DH database 224.9 MB (round 1's terrain, so no hold).

## Run F: Photon TAA, DLAA, FSR 3

Folder `$T\instance\screenshots\dlaa-test\20261024-154042`, outcome `run finished`, 243 shots, 2560x1440.
`"dlaaActive": false` for `photon-taa` (intended) and `fsr3` (FSR not available). The manifest lists `fsr3` with
`"engine": "fsr3"`.

```
Run 20261024-154042 at 2560x1440, NVIDIA GeForce RTX 4080 SUPER/PCIe/SSE2, Photon v1.3a + DLAA (run finished)
```

### shore

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 9.03 | 1.17 / 2.36% | 3.72 / 3.07 | 6.34 |
| dlaa-default | yes | 8.75 | 0.14 / 0.10% | 2.61 / 2.19 | 4.24 |
| fsr3 | no | 8.74 | 1.21 / 2.55% | 3.79 / 3.10 | 6.31 |

### glass

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 8.82 | 0.76 / 1.47% | 2.66 / 1.86 | 4.34 |
| dlaa-default | yes | 8.77 | 0.05 / 0.00% | 1.38 / 1.02 | 3.07 |
| fsr3 | no | 8.66 | 0.75 / 1.41% | 2.64 / 1.76 | 4.39 |

### canopy

| mode | DLAA ran | frame ms | shimmer (levels / >8) | residue (70 v 99 / 110 v 159) | edge contrast |
| --- | --- | --- | --- | --- | --- |
| photon-taa | no | 9.29 | 1.55 / 3.86% | 2.80 / 7.09 | 8.77 |
| dlaa-default | yes | 8.78 | 0.22 / 0.13% | 1.70 / 4.00 | 5.49 |
| fsr3 | no | 8.68 | 1.54 / 3.79% | 2.81 / 6.57 | 8.88 |

The `fsr3` rows are Photon's own TAA (the fallback the log names), within run-to-run noise of `photon-taa`; they say
nothing about FSR. `photon-taa` and `dlaa-default` reproduce round 1 at the new picture size (round 1 run A at the
shore: 1.18 and 0.14 shimmer). There is no FSR frame time to compare.

No crops: FSR didn't produce a picture, and round 1 already has `photon-taa` and `dlaa-default` crops of the same spots.

## DLAA log lines (run F)

```
15:39:18 Perfect Presentation: off (config/create_galore_bridge-display.properties)
15:40:05.396 DLAA: on for this shader pack, preset default
15:40:20.725 DLAA: on for this shader pack, preset default
15:40:30.923 DLAA: NGX ready on NVIDIA GeForce RTX 4080 SUPER (its own DirectX device), nvngx_dlss.dll from C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss
  NGX: feature dlss snippet: C:\Users\coolm\Documents\Claude\MC-CREATE\instance\create-galore-dlss/nvngx_dlss.dll version: 310.9.16
15:40:33.002 DLAA: DLSS running at 2560x1440, preset default
  NGX: (DLAA) App hint Preset Unspecified or Overridden, using title default Preset K
15:40:44.503 DLAA test: mode photon-taa
15:41:13.392 DLAA: on for this shader pack, preset default
15:41:13.401 DLAA test: mode dlaa-default
15:41:35.051 DLAA: on for this shader pack, AMD FSR 3.1 in place of DLSS (testing)
15:41:35.061 DLAA test: mode fsr3
15:41:35.062 DLAA: FSR 3 not available, the shader pack keeps its own anti-aliasing: FSR 3 needs OpenGL 4.5
15:41:55.378 DLAA test: run finished; screenshots in C:\Users\coolm\Documents\Claude\MC-CREATE\instance\screenshots\dlaa-test\20261024-154042
15:41:58.474 DLAA: closed its DirectX device
15:41:58.483 Stopping!
```

No NGX warnings, no `did not compile`, no `DLAA: stopped`. The `GL_INVALID_VALUE ... Invalid texture format` debug
messages at startup (21 here) were already in round 1 (25 in run A) and come before the Bridge's DLAA lines.

## To finish round 2

Once the Bridge's FSR check accepts NVIDIA's 3.3-reported context (and a rebuilt jar is on the branch), runs F–I take
about 15 minutes on this PC with the instance as it is: copy the new Bridge jar, then run F, G, H and I as the runbook
lists them. The logs of this attempt are in `$T\round2-logs\`.
