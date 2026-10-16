# Iron's Spells only applies its spell configs (Portal, Recall and Angel Wings are off in this pack)
# after a /reload, not on the first datapack load. So reload once, 5 seconds after the world opens.
# The reload runs this file again; #age is then about 100 ticks, so it doesn't loop.
scoreboard objectives add cg.boot dummy
execute store result score #now cg.boot run time query gametime
execute unless score #last cg.boot matches -2147483648.. run scoreboard players set #last cg.boot -1000
scoreboard players operation #age cg.boot = #now cg.boot
scoreboard players operation #age cg.boot -= #last cg.boot
execute if score #age cg.boot matches 200.. run function create_galore:boot/schedule
