#!/usr/bin/env python3
"""Headless block-light tester for Vector: no Minecraft. Builds the Bridge's block grid and palette from a hand-made
scene, runs the pack's own programs that make the light (clear, world, sdf, voxel_light) in a stand-in for Iris, then
draws the scene by tracing the voxel copy and lights each pixel with the pack's own per-pixel code (light_from from
program/pixel_light.csh) at full resolution, no temporal filter. Also the per-block layer for comparison.

light_tester.py SCENE.py OUT.png [view ...]   view = name:x,y,z,yaw,pitch[,fov] (Minecraft yaw/pitch, world blocks)
env: W, H, MODE (pixel|block|both, default pixel), EXPO, DEFS ("A=1 B=2"), SUBS ('a=>b;;c=>d' applied to all
programs), ROOT (shaders dir: point it at a copy for a before/after), FRAMES (default 24), MC_JAR (Minecraft's
client jar, for the vanilla textures whose see-through pixels make the hole masks; found in the Bridge's build cache
by default). Needs Python with numpy, Pillow and moderngl, and an OpenGL 4.3 context (Mesa's llvmpipe will do, with
an X display such as Xvfb). It covers the block light only: no sun shadows, sky, water or filtering over frames."""
import glob, io, os, re, sys, math, pathlib, importlib.util, tempfile, zipfile
import numpy as np, moderngl
from PIL import Image, ImageDraw

sys.dont_write_bytecode = True  # the scene files are imported

HERE = pathlib.Path(__file__).resolve().parent
ROOT = pathlib.Path(os.environ.get('ROOT', str(HERE.parent.parent / 'shaders')))
W, H = int(os.environ.get('W', '640')), int(os.environ.get('H', '360'))
MODE = os.environ.get('MODE', 'pixel')
FRAMES = int(os.environ.get('FRAMES', '24'))

VX, VY = 128, 96  # VOXEL_BOX 1 (the default)
SIZE = np.array([VX, VY, VX])
HALF = SIZE // 2

# ---------------------------------------------------------------- GLSL loading (Iris-style absolute includes)
VIRTUAL = {}

def expand(path):
    src = VIRTUAL[path] if path in VIRTUAL else (ROOT / path.lstrip('/')).read_text()
    out = []
    for line in src.split('\n'):
        m = re.match(r'\s*#include\s+"([^"]+)"', line)
        if m:
            out.append(expand(m.group(1)))
        else:
            out.append(line)
    return '\n'.join(out)

def subs(src):
    for s in [x for x in os.environ.get('SUBS', '').split(';;') if x]:
        a, b = s.split('=>')
        src = src.replace(a.replace('\\n', '\n'), b.replace('\\n', '\n'))
    return src

def program_src(stage, extra_subs=()):
    body = expand(stage)
    body = re.sub(r'#version[^\n]*\n', '', body)
    for a, b in extra_subs:
        assert a in body, (stage, a)
        body = body.replace(a, b)
    # An option (DEFS) replaces its #define line in lib/settings.glsl, as Iris does.
    for d in os.environ.get('DEFS', '').split():
        k, v = d.split('=')
        body = re.sub(r'#define %s [^\n/]*' % k, '#define %s %s ' % (k, v), body)
    # moderngl can't point image uniforms at units: give each image its unit in the source.
    names = list(IMAGE_UNITS)
    def unit(m):
        return 'layout(%s, binding = %d) %suniform %s %s;' % (m.group(1), names.index(m.group(4)), m.group(2),
                                                              m.group(3), m.group(4))
    body = re.sub(r'layout\((\w+)\)\s*((?:readonly |writeonly |coherent )*)uniform (\w+) (\w+);', unit, body)
    return '#version 430 core\n' + subs(body)

IMAGE_UNITS = ['cg_vox_img', 'cg_sdf_img', 'cg_blk_img', 'cg_data_img', 'cg_gi_img', 'lh_out']

ctx = moderngl.create_standalone_context(require=430, libgl='libGL.so.1')

def compute(src, name):
    try:
        return ctx.compute_shader(src)
    except Exception as e:
        (pathlib.Path(tempfile.gettempdir()) / ('light-tester-fail-%s.comp' % name)).write_text(src)
        msg = str(e)
        print('compile failed:', name, msg[:3000])
        sys.exit(1)

