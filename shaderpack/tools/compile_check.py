#!/usr/bin/env python3
"""Compile-checks every program of the shader pack with glslangValidator (apt glslang-tools), the way Iris
assembles it: #include "/x" from shaders/, Iris's macros after #version, and stand-ins for what Colorwheel adds.
The root programs and the Nether's, the overworld's and the End's (world-1/, world0/, world1/), each with the
option variants that change the code. It doesn't catch a variable used before it is set.

With --samplers it also counts each fragment and compute program's active samplers, as Iris gives each one a
texture unit and refuses the whole pack when a program wants more than it has ("No more available texture units"):
GL_MAX_TEXTURE_IMAGE_UNITS (32 on NVIDIA and on Mesa) less the units Iris keeps for Minecraft's own textures, 2 in
the deferred and composite passes and 3 in the gbuffers ones. (glslang's count matches the driver's here.)

Iris runs the preprocessor itself before the driver sees the code, without __VERSION__, so a program's #if on it
always takes the false side in game; this check fails on any.

  python3 shaderpack/tools/compile_check.py shaderpack/shaders [--samplers] [part of a program's path]"""
import concurrent.futures, os, pathlib, re, subprocess, sys, tempfile
TMP = tempfile.mkdtemp(prefix="compile_check_")

args = [a for a in sys.argv[1:] if not a.startswith("--")]
count_samplers = "--samplers" in sys.argv
def sampler_limit(rel):
    name = pathlib.PurePath(rel).name
    return 29 if name.startswith(("gbuffers", "dh_", "clrwl", "shadow")) else 30
