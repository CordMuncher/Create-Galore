#!/usr/bin/env python3
"""Mechanical first step of porting Tide 2 (MPL-2.0) into Create Galore Fishing.

Tide's source is one multi-loader, multi-version tree managed by Stonecutter: code for other loaders and versions
sits in comments next to the active code. This script takes the chosen files, resolves every Stonecutter
condition for NeoForge 1.21.1 (keeping the code that applies, dropping the rest, no markers left), moves
`com.li64.tide` to `dev.creategalore.fishing.tide`, and puts the MPL notice on top of each file.

It writes to a staging folder, not into the mod: the files under src/main/java/dev/creategalore/fishing/tide/ were
taken from its output and then changed by hand (Tide's data layer replaced by ours, the rod and minigame removed,
platform calls pointed at our registries). To see what was changed by hand, run this again and diff:

    python3 tools/fishing-mod/port/port_tide.py <tide source checkout> <staging folder>
    diff -ru <staging folder>/dev/creategalore/fishing/tide tools/fishing-mod/src/main/java/dev/creategalore/fishing/tide

Tide 2.1.1: https://github.com/Lightning-64/Tide-2 (git 876b95f).
"""
import pathlib
import re
import sys

TARGET_LOADER = "neoforge"
TARGET_VERSION = (1, 21, 1)

NOTICE = """/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
"""

# Paths under com/li64/tide/ to port. Directories end with "/" and take everything below them.
INCLUDE = [
    "Tide.java",
    "client/FishDisplayRenderer.java", "client/TideClientHelper.java", "client/TideCoreShaders.java",
    "client/TideItemModelProperties.java", "client/TideRenderTypes.java", "client/VoidParticleSpawner.java",
    "client/gui/TideGuiOverlays.java", "client/gui/TideToasts.java", "client/gui/overlays/FishingInfoOverlay.java",
    "client/gui/screens/FishyNoteScreen.java", "client/gui/screens/journal/",
    "compat/CompatHelper.java", "compat/accessories/CuriosCompat.java", "compat/hybridaquatic/HybridAquaticCompat.java",
    "config/",
    "data/DoubleJumper.java", "data/FishLengthHolder.java", "data/FishyNoteRecipe.java", "data/FreezableMob.java",
    "data/ItemDataKey.java", "data/ModAssociatedEntry.java", "data/TideCriteriaTriggers.java",
    "data/TideLootTables.java", "data/TidePlayer.java", "data/TideRecipeSerializers.java", "data/TideTags.java",
    "data/ValidatableDataEntry.java",
    "data/commands/JournalCommand.java",
    "data/fishing/CrateData.java", "data/fishing/DisplayData.java", "data/fishing/FishSizeModel.java",
    "data/fishing/ProfileData.java", "data/fishing/SizeData.java",
    "data/informational/", "data/item/", "data/rods/BaitContents.java", "data/journal/", "data/loot/", "data/player/", "data/triggers/",
    "events/",
    "loaders/LoaderPlatform.java", "loaders/NetworkPlatform.java", "loaders/neoforge/",
    "mixin/AbstractFishMixin.java", "mixin/EntityRenderDispatcherMixin.java", "mixin/InventoryMixin.java", "mixin/ItemMixin.java",
    "mixin/LecternBlockMixin.java", "mixin/MobBucketItemMixin.java", "mixin/MobMixin.java",
    "mixin/PlayerMixin.java", "mixin/ProjectileWeaponItemMixin.java",
    "network/TideMessages.java", "network/messages/InfoDataMsg.java", "network/messages/OpenJournalMsg.java",
    "network/messages/ReadProfileMsg.java", "network/messages/ShowToastMsg.java",
    "network/messages/SyncPlayerDataMsg.java", "network/messages/TidePacketPayload.java",
    "network/messages/ViewNoteMsg.java",
    "registries/",
    "util/MoonPhases.java", "util/TideUtils.java", "util/TimeConstants.java",
]

# Not ported, though under an included directory: the rod, hook, minigame and Angling Table (replaced by ours),
# datagen-only code, and loader glue for other loaders.
EXCLUDE = [
    "registries/TideRegistries.java",
    "registries/blocks/AnglingTableBlock.java",
    "registries/entities/misc/fishing/HookAccessor.java",
    "registries/entities/misc/fishing/TideFishingBobberLayer.java",
    "registries/entities/misc/fishing/TideFishingHook.java",
    "registries/entities/misc/fishing/TideFishingHookModel.java",
    "registries/entities/misc/fishing/TideFishingHookRenderer.java",
    "registries/items/TideFishingRodItem.java",
    "registries/items/FishingLineItem.java",
    "registries/items/GoldenLineItem.java",
    "registries/items/FishingHookItem.java",
    "registries/items/FishingBobberItem.java",
    "registries/items/EnchantedFishingBobberItem.java",
]

