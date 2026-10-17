# Runs on every world load, but sets each default only once per world, so a host's own change sticks.
# One player in bed is enough to skip the night (vanilla needs everyone).
execute unless data storage create_galore:world_defaults {sleep:1b} run gamerule playersSleepingPercentage 1
data modify storage create_galore:world_defaults sleep set value 1b
