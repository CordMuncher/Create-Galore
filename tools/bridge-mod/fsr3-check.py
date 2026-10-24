#!/usr/bin/env python3
"""fsr3-check.py [ROOT]: expands each FSR pass as the Bridge does, preprocesses it with glslang, counts the image and
sampler uniforms each pass DECLARES (NVIDIA counts declared ones, max 8 images per compute shader) and validates the
source with glslang (stricter than Mesa)."""
import re, subprocess, sys, tempfile
from pathlib import Path
root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "src/main/resources/create_galore_bridge/fsr3"
DEFINES = ["FFX_GPU 1", "FFX_GLSL 1", "FFX_HALF 0", "FFX_SPD_NO_WAVE_OPERATIONS 1",
           "FFX_FSR3UPSCALER_OPTION_HDR_COLOR_INPUT 1", "FFX_FSR3UPSCALER_OPTION_LOW_RESOLUTION_MOTION_VECTORS 1",
           "FFX_FSR3UPSCALER_OPTION_JITTERED_MOTION_VECTORS 0", "FFX_FSR3UPSCALER_OPTION_INVERTED_DEPTH 1",
           "FFX_FSR3UPSCALER_OPTION_APPLY_SHARPENING 0", "FFX_FSR3UPSCALER_OPTION_REPROJECT_USE_LANCZOS_TYPE 0"]
def expand(path, depth=0):
    out = []
    for line in path.read_text().splitlines():
        m = re.match(r'\s*#\s*include\s+"([^"]+)"', line)
        if m:
            inc = root / m.group(1)
            if not inc.exists():
                inc = path.parent / m.group(1)
            out.append(expand(inc, depth + 1) if inc.exists() else f'#error missing include {m.group(1)}')
        else:
            out.append(line)
    return "\n".join(out)
bad = False
for p in sorted((root / "passes").glob("*.glsl")):
    body = re.sub(r'^#version.*$', '', expand(p), count=1, flags=re.M)
    src = "#version 450 core\n" + "".join(f"#define {d}\n" for d in DEFINES) + body
    with tempfile.NamedTemporaryFile("w", suffix=".comp", delete=False) as f:
        f.write(src)
    pre = subprocess.run(["glslangValidator", "-E", f.name], capture_output=True, text=True).stdout
    images = re.findall(r'uniform\s+u?image2D\s+(\w+)', pre)
    samplers = re.findall(r'uniform\s+u?sampler2D\s+(\w+)', pre)
    val = subprocess.run(["glslangValidator", f.name], capture_output=True, text=True)
    status = "OK" if val.returncode == 0 else "GLSLANG FAIL"
    over = len(images) > 8
    bad |= over or val.returncode != 0
    print(f"{p.stem:50s} images {len(images)}{' OVER 8' if over else ''}  samplers {len(samplers)}  {status}")
    if val.returncode != 0:
        print("   ", "\n    ".join(l for l in val.stdout.splitlines() if "ERROR" in l)[:1500])
    if over:
        print("    images:", ", ".join(images))
sys.exit(1 if bad else 0)
