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
  # The installer runs here, so it has to be the pinned file (the .sha256 maven.neoforged.net publishes for it).
  case "$NFV" in
    21.1.262) NF_SHA256=e86f5d78ed310465bd1ecf79823abcbc58782699b068ac776f4c5e226d994a8f ;;
    *) echo "No pinned installer checksum for NeoForge $NFV: add the .sha256 maven.neoforged.net publishes" >&2; exit 1 ;;
  esac
  mkdir -p "$CLIENT"
  echo '{"profiles":{}}' > "$CLIENT/launcher_profiles.json"
  curl -sSfL --retry 4 -o "$CACHE/neoforge-$NFV-installer.jar" \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NFV/neoforge-$NFV-installer.jar"
  echo "$NF_SHA256  $CACHE/neoforge-$NFV-installer.jar" | sha256sum -c --status \
    || { rm -f "$CACHE/neoforge-$NFV-installer.jar"; echo "The NeoForge installer doesn't match its pinned checksum" >&2; exit 1; }
  (cd "$CACHE" && java -jar "neoforge-$NFV-installer.jar" --install-client "$CLIENT" > "$CACHE/client-install.log")
fi
# Patched client classes first, so they win over the server's copies of the same classes.
CP="$NFCLIENT:$(ls "$CLIENT"/libraries/net/minecraft/client/*/client-*-srg.jar | head -1):$CP"
# Client-only mods the display code hooks into (DLAA uses Iris's and Colorwheel's classes, the far heights Distant
# Horizons' API). A server has none of them,
# so the pack's own pinned jars are fetched once and checked against the pack's hashes; they are compiled against, not shipped.
for m in iris colorwheel distanthorizons; do
  META="$HERE/../../pack/mods/$m.pw.toml"
  JAR="$CACHE/$(sed -n 's/^filename = "\(.*\)"/\1/p' "$META")"
  HASH=$(sed -n 's/^hash = "\(.*\)"/\1/p' "$META")
  if ! { [ -s "$JAR" ] && echo "$HASH  $JAR" | sha512sum -c --status; }; then
    curl -sSfL --retry 4 -o "$JAR" "$(sed -n 's/^url = "\(.*\)"/\1/p' "$META")"
    echo "$HASH  $JAR" | sha512sum -c --status || { echo "$JAR does not match pack/mods/$m.pw.toml" >&2; exit 1; }
  fi
  CP="$CP$JAR:"
done

# Every DLL the jar ships has to be the one recorded in natives.sha256 (natives-sums.sh rewrites it after a deliberate
# change), so a swapped or extra DLL stops the build instead of reaching players.
(cd "$HERE/src/main/resources" && sha256sum -c --quiet "$HERE/natives.sha256") \
  || { echo "A DLL under natives/ doesn't match natives.sha256 (run natives-sums.sh only if you changed it on purpose)" >&2; exit 1; }
[ "$(cut -d' ' -f3- "$HERE/natives.sha256" | LC_ALL=C sort)" = "$(cd "$HERE/src/main/resources" && find natives -name '*.dll' | LC_ALL=C sort)" ] \
  || { echo "natives.sha256 doesn't list exactly the DLLs under natives/ (run natives-sums.sh if that's on purpose)" >&2; exit 1; }

mkdir -p "$WORK/classes"
javac --release 21 -nowarn -proc:none -cp "$CP" -d "$WORK/classes" $(find "$HERE/src/main/java" -name '*.java')
cp -r "$HERE/src/main/resources/." "$WORK/classes/"
rm -f "$HERE"/../../pack/mods/create-galore-bridge-*.jar
(cd "$WORK/classes" && jar --create --date=2026-01-01T00:00:00Z --file "$OUT" .)
echo "Built $OUT"
