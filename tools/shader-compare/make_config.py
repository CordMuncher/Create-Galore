#!/usr/bin/env python3
"""Writes create_galore_shader_compare.json: the shader packs to compare (each pinned to one Modrinth file, with the
options that turn its resource pack materials on and its highest quality preset worked out from its own profiles),
and the scenes to photograph (vanilla blocks only).

Usage: make_config.py CACHE_DIR [OUT.json]   (CACHE_DIR keeps the downloaded packs; each is checked against its hash)

The capture mod (src/) reads the JSON from the test instance's config folder; PC-RUNBOOK.md downloads the packs it
lists. Pass 1 ("defaults") is each pack as it ships plus its materials switches; pass 2 ("highest") is its highest
preset plus the same switches. A pack with no quality presets has no pass 2.
"""
import hashlib, json, pathlib, re, sys, urllib.request, zipfile

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parent.parent

# Five packs that span the range, picked by the owner (2026-10-27): Complementary (Reimagined, the one the modpack
# ships; Unbound is the same pack underneath), BSL, Bliss, Photon and Rethinking Voxels; and ours. Each pinned to its
# newest file for Minecraft 1.21.1 and Iris. compare-1 (results/) had thirteen.
# pbr: the options that make the pack read a LabPBR resource pack's maps (SPBR's), with parallax on where the pack
# has its own switch for it. None where the pack can't read them.
PACKS = [
    {"name": "ours", "title": "Create Galore Shaders", "file": "Create-Galore-Shaders", "source": "repo shaderpack/",
     "pbr": {}, "note": "LabPBR maps and relief are on by default"},
    {"name": "complementary-reimagined", "title": "Complementary Reimagined", "version": "r5.9.3",
     "url": "https://cdn.modrinth.com/data/HVnmMxH1/versions/Bqen1mJX/ComplementaryReimagined_r5.9.3.zip",
     "sha512": "45304b1d7862afdb2177b7e6226fc9c6737911d17a4ad46cc9c519ca4deb935f05072cd337cd8b19a15bb8062b57b54eceec7bdee4cd572d94858e2946d37d85",
     "pbr": {"RP_MODE": "3"}},
    {"name": "bsl", "title": "BSL", "version": "10.1.8",
     "url": "https://cdn.modrinth.com/data/Q1vvjJYV/versions/Y68yiql9/BSL_v10.1.8.zip",
     "sha512": "94a3c25d6820868db624e11347873e6b572e7cd571967d9ac6f4690ca5d37ca302a7645d85eec800e0654541e3825fba14d37b5a25d3996aaf038178552be26f",
     "pbr": {"ADVANCED_MATERIALS": "true", "MATERIAL_FORMAT": "1", "PARALLAX": "true"}},
    {"name": "photon", "title": "Photon", "version": "v1.4",
     "url": "https://cdn.modrinth.com/data/lLqFfGNs/versions/BnGklH5y/photon_v1.4.zip",
     "sha512": "2dec87084dccf91a72a06235ecbfb3627f066699b5f70583f06077cba61433aedbfa6af28eb68bf1cdc726149cc3d9a587517cfab59e6a24e9d6a40b6874d655",
     "pbr": {"NORMAL_MAPPING": "true", "SPECULAR_MAPPING": "true", "TEXTURE_FORMAT": "TEXTURE_FORMAT_LAB", "POM": "true"},
     "note": "the newest Photon, not the v1.3a the pack's DLAA copy is made from"},
    {"name": "bliss", "title": "Bliss", "version": "2.1.2",
     "url": "https://cdn.modrinth.com/data/ZvMtQlho/versions/kC2Y8q1P/Bliss_v2.1.2_%28Chocapic13_Shaders_edit%29.zip",
     "sha512": "dafc60be4980ec40f40edc0f2625cb0976f3c9ce5ed86383146a120480826bb1de70ef5e38b7f1437294ed4d38c6ef3c82ebef0ae4e00b8cee165788c9c18280",
     "pbr": {"Specular_Reflections": "true", "POM": "true", "MATERIAL_AO": "true"},
     "presets": False, "note": "its profiles pick a tone curve, not a quality level"},
    {"name": "rethinking-voxels", "title": "Rethinking Voxels", "version": "r0.1-beta9",
     "url": "https://cdn.modrinth.com/data/kmwfVOoi/versions/cpD4esk9/rethinking-voxels_r0.1-beta9.zip",
     "sha512": "1e32f41e67e527c3c60149677a05b61c6d305e440812e29abba745b8ed304954780ca48d58b834c49835790bc3c4d38f37c893c5cbdfea9edc07b72e1dabd296",
     "pbr": {"RP_MODE": "3"}},
]

