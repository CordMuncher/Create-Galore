#!/usr/bin/env python3
"""Where the render thread spends its time, per mode: python cpuprofile.py RUN_FOLDER RECORDING.jfr [JFR_TOOL]
(standard library; JFR_TOOL is the JDK's jfr, by default the one on PATH).

For a test kit run started with -XX:StartFlightRecording (Java Flight Recorder samples every thread's stack about
every 10 ms with settings=profile). The run's manifest.json says when each segment ran (wall clock), so each sample
of the render thread (named "main" in the recording) goes to the mode of the camera path it fell in (warm-up and settle segments, with the shader
reloads, are left out). Per mode it prints:
- samples: how many render thread samples fell in that mode's camera paths;
- Bridge: the share of them with the Bridge's DLAA code (dev.creategalore.bridge.dlss) anywhere on the stack, and
  the share in its motion vector code (classes with Motion in the name: entities and the hand, Colorwheel, Sable);
- the packages the samples are in (the top frame of each; "native" for code outside Java, mostly the graphics
  driver), and the busiest methods.
Compare dlaa-default with dlaa-camera (no object motion) and photon-taa (no Bridge DLAA at all).

It reads jfr's text output line by line, as the JSON of a long recording doesn't fit in memory. jfr prints the
sample times in this machine's time zone, so run it on the PC that recorded.
"""
import bisect
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

# Minecraft renames its main thread "Render thread" after Java Flight Recorder has noted it as "main".
THREADS = ("Render thread", "main")
BRIDGE = "dev.creategalore.bridge.dlss."
TOP = 15
EVENTS = ("jdk.ExecutionSample", "jdk.NativeMethodSample")
# startTime = 21:22:36.264 (2026-10-24)
START = re.compile(r"startTime = (\d+):(\d+):(\d+)(?:\.(\d+))? \((\d+)-(\d+)-(\d+)\)")
THREAD_LINE = re.compile(r'sampledThread = "(.*)" \(')


def epoch_ms(match):
    hh, mm, ss, frac, y, mo, d = match.groups()
    micros = int((frac or "0")[:6].ljust(6, "0"))
    local = datetime(int(y), int(mo), int(d), int(hh), int(mm), int(ss), micros)
    return local.timestamp() * 1000  # naive = local time, as jfr printed it


def method_of(line):
    # dev.creategalore.bridge.dlss.DlssPass.copyAndEvaluate(Minecraft) line: 120
    name = line.strip().split("(", 1)[0]
    cls = name.rsplit(".", 1)[0] if "." in name else name
    return cls, name


def package(cls):
    parts = cls.split(".")
    return ".".join(parts[:3]) if len(parts) > 3 else ".".join(parts[:-1]) or cls


def samples(tool, recording):
    """Yields (event type, thread name, epoch ms, [(class, method), ...] top frame first)."""
    proc = subprocess.Popen([tool, "print", "--stack-depth", "64", "--events", ",".join(EVENTS), str(recording)],
                            stdout=subprocess.PIPE, text=True, errors="replace")
    kind = thread = when = None
    frames = []
    in_stack = False
    for line in proc.stdout:
        text = line.strip()
        if text.endswith("{") and text.split(" ", 1)[0] in EVENTS:
            kind, thread, when, frames, in_stack = text.split(" ", 1)[0], None, None, [], False
        elif kind is None:
            continue
        elif in_stack:
            if text == "]":
                in_stack = False
            elif text and text != "...":
                frames.append(method_of(text))
        elif text.startswith("startTime = "):
            m = START.match(text)
            when = epoch_ms(m) if m else None
        elif text.startswith("sampledThread = "):
            m = THREAD_LINE.match(text)
            thread = m.group(1) if m else None
        elif text.startswith("stackTrace = ["):
            in_stack = True
        elif text == "}":
            if when is not None:
                yield kind, thread, when, frames
            kind = None
    if proc.wait() != 0:
        sys.exit(f"{tool} print failed with exit code {proc.returncode}")


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    run, recording = Path(sys.argv[1]), Path(sys.argv[2])
    tool = sys.argv[3] if len(sys.argv) > 3 else "jfr"
    manifest = json.loads((run / "manifest.json").read_text())
    segments = [s for s in manifest.get("timeline", []) if s.get("path") is not None and "endEpochMs" in s]
    if not segments:
        sys.exit(f"{run}: no camera path segments with times in manifest.json")
    segments.sort(key=lambda s: s["startEpochMs"])

    per_mode = defaultdict(lambda: {"samples": 0, "bridge": 0, "motion": 0, "packages": Counter(), "methods": Counter()})
    order = []
    for s in segments:
        if s["mode"] not in order:
            order.append(s["mode"])
    starts = [s["startEpochMs"] for s in segments]
    render_samples = 0
    for kind, thread, t, names in samples(tool, recording):
        if thread not in THREADS or not names:
            continue
        render_samples += 1
        i = bisect.bisect_right(starts, t) - 1
        if i < 0 or t >= segments[i]["endEpochMs"]:
            continue
        m = per_mode[segments[i]["mode"]]
        m["samples"] += 1
        if any(cls.startswith(BRIDGE) for cls, _ in names):
            m["bridge"] += 1
        if any(cls.startswith(BRIDGE) and "Motion" in cls.rsplit(".", 1)[-1] for cls, _ in names):
            m["motion"] += 1
        top_cls, top_method = names[0]
        native = kind == "jdk.NativeMethodSample"
        m["packages"]["native" if native else package(top_cls)] += 1
        m["methods"][("native: " if native else "") + top_method] += 1

    print(f"## {run.name}: render thread samples per mode ({recording.name})\n")
    inside = sum(m["samples"] for m in per_mode.values())
    print(f"{render_samples} render thread samples in the recording, {inside} inside the camera paths.")
    if render_samples and not inside:
        print("**None fell in the run's camera paths: was the recording of this run, and read on the PC that made it?**")
    print()
    print("| mode | samples | Bridge DLAA code | its motion vector code |")
    print("| --- | --- | --- | --- |")
    for mode in order:
        m = per_mode[mode]
        n = max(m["samples"], 1)
        print(f"| {mode} | {m['samples']} | {100 * m['bridge'] / n:.1f}% | {100 * m['motion'] / n:.1f}% |")
    print()
    for mode in order:
        m = per_mode[mode]
        n = max(m["samples"], 1)
        print(f"### {mode}\n")
        print("| package (top frame) | share |")
        print("| --- | --- |")
        for name, count in m["packages"].most_common(10):
            print(f"| {name} | {100 * count / n:.1f}% |")
        print()
        print("| method (top frame) | share |")
        print("| --- | --- |")
        for name, count in m["methods"].most_common(TOP):
            print(f"| {name} | {100 * count / n:.1f}% |")
        print()


if __name__ == "__main__":
    main()