VERSION = re.compile(r"(>=|<=|>|<|=)?\s*(\d+(?:\.\d+)+)")


def version_tuple(text):
    return tuple(int(p) for p in text.split("."))


def evaluate(expr):
    """A Stonecutter condition for NeoForge 1.21.1: loader names, version comparisons, !, &&, ||, ()."""
    def version(m):
        op, v = m.group(1) or "=", version_tuple(m.group(2))
        n = max(len(v), len(TARGET_VERSION))
        a = TARGET_VERSION + (0,) * (n - len(TARGET_VERSION))
        b = v + (0,) * (n - len(v))
        result = {">=": a >= b, "<=": a <= b, ">": a > b, "<": a < b, "=": a == b}[op]
        return " True " if result else " False "
    py = VERSION.sub(version, expr)
    for loader in ("neoforge", "fabric", "forge"):
        py = re.sub(rf"\b{loader}\b", " True " if loader == TARGET_LOADER else " False ", py)
    py = py.replace("&&", " and ").replace("||", " or ").replace("!", " not ")
    return bool(eval(py, {"__builtins__": {}}, {}))


TOKEN = re.compile(r"""
    (?P<lmark>//\?[^\n]*)                      # //? if x {   //?} else {   //?}
  | (?P<bmark>/\*\?.*?\*/)                     # /*? if x {*/  /*?} else*/  /*?}*/
  | (?P<block>/\*.*?\*/)                       # a block comment (may be Stonecutter-wrapped code)
  | (?P<line>//[^\n]*)                         # a line comment
  | (?P<str>"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])+')   # string or char literal
  | (?P<code>[^/"'\n]+|/|\n)                   # anything else, in pieces
""", re.S | re.X)


def tokenize(text):
    out = []
    for m in TOKEN.finditer(text):
        kind = m.lastgroup
        value = m.group(kind)
        if kind in ("str", "line", "code"):
            kind = "code"
        out.append([kind, value])
    return out


def marker_body(kind, value):
    return value[3:].strip() if kind == "lmark" else value[3:-2].strip()


def unwrap(text):
    """Code that Stonecutter commented out: drop the outer /* */ and restore nested comments."""
    inner = text.strip()
    assert inner.startswith("/*") and inner.endswith("*/"), inner[:40]
    return inner[2:-2].replace("/^", "/*").replace("^/", "*/")


def resolve(text):
    """Applies every Stonecutter condition in text. Recurses into code that was commented out."""
    tokens = tokenize(text)
    out, i = [], 0

    def branch_text(parts):
        joined = "".join(v for _, v in parts)
        stripped = joined.strip()
        meaningful = [p for p in parts if p[1].strip()]
        if len(meaningful) == 1 and meaningful[0][0] == "block":
            lead = joined[:len(joined) - len(joined.lstrip())]
            trail = joined[len(joined.rstrip()):]
            return lead + resolve(unwrap(stripped)) + trail
        return resolve(joined) if ("/*?" in joined or "//?" in joined) else joined

    def next_scope(j):
        """For a condition without braces: the next comment, or the rest of the line."""
        parts = []
        while j < len(tokens) and tokens[j][0] == "code" and tokens[j][1].strip() == "" and tokens[j][1] != "\n":
            parts.append(tokens[j]); j += 1
        if j < len(tokens) and tokens[j][0] == "code" and tokens[j][1] == "\n":
            # The scope starts on the next line (e.g. a lone "/*?} else*/" followed by the commented line).
            parts.append(tokens[j]); j += 1
            while j < len(tokens) and tokens[j][0] == "code" and tokens[j][1].strip() == "" and tokens[j][1] != "\n":
                parts.append(tokens[j]); j += 1
        if j < len(tokens) and tokens[j][0] == "block":
            parts.append(tokens[j]); return parts, j + 1
        while j < len(tokens) and not (tokens[j][0] == "code" and tokens[j][1] == "\n") and tokens[j][0] not in ("lmark", "bmark"):
            parts.append(tokens[j]); j += 1
        return parts, j

    while i < len(tokens):
        kind, value = tokens[i]
        if kind not in ("lmark", "bmark"):
            out.append(value); i += 1; continue
        body = marker_body(kind, value)
        if not body.startswith("if "):
            raise ValueError(f"unexpected marker {value!r}")
        # A conditional group: one or more branches, each with a condition (None = else).
        branches = []
        cond = body[3:].rstrip("{").strip()
        braced = body.endswith("{")
        i += 1
        while True:
            if not braced:
                parts, i = next_scope(i)
                branches.append((cond, parts))
                # A brace-less branch may be followed by a brace-less or braced else.
                j = i
                while j < len(tokens) and tokens[j][0] == "code" and tokens[j][1].strip() == "" and tokens[j][1] != "\n":
                    j += 1
                if j < len(tokens) and tokens[j][0] in ("lmark", "bmark") and marker_body(*tokens[j]).startswith("} else"):
                    i = j  # the spacing between the two branches belongs to neither
                    b = marker_body(*tokens[i])
                    rest = b[len("} else"):].strip()
                    i += 1
                    if rest.startswith("if "):
                        cond, braced = rest[3:].rstrip("{").strip(), rest.endswith("{")
                    else:
                        cond, braced = None, rest.endswith("{")
                    continue
                break
            parts = []
            depth = 0
            while i < len(tokens):
                k, v = tokens[i]
                if k in ("lmark", "bmark"):
                    b = marker_body(k, v)
                    if b.startswith("if ") and b.endswith("{"):
                        depth += 1
                    elif b.startswith("}") and depth > 0:
                        # "}" closes a nested group, and so does a brace-less "} else" (its scope is one item).
                        if not b.endswith("{"):
                            depth -= 1
                    elif b.startswith("}"):
                        break
                parts.append(tokens[i]); i += 1
            branches.append((cond, parts))
            b = marker_body(*tokens[i])
            i += 1
            if b == "}":
                break
            rest = b[len("} else"):].strip()
            if rest.startswith("if "):
                cond, braced = rest[3:].rstrip("{").strip(), rest.endswith("{")
            else:
                cond, braced = None, rest.endswith("{")
        chosen = None
        for c, parts in branches:
            if c is None or evaluate(c):
                chosen = parts
                break
        if chosen is not None:
            out.append(branch_text(chosen))
    text = "".join(out)
    # Lines left holding only whitespace by a removed marker.
    return re.sub(r"[ \t]+\n", "\n", text)