# SPBR 16.2, the resource pack every pack is shown with.
SPBR = {"file": "SPBR-16_2.zip", "url": "https://cdn.modrinth.com/data/aNcOVoD7/versions/jtNbhldU/SPBR-16_2.zip",
        "sha512": "c96d7026679ac6843b460267bf3e7300d7e0e48bdff438979579fab323fc9cff40f9912cbea6091d713c3bdc756db500665d9d8cfe892215c304933dd89ae44b"}


def tp(dim, x, y, z, yaw, pitch):
    return {"dimension": dim, "x": x, "y": y, "z": z, "yaw": yaw, "pitch": pitch}


# Scenes in the DLAA test world (tools/dlss-testkit/world/dlaa-test.zip, seed 20261024; the lake shore near 1029 64 162),
# vanilla blocks only. build: commands that put the scene up (run once at the start, with its chunks kept loaded, and
# again on each visit; they only place blocks, so running them again changes nothing). find: a command that puts the
# player somewhere safe on the first visit; that spot is kept for the later visits. Ordered morning to night, then
# the End and the Nether, then rain (the packs' wet look lingers, so it goes last).
O, E, N = "minecraft:overworld", "minecraft:the_end", "minecraft:the_nether"
SCENES = [
    {"name": "landscape-noon", **tp(O, 1029.5, 74, 166.5, 180, 12), "time": 6000},
    {"name": "trees-morning", **tp(O, 1024.5, 88, 205.5, 180, 22), "time": 3000},
    {"name": "trees-sunrise", **tp(O, 1024.5, 216, 205.5, -90, -9), "time": 800, "maxTurn": 0,
     "about": "towards the low sun through a grove of oaks on a grass platform high in the open: light in the air",
     "build": ["fill 1020 215 195 1050 215 217 minecraft:grass_block",
               "fill 1020 216 195 1050 230 217 minecraft:air",
               "fill 1030 216 202 1030 221 202 minecraft:oak_log", "fill 1028 220 200 1032 221 204 minecraft:oak_leaves[persistent=true]",
               "fill 1029 222 201 1031 222 203 minecraft:oak_leaves[persistent=true]",
               "fill 1034 216 209 1034 222 209 minecraft:oak_log", "fill 1032 221 207 1036 222 211 minecraft:oak_leaves[persistent=true]",
               "fill 1033 223 208 1035 223 210 minecraft:oak_leaves[persistent=true]",
               "fill 1039 216 203 1039 220 203 minecraft:oak_log", "fill 1037 219 201 1041 220 205 minecraft:oak_leaves[persistent=true]",
               "fill 1038 221 202 1040 221 204 minecraft:oak_leaves[persistent=true]",
               "fill 1043 216 208 1043 221 208 minecraft:oak_log", "fill 1041 220 206 1045 221 210 minecraft:oak_leaves[persistent=true]",
               "fill 1042 222 207 1044 222 209 minecraft:oak_leaves[persistent=true]",
               "fill 1046 216 202 1046 220 202 minecraft:oak_log", "fill 1044 219 200 1048 220 204 minecraft:oak_leaves[persistent=true]",
               "setblock 1027 216 207 minecraft:short_grass", "setblock 1031 216 206 minecraft:fern",
               "setblock 1036 216 205 minecraft:short_grass", "setblock 1041 216 211 minecraft:poppy"]},
    {"name": "distant-terrain", **tp(O, 1029.5, 190, 160.5, 45, 8), "time": 11500,
     "about": "high over the land to the south-west, the late sun at the edge of the picture: the haze over far terrain"},
    {"name": "distant-water", **tp(O, 1029.5, 90, 168.5, 180, 10), "time": 4000,
     "about": "over the lake from high above its shore"},
    {"name": "close-water", **tp(O, 1160.5, 158, 146.5, 180, 38), "maxTurn": 45, "time": 6000,
     "about": "a shallow pond two blocks deep on sand in the open, a quartz pillar in it",
     "build": ["fill 1149 148 119 1171 148 141 minecraft:stone_bricks",
               "fill 1149 149 119 1171 152 119 minecraft:stone_bricks", "fill 1149 149 141 1171 152 141 minecraft:stone_bricks",
               "fill 1149 149 119 1149 152 141 minecraft:stone_bricks", "fill 1171 149 119 1171 152 141 minecraft:stone_bricks",
               "fill 1150 149 120 1170 149 140 minecraft:sand", "fill 1150 150 120 1170 151 140 minecraft:water",
               "fill 1159 149 127 1161 158 129 minecraft:quartz_pillar", "setblock 1160 159 128 minecraft:lantern",
               "fill 1153 150 123 1154 152 124 minecraft:mossy_cobblestone"]},
    {"name": "water-low", **tp(O, 1160.5, 152.7, 139.5, 180, 4), "maxTurn": 45, "time": 6000,
     "about": "the same pond from just above its surface: reflections", "build": "close-water"},
    {"name": "underwater", **tp(O, 1135.5, 129, 138.5, 90, 8), "maxTurn": 0, "time": 6000,
     "about": "inside a glass tank of water in the open, seagrass on sand, half of it under planks",
     "build": ["fill 1120 120 129 1138 139 147 minecraft:glass hollow",
               "fill 1121 121 130 1137 138 146 minecraft:water", "fill 1121 121 130 1137 121 146 minecraft:sand",
               "fill 1120 139 129 1128 139 147 minecraft:oak_planks",
               "setblock 1131 122 136 minecraft:seagrass", "setblock 1132 122 137 minecraft:seagrass",
               "setblock 1130 122 140 minecraft:tall_seagrass[half=lower]", "setblock 1130 123 140 minecraft:tall_seagrass[half=upper]",
               "setblock 1125 122 134 minecraft:seagrass", "setblock 1124 122 141 minecraft:seagrass",
               "setblock 1126 122 138 minecraft:tall_seagrass[half=lower]", "setblock 1126 123 138 minecraft:tall_seagrass[half=upper]",
               "setblock 1134 122 143 minecraft:sea_pickle[pickles=4]", "setblock 1122 122 132 minecraft:sea_pickle[pickles=3]"]},
    {"name": "materials", **tp(O, 1248.5, 177.2, 112, 180, 18), "time": 2300, "maxTurn": 40,
     "about": "SPBR on common blocks in the low morning sun: stone bricks, cobblestone and bricks walls, metal blocks",
     "build": ["fill 1240 175 100 1262 175 124 minecraft:stone_bricks",
               "fill 1240 175 112 1244 175 124 minecraft:cobblestone", "fill 1245 175 112 1249 175 124 minecraft:oak_planks",
               "fill 1250 175 112 1253 175 124 minecraft:polished_andesite", "fill 1254 175 112 1257 175 124 minecraft:sand",
               "fill 1258 175 112 1262 175 124 minecraft:grass_block",
               "fill 1240 176 104 1246 181 104 minecraft:stone_bricks", "fill 1247 176 104 1253 181 104 minecraft:cobblestone",
               "fill 1254 176 104 1262 181 104 minecraft:bricks",
               "setblock 1243 176 107 minecraft:iron_block", "setblock 1245 176 107 minecraft:gold_block",
               "setblock 1247 176 107 minecraft:copper_block", "setblock 1249 176 107 minecraft:quartz_block",
               "setblock 1251 176 107 minecraft:glowstone", "setblock 1253 176 107 minecraft:diamond_block",
               "setblock 1250 179 105 minecraft:wall_torch[facing=south]", "setblock 1256 179 105 minecraft:lantern[hanging=false]",
               "setblock 1256 178 105 minecraft:oak_fence"]},
    {"name": "materials-close", **tp(O, 1243.5, 177.6, 106.8, 145, 12), "maxTurn": 30, "time": 2300,
     "about": "along the stone brick wall with the sun grazing it: relief and its shadows", "build": "materials"},
    {"name": "window-light", **tp(O, 1222.5, 182, 221.5, -55, -8), "maxTurn": 0, "time": 1500,
     "about": "a dark stone room with a window towards the morning sun: the beam through the air",
     "build": ["fill 1220 180 220 1232 188 232 minecraft:stone hollow", "fill 1232 184 225 1232 186 227 minecraft:air"]},
    {"name": "bounce-light", **tp(O, 1084.5, 112, 160.5, 90, 24), "time": 1800, "maxTurn": 0,
     "about": "a white room with a wide window towards the morning sun behind the camera, its beam across a red wool carpet: light bouncing off the carpet and walls",
     "build": ["fill 1074 110 154 1086 117 166 minecraft:white_concrete hollow",
               "fill 1086 112 158 1086 115 162 minecraft:air", "fill 1077 110 157 1083 110 163 minecraft:red_wool"]},
    {"name": "lights-in-shade", **tp(O, 1046.5, 151, 221.5, 0, 8), "maxTurn": 40, "time": 6000,
     "about": "lanterns, a torch, a sea lantern and a redstone torch under a stone roof by day",
     "build": ["fill 1040 150 220 1052 150 232 minecraft:stone_bricks", "fill 1040 155 220 1052 155 232 minecraft:stone_bricks",
               "fill 1040 151 220 1040 154 220 minecraft:stone_brick_wall", "fill 1052 151 220 1052 154 220 minecraft:stone_brick_wall",
               "fill 1040 151 232 1040 154 232 minecraft:stone_brick_wall", "fill 1052 151 232 1052 154 232 minecraft:stone_brick_wall",
               "setblock 1043 154 224 minecraft:lantern[hanging=true]", "setblock 1049 154 224 minecraft:soul_lantern[hanging=true]",
               "setblock 1046 154 229 minecraft:lantern[hanging=true]", "setblock 1046 151 226 minecraft:torch",
               "setblock 1043 151 230 minecraft:sea_lantern", "setblock 1049 151 230 minecraft:redstone_torch"]},
    {"name": "cave", **tp(O, 1101.5, 24, 197.5, -45, 4), "maxTurn": 0, "time": 6000,
     "about": "a chamber cut into the rock deep underground, lit by torches and a lava pool",
     "build": ["fill 1100 22 196 1114 30 210 minecraft:air", "fill 1100 21 196 1114 21 210 minecraft:stone replace minecraft:air",
               "fill 1109 21 204 1112 21 207 minecraft:lava",
               "setblock 1104 22 200 minecraft:torch", "setblock 1112 22 199 minecraft:torch",
               "setblock 1102 22 208 minecraft:torch"]},
    {"name": "single-light", **tp(O, 1101.5, 142, 241.5, -45, -10), "maxTurn": 0, "time": 18000,
     "about": "a closed stone brick room at night with one lantern on the floor",
     "build": ["fill 1100 140 240 1110 146 250 minecraft:stone_bricks hollow", "setblock 1105 141 245 minecraft:lantern"]},
    {"name": "coloured-lights", **tp(O, 1060.5, 119, 156.5, 0, 14), "time": 18000, "maxTurn": 0,
     "about": "a closed stone room at night, six lights in a row along the far wall, all in the picture: soul lantern, redstone torch, sea lantern, torch, amethyst, glowstone",
     "build": ["fill 1054 116 154 1066 124 166 minecraft:stone hollow",
               "setblock 1055 117 164 minecraft:soul_lantern", "setblock 1057 117 164 minecraft:redstone_torch",
               "setblock 1059 117 164 minecraft:sea_lantern", "setblock 1061 117 164 minecraft:torch",
               "setblock 1063 117 164 minecraft:amethyst_cluster", "setblock 1065 117 164 minecraft:glowstone"]},
    {"name": "sunset", **tp(O, 1029.5, 70, 160.5, 90, -12), "time": 12700},
    {"name": "night-land", **tp(O, 1029.5, 74, 166.5, 180, 12), "time": 18000, "settleSeconds": 14},
    {"name": "night-sky", **tp(O, 1029.5, 70, 160.5, 0, -35), "time": 18000},
    {"name": "end", **tp(E, 0, 85, 60, 180, -10), "settleSeconds": 14},
    {"name": "nether", **tp(N, 0, 0, 0, 0, 8), "settleSeconds": 14,
     "find": "spreadplayers 0 0 0 64 under 90 false @a",
     "about": "somewhere safe in the Nether near 0 0, found on the first visit and kept"},
    {"name": "rain", **tp(O, 1029.5, 66, 160.5, 0, 5), "time": 6000, "weather": "rain", "weatherLeadSeconds": 180,
     "settleSeconds": 20, "about": "rain that has been falling for three minutes: wet ground, puddles and ripples"},
]


