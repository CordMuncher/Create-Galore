<!-- sources sha256:ec284e7a51e8a066deaf5e7496cb1a17b8e2275f3f542b5f50d23fa085a749ef -->
# Survival Layers (survival)
Layers: Temperature (Cold Sweat), Thirst, Seasons (Serene Seasons), Pollution (Pollution of the Realms smog, exhaust systems), food/health, death/graves.

## survival/temperature | Temperature
- Cold Sweat gives body temperature. Hot/cold places push it; too far = damage and cannot sleep. Dress for biome, keep a heat source nearby. Higher is colder.
- Spawn can be snowy: without warm gear you freeze once the 5 minute grace period (see glossary/grace) ends; insulate early (see survival/temperature_gear).
- Thermometer [cold_sweat:thermometer]: shows temperature of the place you stand in. Body temperature has own HUD icon.
- World temperature = what the spot feels like: biome, season, time of day, height/depth, nearby fire, lava, ice, water. Body temperature drifts toward it; armor insulation and warm/cold blocks slow the drift.
- Seasons (see survival/seasons) shift every biome: Mid Summer adds most heat, Mid Winter removes most. Temperate plains: Mid Summer noon ~94 °F, winter night ~40 °F. Comfortable range 45 to 100 °F.
- Cold nights: nights coldest. Bare-skinned on temperate beach in Mid Winter you start freezing after a few minutes. One lit campfire within a few blocks keeps you warm there. Snowy biomes and peaks still need warm clothes (see survival/temperature_gear) or a Hearth. Leather or fur armor slows drift enough to get through; iron armor does not.
- Hot days: deserts/badlands at summer noon >100 °F alone (~115 °F Mid Summer). Stay in shade/underground, keep water handy, or wear heat insulation (see survival/temperature_gear) e.g. leather armor sewn with Rabbit Hide.
- Lit furnace, smoker, blast furnace, fire are hot: 2 to 3 blocks away adds ~20 °F, same indoors and outdoors (walls/roof don't block it). Mid Summer noon: a furnace 2 blocks away takes a temperate beach from 79 °F to ~102 °F, past the 100 °F where heat hurts -> work a few blocks away in summer.
- Campfires: gentler than a furnace: ~12 °F warmer 2 blocks away, fades out over 7 blocks; two campfires ~24 °F; any number caps at 30 °F. Mid Winter night on temperate beach: one campfire within ~4 blocks keeps you comfortable.
- You slowly acclimate to your climate: comfortable range widens by up to ~20 °F.
- Water: getting wet cools you; netherite diving suit makes you immune to that. Rain only soaks so far, even after a swim; under a roof you dry off, faster where warm. Heat and cold can both be lethal.
- Caves/roofs: underground weather and season stop mattering: ~20 blocks down near 60 °F all year, a bit warmer deeper, unless lava near. A roof evens out heat/cold a little: cave or house is good for waiting out a winter night. The Aether is mild too.
- Sleep: cannot sleep while overheating or freezing. Grace: after spawn or death, short immunity to temperature damage.
- Altitude: higher is colder. Pack adds Cold Sweat: Altitude (see survival/temperature_create): splits height range into bands changing temperature, chill message in sky, shelter or sewn Windproof Lining cuts the effect. Bites on mountains, sky islands, airships.
- Body temperature moves at 0.6 speed (Cold Sweat's rate setting); comfortable down to ~45 °F. Too hot or too cold: each hit does half a heart every 2 seconds.
- Seasons shift air temperature: Mid Winter ~13.5 °F colder, Early/Late Winter ~9 °F colder; Summer warmer by the same amounts; start of spring and end of autumn ~4.5 °F colder; end of spring and start of autumn ~4.5 °F warmer.
- Cold Sweat covers Overworld, Nether, End, Aether, Twilight Forest. Nether is hottest of all (see survival/nether_heat).
- Space: Northstar's planets have own temperature system (set by each world + Temperature Regulator block), separate from Cold Sweat, one link: space suits count as warm clothing (suit keeps you warm on a cold night back home). Martian Steel suit keeps out heat too. (see space/planets)
- Thirst now works with Cold Sweat (see survival/thirst).
- Related: (see survival/temperature_gear), (see survival/nether_heat), (see survival/temperature_create), (see create_basics/heat), (see survival/thirst), (see space/planets)

## survival/temperature_gear | Insulation and Heaters
- Armor is main defence: take any armor to a Sewing Table and sew insulation on. Insulators (Goat Fur, Hoglin Hide) have own item tab. JEI (see start_here/tools) has Sewing Recipes category.
- Sewing Table [cold_sweat:sewing_table]: crafted from wool and planks. Adds insulation from items like Goat Fur to armor, held as cold or heat resistance.
- Goat Fur and Hoglin Hide come from goats and hoglins. Rabbit Hide and Leather sew on too: Rabbit Hide = best heat insulator outside the Nether, tied with Chameleon Molt once it has turned toward heat. Finished sets: Goat Fur, Hoglin, Chameleon Scale armor. Hover a piece to read its insulation. Chameleon = Cold Sweat's tameable lizard, sheds scales for a set.
- Goat Fur Parka [cold_sweat:goat_fur_chestplate]: ready-made set piece; insulation in tooltip.
- Blocks: Hearth keeps area around it comfortable, takes fuel (Hearth Fuel category in JEI). Boiler warms, Icebox cools. Put a Smokestack on top to make an area livable.
- Hearth [cold_sweat:hearth]: fuel it, put in enclosed room. Switches on by itself when someone inside is too cold (or too hot, with cold fuel), burns fuel only then; no redstone needed. Boiler and Icebox work the same. Fuels and range in JEI.
- Waterskin [cold_sweat:waterskin]: Cold Sweat's drink container; tooltip shows keys to drink/pour. Create Spout can fill it. Carries Thirst (see survival/thirst) purity: purify in furnace or campfire.
- Full Create netherite diving set (see ocean/diving) (netherite backtank, diving helmet, netherite leggings, diving boots): adds heat dampening and shrugs off wet effect; backtank tooltip shows the hint.
- Related: (see survival/temperature), (see survival/nether_heat), (see survival/temperature_create), (see create_basics/heat)

## survival/nether_heat | Nether Heat
- Nether air 130 to 180 °F; lava within 7 blocks adds up to 200 °F more, portal beside a lava lake can read over 250 °F. About 40 °F cooler while it is night in the Overworld.
- Bare: body overheats in 15 to 45 seconds; heat stroke hurts faster the hotter it is. Iron, diamond, netherite armor carry no heat insulation of their own; only sewn or furred armor slows heat.
- Rabbit Hide [minecraft:rabbit_hide]: best heat insulator outside the Nether, tied with Chameleon Molt once it has turned toward heat. Sew into leather armor at Sewing Table (see survival/temperature_gear): 19 hides fill a full set.
- Before first trip: leather full of Rabbit Hide lasts ~17 minutes in 166 °F air, ~6 minutes beside lava before overheating. Plain leather under 3 minutes.
- Ice: Packed Ice and Blue Ice cool the air around them, neither melts. 4 Blue Ice + 4 Packed Ice two blocks around you: 166 °F -> 86 °F (safe spot by portal). Beside lava 262 °F -> ~150 °F.
- Water: Waterskin chilled in an Icebox cools you from the hotbar until it warms up (~a minute); drinking a cold one cools at once (see survival/temperature_gear).
- Safe ground: deep inside a Soul Sand Valley ~85 to 100 °F, standable. Tip: Fire Resistance stops heat damage while it lasts (body still heats). Brewing needs Nether ingredients; some Overworld dungeon chests hold the potion.
- Once in: hoglins drop Hoglin Hide. Hoglin set ~8 minutes alone, longer with Rabbit Hide sewn in. Soul Sprouts grow in Soul Sand Valleys: eating one cools you; they fuel the Soulspring Lamp, which cools you in the Nether.
- In depth: 40 points of heat insulation = heat up ten times slower. Leather armor 23, each sewn Rabbit Hide 1.5, Leather 1, Hoglin Hide 2, Hoglin set 46. Chameleon armor and Molt turn their insulation toward heat in about 2 minutes, reaching three quarters of their value. Times taken at Overworld noon, the Nether's hottest.
- Related: (see survival/temperature), (see survival/temperature_gear), (see exploration/nether)

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
- Smog is real gas (Pollution of the Realms): blocks you see and walk through. Kinds: grey carbon, yellow sulfur, dust. Fresh smoke rises until a roof or the cloud layer stops it (carbon Y191, sulfur Y190), settles there. Under a roof it fills the room from the top down; outside it gathers over the chunk that made it (see glossary/chunk).
- What pollutes: furnace, smoker, blast furnace: 1 carbon + 0.1 sulfur per coal burned (other fuels own amounts). Blaze Burner, steam boiler, Farmer's Delight stove: ~1 carbon a minute while fuelled. Campfire: a little now and then. Crushing Wheels ~4 dust a minute while working, Millstone 1.
- Looking at a gas block shows its kind and density (light/medium/heavy = 1-3 units); crouch to look through it.
- Bulk smelting: Encased Fan (see create_basics/fans) through lava or Blaze Burner makes no smog by itself; each finished item puffs smog out where it is: 1 carbon per item blasted + 1 sulfur per 5; 1 carbon per item smoked (fire or campfire); 64 ores = 64 carbon. Washing and haunting clean.
- Engines: TFMG (see industry/tfmg_engines) engines, coke ovens, blast furnaces pollute for fuel burned; idle = clean.
- The cloud: chunk pollution = gas in the cloud layer above it. Gas block holds 1 to 3 units (light, medium, heavy). Carbon measured against 512 units per chunk, sulfur 128; effects start at a share of that.
- Watch out, under carbon cloud: 30% Mining Fatigue, 40% Hunger, 50% Weakness, 60% Slowness, 70% Poison, 80% Nausea, 90% Wither. Under sulfur cloud: 30% Blindness, 60% Poison, 90% Wither. Standing inside gas blinds; sulfur also poisons. Respirator keeps it out.
- Acid rain: sulfur in air and rain kills crops, berry bushes, sugar cane, bamboo, ferns, bee nests; grass, moss, farmland -> podzol; pollutes water. Carbon in rain makes grass/plants grow. Stone and builds never harmed.
- Wind pushes smog sideways a few blocks at a time; small base smoke drifts off. Smog never fades on its own: factory cloud keeps growing until exhaust is filtered.
- Aerometer [adpother:aerometer]: needle shows smog around you, beeps near gas; item frame = alarm.
- Watch out, explosions: machine whose smoke has nowhere to go (sealed room full of gas) blows up after a few tries and breaks (see survival/smog_pressure). Fire, torches, lava never set smog off.
- Related: (see survival/pollution_control), (see survival/smog_pressure), (see industry/liquid_fuel), (see industry/tfmg_oil), (see create_basics/heat)

## survival/pollution_control | Exhaust Systems
- Key: Vents pull smog into Ducts, a Pump pushes it up and out; pump runs from an Encased Fan under it; Filter Frames on the way out catch every kind of smog and let out harmless exhaust, nothing to refill; filtered exhaust must reach the open sky. Parts: Advanced Chimneys chimneys, vents, ducts, pumps + Filter Frames.
- Chimney [adchimneys:metal_chimney]: stone, brick, metal, glass chimneys carry smoke up. One on top of a furnace/burner takes its smoke straight in. Smoke leaving the top unfiltered still joins the cloud.
- Vent [adchimneys:metal_vent]: intake inside the room; pulls in smog up to 4 blocks away; join to pump with ducts. Stone version same.
- Duct [adchimneys:duct]: carries smog from vents/machines to a pump, up to 256 blocks. Joins a vent beside or above it, not below. Duct touching a machine takes its smoke straight in.
- Pump [adchimneys:metal_pump]: runs while an Encased Fan (see create_basics/fans) right under it turns >= 16 RPM, blowing up or down; the fan's stress (see create_basics/stress) is the pump's cost (a bare shaft does not run it). Pushes smog upward only, into the chimney or frames above.
- Filter Frame [adpother:iron_filter_frame]: stack on the pump's outlet. Every frame catches all three smogs (carbon, sulfur, dust), needs nothing put in it. Iron filters 2 units a second, gold 4, diamond 8. Right-click a frame to see its rate.
- Respirator filters: craft a Respirator together with a filter material to pick what it stops (units each): Leaves: carbon 8; Wool: sulfur 8; Paper: dust 8; Filter Cartridge [smog:filter_cartridge]: carbon 32; Brass Filter Cartridge: carbon + sulfur 64; Precision Filter Cartridge: all three 128. A clogged cartridge comes clean with a Fan through water, sometimes Soot. Soot burns as fuel, makes black dye, or compacts (see glossary/compacting) to Graphite and Block of Soot.
- Busy stacks: a frame never fills up or runs out. Smog arriving faster than a stack filters waits below until it can pass; nothing slips through unfiltered. Filter faster with more frames or gold/diamond ones; two iron frames = one gold. A pump moves about 1.5 units a second whatever its speed, so one iron frame keeps up with it; gold/diamond pay off in a sealed room's roof under pressure (see survival/smog_pressure).
- Mixed smog: every frame catches carbon, sulfur and dust alike, so one stack handles mixed smog.
- Filtered exhaust: what a filter lets out; pale haze, harmless, not pollution. Rises, slips up through chimneys/frames, drifts along ceilings; under open sky thins away in a couple of seconds. Watch out: not air. Filter with closed top takes nothing; exhaust trapped indoors stays, fills the room, smog can't move into it. Give every filter a way up to the sky.
- Respirator [adpother:iron_respirator]: worn on head, keeps smog out of lungs, wears down. Craft with filter material to choose what it stops. Gold/diamond last longer. Create Backtanks and Create Big Cannons gas mask protect too.
- Vacuum Tube [adpother:iron_vacuum_tube]: hold use to suck gas in front of you into Vacuum Bags in inventory; without a bag blows it behind you.
- In depth: hopper on a pump pulls smog from 8 blocks around, 3 up. Encased Fans push smog blocks along their air stream. Old Air Scrubbers and Flues no longer clean or vent anything; decoration.
- Related: (see survival/pollution), (see survival/smog_pressure), (see create_basics/fans), (see create_basics/stress)

## survival/smog_pressure | Pressure and Sealed Casings
- Smog shut in a sealed space is forced up through Filter Frames in its roof, no pump needed; fuller space = faster. Sealed = full blocks, no open side, no gap to sky, fewer than 2,048 blocks of room inside.
- Rate: each roof frame takes smog as fast as it filters (iron 2 units a second, gold 4, diamond 8); nearly empty space only seeps; packed one hits the limit. Frame top needs a way to the sky (open air or chimney).
- Belts and gaps: smog leaks through anything without a full face: bare belts, shafts and pipes, fences, carpets, trapdoors. Andesite or Brass Casing on a belt where it passes the wall seals it: items ride through, smog stays in.
- Shafts and pipes: Andesite or Brass Casing on a shaft where it passes the wall (Encased Shaft), Copper Casing on a fluid pipe (Encased Fluid Pipe): power and fluid go through, smog stays in. Gearboxes, Encased Chain Drives, clutches, gearshifts, encased cogwheels and Encased Fans are whole blocks and seal like stone. A shaft through a Metal Girder still leaks.
- Sealed smelter: 1. fan, lava (or Blaze Burner) and belt inside a box of full blocks; 2. casing on the belt where it goes in and out, and on any shaft or pipe through the wall; 3. Filter Frame in the roof; 4. open sky or chimney above the frame. Each item smelted sends smog up through the filter.
- Watch out, overpressure: a sealed space that fills up has no room for more smoke. Furnace or other machine in it blows up after a few tries and breaks. Fan smelting into it blows the casing open (as hard as a Creeper) after 4 batches that find no room.
- Related: (see survival/pollution_control), (see survival/pollution), (see create_basics/fans), (see create_basics/item_logistics)

## survival/food_and_health | Food and Health
- AppleSkin shows what food does: food tooltips list hunger and saturation (see glossary/saturation); hunger bar shows how much a held item would restore; saturation appears as overlay. Useful with Farmer's Delight's larger food list (see food/farmers_delight).
- Food Values [minecraft:golden_apple]: AppleSkin adds hunger/saturation values to food tooltips; hold food to preview refill on bar.
- Thirst has own bar and never stops you healing (see survival/thirst). Seasons decide what you can grow (see survival/seasons). Cold Sweat (see survival/temperature) and Smog (see survival/pollution) add own hazards. Early food by area: (see food/first_food).
- Survival layers: (see survival/thirst), (see survival/seasons), (see survival/temperature), (see survival/pollution), (see food/farmers_delight)

## survival/death_and_graves | Death and Graves
- Gravestone [gravestone:gravestone]: on death, GraveStone puts a grave where you fell, storing inventory, armour, offhand and accessories (backpack, rings, charms). Nothing drops on ground, so nothing burns/despawns. Break the grave: every item goes back where it was. Aether gloves and pendants return as normal items; put on again in the Accessories screen (G). Anyone can break it, so a friend can fetch it.
- The Latest Death waypoint on your map leads you back.
- You also get an Obituary (paper: how and where you died); disappears when you break the grave.
- Where graves land: Void (falling out of world, as in the End): just above bottom of the world, in the rock; dig down. Lava: in the lava where you died; bring Fire Resistance or drain it first. Water: on the spot, even deep underwater. On a ship: in the air where you died; ship flies on, grave stays; follow Latest Death waypoint and build up to it. Another planet: on that planet where you died; you wake on Earth, need a rocket to fetch it.
- Tip: Everlasting Upgrade keeps a backpack safe from fire, lava, void if it lies on the ground (e.g. after dropping it) (see home/storage).
- Server keeps regular world backups (see start_here/playing_together).
