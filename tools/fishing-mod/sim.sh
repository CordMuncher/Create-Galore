#!/usr/bin/env bash
# Runs the fight Monte Carlo outside the game: compiles only the pure-Java sim package (no Minecraft needed).
# Usage: tools/fishing-mod/sim.sh species.csv [fights=400] [seed=1] > results.csv
#   species.csv comes from "/cgfish simulate export" on a server (create_galore_fishing/species.csv there).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
javac --release 21 -nowarn -d "$WORK" "$HERE"/src/main/java/dev/creategalore/fishing/sim/*.java
java -cp "$WORK" dev.creategalore.fishing.sim.MonteCarlo "$@"