def sha512(path):
    h = hashlib.sha512()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def properties_lines(text):
    out, cur = [], ""
    for raw in text.replace("\r", "").split("\n"):
        line = raw.rstrip()
        if line.endswith("\\"):
            cur += line[:-1] + " "
            continue
        out.append(cur + line)
        cur = ""
    if cur:
        out.append(cur)
    return out


def read_profiles(text):
    profiles, order = {}, []
    for line in properties_lines(text):
        m = re.match(r"^\s*profile\.([A-Za-z0-9_]+)\s*=\s*(.*)$", line)
        if m:
            if m.group(1) in profiles:
                raise SystemExit("profile %s defined twice" % m.group(1))
            profiles[m.group(1)] = m.group(2).split()
            order.append(m.group(1))
    return profiles, order


# As Iris applies a profile: OPT=value or OPT:value sets a value, OPT turns a switch on, !OPT off, profile.OTHER first.
def resolve(profiles, name, seen=()):
    if name in seen:
        raise SystemExit("profile loop at " + name)
    values = {}
    for token in profiles[name]:
        if token.startswith("profile."):
            values.update(resolve(profiles, token[len("profile."):], seen + (name,)))
        elif token.startswith("!"):
            values[token[1:]] = "false"
        else:
            m = re.match(r"^([A-Za-z0-9_]+)[=:](.*)$", token)
            values[m.group(1) if m else token] = m.group(2) if m else "true"
    return values


