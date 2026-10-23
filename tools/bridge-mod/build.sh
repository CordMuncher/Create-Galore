#!/usr/bin/env bash
# Builds the Create Galore Bridge mod into pack/mods/ with plain javac (no Gradle).
# It compiles against an installed Create Galore server, which already has every jar it needs.
# Usage: tools/bridge-mod/build.sh /path/to/server   (a folder made by server/install-server.sh)
# Needs a JDK 21. After building, run `packwiz refresh` in pack/.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(cd "${1:?give an installed server folder}" && pwd)"
VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' "$HERE/src/main/resources/META-INF/neoforge.mods.toml")
OUT="$HERE/../../pack/mods/create-galore-bridge-$VERSION.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Minecraft (Mojang names), NeoForge, every library and every mod.
NF=$(ls "$SERVER"/libraries/net/neoforged/neoforge/*/neoforge-*-universal.jar | head -1)
# Mods bundle some libraries inside themselves (Create carries Ponder), so unpack those too.
for j in "$NF" "$SERVER"/mods/*.jar; do
  mkdir -p "$WORK/jij/$(basename "$j")"
  unzip -q -o -d "$WORK/jij/$(basename "$j")" "$j" 'META-INF/jarjar/*.jar' 'META-INF/jars/*.jar' 2>/dev/null || true
done
CP=$( { ls "$SERVER"/libraries/net/minecraft/server/*/server-*-srg.jar
        find "$SERVER/libraries" -name '*.jar' ! -path '*/net/minecraft/server/*'
        find "$WORK/jij" -name '*.jar'; ls "$SERVER"/mods/*.jar; } | tr '\n' ':')

# A server has no LWJGL (the display code needs it at compile time only); Minecraft 1.21.1 ships 3.3.3.
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge"
mkdir -p "$CACHE"
for m in lwjgl lwjgl-glfw lwjgl-opengl; do
  # Maven Central rate-limits busy machines (HTTP 429); Mojang's library server has the same jars.
  [ -s "$CACHE/$m-3.3.3.jar" ] || curl -sSfL --retry 4 -o "$CACHE/$m-3.3.3.jar" "https://repo1.maven.org/maven2/org/lwjgl/$m/3.3.3/$m-3.3.3.jar" \
    || curl -sSfL --retry 4 -o "$CACHE/$m-3.3.3.jar" "https://libraries.minecraft.net/org/lwjgl/$m/3.3.3/$m-3.3.3.jar"
  CP="$CP$CACHE/$m-3.3.3.jar:"
done
# A server has no client classes either (the book screen, the compass needle and the display code use them).
# The NeoForge installer puts a client in a cache once; it only downloads and patches Minecraft, nothing runs.
NFV=$(basename "$(dirname "$NF")")
CLIENT="$CACHE/client-$NFV"
NFCLIENT="$CLIENT/libraries/net/neoforged/neoforge/$NFV/neoforge-$NFV-client.jar"
if [ ! -s "$NFCLIENT" ]; then
  mkdir -p "$CLIENT"
  echo '{"profiles":{}}' > "$CLIENT/launcher_profiles.json"
  curl -sSfL --retry 4 -o "$CACHE/neoforge-$NFV-installer.jar" \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NFV/neoforge-$NFV-installer.jar"
  (cd "$CACHE" && java -jar "neoforge-$NFV-installer.jar" --install-client "$CLIENT" > "$CACHE/client-install.log")
fi
# Patched client classes first, so they win over the server's copies of the same classes.
CP="$NFCLIENT:$(ls "$CLIENT"/libraries/net/minecraft/client/*/client-*-srg.jar | head -1):$CP"

mkdir -p "$WORK/classes"
javac --release 21 -nowarn -proc:none -cp "$CP" -d "$WORK/classes" $(find "$HERE/src/main/java" -name '*.java')
cp -r "$HERE/src/main/resources/." "$WORK/classes/"
rm -f "$HERE"/../../pack/mods/create-galore-bridge-*.jar
(cd "$WORK/classes" && jar --create --date=2026-01-01T00:00:00Z --file "$OUT" .)
echo "Built $OUT"
