"""Create Galore pack icon, 64x64 pixel art. A brass cogwheel framing a porthole: airship over the sea."""
import math, sys
from PIL import Image

N = 64
C = (N - 1) / 2

def hx(s):
    s = s.lstrip('#'); return tuple(int(s[i:i+2], 16) for i in (0, 2, 4)) + (255,)

P = {
    'out': hx('1c1712'),
    'b0': hx('5a3a12'), 'b1': hx('8a5e1c'), 'b2': hx('b8862b'), 'b3': hx('e0b44c'), 'b4': hx('fbe08a'), 'b5': hx('fff6c8'),
    'a0': hx('3b3d3c'), 'a1': hx('5d605d'), 'a2': hx('868a85'), 'a3': hx('aeb1aa'),
    's0': hx('3a74c4'), 's1': hx('5593dc'), 's2': hx('79b2ec'), 's3': hx('a7d3f7'), 's4': hx('d3ecff'),
    'cl': hx('f7fbff'), 'cs': hx('c4d8ee'),
    'w0': hx('123a63'), 'w1': hx('1d5687'), 'w2': hx('2f74a8'), 'w3': hx('5aa0cf'),
    'e0': hx('7a2f22'), 'e1': hx('a8432c'), 'e2': hx('d0643a'), 'e3': hx('ee9156'),
    'k0': hx('3e2a17'), 'k1': hx('63432a'), 'k2': hx('8b5f38'),
    'g0': hx('2d5a2a'), 'g1': hx('43803b'),
}

img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = img.load()

def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = P[c] if isinstance(c, str) else c

def polar(x, y):
    dx, dy = x - C, y - C
    return math.hypot(dx, dy), math.atan2(dy, dx)

# Light from the top left: 1 on the lit side, -1 in shadow.
def lit(x, y):
    dx, dy = x - C, y - C
    r = math.hypot(dx, dy) or 1
    return (-dx - dy) / (r * math.sqrt(2))

TEETH = 8
R_OUT, R_BODY, R_RIM, R_WIN = 31.4, 25.4, 19.4, 17.0

def in_tooth(r, a):
    if r > R_OUT:
        return False
    # Tooth width shrinks a little towards the tip, like Create's cogs.
    seg = (a / (2 * math.pi) * TEETH) % 1.0
    half = 0.19 - 0.05 * max(0.0, (r - R_BODY) / (R_OUT - R_BODY))
    return abs(seg - 0.5) < half + 0.02

gear = [[None] * N for _ in range(N)]
for y in range(N):
    for x in range(N):
        r, a = polar(x + 0.0, y + 0.0)
        a = a + math.pi / TEETH * 0.5
        if r <= R_WIN:
            gear[y][x] = 'win'
        elif r <= R_RIM:
            gear[y][x] = 'rim'
        elif r <= R_BODY:
            gear[y][x] = 'body'
        elif in_tooth(r, a):
            gear[y][x] = 'tooth'

# Brass shading
for y in range(N):
    for x in range(N):
        g = gear[y][x]
        if g in (None, 'win'):
            continue
        l = lit(x, y)
        r, _ = polar(x, y)
        if g == 'rim':
            # Inner rim is a bevel facing the opposite way: shadow on the lit side.
            l = -l
            c = 'b0' if l < -0.35 else 'b1' if l < 0.35 else 'b2'
            if r <= R_WIN + 0.8:
                c = 'out'
        elif g == 'body':
            c = 'b1' if l < -0.55 else 'b2' if l < 0.1 else 'b3'
            if l > 0.75:
                c = 'b4'
            # One engraved groove with a lit lip just inside it
            if R_RIM + 2.4 <= r < R_RIM + 3.4:
                c = 'b0' if l < 0 else 'b1'
            elif R_RIM + 1.4 <= r < R_RIM + 2.4 and l > -0.1:
                c = 'b4' if l > 0.5 else 'b3'
        else:  # tooth
            c = 'b1' if l < -0.45 else 'b2' if l < 0.35 else 'b3'
            if l > 0.75:
                c = 'b4'
        put(x, y, c)

