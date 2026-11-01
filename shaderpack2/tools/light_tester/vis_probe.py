#!/usr/bin/env python3
"""How much of one lamp the pack's per-pixel trace lets reach a patch of floor, next to the true answer.

vis_probe.py SCENE.py OUT.png LIGHT_X,LIGHT_Y,LIGHT_Z X0,Z0,SIZE[,FLOOR_Y]

LIGHT_* is the lamp's block. For each point of the floor square (seen from straight above, FLOOR_Y the floor's top,
64 by default), the pack's cone_visibility from that point to the lamp (with the cone the pack gave the lamp:
program/voxel_light.csh), as program/pixel_light.csh calls it; and the reference: the share of a disk as wide as that
cone, facing the point, that a straight ray from the point reaches past the scene's boxes exactly (TRUTH_SAMPLES rays,
the lamp's own block left out as the pack does). Writes pack | reference | difference (red: the pack too dark, green:
too light) and prints the mean and worst errors, and saves the numbers next to the picture (.npz: pack, truth, ok,
xs, zs). Same env as light_tester.py (ROOT, DEFS, SUBS, FRAMES), and N (points a side, 192), TRUTH_SAMPLES (256),
DEBUG_AT=x,z (logs each check cone_visibility makes for that one point), DEBUG_STRIPS=1 (with DEBUG_AT: each strip
of every box the ray goes into)."""
import math, os, sys, pathlib, importlib.util
import numpy as np
from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.argv, args = sys.argv[:1], sys.argv[1:]
import light_tester as lh  # compiles the pack's programs

scene_file, out = args[0], args[1]
light_block = tuple(int(v) for v in args[2].split(','))
sq = [float(v) for v in args[3].split(',')]
x0, z0, size = sq[:3]
floor_y = sq[3] if len(sq) > 3 else 64.0
N = int(os.environ.get('N', '192'))
DEBUG_AT = os.environ.get('DEBUG_AT')
if DEBUG_AT:
    # Every place cone_visibility lowers visible logs (t, what, value, extra) for the one probe point.
    lh.VIRTUAL['/lib/trace.glsl'] = (lh.ROOT / 'lib/trace.glsl').read_text().replace(
        '// The share of a cone of half width w round a ray', '''vec4 g_log[32];
int g_n = 0;
void dbg(float t, float what, float value, float extra) {
    if (g_n < 32) { g_log[g_n] = vec4(t, what, value, extra); g_n++; }
}
// The share of a cone of half width w round a ray''', 1)
    src = lh.VIRTUAL['/lib/trace.glsl']
    for a, wn in (('visible = min(visible, min(full / width, part));', 'width'),
                  ('visible = min(visible, min(full / mid_width, part));', 'mid_width')):
        assert a in src
        src = src.replace(a, a + ' dbg(t, 1.0, full / %s, part);' % wn)
    e = '''        hidden = share > strongest ? hidden_together(share, hidden, agree) : hidden_together(hidden, share, agree);'''
    assert e in src
    src = src.replace(e, e + ' dbg(float(i), 20.0, share, agree);')
    b = 'float cover = part_cover(cell, word, p, dir, inv_dir, t, seg_end, cell == start, mid_width);'
    assert b in src
    src = src.replace(b, b + ' dbg(t, 2.0, cover, float(cell.x * 1000 + cell.y * 100 + cell.z));')
    c = 'visible = min(visible, min(room / mid_width, part));'
    assert c in src
    src = src.replace(c, c + ' dbg(t, 3.0, room / mid_width, part);')
    d = '''                if (look == 0) {
                    if (cover > strongest) {'''
    assert d in src
    src = src.replace(d, '''                dbg(float(look), 4.0 + float(b), cover, dot(way, strongest_way));
''' + d)
    if os.environ.get('DEBUG_STRIPS'):
        # Each strip of every box_cone_cover with beyond (part_cover): (w, left, out_by behind), (bits, out_by ahead).
        f = '        way[k] = clamp((out_by.x - out_by.y) * 4.0 / w, -1.0, 1.0) / len;'
        assert f in src
        src = src.replace(f, f + '''
        if (beyond) { dbg(w, 30.0 + float(k), left[k], out_by.x); dbg(float(bits & 3u), 33.0 + float(k), out_by.y, at); }''')
    lh.VIRTUAL['/lib/trace.glsl'] = src

spec = importlib.util.spec_from_file_location('scene', scene_file)
scene = importlib.util.module_from_spec(spec)
scene.lh = lh
spec.loader.exec_module(scene)
blocks = scene.blocks()
cam = (x0 + size / 2, floor_y + 6.0, z0 + size / 2)
cb = tuple(int(math.floor(c)) for c in cam)
lh.build_grid(blocks, cb)
lh.run_frames(cam, lh.FRAMES)