def main():
    cache = pathlib.Path(sys.argv[1])
    out = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "create_galore_shader_compare.json"
    cache.mkdir(parents=True, exist_ok=True)
    packs = []
    for p in PACKS:
        entry = {k: v for k, v in p.items() if k != "presets"}
        if "url" in p:
            entry["file"] = urllib.request.unquote(p["url"].rsplit("/", 1)[1])
            zip_path = cache / entry["file"]
            if not zip_path.exists() or sha512(zip_path) != p["sha512"]:
                urllib.request.urlretrieve(p["url"], zip_path)
            if sha512(zip_path) != p["sha512"]:
                raise SystemExit("%s: hash does not match" % entry["file"])
            text = zipfile.ZipFile(zip_path).read("shaders/shaders.properties").decode("utf-8", "replace")
        else:
            text = (REPO / "shaderpack/shaders/shaders.properties").read_text()
        profiles, order = read_profiles(text)
        if p.get("presets", True) and order:
            # Each pack lists its quality presets lowest first.
            entry["highestProfile"] = order[-1]
            entry["highest"] = resolve(profiles, order[-1])
        else:
            entry["highestProfile"] = None
            entry["highest"] = None
        packs.append(entry)
    scenes = []
    by_name = {s["name"]: s for s in SCENES}
    for s in SCENES:
        s = dict(s)
        if isinstance(s.get("build"), str):
            s["build"] = by_name[s["build"]]["build"]
        scenes.append(s)
    config = {
        "about": "Made by tools/shader-compare/make_config.py; read by the shader compare mod from config/.",
        "passes": ["defaults", "highest"],
        "prepareSeconds": 30,
        "reloadSeconds": 10,
        "loadSeconds": 3,
        "dimensionSeconds": 6,
        "settleSeconds": 10,
        "settleFrames": 90,
        "arrivalShots": [0.1, 0.5, 1.0, 2.0],
        "surveySeconds": 8,
        "resourcePack": SPBR,
        "packs": packs,
        "scenes": scenes,
    }
    out.write_text(json.dumps(config, indent=2) + "\n")
    print("wrote", out, len(packs), "packs,", len(scenes), "scenes")


if __name__ == "__main__":
    main()
