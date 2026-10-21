<!-- sources sha256:531d8ffff3dce04ffaaecc58f32e9f28c95e0c374402a8b00aa31d3a8e6bccd2 -->
# Survival Layers (survival)
Layers: Temperature (Cold Sweat), Thirst, Seasons (Serene Seasons), Pollution (Create: Smog), food/health, death/graves.

## survival/temperature | Temperature
- Cold Sweat gives body temperature. Hot/cold places push it; too far = damage and cannot sleep. Dress for biome, keep a heat source nearby. Higher is colder.
- Spawn can be snowy: without warm gear you freeze once the 5 minute grace period (see glossary/grace) ends; insulate early (see survival/temperature_gear).
- Thermometer [cold_sweat:thermometer]: shows temperature of the place you stand in. Body temperature has own HUD icon.
- World temperature = what the spot feels like: biome, season, time of day, height/depth, nearby fire, lava, ice, water. Body temperature drifts toward it; armor insulation and warm/cold blocks slow the drift.
- Seasons (see survival/seasons) shift every biome: Mid Summer adds most heat, Mid Winter removes most. Temperate plains: Mid Summer noon ~94 °F, winter night ~40 °F. Comfortable range 45 to 100 °F.
- Cold nights: nights coldest. Bare-skinned on temperate beach in Mid Winter you start freezing after a few minutes. One lit campfire within a few blocks keeps you warm there. Snowy biomes and peaks still need warm clothes (see survival/temperature_gear) or a Hearth. Leather or fur armor slows drift enough to get through; iron armor does not.
- Hot days: deserts/badlands at summer noon >100 °F alone (~115 °F Mid Summer). Stay in shade/underground, keep water handy, or wear heat insulation (see survival/temperature_gear) e.g. Hoglin armor.
- Lit furnace, smoker, blast furnace, campfire, fire are hot: 2 to 3 blocks away in the open adds ~20 °F, inside a room ~7 °F -> summer kitchen best indoors.
- You slowly acclimate to your climate: comfortable range widens by up to ~20 °F.
- Water: getting wet cools you; netherite diving suit makes you immune to that. Rain only soaks so far, even after a swim; under a roof you dry off, faster where warm. Heat and cold can both be lethal.
- Caves/roofs: underground weather and season stop mattering: ~20 blocks down near 60 °F all year, a bit warmer deeper, unless lava near. A roof evens out heat/cold a little: cave or house is good for waiting out a winter night. The Aether is mild too.
- Sleep: cannot sleep while overheating or freezing. Grace: after spawn or death, short immunity to temperature damage.
- Altitude: higher is colder. Pack adds Cold Sweat: Altitude (see survival/temperature_create): splits height range into bands changing temperature, chill message in sky, shelter or sewn Windproof Lining cuts the effect. Bites on mountains, sky islands, airships.
- Body temperature moves at 0.6 speed (Cold Sweat's rate setting); comfortable down to ~45 °F. Too hot or too cold: each hit does half a heart every 2 seconds.
- Seasons shift air temperature: Mid Winter ~13.5 °F colder, Early/Late Winter ~9 °F colder; Summer warmer by the same amounts; start of spring and end of autumn ~4.5 °F colder; end of spring and start of autumn ~4.5 °F warmer.
- Space: Cold Sweat covers Overworld and Aether only. Northstar's planets have own temperature system (set by each world + Temperature Regulator block), separate from Cold Sweat, one link: space suits count as warm clothing (suit keeps you warm on a cold night back home). Martian Steel suit keeps out heat too. (see space/planets)
- Thirst now works with Cold Sweat (see survival/thirst).
- Related: (see survival/temperature_gear), (see survival/temperature_create), (see create_basics/heat), (see survival/thirst), (see space/planets)

## survival/temperature_gear | Insulation and Heaters
- Armor is main defence: take any armor to a Sewing Table and sew insulation on. Insulators (Goat Fur, Hoglin Hide) have own item tab. JEI (see start_here/tools) has Sewing Recipes category.
- Sewing Table [cold_sweat:sewing_table]: crafted from wool and planks. Adds insulation from items like Goat Fur to armor, held as cold or heat resistance.
- Goat Fur and Hoglin Hide come from goats and hoglins. Finished sets: Goat Fur, Hoglin, Chameleon Scale armor. Hover a piece to read its insulation. Chameleon = Cold Sweat's tameable lizard, sheds scales for a set.
- Goat Fur Parka [cold_sweat:goat_fur_chestplate]: ready-made set piece; insulation in tooltip.
- Blocks: Hearth keeps area around it comfortable, takes fuel (Hearth Fuel category in JEI). Boiler warms, Icebox cools. Put a Smokestack on top to make an area livable.
- Hearth [cold_sweat:hearth]: fuel it, put in enclosed room. Switches on by itself when someone inside is too cold (or too hot, with cold fuel), burns fuel only then; no redstone needed. Boiler and Icebox work the same. Fuels and range in JEI.
- Waterskin [cold_sweat:waterskin]: Cold Sweat's drink container; tooltip shows keys to drink/pour. Create Spout can fill it. Carries Thirst (see survival/thirst) purity: purify in furnace or campfire.
- Full Create netherite diving set (see ocean/diving) (netherite backtank, diving helmet, netherite leggings, diving boots): adds heat dampening and shrugs off wet effect; backtank tooltip shows the hint.
- Related: (see survival/temperature), (see survival/temperature_create), (see create_basics/heat)

## survival/temperature_create | Create and Altitude Heat
- Create: Cold Sweat makes Create machines change temperature of nearby players; working factory heats the room. Heat idea: (see create_basics/heat).
- Blaze Burner [create:blaze_burner]: warms nearby players; hotter flame adds more: smouldering, fading, kindled, seething.
- Also counted: Steam Engines, Encased Fans, Fluid Tanks, Basins, pipes, pumps. Working steam boiler (see glossary/boiler) radiates more as level rises. Lava in a container gives heat; water neutral. Pipes/tanks matter only when holding something hot or cold.
- Encased Fan [create:encased_fan]: can take its temperature from the block it faces: fan blowing out of fire/lava can heat a room.
- Each effect has max range: need to be fairly close.
- Altitude: Cold Sweat: Altitude splits sky into temperature bands by Y (see glossary/y_level), set in its own config file (see glossary/config). Messages: "The mountain air grows colder." from Y 128; "The air grows colder at this altitude." from Y 192; "The air grows thin and bitter." from Y 256. Below ground Cold Sweat's steady cave temperatures apply instead.
- Watch out: open sky harsh. On open deck above Y 192 you freeze in under a minute without insulation; leather buys ~2 minutes. Closed cabin with roof and walls stops the chill completely, on ground or ship.
- Airships: altitude also knows Create Aeronautics ships. Burners and steam vents aboard add heat; enclosed ship shelters from chill (see flight/airships).
- Protection (in depth): most chill up high is wind. Windproof Lining (four leather around a phantom membrane) sews onto armor at a Sewing Table; one on every piece removes most wind chill. The band's own cold stays. Roof and walls, or enclosed Aeronautics ship, cut it too.
- Related: (see survival/temperature), (see survival/temperature_gear), (see flight/airships), (see create_basics/heat)

## survival/thirst | Thirst
- Thirst Was Reclaimed adds thirst bar beside hunger. Drains as you play; nothing to drink = eventually die of dehydration. Hidden buffer "quenched", like saturation (see glossary/saturation) for drinks; a good drink fills it.
- At 3 droplets or less you can't sprint. At zero you lose half a heart every 2 seconds until you drink; on Normal that can kill you.
- Thirst doesn't touch healing: health returns from food and saturation (see glossary/saturation) exactly as vanilla, however low water is; healing doesn't use up water.
- How to drink: Rain: under open sky, look straight up; hands can hold anything; snow does not count. By hand: both hands empty, crouch and right-click water. Bottles: fill a glass bottle at any water, hold right-click to drink. Short version: (see start_here/first_days).
- Water purity: Dirty, Slightly Dirty, Acceptable, Purified. Watch out: still water at sea level is Dirty: always makes you sick, sometimes poisons. Slightly Dirty = coin flip. Acceptable and Purified safe. Bottles, buckets, bowls of water all carry purity.
- Boiling raises purity one step at a time. Campfire: one step per cook. Furnace faster: dirty -> Acceptable straight, next cook -> Purified. Works on water bottles, water buckets, terracotta water bowls.
- Campfire [minecraft:campfire]: cooking water bottles/buckets/bowls raises purity one step per cook. Slow, free.
- Sand Filter [thirst:sand_filter]: pump dirty water in from top, purified out of bottom, one step per pass. Recipe: sand over a Nozzle over a Fluid Tank.
- Create filter: Sand Filter is where thirst meets Create: build with Nozzle + Fluid Tank, run with a pump (see create_basics/fluids). Automatic way to supply a base.
- Terracotta Water Bowl [thirst:terracotta_water_bowl]: reusable. Clay bowls bake into terracotta bowls; a Spout fills one with 250 mB (see glossary/mb) water.
- Tip: Create Compacting (see glossary/compacting) recipe squeezes a Cactus into purified water (250 mB) and green dye; good for desert bases.
- Fruits Delight juices count as drinks. Farmer's Delight melon juice and apple cider hydrate like a water bottle.
- Also hydrate like a water bottle: Create Cafe milk teas and iced coffees; Garnished sweet tea; hot chocolate (Confectionery and Alex's Caves); Brewin' And Chewin' beer from a keg.
- Cold Sweat (in depth): Thirst and Cold Sweat (see survival/temperature) work together; Waterskin (see survival/temperature_gear) carries purity like a bottle, furnace/campfire purifies it the same.
- Related: (see survival/temperature), (see survival/temperature_gear), (see survival/food_and_health), (see create_basics/fluids), (see food/farmers_delight)

## survival/seasons | Seasons
- Serene Seasons: Overworld year = Spring, Summer, Autumn, Winter, each Early/Mid/Late. Each sub-season 8 days -> season 24 days, year 96. Foliage colors follow season.
- Calendar [sereneseasons:calendar]: tooltip shows current season and day. Crafted from a Clock and paper. Handy for planning plantings.
- Weather: winter brings snow and ice; lakes/rivers in cold places can freeze. Oceans never freeze except frozen oceans (boats always reach the sea). Rain/snow/storm frequency vary by season. Tropical biomes (deserts, badlands, savanna, jungle, similar) have Wet and Dry seasons instead.
- Big rule: crops only grow in right season; out of season grow slowly. A config (see glossary/config) setting can make them stop or break instead. Tooltips may list crop seasons. Winter hardest: very few crops grow (see survival/seasons_crops).
- Greenhouses: plants under a glass roof count as in season, via greenhouse glass tag (see glossary/tags) (ordinary glass blocks). Crops deep underground are also fertile, below a height set in the config.
- Season Sensor [sereneseasons:season_sensor]: redstone signal depending on season, like a Daylight Detector.
- Seasonal Integration (in depth): datapack (see glossary/datapack) teaching Serene Seasons about other mods: adds crops to season lists, sorts biomes into tropical or off-limits.
- Related: (see survival/seasons_crops), (see food/farmers_delight), (see survival/temperature), (see exploration/dimensions)

## survival/seasons_crops | Seasonal Crops
- Serene Seasons lists vanilla crops by season: wheat summer+autumn, carrots spring+autumn, etc. Mushrooms, nether wart, oak saplings grow year round.
- Farmer's Delight (see food/farmers_delight) season data: Tomato: summer; Cabbage: autumn, winter; Onion: spring, autumn; Rice: summer, autumn.
- Tomato Seeds [farmersdelight:tomato_seeds]: summer crop; out of season grows only slowly; glass roof keeps patch at full speed.
- Watch out: bone meal on out-of-season crop is used up but works only ~1 time in 6; save for in-season crops.
- Cabbage Seeds [farmersdelight:cabbage_seeds]: autumn and winter crop; one of few things growable in cold.
- Fruits Delight trees: Spring: pear, peach, blueberry; Summer: lychee, mango, durian, lemon, pineapple; Autumn: apple, orange, fig, cranberry; Winter: persimmon, kiwi, hawberry.
- Other mods: Seasonal Integration adds Berry Good's sweet berries (spring, winter) and glow berries, and My Nether's Delight peppers, to year round. Fungus Delight mushrooms grow in any season.
- Pack additions (crops with no season data used to grow all year; pack gives real seasons): Coffee: summer; Cassava: summer, autumn; Bok choy: spring, autumn; Cotton: summer, autumn.
- Berries and Cherries bushes/trees follow seasons, also when regrowing fruit: Strawberry, cherry: spring, summer; Blueberry: summer; Raspberry, grapes: summer, autumn.
- Garnished nut trees grow from planted Cracked nuts: Almond: spring, summer; Cashew, pistachio: summer; the rest: summer, autumn.
- Coffee Beans [createcafe:coffee_beans]: summer crop. Tropical biomes (jungle, savanna, desert, similar) grow summer crops all year -> coffee, cassava, cashews do best there. Snowy biomes grow only winter crops.
- Related: (see survival/seasons), (see food/farmers_delight), (see food/kitchen_automation), (see food/first_food)

## survival/pollution | Pollution
- Create: Smog tracks air pollution per chunk (see glossary/chunk). Anything that burns adds smog to its chunk. Spreads to neighbours, fades slowly, faster in rain. Trees, logs, water, plants absorb it.
- Pressure, not a wall: few furnaces barely register; big industrial base left alone gets bad. Fix built from Create parts, cleaning is part of machine room (see survival/pollution_control).
- Smog per second by source: Campfire 1; Stove (Farmer's Delight) 2; Furnace, smoker 4; Blast furnace 8; Blaze Burner 16, superheated 32; Blaze Burner with Straw: same.
- Blaze Burner [create:blaze_burner]: pollutes while burning fuel, common factory source; no fuel = clean (see create_basics/heat).
- Watch out: heavy pollution gives Nausea; Toxic adds Poison and Weakness. Land suffers: crops lose growth, flowers and grass die, grass -> dirt, in toxic air farmland too.
- Aerometer [smog:aerometer]: hold to read air where you stand. Levels: Clean, Light Haze, Polluted, Heavily Polluted, Toxic.
- Level thresholds by chunk value (in depth): Light Haze 400; Polluted 1,800; Heavily Polluted 4,500; Toxic 8,000; cap 10,000.
- Other machines: any machine reporting a "lit" state adds 3 per second. TFMG (see industry/tfmg_engines) machines pollute only while burning fuel: engines 6 per block at full throttle (large 4), fireboxes 8, blast furnaces 30; idle ones clean. Aerometer shows real result.
- Related: (see survival/pollution_control), (see industry/liquid_fuel), (see industry/tfmg_oil), (see create_basics/heat)

## survival/pollution_control | Clearing the Air
- Three tools: stop at source (Flues), scrub (Air Scrubbers), shield yourself (Respirator); mix them. Tall Flue stack + a few scrubbers keep an average base clear.
- Flue [smog:flue]: stack above furnace/burner; each block sends more output high and wide instead of into the chunk below, up to 80% total.
- Air Scrubber [smog:air_scrubber]: kinetic (see glossary/kinetic_network): needs rotation (see create_basics/rotation) and a Filter Cartridge, and stress (see create_basics/stress). Cleans own chunk and the four beside it, faster the faster it spins. Funnels and chutes can feed cartridges; a water pipe wets the filter for a bonus. Works on a moving contraption, a bit slower.
- Filter Cartridge [smog:filter_cartridge]: basic filter. Brass one holds 4x as much, Precision one 16x. Spent ones become clogged cartridges.
- Washing: clogged cartridges cleaned by a Fan (see create_basics/fans) blowing through water; cartridge returns, sometimes Soot. Soot burns as fuel, makes black dye, or compacts (see glossary/compacting) to Graphite and a Block of Soot.
- Respirator [smog:respirator]: wear on head or in an accessory slot. Keeps smog out of you, slowly clears what you breathed in. Wears down as it works.
- Panels (in depth): scrubbers side by side share one cartridge and work as a panel, more output for more stress. Flat 5x5 of 25 reaches upward to break the cloud above.
- Upgrades: Precision Cartridge uses Electron Tubes [create:electron_tube] and Graphite, so best filter sits behind brass and assembly (see create_basics/brass_and_assembly). Filter Medium [smog:filter_medium], pressed from soot and string, replaces wool in the basic cartridge.
- Related: (see survival/pollution), (see create_basics/fans), (see create_basics/stress), (see create_basics/rotation)

## survival/food_and_health | Food and Health
- AppleSkin shows what food does: food tooltips list hunger and saturation (see glossary/saturation); hunger bar shows how much a held item would restore; saturation appears as overlay. Useful with Farmer's Delight's larger food list (see food/farmers_delight).
- Food Values [minecraft:golden_apple]: AppleSkin adds hunger/saturation values to food tooltips; hold food to preview refill on bar.
- Thirst has own bar and never stops you healing (see survival/thirst). Seasons decide what you can grow (see survival/seasons). Cold Sweat (see survival/temperature) and Smog (see survival/pollution) add own hazards. Early food by area: (see food/first_food).
- Survival layers: (see survival/thirst), (see survival/seasons), (see survival/temperature), (see survival/pollution), (see food/farmers_delight)

## survival/death_and_graves | Death and Graves
- Gravestone [gravestone:gravestone]: on death, GraveStone puts a grave where you fell, storing inventory, armour, offhand and Curios slots (backpack, charms). Nothing drops on ground, so nothing burns/despawns. Break the grave: every item goes back where it was. Aether gloves and pendants return as normal items; put on again with comma. Anyone can break it, so a friend can fetch it.
- The Latest Death waypoint on your map leads you back.
- You also get an Obituary (paper: how and where you died); disappears when you break the grave.
- Where graves land: Void (falling out of world, as in the End): just above bottom of the world, in the rock; dig down. Lava: in the lava where you died; bring Fire Resistance or drain it first. Water: on the spot, even deep underwater. On a ship: in the air where you died; ship flies on, grave stays; follow Latest Death waypoint and build up to it. Another planet: on that planet where you died; you wake on Earth, need a rocket to fetch it.
- Tip: Everlasting Upgrade keeps a backpack safe from fire, lava, void if it lies on the ground (e.g. after dropping it) (see home/storage).
- Server keeps regular world backups (see start_here/playing_together).