PROBE = r'''
#include "/lib/settings.glsl"
#include "/lib/trace.glsl"
#include "/lib/lights.glsl"
layout(local_size_x = 8, local_size_y = 8) in;
layout(rgba32f) uniform image2D lh_out;
uniform vec3 u_light; uniform vec3 u_origin; uniform float u_size; uniform int u_n; uniform ivec2 u_debug_px;
void main() {
    ivec2 px = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(px, ivec2(u_n)))) {
        return;
    }
    vec3 p = u_origin + vec3((float(px.x) + 0.5) / float(u_n) * u_size, 0.04, (float(px.y) + 0.5) / float(u_n) * u_size);
    ivec3 tile;
    float vis = -1.0;
    float rad = 0.0;
    vec3 lp = vec3(0.0);
    if (block_tile_of(p, tile)) {
        uint records = record_index(tile);
        uint count = min(data_load(records), record_max);
        for (uint j = 0u; j < count; j++) {
            float radius;
            Light l = load_record(records + 1u + 3u * j, radius);
            if (all(equal(ivec3(floor(l.pos)), ivec3(floor(u_light))))) {
                vec3 to = l.pos - p;
                float d = length(to);
                vis = cone_visibility(p, to / d, d, 0.0, radius, 0.0, 64);
#ifdef DEBUG_LOG
                if (px == u_debug_px) {
                    for (int k = 0; k < g_n; k++) {
                        imageStore(lh_out, ivec2(k, u_n + 2), g_log[k]);
                    }
                    imageStore(lh_out, ivec2(31, u_n + 3), vec4(float(g_n), vis, d, radius));
                }
#endif
                rad = radius;
                lp = l.pos;
            }
        }
    }
    imageStore(lh_out, px, vec4(vis, rad, lp.x, lp.z));
}
'''
lh.VIRTUAL['lh_probe'] = ('#define DEBUG_LOG\n' if DEBUG_AT else '') + PROBE
probe = lh.compute(lh.program_src('lh_probe'), 'probe')
ci = np.array(cb)
lh.bind(probe)
lh.setu(probe, 'cameraPositionInt', tuple(int(v) for v in ci))
lh.setu(probe, 'cameraPositionFract', tuple(c - math.floor(c) for c in cam))
lh.setu(probe, 'cameraPosition', cam)
lh.setu(probe, 'previousCameraPositionInt', tuple(int(v) for v in ci))
lh.setu(probe, 'previousCameraPositionFract', tuple(c - math.floor(c) for c in cam))
lh.setu(probe, 'u_light', tuple(float(v) for v in np.array(light_block) - ci + 0.5))
lh.setu(probe, 'u_origin', (x0 - ci[0], floor_y - ci[1], z0 - ci[2]))
lh.setu(probe, 'u_size', size)
lh.setu(probe, 'u_n', N)
if DEBUG_AT:
    dx, dz = (float(v) for v in DEBUG_AT.split(','))
    lh.setu(probe, 'u_debug_px', (int((dx - x0) / size * N), int((dz - z0) / size * N)))
