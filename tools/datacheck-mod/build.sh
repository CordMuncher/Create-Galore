#!/usr/bin/env bash
# Builds the Create Galore Data Check mod (a test tool, not part of the pack) with plain javac (no Gradle).
# It compiles against an installed Create Galore server, which already has every jar it needs.
# Usage: tools/datacheck-mod/build.sh /path/to/server   (a folder made by server/install-server.sh)
# Needs a JDK 21. The jar lands next to this script; copy it into a test server's mods folder.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(cd "${1:?give an installed server folder}" && pwd)"
VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' "$HERE/src/main/resources/META-INF/neoforge.mods.toml")
OUT="$HERE/create-galore-datacheck-$VERSION.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Minecraft (Mojang names), NeoForge, every library and every mod.
NF=$(ls "$SERVER"/libraries/net/neoforged/neoforge/*/neoforge-*-universal.jar | head -1)
# Mods bundle some libraries inside themselves (Create carries Ponder), so unpack those too.
for j in "$NF" "$SERVER"/mods/*.jar; do
  mkdir -p "$WORK/jij/$(basename "$j")"
  unzip -q -o -d "$WORK/jij/$(basename "$j")" "$j" 'META-INF/jarjar/*.jar' 2>/dev/null || true
done
CP=$( { ls "$SERVER"/libraries/net/minecraft/server/*/server-*-srg.jar
        find "$SERVER/libraries" -name '*.jar' ! -path '*/net/minecraft/server/*'
        find "$WORK/jij" -name '*.jar'; ls "$SERVER"/mods/*.jar; } | tr '\n' ':')

mkdir -p "$WORK/classes"
javac --release 21 -nowarn -proc:none -cp "$CP" -d "$WORK/classes" $(find "$HERE/src/main/java" -name '*.java')
cp -r "$HERE/src/main/resources/." "$WORK/classes/"
rm -f "$HERE"/create-galore-datacheck-*.jar
(cd "$WORK/classes" && jar --create --date=2026-01-01T00:00:00Z --file "$OUT" .)
echo "Built $OUT"
