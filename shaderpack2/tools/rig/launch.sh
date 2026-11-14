#!/usr/bin/env bash
# Starts the virtual screen (if it isn't up) and the slim client, in the background.
#
#   launch.sh [WORLD]      # WORLD: a save's folder name to open straight away (else the title screen)
#
# RES=1280x720 the screen's size; HEAP=6G; PACK=Vector the shader pack (a folder in SLIM/shaderpacks, copied there by
# sync.sh); SHADERS=false starts without shaders. The game's output goes to SLIM/logs/rig.log.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MAIN="${MAIN:-/home/user/cg-client}"
SLIM="${SLIM:-/home/user/cg-slim}"
RES="${RES:-1280x720}"
export DISPLAY="${DISPLAY:-:99}"
if ! xdpyinfo >/dev/null 2>&1; then
  rm -f "/tmp/.X${DISPLAY#:}-lock"
  setsid nohup Xvfb "$DISPLAY" -screen 0 "${RES}x24" -nolisten tcp >/dev/null 2>&1 &
  sleep 2
fi
mkdir -p "$SLIM/config" "$SLIM/logs"
cat > "$SLIM/config/iris.properties" <<OPT
shaderPack=${PACK:-Vector}
enableShaders=${SHADERS:-true}
enableDebugOptions=false
maxShadowRenderDistance=32
OPT
sed -i '/^overrideWidth\|^overrideHeight\|^fullscreen/d' "$SLIM/options.txt"
echo "overrideWidth:${RES%x*}" >> "$SLIM/options.txt"
echo "overrideHeight:${RES#*x}" >> "$SLIM/options.txt"
echo "fullscreen:false" >> "$SLIM/options.txt"
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MALLOC_ARENA_MAX=2
args=(--main-dir "$MAIN" start --mc-dir "$SLIM" --resolution "$RES"
      --jvm-arg=-Xmx"${HEAP:-6G}" --jvm-arg=-Xms2G)
[ -n "${1:-}" ] && args+=(--join-world "$1")
cd "$SLIM"
setsid nohup portablemc "${args[@]}" neoforge::21.1.262 > "$SLIM/logs/rig.log" 2>&1 &
echo "started (log: $SLIM/logs/rig.log)"
