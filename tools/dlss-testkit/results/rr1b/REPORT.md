# Ray Reconstruction 1b: Ray Reconstruction crashes on DirectX 11 and runs on DirectX 12

A PC session on 2026-10-27, 17:00–17:08, run by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`).
- **What it followed:** "Ray Reconstruction 1b" in `tools/dlss-testkit/PC-RUNBOOK.md` at `e07e695c`, with the dev
  build Bridge 1.125.0-rr.1 (pack 0.6.1-rr.1), the branch's test kit and `rr-probe.exe` from the same commit.
- **Part 1** (the probe) ran on its own; **part 2** (in the game) with Emre at the keyboard.
- NVIDIA's two RR DLLs are the ones Ray Reconstruction 1 fetched (310.9.16, hashes as there). The NVIDIA app had no
  overrides on.

## Summary

- **The probe names the cause: DirectX 11.**
  - **Ray Reconstruction on DirectX 12 runs:** `d3d12-game`, RR as the game calls it, ran three frames to a clean
    picture (output mean 0.4964 with the release DLL, 0.4961 with the dev one).
  - **On DirectX 11 every variant crashes:** the game's inputs, no hit distance and matrices, roughness in its own
    image, NGX parameters of its own, even sizes, and a feature level 11_1 device. All six crash on `evaluate frame 1`
    with `code 0xC0000005 ... reading address 0000000000000034`, inside `nvngx_dlssd.dll`, with both DLLs.
  - **They are Ray Reconstruction 1's crashes exactly:** the same instruction address as the game's crash with each
    DLL (`00007FF8156C0EA2` release, `00007FFFFA77AFE2` dev).
  - **Plain DLSS runs on both** DirectX 11 and 12 (mean 0.4977).
  - **NGX doesn't refuse RR on DirectX 11.** It reports `Ray Reconstruction available 1, init result 0x00000001`,
    creates the feature, chooses preset F, and then reads a null pointer on the first evaluate.
- **By the runbook: RR needs our DLSS on DirectX 12.** The two test switches in this build (`no-hit-distance`,
  `own-parameters`) match variants that crash too, so part 2b was skipped.
- **DLSS without RR runs on the pilot build** (part 2a, which Ray Reconstruction 1 never reached).
  - `DLAA: DLSS running at 2544x1401 (full resolution), preset K, the pack's exposure`.
  - Emre: "the image looks like I would expect. I don't see any regressions."
- **A runbook fix.** Part 1's `Copy-Item $T\instance\create-galore-dlss\nvngx_dlss.dll $T\rr1b\rel\, $T\rr1b\dev\`
  fails in PowerShell: `Copy-Item`'s `-Destination` takes one path. Without `nvngx_dlss.dll` the two plain-DLSS
  variants fail (`the feature isn't available`). I copied it into each folder on its own and ran both probes again.
  The RR variants came out the same either way.

## Part 1: the probe

- **Folders:** `$T\rr1b\rel` and `$T\rr1b\dev` each hold NVIDIA's `nvngx_dlssd.dll` (release 48,343,664 bytes, dev
  93,504,624 bytes) and the instance's `nvngx_dlss.dll` (58,956,912 bytes).
- **Runs:** `rr-probe.exe $T\rr1b\rel $T\rr1b\probe-rel`, then `... dev ... probe-dev`, about 19 s each.
- No window or Windows prompt came up.

**Release DLL** (`summary.txt`, whole; the paths are under `$T`):

```
rr-probe: DLSS Ray Reconstruction alone, nvngx DLLs from $T\rr1b\rel
d3d11-dlss               OK: ran 3 frames; output red mean 0.4977 over 2544x1401, 0 pixels not finite
d3d11-game               CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FF8156C0EA2, reading address 0000000000000034 ($T\rr1b\rel\nvngx_dlssd.dll)
d3d11-no-hit-distance    CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FF8156C0EA2, reading address 0000000000000034 ($T\rr1b\rel\nvngx_dlssd.dll)
d3d11-roughness-apart    CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FF8156C0EA2, reading address 0000000000000034 ($T\rr1b\rel\nvngx_dlssd.dll)
d3d11-own-parameters     CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FF8156C0EA2, reading address 0000000000000034 ($T\rr1b\rel\nvngx_dlssd.dll)
d3d11-even-size          CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FF8156C0EA2, reading address 0000000000000034 ($T\rr1b\rel\nvngx_dlssd.dll)
d3d11-level-11-1         CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FF8156C0EA2, reading address 0000000000000034 ($T\rr1b\rel\nvngx_dlssd.dll)
d3d12-dlss               OK: ran 3 frames; output red mean 0.4977 over 2544x1401, 0 pixels not finite
d3d12-game               OK: ran 3 frames; output red mean 0.4964 over 2544x1401, 0 pixels not finite
```

