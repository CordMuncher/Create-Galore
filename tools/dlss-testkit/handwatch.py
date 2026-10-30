#!/usr/bin/env python3
"""Finds the first-person hand's flashes in a hand watch log (logs/create-galore-hand-watch-*.csv, see the Bridge's
HandWatch) and what changed with them.

A flash is a frame where the hand's brightness in DLSS's (or FSR's) output changes from the frame before far more
than it usually does. For each one it prints the frames around it and checks what else happened then: a change in
the hand's brightness in the inputs already (the pack lit it differently: lighting, shadows), a jump of the hand's
brightness that stays (a step, like a shadow edge moving one shadow map texel), its motion vectors, its vertices not
matching last frame's, the shadow pass drawing the player differently, a DLSS reset, the camera moving, the hand's
light or view matrix changing. The summary compares how often each happens with the flashes and without them.

Usage: handwatch.py <log.csv> [--ratio 4] [--around 3] [--skip 3]
Plain Python 3, no packages needed.
"""
import argparse
import csv
import statistics
import sys


def median(values):
    return statistics.median(values) if values else 0.0


def load(path):
    with open(path, newline="") as f:
        rows = list(csv.DictReader(f))
    out = []
    for row in rows:
        r = {}
        for k, v in row.items():
            try:
                r[k] = float(v)
            except (TypeError, ValueError):
                r[k] = v
        out.append(r)
    return out


def spikes(rows, key, ratio, floor):
    """Indices where rows[key] is ratio times its median (and above floor)."""
    base = median([r[key] for r in rows])
    limit = max(base * ratio, floor)
    return {i for i, r in enumerate(rows) if r[key] > limit}, base


