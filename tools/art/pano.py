"""Create Galore title-screen panorama, drawn as 720x360 pixel art (0.5 degree per pixel, equirectangular).

Row 0 is straight up, row 180 the horizon, row 359 straight down. Column 0 faces panorama_0 (front),
columns grow to the right. cube.py turns this into the six panorama_N.png faces Minecraft wants.
"""
import math, sys
from PIL import Image

W, H, HZ = 720, 360, 180

def hx(s):
    s = s.lstrip('#'); return tuple(int(s[i:i+2], 16) for i in (0, 2, 4))

img = Image.new('RGB', (W, H))
px = img.load()

def put(x, y, c):
    if 0 <= y < H:
        px[x % W, y] = hx(c) if isinstance(c, str) else c

def get(x, y):
    return px[x % W, y]

def rect(x0, y0, x1, y1, c):  # inclusive
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(x, y, c)

def hline(x0, x1, y, c):
    for x in range(x0, x1 + 1):
        put(x, y, c)

def vline(x, y0, y1, c):
    for y in range(y0, y1 + 1):
        put(x, y, c)

def stamp(ox, oy, rows, key):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in key:
                put(ox + i, oy + j, key[ch])

def disc(cx, cy, r, c):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                put(x, y, c)

# Deterministic value noise that wraps around the 720 columns.
def h1(i, seed):
    n = (i * 374761393 + seed * 668265263) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 0xFFFF

def noise(x, period, seed):
    cells = W // period
    t = x / period
    i = int(math.floor(t)); f = t - i
    a, b = h1(i % cells, seed), h1((i + 1) % cells, seed)
    f = f * f * (3 - 2 * f)
    return a + (b - a) * f

def fbm(x, seed, periods=(90, 45, 18, 9), amps=(1, .5, .25, .12)):
    return sum(a * noise(x, p, seed + k) for k, (p, a) in enumerate(zip(periods, amps))) / sum(amps)

def wrapdist(x, c):
    d = abs((x - c) % W)
    return min(d, W - d)

BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]

def dith(x, y, t):  # True with probability t, ordered
    return BAYER[y % 4][x % 4] < t * 16

# ---------------------------------------------------------------- sky
SUN_X, SUN_Y = 468, 150
SKY = ['1d3263', '24407a', '2d5190', '3a64a4', '4c7ab5', '6390c3', '7fa6cd', '9dbbd3', 'bccbd2', 'd9d3c4', 'eed2ae', 'f6c894']
# Elevation of each band's lower edge, in rows (top is row 0).
SKY_ROWS = [40, 66, 86, 102, 116, 128, 139, 149, 158, 166, 173, 180]

def sky_color(x, y):
    # Bands, with a 4x4 ordered dither on the edge between two bands.
    prev = 0
    for i, edge in enumerate(SKY_ROWS):
        if y < edge:
            span = edge - prev
            t = (y - prev) / span
            if i + 1 < len(SKY) and t > 0.7 and dith(x, y, (t - 0.7) / 0.3 * 0.5):
                return SKY[i + 1]
            return SKY[i]
        prev = edge
    return SKY[-1]

for y in range(HZ + 4):
    for x in range(W):
        put(x, y, sky_color(x, y))

# Warm glow around the sun: the sky colour pulled toward gold in dithered steps
def mix(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))

GOLD = hx('ffdca0')
for y in range(SUN_Y - 40, HZ + 4):
    for x in range(SUN_X - 50, SUN_X + 51):
        d = math.hypot((x - SUN_X) * 0.75, y - SUN_Y)
        t = max(0.0, 1 - d / 36)
        if t <= 0:
            continue
        q = t * 4
        lvl = int(q) + (1 if dith(x, y, q - int(q)) else 0)
        if lvl:
            put(x, y, mix(hx(sky_color(x, y)), GOLD, min(1.0, lvl * 0.24)))
disc(SUN_X, SUN_Y, 9.5, 'ffeec4')
disc(SUN_X, SUN_Y, 6.2, 'fffbea')

# The Moon, pale in the afternoon sky, with a few craters
MOON_X, MOON_Y = 300, 114
disc(MOON_X, MOON_Y, 7.3, 'c9d2e2')
disc(MOON_X - 1, MOON_Y - 1, 6.2, 'dfe5ef')
for (cx, cy, r) in ((MOON_X - 3, MOON_Y - 2, 1.4), (MOON_X + 2, MOON_Y + 2, 1.9), (MOON_X + 3, MOON_Y - 3, 0.9)):
    disc(cx, cy, r, 'bcc6d8')
# Stars faintly near the zenith
for i in range(90):
    sx, sy = int(h1(i, 7) * W), int(h1(i, 8) * 40)
    put(sx, sy, '6d82b4' if h1(i, 9) < 0.7 else 'a9b8dc')

# ---------------------------------------------------------------- clouds (blocky, Minecraft style)
def cloud(cx, cy, w, h, seed, lit='fbf3e6', mid='ecdccb', shade='c9b6b0', edge=None):
    # Union of a few slabs; flat bottom, stepped top.
    slabs = []
    for k in range(4):
        sw = int(w * (0.35 + 0.4 * h1(k, seed)))
        sx = cx - w // 2 + int((w - sw) * h1(k + 10, seed))
        sh = max(2, int(h * (0.45 + 0.55 * h1(k + 20, seed))))
        slabs.append((sx, sx + sw, cy - sh, cy))
    slabs.append((cx - w // 2, cx + w // 2, cy - max(2, h // 3), cy))
    cells = set()
    for (x0, x1, y0, y1) in slabs:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                cells.add((x, y))
    for (x, y) in cells:
        c = mid
        if (x, y - 1) not in cells:
            c = lit
        elif (x, y + 1) not in cells or (x, y + 2) not in cells:
            c = shade
        put(x, y, c)

# Sun-side clouds are warmer.
CLOUDS = [
    (40, 118, 44, 11, 1), (178, 104, 58, 13, 2), (250, 130, 36, 9, 3), (330, 92, 64, 14, 4), (402, 134, 34, 8, 5),
    (528, 110, 50, 11, 6), (606, 138, 32, 8, 7), (690, 98, 54, 12, 8), (300, 152, 26, 6, 9), (566, 156, 28, 6, 10),
    (126, 148, 30, 6, 11), (436, 86, 38, 9, 12),
]
for (cx, cy, w, h, s) in CLOUDS:
    near_sun = wrapdist(cx, SUN_X) < 90
    if near_sun:
        cloud(cx, cy, w, h, s, lit='fff0d0', mid='f6d6aa', shade='d7a98c')
    else:
        cloud(cx, cy, w, h, s)

if __name__ == '__main__' and 'sky' in sys.argv:
    img.save('stage.png'); sys.exit()

exec(open('pano_land.py').read())
