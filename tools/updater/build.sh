#!/usr/bin/env bash
# Builds create-galore-updater.jar (the instance's pre-launch updater) into the folder given, default dist/.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mkdir -p "${1:-dist}" && cd "${1:-dist}" && pwd)"
TMP="$(mktemp -d)"
javac --release 17 -d "$TMP" "$HERE/src/CreateGaloreUpdater.java"
printf 'Main-Class: CreateGaloreUpdater\n' > "$TMP/manifest.txt"
jar --create --file "$OUT/create-galore-updater.jar" --manifest "$TMP/manifest.txt" -C "$TMP" CreateGaloreUpdater.class -C "$TMP" 'CreateGaloreUpdater$1.class'
rm -rf "$TMP"
echo "built $OUT/create-galore-updater.jar"