# ---------------------------------------------------------------- images and textures (shaders.properties sizes)
def tex3(comps, dtype, filt=moderngl.NEAREST):
    t = ctx.texture3d(tuple(int(v) for v in SIZE), comps, dtype=dtype)
    t.filter = (filt, filt)
    return t

vox = tex3(1, 'u4')
sdf = tex3(2, 'f1', moderngl.LINEAR)
blk = tex3(4, 'f2', moderngl.LINEAR)
world = tex3(1, 'u4')
gi = ctx.texture3d((VX * 2, VY, VX), 4, dtype='f2'); gi.filter = (moderngl.LINEAR, moderngl.LINEAR)
palette = ctx.texture((4096, 304), 1, dtype='u4'); palette.filter = (moderngl.NEAREST, moderngl.NEAREST)
data = ctx.texture((4096, 1552), 1, dtype='u4'); data.filter = (moderngl.NEAREST, moderngl.NEAREST)
globals_tex = ctx.texture((9, 1), 4, dtype='f4')
out_img = ctx.texture((W, H), 4, dtype='f4')

IMAGES = {'cg_vox_img': vox, 'cg_sdf_img': sdf, 'cg_blk_img': blk, 'cg_data_img': data, 'cg_gi_img': gi,
          'lh_out': out_img}
SAMPLERS = {'cg_world_tex': world, 'cg_palette_tex': palette, 'cg_sdf_tex': sdf, 'cg_blk_tex': blk,
            'cg_gi_tex': gi, 'cg_globals_tex': globals_tex}

def bind(prog):
    for n, t in IMAGES.items():
        t.bind_to_image(IMAGE_UNITS.index(n), read=True, write=True)
    for i, (n, t) in enumerate(SAMPLERS.items()):
        t.use(i)
        if n in prog:
            prog[n].value = i

# ---------------------------------------------------------------- the Bridge's palette and grid
PAL_WATER, PAL_TRANSLUCENT, PAL_MOSTLY_CLEAR, PAL_NO_COLLISION = 1 << 20, 1 << 21, 1 << 22, 1 << 23
PAL_FULL_CUBE, PAL_HAS_SHAPE, PAL_LAVA, PAL_FLUID, PAL_HOLES = 1 << 24, 1 << 25, 1 << 26, 1 << 27, 1 << 28
GRID_MARK, HOLE_MARK = 0x43474731, 0x484f4c31

pal_words = np.zeros((304, 4096), np.uint32)
def hole_set(i, v):
    pal_words[256 + (i >> 12), i & 4095] = v
pal_words[0, 15] = GRID_MARK
hole_set(0, HOLE_MARK)
entries = {'air': 0}
next_entry = [1]
next_table = [65536]
next_mask = [1]

def srgb8(rgb, a=1.0):
    r, g, b = (int(round(max(0, min(1, c)) * 255)) for c in rgb)
    return r | (g << 8) | (b << 16) | (int(round(a * 255)) << 24)

def box_word(lo, hi):
    return lo[0] | (lo[1] << 5) | (lo[2] << 10) | (hi[0] << 15) | (hi[1] << 20) | (hi[2] << 25)

def add_mask(rows):  # rows[j] = 16-bit int, bit i open (cell (i, j))
    m = next_mask[0]; next_mask[0] += 1
    for k in range(8):
        hole_set(131072 + m * 8 + k, (rows[2 * k] & 0xffff) | ((rows[2 * k + 1] & 0xffff) << 16))
    return m

def add_entry(name, block_id=0xffff, emission=0, flags=0, color=(0.5, 0.5, 0.5), alpha=1.0, glow=(0, 0, 0),
              at=(16, 16, 16), boxes=(((0, 0, 0), (16, 16, 16)),), masks=None):
    """boxes in 16ths; at = light point in 32nds; masks: per box (mask_x, mask_y, mask_z) as row lists or None."""
    e = next_entry[0]; next_entry[0] += 1
    row, col = e >> 8, (e & 255) * 16
    w = [0] * 16
    w[0] = (block_id & 0xffff) | (emission << 16) | flags
    w[1] = srgb8(color, alpha)
    w[2] = srgb8(glow)
    w[3] = at[0] | (at[1] << 6) | (at[2] << 12) | (len(boxes) << 24)
    for i, (lo, hi) in enumerate(boxes):
        w[4 + i] = box_word(lo, hi)
    pal_words[row, col:col + 16] = w
    if masks:
        t = next_table[0]; next_table[0] += 2 * len(boxes)
        hole_set(e, t)
        for i, mk in enumerate(masks):
            ids = [add_mask(r) if r else 0 for r in (mk or (None, None, None))]
            hole_set(t + 2 * i, ids[0] | (ids[1] << 16))
            hole_set(t + 2 * i + 1, ids[2])
    entries[name] = e
    return e

