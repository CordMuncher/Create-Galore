#!/usr/bin/env python3
"""Frame times per viewpoint and mode: python frametimes.py RUN_FOLDER [PRESENTMON.csv] [RUN_FOLDER [PRESENTMON.csv] ...]
(standard library only).

Reads the timeline in each run's manifest.json: when each segment of the run started and ended. Without a PresentMon
capture it prints the test kit's own frame time per segment (its length over its frame count). With one (taken with
--qpc_time_ms, so its times are on the clock the test kit writes on Windows), it puts each frame in the segment it
falls in and prints, per viewpoint and mode, over the camera paths (not the warm-up and settle segments):
- frame ms: the median and the 90th percentile of the time between frames;
- CPU busy and GPU busy ms: PresentMon's medians (how long the CPU worked on a frame, how long the GPU did);
- GPU-bound: the share of frames where the GPU was busy for more than 90% of the frame time. Near 0 means the CPU
  sets the pace and anything that only costs GPU time is free; near 100% means the GPU sets it.
Several runs of the same modes (repeats) are pooled, and "repeats" shows the lowest and highest median among them,
which is how much the numbers move from run to run.
Screenshots hold up their frame, so measure in runs with "screenshots": false (the medians shrug off a few anyway).
"""
import bisect
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

FRAME = ("MsBetweenAppStart", "MsBetweenPresents", "FrameTime")
CPU_BUSY = ("MsCPUBusy", "CPUBusy")
GPU_BUSY = ("MsGPUBusy", "GPUBusy")
START = ("CPUStartTime", "CPUStartQPCTime", "TimeInSeconds")


def pick(header, names):
    return next((n for n in names if n in header), None)


def number(row, column):
    if column is None:
        return None
    try:
        value = float(row[column])
    except (KeyError, ValueError):
        return None
    return value if value == value else None


def percentile(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


def load_presentmon(path):
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        header = reader.fieldnames or []
        cols = {name: pick(header, names) for name, names in
                (("frame", FRAME), ("cpu", CPU_BUSY), ("gpu", GPU_BUSY), ("start", START))}
        if cols["frame"] is None or cols["start"] is None:
            sys.exit(f"{path}: no frame time or start time column in {header}")
        frames = []
        for row in reader:
            start = number(row, cols["start"])
            frame = number(row, cols["frame"])
            if start is None or frame is None:
                continue
            if cols["start"] == "TimeInSeconds":
                start *= 1000
            frames.append((start, frame, number(row, cols["cpu"]), number(row, cols["gpu"])))
    frames.sort()
    return frames, cols


def main():
    args = [Path(a) for a in sys.argv[1:]]
    if not args:
        sys.exit(__doc__)
    runs = []
    for arg in args:
        if arg.suffix.lower() == ".csv":
            if not runs or runs[-1][1] is not None:
                sys.exit(f"{arg}: a PresentMon file goes right after its run folder")
            runs[-1] = (runs[-1][0], arg)
        else:
            runs.append((arg, None))

    pooled = defaultdict(lambda: {"frame": [], "cpu": [], "gpu": [], "bound": [], "medians": []})
    order = []
    for run, capture in runs:
        manifest = json.loads((run / "manifest.json").read_text())
        timeline = [s for s in manifest.get("timeline", []) if "endMs" in s]
        if not timeline:
            sys.exit(f"{run}: no timeline in manifest.json (a test kit from before frame times were measured)")
        print(f"## {run.name}: {manifest.get('renderer', '?')}, {manifest.get('shaderPack', '?')}, "
              f"{'x'.join(map(str, manifest.get('resolution', [])))} ({manifest.get('outcome', '?')})\n")
        if capture is None:
            print("| segment | mode | frames | ms per frame |")
            print("| --- | --- | --- | --- |")
            for s in timeline:
                print(f"| {s['segment']} | {s['mode']} | {s['frames']} | "
                      f"{(s['endMs'] - s['startMs']) / max(s['frames'], 1):.2f} |")
            print()
            continue
        frames, cols = load_presentmon(capture)
        span = (timeline[0]["startMs"], timeline[-1]["endMs"])
        inside = sum(1 for f in frames if span[0] <= f[0] < span[1])
        print(f"{capture.name}: {len(frames)} frames, {inside} inside the run "
              f"(columns {', '.join(f'{k}={v}' for k, v in cols.items())})")
        if inside < 0.5 * sum(s["frames"] for s in timeline):
            print("**Most of the run has no frames in the capture: was it taken with --qpc_time_ms, of the game's"
                  " process, while the run ran?**")
        print()
        starts = [f[0] for f in frames]
        per_run = defaultdict(list)
        for s in timeline:
            if s.get("path") is None:
                continue
            key = (s["viewpoint"], s["mode"])
            if key not in order:
                order.append(key)
            lo = bisect.bisect_left(starts, s["startMs"])
            hi = bisect.bisect_left(starts, s["endMs"])
            per_run[key].extend(frames[lo:hi])
        for key, rows in per_run.items():
            p = pooled[key]
            p["frame"].extend(r[1] for r in rows)
            p["cpu"].extend(r[2] for r in rows if r[2] is not None)
            p["gpu"].extend(r[3] for r in rows if r[3] is not None)
            p["bound"].extend(r[3] > 0.9 * r[1] for r in rows if r[3] is not None and r[1] > 0)
            if rows:
                p["medians"].append(statistics.median(r[1] for r in rows))

    if not pooled:
        return
    print("## Frame times over the camera paths\n")
    print("| viewpoint | mode | frames | frame ms (median / p90) | repeats | CPU busy ms | GPU busy ms | GPU-bound |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for key in order:
        p = pooled[key]
        if not p["frame"]:
            print(f"| {key[0]} | {key[1]} | 0 | - | - | - | - | - |")
            continue
        repeats = (f"{min(p['medians']):.2f} to {max(p['medians']):.2f}" if len(p["medians"]) > 1 else "-")
        cpu = f"{statistics.median(p['cpu']):.2f}" if p["cpu"] else "-"
        gpu = f"{statistics.median(p['gpu']):.2f}" if p["gpu"] else "-"
        bound = f"{100 * sum(p['bound']) / len(p['bound']):.0f}%" if p["bound"] else "-"
        print(f"| {key[0]} | {key[1]} | {len(p['frame'])} | {statistics.median(p['frame']):.2f} / "
              f"{percentile(p['frame'], 90):.2f} | {repeats} | {cpu} | {gpu} | {bound} |")
    print()


if __name__ == "__main__":
    main()
