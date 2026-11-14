#!/usr/bin/env bash
# Stops the game (not the virtual screen).
pkill -f 'net.neoforged|cpw.mods.bootstraplauncher|fml.loading' || true