def mc_jar():
    jar = os.environ.get('MC_JAR')
    if not jar:
        found = sorted(glob.glob(os.path.expanduser(
            '~/.cache/create-galore-bridge/client-*/libraries/net/minecraft/client/*/client-*-extra.jar')))
        if not found:
            sys.exit('No Minecraft client jar for the textures: set MC_JAR')
        jar = found[-1]
    return jar

def tex_open(png):
    with zipfile.ZipFile(mc_jar()) as z:
        im = Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/' + png))).convert('RGBA')
    return [[im.getpixel((x, y))[3] < 128 for x in range(16)] for y in range(16)]  # [v][u]

def rows_from(fn):
    return [sum(1 << i for i in range(16) if fn(i, j)) for j in range(16)]

# Mask helpers for a texture laid on a face across an axis: cell (i, j) along a = (axis+1)%3, b = (axis+2)%3.
def face_mask(axis, open_uv):
    if axis == 2:   # a = x, b = y: u = x, v = 15 - y
        return rows_from(lambda i, j: open_uv[15 - j][i])
    if axis == 0:   # a = y, b = z: u = z, v = 15 - y
        return rows_from(lambda i, j: open_uv[15 - i][j])
    return rows_from(lambda i, j: open_uv[j][i])  # axis 1: a = z, b = x: u = x, v = z

FULL = PAL_FULL_CUBE | PAL_HAS_SHAPE

def trapdoor(name, png, side, color):
    """side: where the open trapdoor stands in its cell: n s e w (its face) or 'bottom' (closed, lower half)."""
    op = tex_open(png)
    if side == 'n':
        box, ax = ((0, 0, 0), (16, 16, 3)), 2
    elif side == 's':
        box, ax = ((0, 0, 13), (16, 16, 16)), 2
    elif side == 'w':
        box, ax = ((0, 0, 0), (3, 16, 16)), 0
    elif side == 'e':
        box, ax = ((13, 0, 0), (16, 16, 16)), 0
    else:
        box, ax = ((0, 0, 0), (16, 3, 16)), 1
    mk = [None, None, None]
    mk[ax] = face_mask(ax, op)
    return add_entry(name, flags=PAL_HAS_SHAPE | PAL_HOLES, color=color, alpha=0.8, boxes=(box,), masks=[tuple(mk)])

def build_grid(blocks, cam_block):
    """blocks: {(x, y, z): entry}; writes cg_world_tex for the box around cam_block."""
    lo = ((np.array(cam_block) - HALF) >> 3) << 3
    g = np.zeros((VX, VY, VX), np.uint32)  # [x][y][z] by slot
    xs, ys, zs = [lo[i] + np.arange(SIZE[i]) for i in range(3)]
    sx, sy, sz = xs % VX, ys % VY, zs % VX
    tx, ty, tz = (np.floor_divide(xs, VX) & 7), (np.floor_divide(ys, VY) & 3), (np.floor_divide(zs, VX) & 7)
    tag = (tx[:, None, None] << 20) | (tz[None, None, :] << 23) | (ty[None, :, None] << 26) | (1 << 31)
    words = np.broadcast_to(tag, (VX, VY, VX)).astype(np.uint32).copy()
    for (x, y, z), e in blocks.items():
        i, j, k = x - lo[0], y - lo[1], z - lo[2]
        if 0 <= i < VX and 0 <= j < VY and 0 <= k < VX:
            words[i, j, k] |= e
    g[np.ix_(sx, sy, sz)] = words
    # texture3d data: x fastest, then y, then z
    world.write(np.ascontiguousarray(g.transpose(2, 1, 0)).tobytes())
    palette.write(pal_words.tobytes())

