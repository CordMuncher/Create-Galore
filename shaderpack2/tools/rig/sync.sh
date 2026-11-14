#!/usr/bin/env bash
# Copies the pack into the slim client (Iris doesn't follow a linked folder): sync.sh [NAME] [SHADERS_DIR].
# Options for the pack go in SLIM/shaderpacks/NAME.txt (OPTION=VALUE lines): sync.sh keeps the ones given in OPTS
# ("A=1 B=2"), or clears the file when OPTS is set but empty.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SLIM="${SLIM:-/home/user/cg-slim}"
NAME="${1:-Vector}"
SRC="${2:-$HERE/../../shaders}"
rm -rf "$SLIM/shaderpacks/$NAME"
mkdir -p "$SLIM/shaderpacks/$NAME"
cp -r "$SRC" "$SLIM/shaderpacks/$NAME/shaders"
if [ -n "${OPTS+x}" ]; then
  : > "$SLIM/shaderpacks/$NAME.txt"
  for o in $OPTS; do echo "$o" >> "$SLIM/shaderpacks/$NAME.txt"; done
fi
