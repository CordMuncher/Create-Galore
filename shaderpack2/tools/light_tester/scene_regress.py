# Everyday light around part blocks, to check a change to the light trace doesn't move anything it shouldn't:
# a torch in a fence pen, a lantern on a fence post, a lantern by a wall of trapdoor windows, lanterns by slabs and
# stairs, and two fence posts side by side in front of a lantern.
lh = lh  # set by light_tester.py before this runs
def fence(name, n, s, e, w):
    boxes = [((6, 0, 6), (10, 16, 10))]
    for on, lo1, hi1, lo2, hi2 in [(n, (7, 12, 0), (9, 15, 6), (7, 6, 0), (9, 9, 6)),
                                   (s, (7, 12, 10), (9, 15, 16), (7, 6, 10), (9, 9, 16)),
                                   (e, (10, 12, 7), (16, 15, 9), (10, 6, 7), (16, 9, 9)),
                                   (w, (0, 12, 7), (6, 15, 9), (0, 6, 7), (6, 9, 9))]:
        if on:
            boxes += [(lo1, hi1), (lo2, hi2)]
    return lh.add_entry(name, flags=lh.PAL_HAS_SHAPE, color=(0.6, 0.45, 0.3), boxes=tuple(boxes))

def blocks():
    E = lh.entries
    if 'grass' not in E:
        lh.add_entry('grass', flags=lh.FULL, color=(0.33, 0.5, 0.2))
        lh.add_entry('stone', flags=lh.FULL, color=(0.5, 0.5, 0.5))
        lh.add_entry('lantern', block_id=101, emission=15, flags=lh.PAL_HAS_SHAPE, color=(0.3, 0.3, 0.3),
                     glow=(1.0, 0.8, 0.4), at=(16, 7, 16),
                     boxes=(((5, 0, 5), (11, 7, 11)), ((6, 7, 6), (10, 9, 10))))
        lh.add_entry('torch', block_id=100, emission=14, flags=lh.PAL_HAS_SHAPE | lh.PAL_NO_COLLISION,
                     color=(0.5, 0.4, 0.2), glow=(1.0, 0.8, 0.4), at=(16, 20, 16), boxes=(((7, 0, 7), (9, 10, 9)),))
        for k in ['ns', 'ew', 'ne', 'nw', 'se', 'sw', 'post']:
            fence('fence_' + k, 'n' in k, 's' in k, 'e' in k, 'w' in k)
        lh.add_entry('slab', flags=lh.PAL_HAS_SHAPE, color=(0.55, 0.55, 0.55), boxes=(((0, 0, 0), (16, 8, 16)),))
        lh.add_entry('stairs', flags=lh.PAL_HAS_SHAPE, color=(0.6, 0.45, 0.3),
                     boxes=(((0, 0, 0), (16, 8, 16)), ((0, 8, 8), (16, 16, 16))))
        lh.trapdoor('trap_s', 'oak_trapdoor.png', 's', (0.6, 0.45, 0.3))
    b = {}
    for x in range(0, 48):
        for z in range(0, 48):
            b[(x, 63, z)] = E['grass']
            b[(x, 72, z)] = E['stone']
    for y in range(64, 72):
        for i in range(48):
            b[(i, y, 0)] = b[(i, y, 47)] = b[(0, y, i)] = b[(47, y, i)] = E['stone']
    # A fence pen 5 x 5 (inside 3 x 3) with a torch in the middle.
    x0, z0 = 6, 6
    for i in range(1, 4):
        b[(x0 + i, 64, z0)] = E['fence_ew']; b[(x0 + i, 64, z0 + 4)] = E['fence_ew']
        b[(x0, 64, z0 + i)] = E['fence_ns']; b[(x0 + 4, 64, z0 + i)] = E['fence_ns']
    b[(x0, 64, z0)] = E['fence_se']; b[(x0 + 4, 64, z0)] = E['fence_sw']
    b[(x0, 64, z0 + 4)] = E['fence_ne']; b[(x0 + 4, 64, z0 + 4)] = E['fence_nw']
    b[(x0 + 2, 64, z0 + 2)] = E['torch']
    # A lantern on a fence post.
    b[(24, 64, 8)] = E['fence_post']; b[(24, 65, 8)] = E['lantern']
    # A wall of open trapdoors (windows) with a lantern behind it.
    for x in range(36, 41):
        for y in (64, 65):
            b[(x, y, 8)] = E['trap_s']
    b[(38, 64, 7)] = E['lantern']
    # A lantern on a slab, slabs round it; a lantern by stairs.
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            b[(8 + dx, 64, 24 + dz)] = E['slab']
    b[(8, 65, 24)] = E['lantern']
    b[(24, 64, 24)] = E['stairs']; b[(25, 64, 24)] = E['lantern']
    # Two fence posts side by side (a gap of 12/16 between them) in front of a lantern.
    b[(37, 64, 24)] = E['fence_post']; b[(38, 64, 24)] = E['fence_post']
    b[(37, 64, 22)] = E['lantern']
    return b

VIEWS = ['pen:8.5,70,8.5,0,90,80', 'post:24.5,70,8.5,0,90,80', 'trapwall:38.5,70,10.5,0,90,80',
         'slab:8.5,70,24.5,0,90,80', 'stairs:24.5,70,24.5,0,90,80', 'posts:37.5,70,25.5,0,90,80',
         'pen-side:3.5,67,12.5,-135,25,70', 'trapwall-side:35.5,66.5,13.5,-150,25,70']
