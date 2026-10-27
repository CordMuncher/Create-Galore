#!/usr/bin/env bash
# Replays Ask the Book's casebook on a throwaway test server and prints the report.
# Usage: tools/book-casebook/run.sh SERVER_DIR [CASE_ID]
#   SERVER_DIR  a server made by server/install-server.sh, used only for testing: this script copies the Bridge jar from
#               pack/mods into it, sets it to a flat offline world named "casebook" and starts and stops it.
#   CASE_ID     run one case (the file name without .json) instead of all of them.
# Needs Java 21. No API key: the casebook runs the book's checks and tools, not the model.
# Exit status: 0 when nothing regressed (PASS, OPEN and FIXED are fine), 1 when a case FAILs or the run breaks.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(cd "${1:?give a test server folder}" && pwd)"
ONLY="${2:-}"
cd "$SERVER"

rm -f mods/create-galore-bridge-*.jar
cp "$HERE"/../../pack/mods/create-galore-bridge-*.jar mods/
grep -q 'eula=true' eula.txt 2>/dev/null || echo 'eula=true' > eula.txt
set_prop() { if grep -q "^$1=" server.properties; then sed -i "s|^$1=.*|$1=$2|" server.properties; else echo "$1=$2" >> server.properties; fi; }
set_prop level-name casebook
set_prop level-type minecraft:flat
set_prop online-mode false
set_prop spawn-protection 0
set_prop max-tick-time -1
rm -f "$HERE/casebook-report.md"

FIFO="$(mktemp -u)"
mkfifo "$FIFO"
LOG="$SERVER/logs/casebook-console.log"
mkdir -p logs
# The server reads its console from the fifo; holding it open on fd 3 keeps the server from seeing end-of-input.
exec 3<>"$FIFO"
./run.sh nogui <&3 > "$LOG" 2>&1 &
PID=$!
# On any way out, ask the server to stop (saving its world) before giving up on it.
cleanup() {
  echo stop >&3 2>/dev/null || true
  for _ in $(seq 120); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
  pkill -P "$PID" 2>/dev/null || true; kill "$PID" 2>/dev/null || true; rm -f "$FIFO"
}
trap cleanup EXIT

wait_for() { # pattern, seconds
  local i=0
  until grep -q "$1" "$LOG" 2>/dev/null; do
    kill -0 "$PID" 2>/dev/null || { echo "The server stopped; see $LOG" >&2; return 1; }
    i=$((i + 1)); [ "$i" -le "$2" ] || { echo "Timed out waiting for '$1'; see $LOG" >&2; return 1; }
    sleep 1
  done
}

echo "Starting the test server (a few minutes)..."
wait_for 'Done (' 900
echo "fieldguide casebook run \"$HERE\"${ONLY:+ $ONLY}" >&3
wait_for 'Casebook: [0-9]* pass\|Casebook run stopped\|Couldn.t read the casebook\|No cases\|Incorrect argument\|Unknown or incomplete' 600
cleanup
trap - EXIT

[ -f "$HERE/casebook-report.md" ] || { grep -m3 'Casebook' "$LOG" >&2; exit 1; }
cat "$HERE/casebook-report.md"
! grep -q '| FAIL |\|| ERROR |' "$HERE/casebook-report.md"