root = pathlib.Path(args[0])
only = args[1] if len(args) > 1 else None
IRIS = """#define MC_VERSION 12101
#define MC_GL_VERSION 460
#define MC_GLSL_VERSION 460
#define MC_RENDER_STAGE_SUNSET 2
#define MC_HAND_DEPTH 0.125
#define IS_IRIS
#define DH_BLOCK_UNKNOWN 0
#define DH_BLOCK_LAVA 6
#define DH_BLOCK_WATER 12
#define DH_BLOCK_ILLUMINATED 15
"""
CLRWL_DEFS = "#define CLRWL_OLD_LIGHTING\n"
CLRWL = """
void clrwl_computeFragment(vec4 sampleColor, out vec4 fragColor, out vec2 fragLight, out float fragAo, out vec4 overlayColor) {
    fragColor = sampleColor; fragLight = vec2(0.5); fragAo = 1.0; overlayColor = vec4(0.0);
}
float _clrwl_diffuseFactor() { return 1.0; }
"""
# (name, defines, settings line edits)
ROOT_VARIANTS = [("", [], []), ("dh", ["DISTANT_HORIZONS"], []), ("up", ["UPSCALING"], [])]
WORLD_VARIANTS = ROOT_VARIANTS + [
    ("dh-up", ["DISTANT_HORIZONS", "UPSCALING"], []),
    ("noshadow", [], [("#define SHADOWS ", "//#define SHADOWS ")]),
    ("unlit", [], [("#define LIGHTING\n", "//#define LIGHTING\n")]),
    ("unlit-dh", ["DISTANT_HORIZONS"], [("#define LIGHTING\n", "//#define LIGHTING\n")]),
    ("noclouds", [], [("#define CLOUDS\n", "//#define CLOUDS\n")]),
    ("cq1", [], [("#define CLOUD_QUALITY 3 ", "#define CLOUD_QUALITY 1 ")]),
    ("cq2-dh", ["DISTANT_HORIZONS"], [("#define CLOUD_QUALITY 3 ", "#define CLOUD_QUALITY 2 ")]),
    ("cq4", [], [("#define CLOUD_QUALITY 3 ", "#define CLOUD_QUALITY 4 ")]),
    ("nocirrus-noshadow", [], [("#define CIRRUS\n", "//#define CIRRUS\n"), ("#define CLOUD_SHADOWS\n", "//#define CLOUD_SHADOWS\n")]),
    ("nocolor", [], [("#define COLORED_LIGHT\n", "//#define COLORED_LIGHT\n")]),
    ("cl64", [], [("#define LIGHT_RANGE 128 ", "#define LIGHT_RANGE 64 ")]),
    ("cl256-dh", ["DISTANT_HORIZONS"], [("#define LIGHT_RANGE 128 ", "#define LIGHT_RANGE 256 ")]),
    ("noripples", [], [("#define WATER_RIPPLES\n", "//#define WATER_RIPPLES\n")]),
    ("noripples-nocolor", [], [("#define WATER_RIPPLES\n", "//#define WATER_RIPPLES\n"), ("#define COLORED_LIGHT\n", "//#define COLORED_LIGHT\n")]),
    ("nogi", [], [("#define GI\n", "//#define GI\n")]),
    ("nogi-nocolor", [], [("#define GI\n", "//#define GI\n"), ("#define COLORED_LIGHT\n", "//#define COLORED_LIGHT\n")]),
    ("nocolor-noripples-cl192", [], [("#define COLORED_LIGHT\n", "//#define COLORED_LIGHT\n"), ("#define WATER_RIPPLES\n", "//#define WATER_RIPPLES\n"), ("#define LIGHT_RANGE 128 ", "#define LIGHT_RANGE 192 ")]),
    ("debug-gi", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 1 ")]),
    ("noall", [], [("#define GI\n", "//#define GI\n"), ("#define COLORED_LIGHT\n", "//#define COLORED_LIGHT\n"), ("#define WATER_RIPPLES\n", "//#define WATER_RIPPLES\n")]),
    ("sq1-ao0", [], [("#define SHADOW_QUALITY 2 ", "#define SHADOW_QUALITY 1 "), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 0 ")]),
    ("sq4-ao3", [], [("#define SHADOW_QUALITY 2 ", "#define SHADOW_QUALITY 4 "), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 3 ")]),
    ("sq3-ao2-dh", ["DISTANT_HORIZONS"], [("#define SHADOW_QUALITY 2 ", "#define SHADOW_QUALITY 3 "), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 2 ")]),
    ("debug-ao", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 2 ")]),
    ("debug-water", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 3 ")]),
    ("debug-water-why", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 4 ")]),
    ("debug-crown", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 5 ")]),
    ("debug-6", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 6 ")]),
    ("debug-6-dh", ["DISTANT_HORIZONS"], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 6 ")]),
    ("nocontact-ao0", [], [("#define CONTACT_SHADOWS\n", "//#define CONTACT_SHADOWS\n"), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 0 ")]),
    ("air0", [], [("#define AIR_QUALITY 2 ", "#define AIR_QUALITY 0 ")]),
    ("air0-dh", ["DISTANT_HORIZONS"], [("#define AIR_QUALITY 2 ", "#define AIR_QUALITY 0 ")]),
    ("air1-up", ["UPSCALING"], [("#define AIR_QUALITY 2 ", "#define AIR_QUALITY 1 ")]),
    ("air3-dh", ["DISTANT_HORIZONS"], [("#define AIR_QUALITY 2 ", "#define AIR_QUALITY 3 ")]),
    ("air4-dh-up", ["DISTANT_HORIZONS", "UPSCALING"], [("#define AIR_QUALITY 2 ", "#define AIR_QUALITY 4 ")]),
    ("air-noshadow-nocolor", [], [("#define SHADOWS ", "//#define SHADOWS "), ("#define COLORED_LIGHT\n", "//#define COLORED_LIGHT\n")]),
    ("nomat", [], [("#define MATERIALS\n", "//#define MATERIALS\n")]),
    ("nomat-dh-up", ["DISTANT_HORIZONS", "UPSCALING"], [("#define MATERIALS\n", "//#define MATERIALS\n")]),
    ("parallax", [], [("//#define PARALLAX\n", "#define PARALLAX\n")]),
    ("parallax-noshadow-dh", ["DISTANT_HORIZONS"], [("//#define PARALLAX\n", "#define PARALLAX\n"), ("#define SHADOWS ", "//#define SHADOWS ")]),
    ("debug-7", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 7 ")]),
    ("debug-9", [], [("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 9 ")]),
    ("bounce-ao2", [], [("//#define SCREEN_BOUNCE\n", "#define SCREEN_BOUNCE\n"), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 2 ")]),
    ("grade", [], [("#define WB_TEMPERATURE 6500 ", "#define WB_TEMPERATURE 5000 "), ("#define WB_TINT 0 ", "#define WB_TINT 10 "), ("#define PRIMARY_RED_HUE 0 ", "#define PRIMARY_RED_HUE 20 "), ("//#define COLOR_MIXER", "#define COLOR_MIXER"), ("#define MIX_RED_HUE 0 ", "#define MIX_RED_HUE 30 ")]),
    ("dof", [], [("//#define DEPTH_OF_FIELD", "#define DEPTH_OF_FIELD")]),
    ("dof-dh-up-q3-fixed", ["DISTANT_HORIZONS", "UPSCALING"], [("//#define DEPTH_OF_FIELD", "#define DEPTH_OF_FIELD"), ("#define DOF_QUALITY 2 ", "#define DOF_QUALITY 3 "), ("#define DOF_FOCUS 0 ", "#define DOF_FOCUS 8 ")]),
    ("bounce-ao3-dh", ["DISTANT_HORIZONS"], [("//#define SCREEN_BOUNCE\n", "#define SCREEN_BOUNCE\n"), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 3 ")]),
    ("refl0-ao2", [], [("#define REFLECTION_QUALITY 1 ", "#define REFLECTION_QUALITY 0 "), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 2 ")]),
    ("refl3-ao0-up", ["UPSCALING"], [("#define REFLECTION_QUALITY 1 ", "#define REFLECTION_QUALITY 3 "), ("#define AO_QUALITY 1 ", "#define AO_QUALITY 0 ")]),
    ("refl2-dh", ["DISTANT_HORIZONS"], [("#define REFLECTION_QUALITY 1 ", "#define REFLECTION_QUALITY 2 ")]),
    ("nosky", [], [("#define SKY_VISIBILITY\n", "//#define SKY_VISIBILITY\n")]),
    ("nosky-nogi-dh", ["DISTANT_HORIZONS"], [("#define SKY_VISIBILITY\n", "//#define SKY_VISIBILITY\n"), ("#define GI\n", "//#define GI\n")]),
    ("nocrowns", [], [("#define TREE_CROWNS\n", "//#define TREE_CROWNS\n")]),
    ("nocrowns-debug-crown", [], [("#define TREE_CROWNS\n", "//#define TREE_CROWNS\n"), ("#define DEBUG_VIEW 0 ", "#define DEBUG_VIEW 5 ")]),
    ("noshore-nocaustics-nowet", [], [("#define WATER_SHORE\n", "//#define WATER_SHORE\n"), ("#define WATER_CAUSTICS\n", "//#define WATER_CAUSTICS\n"), ("#define RAIN_WETNESS\n", "//#define RAIN_WETNESS\n")]),
    ("noshore-noripples-dh", ["DISTANT_HORIZONS"], [("#define WATER_SHORE\n", "//#define WATER_SHORE\n"), ("#define WATER_RIPPLES\n", "//#define WATER_RIPPLES\n")]),
    ("air-noclouds-noall-dh", ["DISTANT_HORIZONS"], [("#define CLOUDS\n", "//#define CLOUDS\n"), ("#define GI\n", "//#define GI\n"), ("#define COLORED_LIGHT\n", "//#define COLORED_LIGHT\n"), ("#define WATER_RIPPLES\n", "//#define WATER_RIPPLES\n")]),
]

def expand(path, edits):
    out = []
    text = path.read_text()
    if path.name == "settings.glsl":
        for a, b in edits:
            assert a in text, (a, path)
            text = text.replace(a, b)
    for line in text.splitlines():
        m = re.match(r'\s*#include\s+"(/[^"]+)"', line)
        if m:
            out.append(expand(root / m.group(1).lstrip('/'), edits))
        else:
            out.append(line)
    return "\n".join(out)

progs = []
END_VARIANTS = ROOT_VARIANTS + [("noendsky", [], [("#define END_SKY\n", "//#define END_SKY\n")])]
# The Nether and the End are lit through copies of the overworld's passes: a few variants each.
LIT_OTHER = [v for v in WORLD_VARIANTS if v[0] in ("", "dh", "up", "dh-up", "noshadow", "unlit", "unlit-dh", "nocolor", "nogi", "noall",
             "air0", "air4-dh-up", "sq1-ao0", "nomat", "parallax-noshadow-dh", "dof", "debug-6", "nosky", "nocrowns")]
for d, variants in ((root, ROOT_VARIANTS), (root / "world0", WORLD_VARIANTS), (root / "world-1", LIT_OTHER),
                    (root / "world1", LIT_OTHER + [("noendsky", [], [("#define END_SKY\n", "//#define END_SKY\n")])])):
    for ext in ("vsh", "fsh", "csh"):
        progs += [(p, variants) for p in sorted(d.glob("*." + ext))]

jobs = []
version_fails = []
for prog, variants in progs:
    rel = prog.relative_to(root)
    if only and only not in str(rel):
        continue
    # The overworld's own passes only load with the options that need them (see shaders.properties).
    text0 = prog.read_text()
    lit_only = (prog.parent.name == "world0" and "Generated by" not in text0) or "dimensions.sh from world0/" in text0
    if prog.parent.name == "world-1" and prog.name == "shadowcomp1.csh":
        continue  # never loaded in the Nether (no bounce light)
    for vname, defines, edits in variants:
        if lit_only and vname.startswith("unlit"):
            continue
        if lit_only and "shadow" in prog.name and "noshadow" in vname:
            continue
        # Which shadowcomp passes each variant loads (see shaders.properties).
        gi, color, ripples = "nogi" not in vname and "noall" not in vname, "nocolor" not in vname and "noall" not in vname, "noripples" not in vname and "noall" not in vname
        if prog.parent.name == "world-1":
            gi = False  # the Nether has no bounce light (see shaders.properties)
        if lit_only and prog.name == "shadowcomp1.csh" and not gi:
            continue
        if lit_only and prog.name == "shadowcomp2.csh" and not (gi or color):
            continue
        if lit_only and prog.name in ("shadowcomp3.csh", "shadowcomp4.csh", "shadowcomp5.csh") and not ripples:
            continue
        if lit_only and prog.name == "shadowcomp11.csh" and (not (gi or color) or "nosky" in vname):
            continue
        src = expand(prog, edits)
        if re.search(r"#\s*(el)?if.*__VERSION__", src):
            print(f"== {rel} [{vname}]\nan #if on __VERSION__, which Iris's preprocessor doesn't define")
            version_fails.append(rel)
        lines = src.split("\n")
        assert lines[0].startswith("#version"), prog
        extra = IRIS + "".join(f"#define {d}\n" for d in defines)
        body = "\n".join(lines[1:])
        if prog.name.startswith("clrwl"):
            extra += CLRWL_DEFS
            if prog.suffix == ".fsh":
                # Colorwheel puts its material code before the pack's main.
                body = body.replace("void main()", CLRWL + "\nvoid main()", 1)
        if prog.name.startswith("dh_") and prog.suffix == ".vsh":
            body = "int dhMaterialId;\n" + body  # Iris declares it in Distant Horizons' vertex programs
        version = lines[0]
        stage = {".vsh": "vert", ".fsh": "frag", ".csh": "comp"}[prog.suffix]
        if stage == "comp":
            version = version.replace("compatibility", "core")
        text = version + "\n" + extra + body
        jobs.append((rel, vname, stage, text))


def run(job):
    rel, vname, stage, text = job
    with tempfile.NamedTemporaryFile("w", suffix="." + stage, dir=TMP, delete=False) as f:
        f.write(text)
    sampling = count_samplers and stage != "vert"
    r = subprocess.run(["glslangValidator"] + (["-l", "-q"] if sampling else []) + [f.name], capture_output=True, text=True)
    os.unlink(f.name)
    active = None
    if sampling and r.returncode == 0:
        declared = set(m.group(1) for m in re.finditer(r"uniform\s+(?:\w+\s+)?\w*sampler\w*\s+(\w+)", text))
        active, section = set(), False
        for line in r.stdout.splitlines():
            if line.endswith("reflection:"):
                section = line.startswith("Uniform reflection")
            elif section and line.split(":")[0].strip() in declared:
                active.add(line.split(":")[0].strip())
    return rel, vname, r, active


fails = len(version_fails)
most = []
with concurrent.futures.ThreadPoolExecutor(os.cpu_count() or 4) as pool:
    for rel, vname, r, active in pool.map(run, jobs):
        if r.returncode != 0:
            fails += 1
            out = r.stdout if not count_samplers else "\n".join(l for l in r.stdout.splitlines() if "ERROR" in l)
            print(f"== {rel} [{vname}]\n{out}{r.stderr}")
        if active is not None:
            most.append((len(active), str(rel), vname))
print(f"{len(jobs) - fails}/{len(jobs)} compiled")
if count_samplers:
    most.sort(reverse=True)
    for n, rel, vname in most[:5]:
        print(f"{n} samplers: {rel} [{vname}]")
    over = [m for m in most if m[0] > sampler_limit(m[1])]
    for n, rel, vname in over[:10]:
        print(f"over the {sampler_limit(rel)} texture units Iris has for it: {rel} [{vname}] with {n}")
    fails += len(over)
sys.exit(1 if fails else 0)
