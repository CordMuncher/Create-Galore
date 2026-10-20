#!/usr/bin/env bash
# Builds the Create Galore Fishing mod into pack/mods/ with plain javac (no Gradle), the same way as the Bridge.
# It compiles against an installed Create Galore server, which already has every jar it needs.
# Usage: tools/fishing-mod/build.sh /path/to/server   (a folder made by server/install-server.sh)
# Needs a JDK 21. After building, refresh the pack index (packwiz refresh, or the sha256 in pack/index.toml).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(cd "${1:?give an installed server folder}" && pwd)"
VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' "$HERE/src/main/resources/META-INF/neoforge.mods.toml")
OUT="$HERE/../../pack/mods/create-galore-fishing-$VERSION.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Minecraft (Mojang names), NeoForge, every library and every mod.
NF=$(ls "$SERVER"/libraries/net/neoforged/neoforge/*/neoforge-*-universal.jar | head -1)
# Mods bundle some libraries inside themselves (Create carries Ponder, Flywheel and Registrate; Sable its
# companion API), so unpack those too.
for j in "$NF" "$SERVER"/mods/*.jar; do
  mkdir -p "$WORK/jij/$(basename "$j")"
  unzip -q -o -d "$WORK/jij/$(basename "$j")" "$j" 'META-INF/jarjar/*.jar' 2>/dev/null || true
done
# Our own jar must not be on the classpath (an older build would shadow the sources being compiled).
CP=$( { ls "$SERVER"/libraries/net/minecraft/server/*/server-*-srg.jar
        find "$SERVER/libraries" -name '*.jar' ! -path '*/net/minecraft/server/*'
        find "$WORK/jij" -name '*.jar'; ls "$SERVER"/mods/*.jar | grep -v 'create-galore-fishing-'; } | tr '\n' ':')

# A server has no LWJGL and no client classes; the renderers need both at compile time only.
# Shares the Bridge's cache, so the client is only installed once per machine.
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/create-galore-bridge"
mkdir -p "$CACHE"
for m in lwjgl lwjgl-glfw lwjgl-opengl; do
  # Maven Central rate-limits busy machines (HTTP 429); Mojang's library server has the same jars.
  [ -s "$CACHE/$m-3.3.3.jar" ] || curl -sSfL --retry 4 -o "$CACHE/$m-3.3.3.jar" "https://repo1.maven.org/maven2/org/lwjgl/$m/3.3.3/$m-3.3.3.jar" \
    || curl -sSfL --retry 4 -o "$CACHE/$m-3.3.3.jar" "https://libraries.minecraft.net/org/lwjgl/$m/3.3.3/$m-3.3.3.jar"
  CP="$CP$CACHE/$m-3.3.3.jar:"
done
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
javac --release 21 -nowarn -proc:none -encoding UTF-8 -cp "$CP" -d "$WORK/classes" $(find "$HERE/src/main/java" -name '*.java')
cp -r "$HERE/src/main/resources/." "$WORK/classes/"
cp "$HERE/LICENSE" "$HERE/LICENSE-MPL-2.0" "$HERE/LICENSE-TIDE-EXTRA-COMPATIBILITY" "$HERE/NOTICE" "$WORK/classes/META-INF/"
rm -f "$HERE"/../../pack/mods/create-galore-fishing-*.jar
(cd "$WORK/classes" && jar --create --date=2026-01-01T00:00:00Z --file "$OUT" .)
echo "Built $OUT"
