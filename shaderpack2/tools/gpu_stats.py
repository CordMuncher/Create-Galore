#!/usr/bin/env python3
"""What a GPU's compiler makes of the pack's compute programs, without a GPU: registers, spills, instructions and an
estimate of cycles per wave, from Mesa's AMD Vulkan driver (RADV) on a null device. A program that needs more
registers runs fewer copies at once on the card, and a spill goes through memory; both slow a pass down on any GPU,
NVIDIA's too, even where a CPU renderer (llvmpipe) shows no difference. The numbers are AMD's, so compare a program
with itself (before and after a change), not with a frame time.

Each program is assembled as compile_check.py does it (Iris's #include and macros), turned into SPIR-V by glslang
with Vulkan's relaxed rules (Iris's loose uniforms go into one block), and compiled into a compute pipeline.

  python3 shaderpack2/tools/gpu_stats.py [part of a program's path ...]   (default: every compute program)
env: ROOT (shaders dir, for a before/after against a copy), DEFS ("A B" extra #defines), EDITS ('NAME=old=>new;;...'
settings.glsl value edits like compile_check's), FAMILY (default navi31), RADV_ICD (radeon_icd json; to get one
without installing: apt-get download mesa-vulkan-drivers, dpkg -x the .deb into a folder, and write a json whose
library_path is that folder's usr/lib/x86_64-linux-gnu/libvulkan_radeon.so). Needs pip's vulkan package and
glslang-tools + spirv-tools."""
import os, pathlib, re, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = pathlib.Path(os.environ.get("ROOT", str(HERE.parent / "shaders")))
os.environ.setdefault("RADV_FORCE_FAMILY", os.environ.get("FAMILY", "navi31"))
if "RADV_ICD" in os.environ:
    os.environ["VK_ICD_FILENAMES"] = os.environ["RADV_ICD"]
import vulkan as vk  # noqa: E402  (after the environment is set)

IRIS = """#define MC_VERSION 12101
#define MC_GL_VERSION 460
#define MC_GLSL_VERSION 460
#define MC_RENDER_STAGE_MOON 5
#define MC_HAND_DEPTH 0.125
#define IS_IRIS
"""


def expand(path, edits):
    text = path.read_text()
    if path.name == "settings.glsl":
        for a, b in edits:
            assert a in text, (a, path)
            text = text.replace(a, b)
    out = []
    for line in text.splitlines():
        m = re.match(r'\s*#include\s+"(/[^"]+)"', line)
        out.append(expand(ROOT / m.group(1).lstrip("/"), edits) if m else line)
    return "\n".join(out)


def source(prog):
    edits = []
    for e in [x for x in os.environ.get("EDITS", "").split(";;") if x]:
        name, old, new = re.match(r"(\w+)=(.*)=>(.*)", e).groups()
        edits.append((f"#define {name} {old} ", f"#define {name} {new} "))
    lines = expand(prog, edits).split("\n")
    defs = "".join(f"#define {d}\n" for d in os.environ.get("DEFS", "").split())
    return lines[0].replace("compatibility", "core") + "\n" + IRIS + defs + "\n".join(lines[1:])


def spirv(prog, tmp):
    src = tmp / (prog.stem + ".comp")
    src.write_text(source(prog))
    out = tmp / (prog.stem + ".spv")
    r = subprocess.run(["glslangValidator", "-V", "-R", "--amb", "--aml", "--target-env", "vulkan1.2", "-o", str(out),
                        str(src)], capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stdout[-2000:] + r.stderr[-2000:])
    return out


def bindings(spv):
    """(set, binding) -> (descriptor type, count) from the SPIR-V's own declarations."""
    dis = subprocess.run(["spirv-dis", "--raw-id", str(spv)], capture_output=True, text=True, check=True).stdout
    types, deco, out = {}, {}, {}
    for line in dis.splitlines():
        m = re.match(r"\s*(%\d+) = (Op\w+)(.*)", line)
        if m:
            types[m.group(1)] = (m.group(2), m.group(3).split())
        m = re.match(r"\s*OpDecorate (%\d+) (DescriptorSet|Binding) (\d+)", line)
        if m:
            deco.setdefault(m.group(1), {})[m.group(2)] = int(m.group(3))
    for var, d in deco.items():
        op, args = types[var]
        if op != "OpVariable":
            continue
        _, (ptr_class, pointee) = types[args[0]][0], types[args[0]][1][:2]
        count = 1
        t_op, t_args = types[pointee]
        if t_op in ("OpTypeArray", "OpTypeRuntimeArray"):
            if t_op == "OpTypeArray":
                count = int(types[t_args[1]][1][0])
            t_op, t_args = types[t_args[0]]
        if t_op == "OpTypeSampledImage":
            kind = vk.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
        elif t_op == "OpTypeImage":
            sampled = int(t_args[5])
            if t_args[1] == "Buffer":
                kind = (vk.VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER if sampled == 2
                        else vk.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER)
            else:
                kind = vk.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE if sampled == 2 else vk.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE
        elif t_op == "OpTypeSampler":
            kind = vk.VK_DESCRIPTOR_TYPE_SAMPLER
        elif ptr_class == "StorageBuffer":
            kind = vk.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
        else:
            kind = vk.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
        out[(d.get("DescriptorSet", 0), d["Binding"])] = (kind, count)
    return out


