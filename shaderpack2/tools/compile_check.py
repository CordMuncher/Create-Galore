#!/usr/bin/env python3
"""Compile-checks every program of the pack with glslangValidator (apt glslang-tools), the way Iris assembles it:
#include "/x" from shaders/, Iris's macros after #version, and stand-ins for what Colorwheel and Distant Horizons
add. The root programs and the Nether's and the End's (world-1/, world1/), each with the option variants that
change the code. It doesn't catch a variable used before it is set.

It also counts each program's active samplers and images: Iris refuses the whole pack when a program wants more
texture units than it has ("No more available texture units": 32 on NVIDIA and Mesa, less the units Iris keeps
for Minecraft's own textures, 2 in the deferred and composite passes and 3 in the world's), and NVIDIA's drivers
give a program 8 image units.

Iris runs the preprocessor itself before the driver sees the code, without __VERSION__, so a program's #if on it
always takes the false side in game; this check fails on any.

And it checks what Iris refuses or misreads when it loads the pack: an on/off option no file tests with #ifdef or
#ifndef (Iris then doesn't take it as an option at all), storage buffers (Iris takes them only at bindings 0 to 8,
which Flywheel binds its own over every frame), more than 16 custom images, a compute program's workGroups or
workGroupsRender that is not plain numbers once the preprocessor has run, and an image or a cg_ sampler a program
uses that shaders.properties doesn't make (an image. or customTexture. line).

  python3 shaderpack2/tools/compile_check.py [part of a program's path]"""
import concurrent.futures, os, pathlib, re, subprocess, sys, tempfile

TMP = tempfile.mkdtemp(prefix="compile_check_")
root = pathlib.Path(__file__).resolve().parent.parent / "shaders"
only = sys.argv[1] if len(sys.argv) > 1 else None
IMAGE_UNITS = 8
CUSTOM_IMAGES = 16

