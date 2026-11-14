#!/usr/bin/env bash
# Saves what the virtual screen shows: shot.sh OUT.png
export DISPLAY="${DISPLAY:-:99}"
import -window root "$1"
