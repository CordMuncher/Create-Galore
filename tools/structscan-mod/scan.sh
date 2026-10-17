#!/usr/bin/env bash
# Scans where every structure lands for one seed, straight from the seed (no chunks generated), and writes
# density numbers to <server>/structscan/<time>-<label>-<dimension>/summary.md (+ structures.tsv, sets.tsv, starts.tsv).
#
# Usage: tools/structscan-mod/scan.sh /path/to/server [options]
#   --seed N         seed to scan (default: random)
#   --radius B       scan a square ±B blocks around 0,0 (default 6000)
#   --label NAME     name for the report folder (default scan)
#   --rules FILE     what-if rules: "<structure set pattern> <spacing scale> [frequency scale]" per line
#                    several runs in one start: --rules none+a.txt+b.txt (none = a plain scan; much faster than separate starts)
#   --baseline DIR   compare against this earlier scan's folder (default: <server>/structscan/baseline if it exists)
#   --verify B       afterwards, generate the chunks within ±B blocks for real (structure step only) and compare
#   --dims D1+D2     dimensions (default overworld), e.g. overworld+the_nether+twilightforest:twilight_forest
#
# It starts the server once on a throwaway world (structscan-world), runs the scan and stops. The server's own
# world, server.properties and mods folder are put back exactly as they were. Needs a server from
# server/install-server.sh with eula=true, and the jar from build.sh.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(cd "${1:?give the server folder}" && pwd)"; shift
SEED="$RANDOM$RANDOM"; RADIUS=6000; LABEL=scan; RULES=""; BASELINE=""; VERIFY=0; DIMS=overworld
while [ $# -gt 0 ]; do
  case "$1" in
    --seed) SEED="$2"; shift 2 ;;
    --radius) RADIUS="$2"; shift 2 ;;
    --label) LABEL="$2"; shift 2 ;;
    --rules) # each entry made absolute, since the server runs in its own folder
      RULES=""; IFS='+' read -ra LIST <<< "$2"
      for f in "${LIST[@]}"; do
        [ "$f" = none ] || f="$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"
        RULES="${RULES:+$RULES+}$f"
      done
      shift 2 ;;
    --baseline) BASELINE="$(cd "$2" && pwd)"; shift 2 ;;
    --verify) VERIFY="$2"; shift 2 ;;
    --dims) DIMS="$2"; shift 2 ;;
    *) echo "Unknown option $1"; exit 1 ;;
  esac
done

JAR=$(ls "$HERE"/create-galore-structscan-*.jar 2>/dev/null | head -1)
[ -n "$JAR" ] || { echo "Build the mod first: $HERE/build.sh $SERVER"; exit 1; }

cd "$SERVER"
cp server.properties .server.properties.structscan
cp "$JAR" mods/
cleanup() {
  mv -f .server.properties.structscan server.properties
  rm -f "mods/$(basename "$JAR")"
  rm -rf structscan-world
}
trap cleanup EXIT

rm -rf structscan-world
# Sets a server.properties key, adding it if the file doesn't have it yet.
setprop() { if grep -q "^$1=" server.properties; then sed -i "s|^$1=.*|$1=$2|" server.properties; else echo "$1=$2" >> server.properties; fi; }
setprop level-seed "$SEED"
setprop level-name structscan-world
# Own port and no RCON/query, so it doesn't clash with a server that's already running.
setprop server-port 25591
setprop enable-rcon false
setprop enable-query false

ARGS="radius=$RADIUS,label=$LABEL,dims=$DIMS,verify=$VERIFY,exit=true"
[ -n "$RULES" ] && ARGS="$ARGS,rules=$RULES"
[ -n "$BASELINE" ] && ARGS="$ARGS,baseline=$BASELINE"
echo "Scanning seed $SEED, ±$RADIUS blocks ($DIMS)..."
JDK_JAVA_OPTIONS="-Dstructscan=$ARGS" ./run.sh nogui 2>&1 | grep --line-buffered -E "StructScan|Failed to (start|initialize)|crash report" || true
RUNS=$(( $(echo "$DIMS" | tr '+' '\n' | wc -l) * $(echo "${RULES:-none}" | tr '+' '\n' | wc -l) ))
ls -td structscan/*-"$LABEL"* 2>/dev/null | head -n "$RUNS"
