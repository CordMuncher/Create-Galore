#!/usr/bin/env bash
# The cloud test rig for Vector: a slim Minecraft client (NeoForge + Iris + Sodium + Distant Horizons + the Bridge and
# the few mods its mixins need) drawn by Mesa's software renderer (llvmpipe) on a virtual screen. Slow (a frame or
# two a second), but it runs the real pack in the real game.
#
#   shaderpack2/tools/rig/setup.sh            # once per machine: the game, the mods, the folders
#   shaderpack2/tools/rig/launch.sh           # starts the virtual screen and the game (see its notes)
#
# Needs: java 21, python3 + pip, Xvfb, xdotool, ImageMagick. MAIN is where Minecraft's own files go, SLIM the
# instance. The mods come from the pack's own pinned files on the branch REF (default origin/main-6fi2ef).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
MAIN="${MAIN:-/home/user/cg-client}"
SLIM="${SLIM:-/home/user/cg-slim}"
REF="${REF:-origin/main-6fi2ef}"
MODS="create sable create-aeronautics aeronautics-winds-and-weather create-deep-seas ssrd create-smog
pollution-of-the-realms advanced-chimneys forgeendertech tfmg-community-edition create-aeronautics-discovery iris
sodium distanthorizons ${EXTRA_MODS:-}"

# The pack as it is on REF now (a stale copy of the branch brings an old Bridge).
case "$REF" in origin/*) git -C "$REPO" fetch -q origin "${REF#origin/}" || true ;; esac

command -v portablemc >/dev/null || pip install -q portablemc==5.0.6
[ -d "$MAIN/versions" ] || portablemc --main-dir "$MAIN" start neoforge::21.1.262 --dry

mkdir -p "$SLIM/mods" "$SLIM/shaderpacks" "$SLIM/config" "$SLIM/saves"
for m in $MODS; do
  meta="$(git -C "$REPO" show "$REF:pack/mods/$m.pw.toml")"
  file="$(sed -n 's/^filename = "\(.*\)"/\1/p' <<<"$meta")"
  url="$(sed -n 's/^url = "\(.*\)"/\1/p' <<<"$meta")"
  hash="$(sed -n 's/^hash = "\(.*\)"/\1/p' <<<"$meta")"
  if ! { [ -s "$SLIM/mods/$file" ] && echo "$hash  $SLIM/mods/$file" | sha512sum -c --status; }; then
    echo "fetching $file"
    curl -sSfL --retry 4 -o "$SLIM/mods/$file" "$url"
    echo "$hash  $SLIM/mods/$file" | sha512sum -c --status || { echo "$file doesn't match the pack's hash" >&2; exit 1; }
  fi
done
# The Bridge under test: the pack's own jar on REF, unless BRIDGE_JAR names another.
rm -f "$SLIM"/mods/create-galore-bridge-*.jar
if [ -n "${BRIDGE_JAR:-}" ]; then
  cp "$BRIDGE_JAR" "$SLIM/mods/"
else
  jar="$(git -C "$REPO" ls-tree --name-only "$REF" pack/mods/ | grep 'create-galore-bridge-.*\.jar$' | tail -1)"
  git -C "$REPO" show "$REF:$jar" > "$SLIM/mods/$(basename "$jar")"
fi
# The pack's own settings for the mods that have them here (Distant Horizons, the Bridge's display settings).
for c in DistantHorizons.toml; do
  git -C "$REPO" show "$REF:pack/config/$c" > "$SLIM/config/$c" 2>/dev/null || true
done
cat > "$SLIM/options.txt" <<OPT
pauseOnLostFocus:false
tutorialStep:none
narrator:0
renderDistance:${RENDER_DISTANCE:-10}
simulationDistance:5
maxFps:260
enableVsync:false
fov:0.0
guiScale:2
soundCategory_master:0.0
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
OPT
mkdir -p "$SLIM/config"
cat > "$SLIM/config/fml.toml" <<OPT
earlyWindowControl = false
OPT
echo "slim client ready in $SLIM ($(ls "$SLIM/mods" | wc -l) mods)"
