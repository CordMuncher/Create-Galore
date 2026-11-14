#!/usr/bin/env python3
"""Headless sky, cloud and water preview for Vector: no Minecraft. Runs the pack's own programs for the air's tables
(setup, setup1, begin), the frame's light (begin2) and the clouds (deferred3) in a stand-in for Iris, over a made-up
depth buffer (open sea at Y 62.9 under the horizon, sky above it), then draws each view with the pack's own code: the
sky and the clouds over it as program/deferred_light.fsh lays them, the sea as lib/water_shade.glsl shades it (the
same function the game's water and Distant Horizons' water call), the haze as lib/haze.glsl, the tone curve as
lib/post.glsl. No terrain, no shadow map, no bloom, no upscaler. A look in seconds instead of the game's minutes; the
game (tools/rig) stays the proof.

view_tester.py OUT.png [view ...]     view = name:time,yaw,pitch[,height[,rain[,fov[,day]]]]
    time: the game's clock in ticks (6000 noon, 12500 sunset, 18000 midnight); yaw, pitch: Minecraft's (yaw 0 looks
    south, -90 east; pitch negative looks up); height: the camera's Y (default 66); rain 0..1; day: the world's day
    (the weather changes with it).
env: W, H (default 640 x 360), FRAMES (default 10: the clouds settle over frames), ROOT (shaders dir: point it at a
copy for a before/after), DEFS ("A=1 B=2": option values; "A=on" / "A=off" for switches), SUBS ('a=>b;;c=>d' applied
to every program), POS ("x,z": where in the world, default 0,0), COLS (pictures per row), TIMING (1: each pass's time
on this machine), RAW (1: also save OUT.npy, the linear picture before the tone curve), GLOBALS (1: print the frame's
values, lib/globals.glsl, after each view), WEATHER (the Winds & Weather mod's weather as the Bridge hands it over,
lib/weather.glsl; without it there is none and the clouds keep to the pack's own: "amount,dark,heaped" the same
everywhere, each as the pack reads it (amount 0..1: 0.31 fair, 0.46 the mod's middle, 0.6 heavy, 0.7 and over rain
and storms; dark 0..1; heaped 0.2 a flat sheet .. 1.2 towers); "a,d,h;a,d,h@X" the second from X blocks east of the
camera on, the first west of it, changing over 640 blocks; "row:FILE" the map's 4096 words as they are), FRAME_TIME
(seconds a frame, default 1/60: what eases over time, like the light under a closed sky, needs 5 or so to arrive).
Needs numpy, Pillow and moderngl with an OpenGL 4.3 context (Mesa's llvmpipe will do; EGL, or an X display)."""
import math, os, pathlib, re, subprocess, sys, tempfile, time
import numpy as np, moderngl
from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
ROOT = pathlib.Path(os.environ.get('ROOT', str(HERE.parent.parent / 'shaders')))
W, H = int(os.environ.get('W', '640')), int(os.environ.get('H', '360'))
FRAMES = int(os.environ.get('FRAMES', '10'))
NEAR, FAR = 0.05, 8192.0
SEA_Y = 62.0 + 8.0 / 9.0

# ---------------------------------------------------------------- GLSL loading (Iris-style absolute includes)
VIRTUAL = {}


def expand(path):
    src = VIRTUAL[path] if path in VIRTUAL else (ROOT / path.lstrip('/')).read_text()
    out = []
    for line in src.split('\n'):
        m = re.match(r'\s*#include\s+"([^"]+)"', line)
        out.append(expand(m.group(1)) if m else line)
    return '\n'.join(out)


DEFS_FOUND = {}  # how often each option of DEFS was found (not every program reads the settings)


