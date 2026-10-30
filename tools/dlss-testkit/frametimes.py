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
- GPU wait ms: PresentMon's median time the GPU sat idle while the frame was in progress (Presenter 2: a hand-off
  between OpenGL and DirectX that stalls the GPU shows here);
- until displayed ms: PresentMon's median time from Present() to the frame on screen;
- present mode: the presentation mode most frames had, with its share ("Hardware: Independent Flip" is what G-SYNC
  and tearing need; "Composed: Flip" means the desktop compositor copies every frame);
- shown every ms: the median, 10th and 90th percentile time between two frames reaching the screen, generated frames
  included (PresentMon's MsBetweenDisplayChange): with frame generation about half the rendered frame time;
- pair ratio: for each two frames shown one after the other, the longer one's time on screen over the shorter one's,
  median and 90th percentile. About 1 is even pacing; 2 or more is a frame shown briefly next to one held long (the
  "double tap" reported for FSR frame generation).
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
GPU_WAIT = ("MsGPUWait", "GPUWait")
UNTIL_DISPLAYED = ("MsUntilDisplayed", "UntilDisplayed")
PRESENT_MODE = ("PresentMode",)
DISPLAY_CHANGE = ("MsBetweenDisplayChange", "msBetweenDisplayChange", "DisplayedTime")
START = ("CPUStartQPCTimeInMs", "CPUStartQPCTime", "CPUStartTime", "TimeInSeconds")


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
                (("frame", FRAME), ("cpu", CPU_BUSY), ("gpu", GPU_BUSY), ("start", START), ("wait", GPU_WAIT),
                 ("shown", UNTIL_DISPLAYED), ("mode", PRESENT_MODE), ("display", DISPLAY_CHANGE))}
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
            frames.append((start, frame, number(row, cols["cpu"]), number(row, cols["gpu"]), number(row, cols["wait"]),
                           number(row, cols["shown"]), row.get(cols["mode"]) if cols["mode"] else None,
                           number(row, cols["display"])))
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

    pooled = defaultdict(lambda: {"frame": [], "cpu": [], "gpu": [], "bound": [], "medians": [], "wait": [], "shown": [],
                                  "mode": defaultdict(int), "display": [], "pairs": []})
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
            # Frames that reached the screen, in order, within this segment only (no pair across two segments).
            shown = [f[7] for f in frames[lo:hi] if f[7] is not None and f[7] > 0]
            pooled[key]["display"].extend(shown)
            pooled[key]["pairs"].extend(max(a, b) / min(a, b) for a, b in zip(shown, shown[1:]))
        for key, rows in per_run.items():
            p = pooled[key]
            p["frame"].extend(r[1] for r in rows)
            p["cpu"].extend(r[2] for r in rows if r[2] is not None)
            p["gpu"].extend(r[3] for r in rows if r[3] is not None)
            p["bound"].extend(r[3] > 0.9 * r[1] for r in rows if r[3] is not None and r[1] > 0)
            p["wait"].extend(r[4] for r in rows if r[4] is not None)
            p["shown"].extend(r[5] for r in rows if r[5] is not None)
            for r in rows:
                if r[6]:
                    p["mode"][r[6]] += 1
            if rows:
                p["medians"].append(statistics.median(r[1] for r in rows))

    if not pooled:
        return
    print("## Frame times over the camera paths\n")
    print("| viewpoint | mode | frames | frame ms (median / p90) | repeats | CPU busy ms | GPU busy ms | GPU-bound "
          "| GPU wait ms | until displayed ms | present mode | shown every ms (median / p10 / p90) | pair ratio (median / p90) |")
    print("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for key in order:
        p = pooled[key]
        if not p["frame"]:
            print(f"| {key[0]} | {key[1]} | 0 | - | - | - | - | - | - | - | - | - | - |")
            continue
        wait = f"{statistics.median(p['wait']):.2f}" if p["wait"] else "-"
        shown = f"{statistics.median(p['shown']):.2f}" if p["shown"] else "-"
        if p["mode"]:
            top = max(p["mode"], key=p["mode"].get)
            present = f"{top} ({100 * p['mode'][top] / sum(p['mode'].values()):.0f}%)"
        else:
            present = "-"

        repeats = (f"{min(p['medians']):.2f} to {max(p['medians']):.2f}" if len(p["medians"]) > 1 else "-")
        cpu = f"{statistics.median(p['cpu']):.2f}" if p["cpu"] else "-"
        gpu = f"{statistics.median(p['gpu']):.2f}" if p["gpu"] else "-"
        bound = f"{100 * sum(p['bound']) / len(p['bound']):.0f}%" if p["bound"] else "-"
        display = (f"{statistics.median(p['display']):.2f} / {percentile(p['display'], 10):.2f} / "
                   f"{percentile(p['display'], 90):.2f}" if p["display"] else "-")
        pair = f"{statistics.median(p['pairs']):.2f} / {percentile(p['pairs'], 90):.2f}" if p["pairs"] else "-"
        print(f"| {key[0]} | {key[1]} | {len(p['frame'])} | {statistics.median(p['frame']):.2f} / "
              f"{percentile(p['frame'], 90):.2f} | {repeats} | {cpu} | {gpu} | {bound} | {wait} | {shown} | {present} "
              f"| {display} | {pair} |")
    print()


if __name__ == "__main__":
    main()
