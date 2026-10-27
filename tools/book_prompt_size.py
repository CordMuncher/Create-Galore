#!/usr/bin/env python3
"""How much text Ask the Book sends with every question, against a fixed budget.

The book's instructions (GuideText.PERSONA) and its tool descriptions go with every question a player asks. Patching
each wrong answer with one more sentence there made them grow about sixfold in a week without the book
getting better. This check keeps them inside a budget, so a fix goes into a check, the player snapshot or a design
brief instead (see tools/book-casebook/README.md).

  book_prompt_size.py           print the sizes
  book_prompt_size.py --check   fail when either is over its budget (CI runs this)

Persona = the PERSONA text block. Tools = every string literal inside the tool(...) and prop(...) calls and the
addProperty("description", ...) lines that build the tool definitions, as the API receives them. Lower a budget when
the text shrinks; raising one needs a reason in the PR.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "tools/bridge-mod/src/main/java/dev/creategalore/bridge/guideai")
BUDGET = {"persona": 15500, "tools": 20100}


def literals(src):
    """(start, end, text) of every Java string literal and text block, comments skipped."""
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            body = re.sub(r"\\\n\s*", "", src[i + 3:j])
            out.append((i, j + 3, re.sub(r"\n[ \t]+", "\n", body).strip("\n")))
            i = j + 3
        elif src[i] == '"':
            j = i + 1
            while src[j] != '"':
                j += 2 if src[j] == "\\" else 1
            out.append((i, j + 1, src[i + 1:j]))
            i = j + 1
        elif src[i] == "'":
            i = src.find("'", i + 3 if src[i + 1] == "\\" else i + 2) + 1
        else:
            i += 1
    return out


def call_text(src, names):
    lits = literals(src)
    starts = {s: e for s, e, _ in lits}
    total = 0
    for m in re.finditer(r"\b(?:%s)\(|addProperty\(\"description\",|put\(\"description\"," % "|".join(names), src):
        i, depth = m.end(), 1
        while depth:
            if i in starts:
                i = starts[i]
                continue
            depth += {"(": 1, ")": -1}.get(src[i], 0)
            i += 1
        total += sum(len(t) for s, e, t in lits if s >= m.end() and e <= i)
    return total


def sizes():
    text = open(os.path.join(SRC, "GuideText.java"), encoding="utf-8").read()
    at = re.search(r"PERSONA\s*=\s*", text).end()
    persona = next(len(t) for s, e, t in literals(text) if s >= at)
    tools = sum(call_text(open(os.path.join(SRC, f), encoding="utf-8").read(), ["tool", "prop"])
                for f in sorted(os.listdir(SRC)) if f.endswith(".java"))
    return {"persona": persona, "tools": tools}


def main():
    got = sizes()
    over = False
    for k, v in got.items():
        print(f"{k:8s} {v:6d} characters (about {v // 4} tokens), budget {BUDGET[k]}")
        if v > BUDGET[k]:
            over = True
            print(f"  over budget by {v - BUDGET[k]}: put the fix in a check, the player snapshot or a design brief, "
                  f"not in the text sent with every question")
    if "--check" in sys.argv and over:
        sys.exit(1)


if __name__ == "__main__":
    main()
