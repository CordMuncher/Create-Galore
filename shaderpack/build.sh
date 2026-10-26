#!/usr/bin/env bash
# Zips the shader pack for Iris into the folder given, default shaderpack/dist/.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mkdir -p "${1:-$HERE/dist}" && cd "${1:-$HERE/dist}" && pwd)"
ZIP="$OUT/Create-Galore-Shaders.zip"
rm -f "$ZIP"
(cd "$HERE" && zip -qr -X "$ZIP" shaders LICENSE README.md THIRD-PARTY-NOTICES.md)
echo "built $ZIP"
