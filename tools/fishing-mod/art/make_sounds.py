#!/usr/bin/env python3
"""Synthesizes the mod's sounds (no recordings, nothing borrowed) and encodes them as Ogg Vorbis with ffmpeg.

Writes src/main/resources/assets/create_galore_fishing/sounds/<name>.ogg; sounds.json maps sound events to these.
Mono 44.1 kHz, as Minecraft wants for positional sounds. Needs numpy and ffmpeg with libvorbis.

    python3 tools/fishing-mod/art/make_sounds.py [only these, e.g. reel/crank_loop]

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


ONLY = set()


def save(name, x):
    if ONLY and name not in ONLY:
        return
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


def pnoise(seconds, lo, hi, tilt=0.0):
    """Band-limited noise that repeats seamlessly every `seconds` (built in the frequency domain), lo..hi Hz.

    tilt > 0 makes it darker (amplitude falls as f^-tilt across the band)."""
    n = int(seconds * RATE)
    spec = np.zeros(n // 2 + 1, complex)
    f = np.fft.rfftfreq(n, 1 / RATE)
    band = (f >= lo) & (f <= hi)
    mags = np.where(band, np.power(np.maximum(f, 1), -tilt), 0)
    # Soft band edges, so it isn't a brick-wall filter.
    mags *= np.clip((f - lo * 0.8) / (lo * 0.2 + 1), 0, 1) * np.clip((hi * 1.25 - f) / (hi * 0.25 + 1), 0, 1)
    spec[:] = mags * np.exp(2j * np.pi * rng.random(len(f)))
    x = np.fft.irfft(spec, n)
    return x / (np.max(np.abs(x)) + 1e-9)


def resonate(x, freqs, decay):
    """Rings x through a few damped resonators (a small metal part): sum of decaying sines convolved, via FFT."""
    n = len(x)
    k = np.arange(int(decay * 6 * RATE)) / RATE
    ir = sum(np.sin(2 * np.pi * fq * k) * np.exp(-k / decay) * w for fq, w in freqs)
    y = np.fft.irfft(np.fft.rfft(x, n + len(ir)) * np.fft.rfft(ir, n + len(ir)))[:n]
    return y


def tick(partials, decay, grit=0.5):
    """One tiny metal tick, the grain every reel sound is made of: an impulse ringing a few high, inharmonic partials
    for a millisecond or so, over a burst of bright noise. Real reel recordings are trains of these (centroid near
    10 kHz, almost nothing under 300 Hz), not tones."""
    length = decay * 8 + 0.001
    n = int(length * RATE)
    imp = np.zeros(n)
    imp[0] = 1.0
    imp += highpass(noise(length), 4000) * np.exp(-np.arange(n) / (0.0003 * RATE)) * grit
    return resonate(imp, partials, decay)


def scatter(n, grains, times, gains):
    """Adds each grain at its time (samples) and gain into a buffer of n samples, wrapping the tail round to the
    start so a loop closes without a seam."""
    y = np.zeros(n)
    for grain, at, g in zip(grains, times, gains):
        at = int(at) % n
        end = min(n, at + len(grain))
        y[at:end] += grain[:end - at] * g
        if end - at < len(grain):
            y[:len(grain) - (end - at)] += grain[end - at:] * g
    return y


def pawl(base, rough=1.0):
    """The drag clicker: a spring pawl snapping over one tooth of the spool's ratchet. A sharp, bright tick (the
    BBC's fly reel recordings ring for 2 to 3 ms and have most of their energy above 3 kHz) with a softer lift tick
    a fraction of a millisecond before it."""
    hit = tick([(base, 1.0), (base * 1.53, 0.7), (base * 2.21, 0.45), (base * 0.41, 0.4)], 0.0022, 0.6 * rough)
    lift = tick([(base * 1.8, 1.0), (base * 2.6, 0.5)], 0.0003, 0.3) * 0.25 * rough
    y = np.zeros(len(hit) + int(0.0006 * RATE))
    y[:len(lift)] += lift
    y[int(0.0006 * RATE):] += hit
    return y


def drag(variant):
    """One pawl click of the drag ratchet. Played per few cm of line out while a fish takes line slowly; a fast run
    plays the drag loop instead."""
    base = [4700, 5300, 4200][variant]
    return norm(pawl(base), 0.8)


def drag_loop():
    """The drag screaming under a running fish: the clicker's ticks so close together (160 a second at pitch 1,
    like the BBC fly reel paying out at about 150) that they blur into a buzz, each a little different in strength
    and spacing, over the hiss of line leaving the spool. One second, seamless; the game sets the pitch from how
    fast line is going out."""
    n = RATE
    clicks = 160
    grains = [pawl(rng.uniform(4300, 5600), rough=rng.uniform(0.6, 1.0)) for _ in range(12)]
    times = (np.arange(clicks) + rng.uniform(-0.12, 0.12, clicks)) * n / clicks
    y = scatter(n, [grains[k % 12] for k in range(clicks)], times, rng.uniform(0.6, 1.0, clicks))
    hiss = pnoise(1.0, 4000, 14000, 0.3) * 0.12
    return norm(y + hiss, 0.7)


def crank_loop():
    """A spinning reel being wound, one second, seamless, the handle going round four times (pitch slows it). Made
    to match recordings of real spinning reels, which are a dense, even whirr, not a tone: the gear teeth, bearings
    and line roller tick so fast (well over a thousand times a second, each tick a little different) that they blur
    into a fizzy rattle between about 2 and 7 kHz, with a gear whine near 4 kHz on top, a coarser tick from the
    anti-reverse and roller a couple of dozen times a second, and the hiss of line laying onto the spool. Nothing to
    speak of below 300 Hz: a low hum is what made the old loop sound like an engine."""
    n = RATE
    x = t(1.0)
    handle = 4
    turn = 0.5 + 0.5 * np.sin(2 * np.pi * handle * x)
    rattle = np.zeros(n)
    for partials in ([(2600, 1.0), (4100, 0.7), (6300, 0.4)], [(3300, 1.0), (5200, 0.6), (7400, 0.3)],
                     [(4500, 1.0), (6100, 0.5), (8800, 0.25)]):
        count = 600
        times = ((np.arange(count) + rng.uniform(0, 1, count)) * n / count).astype(int) % n
        train = np.zeros(n)
        np.add.at(train, times, rng.gamma(2.0, 0.5, count) * (0.6 + 0.4 * turn[times]))
        # Rings the whole train through the part's resonances at once; wrap the ring-out to close the loop.
        rung = resonate(np.r_[train, np.zeros(2000)], partials, rng.uniform(0.0005, 0.0009))
        rung[:2000] += rung[n:]
        rattle += rung[:n]
    rattle /= np.max(np.abs(rattle))
    coarse_n = 24
    coarse = scatter(n, [tick([(f, 1.0), (f * 1.6, 0.5)], 0.0012, 0.4) for f in rng.uniform(1800, 3200, coarse_n)],
                     (np.arange(coarse_n) + rng.uniform(-0.2, 0.2, coarse_n)) * n / coarse_n,
                     rng.uniform(0.4, 1.0, coarse_n))
    coarse /= np.max(np.abs(coarse))
    whine = pnoise(1.0, 3800, 4150) * (0.75 + 0.25 * turn)
    lay = pnoise(1.0, 5000, 12000, 0.6) * (0.4 + 0.6 * (0.5 + 0.5 * np.cos(2 * np.pi * handle * x)))
    y = rattle + 0.35 * coarse + 0.35 * whine + 0.15 * lay
    return norm(y, 0.6)


def grind():
    """Cranking against a slipping drag: the handle forced round while the spool can't turn, gear teeth skipping
    under load. Harsh and uneven like metal on metal: clusters of hard ticks in the 1 to 6 kHz range over a gritty
    scrape, no low drone."""
    length = 0.5
    n = int(length * RATE)
    times, grains, gains = [], [], []
    pos = 0
    while pos < n:
        times.append(pos)
        base = rng.uniform(1400, 2600)
        grains.append(tick([(base, 1.0), (base * 1.7, 0.7), (base * 2.6, 0.5)], rng.uniform(0.0012, 0.0025), 0.8))
        gains.append(rng.uniform(0.4, 1.0))
        pos += int(rng.uniform(0.004, 0.02) * RATE)
    y = scatter(n + RATE, grains, times, gains)[:n]
    y /= np.max(np.abs(y))
    scrape = bandpass(noise(length), 2600, 1.5) * (0.5 + 0.5 * np.abs(lowpass(noise(length), 40)) * 8).clip(0, 1.5)
    return norm((y + 0.35 * scrape / np.max(np.abs(scrape))) * env(n, 0.01, 0.3), 0.75)


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
    save("reel/drag_loop", drag_loop())
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
    import sys
    # Optional: only write these sounds, e.g. reel/crank_loop reel/drag_loop (the rest are still computed, so the
    # random draws, and with them every other sound, stay the same).
    ONLY.update(sys.argv[1:])
    main()