def drop_calls(text, name):
    """Removes every `.name(...)` call (balanced parentheses) from a builder chain."""
    out, i, needle = [], 0, "." + name + "("
    while True:
        j = text.find(needle, i)
        if j < 0:
            out.append(text[i:])
            return "".join(out)
        # Keep the newline and indentation before the call out; drop the call itself.
        k = j + len(needle)
        depth = 1
        while depth:
            c = text[k]
            depth += c == "("
            depth -= c == ")"
            k += 1
        start = j
        while start > 0 and text[start - 1] in " \t":
            start -= 1
        if start > 0 and text[start - 1] == "\n":
            start -= 1
        out.append(text[i:start])
        i = k


# Datagen-only code inside files that are otherwise ported: Tide builds its fish data JSON from these builder calls
# in a dev environment; the JSON itself is shipped (and read by our species loader).
DATAGEN_CALLS = {"registries/TideFish.java": ["fishData"]}


def port_file(src, rel, dest_root):
    text = src.read_text(encoding="utf-8")
    text = resolve(text)
    for call in DATAGEN_CALLS.get(rel, []):
        text = drop_calls(text, call)
    text = text.replace("com.li64.tide", "dev.creategalore.fishing.tide")
    # JetBrains' annotations aren't on our compile classpath; they only document nullness.
    text = re.sub(r"^import org\.jetbrains\.annotations\.[A-Za-z.*]+;\n", "", text, flags=re.M)
    text = re.sub(r"@(?:NotNull|Nullable|ApiStatus\.\w+|Contract\([^)]*\)|Unmodifiable)\s+", "", text)
    text = re.sub(r"\n{3,}", "\n\n", text).lstrip()
    if "/*?" in text or "//?" in text:
        raise ValueError(f"{rel}: Stonecutter markers left")
    if not text.strip():
        return None
    dest = dest_root / "dev/creategalore/fishing/tide" / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(NOTICE + text, encoding="utf-8")
    return dest


def selected(base):
    for path in sorted(base.rglob("*.java")):
        rel = path.relative_to(base).as_posix()
        if any(rel == e or (e.endswith("/") and rel.startswith(e)) for e in EXCLUDE):
            continue
        if any(rel == inc or (inc.endswith("/") and rel.startswith(inc)) for inc in INCLUDE):
            yield path, rel


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    base = pathlib.Path(sys.argv[1]) / "src/main/java/com/li64/tide"
    dest = pathlib.Path(sys.argv[2])
    count = empty = 0
    for path, rel in selected(base):
        if port_file(path, rel, dest) is None:
            empty += 1
        else:
            count += 1
    print(f"ported {count} files to {dest} ({empty} were empty for NeoForge 1.21.1)")


if __name__ == "__main__":
    main()
