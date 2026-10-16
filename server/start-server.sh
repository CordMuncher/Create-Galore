#!/usr/bin/env sh
# Starts the Create Galore server. Type stop to save and shut down.
# First it checks for a newer pack and asks whether to update (no / yes / back up the world first).
# Arguments go on to the server, e.g. ./start-server.sh nogui
cd "$(dirname "$0")"
if [ -f create-galore-updater.jar ]; then java -jar create-galore-updater.jar --server-start || exit; fi
exec ./run.sh "$@"
