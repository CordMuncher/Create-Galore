"""Print the CHANGELOG.md section for a pack version (the text under "## <version>").

Exits 1 with an error when the section is missing or empty, so a release can't go out without notes.
Usage: python3 tools/changelog_section.py <version> [CHANGELOG.md]
"""
import re
import sys

version = sys.argv[1]
path = sys.argv[2] if len(sys.argv) > 2 else "CHANGELOG.md"
try:
    text = open(path, encoding="utf-8").read()
except FileNotFoundError:
    sys.exit(f"::error::{path} is missing; add a '## {version}' section with Added / Fixed / Removed")
match = re.search(rf"^## {re.escape(version)}[ \t]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
body = match.group(1).strip() if match else ""
if not body:
    sys.exit(f"::error::{path} has no '## {version}' section; say what this version added, fixed or removed")
print(body)
