#!/usr/bin/env bash
# Builds cg_present.dll, the Bridge mod's DirectX 12 presenter (see cg_present.cpp) with its frame generation backends
# (cg_streamline.cpp, cg_ffx.cpp), for Windows x64 on Linux with clang and lld-link, into the mod's resources
# (natives/windows-x64/). Only needed after changing these sources; the built DLL is committed, so the mod's normal
# build (build.sh) does not need this.
#
# Downloads, into a cache: Streamline's and AMD's FidelityFX SDK's headers at the pinned tags (both MIT; their DLLs ship
# separately, see natives/windows-x64/streamline/ and fidelityfx/), and xwin (shared with build-ngx.sh), which fetches
# Microsoft's Windows SDK and MSVC runtime headers/libraries (running it means accepting Microsoft's license).
# Streamline and FidelityFX are loaded at run time, so nothing of them is linked. The link is reproducible (/Brepro), so
# the same inputs give the same DLL.
# Usage: tools/bridge-mod/native/build-present.sh [output.dll]
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/../src/main/resources/natives/windows-x64/cg_present.dll}"
SL_TAG=v2.14.16
FFX_TAG=v2.0.0
XWIN_VERSION=0.6.5
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge"
SL="$CACHE/streamline-$SL_TAG"
FFX="$CACHE/fidelityfx-$FFX_TAG"
XWIN_DIR="$CACHE/xwin-$XWIN_VERSION"
WINSDK="$XWIN_DIR/splat"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

for tool in clang lld-link git curl; do
  command -v "$tool" > /dev/null || { echo "Needs $tool" >&2; exit 1; }
done

if [ ! -s "$SL/include/sl.h" ]; then
  rm -rf "$SL"
  git clone -q --depth 1 --branch "$SL_TAG" --filter=blob:none --sparse https://github.com/NVIDIA-RTX/Streamline.git "$SL"
  git -C "$SL" sparse-checkout set --no-cone /include/ /license.txt
fi

if [ ! -s "$FFX/Kits/FidelityFX/framegeneration/include/ffx_framegeneration.h" ]; then
  rm -rf "$FFX"
  git -c advice.detachedHead=false clone -q --depth 1 --branch "$FFX_TAG" --filter=blob:none --sparse \
    https://github.com/GPUOpen-LibrariesAndSDKs/FidelityFX-SDK.git "$FFX"
  git -C "$FFX" sparse-checkout set --no-cone /Kits/FidelityFX/api/include/ /Kits/FidelityFX/framegeneration/include/ \
    /docs/license.md
fi

if [ ! -d "$WINSDK/crt" ]; then
  mkdir -p "$XWIN_DIR"
  curl -sSfL --retry 4 -o "$XWIN_DIR/xwin.tar.gz" \
    "https://github.com/Jake-Shadle/xwin/releases/download/$XWIN_VERSION/xwin-$XWIN_VERSION-x86_64-unknown-linux-musl.tar.gz"
  tar -xzf "$XWIN_DIR/xwin.tar.gz" -C "$XWIN_DIR" --strip-components=1
  "$XWIN_DIR/xwin" --accept-license --arch x86_64 --cache-dir "$XWIN_DIR/cache" splat --output "$WINSDK"
  rm -rf "$XWIN_DIR/cache"
fi

INCLUDES=(-imsvc "$WINSDK/crt/include" -imsvc "$WINSDK/sdk/include/ucrt" -imsvc "$WINSDK/sdk/include/um"
  -imsvc "$WINSDK/sdk/include/shared")
clang --driver-mode=cl --target=x86_64-pc-windows-msvc /nologo /c /O2 /MT /EHs-c- /GR- /std:c++17 /W3 /Brepro \
  "${INCLUDES[@]}" "$HERE/cg_present.cpp" /Fo"$WORK/cg_present.obj"
# Streamline's headers use the C++ library (std::string, std::vector), so this one keeps exceptions on, and accepts
# a clang older than the one the MSVC library asks for. sl_security.h includes <Softpub.h>, which the case-sensitive
# SDK copy spells softpub.h.
mkdir -p "$WORK/shim"
echo '#include <softpub.h>' > "$WORK/shim/Softpub.h"
clang --driver-mode=cl --target=x86_64-pc-windows-msvc /nologo /c /O2 /MT /EHsc /GR- /std:c++17 /W3 /Brepro \
  /D_ALLOW_COMPILER_AND_STL_VERSION_MISMATCH "${INCLUDES[@]}" -imsvc "$WORK/shim" -imsvc "$SL/include" \
  "$HERE/cg_streamline.cpp" /Fo"$WORK/cg_streamline.obj"

clang --driver-mode=cl --target=x86_64-pc-windows-msvc /nologo /c /O2 /MT /EHs-c- /GR- /std:c++17 /W3 /Brepro \
  "${INCLUDES[@]}" -imsvc "$FFX/Kits/FidelityFX/api/include" -imsvc "$FFX/Kits/FidelityFX/framegeneration/include" \
  "$HERE/cg_ffx.cpp" /Fo"$WORK/cg_ffx.obj"

mkdir -p "$(dirname "$OUT")"
# dxgi, d3d12, d3dcompiler, Streamline and FidelityFX are loaded at run time (GetProcAddress); dxguid.lib only supplies
# the interface IDs. The signature checks load wintrust and crypt32 themselves.
lld-link /nologo /dll /machine:x64 /Brepro /opt:ref /opt:icf /out:"$WORK/cg_present.dll" \
  /libpath:"$WINSDK/crt/lib/x86_64" /libpath:"$WINSDK/sdk/lib/um/x86_64" /libpath:"$WINSDK/sdk/lib/ucrt/x86_64" \
  "$WORK/cg_present.obj" "$WORK/cg_streamline.obj" "$WORK/cg_ffx.obj" dxguid.lib kernel32.lib user32.lib
cp "$WORK/cg_present.dll" "$OUT"
echo "Built $OUT ($(sha256sum "$OUT" | cut -d' ' -f1))"
