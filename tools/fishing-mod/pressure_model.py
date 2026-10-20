"""Steady-state catches per hour in one chunk with N rods, as CastHook and FishingPressure work it out (balance aid).

Per rod: wait for a bite (rolled every 0.5 s with p = 1-exp(-0.5/wait), wait = clamp(17.4/f, 5, 240)), then a fight
of FIGHT seconds (plus HANDLE seconds of recast). Every landed fish adds 1 pressure to the chunk, pressure halves
every 600 s. f = max(floor, 1/(1+0.25 p)).
"""
import math, random, sys

def run(n, floor, fight=30.0, handle=3.0, hours=4.0, seed=1):
    rng = random.Random(seed)
    dt = 0.5
    p = 0.0
    state = [('wait', 0.0)] * n
    caught = 0
    t = 0.0
    warm = 3600.0
    end = warm + hours * 3600
    decay = 0.5 ** (dt / 600.0)
    while t < end:
        f = max(floor, 1 / (1 + 0.25 * p))
        wait = min(240, max(5, 17.4 / f))
        pb = 1 - math.exp(-0.5 / wait)
        for i in range(n):
            kind, left = state[i]
            if kind == 'wait':
                if rng.random() < pb:
                    state[i] = ('fight', fight + handle)
            else:
                left -= dt
                if left <= 0:
                    p += 1
                    if t >= warm:
                        caught += 1
                    state[i] = ('wait', 0.0)
                else:
                    state[i] = ('fight', left)
        p *= decay
        t += dt
    return caught / hours, p

FLOORS = [float(x) for x in sys.argv[1:]] or [0.2, 0.05]
for floor in FLOORS:
    for n in (1, 2, 5, 10, 20, 50):
        rate, p = run(n, floor)
        print(f'floor {floor:4}  rods {n:3}  {rate:6.0f} fish/h in the chunk  {rate / n:5.1f} per rod  (pressure {p:5.1f}, factor {max(floor, 1 / (1 + 0.25 * p)):.2f})')
    print()