def steps(rows, key, window=8, top=10):
    """The largest changes of rows[key] that stay: mean of the window after against the window before."""
    found = []
    for i in range(window, len(rows) - window):
        before = statistics.fmean(r[key] for r in rows[i - window:i])
        after = statistics.fmean(r[key] for r in rows[i:i + window])
        found.append((abs(after - before), i, before, after))
    found.sort(reverse=True)
    picked = []
    for f in found:
        if all(abs(f[1] - p[1]) > window for p in picked):
            picked.append(f)
        if len(picked) == top:
            break
    return picked


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("log")
    ap.add_argument("--ratio", type=float, default=4.0, help="a flash: the frame's change this many times the median")
    ap.add_argument("--around", type=int, default=3, help="frames shown before and after each flash")
    ap.add_argument("--skip", type=float, default=3.0,
                    help="first seconds left out: the watch starts with a shader reload, after which the pack's exposure settles")
    args = ap.parse_args()

    rows = load(args.log)
    if rows:
        start = rows[0]["nanos"] + args.skip * 1e9
        rows = [r for r in rows if r["nanos"] >= start]
    if len(rows) < 20:
        sys.exit(f"only {len(rows)} frames in {args.log}")
    seconds = (rows[-1]["nanos"] - rows[0]["nanos"]) / 1e9
    print(f"{args.log}: {len(rows)} frames over {seconds:.1f} s ({len(rows) / max(seconds, 1e-9):.1f} fps), "
          f"engine {rows[0]['engine']}")
    hand_px = median([r["inHandPx"] for r in rows])
    print(f"hand: {hand_px:.0f} render pixels, {median([r['outHandPx'] for r in rows]):.0f} output pixels; "
          f"brightness in {median([r['inHandLuma'] for r in rows]):.4f}, out {median([r['outHandLuma'] for r in rows]):.4f}")
    if hand_px == 0:
        print("no hand in the frames (is the hand shown, holding something?)")

    still = [r for r in rows if r["camMove"] < 1e-4 and r["camTurn"] < 1e-3]
    print(f"camera still in {len(still)} of {len(rows)} frames")

    out_flash, out_base = spikes(rows, "outHandDLuma", args.ratio, 0.002)
    in_flash, in_base = spikes(rows, "inHandDLuma", args.ratio, 0.002)
    world_flash, world_base = spikes(rows, "outWorldDLuma", args.ratio, 0.002)
    print(f"\nframe-to-frame change of the hand's brightness, median: output {out_base:.5f}, input {in_base:.5f}; "
          f"the world's in the output {world_base:.5f}")
    print(f"hand flashes in the output: {len(out_flash)}; in the input: {len(in_flash)}; world flashes in the output: {len(world_flash)}")

    mode_shadow = statistics.mode([r["shadowPlayerVerts"] for r in rows])
    mode_draws = statistics.mode([r["shadowPlayerDraws"] for r in rows])
    suspects = {
        "input hand change (the pack lit it differently)": lambda r: r["inHandDLuma"] > max(in_base * args.ratio, 0.002),
        "hand motion vector over 0.5 px": lambda r: r["inHandMaxMv"] > 0.5,
        "hand vertices not matched to last frame's": lambda r: r["handUnknown"] > 0,
        "hand vertex count changed": lambda r: r["handVerts"] != r["handLastVerts"],
        "hand vertex moved over 0.001 blocks": lambda r: r["handMaxMove"] > 0.001,
        "hand view matrix changed": lambda r: r["handViewDelta"] > 1e-5,
        "hand drawn other than in 1-2 passes": lambda r: not 1 <= r["handPasses"] <= 2,
        f"shadow pass drew the player other than {mode_draws:.0f}x / {mode_shadow:.0f} vertices":
            lambda r: r["shadowPlayerDraws"] != mode_draws or r["shadowPlayerVerts"] != mode_shadow,
        "player's model in the shadow pass moved over 0.001 blocks": lambda r: r.get("shadowPlayerMove", 0.0) > 0.001,
        "DLSS reset": lambda r: r["reset"] > 0,
        "camera moved or turned": lambda r: r["camMove"] >= 1e-4 or r["camTurn"] >= 1e-3,
        "world changed too (whole picture)": lambda r: r["outWorldDLuma"] > max(world_base * args.ratio, 0.002),
    }
    light_prev = {}
    for i, r in enumerate(rows):
        light_prev[i] = rows[i - 1]["handLight"] if i else r["handLight"]
    suspects["hand light (lightmap) changed"] = None

    def hit(name, i):
        lo, hi = max(0, i - 2), min(len(rows), i + 3)
        if suspects[name] is None:
            return any(rows[j]["handLight"] != light_prev[j] for j in range(lo, hi))
        return any(suspects[name](rows[j]) for j in range(lo, hi))

    cols = ["seq", "gameTime", "partialTick", "reset", "camMove", "handPasses", "handLight", "handVerts", "handUnknown",
            "handMaxMove", "handViewDelta", "shadowPlayerVerts", "shadowPlayerMove", "inHandLuma", "inHandDLuma", "inHandMaxMv",
            "outHandLuma", "outHandDLuma", "outWorldDLuma"]
    events = sorted(out_flash)
    if events:
        print("\nhand flashes (output), with the frames around them (* the flash):")
        print(" " + " ".join(f"{c:>14}" for c in cols))
        for i in events[:40]:
            for j in range(max(0, i - args.around), min(len(rows), i + args.around + 1)):
                mark = "*" if j == i else " "
                print(mark + " ".join(f"{rows[j].get(c, ''):>14.6g}" if isinstance(rows[j].get(c), float) else f"{rows[j].get(c, ''):>14}" for c in cols))
            print()
        if len(events) > 40:
            print(f"(and {len(events) - 40} more)")

    print("what happened within 2 frames of a hand flash, against all frames without one:")
    others = [i for i in range(len(rows)) if all(abs(i - e) > 2 for e in events)]
    for name in suspects:
        with_flash = sum(hit(name, i) for i in events)
        base = sum(hit(name, i) for i in others)
        print(f"  {name:60} {with_flash:4}/{len(events):<4} flashes   {base:6}/{len(others):<6} other frames"
              f" ({100.0 * base / max(len(others), 1):.1f}%)")

    if len(events) > 1:
        gaps_frames = [b - a for a, b in zip(events, events[1:])]
        gaps_s = [(rows[b]["nanos"] - rows[a]["nanos"]) / 1e9 for a, b in zip(events, events[1:])]
        ticks = [rows[b]["gameTime"] - rows[a]["gameTime"] for a, b in zip(events, events[1:])]
        print(f"\ntime between flashes: median {median(gaps_s):.2f} s ({median(gaps_frames):.0f} frames, {median(ticks):.0f} ticks); "
              f"all (s): {', '.join(f'{g:.2f}' for g in gaps_s[:30])}")

    # The flash seen after walking a few blocks and stopping: each stop, and the flashes while standing after it.
    moving = [r["camMove"] >= 1e-4 for r in rows]
    stops = [i for i in range(1, len(rows)) if moving[i - 1] and not moving[i]]
    if stops:
        print("\nstops (the camera stopped moving) and the flashes while standing after each:")
        for n, i in enumerate(stops):
            end = next((j for j in range(i, len(rows)) if moving[j]), len(rows))
            still_s = (rows[end - 1]["nanos"] - rows[i]["nanos"]) / 1e9
            found = []
            for name, flashes in (("out", out_flash), ("in", in_flash)):
                for j in sorted(f for f in flashes if i <= f < end):
                    found.append(f"{name} +{(rows[j]['nanos'] - rows[i]['nanos']) / 1e9:.2f} s "
                                 f"(+{rows[j]['gameTime'] - rows[i]['gameTime']:.0f} ticks, seq {rows[j]['seq']:.0f})")
            # When the hand and the player's model in the shadow map came to rest after the stop.
            settled = []
            for name, key, limit in (("hand", "handMaxMove", 1e-5), ("shadow-map player", "shadowPlayerMove", 1e-4)):
                last = max((j for j in range(i, end) if rows[j].get(key, 0.0) > limit), default=None)
                settled.append(f"{name} {'still from the stop' if last is None else 'moved until +%.2f s' % ((rows[last]['nanos'] - rows[i]['nanos']) / 1e9)}")
            print(f"  stop {n + 1} at seq {rows[i]['seq']:.0f}, still {still_s:.1f} s: {'; '.join(found) if found else 'no flash'}"
                  f" ({', '.join(settled)})")

    # Things that move by themselves (mobs), with the camera still: their largest motion vector against frames where
    # something drew a different number of vertices (a model part shown or hidden, such as Fresh Animations' blinks).
    if "inWorldMaxMv" in rows[0]:
        still_rows = [r for r in rows if r["camMove"] < 1e-4 and r["camTurn"] < 1e-3]
        changed = [r["inWorldMaxMv"] for r in still_rows if r.get("countChanges", 0) > 0]
        steady = [r["inWorldMaxMv"] for r in still_rows if r.get("countChanges", 0) == 0]
        print(f"\ncamera still: largest motion vector in the world (render pixels), {len(still_rows)} frames:")
        for name, values in (("a thing's vertex count changed last frame", changed), ("no count change", steady)):
            if values:
                print(f"  {name:45} {len(values):5} frames: median {median(values):.3f}, max {max(values):.3f}, "
                      f"over 4 px in {sum(v > 4 for v in values)}")
            else:
                print(f"  {name:45}     0 frames")

    print("\nlargest lasting changes of the hand's brightness (input: the pack's lighting; output: what was shown):")
    for key in ("inHandLuma", "outHandLuma"):
        for size, i, before, after in steps(rows, key)[:6]:
            print(f"  {key:12} at seq {rows[i]['seq']:.0f} (gameTime {rows[i]['gameTime']:.0f}): {before:.4f} -> {after:.4f}")


if __name__ == "__main__":
    main()
