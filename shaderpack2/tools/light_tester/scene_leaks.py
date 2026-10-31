# The two leak setups from the 0.6 test (shots a09 and a10), side by side on a grass floor in a closed stone room
# (no sky).
lh = lh  # set by light_tester.py before this runs
def blocks():
    E = lh.entries
    if 'grass' not in E:
        lh.add_entry('grass', flags=lh.FULL, color=(0.33, 0.5, 0.2))
        lh.add_entry('stone', flags=lh.FULL, color=(0.5, 0.5, 0.5))
        lh.add_entry('cut_copper', flags=lh.FULL, color=(0.75, 0.45, 0.33))
        lh.add_entry('lantern', block_id=101, emission=15, flags=lh.PAL_HAS_SHAPE, color=(0.3, 0.3, 0.3),
                     glow=(1.0, 0.8, 0.4), at=(16, 7, 16),
                     boxes=(((5, 0, 5), (11, 7, 11)), ((6, 7, 6), (10, 9, 10))))
        g = lh.tex_open('oxidized_copper_grate.png')
        lh.add_entry('grate', flags=lh.PAL_HAS_SHAPE | lh.PAL_FULL_CUBE | lh.PAL_HOLES, color=(0.3, 0.6, 0.5),
                     alpha=0.85,
                     masks=[(lh.face_mask(0, g), lh.face_mask(1, g), lh.face_mask(2, g))])
        lv = lh.tex_open('oak_leaves.png')
        lh.add_entry('leaves', block_id=3, flags=lh.PAL_HAS_SHAPE | lh.PAL_FULL_CUBE | lh.PAL_HOLES,
                     color=(0.2, 0.45, 0.15),
                     alpha=0.8, masks=[(lh.face_mask(0, lv), lh.face_mask(1, lv), lh.face_mask(2, lv))])
        for s in 'nsewb':
            lh.trapdoor('trap_' + s, 'oxidized_copper_trapdoor.png', s if s != 'b' else 'bottom', (0.35, 0.65, 0.55))
    b = {}
    for x in range(0, 40):
        for z in range(0, 40):
            b[(x, 63, z)] = E['grass']
            b[(x, 72, z)] = E['stone']
    for y in range(64, 72):
        for i in range(40):
            b[(i, y, 0)] = b[(i, y, 39)] = b[(0, y, i)] = b[(39, y, i)] = E['stone']
    # a09: a lantern boxed in by open trapdoors standing against it, and a closed one on top.
    L = (10, 64, 20)
    b[L] = E['lantern']
    b[(L[0], L[1], L[2] - 1)] = E['trap_s']   # the cell to the north: stands on its south side, against the lantern
    b[(L[0], L[1], L[2] + 1)] = E['trap_n']
    b[(L[0] + 1, L[1], L[2])] = E['trap_w']
    b[(L[0] - 1, L[1], L[2])] = E['trap_e']
    b[(L[0], L[1] + 1, L[2])] = E['trap_b']
    # a10: a lantern beside a block of cut copper.
    b[(26, 64, 20)] = E['cut_copper']
    b[(27, 64, 20)] = E['lantern']
    # a10 as it really was: a copper grate (its little cross-shaped holes), the lantern beside it.
    b[(26, 64, 30)] = E['grate']
    b[(27, 64, 30)] = E['lantern']
    # The same with leaves.
    b[(10, 64, 30)] = E['leaves']
    b[(11, 64, 30)] = E['lantern']
    return b

VIEWS = ['a09:13.5,66.2,23.5,135,32', 'a10:24.5,66.0,23.5,-135,29']
