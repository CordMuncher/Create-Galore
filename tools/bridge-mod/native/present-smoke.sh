#!/usr/bin/env bash
# Builds present-smoke.exe (the DirectX 12 presenter's smoke test, see present-smoke.cpp) and runs it under Wine,
# whose D3D12 runs on Vulkan (on a machine without a GPU: Mesa's lavapipe, package mesa-vulkan-drivers). Needs the
# Windows SDK and Streamline header caches build-present.sh makes, wine64 and an X display (DISPLAY, e.g. Xvfb).
# Then it runs it once more with the mod's Streamline DLLs, which have no NVIDIA card to run on under Wine: the
# presenter must go on without them (reported, not part of the pass).
# Usage: tools/bridge-mod/native/present-smoke.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WINSDK="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge/xwin-0.6.5/splat"
SL="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge/streamline-v2.14.16"
SL_DLLS="$HERE/../src/main/resources/natives/windows-x64/streamline"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
WINE="$(command -v wine64 || command -v wine || echo /usr/lib/wine/wine64)"
[ -d "$WINSDK/crt" ] && [ -s "$SL/include/sl.h" ] || { echo "Run build-present.sh first (it fetches the SDKs)" >&2; exit 1; }
[ -x "$WINE" ] || { echo "Needs wine64" >&2; exit 1; }
[ -n "${DISPLAY:-}" ] || { echo "Needs an X display (DISPLAY)" >&2; exit 1; }

INCLUDES=(-imsvc "$WINSDK/crt/include" -imsvc "$WINSDK/sdk/include/ucrt" -imsvc "$WINSDK/sdk/include/um"
  -imsvc "$WINSDK/sdk/include/shared")
clang --driver-mode=cl --target=x86_64-pc-windows-msvc /nologo /c /O2 /MT /EHs-c- /GR- /std:c++17 /W3 \
  "${INCLUDES[@]}" /I "$HERE" "$HERE/present-smoke.cpp" /Fo"$WORK/present-smoke.obj"
mkdir -p "$WORK/shim"
echo '#include <softpub.h>' > "$WORK/shim/Softpub.h"
clang --driver-mode=cl --target=x86_64-pc-windows-msvc /nologo /c /O2 /MT /EHsc /GR- /std:c++17 /W3 \
  /D_ALLOW_COMPILER_AND_STL_VERSION_MISMATCH "${INCLUDES[@]}" -imsvc "$WORK/shim" -imsvc "$SL/include" \
  "$HERE/cg_streamline.cpp" /Fo"$WORK/cg_streamline.obj"
lld-link /nologo /machine:x64 /subsystem:console /out:"$WORK/present-smoke.exe" \
  /libpath:"$WINSDK/crt/lib/x86_64" /libpath:"$WINSDK/sdk/lib/um/x86_64" /libpath:"$WINSDK/sdk/lib/ucrt/x86_64" \
  "$WORK/present-smoke.obj" "$WORK/cg_streamline.obj" dxguid.lib kernel32.lib user32.lib

export WINEPREFIX="${WINEPREFIX:-${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge/wineprefix}"
export WINEDEBUG="${WINEDEBUG:--all,err+d3d12,err+vkd3d,err+dxgi}"
"$WINE" "$WORK/present-smoke.exe"
if [ -s "$SL_DLLS/sl.interposer.dll" ]; then
  mkdir -p "$WORK/streamline"
  cp "$SL_DLLS"/*.dll "$WORK/streamline/"
  STREAMLINE_DIR="$WORK/streamline"
  "$WINE" "$WORK/present-smoke.exe" --streamline "Z:${STREAMLINE_DIR//\//\\}" \
    || echo "(Streamline under Wine: exit $?, not part of the pass)"
fi
