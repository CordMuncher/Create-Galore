#!/usr/bin/env bash
# Builds rr-probe.exe (see rr-probe.cpp), the stand-alone DLSS Ray Reconstruction check for the PC test, for Windows
# x64 on Linux with clang and lld-link, into tools/dlss-testkit/dist/. Same downloads and cache as build-ngx.sh.
# Never part of the pack.
# Usage: tools/bridge-mod/native/build-rr-probe.sh

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../../dlss-testkit/dist/rr-probe.exe"
DLSS_TAG=v310.9.16
XWIN_VERSION=0.6.5
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge"
SDK="$CACHE/dlss-sdk-$DLSS_TAG"
XWIN_DIR="$CACHE/xwin-$XWIN_VERSION"
WINSDK="$XWIN_DIR/splat"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

for tool in clang lld-link git curl; do
  command -v "$tool" > /dev/null || { echo "Needs $tool" >&2; exit 1; }
done

if [ ! -s "$SDK/lib/Windows_x86_64/x64/nvsdk_ngx_s.lib" ]; then
  rm -rf "$SDK"
  git clone -q --depth 1 --branch "$DLSS_TAG" --filter=blob:none --sparse https://github.com/NVIDIA/DLSS.git "$SDK"
  git -C "$SDK" sparse-checkout set --no-cone /include/ /lib/Windows_x86_64/x64/nvsdk_ngx_s.lib /LICENSE.txt
fi

if [ ! -d "$WINSDK/crt" ]; then
  mkdir -p "$XWIN_DIR"
  curl -sSfL --retry 4 -o "$XWIN_DIR/xwin.tar.gz" \
    "https://github.com/Jake-Shadle/xwin/releases/download/$XWIN_VERSION/xwin-$XWIN_VERSION-x86_64-unknown-linux-musl.tar.gz"
  tar -xzf "$XWIN_DIR/xwin.tar.gz" -C "$XWIN_DIR" --strip-components=1
  "$XWIN_DIR/xwin" --accept-license --arch x86_64 --cache-dir "$XWIN_DIR/cache" splat --output "$WINSDK"
  rm -rf "$XWIN_DIR/cache"
fi

clang --driver-mode=cl --target=x86_64-pc-windows-msvc /nologo /c /O2 /MT /EHsc /std:c++17 /W3 /Brepro \
  -imsvc "$WINSDK/crt/include" -imsvc "$WINSDK/sdk/include/ucrt" -imsvc "$WINSDK/sdk/include/um" \
  -imsvc "$WINSDK/sdk/include/shared" /I "$SDK/include" \
  "$HERE/rr-probe.cpp" /Fo"$WORK/rr-probe.obj"

mkdir -p "$(dirname "$OUT")"
lld-link /nologo /subsystem:console /machine:x64 /Brepro /opt:ref /opt:icf /out:"$WORK/rr-probe.exe" \
  /libpath:"$WINSDK/crt/lib/x86_64" /libpath:"$WINSDK/sdk/lib/um/x86_64" /libpath:"$WINSDK/sdk/lib/ucrt/x86_64" \
  "$WORK/rr-probe.obj" "$SDK/lib/Windows_x86_64/x64/nvsdk_ngx_s.lib" \
  d3d11.lib d3d12.lib dxgi.lib advapi32.lib user32.lib gdi32.lib shell32.lib ole32.lib version.lib
cp "$WORK/rr-probe.exe" "$OUT"
echo "Built $OUT ($(sha256sum "$OUT" | cut -d' ' -f1))"
