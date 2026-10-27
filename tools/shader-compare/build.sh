#!/usr/bin/env bash
# Builds the shader compare mod with plain javac (no Gradle). It is a test tool and never goes into pack/mods:
# copy the jar into a separate test instance's mods folder (see README.md).
# Usage: tools/shader-compare/build.sh /path/to/server [output.jar]   (a folder made by server/install-server.sh)
# It reuses the client jars that tools/bridge-mod/build.sh caches, so build the Bridge once first.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(cd "${1:?give an installed server folder}" && pwd)"
VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' "$HERE/src/main/resources/META-INF/neoforge.mods.toml")
OUT="${2:-$HERE/build/create-galore-shader-compare-$VERSION.jar}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge"
NF=$(ls "$SERVER"/libraries/net/neoforged/neoforge/*/neoforge-*-universal.jar | head -1)
NFV=$(basename "$(dirname "$NF")")
CLIENT="$CACHE/client-$NFV"
NFCLIENT="$CLIENT/libraries/net/neoforged/neoforge/$NFV/neoforge-$NFV-client.jar"
IRIS="$CACHE/$(sed -n 's/^filename = "\(.*\)"/\1/p' "$HERE/../../pack/mods/iris.pw.toml")"
for f in "$NFCLIENT" "$IRIS"; do
  [ -s "$f" ] || { echo "$f is missing: run tools/bridge-mod/build.sh first" >&2; exit 1; }
done
CP=$( { echo "$NFCLIENT"; ls "$CLIENT"/libraries/net/minecraft/client/*/client-*-srg.jar | head -1
        find "$SERVER/libraries" -name '*.jar' ! -path '*/net/minecraft/server/*'
        ls "$CACHE"/lwjgl*-3.3.3.jar; echo "$IRIS"; } | tr '\n' ':')

mkdir -p "$WORK/classes" "$(dirname "$OUT")"
javac --release 21 -nowarn -proc:none -cp "$CP" -d "$WORK/classes" $(find "$HERE/src/main/java" -name '*.java')
cp -r "$HERE/src/main/resources/." "$WORK/classes/"
(cd "$WORK/classes" && jar --create --date=2026-01-01T00:00:00Z --file "$OUT" .)
echo "Built $OUT"
