#!/usr/bin/env bash
# Builds (or updates) a Create Galore dedicated server in the folder you give it.
# Usage: ./install-server.sh /path/to/server-folder
# Needs Java 21 and curl. Run it again after the pack changes to update the mods.
set -euo pipefail

NEOFORGE=21.1.259
HERE="$(cd "$(dirname "$0")" && pwd)"
PACK="$HERE/../pack/pack.toml"
DEST="${1:?give the server folder, e.g. ./install-server.sh ~/CreateGaloreServer}"

mkdir -p "$DEST"
cd "$DEST"

if [ ! -f run.sh ]; then
  echo "Installing NeoForge $NEOFORGE server..."
  curl -fL -o neoforge-installer.jar \
    "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
  java -jar neoforge-installer.jar --install-server .
  rm -f neoforge-installer.jar neoforge-installer.jar.log
fi

[ -f packwiz-installer-bootstrap.jar ] || curl -fL -o packwiz-installer-bootstrap.jar \
  https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar

[ -f packwiz-installer.jar ] || curl -fL -o packwiz-installer.jar \
  https://github.com/packwiz/packwiz-installer/releases/latest/download/packwiz-installer.jar

echo "Downloading the server-side mods and configs..."
java -jar packwiz-installer-bootstrap.jar --bootstrap-no-update -g -s server "$PACK"

# First run only: memory settings and server settings. Your own edits are kept on later runs.
[ -f user_jvm_args.txt.galore ] || { cp "$HERE/user_jvm_args.txt" user_jvm_args.txt; touch user_jvm_args.txt.galore; }
[ -f server.properties ] || cp "$HERE/server.properties" server.properties

if ! ls mods/twilightforest-*.jar >/dev/null 2>&1; then
  echo
  echo "ONE MANUAL STEP: put twilightforest-1.21.1-4.8.3345-universal.jar (CurseForge) into $DEST/mods"
fi
if ! grep -q 'eula=true' eula.txt 2>/dev/null; then
  echo "Read https://aka.ms/MinecraftEULA, then put eula=true in $DEST/eula.txt"
fi
echo "Done. Start the server with ./run.sh nogui"
