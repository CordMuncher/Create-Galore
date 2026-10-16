# Safety net for Elytra from sources the datapack can't reach (mod code, old chunks, commands).
# Runs once each time an Elytra lands in a player's inventory or armor slot. Creative players keep theirs.
advancement revoke @s only create_galore:elytra_picked_up
scoreboard objectives add cg.elytra dummy
scoreboard players set @s cg.elytra 0
execute unless entity @s[gamemode=creative] store result score @s cg.elytra run clear @s minecraft:elytra
execute if score @s cg.elytra matches 1.. run tellraw @s {"text":"Elytra are turned off in Create Galore, so this one crumbled to Phantom Membrane. Build an airship, a train or a jetpack instead. (Server owners can turn Elytra back on, see the README.)","color":"gold"}
execute if score @s cg.elytra matches 1.. run give @s minecraft:phantom_membrane 2
