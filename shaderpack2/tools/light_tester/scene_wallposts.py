# The 0.6.1 test's "pointy" spot at night, as built: cobblestone walls in a reversed L on grass, a redstone torch on
# the short end's post, a lantern on the long end's post, and a soul lantern on one more post past the corner.
lh = lh  # set by light_tester.py before this runs
POST = ((4, 0, 4), (12, 16, 12))
ARM = {'n': ((5, 0, 0), (11, 14, 8)), 's': ((5, 0, 8), (11, 14, 16)),
       'w': ((0, 0, 5), (8, 14, 11)), 'e': ((8, 0, 5), (16, 14, 11))}
def wall(name, post, sides):
    boxes = ((POST,) if post else ()) + tuple(ARM[s] for s in sides)
    if not post and sides in ('ew', 'we'):
        boxes = (((0, 0, 5), (16, 14, 11)),)
    if not post and sides in ('ns', 'sn'):
        boxes = (((5, 0, 0), (11, 14, 16)),)
    lh.add_entry(name, flags=lh.PAL_HAS_SHAPE, color=(0.45, 0.45, 0.45), boxes=boxes)
def blocks():
    E = lh.entries
    if 'grass' not in E:
        lh.add_entry('grass', flags=lh.FULL, color=(0.33, 0.5, 0.2))
        lh.add_entry('stone', flags=lh.FULL, color=(0.5, 0.5, 0.5))
        wall('wall_post_e', True, 'e')
        wall('wall_ew', False, 'ew')
        wall('wall_corner', True, 'wns')
        wall('wall_post_n', True, 'n')
        wall('wall_ns', False, 'ns')
        wall('wall_post_s', True, 's')
        lh.add_entry('soul_lantern', block_id=103, emission=10, flags=lh.PAL_HAS_SHAPE, color=(0.3, 0.3, 0.3),
                     glow=(0.3, 0.9, 1.0), at=(16, 7, 16),
                     boxes=(((5, 0, 5), (11, 7, 11)), ((6, 7, 6), (10, 9, 10))))
        lh.add_entry('lantern', block_id=101, emission=15, flags=lh.PAL_HAS_SHAPE, color=(0.3, 0.3, 0.3),
                     glow=(1.0, 0.7, 0.3), at=(16, 7, 16),
                     boxes=(((5, 0, 5), (11, 7, 11)), ((6, 7, 6), (10, 9, 10))))
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
    # Row z 10 from x 8 (redstone torch) to the corner at x 11; the long stroke north to the lantern at z 8, the soul
    # lantern's post one past the corner at z 11.
    b[(8, 64, 10)] = E['wall_post_e']
    b[(9, 64, 10)] = b[(10, 64, 10)] = E['wall_ew']
    b[(11, 64, 10)] = E['wall_corner']
    b[(11, 64, 9)] = E['wall_ns']
    b[(11, 64, 8)] = E['wall_post_s']
    b[(11, 64, 11)] = E['wall_post_n']
    b[(8, 65, 10)] = E['redstone_torch']
    b[(11, 65, 8)] = E['lantern']
    b[(11, 65, 11)] = E['soul_lantern']
    return b

VIEWS = ['front:9.6,65.6,14.2,205,40,80', 'top:11.5,69.0,14.8,180,62,70']