class Device:
    def __init__(self):
        app = vk.VkApplicationInfo(sType=vk.VK_STRUCTURE_TYPE_APPLICATION_INFO, apiVersion=vk.VK_MAKE_VERSION(1, 3, 0))
        self.inst = vk.vkCreateInstance(vk.VkInstanceCreateInfo(sType=vk.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
                                                                pApplicationInfo=app), None)
        self.phys = vk.vkEnumeratePhysicalDevices(self.inst)[0]
        self.name = vk.vkGetPhysicalDeviceProperties(self.phys).deviceName
        exec_feat = vk.VkPhysicalDevicePipelineExecutablePropertiesFeaturesKHR(
            sType=vk.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_EXECUTABLE_PROPERTIES_FEATURES_KHR,
            pipelineExecutableInfo=vk.VK_TRUE)
        feats = vk.vkGetPhysicalDeviceFeatures(self.phys)
        queue = vk.VkDeviceQueueCreateInfo(sType=vk.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO, queueFamilyIndex=0,
                                           queueCount=1, pQueuePriorities=[1.0])
        self.dev = vk.vkCreateDevice(self.phys, vk.VkDeviceCreateInfo(
            sType=vk.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO, pNext=exec_feat, queueCreateInfoCount=1,
            pQueueCreateInfos=[queue], enabledExtensionCount=1,
            ppEnabledExtensionNames=["VK_KHR_pipeline_executable_properties"], pEnabledFeatures=feats), None)
        self.props = vk.vkGetDeviceProcAddr(self.dev, "vkGetPipelineExecutablePropertiesKHR")
        self.stats = vk.vkGetDeviceProcAddr(self.dev, "vkGetPipelineExecutableStatisticsKHR")

    def compute_stats(self, spv):
        code = spv.read_bytes()
        module = vk.vkCreateShaderModule(self.dev, vk.VkShaderModuleCreateInfo(
            sType=vk.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO, codeSize=len(code), pCode=code), None)
        sets = {}
        for (s, b), (kind, count) in bindings(spv).items():
            sets.setdefault(s, []).append(vk.VkDescriptorSetLayoutBinding(
                binding=b, descriptorType=kind, descriptorCount=count, stageFlags=vk.VK_SHADER_STAGE_COMPUTE_BIT))
        layouts = []
        for s in range(max(sets) + 1 if sets else 0):
            layouts.append(vk.vkCreateDescriptorSetLayout(self.dev, vk.VkDescriptorSetLayoutCreateInfo(
                sType=vk.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO, bindingCount=len(sets.get(s, [])),
                pBindings=sets.get(s, [])), None))
        layout = vk.vkCreatePipelineLayout(self.dev, vk.VkPipelineLayoutCreateInfo(
            sType=vk.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO, setLayoutCount=len(layouts),
            pSetLayouts=layouts), None)
        stage = vk.VkPipelineShaderStageCreateInfo(sType=vk.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                                                   stage=vk.VK_SHADER_STAGE_COMPUTE_BIT, module=module, pName="main")
        pipe = vk.vkCreateComputePipelines(self.dev, None, 1, [vk.VkComputePipelineCreateInfo(
            sType=vk.VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
            flags=vk.VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR, stage=stage, layout=layout)], None)[0]
        info = vk.VkPipelineExecutableInfoKHR(sType=vk.VK_STRUCTURE_TYPE_PIPELINE_EXECUTABLE_INFO_KHR, pipeline=pipe,
                                              executableIndex=0)
        out = {}
        for st in self.stats(self.dev, info):
            v = st.value
            val = {vk.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_BOOL32_KHR: lambda: v.b32,
                   vk.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_INT64_KHR: lambda: v.i64,
                   vk.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_UINT64_KHR: lambda: v.u64,
                   vk.VK_PIPELINE_EXECUTABLE_STATISTIC_FORMAT_FLOAT64_KHR: lambda: v.f64}[st.format]()
            name = st.name if isinstance(st.name, str) else vk.ffi.string(st.name).decode()
            out[name] = val
        vk.vkDestroyPipeline(self.dev, pipe, None)
        vk.vkDestroyPipelineLayout(self.dev, layout, None)
        for lay in layouts:
            vk.vkDestroyDescriptorSetLayout(self.dev, lay, None)
        vk.vkDestroyShaderModule(self.dev, module, None)
        return out


SHOW = ["VGPRs", "SGPRs", "Spilled VGPRs", "Spilled SGPRs", "Scratch", "Instructions", "Code size", "Latency",
        "Inverse Throughput", "VMEM Clause", "Subgroups per SIMD"]


def main():
    wanted = sys.argv[1:]
    progs = sorted(ROOT.glob("*.csh")) + sorted((ROOT / "world-1").glob("*.csh")) + sorted((ROOT / "world1").glob("*.csh"))
    if wanted:
        progs = [p for p in progs if any(w in str(p.relative_to(ROOT)) for w in wanted)]
    dev = Device()
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="gpu_stats_"))
    print(f"# {dev.name}; ROOT={ROOT}")
    print("program".ljust(26) + "".join(k[:11].rjust(12) for k in SHOW))
    for p in progs:
        rel = str(p.relative_to(ROOT))
        try:
            st = dev.compute_stats(spirv(p, tmp))
        except Exception as e:  # noqa: BLE001 (report and go on)
            print(rel.ljust(26) + "  failed: " + str(e).strip().splitlines()[-1][:150])
            continue
        print(rel.ljust(26) + "".join(str(st.get(k, "-")).rjust(12) for k in SHOW))


if __name__ == "__main__":
    main()
