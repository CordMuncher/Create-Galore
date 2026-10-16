# ---------------------------------------------------------------- props (run from pano_land.py)
OUT = '2a2220'
K = {
    'O': OUT, 'S': '3b3836', 's': '5a5552',
    'B': 'f0cc6a', 'b': 'c8962f', 'n': '8a5e1c',             # brass light / mid / dark
    'A': 'b4b7b0', 'a': '8a8d87', 'd': '5f625e', 'D': '454846',  # andesite
    'C': 'e08a52', 'c': 'b85a32',                            # copper
    'R': 'b8452e', 'r': '8a3022',                            # red paint
    'W': 'ffe39a', 'w': 'e8b860',                            # lit window
    'P': '9c6b3c', 'p': '74502c', 'q': '573a20',              # spruce planks
    'G': '5b8f45', 'g': '3e6b33', 'h': '2d5227',              # leaves
    'L': '6d5a44', 'l': '4b3c2c',                            # log
    'X': 'efe9dc', 'x': 'cfc6b4',                            # white wool / sail
    'T': '7d7b7a', 't': '5e5c5b',                            # stone
    'Y': 'e8c35a', 'y': 'b89a3a',                            # wheat
    'V': '9aa7b5',                                           # glass
}

def sprite(ox, oy, rows, key=K, flip=False):
    for j, row in enumerate(rows):
        if flip:
            row = row[::-1]
        for i, ch in enumerate(row):
            if ch != '.' and ch in key:
                put(ox + i, oy + j, key[ch])

def outline_set(cells, c=OUT):
    for (x, y) in list(cells):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in cells:
                put(x + dx, y + dy, c)

