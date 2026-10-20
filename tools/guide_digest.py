#!/usr/bin/env python3
"""The Field Guide's compact notes for its voice (Ask the Book).

The voice starts every conversation with a compact, model-facing version of the Field Guide instead of the whole
human-facing book: the same facts, written once, without the repetition a reader who dips into one entry needs. The
notes live next to the book in the tweaks datapack, one file per section (field_guide_ai/<section>.md), and each
starts with a stamp of the book entries it was written from. When an entry changes and its section's notes aren't
updated, the server uses that section's full text instead (so nothing is ever lost), and this check fails in CI.

  guide_digest.py --check            every section has notes, stamped against the current entries (CI runs this)
  guide_digest.py --audit [section]  every item id, recipe id, entry link and number of each entry is in its notes
                                     (or in the notes of an entry it links to)
  guide_digest.py --stamp <section>  stamp a section's notes after rewriting them
  guide_digest.py --dump DIR         the book as the voice used to read it, one file per section (for writing notes)

The stamp hashes the entry files themselves (path and bytes), which the Bridge mod recomputes the same way
(GuideText.sourceHash); keep the two in step.
"""
import hashlib
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DATA = os.path.join(ROOT, "pack/config/paxi/datapacks/create-galore-tweaks/data/create_galore")
BOOK = os.path.join(DATA, "modonomicon/books/field_guide")
NOTES = os.path.join(DATA, "field_guide_ai")
STAMP = re.compile(r"^<!-- sources sha256:([0-9a-f]{64}) -->\n")

# Mirrors GuideText.clean(): book markup to plain text.
COLOR = re.compile(r"\[#]\([0-9A-Fa-f]*\)")
ENTRY_LINK = re.compile(r"\[([^\]]*)]\(entry://([^)]*)\)")
ITEM_LINK = re.compile(r"\[([^\]]*)]\(item://([^)]*)\)")
ICONS = re.compile("[-]")


def clean(t):
    t = t.replace("\\\n", "\n")
    t = ENTRY_LINK.sub(lambda m: m.group(1) + " (see " + m.group(2) + ")", t)
    t = ITEM_LINK.sub(lambda m: m.group(2) if not m.group(1) else m.group(1) + " [" + m.group(2) + "]", t)
    t = ICONS.sub("", COLOR.sub("", t)).replace("%%", "%").replace("\\_", "_")
    return re.sub(r"\n{3,}", "\n\n", re.sub(r"[ \t]+\n", "\n", t)).strip()


def item_of(page):
    e = page.get("item")
    if isinstance(e, str):
        return e
    if isinstance(e, dict):
        return e.get("item") or e.get("id") or ("#" + e["tag"] if "tag" in e else "")
    if isinstance(e, list) and e:
        return item_of({"item": e[0]})
    return ""


def entry_files(section):
    base = os.path.join(BOOK, "entries", section)
    out = []
    for d, _, files in os.walk(base):
        for f in files:
            if f.endswith(".json"):
                out.append(os.path.relpath(os.path.join(d, f), os.path.join(BOOK, "entries")).replace(os.sep, "/"))
    return sorted(out)


def sections():
    return sorted(d for d in os.listdir(os.path.join(BOOK, "entries")) if os.path.isdir(os.path.join(BOOK, "entries", d)))


def source_hash(section):
    h = hashlib.sha256()
    for rel in entry_files(section):
        h.update(rel.encode() + b"\n")
        with open(os.path.join(BOOK, "entries", rel), "rb") as f:
            h.update(f.read())
        h.update(b"\n")
    return h.hexdigest()


def entries(section):
    """The section's entries as (id, name, text) in book order, text as GuideText.guide() writes it."""
    out = []
    for rel in entry_files(section):
        with open(os.path.join(BOOK, "entries", rel), encoding="utf-8") as f:
            j = json.load(f)
        if "pages" not in j:
            continue
        lines = []
        for p in j["pages"]:
            if not isinstance(p, dict):
                continue
            title = p.get("title", "")
            if title and title != j.get("name", ""):
                lines.append(title)
            if item_of(p):
                lines.append("[Shows item " + item_of(p) + "]")
            if "recipe_id_1" in p:
                lines.append("[Shows recipe " + p["recipe_id_1"] + (" and " + p["recipe_id_2"] if "recipe_id_2" in p else "") + "]")
            if "multiblock_name" in p:
                lines.append("[Shows the build: " + p["multiblock_name"] + "]")
            if clean(p.get("text", "")):
                lines.append(clean(p.get("text", "")))
        out.append((j.get("sort_number", 0), rel[:-5], j.get("name", ""), "\n".join(lines)))
    out.sort(key=lambda e: (e[0], e[1]))
    return [(i, n, t) for _, i, n, t in out]


