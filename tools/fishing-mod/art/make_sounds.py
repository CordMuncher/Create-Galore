#!/usr/bin/env python3
"""Synthesizes the mod's sounds (no recordings, nothing borrowed) and encodes them as Ogg Vorbis with ffmpeg.

Writes src/main/resources/assets/create_galore_fishing/sounds/<name>.ogg; sounds.json maps sound events to these.
Mono 44.1 kHz, as Minecraft wants for positional sounds. Needs numpy and ffmpeg with libvorbis.

    python3 tools/fishing-mod/art/make_sounds.py

Each sound is a small recipe below: noise and tones shaped by envelopes and simple filters. Loops (crank, motor,
engine) are built from whole periods so they repeat without a seam.
"""
import pathlib
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent / "src/main/resources/assets/create_galore_fishing/sounds"
rng = np.random.default_rng(1234)


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def env(n, attack, decay):
    """Attack/exponential-decay envelope over n samples (times in seconds)."""
    x = np.arange(n) / RATE
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(0, x - attack) / max(decay, 1e-4))


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, center, q=4.0):
    """Biquad band-pass (constant peak gain)."""
    w = 2 * np.pi * center / RATE
    alpha = np.sin(w) / (2 * q)
    b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w), 1 - alpha
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        out = (b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, v, y1, out
        y[i] = out
    return y


def noise(seconds):
    return rng.uniform(-1, 1, int(seconds * RATE))


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x * (peak / m)


def save(name, x):
    (OUT / name).parent.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / "s.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5",
                        str(OUT / f"{name}.ogg")], check=True)


def click(freq, length=0.018, ring=0.004, grit=0.35):
    """A metal ratchet click: a sharp transient and a short ring."""
    n = int(length * RATE)
    x = t(length)
    tone = np.sin(2 * np.pi * freq * x) + 0.5 * np.sin(2 * np.pi * freq * 2.7 * x)
    burst = highpass(noise(length), 2000) * grit
    return (tone * 0.6 + burst) * env(n, 0.0004, ring)


def drag(variant):
    """One pawl click of the drag ratchet. Played once per few cm of line out, so its rate is the run speed."""
    base = [3600, 4200, 3100][variant]
    x = np.concatenate([click(base, ring=0.0035), np.zeros(int(0.004 * RATE))])
    x[:len(click(base * 0.5, ring=0.006))] += 0.35 * click(base * 0.5, ring=0.006)
    return norm(x, 0.8)


def crank_loop():
    """Reel crank: gear teeth ticking over a soft whirr, one second, seamless."""
    length = 1.0
    x = t(length)
    teeth = 48  # ticks per second (a whole number, so the loop closes)
    tick_len = int(RATE / teeth)
    ticks = np.zeros(len(x))
    one = click(2400, length=tick_len / RATE, ring=0.002, grit=0.2)
    for k in range(teeth):
        ticks[k * tick_len:k * tick_len + len(one)] += one * (0.6 + 0.4 * rng.random())
    whirr = bandpass(noise(length), 900, 2.0) * 0.25 + 0.15 * np.sin(2 * np.pi * 96 * x)
    return norm(ticks * 0.6 + whirr, 0.6)


def grind():
    """Cranking against a slipping drag: a rough, uneven grind."""
    length = 0.45
    x = t(length)
    rough = bandpass(noise(length), 380, 1.5) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * x))
    teeth = np.sign(np.sin(2 * np.pi * 61 * x)) * 0.2
    return norm((rough + lowpass(teeth, 800)) * env(len(x), 0.02, 0.3), 0.7)


def strain(variant):
    """Line strain: a stretched creak, resonant noise whose pitch rises a little."""
    length = 0.7
    x = t(length)
    src = noise(length)
    center = [700, 900][variant]
    creak = np.zeros(len(x))
    # Stick-slip: short bursts at an uneven rate.
    pos = 0
    while pos < len(x):
        n = int(rng.uniform(0.006, 0.02) * RATE)
        creak[pos:pos + n] += rng.uniform(0.4, 1.0)
        pos += n + int(rng.uniform(0.004, 0.03) * RATE)
    shaped = bandpass(src * creak[:len(src)], center, 6) + bandpass(src * creak[:len(src)], center * 2.1, 8) * 0.5
    return norm(shaped * env(len(x), 0.08, 0.5), 0.6)


def snap():
    """Line snap: a crack and a falling twang."""
    length = 0.6
    x = t(length)
    crack = highpass(noise(0.02), 1500) * env(int(0.02 * RATE), 0.0002, 0.006)
    freq = 900 * np.exp(-x * 3)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    twang = (np.sin(phase) + 0.3 * np.sin(2 * phase)) * env(len(x), 0.001, 0.15)
    y = twang * 0.6
    y[:len(crack)] += crack
    return norm(y, 0.9)


def whoosh(length=0.5, center=1200):
    """Air past the rod: band noise that swells and fades, sweeping up."""
    x = t(length)
    src = noise(length)
    lo = bandpass(src, center * 0.6, 1.2)
    hi = bandpass(src, center * 1.6, 1.2)
    mix = lo * (1 - x / length) + hi * (x / length)
    shape = np.sin(np.pi * np.clip(x / length, 0, 1)) ** 2
    return norm(mix * shape, 0.7)


