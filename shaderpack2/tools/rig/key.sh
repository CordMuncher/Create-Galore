#!/usr/bin/env bash
# Sends keys to the game: key.sh F1   |   key.sh --type "text"   (slow enough for a frame or two a second)
export DISPLAY="${DISPLAY:-:99}"
if [ "$1" = "--type" ]; then
  xdotool type --delay "${DELAY:-120}" "$2"
else
  for k in "$@"; do xdotool key --delay 200 "$k"; sleep "${GAP:-0.5}"; done
fi
