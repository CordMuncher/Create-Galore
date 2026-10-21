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


def pawl(base, rough=1.0):
    """One drag pawl snapping over a ratchet tooth: a tiny impact ringing the reel's metal (a few inharmonic
    partials), with a softer lift tick just before it."""
    length = 0.03
    n = int(length * RATE)
    imp = np.zeros(n)
    imp[int(0.004 * RATE)] = 1.0
    imp[0] = 0.25 * rough
    imp += highpass(noise(length), 3000) * np.exp(-np.arange(n) / (0.0008 * RATE)) * 0.4 * rough
    return resonate(imp, [(base, 1.0), (base * 1.47, 0.6), (base * 2.31, 0.35), (base * 0.52, 0.3)], 0.0022)


def drag(variant):
    """One pawl click of the drag ratchet. Played per few cm of line out while a fish takes line slowly; a fast run
    plays the drag loop instead."""
    base = [3300, 3700, 2950][variant]
    return norm(pawl(base), 0.8)


def drag_loop():
    """The drag screaming under a running fish: the ratchet's clicks run together into a buzz (160 a second at
    pitch 1, a little uneven), over the hiss of line leaving the spool and the spool's own whirr. One second,
    seamless; the game sets the pitch from how fast line is going out."""
    length = 1.0
    n = int(length * RATE)
    y = np.zeros(n)
    clicks = 160
    period = n // clicks
    one = pawl(3400, rough=0.6)
    for k in range(clicks):
        start = k * period + int(rng.integers(-6, 7)) % period
        part = one * (0.65 + 0.35 * rng.random())
        end = min(n, start + len(part))
        y[start:end] += part[:end - start]
        if end - start < len(part):  # wrap around so the loop closes
            y[:len(part) - (end - start)] += part[end - start:]
    hiss = pnoise(length, 2500, 9000, 0.3) * 0.18
    whirr = pnoise(length, 300, 1400, 0.8) * 0.12 * (1 + 0.3 * np.sin(2 * np.pi * 20 * t(length)))
    return norm(y + hiss + whirr, 0.7)


def crank_loop():
    """A spinning reel being wound, one second, seamless: the handle goes round twice. Built like the real thing:
    the main gear meshing with the pinion (a soft, slightly rough hum that swells and fades once per turn of the
    handle, as the gears load and unload), the rotor and bail spinning five times faster (a whirr with a faint tick
    each turn from the line roller), the line laying onto the spool (a hiss that rises and falls with the
    oscillating spool), and the bearings' fine sizzle. No harsh clicks: a good reel purrs."""
    length = 1.0
    x = t(length)
    handle = 2  # turns a second
    rotor = handle * 5  # gear ratio 5:1
    turn = 0.5 + 0.5 * np.sin(2 * np.pi * handle * x)  # 0..1 once per handle turn
    # Gear mesh: 34 teeth on the main gear -> 68 Hz, with harmonics, made rough by a little noise in its amplitude.
    mesh_f = 34 * handle
    mesh = sum(np.sin(2 * np.pi * mesh_f * k * x + rng.random() * 6.28) / k ** 1.3 for k in range(1, 12))
    mesh *= 0.75 + 0.25 * pnoise(length, 20, 200)
    mesh = mesh * (0.55 + 0.45 * turn)
    # Pinion and rotor: a brighter whirr at rotor speed times its teeth, plus air from the spinning rotor.
    pinion = sum(np.sin(2 * np.pi * rotor * 11 * k * x + rng.random() * 6.28) / k ** 1.8 for k in range(1, 8))
    rotor_air = pnoise(length, 400, 2500, 0.6) * (0.6 + 0.4 * np.sin(2 * np.pi * rotor * x) ** 2)
    # Line roller tick once per rotor turn, very soft.
    ticks = np.zeros(len(x))
    tick = pawl(2200, rough=0.3) * 0.25
    for k in range(rotor):
        start = int(k * RATE / rotor)
        ticks[start:start + len(tick)] += tick[:len(x) - start]
    # Line onto the spool: hiss, swelling as the spool oscillates (once per handle turn).
    lay = pnoise(length, 3000, 11000, 0.2) * (0.35 + 0.65 * (0.5 + 0.5 * np.cos(2 * np.pi * handle * x)))
    bearings = pnoise(length, 6000, 14000) * 0.4
    y = 0.42 * mesh / np.max(np.abs(mesh)) + 0.22 * pinion / np.max(np.abs(pinion)) + 0.2 * rotor_air \
        + ticks + 0.15 * lay + 0.03 * bearings
    return norm(y, 0.6)


def grind():
    """Cranking against a slipping drag: the handle forced round while the spool can't turn, gear teeth skipping and
    crunching under load, uneven and gritty."""
    length = 0.5
    x = t(length)
    y = np.zeros(len(x))
    pos = 0
    while pos < len(x):
        crunch = resonate(np.r_[1.0, np.zeros(int(0.03 * RATE))] * rng.uniform(0.5, 1.0),
                          [(rng.uniform(700, 1100), 1.0), (rng.uniform(1600, 2400), 0.5), (180, 0.6)], 0.006)
        end = min(len(x), pos + len(crunch))
        y[pos:end] += crunch[:end - pos]
        pos += int(rng.uniform(0.012, 0.035) * RATE)
    rough = bandpass(noise(length), 450, 1.2) * 0.5
    groan = np.sin(2 * np.pi * np.cumsum(95 + 15 * np.sin(2 * np.pi * 7 * x)) / RATE) * 0.35
    return norm((y + rough + groan) * env(len(x), 0.015, 0.35), 0.75)


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