def zip_line():
    """Line zipping off the spool in flight: fast faint clicks and a hiss."""
    length = 0.8
    x = t(length)
    rate = 140 * np.exp(-x * 1.5)
    phase = np.cumsum(rate) / RATE
    ticks = (np.diff(np.floor(phase), prepend=0) > 0).astype(float)
    ticks = lowpass(ticks, 6000) * 4
    hiss = bandpass(noise(length), 4000, 1.0) * 0.15
    return norm((ticks + hiss) * env(len(x), 0.005, 0.5), 0.5)


def plop(weight=1.0):
    """A small rig hitting the water: a falling thump with a bubbly tail."""
    length = 0.35
    x = t(length)
    freq = (420 / weight) * np.exp(-x * 9) + 90
    body = np.sin(2 * np.pi * np.cumsum(freq) / RATE) * env(len(x), 0.002, 0.06)
    bubbles = bandpass(noise(length), 1800, 3) * env(len(x), 0.01, 0.12) * 0.35
    return norm(body + bubbles, 0.8)


def tap():
    """Rod tip tap / float bob tick."""
    x = click(1500, length=0.06, ring=0.012, grit=0.15) + 0.5 * click(600, length=0.06, ring=0.02, grit=0.0)
    return norm(x, 0.6)


def swish():
    """A strike: a fast, short whoosh."""
    return whoosh(0.22, 1800)


def whip():
    """A hook coming free: a light whip and a flick."""
    y = whoosh(0.15, 2600)
    y[:int(0.01 * RATE)] += highpass(noise(0.01), 3000) * 0.5
    return norm(y, 0.6)


def clack():
    """The bail closing as reeling starts."""
    x = click(1300, length=0.09, ring=0.02, grit=0.5) + click(650, length=0.09, ring=0.03, grit=0.0) * 0.6
    return norm(x, 0.8)


def alarm():
    """A rod holder's bite alarm: a clicker ratchet rattling as line goes out."""
    length = 0.8
    y = np.zeros(int(length * RATE))
    for k in range(14):
        c = click(2800 + 200 * (k % 2), length=0.02, ring=0.004)
        start = int(k * 0.055 * RATE)
        y[start:start + len(c)] += c
    return norm(y * env(len(y), 0.0, 0.7), 0.85)


def motor_loop():
    """Drive motor hum: a filtered buzz with gear whine, one second, seamless."""
    x = t(1.0)
    buzz = sum(np.sin(2 * np.pi * 75 * k * x) / k for k in range(1, 8))
    whine = 0.15 * np.sin(2 * np.pi * 750 * x)
    return norm(lowpass(buzz, 1200) + whine, 0.5)


def engine_loop():
    """Small fuel engine putter: two-stroke pops at 25 Hz, one second, seamless."""
    x = t(1.0)
    y = np.zeros(len(x))
    pop = lowpass(noise(0.03), 900) * env(int(0.03 * RATE), 0.001, 0.008)
    for k in range(25):
        start = int(k * RATE / 25)
        y[start:start + len(pop)] += pop * (0.7 + 0.3 * rng.random())
    y += 0.2 * np.sin(2 * np.pi * 50 * x)
    return norm(y, 0.6)


def clunk():
    """Gearbox shift: a heavy mechanical clunk."""
    length = 0.25
    x = t(length)
    thud = np.sin(2 * np.pi * (180 * np.exp(-x * 12) + 60) * x) * env(len(x), 0.001, 0.05)
    metal = bandpass(noise(length), 1100, 5) * env(len(x), 0.0005, 0.03)
    return norm(thud + metal * 0.6, 0.85)


def groan():
    """A stalling drive: a low strained groan that sags."""
    length = 0.9
    x = t(length)
    freq = 110 * (1 - 0.35 * x / length)
    tone = np.sin(2 * np.pi * np.cumsum(freq) / RATE)
    rough = bandpass(noise(length), 220, 2) * 0.5
    return norm((tone + rough) * env(len(x), 0.08, 0.6), 0.7)


def flop():
    """A fish flapping in the hands: a wet slap."""
    length = 0.18
    x = t(length)
    slap = lowpass(noise(length), 2500) * env(len(x), 0.001, 0.03)
    thump = np.sin(2 * np.pi * 140 * x) * env(len(x), 0.001, 0.04) * 0.6
    return norm(slap + thump, 0.7)


def main():
    for v in range(3):
        save(f"reel/drag{v + 1}", drag(v))
    save("reel/crank_loop", crank_loop())
    save("reel/grind", grind())
    save("reel/bail", clack())
    for v in range(2):
        save(f"line/strain{v + 1}", strain(v))
    save("line/snap", snap())
    save("line/zip", zip_line())
    save("line/whip", whip())
    save("cast/whoosh", whoosh())
    save("cast/plop_light", plop(0.8))
    save("cast/plop_heavy", plop(1.6))
    save("bite/tap", tap())
    save("bite/strike", swish())
    save("catch/flop", flop())
    save("machine/alarm", alarm())
    save("machine/motor_loop", motor_loop())
    save("machine/engine_loop", engine_loop())
    save("machine/shift", clunk())
    save("machine/stall", groan())
    print("sounds written to", OUT)


if __name__ == "__main__":
    main()
