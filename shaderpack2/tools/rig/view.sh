#!/usr/bin/env bash
# Moves the camera and takes a picture: view.sh OUT.png X Y Z YAW PITCH [seconds to settle, default 12]
# (the player in spectator mode, the HUD hidden with F1 beforehand).
HERE="$(cd "$(dirname "$0")" && pwd)"
"$HERE/cmd.sh" "tp @s $2 $3 $4 $5 $6"
sleep "${7:-12}"
"$HERE/shot.sh" "$1"