**Dev DLL** (`summary.txt`, whole):

```
rr-probe: DLSS Ray Reconstruction alone, nvngx DLLs from $T\rr1b\dev
d3d11-dlss               OK: ran 3 frames; output red mean 0.4977 over 2544x1401, 0 pixels not finite
d3d11-game               CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFFA77AFE2, reading address 0000000000000034 ($T\rr1b\dev\nvngx_dlssd.dll)
d3d11-no-hit-distance    CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFFA77AFE2, reading address 0000000000000034 ($T\rr1b\dev\nvngx_dlssd.dll)
d3d11-roughness-apart    CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFFA77AFE2, reading address 0000000000000034 ($T\rr1b\dev\nvngx_dlssd.dll)
d3d11-own-parameters     CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFFA77AFE2, reading address 0000000000000034 ($T\rr1b\dev\nvngx_dlssd.dll)
d3d11-even-size          CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFFA77AFE2, reading address 0000000000000034 ($T\rr1b\dev\nvngx_dlssd.dll)
d3d11-level-11-1         CRASHED at: evaluate frame 1; code 0xC0000005 at 00007FFFFA77AFE2, reading address 0000000000000034 ($T\rr1b\dev\nvngx_dlssd.dll)
d3d12-dlss               OK: ran 3 frames; output red mean 0.4977 over 2544x1401, 0 pixels not finite
d3d12-game               OK: ran 3 frames; output red mean 0.4961 over 2544x1401, 0 pixels not finite
```

The first release run, before `nvngx_dlss.dll` was in the folder, had the same seven RR lines. Its two DLSS lines read
`FAILED: failed at the feature isn't available (0x00000000)`. That run is kept as `probe-rel-without-nvngx_dlss`.

### The crashed variants

- **Each one's own lines are the same up to the crash:**
  - `step: device`;
  - `info: DirectX 11 feature level b000` (`b100` for `d3d11-level-11-1`);
  - `step: NGX init`;
  - `info: Ray Reconstruction available 1, init result 0x00000001, needs a newer driver 0 (minimum 537.2)`;
  - `step: textures`, then `step: create Ray Reconstruction, preset F`, then `step: evaluate frame 1`;
  - and the crash line from the summary.
- **Their last ten `ngx:` lines are the same in all six,** with both DLLs, apart from times. The one other
  difference: in the dev `d3d11-no-hit-distance`, the `app id` line says `241534722` instead of `141959980`, and it
  crashed all the same. The release DLL's lines, from `d3d11-game`:

```
ngx: [tid][DLSSCubinKernelMap::InitCubins:208] Setting DLSS Base Cubins for standalone RR
ngx: [tid][DldnCubinKernelMap::InitCubins:156] Setting DLDN Engine Pre / Post Cubins
ngx: [tid][DldnCubinKernelMap::InitCubins:188] Setting DLDN Engine Pre / Post Cubins
ngx: [NVSDK_NGX_CreateFeature_Validate:762] app id is 141959980
ngx: [tid][NgxRayReconstruction::FillCreationParams::<lambda_...>::operator ():1807] Info: (DLAA) Using Default Overriden Preset Preset_F
ngx: [tid][NgxRayReconstruction::FillCreationParams::<lambda_...>::operator ():1807] Info: (Quality) Using Default Overriden Preset Preset_F
ngx: [tid][NgxRayReconstruction::FillCreationParams::<lambda_...>::operator ():1807] Info: (Balanced) Using Default Overriden Preset Preset_F
ngx: [tid][NgxRayReconstruction::FillCreationParams::<lambda_...>::operator ():1807] Info: (Perf) Using Default Overriden Preset Preset_F
ngx: [tid][NgxRayReconstruction::FillCreationParams::<lambda_...>::operator ():1807] Info: (UltraPerf) Using Default Overriden Preset Preset_F
ngx: [tid][NgxRayReconstruction::FillCreationParams::<lambda_...>::operator ():1807] Info: (UltraQuality) Using Default Overriden Preset Preset_F
```

