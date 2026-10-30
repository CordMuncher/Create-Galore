#!/usr/bin/env python3
"""GPU utilization and clocks per mode: python gpustats.py RUN_FOLDER NVIDIA_SMI.csv [RUN_FOLDER NVIDIA_SMI.csv ...]
(standard library only; run it on the PC that took the samples, since nvidia-smi writes local time).

The samples come from nvidia-smi running beside the game:
  nvidia-smi --query-gpu=timestamp,utilization.gpu,clocks.gr,clocks.mem,power.draw,pstate --format=csv,nounits -lms 250 -f FILE
Each sample goes into the segment of the run's timeline (manifest.json, startEpochMs/endEpochMs) it falls in. Per
viewpoint and mode, over the camera paths, it prints the median GPU utilization (%), graphics and memory clock (MHz)
and power (W), and the share of samples in P0 (the full-speed power state). A presenter that leaves the GPU idle part
of each frame shows as lower utilization, and the driver may answer that with lower clocks.
"""
import bisect
import csv
import json
import statistics
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path


def column(header, prefix):
    return next((h for h in header if h.strip().startswith(prefix)), None)


def load(path):
    with open(path, newline="") as f:
        reader = csv.reader(f, skipinitialspace=True)
        header = next(reader)
        names = {k: column(header, p) for k, p in (("time", "timestamp"), ("util", "utilization.gpu"),
                                                   ("gr", "clocks.current.graphics"), ("mem", "clocks.current.memory"),
                                                   ("power", "power.draw"), ("pstate", "pstate"))}
        index = {k: header.index(v) if v else None for k, v in names.items()}
        samples = []
        for row in reader:
            try:
                when = datetime.strptime(row[index["time"]].strip(), "%Y/%m/%d %H:%M:%S.%f").timestamp() * 1000
            except (ValueError, IndexError):
                continue

            def num(key):
                try:
                    return float(row[index[key]]) if index[key] is not None else None
                except (ValueError, IndexError):
                    return None

            samples.append((when, num("util"), num("gr"), num("mem"), num("power"),
                            row[index["pstate"]].strip() if index["pstate"] is not None else None))
    samples.sort()
    return samples


def med(values):
    values = [v for v in values if v is not None]
    return f"{statistics.median(values):.0f}" if values else "-"


def main():
    args = [Path(a) for a in sys.argv[1:]]
    if len(args) < 2 or len(args) % 2:
        sys.exit(__doc__)
    for run, capture in zip(args[0::2], args[1::2]):
        manifest = json.loads((run / "manifest.json").read_text())
        timeline = [s for s in manifest.get("timeline", []) if "endEpochMs" in s and s.get("path") is not None]
        samples = load(capture)
        times = [s[0] for s in samples]
        print(f"## {run.name}: {capture.name}, {len(samples)} samples\n")
        print("| viewpoint | mode | samples | GPU % | graphics MHz | memory MHz | power W | P0 |")
        print("| --- | --- | --- | --- | --- | --- | --- | --- |")
        groups = defaultdict(list)
        order = []
        for s in timeline:
            key = (s["viewpoint"], s["mode"])
            if key not in order:
                order.append(key)
            lo = bisect.bisect_left(times, s["startEpochMs"])
            hi = bisect.bisect_left(times, s["endEpochMs"])
            groups[key].extend(samples[lo:hi])
        for key in order:
            rows = groups[key]
            p0 = [r[5] for r in rows if r[5]]
            share = f"{100 * sum(1 for p in p0 if p == 'P0') / len(p0):.0f}%" if p0 else "-"
            print(f"| {key[0]} | {key[1]} | {len(rows)} | {med(r[1] for r in rows)} | {med(r[2] for r in rows)} | "
                  f"{med(r[3] for r in rows)} | {med(r[4] for r in rows)} | {share} |")
        print()


if __name__ == "__main__":
    main()
