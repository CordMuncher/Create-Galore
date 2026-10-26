#!/usr/bin/env bash
# Builds cg_present.dll, the Bridge mod's DirectX 12 presenter (see cg_present.cpp), for Windows x64 on Linux with
# clang and lld-link, into the mod's resources (natives/windows-x64/). Only needed after changing cg_present.cpp; the
# built DLL is committed, so the mod's normal build (build.sh) does not need this.
#
# Downloads xwin into a cache (shared with build-ngx.sh), which fetches Microsoft's Windows SDK and MSVC runtime
# headers/libraries (running it means accepting Microsoft's license). No other SDK is needed. The link is
# reproducible (/Brepro), so the same inputs give the same DLL.
# Usage: tools/bridge-mod/native/build-present.sh [output.dll]
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/../src/main/resources/natives/windows-x64/cg_present.dll}"
XWIN_VERSION=0.6.5
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge"
XWIN_DIR="$CACHE/xwin-$XWIN_VERSION"
WINSDK="$XWIN_DIR/splat"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

for tool in clang lld-link curl; do
  command -v "$tool" > /dev/null || { echo "Needs $tool" >&2; exit 1; }
done

if [ ! -d "$WINSDK/crt" ]; then
  mkdir -p "$XWIN_DIR"
  curl -sSfL --retry 4 -o "$XWIN_DIR/xwin.tar.gz" \
    "https://github.com/Jake-Shadle/xwin/releases/download/$XWIN_VERSION/xwin-$XWIN_VERSION-x86_64-unknown-linux-musl.tar.gz"
  tar -xzf "$XWIN_DIR/xwin.tar.gz" -C "$XWIN_DIR" --strip-components=1
  "$XWIN_DIR/xwin" --accept-license --arch x86_64 --cache-dir "$XWIN_DIR/cache" splat --output "$WINSDK"
  rm -rf "$XWIN_DIR/cache"
fi

clang --driver-mode=cl --target=x86_64-pc-windows-msvc /nologo /c /O2 /MT /EHs-c- /GR- /std:c++17 /W3 /Brepro \
  -imsvc "$WINSDK/crt/include" -imsvc "$WINSDK/sdk/include/ucrt" -imsvc "$WINSDK/sdk/include/um" \
  -imsvc "$WINSDK/sdk/include/shared" \
  "$HERE/cg_present.cpp" /Fo"$WORK/cg_present.obj"

mkdir -p "$(dirname "$OUT")"
# dxgi, d3d12 and d3dcompiler are loaded at run time (GetProcAddress); dxguid.lib only supplies the interface IDs.
lld-link /nologo /dll /machine:x64 /Brepro /opt:ref /opt:icf /out:"$WORK/cg_present.dll" \
  /libpath:"$WINSDK/crt/lib/x86_64" /libpath:"$WINSDK/sdk/lib/um/x86_64" /libpath:"$WINSDK/sdk/lib/ucrt/x86_64" \
  "$WORK/cg_present.obj" dxguid.lib kernel32.lib user32.lib
cp "$WORK/cg_present.dll" "$OUT"
echo "Built $OUT ($(sha256sum "$OUT" | cut -d' ' -f1))"
