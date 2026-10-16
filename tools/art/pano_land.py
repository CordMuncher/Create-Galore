# ---------------------------------------------------------------- land (run from pano.py)
SEA = (372, 566)          # columns of open sea at the horizon
RIVER_X = 286             # where the river comes out from under the viaduct

def warm(x):
    # 1 facing the sun, 0 facing away
    return max(0.0, 1 - wrapdist(x, SUN_X) / 200)

# Far mountains: a ring of peaks with stepped slopes, the side facing left catches the light
PEAKS = []
for i in range(34):
    PEAKS.append((int(h1(i, 31) * W), 6 + int(30 * h1(i, 32) ** 1.7), 0.55 + 0.6 * h1(i, 33)))
for x in range(W):
    best, side, ph = 0, 0, 0
    for (pxk, ht, sl) in PEAKS:
        d = (x - pxk + W // 2) % W - W // 2
        v = ht - abs(d) * sl
        # small steps so slopes look hand placed, not smooth
        v = v - (abs(d) % 3 == 2) * 0.6
        if v > best:
            best, side, ph = v, d, ht
    top = HZ - 2 - int(best)
    w = warm(x)
    for y in range(top, HZ + 1):
        lit = side <= 0
        c = ('a7b0cc' if lit else '7f88aa') if w < 0.35 else ('c2b1b8' if lit else '9b8ea6')
        depth = y - top
        if ph > 18 and depth < 3 + (x % 4 == 0) + (x % 7 == 3) and y < HZ - 14:
            c = 'f1f0f6' if lit else 'c9cbe0'
        elif y > HZ - 4:
            c = '8e9bb2' if w < 0.35 else 'a99ba8'
        put(x, y, c)

# Mid hills, greener and darker
for x in range(W):
    h = fbm(x, 23, (80, 40, 16, 6), (1, .5, .3, .12))
    top = HZ - int(1 + 14 * max(0, h - 0.3) / 0.7)
    w = warm(x)
    for y in range(top, HZ + 1):
        c = '61807a' if w < 0.4 else '7f8a7c'
        if y == top:
            c = '7a9886' if w < 0.4 else '9aa189'
        put(x, y, c)

def sea_span(y):
    """Columns of water on row y (sea narrows toward the viewer, ending in a beach)."""
    d = y - HZ
    if d < 0 or d > 44:
        return None
    k = d / 44
    left = SEA[0] + int(70 * k ** 1.6 + 3 * math.sin(d * 0.7))
    right = SEA[1] - int(60 * k ** 1.4 + 3 * math.sin(d * 0.5 + 1))
    return left, right

def river_span(y):
    d = y - HZ - 8
    if d < 0:
        return None
    c = RIVER_X + int(d * 0.55 + 4 * math.sin(d * 0.09))
    half = 1 + d * 0.32
    return int(c - half), int(c + half)

# Ground: hazy near the horizon, saturated in the foreground
GROUND = [(4, '7f9a6a'), (10, '6f9459'), (20, '5f8b4a'), (34, '518042'), (56, '467339'), (90, '3c6631'), (999, '33582b')]
for y in range(HZ + 1, H):
    d = y - HZ
    for x in range(W):
        for lim, c in GROUND:
            if d <= lim:
                break
        # grass tufts: short dashes that get longer toward the viewer
        L = 1 + d // 25
        n = h1((x // max(1, L)) * 7 + y * 131, 3)
        if d > 5 and n < 0.07:
            c = '72a253' if d < 40 else '62964a'
        elif d > 5 and n > 0.94:
            c = '416e36' if d < 40 else '2f5428'
        put(x, y, c)

# Sea
for y in range(HZ, HZ + 46):
    sp = sea_span(y)
    if not sp:
        continue
    l, r = sp
    d = y - HZ
    for x in range(l - 3, r + 4):
        if x < l or x > r:
            put(x, y, 'e2cf9a' if d > 2 else 'b9b590')  # sand
            continue
        c = '6f8fb4' if d < 2 else '557ea6' if d < 8 else '426d98' if d < 20 else '365f8a'
        # glitter under the sun
        if abs(x - SUN_X) < 3 + d * 0.5 and h1(x + y * 977, 5) < 0.3 - d * 0.006:
            c = 'ffe6b0' if d < 12 else 'f2d39c'
        elif h1(x * 3 + y * 619, 6) < 0.05 and d > 4:
            c = '7aa3c8'
        put(x, y, c)
# Waves breaking on the beach
for y in range(HZ + 4, HZ + 46):
    sp = sea_span(y)
    if sp:
        for x in (sp[0], sp[1]):
            put(x, y, 'e9f2f6')

# River
for y in range(HZ + 8, H):
    sp = river_span(y)
    l, r = sp
    d = y - HZ
    for x in range(l - 1, r + 2):
        if x < l or x > r:
            put(x, y, '6b7d4a' if d < 40 else '58603d')  # muddy bank
            continue
        c = '5f86a8' if d < 20 else '4f7a9e' if d < 60 else '41698c'
        if h1(x * 5 + y * 311, 8) < 0.06:
            c = '8db4d0'
        put(x, y, c)

if __name__ == '__main__' and 'land' in sys.argv:
    img.save('stage.png'); sys.exit()

exec(open('pano_props.py').read())
