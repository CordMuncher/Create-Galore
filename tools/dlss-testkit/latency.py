#!/usr/bin/env python3
"""Input latency per mode and viewpoint: python latency.py RUN_FOLDER [RUN_FOLDER ...] (standard library only).

Reads each run's manifest.json (runs L1 and L2 of Reflex 1, L3 and L4 of Pacer 1 in PC-RUNBOOK.md). For each mode and
viewpoint it prints the Bridge's numbers over the latency path, one row per time the mode played:
- input to GPU ms: the median and 90th percentile time from the game last reading input to the GPU finishing that
  frame (Bridge LatencyMeter; what the swap and the screen add comes on top, the same in every mode);
- GPU behind ms: the median time from the frame's end on the CPU to the GPU finishing it (how far the GPU trails);
- fps over the path;
- queue ms: the median time each frame's rendering start waited on the GPU behind earlier work (Bridge 1.107.0 on);
- gap ms: the median and 90th percentile time the GPU spent between frames (idle, plus the swap's own work);
- with the GPU pacer: its median wait just before rendering (ms), how late that wait ended (median, us) and its
  queue target at the window's end (us; the automatic one as measured);
- with Reflex markers: the driver's own report of its last 64 frames (input to GPU end, frame start to GPU end, GPU
  busy of GPU frame time). Its unit check should be about 1 (its times are microseconds).
Then the change of each mode's median against the run's first mode at the same viewpoint (lat-base in each run).
"""
import json
import statistics
import sys
from pathlib import Path


def fmt(value, digits=1):
    if value is None or (isinstance(value, float) and value != value):
        return "-"
    return f"{value:.{digits}f}"


def main(folders):
    if not folders:
        print(__doc__)
        return 1
    for folder in folders:
        manifest = json.loads((Path(folder) / "manifest.json").read_text(encoding="utf-8"))
        print(f"\n== {manifest.get('run')} ({manifest.get('outcome')}), {manifest.get('renderer')}, "
              f"Perfect Presentation {'on' if manifest.get('perfectPresentation') else 'off'}, "
              f"{'x'.join(str(v) for v in manifest.get('resolution', []))}")
        base = {}
        print(f"{'mode':16} {'viewpoint':9} {'input>GPU':>9} {'p90':>6} {'behind':>6} {'fps':>6} {'queue':>6} {'gap':>5} "
              f"{'gap90':>5} {'wait':>5} {'late':>5} {'tgt':>5}  {'drv in>GPU':>10} {'drv p90':>7} {'drv sim>GPU':>11} {'GPU busy/frame':>15} "
              f"{'unit':>5}  reflex")
        for mode in manifest.get("modes", []):
            for viewpoint, windows in (mode.get("latency") or {}).items():
                if isinstance(windows, dict):
                    windows = [windows]
                medians = []
                for w in windows:
                    medians.append(w.get("inputToGpuMs"))
                    busy = (f"{fmt(w.get('driverGpuActiveMs'))}/{fmt(w.get('driverGpuFrameMs'))}"
                            if "driverGpuActiveMs" in w else "-")
                    print(f"{mode['name']:16} {viewpoint:9} {fmt(w.get('inputToGpuMs')):>9} {fmt(w.get('inputToGpuP90Ms')):>6} "
                          f"{fmt(w.get('gpuBehindMs')):>6} {fmt(w.get('fps'), 0):>6} {fmt(w.get('gpuQueueMs')):>6} "
                          f"{fmt(w.get('gpuGapMs')):>5} {fmt(w.get('gpuGapP90Ms')):>5} {fmt(w.get('pacerWaitMs')):>5} "
                          f"{fmt(w.get('pacerLateUs'), 0):>5} {fmt(w.get('pacerTargetUs'), 0):>5}  {fmt(w.get('driverInputToGpuMs')):>10} "
                          f"{fmt(w.get('driverInputToGpuP90Ms')):>7} {fmt(w.get('driverSimToGpuMs')):>11} {busy:>15} "
                          f"{fmt(w.get('driverUnitCheck'), 2):>5}  {w.get('reflex', '')}")
                values = [m for m in medians if isinstance(m, (int, float)) and m == m]
                if not values:
                    continue
                median = statistics.median(values)
                base.setdefault(viewpoint, median)
                if len(values) > 1 or base[viewpoint] != median:
                    spread = f", repeats {min(values):.1f}-{max(values):.1f}" if len(values) > 1 else ""
                    print(f"{'':16} {viewpoint:9} median {median:.1f} ms, {median - base[viewpoint]:+.1f} ms against the first mode{spread}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