# ---------------------------------------------------------------- the pack's programs
HARNESS_SUBS = {
    # Scheduling only: every tile updates every frame (no view test, no interval).
    'voxel_light': [('s_update = due && in_view(centre, 7.0, 10.0) ? 1u : 0u;', 's_update = 1u;')],
}
progs = {}
for stage, name in [('begin1.csh', 'clear'), ('begin3.csh', 'world'), ('shadowcomp1.csh', 'sdf'),
                    ('shadowcomp2.csh', 'voxel_light')]:
    progs[name] = compute(program_src(stage, HARNESS_SUBS.get(name, ())), name)

pl = (ROOT / 'program/pixel_light.csh').read_text()
light_from = re.search(r'vec3 light_from\(.*?\n\}\n', pl, re.S).group(0)
DRAW = r'''
#include "/lib/settings.glsl"
#include "/lib/trace.glsl"
#include "/lib/lights.glsl"
#include "/lib/blocks.glsl"
#include "/lib/voxel_sample.glsl"
layout(local_size_x = 8, local_size_y = 8) in;
layout(rgba32f) uniform image2D lh_out;
uniform mat3 u_cam; uniform float u_tan; uniform int u_mode; uniform vec2 u_size;
vec3 ggx_specular(vec3 n, vec3 v, vec3 l, float s, float f0) { return vec3(0.0); }
''' + light_from + r'''
void main() {
    ivec2 px = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(px, ivec2(u_size)))) {
        return;
    }
    vec2 ndc = (vec2(px) + 0.5) / u_size * 2.0 - 1.0;
    vec3 dir = normalize(u_cam * vec3(ndc.x * u_tan * u_size.x / u_size.y, -ndc.y * u_tan, 1.0));
    vec3 o = cameraPositionFract;
    Hit h = trace_ray(o, dir, 80.0, 256);
    if (!h.hit) {
        imageStore(lh_out, px, vec4(0.02, 0.02, 0.03, 1.0));
        return;
    }
    vec3 n = h.normal;
    vec4 alb = vox_color(h.block);
    vec3 p = h.pos + n * 0.04;
    vec3 v = -dir;
    vec3 diffuse = vec3(0.0);
    vec3 spec = vec3(0.0);
    int traces = 0;
    if (u_mode == 0 || u_mode == 2) {
        ivec3 tile;
        if (block_tile_of(p, tile)) {
            uint records = record_index(tile);
            uint count = min(data_load(records), record_max);
            for (uint j = 0u; j < count && traces < PIXEL_LIGHT_TRACES; j++) {
                float radius;
                Light l = load_record(records + 1u + 3u * j, radius);
                diffuse += light_from(p, p, n, v, 0.0, false, l.pos, l.color, l.reach, radius, 0.0, spec, traces,
                                      vec3(0.0));
            }
        }
        diffuse = compress_light(diffuse);
    }
    vec3 c = diffuse;
    if (u_mode == 1) {
        c = sample_block_table(cg_blk_tex, p).rgb;
    }
    // Faint block grid lines on the surface, for orientation.
    vec3 f = fract(h.pos + 1e-4);
    vec3 e = min(f, 1.0 - f);
    float edge = 1.0;
    if (abs(n.y) > 0.5) {
        edge = min(e.x, e.z);
    } else if (abs(n.x) > 0.5) {
        edge = min(e.y, e.z);
    } else {
        edge = min(e.x, e.y);
    }
    float grid = edge < 0.01 ? 0.85 : 1.0;
    imageStore(lh_out, px, vec4(c * 4.0 * alb.rgb * grid + 0.004 * alb.rgb, 1.0));
}
'''
VIRTUAL['lh_draw'] = DRAW
draw = compute(program_src('lh_draw'), 'draw')

def setu(prog, name, v):
    if name in prog:
        prog[name].value = v