# Outline around the whole gear
for y in range(N):
    for x in range(N):
        if gear[y][x] is not None:
            continue
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            xx, yy = x + dx, y + dy
            if 0 <= xx < N and 0 <= yy < N and gear[yy][xx] is not None:
                put(x, y, 'out'); break

# Bolts on the body (andesite rivets)
for k in range(TEETH):
    a = (k + 0.5) * 2 * math.pi / TEETH - math.pi / TEETH * 0.5
    bx = round(C + math.cos(a) * (R_BODY - 2.2)); by = round(C + math.sin(a) * (R_BODY - 2.2))
    put(bx, by, 'a3'); put(bx + 1, by, 'a1'); put(bx, by + 1, 'a1'); put(bx + 1, by + 1, 'a0')

# --- Porthole scene ---
HORIZON = 41
for y in range(N):
    for x in range(N):
        if gear[y][x] != 'win':
            continue
        if y < HORIZON:
            t = (y - (C - R_WIN)) / (HORIZON - (C - R_WIN))
            c = 's0' if t < 0.18 else 's1' if t < 0.42 else 's2' if t < 0.7 else 's3' if t < 0.9 else 's4'
        else:
            t = y - HORIZON
            c = 'w2' if t < 2 else 'w1' if t < 6 else 'w0'
        put(x, y, c)

def win(x, y):
    return 0 <= x < N and 0 <= y < N and gear[y][x] == 'win'

def wput(x, y, c):
    if win(x, y):
        put(x, y, c)

def stamp(ox, oy, rows, key):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != '.' and ch in key:
                wput(ox + i, oy + j, key[ch])

# Clouds
cloud_a = ["....ww....",
           "..wwwwww..",
           ".wwwwwwwww",
           "sssssssss."]
cloud_b = ["...www..",
           ".wwwwwww",
           "ssssss.."]
stamp(16, 21, cloud_a, {'w': 'cl', 's': 'cs'})
stamp(40, 25, cloud_b, {'w': 'cl', 's': 'cs'})
stamp(13, 33, cloud_b, {'w': 'cl', 's': 'cs'})

# Far island on the horizon
stamp(39, 38, ["...gg....",
               ".gggggg..",
               "kkkkkkkkk"], {'g': 'g1', 'k': 'g0'})

# Waves
for x in range(N):
    if win(x, HORIZON + 3) and x % 5 == 1:
        wput(x, HORIZON + 3, 'w3'); wput(x + 1, HORIZON + 3, 'w3')
    if win(x, HORIZON + 8) and x % 6 == 3:
        wput(x, HORIZON + 8, 'w2'); wput(x + 1, HORIZON + 8, 'w2'); wput(x + 2, HORIZON + 8, 'w2')

exec(open('ship.py').read())


# Submarine surfacing in the foreground
stamp(17, 39, ["....OO.......",
               "...OBbO......",
               "..OOBbOOOOO..",
               ".OaaaaaaaaaaO",
               "OaWaaWaaWaaa1",
               "wwwwwwwwwwwww"], {'O': 'out', 'B': 'b4', 'b': 'b2', 'a': 'a2', 'W': 'b4', '1': 'a1', 'w': 'w3'})

# Porthole glass glint
for (x, y) in ((22, 19), (23, 18), (21, 20), (25, 17)):
    if win(x, y):
        r = px[x, y]
        put(x, y, tuple(min(255, int(v * 0.55 + 255 * 0.45)) for v in r[:3]) + (255,))

out = sys.argv[1] if len(sys.argv) > 1 else 'icon.png'
img.save(out)
img.resize((N * 8, N * 8), Image.NEAREST).save(out.replace('.png', '@8x.png'))
print('ok')