def program_src(stage):
    body = re.sub(r'#version[^\n]*\n', '', expand(stage))
    for d in os.environ.get('DEFS', '').split():
        k, v = d.split('=')
        if v in ('on', 'off'):
            body, n = re.subn(r'(?m)^(//)?#define %s\b[^\n]*' % k, ('' if v == 'on' else '//') + '#define %s' % k, body)
        else:
            body, n = re.subn(r'#define %s [^\n/]*' % k, '#define %s %s ' % (k, v), body)
        DEFS_FOUND[k] = DEFS_FOUND.get(k, 0) + n
    for s in [x for x in os.environ.get('SUBS', '').split(';;') if x]:
        a, b = s.split('=>')
        body = body.replace(a.replace('\\n', '\n'), b.replace('\\n', '\n'))
    iris = '#define MC_VERSION 12101\n#define MC_HAND_DEPTH 0.125\n#define IS_IRIS\n'
    # The preprocessor first (as Iris runs it), so what follows sees only the lines this program keeps.
    with tempfile.TemporaryDirectory() as tmp:
        f = pathlib.Path(tmp) / 'p.comp'
        f.write_text('#version 430 core\n' + iris + body)
        r = subprocess.run(['glslangValidator', '-E', str(f)], capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit('preprocessor failed: %s\n%s' % (stage, (r.stdout + r.stderr)[-3000:]))
        body = r.stdout
    # moderngl can't point image uniforms at units: each image gets its unit in the source, in order.
    images = []

    def unit(m):
        if m.group(4) not in images:
            images.append(m.group(4))
        return 'layout(%s, binding = %d) %suniform %s %s;' % (m.group(1), images.index(m.group(4)), m.group(2),
                                                              m.group(3), m.group(4))
    body = re.sub(r'layout\((\w+)\)\s*((?:readonly |writeonly |coherent )*)uniform (\w+) (\w+);', unit, body)
    return body, images


def make_context():
    for kw in ({'backend': 'egl'}, {'libgl': 'libGL.so.1'}):
        try:
            return moderngl.create_standalone_context(require=430, **kw)
        except Exception as e:  # noqa: BLE001
            last = e
    sys.exit('no OpenGL 4.3 context: %s' % last)


os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE', '1')
ctx = make_context()


class Program:
    def __init__(self, stage, name):
        src, self.images = program_src(stage)
        self.name = name
        try:
            self.prog = ctx.compute_shader(src)
        except Exception as e:  # noqa: BLE001
            path = pathlib.Path(tempfile.gettempdir()) / ('view-tester-fail-%s.comp' % name)
            path.write_text(src)
            sys.exit('compile failed: %s (%s)\n%s' % (name, path, str(e)[:4000]))
        m = re.search(r'local_size_x\s*=\s*(\d+)(?:\s*,\s*local_size_y\s*=\s*(\d+))?(?:\s*,\s*local_size_z\s*=\s*(\d+))?',
                      src)
        self.local = [int(g) if g else 1 for g in m.groups()]
        g = re.search(r'const ivec3 workGroups = ivec3\((\d+), (\d+), (\d+)\);', src)
        r = re.search(r'const vec2 workGroupsRender = vec2\(([\d.]+), ([\d.]+)\);', src)
        if g:
            self.groups = tuple(int(v) for v in g.groups())
        elif r:
            self.groups = (math.ceil(W * float(r.group(1)) / self.local[0]),
                           math.ceil(H * float(r.group(2)) / self.local[1]), 1)
        else:
            self.groups = (math.ceil(W / self.local[0]), math.ceil(H / self.local[1]), 1)

    def run(self, uniforms):
        assert len(self.images) <= 8, (self.name, self.images)
        for i, n in enumerate(self.images):
            IMAGES[n].bind_to_image(i, read=True, write=True)
        unit = 0
        for n in self.prog:
            member = self.prog[n]
            if not isinstance(member, moderngl.Uniform):
                continue
            if n in SAMPLERS:
                SAMPLERS[n].use(unit)
                member.value = unit
                unit += 1
            elif n in uniforms:
                member.value = uniforms[n]
        timed(self.name, lambda: self.prog.run(*self.groups))
        ctx.memory_barrier()


TIMES = {}


def timed(name, fn):
    if os.environ.get('TIMING') != '1':
        fn()
        return
    ctx.finish()
    t = time.perf_counter()
    fn()
    ctx.finish()
    TIMES[name] = TIMES.get(name, 0.0) + time.perf_counter() - t


# ---------------------------------------------------------------- images and textures (shaders.properties' sizes)
def tex2(size, comps, dtype, filt=moderngl.LINEAR, repeat=False):
    t = ctx.texture(size, comps, dtype=dtype)
    t.filter = (filt, filt)
    t.repeat_x = t.repeat_y = repeat
    return t


sky_rows = int(re.search(r'image\.cg_sky_img = .* 256 (\d+)', (ROOT / 'shaders.properties').read_text()).group(1))
sky = tex2((256, sky_rows), 4, 'f2')
globals_tex = tex2((11, 1), 4, 'f4', moderngl.NEAREST)
data = tex2((4096, 1880), 1, 'u4', moderngl.NEAREST)
cloud = tex2((W, H), 4, 'f2', moderngl.NEAREST)
out_img = tex2((W, H), 4, 'f4', moderngl.NEAREST)
depth = tex2((W, H), 1, 'f4', moderngl.NEAREST)
blank = tex2((W, H), 4, 'f1', moderngl.NEAREST)
vox = ctx.texture3d((8, 8, 8), 1, dtype='u4'); vox.filter = (moderngl.NEAREST, moderngl.NEAREST)
sdf = ctx.texture3d((8, 8, 8), 4, dtype='f1')
palette_rows = int(re.search(r'image\.cg_palette_img = .* 4096 (\d+)', (ROOT / 'shaders.properties').read_text())
                   .group(1))
palette = tex2((4096, palette_rows), 1, 'u4', moderngl.NEAREST)
palette.write(bytes(4096 * palette_rows * 4))
noise_bytes = (ROOT / 'textures/cloud_noise.dat').read_bytes()
noise_n = round((len(noise_bytes) // 4) ** (1 / 3))
noise = ctx.texture3d((noise_n,) * 3, 4, noise_bytes)
noise.filter = (moderngl.LINEAR, moderngl.LINEAR)
noise.repeat_x = noise.repeat_y = noise.repeat_z = True
shape_path = pathlib.Path(os.environ.get('SHAPE', str(ROOT / 'textures/cloud_shape.dat')))
shape = ctx.texture3d((128,) * 3, 1, shape_path.read_bytes() if shape_path.exists() else bytes(128 ** 3))
shape.filter = (moderngl.LINEAR, moderngl.LINEAR)
shape.repeat_x = shape.repeat_y = shape.repeat_z = True
waves_path = ROOT / 'textures/water_waves.dat'
waves = ctx.texture((128, 128), 4, waves_path.read_bytes() if waves_path.exists() else bytes(128 * 128 * 4))
waves.filter = (moderngl.LINEAR, moderngl.LINEAR)
waves.repeat_x = waves.repeat_y = True
moon_im = Image.open(ROOT / 'textures/moon.png').convert('RGBA')
moon = ctx.texture(moon_im.size, 4, moon_im.tobytes())
moon.filter = (moderngl.LINEAR, moderngl.LINEAR)

IMAGES = {'cg_sky_img': sky, 'cg_globals_img': globals_tex, 'cg_data_img': data, 'cg_cloud_img': cloud,
          'vt_out': out_img, 'cg_vox_img': vox, 'cg_sdf_img': sdf}
SAMPLERS = {'cg_water_waves': waves, 'cg_sky_tex': sky, 'cg_globals_tex': globals_tex, 'cg_cloud_noise': noise, 'cg_cloud_shape': shape, 'cg_moon': moon,
            'depthtex0': depth, 'depthtex1': depth, 'colortex0': blank, 'colortex1': blank, 'colortex2': blank,
            'colortex3': blank, 'colortex4': blank, 'cg_world_tex': vox, 'cg_palette_tex': palette, 'cg_sdf_tex': sdf}

# ---------------------------------------------------------------- the view's own program
DRAW = r'''
#include "/lib/settings.glsl"
#include "/lib/gbuffer_read.glsl"
#include "/lib/sky.glsl"
layout(rgba16f) readonly uniform image2D cg_cloud_img;
#include "/lib/clouds.glsl"
#include "/lib/post.glsl"
VT_INCLUDES
layout(local_size_x = 8, local_size_y = 8) in;
layout(rgba32f) uniform image2D vt_out;
uniform int vt_raw;

void main() {
    ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(vec2(texel), render_size()))) {
        return;
    }
    float depth = texelFetch(depthtex0, texel, 0).r;
    vec3 view = pixel_to_view(texel, min(depth, 0.9999999));
    vec3 scene = view_to_scene_pos(view);
    vec3 dir = normalize(scene);
    vec3 c;
    if (depth >= 1.0) {
        // The sky and the clouds over it, as program/deferred_light.fsh lays them.
        c = sky(dir, true);
        float bottom = cloud_bottom();
        float y0 = cameraPosition.y;
        float surface_y = dir.y > 0.0 ? 1e6 : -1e6;
        if (clouds_here() && !(y0 < bottom && surface_y < bottom)
                && !(y0 > bottom + cloud_depth && surface_y > bottom + cloud_depth)) {
            vec4 cl = clouds_at(texel, cloud_far_sky).light;
            c = c * cl.a + cl.rgb;
        }
    } else {
        VT_SURFACE
    }
    if (vt_raw == 0) {
        c = linear_to_srgb(grade(tone_curve(c * exposure_scale())));
    }
    imageStore(vt_out, ivec2(texel.x, int(render_size().y) - 1 - texel.y), vec4(c, 1.0));
}
'''
# What the sea's pixels take: the pack's own water and haze where it has them as functions (see the libs), else a
# plain dark sea so the sky and the clouds can still be looked at.
HAS_WATER = (ROOT / 'lib/water_shade.glsl').exists()
HAS_HAZE = (ROOT / 'lib/haze.glsl').exists()
INCLUDES = ('#include "/lib/water_shade.glsl"\n' if HAS_WATER else '') + ('#include "/lib/haze.glsl"\n' if HAS_HAZE else '')
INCLUDES += r"""
#ifdef VT_WATER
#include "/lib/sky_mirror.glsl"
// The open sea as program/gbuffers.fsh shades water (64 blocks deep, nothing in it, no shadows but the clouds').
vec3 water_open_sea(vec3 scene, ivec2 texel) {
    vec3 to_eye = normalize(-scene);
    float footprint;
    Waves w = water_waves_seen(scene, to_eye, 1.0, 1.0, g_water.x, footprint);
    WaterSurface s = water_finish(w, vec3(0.0, 1.0, 0.0), to_eye, false);
    float shade = cloud_shadow(scene);
    vec3 sun = g_light_color.rgb * max(g_light_dir.y, 0.0) * shade;
    vec3 light_here = sun + g_sky_up.rgb;
    vec3 through;
    vec3 glow = water_body(light_here, water_view_extinction(vec3(0.247, 0.463, 0.894)), 64.0, s.cos_v, through);
    vec3 r = reflect(-to_eye, s.n);
    r.y = abs(r.y);
    vec3 mirrored = reflect_sky(r, scene, s.soft);
    if (g_light_dir.w > 0.5) {
        mirrored += sun_seen(r, s.spread) * shade;
    }
    return (glow + water_crest_glow(s, to_eye, sun)) * (1.0 - s.fresnel) + mirrored * s.fresnel;
}
#endif
vec3 vt_sea(ivec2 texel, vec3 scene) {
#ifdef VT_WATER
    vec3 c = water_open_sea(scene, texel);
#else
    vec3 c = vec3(0.02, 0.05, 0.09) * g_sky_up.rgb * 3.0;
#endif
    VT_HAZE
    if (clouds_here() && cameraPosition.y > cloud_bottom()) {
        vec4 cl = clouds_at(texel, length(scene)).light;
        c = c * cl.a + cl.rgb;
    }
    return c;
}
""".replace('VT_HAZE', 'c = haze_over(c, normalize(scene), length(scene), 1.0, %.1f);'
            % float(os.environ.get('FAR_END', '2048')) if HAS_HAZE else '')
if HAS_WATER:
    INCLUDES = '#define VT_WATER\n' + INCLUDES
SURFACE = 'c = vt_sea(texel, scene);'
VIRTUAL['vt_draw'] = DRAW.replace('VT_INCLUDES', INCLUDES).replace('VT_SURFACE', SURFACE)


# ---------------------------------------------------------------- Iris's uniforms
def mat(m):
    return tuple(np.asarray(m, dtype='f4').T.flatten())


def camera(yaw, pitch, fov):
    yr, pr = math.radians(yaw), math.radians(pitch)
    fwd = np.array([-math.sin(yr) * math.cos(pr), -math.sin(pr), math.cos(yr) * math.cos(pr)])
    right = np.cross(fwd, [0.0, 1.0, 0.0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    view = np.eye(4); view[0, :3] = right; view[1, :3] = up; view[2, :3] = -fwd
    f = 1 / math.tan(math.radians(fov) / 2)
    proj = np.array([[f * H / W, 0, 0, 0], [0, f, 0, 0],
                     [0, 0, (FAR + NEAR) / (NEAR - FAR), 2 * FAR * NEAR / (NEAR - FAR)], [0, 0, -1, 0]])
    return view, proj


def sun_direction(ticks):
    """Where Minecraft puts the sun: rising in the east (+x), overhead at 6000 ticks, tilted by sunPathRotation."""
    tilt = float(re.search(r'const float sunPathRotation = ([-\d.]+);', (ROOT / 'lib/settings.glsl').read_text())
                 .group(1))
    # Minecraft's own clock: noon at 6000, the sun a little quicker near the horizon than overhead.
    f = (ticks / 24000.0 - 0.25) % 1.0
    angle = (f * 2.0 + (0.5 - math.cos(f * math.pi) / 2.0)) / 3.0
    a = 2 * math.pi * angle
    x, y = -math.sin(a), math.cos(a)
    t = math.radians(tilt)
    return np.array([x, y * math.cos(t), y * math.sin(t)])


def sea_depth(view, proj, cam_y):
    """The depth buffer of an open sea: the plane at SEA_Y under the horizon, the sky (1) above it."""
    ys, xs = np.mgrid[0:H, 0:W]
    ndc = np.stack([(xs + 0.5) / W * 2 - 1, (ys + 0.5) / H * 2 - 1, np.ones_like(xs, float), np.ones_like(xs, float)],
                   -1)
    v = ndc @ np.linalg.inv(proj).T
    v = v[..., :3] / v[..., 3:4]
    d = v @ view[:3, :3]  # view to scene (the view matrix is a rotation: its transpose is its inverse)
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    with np.errstate(divide='ignore', invalid='ignore'):
        t = np.where(d[..., 1] < -1e-6, (SEA_Y - cam_y) / d[..., 1], np.inf)
    if cam_y < SEA_Y:
        t = np.where(d[..., 1] > 1e-6, (SEA_Y - cam_y) / d[..., 1], np.inf)
    p = d * np.where(np.isfinite(t), t, 0.0)[..., None]
    pv = p @ view[:3, :3].T
    z = -pv[..., 2]
    with np.errstate(divide='ignore', invalid='ignore'):
        dep = (proj[2, 2] * -z + proj[2, 3]) / z * 0.5 + 0.5
    dep = np.where(np.isfinite(t) & (z < FAR * 0.98) & (z > NEAR), dep, 1.0)
    return dep.astype('f4')


def weather_row(cam):
    """The Bridge's weather map (its WeatherMap) round the camera, from WEATHER: 64 x 64 points 160 blocks apart, a
    point in slot (x mod 64, z mod 64) of the row after the far heights'."""
    spec = os.environ.get('WEATHER')
    if not spec or palette_rows < 881:
        return
    if spec.startswith('row:'):
        words = np.fromfile(spec[4:], np.uint32)
    else:
        parts = spec.split('@')
        kinds = [[float(v) for v in k.split(',')] for k in parts[0].split(';')]
        edge = float(parts[1]) if len(parts) > 1 else 0.0
        cx, cz = math.floor(cam[0] / 160), math.floor(cam[2] / 160)
        words = np.zeros(4096, np.uint32)
        for z in range(cz - 32, cz + 32):
            for x in range(cx - 32, cx + 32):
                f = min(max((x * 160 - cam[0] - edge) / 640.0 + 0.5, 0.0), 1.0) if len(kinds) > 1 else 0.0
                f = f * f * (3 - 2 * f)
                a, d, h = (k0 + (k1 - k0) * f for k0, k1 in zip(kinds[0], kinds[-1]))
                words[(z & 63) * 64 + (x & 63)] = (1 << 31 | round(a * 4095) | round(d * 127) << 12
                                                   | round(h * 100) << 19 | 10 << 26)
    palette.write(words.tobytes(), viewport=(0, 880, 4096, 1))


def render(spec):
    vals = [float(x) for x in spec.split(',')]
    ticks, yaw, pitch = vals[:3]
    cam_y = vals[3] if len(vals) > 3 else 66.0
    rain = vals[4] if len(vals) > 4 else 0.0
    fov = vals[5] if len(vals) > 5 else 70.0
    day = int(vals[6]) if len(vals) > 6 else 3
    px, pz = (float(v) for v in os.environ.get('POS', '0,0').split(','))
    cam = np.array([px + 0.5, cam_y, pz + 0.5])
    ci = tuple(int(math.floor(c)) for c in cam)
    cf = tuple(float(c - math.floor(c)) for c in cam)
    view, proj = camera(yaw, pitch, fov)
    sun = sun_direction(ticks)
    light = sun if sun[1] > 0 else -sun
    rot = view[:3, :3]
    depth.write(sea_depth(view, proj, cam_y).tobytes())
    weather_row(cam)
    u = {
        'viewWidth': float(W), 'viewHeight': float(H), 'taau_render_scale': (1.0, 1.0),
        'cameraPosition': tuple(cam), 'cameraPositionInt': ci, 'cameraPositionFract': cf,
        'previousCameraPositionInt': ci, 'previousCameraPositionFract': cf,
        'gbufferModelView': mat(view), 'gbufferModelViewInverse': mat(np.linalg.inv(view)),
        'gbufferProjection': mat(proj), 'gbufferProjectionInverse': mat(np.linalg.inv(proj)),
        'gbufferPreviousModelView': mat(view), 'gbufferPreviousProjection': mat(proj),
        'sunPosition': tuple(rot @ sun * 100.0), 'shadowLightPosition': tuple(rot @ light * 100.0),
        'worldTime': int(ticks) % 24000, 'worldDay': day, 'moonPhase': int(os.environ.get('MOON', '0')),
        'rainStrength': rain, 'cloudHeight': 192.0, 'biome_precipitation': 1,
        'frameTime': float(os.environ.get('FRAME_TIME', 1 / 60.0)), 'near': NEAR, 'far': FAR, 'isEyeInWater': 1 if cam_y < SEA_Y else 0,
        'eyeBrightnessSmooth': (0, 240), 'fogColor': (0.6, 0.7, 1.0),
    }
    for fr in range(FRAMES):
        u['frameCounter'] = fr
        u['frameTimeCounter'] = 10.0 + fr / 60.0
        for name in ('setup', 'setup1') if fr == 0 else ():
            progs[name].run(u)
        for name in ('begin', 'begin2', 'deferred3'):
            progs[name].run(u)
    u['vt_raw'] = 1 if os.environ.get('RAW') == '1' else 0
    progs['draw'].run(u)
    ctx.finish()
    if os.environ.get('MIRROR'):
        # The sky mirror's map (lib/sky_mirror.glsl) as a picture: the clouds over a flat blue, the soft copy under it.
        t = np.frombuffer(sky.read(), np.float16).reshape(sky_rows, 256, 4).astype(np.float32)
        def shown(block):
            c = np.array([0.1, 0.25, 0.6]) * block[..., 3:4] + block[..., :3]
            c = c * 1.4 / np.sqrt(1 + (c * 1.4) ** 2)
            return (np.clip(c, 0, 1) ** (1 / 2.2) * 255).astype(np.uint8)[::-1]
        sharp = shown(t[1084:1180])
        soft = np.kron(shown(t[1184:1208, :64]), np.ones((4, 4, 1), np.uint8))
        Image.fromarray(np.concatenate([sharp, soft], 0)).resize((768, 576), Image.NEAREST).save(os.environ['MIRROR'])
    if os.environ.get('GLOBALS') == '1':
        g = np.frombuffer(globals_tex.read(), np.float32).reshape(-1, 4)
        for i, row in enumerate(g):
            print('global %2d: %s' % (i, ' '.join('%9.4f' % v for v in row)))
    return np.frombuffer(out_img.read(), np.float32).reshape(H, W, 4)[..., :3].copy()


progs = {}
for stage, name in [('setup.csh', 'setup'), ('setup1.csh', 'setup1'), ('begin.csh', 'begin'), ('begin2.csh', 'begin2'),
                    ('deferred3.csh', 'deferred3'), ('vt_draw', 'draw')]:
    progs[name] = Program(stage, name)
for k, n in DEFS_FOUND.items():
    assert n, 'no option ' + k

if __name__ == '__main__':
    out = sys.argv[1]
    views = sys.argv[2:] or ['noon:6000,20,-10', 'sunset:12500,80,-6', 'night:18000,20,-12']
    tiles, raws = [], []
    for v in views:
        name, spec = v.split(':')
        a = render(spec)
        raws.append(a)
        im = Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8))
        if os.environ.get('LABEL', '1') == '1':
            ImageDraw.Draw(im).text((4, 4), name, fill=(255, 255, 0))
        tiles.append(im)
    cols = int(os.environ.get('COLS', '2' if len(tiles) > 1 else '1'))
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (W * cols, H * rows))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * W, (i // cols) * H))
    sheet.save(out)
    if os.environ.get('RAW') == '1':
        np.save(os.path.splitext(out)[0] + '.npy', np.stack(raws))
    print('saved', out)
    for k, v in TIMES.items():
        print(f'{k}: {v:.2f} s')
