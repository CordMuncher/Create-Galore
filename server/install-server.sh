#!/usr/bin/env bash
# Builds (or updates) a Create Galore dedicated server in the folder you give it.
# Usage: ./install-server.sh /path/to/server-folder [--clean]
#   --clean  also resets every config to the pack's version (config/, defaultconfigs/, each world's serverconfig/).
#            For bug hunting. Mods aren't downloaded again; the world, server.properties, memory and whitelist stay.
# Needs Java 21 and curl. Start the server with start-server.sh: it asks before applying pack updates.
set -euo pipefail

NEOFORGE=21.1.259
HERE="$(cd "$(dirname "$0")" && pwd)"
PACK="$HERE/../pack/pack.toml"
DEST="${1:?give the server folder, e.g. ./install-server.sh ~/CreateGaloreServer}"

mkdir -p "$DEST"
cd "$DEST"

# Installs NeoForge on the first run, and again when the pack moves to another NeoForge version.
if [ ! -f run.sh ] || [ ! -d "libraries/net/neoforged/neoforge/$NEOFORGE" ]; then
  echo "Installing NeoForge $NEOFORGE server..."
  curl -fL -o neoforge-installer.jar \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
  java -jar neoforge-installer.jar --install-server .
  rm -f neoforge-installer.jar neoforge-installer.jar.log
fi

# packwiz-installer remembers what it installed (packwiz.json), so a re-run only downloads what changed,
# removes mods the pack dropped and leaves config edits alone unless the pack changed that file.
[ -f packwiz-installer-bootstrap.jar ] || curl -fL -o packwiz-installer-bootstrap.jar \
  https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v0.0.3/packwiz-installer-bootstrap.jar

[ -f packwiz-installer.jar ] || curl -fL -o packwiz-installer.jar \
  https://github.com/packwiz/packwiz-installer/releases/download/v0.5.14/packwiz-installer.jar

echo "Downloading the server-side mods and configs that changed..."
java -jar packwiz-installer-bootstrap.jar --bootstrap-no-update -g -s server "$PACK"

if [ "${2:-}" = "--clean" ]; then
  echo "Resetting every config to the pack's version (the world is kept)..."
  rm -rf config defaultconfigs
  for w in */level.dat; do [ -f "$w" ] && rm -rf "$(dirname "$w")/serverconfig"; done
  # Without its record packwiz-installer re-checks every file: mods match and stay, the deleted configs come back.
  rm -f packwiz.json
  java -jar packwiz-installer-bootstrap.jar --bootstrap-no-update -g -s server "$PACK"
fi

# The start script and its update check (asks no / yes / back up first when the pack on GitHub is newer).
cp "$HERE/start-server.sh" start-server.sh && chmod +x start-server.sh
if [ -f "$HERE/create-galore-updater.jar" ]; then cp "$HERE/create-galore-updater.jar" .
else curl -fsSL -o create-galore-updater.jar https://github.com/emresahin0906-code/Create-Galore/releases/latest/download/create-galore-updater.jar \
  || echo "Couldn't download create-galore-updater.jar, so start-server.sh won't check for updates"
fi
VERSION="$(sed -n 's/^version *= *"\(.*\)"/\1/p' "$PACK")"
printf '# Create Galore pack version this server has, for the update check at start\npack=%s\ninstaller=script\n' "$VERSION" > .galore-pack.txt

# First run only: memory settings and server settings. Your own edits are kept on later runs.
[ -f user_jvm_args.txt.galore ] || { cp "$HERE/user_jvm_args.txt" user_jvm_args.txt; touch user_jvm_args.txt.galore; }
[ -f server.properties ] || cp "$HERE/server.properties" server.properties
# Older installs got view 8 / simulation 6: raise them unless you changed them yourself.
if grep -qx 'view-distance=8' server.properties && grep -qx 'simulation-distance=6' server.properties; then
  sed -i.bak -e 's/^view-distance=8$/view-distance=12/' -e 's/^simulation-distance=6$/simulation-distance=10/' server.properties && rm -f server.properties.bak
fi

if ! ls mods/twilightforest-*.jar >/dev/null 2>&1; then
  echo
  echo "ONE MANUAL STEP: put twilightforest-1.21.1-4.8.3345-universal.jar (CurseForge) into $DEST/mods"
fi
if ! grep -q 'eula=true' eula.txt 2>/dev/null; then
  echo "Read https://aka.ms/MinecraftEULA, then put eula=true in $DEST/eula.txt"
fi
echo "Done. Start the server with ./start-server.sh (it checks for pack updates first, then opens the server window with the live memory and player panel; add nogui for a console-only server)"