probe.run((N + 7) // 8, (N + 7) // 8)
lh.ctx.finish()
full = np.frombuffer(lh.out_img.read(), np.float32).reshape(lh.H, lh.W, 4)
raw = full[:N, :N]
if DEBUG_AT:
    n, vis, dist, rad = full[N + 3, 31]
    print('debug at', DEBUG_AT, 'visible %.3f dist %.3f radius %.3f, %d checks:' % (vis, dist, rad, int(n)))
    names = {1: 'beside (room, part)', 2: 'part cover (cell)', 3: 'part cell room, part'}
    for b in range(12):
        names[4 + b] = 'near box %d (t=look, cover, agree)' % b
    names[20] = 'part_cover box (t=box, share, agree)'
    for k in range(3):
        names[30 + k] = 'strip %d (t=w, left, out behind)' % k
        names[33 + k] = 'strip %d (t=closed bits, out ahead, at)' % k
    for k in range(int(n)):
        t, what, v, extra = full[N + 2, k]
        print('  t %.3f  %-36s %.3f  %.3f' % (t, names.get(int(what), what), v, extra))
pack = raw[..., 0]
found = pack >= 0
if not found.any():
    sys.exit('the lamp has no record over this floor')
radius = float(raw[..., 1][found].max())
lpos_vox = None
# The light's place in its block from the palette entry (at, in 32nds), as the pack's record holds it.
e = blocks[light_block]
w3 = int(lh.pal_words[e >> 8, (e & 255) * 16 + 3])
at = np.array([w3 & 63, (w3 >> 6) & 63, (w3 >> 12) & 63]) / 32.0
L = np.array(light_block, float) + at
print('lamp at', L, 'cone radius %.3f' % radius)

# ---------------------------------------------------------------- the reference
boxes = []
reach_lo = np.minimum([x0, floor_y, z0], L) - 1.0
reach_hi = np.maximum([x0 + size, floor_y, z0 + size], L) + 1.0
for (x, y, z), ent in blocks.items():
    if (x, y, z) == light_block or y < floor_y - 0.5:
        continue  # the lamp's own block, and the floor itself (the points sit on it)
    if np.any(np.array([x, y, z]) + 1 < reach_lo) or np.any(np.array([x, y, z]) > reach_hi):
        continue  # nowhere near a ray from the floor square to the lamp
    word0 = int(lh.pal_words[ent >> 8, (ent & 255) * 16])
    nb = (int(lh.pal_words[ent >> 8, (ent & 255) * 16 + 3]) >> 24) & 15
    for i in range(nb):
        bw = int(lh.pal_words[ent >> 8, (ent & 255) * 16 + 4 + i])
        lo = np.array([bw & 31, (bw >> 5) & 31, (bw >> 10) & 31], float)
        hi = np.array([(bw >> 15) & 31, (bw >> 20) & 31, (bw >> 25) & 31], float)
        boxes.append((np.array([x, y, z]) + lo / 16.0, np.array([x, y, z]) + hi / 16.0))
if 'BOXCHECK' in os.environ:
    print(len(boxes), 'boxes', boxes[:3])
B_lo = np.array([b[0] for b in boxes])
B_hi = np.array([b[1] for b in boxes])

S = int(os.environ.get('TRUTH_SAMPLES', '256'))
# Stratified points on a unit disk (Fibonacci spiral).
k = np.arange(S) + 0.5
disk = np.stack([np.sqrt(k / S) * np.cos(k * 2.399963), np.sqrt(k / S) * np.sin(k * 2.399963)], 1)
xs = x0 + (np.arange(N) + 0.5) / N * size
zs = z0 + (np.arange(N) + 0.5) / N * size
truth = np.zeros((N, N))
for j, z in enumerate(zs):
    P = np.stack([xs, np.full(N, floor_y + 0.04), np.full(N, z)], 1)  # (N, 3)
    to = L[None] - P
    d = np.linalg.norm(to, axis=1)
    f = to / d[:, None]
    a = np.cross(f, [0.0, 1.0, 0.0]); a /= np.maximum(np.linalg.norm(a, axis=1), 1e-6)[:, None]
    b = np.cross(f, a)
    T = L[None, None] + radius * (disk[None, :, 0:1] * a[:, None] + disk[None, :, 1:2] * b[:, None])  # (N, S, 3)
    D = T - P[:, None]  # rays, t in 0..1
    inv = 1.0 / np.where(np.abs(D) < 1e-9, 1e-9, D)
    t1 = (B_lo[None, None] - P[:, None, None]) * inv[:, :, None]
    t2 = (B_hi[None, None] - P[:, None, None]) * inv[:, :, None]
    tn = np.minimum(t1, t2).max(-1)
    tf = np.maximum(t1, t2).min(-1)
    blocked = ((tn < tf) & (tf > 1e-4) & (tn < 1.0 - 1e-4)).any(-1)  # (N, S)
    truth[j] = 1.0 - blocked.mean(1)

# Points inside a box (under a wall) are never seen: left out.
inside = np.zeros((N, N), bool)
for lo, hi in boxes:
    inside |= ((xs[None, :] > lo[0]) & (xs[None, :] < hi[0]) & (zs[:, None] > lo[2]) & (zs[:, None] < hi[2])
               & (lo[1] < floor_y + 0.04) & (hi[1] > floor_y + 0.04))
ok = found & ~inside
np.savez(os.path.splitext(out)[0] + '.npz', pack=pack, truth=truth, ok=ok, xs=xs, zs=zs)
err = np.where(ok, pack - truth, 0)
print('mean |error| %.4f  worst too dark %.3f  worst too light %.3f  share off by >0.25: %.3f'
      % (np.abs(err[ok]).mean(), -err.min(), err.max(), (np.abs(err[ok]) > 0.25).mean()))

def grey(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).convert('RGB')
diff = np.zeros((N, N, 3))
diff[..., 0] = np.clip(-err, 0, 1)
diff[..., 1] = np.clip(err, 0, 1)
tiles = [grey(np.where(ok, pack, 0)), grey(np.where(ok, truth, 0)), Image.fromarray((diff * 255).astype(np.uint8))]
sheet = Image.new('RGB', (N * 3 + 8, N + 14), (40, 40, 40))
for i, (t, name) in enumerate(zip(tiles, ['pack', 'reference', 'pack - reference'])):
    sheet.paste(t, (i * (N + 4), 14))
    ImageDraw.Draw(sheet).text((i * (N + 4) + 2, 1), name, fill=(255, 255, 0))
sheet.save(out)
print('saved', out)
