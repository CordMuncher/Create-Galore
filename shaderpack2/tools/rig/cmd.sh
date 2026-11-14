#!/usr/bin/env bash
# Types one chat command into the game: cmd.sh "time set 6000" (without the slash). Opens the chat with "/", waits
# for the screen to open (WAIT seconds, default 4: at a frame a second, keys typed before it opens leak into the
# game as movement), types, and presses Enter.
export DISPLAY="${DISPLAY:-:99}"
xdotool key slash
sleep "${WAIT:-4}"
xdotool type --delay "${DELAY:-100}" "$1"
sleep 1
xdotool key Return
sleep "${AFTER:-2}"