def smoke(x0, y0, n, seed, drift=1.2):
    for k in range(n):
        r = 1.6 + k * 0.55
        cx = x0 + k * drift * 2.6 + (h1(k, seed) - 0.5) * 2
        cy = y0 - k * 3.4
        tone = ['e9e4dc', 'd9d3cb', 'c9c3bd', 'bcb7b2'][min(3, k // 2)]
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                dd = (x - cx) ** 2 + (y - cy) ** 2
                if dd <= r * r and (k < 5 or dith(x, y, 1 - k * 0.07)):
                    put(x, y, tone if dd < (r - 1) ** 2 or y > cy else 'f6f2ea')

# ---------- trees
OAK = ["..GGG..",
       ".GGGGG.",
       "GGGgGGG",
       "GgGGGgG",
       ".gggGg.",
       "..hLh..",
       "...L..."]
SPRUCE = ["..g..",
          ".ggg.",
          "..g..",
          ".ggg.",
          "ggghg",
          ".ghg.",
          "gghgg",
          "hghgh",
          "..l.."]
OAK_BIG = ["....GGGGG....",
           "..GGGGGGGGG..",
           ".GGGGGgGGGGG.",
           "GGGgGGGGGgGGG",
           "GGGGGGGGGGGgG",
           "GgGGGgGGGGGGG",
           ".gggGGgggGgg.",
           "..ggghgghgg..",
           ".....lLl.....",
           ".....lLl.....",
           ".....lLl.....",
           "....llLll...."]

def forest(x0, x1, base, seed, density=0.55, big=False):
    x = x0
    k = 0
    while x < x1:
        k += 1
        if h1(k, seed) < density:
            spr = SPRUCE if h1(k, seed + 1) < 0.45 else OAK
            jitter = int(h1(k, seed + 2) * 3)
            sprite(x, base - len(spr) + 1 + jitter, spr)
        x += 4 + int(h1(k, seed + 3) * 4)

forest(560, 600, 183, 1, 0.8)
forest(692, 760, 183, 2, 0.8)
forest(170, 196, 184, 3, 0.7)
forest(352, 372, 183, 4, 0.7)

# ---------- airships
def airship(cx, cy, a, b, seed, scale=1, face_left=True, env=('e8935a', 'd0703e', 'a8502e', '7a3624')):
    cells = set()
    for y in range(int(cy - b - 2), int(cy + b + 3)):
        for x in range(int(cx - a - 2), int(cx + a + 3)):
            dx = (x - cx) * (1 if face_left else -1)
            bb = b * (1 - 0.3 * max(0.0, dx / a)) if dx > 0 else b
            if (dx / a) ** 2 + ((y - cy) / bb) ** 2 <= 1:
                cells.add((x, y))
    sgn = 1 if face_left else -1
    # tail fins
    tx = cx + sgn * a * 0.78
    fin = set()
    fh = max(2, int(b * 0.9))
    for i in range(int(a * 0.3)):
        x = int(tx + sgn * i * 0.9)
        hgt = int(fh * (0.4 + i / (a * 0.3)))
        for j in range(hgt):
            fin.add((x, int(cy - b * 0.55) - j))
            fin.add((x, int(cy + b * 0.55) + j))
    for (x, y) in fin:
        put(x, y, env[2] if y < cy else env[3])
    bands = [cx - sgn * a * 0.45, cx + sgn * a * 0.05, cx + sgn * a * 0.5]
    for (x, y) in cells:
        t = (y - (cy - b)) / (2 * b)
        c = env[0] if t < 0.28 else env[1] if t < 0.6 else env[2] if t < 0.85 else env[3]
        if any(abs(x - bx) < 0.5 + 0.4 * scale for bx in bands):
            c = K['B'] if t < 0.3 else K['b'] if t < 0.75 else K['n']
        put(x, y, c)
    # glint along the top
    for i in range(int(a * 0.8)):
        x = int(cx - sgn * a * 0.55 + sgn * i)
        top = min((yy for (xx, yy) in cells if xx == x), default=None)
        if top is not None and h1(i, seed) < 0.75:
            put(x, top + 1, 'ffd0a0')
    outline_set(cells | fin)
    # gondola
    gw, gh = max(6, int(a * 0.62)), max(3, int(b * 0.6))
    gx0, gy0 = int(cx - gw / 2 - sgn * a * 0.05), int(cy + b + 2 + max(1, b * 0.25))
    for x in (gx0 + 2, gx0 + gw - 3):
        vline(x, int(cy + b), gy0 - 1, '3b2a1a')
    g = set()
    for y in range(gy0, gy0 + gh):
        inset = 1 if y == gy0 + gh - 1 else 0
        for x in range(gx0 + inset, gx0 + gw - inset):
            g.add((x, y))
            put(x, y, K['P'] if y == gy0 else K['p'])
    for x in range(gx0 + 2, gx0 + gw - 2, 3):
        if gh >= 3:
            put(x, gy0 + 1, K['W'])
    outline_set(g)
    # propeller on the tail
    px_ = int(cx + sgn * (a + 2))
    for j in range(-int(b * 0.7), int(b * 0.7) + 1):
        put(px_ + sgn, int(cy) + j, 'c9cbc4' if j < 0 else '8a8d87')
    put(px_, int(cy), K['B'])

airship(612, 134, 40, 12, 1, scale=2)
airship(150, 128, 18, 6, 2, scale=1, face_left=False)
airship(404, 158, 8, 3, 3, scale=0.5)

# ---------- factory town (x 20..170)
TB = 192  # street level
def building(x0, w, h, wall, roof, seed, roof_kind='flat', windows=True):
    y0 = TB - h
    for y in range(y0, TB + 1):
        for x in range(x0, x0 + w):
            c = K[wall[0]]
            if x == x0 or y == y0:
                c = K[wall[1]]
            if x == x0 + w - 1:
                c = K[wall[2]]
            put(x, y, c)
    if windows:
        for y in range(y0 + 3, TB - 2, 5):
            for x in range(x0 + 2, x0 + w - 3, 4):
                lit_ = h1(x * 13 + y, seed) < 0.55
                put(x, y, K['W'] if lit_ else K['V']); put(x + 1, y, K['w'] if lit_ else K['V'])
                put(x, y + 1, K['w'] if lit_ else K['d']); put(x + 1, y + 1, K['w'] if lit_ else K['d'])
    # brass trim under the roof
    hline(x0, x0 + w - 1, y0 + 1, K['b'])
    if roof_kind == 'gable':
        for i in range((w + 1) // 2 + 1):
            hline(x0 - 1 + i, x0 + w - i, y0 - 1 - i, K[roof[0]] if i % 2 == 0 else K[roof[1]])
            put(x0 - 1 + i, y0 - 1 - i, OUT); put(x0 + w - i, y0 - 1 - i, OUT)
    else:
        hline(x0 - 1, x0 + w, y0 - 1, K[roof[0]]); hline(x0 - 1, x0 + w, y0 - 2, OUT)
    vline(x0 - 1, y0, TB, OUT); vline(x0 + w, y0, TB, OUT)

def cog(cx, cy, r, teeth, rot, light='B', mid='b', dark='n'):
    cells = set()
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            d = math.hypot(x - cx, y - cy)
            an = (math.atan2(y - cy, x - cx) + rot) / (2 * math.pi) * teeth % 1
            if d <= r or (d <= r + 1.6 and abs(an - 0.5) < 0.22):
                cells.add((x, y))
    for (x, y) in cells:
        d = math.hypot(x - cx, y - cy)
        l = (-(x - cx) - (y - cy)) / (d or 1)
        c = K[light] if l > 0.4 else K[mid] if l > -0.4 else K[dark]
        if d < r * 0.38:
            c = K['a']
        if d < 1:
            c = K['D']
        put(x, y, c)
    outline_set(cells)

# Windmill: Create windmill bearing with four sail arms on an andesite tower
WMX, WMY = 44, 148
for y in range(WMY, TB + 1):
    half = 4 + (y - WMY) // 9
    for x in range(WMX - half, WMX + half + 1):
        c = K['a'] if x < WMX + half - 1 else K['d']
        if x == WMX - half:
            c = K['A']
        if (y - WMY) % 8 == 0:
            c = K['b']
        put(x, y, c)
    put(WMX - half - 1, y, OUT); put(WMX + half + 1, y, OUT)
stamp_rows = ["OOOOO", "ObBbO", "OBBbO", "ObbnO", "OOOOO"]
for k in range(4):
    an = math.radians(25 + 90 * k)
    ux, uy = math.cos(an), math.sin(an)
    vx, vy = -uy, ux
    for t10 in range(30, 260):
        t = t10 / 10
        for s10 in range(-45, 46):
            s = s10 / 10
            x = round(WMX + ux * t + vx * s); y = round(WMY + uy * t + vy * s)
            if abs(s) < 0.6:
                put(x, y, K['p'])  # spar
            elif t > 6 and 0.6 <= s <= 4.4:
                frame = abs(s - 4.4) < 0.6 or t > 25.2 or int(t) % 6 == 0
                put(x, y, K['q'] if frame else (K['X'] if k % 2 == 0 else K['x']))
sprite(WMX - 2, WMY - 2, stamp_rows)

building(58, 22, 22, 'adA', 'cn', 4, 'gable')
building(84, 30, 30, 'adA', 'BO', 5, 'flat')
building(118, 16, 18, 'pqP', 'Rr', 6, 'gable')
building(138, 26, 24, 'adA', 'cn', 7, 'gable')
# Chimneys with smoke
for (cx, top) in ((92, 140), (104, 148)):
    for y in range(top, TB - 30):
        hline(cx, cx + 3, y, K['R'] if (y // 2) % 2 == 0 else K['r'])
        put(cx - 1, y, OUT); put(cx + 4, y, OUT)
    hline(cx - 1, cx + 4, top - 1, OUT); hline(cx - 1, cx + 4, top, K['d'])
smoke(93, 136, 9, 1)
smoke(105, 144, 7, 2)
# Big cog on the factory wall and a smaller one meshing with it
cog(98, 178, 6, 10, 0.1)
cog(110, 172, 3.2, 6, 0.4, 'A', 'a', 'd')
# Copper fluid tank
for y in range(170, TB + 1):
    for x in range(160, 170):
        put(x, y, K['C'] if x < 166 else K['c'])
    put(159, y, OUT); put(170, y, OUT)
hline(159, 170, 169, OUT); hline(160, 169, 172, K['b']); hline(160, 169, 184, K['b'])
# Belt line on the ground in front of the town
hline(20, 172, TB + 1, K['D']); hline(20, 172, TB + 2, K['S'])
for x in range(20, 173, 3):
    put(x, TB + 1, K['d'])

# ---------- viaduct and train (x 196..364)
DECK = 172
V0, V1 = 196, 364
for y in range(DECK, DECK + 4):
    hline(V0, V1, y, K['A'] if y == DECK else K['a'] if y < DECK + 3 else K['d'])
hline(V0, V1, DECK - 1, OUT); hline(V0, V1, DECK + 4, OUT)
# rails
hline(V0, V1, DECK - 2, '6e6a66')
for x in range(V0, V1 + 1, 3):
    put(x, DECK - 2, K['p'])
PIER_W, SPAN = 6, 22
piers = list(range(V0 + 2, V1 - 4, SPAN))
for i, x0 in enumerate(piers):
    bottom = 205 + int(h1(i, 41) * 3)
    for y in range(DECK + 5, bottom):
        for x in range(x0, x0 + PIER_W):
            c = K['T'] if x < x0 + PIER_W - 2 else K['t']
            if (y + (x // 3)) % 6 == 0:
                c = '6a6866'
            put(x, y, c)
        put(x0 - 1, y, OUT); put(x0 + PIER_W, y, OUT)
    # arch to the next pier
    if i + 1 < len(piers):
        a0, a1 = x0 + PIER_W, piers[i + 1] - 1
        mid, half = (a0 + a1) / 2, (a1 - a0) / 2 + 0.5
        for x in range(a0, a1 + 1):
            k = (x - mid) / half
            depth = int(3 + 8 * (k * k))
            for y in range(DECK + 5, DECK + 5 + depth):
                put(x, y, K['T'] if y < DECK + 4 + depth else K['t'])
            put(x, DECK + 5 + depth, OUT)

LOCO = [
    "......OOO..........................",
    "......OSO..........................",
    ".....OOSOO..........OO.....OOOOOOOO",
    ".....OsSsO.........ObbO....OrrrrrrO",
    "OOOOOOOOOOOOOOOOOOOObbOOOOOORrRRrRO",
    "OCBBBBBBBBBBBBBBBBBBBBBBBBBOWWrWWrO",
    "OCbBbbbbBbbbbbbbBbbbbbbbbbbOWWrWWrO",
    "OCbbbbbbbbbbbbbbbbbbbbbbbbbORRRRRRO",
    "OcnnnnnnnnnnnnnnnnnnnnnnnnnOrrrrrrO",
    "OOddddddddddddddddddddddddddddddddO",
    "ODDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDO",
    ".OObbOOObbOOOObbOOOObbOOOOOOObbOOO.",
    "..ObnO.ObnO..ObnO..ObnO.....ObnO...",
    "...OO...OO....OO....OO.......OO....",
]
CAR = [
    "OOOOOOOOOOOOOOOOOOOOOOOOOO",
    "OAAAAAAAAAAAAAAAAAAAAAAAAO",
    "OPPPPPPPPPPPPPPPPPPPPPPPPO",
    "OPWWPPWWPPWWPPWWPPWWPPWWPO",
    "OpWwppWwppWwppWwppWwppWwpO",
    "OppppppppppppppppppppppppO",
    "OqqqqqqqqqqqqqqqqqqqqqqqqO",
    "ODDDDDDDDDDDDDDDDDDDDDDDDO",
    ".OObbOOOObbOOOOObbOOOObbO.",
    "..ObnO..ObnO...ObnO..ObnO.",
    "...OO....OO.....OO....OO..",
]
TRAIN_X = 218
ty = DECK - 2 - len(LOCO) + 1
sprite(TRAIN_X, ty, LOCO)
cx = TRAIN_X + len(LOCO[0]) + 1
for n in range(3):
    sprite(cx, DECK - 2 - len(CAR) + 1, CAR)
    put(cx - 1, DECK - 5, OUT)
    cx += len(CAR[0]) + 1
smoke(TRAIN_X + 7, ty - 2, 10, 5, drift=2.2)

# ---------- lighthouse on a rock in the sea
LX, LB = 540, 188
for y in range(LB - 4, LB + 3):
    w_ = 9 - (LB - y) if y < LB else 10
    for x in range(LX - w_, LX + w_ + 1):
        put(x, y, K['t'] if x > LX + 2 else K['T'])
for y in range(LB - 30, LB - 3):
    half = 2 + (y - (LB - 30)) // 9
    band = ((y - (LB - 30)) // 5) % 2
    for x in range(LX - half, LX + half + 1):
        c = (K['R'] if band else 'f2ece2')
        if x >= LX + half - 1:
            c = K['r'] if band else 'c9c2b6'
        put(x, y, c)
    put(LX - half - 1, y, OUT); put(LX + half + 1, y, OUT)
sprite(LX - 4, LB - 38, ["..OOOOO..",
                         ".OnbbbnO.",
                         "OOOOOOOOO",
                         ".OWWWWWO.",
                         ".OWWWwwO.",
                         "OOOOOOOOO",
                         ".ObbbbbO.",
                         "..OOOOO.."])

# ---------- submarine surfacing (Create Submarine vibes: brass and andesite)
SUB = [
    "............OO..............",
    "...........OVO..............",
    "...........OaO..............",
    ".........OOOaOOO............",
    ".........OAAAAAaO...........",
    ".........OBWBWBbO...........",
    ".....OOOOOaaaaaadOOOOOOO....",
    "...OOAAAAAAAAAAAAAAAAAAAOO..",
    ".OOBAWAAWAAWAAWAAWAAAAAAAdOO",
    "OAABAAAAAAAAAAAAAAAAAAAaadddO",
    ".OOnaaaaaaaaaaaaaaaaaaaddOO.",
]
sprite(420, 192, SUB)
for x in range(414, 455):
    if h1(x, 51) < 0.5:
        put(x, 203, 'e9f2f6')
    put(x, 202 + (x % 3 == 0), '557ea6')

# ---------- farm with a cottage (x 588..700)
for y in range(190, 214):
    d = y - 190
    left = 596 - d // 2
    right = 690 + d // 3
    for x in range(left, right):
        # crop rows run away from the viewer: wheat, then carrots/potatoes (green)
        band = (d // 4) % 3
        c = [K['Y'], '7fae4a', K['y']][band]
        if (x * 3 + y) % 4 == 0:
            c = ['d8b14c', '5f9139', 'a3893a'][band]
        if d % 4 == 3:
            c = '6b5a3a'
        put(x, y, c)
# fence
for x in range(590, 702):
    put(x, 214, K['p'])
    if x % 6 == 0:
        vline(x, 212, 216, K['q'])
COTTAGE = [
    "..........OO.................",
    ".........ORRO...........OO...",
    "........ORRRRO..........OsO..",
    ".......ORRrRRRO.........OsO..",
    "......ORRRRRrRRO........OsO..",
    ".....ORRrRRRRRRRO.......OsO..",
    "....ORRRRRRRrRRRRO......OsO..",
    "...ORRRRrRRRRRRRRRO.....OsO..",
    "..OOOOOOOOOOOOOOOOOOOOOOOOOO.",
    "...OLPPPPPPPPPPPPPPPPPPPPLO..",
    "...OLPWWPPPPPPqqPPPPPWWPPLO..",
    "...OLPWwPPPPPPqqPPPPPWwPPLO..",
    "...OLPPPPPPPPPqqPPPPPPPPPLO..",
    "...OLppppppppppqqpppppppppLO.",
    "...OOOOOOOOOOOOOOOOOOOOOOOOO.",
]
sprite(640, 187 - len(COTTAGE) + 1, COTTAGE)
smoke(666, 172, 6, 9)

# ---------- foreground: big trees at the edges of view, bushes and flowers
def sprite_scaled(ox, oy, rows, sc, key=K):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != '.' and ch in key:
                for yy in range(sc):
                    for xx in range(sc):
                        put(ox + i * sc + xx, oy + j * sc + yy, key[ch])

def outlined_scaled(ox, oy, rows, sc):
    cells = set()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != '.':
                for yy in range(sc):
                    for xx in range(sc):
                        cells.add((ox + i * sc + xx, oy + j * sc + yy))
    outline_set(cells, '213d1d')
    sprite_scaled(ox, oy, rows, sc)

sprite(186, 208, OAK_BIG)
outlined_scaled(548, 208, OAK_BIG, 2)
outlined_scaled(196, 232, OAK_BIG, 3)
outlined_scaled(690, 222, OAK_BIG, 3)
outlined_scaled(452, 250, SPRUCE, 3)
BUSH = ["..GGGG..", ".GGgGGG.", "GGGGGgGG", "gGgggGgg"]
for (bx, by, sc) in ((20, 236, 2), (120, 250, 3), (340, 238, 2), (610, 244, 3), (520, 266, 2), (60, 280, 3), (380, 300, 3), (640, 300, 3)):
    outlined_scaled(bx, by, BUSH, sc)
ROCK = ["..OOO..", ".OAAaO.", "OAAaadO", "OaadddO", ".OOOOO."]
for (rx, ry, sc) in ((230, 262, 2), (90, 226, 1), (590, 280, 2), (420, 226, 1)):
    sprite_scaled(rx, ry, ROCK, sc)
FLOWERS = [('e8443a', 'f07a6c'), ('f2d23c', 'fff08a'), ('6c8ff0', 'a4bdf8'), ('f2f2f2', 'ffffff'), ('d070d8', 'e8a4ee')]
for i in range(520):
    fx = int(h1(i, 61) * W)
    fy = HZ + 24 + int((h1(i, 62) ** 0.8) * 150)
    # keep water clear
    sp = river_span(fy)
    if sp and sp[0] - 2 <= fx <= sp[1] + 2:
        continue
    if sea_span(fy) and sea_span(fy)[0] - 4 <= fx <= sea_span(fy)[1] + 4:
        continue
    c0, c1 = FLOWERS[int(h1(i, 63) * len(FLOWERS))]
    size = 1 if fy < 230 else 2
    if size == 1:
        put(fx, fy, c0)
    else:
        put(fx, fy, c1); put(fx + 1, fy, c0); put(fx, fy + 1, c0); put(fx + 1, fy + 1, '2f5428')
        put(fx, fy + 2, '2f5428')

out = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1].endswith('.png') else 'pano.png'
img.save(out)
print('saved', out)
