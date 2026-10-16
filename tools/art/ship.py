# Airship: copper envelope (nose left) with brass bands, tail fins, propeller, wooden gondola.
SX, SY, SA, SB = 30.0, 26.5, 12.2, 5.6
env = set()
for y in range(N):
    for x in range(N):
        dx, dy = x - SX, y - SY
        b = SB * (1 - 0.32 * max(0.0, dx / SA)) if dx > 0 else SB
        if (dx / SA) ** 2 + (dy / b) ** 2 <= 1.0:
            env.add((x, y))
# Tail fins (above and below, plus a side fin pointing at the viewer)
fins = {}
for (x, y, c) in [
    (40, 20, 'e1'), (41, 20, 'e1'), (40, 21, 'e2'), (41, 21, 'e1'), (42, 21, 'e1'), (39, 22, 'e2'), (40, 22, 'e2'), (41, 22, 'e1'), (42, 22, 'e1'),
    (40, 32, 'e1'), (41, 32, 'e0'), (40, 31, 'e1'), (41, 31, 'e1'), (42, 31, 'e0'), (39, 30, 'e1'), (40, 30, 'e1'), (41, 30, 'e0'), (42, 30, 'e0'),
]:
    fins[(x, y)] = c
shape = env | set(fins)
for (x, y) in shape:
    if (x, y) in fins:
        wput(x, y, fins[(x, y)]); continue
    t = (y - (SY - SB)) / (2 * SB)
    c = 'e3' if t < 0.25 else 'e2' if t < 0.55 else 'e1' if t < 0.82 else 'e0'
    if x > SX + 6 and c in ('e3', 'e2'):
        c = {'e3': 'e2', 'e2': 'e1'}[c]
    if x in (24, 33):
        c = 'b4' if t < 0.3 else 'b3' if t < 0.7 else 'b2'
    wput(x, y, c)
# Nose highlight
for (x, y) in ((20, 25), (21, 24), (22, 23), (23, 23)):
    if (x, y) in env:
        wput(x, y, 'b5')
# Outline
for (x, y) in shape:
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (x + dx, y + dy)
        if q not in shape:
            wput(*q, 'out')
# Propeller hub + blades at the tail
for (x, y, c) in [(44, 26, 'a1'), (44, 27, 'out'), (45, 26, 'a3'), (45, 27, 'a2'), (46, 23, 'a3'), (46, 24, 'a3'), (46, 25, 'a2'),
                  (46, 28, 'a2'), (46, 29, 'a1'), (46, 30, 'a1'), (46, 26, 'out'), (46, 27, 'out')]:
    wput(x, y, c)
# Ropes and gondola
for y in (33, 34):
    wput(26, y, 'k0'); wput(33, y, 'k0')
gondola = ["OOOOOOOOOOOO",
           "OKKKKKKKKKKO",
           "OkWkkWkkWkkO",
           ".OkkkkkkkkO.",
           "..OOOOOOOO.."]
stamp(24, 35, gondola, {'O': 'out', 'K': 'k2', 'k': 'k1', 'W': 'b4'})