def mats(cam, yaw, pitch, fov):
    yr, pr = math.radians(yaw), math.radians(pitch)
    fwd = np.array([-math.sin(yr) * math.cos(pr), -math.sin(pr), math.cos(yr) * math.cos(pr)])
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    cam3 = np.stack([-right, up, fwd], axis=1)
    view = np.eye(4); view[0, :3] = right; view[1, :3] = up; view[2, :3] = -fwd
    f = 1 / math.tan(math.radians(fov) / 2); a = W / H; nr, fr = 0.05, 512.0
    proj = np.array([[f / a, 0, 0, 0], [0, f, 0, 0], [0, 0, (fr + nr) / (nr - fr), 2 * fr * nr / (nr - fr)],
                     [0, 0, -1, 0]])
    return cam3, view, proj

def run_frames(cam, frames):
    ci = tuple(int(math.floor(c)) for c in cam)
    cf = tuple(c - math.floor(c) for c in cam)
    for fr in range(frames):
        first = fr == 0
        prev = (ci[0] + 100000, ci[1], ci[2]) if first else ci
        for name in ('clear', 'world', 'sdf', 'voxel_light'):
            p = progs[name]
            bind(p)
            setu(p, 'cameraPositionInt', ci); setu(p, 'cameraPositionFract', cf)
            setu(p, 'cameraPosition', tuple(cam))
            setu(p, 'previousCameraPositionInt', prev); setu(p, 'previousCameraPositionFract', cf)
            setu(p, 'frameCounter', fr); setu(p, 'frameTimeCounter', fr / 60.0)
            groups = (2048, 1, 1) if name == 'clear' else (VX // 8, VY // 8, VX // 8)
            p.run(*groups)
            ctx.memory_barrier()

def render(cam, yaw, pitch, fov, mode):
    cam3, view, proj = mats(cam, yaw, pitch, fov)
    ci = tuple(int(math.floor(c)) for c in cam)
    cf = tuple(c - math.floor(c) for c in cam)
    bind(draw)
    setu(draw, 'cameraPositionInt', ci); setu(draw, 'cameraPositionFract', cf)
    setu(draw, 'cameraPosition', tuple(cam))
    setu(draw, 'previousCameraPositionInt', ci); setu(draw, 'previousCameraPositionFract', cf)
    setu(draw, 'u_cam', tuple(cam3.T.flatten())); setu(draw, 'u_tan', math.tan(math.radians(fov) / 2))
    setu(draw, 'u_mode', mode); setu(draw, 'u_size', (float(W), float(H)))
    draw.run((W + 7) // 8, (H + 7) // 8)
    ctx.finish()
    a = np.frombuffer(out_img.read(), np.float32).reshape(H, W, 4)[..., :3]
    return a

def to_img(a):
    a = np.maximum(a * float(os.environ.get('EXPO', '1')), 0)
    y = a / (1 + a)
    s = np.where(y <= 0.0031308, y * 12.92, 1.055 * np.power(y, 1 / 2.4) - 0.055)
    return Image.fromarray((np.clip(s, 0, 1) * 255 + 0.5).astype(np.uint8))

if __name__ == '__main__':
    scene_file, out = sys.argv[1], sys.argv[2]
    spec = importlib.util.spec_from_file_location('scene', scene_file)
    scene = importlib.util.module_from_spec(spec)
    scene.lh = sys.modules[__name__]  # the scene builds its palette with this module's helpers
    spec.loader.exec_module(scene)
    views = sys.argv[3:] or scene.VIEWS
    tiles = []
    built_for = None
    for v in views:
        name, args = v.split(':')
        vals = [float(x) for x in args.split(',')]
        cam, yaw, pitch = vals[:3], vals[3], vals[4]
        fov = vals[5] if len(vals) > 5 else 70.0
        cb = tuple(int(math.floor(c)) for c in cam)
        if built_for is None or any(abs(a - b) > 8 for a, b in zip(cb, built_for)):
            build_grid(scene.blocks(), cb)
            run_frames(cam, FRAMES)
            built_for = cb
        modes = {'pixel': [0], 'block': [1], 'both': [0, 1]}[MODE]
        for m in modes:
            im = to_img(render(cam, yaw, pitch, fov, m))
            ImageDraw.Draw(im).text((4, 4), name + ('' if len(modes) == 1 else [' pixel', ' block'][m]),
                                    fill=(255, 255, 0))
            tiles.append(im)
    cols = 2 if len(tiles) > 1 else 1
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (W * cols, H * rows))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * W, (i // cols) * H))
    sheet.save(out)
    print('saved', out)
