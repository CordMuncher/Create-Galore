#!/usr/bin/env bash
# Zips the shader pack for Iris into the folder given, default shaderpack2/dist/. Each build's zip carries its version,
# so builds can sit side by side in shaderpacks/ (Iris names the pack, and its settings file, after the zip).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mkdir -p "${1:-$HERE/dist}" && cd "${1:-$HERE/dist}" && pwd)"
VERSION=alpha-0.6
ZIP="$OUT/vector-$VERSION.zip"
python3 "$HERE/programs.py" --check
rm -f "$ZIP"
(cd "$HERE" && zip -qr -X "$ZIP" shaders README.md)
echo "built $ZIP"
