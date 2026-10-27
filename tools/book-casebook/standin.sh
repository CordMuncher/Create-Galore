#!/usr/bin/env bash
# A stand-in Ask the Book for the answer eval: a test server that plays the asking player of a question case and runs
# the book's tools for whoever plays the book (a person or a model given persona.txt, reference.txt and tools.json).
# Usage: tools/book-casebook/standin.sh start SERVER_DIR      start the test server (a few minutes) and dump the prompt
#        tools/book-casebook/standin.sh as CASE_FILE OUT [plain]
#                                                             set up the case's player and world; OUT gets the question
#                                                             (plain: without the player snapshot or design briefs, as
#                                                             the book asked before them, for comparing)
#        tools/book-casebook/standin.sh call TOOL IN OUT      run one tool call (IN holds its JSON input); OUT gets the
#                                                             result, and OUT.png the picture of a draft model
#        tools/book-casebook/standin.sh stop                  stop the server
# The prompt goes to tools/book-casebook/standin/ (git ignores it). Needs Java 21; no API key.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
STATE="$HERE/standin"
mkdir -p "$STATE"
abs() { echo "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"; }

send() { # command, file it writes, seconds
  [ -p "$STATE/console" ] || { echo "The stand-in server isn't running (standin.sh start first)" >&2; exit 1; }
  rm -f "$2"
  echo "$1" > "$STATE/console"
  for _ in $(seq "$3"); do [ -s "$2" ] && return 0; sleep 1; done
  echo "No answer after $3 s; see $(cat "$STATE/server")/logs/casebook-console.log" >&2
  tail -5 "$(cat "$STATE/server")/logs/casebook-console.log" >&2
  exit 1
}

case "${1:-}" in
  start)
    SERVER="$(cd "${2:?give a test server folder}" && pwd)"
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
    rm -f "$STATE/console"; mkfifo "$STATE/console"
    echo "$SERVER" > "$STATE/server"
    mkdir -p logs
    # A sleeping writer holds the fifo open so the server never reads end-of-input between commands.
    nohup sleep infinity > "$STATE/console" 2>/dev/null &
    echo $! > "$STATE/holder"
    nohup ./run.sh nogui < "$STATE/console" > logs/casebook-console.log 2>&1 &
    echo $! > "$STATE/pid"
    echo "Starting the test server (a few minutes)..."
    for _ in $(seq 900); do
      grep -q 'Done (' logs/casebook-console.log 2>/dev/null && break
      kill -0 "$(cat "$STATE/pid")" 2>/dev/null || { echo "The server stopped; see $SERVER/logs/casebook-console.log" >&2; exit 1; }
      sleep 1
    done
    send "fieldguide casebook dump \"$STATE\"" "$STATE/tools.json" 120
    echo "Ready. The book's prompt is in $STATE (persona.txt, reference.txt, tools.json)."
    ;;
  as)
    OUT="$(abs "${3:?give an output file}")"
    send "fieldguide casebook as \"$(abs "${2:?give a case file}")\" \"$OUT\"${4:+ $4}" "$OUT" 120
    cat "$OUT"
    ;;
  call)
    OUT="$(abs "${4:?give an output file}")"
    rm -f "$OUT.png"
    send "fieldguide casebook call ${2:?give a tool name} \"$(abs "${3:?give an input file}")\" \"$OUT\"" "$OUT" 300
    cat "$OUT"
    ;;
  stop)
    [ -p "$STATE/console" ] && echo stop > "$STATE/console" || true
    PID="$(cat "$STATE/pid" 2>/dev/null || true)"
    if [ -n "$PID" ]; then
      for _ in $(seq 120); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
      pkill -P "$PID" 2>/dev/null || true; kill "$PID" 2>/dev/null || true
    fi
    kill "$(cat "$STATE/holder" 2>/dev/null)" 2>/dev/null || true
    rm -f "$STATE/console" "$STATE/pid" "$STATE/holder"
    ;;
  *) sed -n '2,13p' "$0"; exit 1 ;;
esac
