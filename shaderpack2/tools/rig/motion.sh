#!/usr/bin/env bash
# Takes pictures while the camera moves, to see what a still can't (trails behind edges, patterns that crawl or
# flicker, light that lags): motion.sh OUT_PREFIX "STEP" [SECONDS, default 14]
# STEP is what a tp adds every game tick, from where the player is: "~ ~ ~ ~0.04 ~" turns the camera 0.8 degrees
# a second, "~0.01 ~ ~" moves it 0.2 blocks a second. The rig draws a frame every second or two, so a frame sees
# about what 25 ticks add up to: 0.04 a tick is a fast turn (a degree a frame), 0.01 a tick fast flight (a quarter
# block a frame). A repeating command block high over the test world does the moving (BLOCK, default "-430 250 430"),
# and is taken away again at the end. Writes OUT_PREFIX_before.png (still), _moving1.png, _moving2.png (two frames
# on the way) and _after.png (still again, SETTLE seconds after it stops, default 40). Go to the view first (view.sh).
HERE="$(cd "$(dirname "$0")" && pwd)"
BLOCK="${BLOCK:--430 250 430}"
"$HERE/shot.sh" "$1_before.png"
DELAY=60 "$HERE/cmd.sh" "setblock $BLOCK repeating_command_block{auto:1b,Command:\"execute as @p at @s run tp @s $2\"}"
sleep "${3:-14}"
"$HERE/shot.sh" "$1_moving1.png"
sleep 4
"$HERE/shot.sh" "$1_moving2.png"
DELAY=60 "$HERE/cmd.sh" "setblock $BLOCK air"
sleep "${SETTLE:-40}"
"$HERE/shot.sh" "$1_after.png"