The dev DLL's ten are the same, except that the first one is `Setting DLDN Debug Cubins`. The dev DLL logs that one
cubin line more.

### The variants that ran

- `d3d11-dlss` and `d3d12-dlss`: `OK: ran 3 frames; output red mean 0.4977 over 2544x1401, 0 pixels not finite`.
- `d3d12-game`: `OK: ran 3 frames; output red mean 0.4964 ...` (release) and `0.4961` (dev). Its lines go `step:
  evaluate frame 1`, `evaluate frame 2`, `evaluate frame 3`, `step: read back`, `step: release`.

## Part 2: in the game

- **Settings,** as in Ray Reconstruction 1: `dlaa=dlss`, `dlaaPreset=default`, `perfectPresentation=true`,
  `presenter=dx12`, `reflex=on`, `gpuPacing=off`, `fpsCounter=detailed`, `frameGen=off`, `frameGenType=dlss`, no RR
  lines; `options.txt` `fullscreen:false`, `maxFps:260`, `enableVsync:false`.
- **The release DLL in the instance.** The launch was at 17:03.
- **a. DLSS without RR: runs, and looks right.**
  - **Start:** `Frame generation: ready in the DirectX 12 presenter ..., off; Reflex goes through Streamline`, then
    `Reflex: started through Streamline, as DLSS Frame Generation needs (...)`.
  - **In `dlaa-test`:** `DLAA: DLSS Ray Reconstruction can run, nvngx_dlssd.dll 310.9.16 (made with 310.9.16) (not
    asked for)` (17:04:54.061), then `DLAA: DLSS running at 1272x701 to 2544x1401 (Performance, 50%), preset K, the
    pack's exposure` (17:04:57.402).
  - **The render scale was still Ray Reconstruction 1's Performance.** It lives in the shader pack's options file
    `shaderpacks\Photon v1.3a + DLAA.txt` (`TAAU=true`, `TAAU_RENDER_SCALE=0.50`), which Ray Reconstruction 1's
    backups didn't cover.
  - **At full resolution, as in Frame generation 3:** Emre set Native in the Upscaling tab: `DLAA: on for this shader
    pack, engine dlss, DLSS preset K (set to default), full resolution`, then `DLAA: DLSS running at 2544x1401 (full
    resolution), preset K, the pack's exposure` (17:05:52.421).
  - Emre: "Yep, the image looks like I would expect. I don't see any regressions."
  - **WARN:** only `Frame generation (native): 184 log lines dropped` and the usual `irons_lib:steve` texture line.
    No crash.
- **b. Skipped,** as the runbook says. No DirectX 11 variant ran, so `dlaaRrTest` would crash again.

## Afterwards

- **Settings put back.**
  - `create_galore_bridge-display.properties`, `options.txt`, `DistantHorizons.toml`, `iris.properties`, `fml.toml` and
    `sodium-options.json` were copied back from `$T\rr1b\*.before`, and each was checked with `cmp`.
  - That leaves frame generation off, Perfect Presentation off, windowed, 120 fps cap with vsync.
  - No `dlaaRrTest` line; the test kit's json was deleted.
- **NVIDIA's `nvngx_dlssd.dll` was removed from the instance.**
- **The shader pack's options file** went back to what it held before Ray Reconstruction 1: Upscaling 1's leftover,
  `TAAU_RENDER_SCALE=0.33` with TAAU off, so full resolution.
- **The instance keeps** the pilot Bridge 1.125.0-rr.1 and its test kit.
- **On the PC in `$T\rr1b`:** both probe outputs (`probe-rel`, `probe-dev`, `probe-rel-without-nvngx_dlss`), with NGX's
  logs, part 2's game log and the session notes. They name the user folder, so they aren't committed.

What comes of it, by the runbook: only `d3d12-game` runs, so RR needs our DLSS on DirectX 12.