IRIS = """#define MC_VERSION 12101
#define MC_GL_VERSION 460
#define MC_GLSL_VERSION 460
#define MC_RENDER_STAGE_MOON 5
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


def flip(name, on):
    """A settings edit that turns a boolean option on or off."""
    return (f"#define {name}\n", f"//#define {name}\n") if not on else (f"//#define {name}\n", f"#define {name}\n")


def value(name, old, new):
    return (f"#define {name} {old} ", f"#define {name} {new} ")


# (name, defines, settings edits)
VARIANTS = [
    ("", [], []),
    ("dh", ["DISTANT_HORIZONS"], []),
    ("up", ["UPSCALING"], []),
    ("dh-up", ["DISTANT_HORIZONS", "UPSCALING"], []),
    ("nopixel", [], [flip("PIXEL_LIGHT", False)]),
    ("nogi-nossao", [], [flip("GI", False), flip("SSAO", False)]),
    ("nohand-nospec", [], [flip("HAND_LIGHT", False), flip("LIGHT_SPECULAR", False)]),
    ("box0-scale1", [], [value("VOXEL_BOX", 1, 0), value("PIXEL_LIGHT_SCALE", 3, 1)]),
    ("box2-scale6", ["DISTANT_HORIZONS"], [value("VOXEL_BOX", 1, 2), value("PIXEL_LIGHT_SCALE", 3, 6)]),
    ("noall", [], [flip("PIXEL_LIGHT", False), flip("GI", False), flip("SSAO", False), flip("HAND_LIGHT", False)]),
    ("noclouds", [], [flip("REAL_CLOUDS", False)]),
] + [(f"debug-{n}", [], [value("DEBUG_VIEW", 0, n)]) for n in range(1, 15)]


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


RESERVED = ("common|partition|active|asm|class|union|enum|typedef|template|this|packed|resource|goto|inline|noinline|"
            "public|static|extern|external|interface|long|short|half|fixed|unsigned|superp|input|output|filter|sizeof|"
            "cast|namespace|using")


def texture_unit_limit(name):
    return 29 if name.startswith(("gbuffers", "dh_", "clrwl", "shadow")) else 30


progs = []
for d in (root, root / "world-1", root / "world1"):
    for ext in ("vsh", "gsh", "fsh", "csh"):
        progs += sorted(d.glob("*." + ext))

jobs = []
fails = 0

# The pack's custom images, in every option's branch: image.<image name> = <sampler name> ...
properties = (root / "shaders.properties").read_text()
made_images, made_samplers = set(), set()
for m in re.finditer(r"^image\.(\w+)\s*=\s*(\w+)", properties, re.M):
    made_images.add(m.group(1))
    if m.group(2) != "none":
        made_samplers.add(m.group(2))
for m in re.finditer(r"^customTexture\.(\w+)\s*=", properties, re.M):
    made_samplers.add(m.group(1))
if len(made_images) > CUSTOM_IMAGES:
    print(f"{len(made_images)} custom images, over the {CUSTOM_IMAGES} Iris allows: {sorted(made_images)}")
    fails += 1
if re.search(r"^\s*bufferObject\.", properties, re.M):
    print("a storage buffer in shaders.properties: Iris takes them only at bindings 0 to 8, which Flywheel binds over")
    fails += 1
# One light table (light_kind in lib/blocks.glsl) for a light placed and the same light held: every light block in
# block.properties has its colour there, and every held light in item.properties is a light block's number.
blocks_glsl = (root / "lib" / "blocks.glsl").read_text()
kinds = int(re.search(r"#define LIGHT_KINDS (\d+)", blocks_glsl).group(1))
light_base = int(re.search(r"#define BLOCK_LIGHT (\d+)", blocks_glsl).group(1))
table = re.search(r"vec4 light_kind\(int kind\) \{(.*?)\n\}", blocks_glsl, re.S).group(1)
with_colour = {int(k) for k in re.findall(r"case (\d+): return vec4\(", table)}
light_blocks = {int(n) for n in re.findall(r"^block\.(\d+)\s*=", (root / "block.properties").read_text(), re.M)
                if light_base <= int(n) < light_base + kinds}
for n in sorted(light_blocks):
    if n - light_base not in with_colour:
        print(f"block.{n} is a light with no colour in light_kind (lib/blocks.glsl)")
        fails += 1
for n in re.findall(r"^item\.(\d+)\s*=", (root / "item.properties").read_text(), re.M):
    if light_base <= int(n) < light_base + kinds and int(n) not in light_blocks:
        print(f"item.{n} is a held light with no light block of its number in block.properties")
        fails += 1
# Iris takes a bare #define on a menu screen as an on/off option only when some file tests it with #ifdef or #ifndef
# (#if defined doesn't count); otherwise it isn't an option at all, and #ifdef on it in shaders.properties is false.
on_screens = set(re.findall(r"\b[A-Z][A-Z0-9_]+\b", " ".join(re.findall(r"^screen\.?\w*\s*=(.*)$", properties, re.M))))
settings = (root / "lib" / "settings.glsl").read_text()
switches = set(re.findall(r"^\s*(?://\s*)?#define\s+([A-Z][A-Z0-9_]*)\s*(?://.*)?$", settings, re.M)) & on_screens
tested = set()
for path in root.rglob("*"):
    if path.suffix in (".glsl", ".vsh", ".gsh", ".fsh", ".csh"):
        tested.update(re.findall(r"^\s*#\s*if(?:n)?def\s+(\w+)", path.read_text(errors="ignore"), re.M))
for name in sorted(switches - tested):
    print(f"the option {name} is never tested with #ifdef or #ifndef, so Iris doesn't take it as an option")
    fails += 1
# The Nether's and the End's programs differ from the overworld's by their dimension only: a few variants each.
OTHER_WORLD_VARIANTS = ("", "dh-up", "nopixel", "noall", "debug-7")
for prog in progs:
    rel = prog.relative_to(root)
    if only and only not in str(rel):
        continue
    for vname, defines, edits in VARIANTS:
        if prog.parent != root and vname not in OTHER_WORLD_VARIANTS:
            continue
        src = expand(prog, edits)
        # Words GLSL keeps for later that glslang lets through as names, but Mesa (and maybe other drivers) refuses.
        reserved = re.search(r"\b(?:u?int|float|bool|[iu]?vec[234]|mat[234])\s+(" + RESERVED + r")\b", src)
        if reserved:
            print(f"== {rel} [{vname}]\na reserved word as a name: {reserved.group(1)}")
            fails += 1
        if re.search(r"#\s*(el)?if.*__VERSION__", src):
            print(f"== {rel} [{vname}]\nan #if on __VERSION__, which Iris's preprocessor doesn't define")
            fails += 1
        lines = src.split("\n")
        assert lines[0].startswith("#version"), prog
        extra = IRIS + "".join(f"#define {d}\n" for d in defines)
        body = "\n".join(lines[1:])
        if prog.name.startswith("clrwl"):
            extra += CLRWL_DEFS
            if prog.suffix == ".fsh":
                # Colorwheel puts its material code before the pack's own.
                body = CLRWL + body
        if prog.name.startswith("dh_") and prog.suffix == ".vsh":
            body = "int dhMaterialId;\n" + body  # Iris declares it in Distant Horizons' vertex programs
        version = lines[0]
        stage = {".vsh": "vert", ".gsh": "geom", ".fsh": "frag", ".csh": "comp"}[prog.suffix]
        if stage == "comp":
            version = version.replace("compatibility", "core")
        jobs.append((rel, vname, stage, version + "\n" + extra + body))


def run(job):
    rel, vname, stage, text = job
    with tempfile.NamedTemporaryFile("w", suffix="." + stage, dir=TMP, delete=False) as f:
        f.write(text)
    r = subprocess.run(["glslangValidator", "-l", "-q", f.name], capture_output=True, text=True)
    problems = []
    if stage == "comp" and r.returncode == 0:
        # Iris reads these from the preprocessed code and wants plain numbers.
        pre = subprocess.run(["glslangValidator", "-E", f.name], capture_output=True, text=True).stdout
        number = r"\s*[0-9.]+\s*"
        for m in re.finditer(r"const\s+(ivec3|vec2)\s+(workGroups\w*)\s*=\s*([^;]*);", pre):
            if not re.fullmatch(rf"{m.group(1)}\({number}(,{number})*\)", m.group(3).strip()):
                problems.append(f"{m.group(2)} is not plain numbers: {m.group(3).strip()}")
    os.unlink(f.name)
    samplers, images = set(), set()
    if r.returncode == 0:
        declared_samplers = set(m.group(1) for m in re.finditer(r"uniform\s+(?:\w+\s+)?\w*sampler\w*\s+(\w+)", text))
        declared_images = set(m.group(1) for m in re.finditer(r"\b[iu]?image[123]D\w*\s+(\w+)\s*;", text))
        section = False
        for line in r.stdout.splitlines():
            if line.endswith("reflection:"):
                section = line.startswith("Uniform reflection")
            elif section:
                name = line.split(":")[0].strip()
                if name in declared_samplers:
                    samplers.add(name)
                if name in declared_images:
                    images.add(name)
    for name in sorted(images - made_images):
        problems.append(f"uses the image {name}, which shaders.properties doesn't make")
    for name in sorted(s for s in samplers if s.startswith("cg_") and s not in made_samplers):
        problems.append(f"uses the sampler {name}, which shaders.properties doesn't make")
    return rel, vname, r, samplers, images, problems


most = []
with concurrent.futures.ThreadPoolExecutor(os.cpu_count() or 4) as pool:
    for rel, vname, r, samplers, images, problems in pool.map(run, jobs):
        if r.returncode != 0:
            fails += 1
            out = "\n".join(l for l in r.stdout.splitlines() if "ERROR" in l or "error" in l)
            print(f"== {rel} [{vname}]\n{out}{r.stderr}")
            continue
        if problems:
            fails += 1
            print(f"== {rel} [{vname}]\n" + "\n".join(problems))
        most.append((len(samplers), len(images), str(rel), vname))
        if len(samplers) > texture_unit_limit(rel.name):
            print(f"over the {texture_unit_limit(rel.name)} texture units Iris has for it: {rel} [{vname}] with {len(samplers)}")
            fails += 1
        if len(images) > IMAGE_UNITS:
            print(f"over {IMAGE_UNITS} image units: {rel} [{vname}] with {len(images)}: {sorted(images)}")
            fails += 1
print(f"{len(jobs) - fails}/{len(jobs)} compiled within the limits")
for n, i, rel, vname in sorted(most, reverse=True)[:3]:
    print(f"{n} samplers, {i} images: {rel} [{vname}]")
for n, i, rel, vname in sorted(most, key=lambda m: -m[1])[:3]:
    print(f"{i} images, {n} samplers: {rel} [{vname}]")
sys.exit(1 if fails else 0)
