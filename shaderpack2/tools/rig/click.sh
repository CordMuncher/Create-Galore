#!/usr/bin/env bash
# Clicks at a point of the virtual screen: click.sh X Y
export DISPLAY="${DISPLAY:-:99}"
xdotool mousemove "$1" "$2"; sleep 0.6
xdotool mousedown 1; sleep 0.25; xdotool mouseup 1
sleep "${AFTER:-3}"
