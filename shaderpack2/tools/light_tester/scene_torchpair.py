# The 0.6.1 test's torch pair at night (the "pointy" shot): a soul lantern, a redstone torch and a torch standing on
# a short row of blocks, on a grass floor in a closed stone room.
lh = lh  # set by light_tester.py before this runs
def blocks():
    E = lh.entries
    if 'grass' not in E:
        lh.add_entry('grass', flags=lh.FULL, color=(0.33, 0.5, 0.2))
        lh.add_entry('stone', flags=lh.FULL, color=(0.5, 0.5, 0.5))
        lh.add_entry('cyan', flags=lh.FULL, color=(0.2, 0.55, 0.65))
        lh.add_entry('red', flags=lh.FULL, color=(0.55, 0.15, 0.2))
        lh.add_entry('soul_lantern', block_id=103, emission=15, flags=lh.PAL_HAS_SHAPE, color=(0.3, 0.3, 0.3),
                     glow=(0.3, 0.9, 1.0), at=(16, 7, 16),
                     boxes=(((5, 0, 5), (11, 7, 11)), ((6, 7, 6), (10, 9, 10))))
        lh.add_entry('torch', block_id=100, emission=14, flags=lh.PAL_HAS_SHAPE | lh.PAL_NO_COLLISION,
                     color=(0.5, 0.4, 0.2), glow=(1.0, 0.8, 0.4), at=(16, 20, 16), boxes=(((7, 0, 7), (9, 10, 9)),))
        lh.add_entry('redstone_torch', block_id=112, emission=7, flags=lh.PAL_HAS_SHAPE | lh.PAL_NO_COLLISION,
                     color=(0.5, 0.2, 0.2), glow=(1.0, 0.1, 0.1), at=(16, 20, 16), boxes=(((7, 0, 7), (9, 10, 9)),))
    b = {}
    for x in range(0, 24):
        for z in range(0, 24):
            b[(x, 63, z)] = E['grass']
            b[(x, 72, z)] = E['stone']
    for y in range(64, 72):
        for i in range(24):
            b[(i, y, 0)] = b[(i, y, 23)] = b[(0, y, i)] = b[(23, y, i)] = E['stone']
    # One row of single blocks with the soul lantern on its right end and the torches on the blocks behind its left
    # end, all one block high: the lights sit just above the tops their light skims across.
    for x in (9, 10, 11, 12):
        b[(x, 64, 10)] = E['cyan'] if x < 11 else E['red']
    for x in (9, 10):
        b[(x, 64, 9)] = E['red']
    b[(9, 65, 10)] = E['redstone_torch']
    b[(10, 65, 9)] = E['torch']
    b[(12, 65, 10)] = E['soul_lantern']
    return b

VIEWS = ['front:11.0,66.6,13.5,180,40,80', 'top:11.0,71.5,10.5,0,90,70']