def section_name(section):
    with open(os.path.join(BOOK, "categories", section + ".json"), encoding="utf-8") as f:
        return json.load(f).get("name", section)


def notes_path(section):
    return os.path.join(NOTES, section + ".md")


def read_notes(section):
    try:
        with open(notes_path(section), encoding="utf-8") as f:
            return f.read()
    except FileNotFoundError:
        return None


ID = re.compile(r"#?[a-z0-9_.-]+:[a-z0-9_./-]+")
SEE = re.compile(r"\(see ([a-z0-9_]+/[a-z0-9_/]+)\)")
NUMBER = re.compile(r"(?<![\w.])-?\d+(?:[.,]\d+)?(?![\w])")


def facts(text):
    """The checkable bits of an entry: ids, links and numbers."""
    ids = {m.rstrip(".") for m in ID.findall(text)}
    links = set(SEE.findall(text))
    numbers = set(NUMBER.findall(text.replace(",", "")))
    return ids, links, numbers


def split_notes(notes):
    """Notes text to {entry id: block}, by the '## <entry id> |' headings."""
    out, cur = {}, None
    for line in notes.splitlines():
        m = re.match(r"^## ([a-z0-9_]+/[a-z0-9_/]+)\b", line)
        if m:
            cur = m.group(1)
            out[cur] = []
        if cur:
            out[cur].append(line)
    return {k: "\n".join(v) for k, v in out.items()}


def all_blocks():
    out = {}
    for s in sections():
        out.update(split_notes(read_notes(s) or ""))
    return out


def audit(section):
    """Ids and numbers count as kept when they are in the entry's block, or in the block of an entry it points to
    with (see x/y): a hub entry may hand a fact to the topic entry that holds it in full."""
    notes = read_notes(section)
    if notes is None:
        return [f"{section}: no notes"]
    blocks = split_notes(notes)
    everywhere = all_blocks()
    problems = []
    for eid, name, text in entries(section):
        block = blocks.get(eid)
        if block is None:
            problems.append(f"{eid}: no '## {eid}' block")
            continue
        reach = [block] + [everywhere[l] for l in SEE.findall(block) if l in everywhere and l != eid]
        has_number = lambda n: any(re.search(r"(?<![\w.])" + re.escape(n) + r"(?![\w])", b.replace(",", "")) for b in reach)
        ids, links, numbers = facts(text)
        for i in sorted(ids):
            if not any(i in b for b in reach):
                problems.append(f"{eid}: id {i} missing")
        for l in sorted(links):
            if l not in block:
                problems.append(f"{eid}: link to {l} missing")
        for n in sorted(numbers):
            if not has_number(n):
                problems.append(f"{eid}: number {n} missing")
    extra = set(blocks) - {e[0] for e in entries(section)}
    problems += [f"{e}: notes for an entry that doesn't exist" for e in sorted(extra)]
    return problems


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    cmd = argv[0]
    if cmd == "--check":
        bad = []
        for s in sections():
            notes = read_notes(s)
            m = STAMP.match(notes or "")
            if not m:
                bad.append(f"{s}: {notes_path(s)} is missing or unstamped")
            elif m.group(1) != source_hash(s):
                bad.append(f"{s}: entries changed since the notes were written; update {os.path.relpath(notes_path(s), ROOT)}"
                           f" (the server sends this section in full until then), then run tools/guide_digest.py --stamp {s}")
        for b in bad:
            print(b)
        print("OK" if not bad else f"{len(bad)} section(s) need their AI notes updated")
        return 1 if bad else 0
    if cmd == "--audit":
        todo = argv[1:] or sections()
        total = 0
        for s in todo:
            for p in audit(s):
                print(p)
                total += 1
        print("OK" if not total else f"{total} problem(s)")
        return 1 if total else 0
    if cmd == "--stamp":
        for s in argv[1:]:
            notes = read_notes(s)
            if notes is None:
                print(f"{s}: no notes to stamp")
                return 1
            notes = STAMP.sub("", notes, count=1)
            with open(notes_path(s), "w", encoding="utf-8", newline="\n") as f:
                f.write(f"<!-- sources sha256:{source_hash(s)} -->\n" + notes)
        return 0
    if cmd == "--dump":
        out = argv[1]
        os.makedirs(out, exist_ok=True)
        for s in sections():
            with open(os.path.join(out, s + ".txt"), "w", encoding="utf-8") as f:
                f.write(f"=== Section: {section_name(s)} ({s}) ===\n")
                for eid, name, text in entries(s):
                    f.write(f"\n--- {name} (entry {eid}) ---\n{text}\n")
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
