#!/usr/bin/env bash
# Copies the pack into the slim client (Iris doesn't follow a linked folder): sync.sh [NAME] [SHADERS_DIR].
# Options for the pack go in SLIM/shaderpacks/NAME.txt (OPTION=VALUE lines): sync.sh keeps the ones given in OPTS
# ("A=1 B=2"), or clears the file when OPTS is set but empty.
# FREEZE=SECONDS holds the water's clock at that time in the copy (lib/waves.glsl): at the frame or two a second the
# rig draws, moving waves and the light lines under them smear over the frames the picture is built from, so stills
# of water are taken with the clock held.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SLIM="${SLIM:-/home/user/cg-slim}"
NAME="${1:-Vector}"
SRC="${2:-$HERE/../../shaders}"
rm -rf "$SLIM/shaderpacks/$NAME"
mkdir -p "$SLIM/shaderpacks/$NAME"
cp -r "$SRC" "$SLIM/shaderpacks/$NAME/shaders"
if [ -n "${FREEZE:-}" ]; then
  sed -i "s/frameTimeCounter/float($FREEZE)/g" "$SLIM/shaderpacks/$NAME/shaders/lib/waves.glsl"
fi
if [ -n "${OPTS+x}" ]; then
  : > "$SLIM/shaderpacks/$NAME.txt"
  for o in $OPTS; do echo "$o" >> "$SLIM/shaderpacks/$NAME.txt"; done
fi
