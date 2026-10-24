# DLAA test kit

A test tool for the Bridge's DLAA (NVIDIA DLSS at full resolution in place of Photon's TAA). It is never part of the
pack: it goes only into a separate test instance.

- `src/`: a small client mod. In the DLAA test world, `/dlaatest run` flies the camera along fixed paths at fixed
  viewpoints and saves screenshots at the same frames for every anti-aliasing mode. The paths count frames, not
  seconds, and the shader pack's clock and frame counter count the same frames during a run, so the waving leaves,
  the exposure and the jitter repeat from mode to mode.
- `compare.html`: the side-by-side viewer. Open it in a browser, pick the run folder
  (`screenshots/dlaa-test/<run>` in the test instance), then compare any two modes with a split slider, side by side,
  flip (Space) or a difference view, zoomed and panned together. The slider at the bottom scrubs through the frames of
  a path (the flick path has 13, the still path 8 in a row).

## Build

```
tools/bridge-mod/build.sh /path/to/server      # once, caches the client jars
tools/dlss-testkit/build.sh /path/to/server    # -> tools/dlss-testkit/build/create-galore-dlss-testkit-1.0.0.jar
```

## Run

1. Copy the jar into the test instance's `mods/` (`dist/` has a built one), and unzip `world/dlaa-test.zip` into its
   `saves/`: the test world (seed 20261024, the scene on the lakeshore near 1029 64 162, terrain generated for render
   distance 12 around it).
2. Pick the "Photon v1.3a + DLAA" shader pack once (the Bridge makes it when `dlaa=true` in
   `config/create_galore_bridge-display.properties`).
3. Open the world and type `/dlaatest run` (or `/dlaatest run photon-taa+dlaa-K` for some modes only). Keep the
   window visible (it needn't have focus) until the chat says the run finished; `/dlaatest stop` ends it early.

Or start the game with `-Dcreate_galore_dlss_testkit.autorun=dlaa-test`: it opens the world, runs, and closes. Adding
`-Dcreate_galore_dlss_testkit.hold=SECONDS` only stands at the first viewpoint that long and closes, so chunks and
Distant Horizons' far terrain exist before the first run.

`config/create_galore_dlss_testkit.json` (written on the first run) lists the modes, viewpoints and paths. A mode is
`{"name", "dlaa", "preset"}` with an optional `"debugView": "motion" | "depth" | "color"`, which shows that DLSS input
instead of DLSS (any GPU), and an optional `"jitterSign": "-1,1"` that flips the jitter handed to DLSS (the
`sign-flip-*` modes check which way is right). `/dlaatest where` prints the camera for adding a viewpoint. A run puts
the Bridge's display settings back when it ends.

`metrics.py RUN_FOLDER` (Pillow and numpy) prints, per viewpoint and mode, the frame time, the shimmer between the
still path's last 8 frames, the smear left two frames after the flick, and the edge contrast.
`PC-RUNBOOK.md` is the round 1 recipe for an RTX PC.

Each run folder has `manifest.json`: the modes (with the frame time on the still path and whether DLAA really ran),
the viewpoints, the capture frames of each path and every screenshot.
