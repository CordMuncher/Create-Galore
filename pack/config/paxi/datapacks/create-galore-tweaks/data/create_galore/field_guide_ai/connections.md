<!-- sources sha256:4968e979e45fcc3a8f2bf4fdeb6f287baecd522fab325d131397489d3e1b23d2 -->
# How Things Connect (connections)
Cross-mod joins. Shared hubs: rotation and electricity (and converters), heat (mostly Blaze Burner), liquid fuel (mostly oil), shared metals, vehicle destinations. Each entry points to the full entries.

## connections/overview | How Things Connect
- Use when asking "can this mod's thing go into that mod's machine?"
- Anything that burns makes smog (see survival/pollution); anything hot warms you. See Side Effects (see connections/side_effects).
- Entries: Power Across Mods (see connections/power), Heat Is Shared (see connections/heat), Fuel Goes Everywhere (see connections/fuel), One Metal, Many Mods (see connections/metals), Where Vehicles Go (see connections/vehicles), Farm to Factory (see connections/farm_to_factory), Loot Feeds the Factory (see connections/adventure_loot), Side Effects to Plan For (see connections/side_effects).

## connections/power | Power Across Mods
- Rotation and Forge Energy (FE) never mix on their own; converters needed: Alternator [createaddition:alternator] (rotation -> FE, at least 32 RPM (see glossary/rpm)) and Electric Motor [createaddition:electric_motor] (FE -> rotation); other rotation sources and FE users in Rotation vs Electricity (see industry/power_compared), Electricity (see industry/electricity). shows item createaddition:alternator; shows item createaddition:electric_motor
- Rotation sources beyond water, wind, steam (see create_basics/power_sources): TFMG engines (see industry/tfmg_engines), Create Nuclear (see industry/nuclear), Electric Motors on FE, and Create Propulsion's Stirling Engine (heat -> spin) (see flight/propulsion).
- Want FE: Ion Thruster, Flight Control vector ion thrusters, Deep Seas Electrolyzer on submarines (see ocean/submarines).
- TFMG wires its own voltage circuits, but its connectors can import or export FE (see industry/power_compared).

## connections/heat | Heat Is Shared
- Blaze Burner is the closest thing to a universal heater; many mods read Create's heat, and Create reads heat from other mods. Basics and the list of passive boiler heaters (Stove, Twilight fiery blocks, lava, magma, campfires, fire): (see create_basics/heat).
- Cooking: with Central Kitchen, a Blaze Burner heats a Farmer's Delight Cooking Pot or Skillet; Stove counts as a passive heater (see glossary/passive_heater) under a boiler (see glossary/boiler). (see food/kitchen_automation)
- Your body: factories, lit burners, steam engines, fans and hot tanks warm nearby players, useful in winter or high up. (see survival/temperature_create)
- Flight: burners lift hot air balloons (see flight/balloons_and_lift) on Aeronautics ships and keep crew warm against altitude cold.
- Power: Stirling Engine turns heat below it into rotation (see flight/propulsion).
- Water: boiling in a campfire or furnace purifies it (see survival/thirst).
- Watch out: a fuelled burner is one of the biggest smog sources; put a Flue on top (see survival/pollution) (see survival/pollution_control).
- Related: Burning Liquid Fuel (see industry/liquid_fuel).

## connections/fuel | Fuel Goes Everywhere
- TFMG oil (see industry/tfmg_oil) is the main liquid fuel; other mods accept it through shared fluid tags (see glossary/tags). Pipe once, many things drink.
- Which fuel burns where:
  - Blaze Burners, plain and Straw (napalm superheats, see create_basics/heat): (see industry/liquid_fuel)
  - Create Propulsion thrusters, Solid Fuel Thrusters, Turpentine: (see flight/propulsion)
  - Northstar rockets (own fuels, kerosene and naphtha; diesel and gasoline only run the Combustion Engine): (see space/rocket_building)
- Not oil: Crafts & Additions Seed Oil and Bioethanol (from crops), Garnished Peanut Oil; Create: Liquid Fuel lets a plain Blaze Burner burn them, Bioethanol and Peanut Oil superheat (see industry/liquid_fuel).
- Soot from air scrubbers burns as solid fuel (see survival/pollution_control).
- Related: Fluids, Pipes and Pumps (see create_basics/fluids).

## connections/metals | One Metal, Many Mods
- Where several mods add the same metal, one version is canonical: steel TFMG's, bronze Alloyed's, lead Create Nuclear's, copper wire Crafts & Additions', sulfur dust TFMG's; leftovers convert 1:1 in a crafting grid (see materials/shared_metals) (see industry/tfmg_steel) (see industry/alloyed).
- Lead: Create Nuclear's is the only lead ore (TFMG's lead ore is off).
- Salt, cocoa butter, cocoa powder: Create Ratatouille's (see food/ratatouille).
- Conversion is one for one with no gain, so loops can't be farmed. Check JEI (see start_here/tools) (R, U) for recipes.
- Titanium: ore only on Northstar's planets, but makeable on Earth by crushing limestone or sand into Rutile and running an assembly line (see create_basics/brass_and_assembly).
- Platinum: Create Propulsion ore, needed for vector thrusters (see flight/propulsion).
- Zinc: Create's metal, needed for brass. Productive Bees can make zinc and lead bees.
- Crushing ores with Create gives more metal per block (see materials/ore_doubling).
- Related: Ore Guide (see materials/ore_guide), Space Metals (see space/space_materials), Productive Bees (see food/bees).

## connections/vehicles | Where Vehicles Go
- Map of which vehicle reaches which places.
- Trains: link bases and farms over long distances. Create: Power Loader can keep a station's area loaded (see glossary/chunk). Trains (see transport/trains), Power Loader (see industry/power_loader).
- Airships: reach sky villages, floating ruins, soaring structures. Watch out: Hostile Skies' raiders and altitude cold. Flight Destinations (see flight/destinations).
- Space: Northstar rocket goes to Moon, Mars, Mercury, Venus. An Aeronautics ship can too with a Dimensional Drive (or the Stellar Navigator). Every way up needs a Stellar Core (see stars/why). Reaching Space (see space/reaching_space), Ships to Space (see space/aeronautics_bridge).
- Submarines: reach ruins, shipwrecks, ocean monuments, the deep (pressure and air become the problem). Submarines (see ocean/submarines), Underwater Places (see ocean/underwater_places).
- Roller coasters go nowhere in particular (that is the point). Roller Coasters (see transport/coasters).
- Related: Destinations (see flight/destinations), Planets (see space/planets), Structures (see exploration/structures).

## connections/farm_to_factory | Farm to Factory
- Links to know before a big farm (food touches seasons, Create machines, pollution, thirst).
- Seasons decide what grows: most crops grow only in some seasons; greenhouses and underground farms get around it. Crops by Season (see survival/seasons_crops).
- Create farms and cooks: harvesters and ploughs on contraptions (see create_basics/contraptions) reap fields; Slice & Dice's Sprinkler spreads Liquid Fertilizer; Central Kitchen lets arms and deployers run Farmer's Delight pots and boards (see food/kitchen_automation).
- Smog hurts crops (see survival/pollution): keep burners away from fields or put scrubbers between.
- Drinking water from Create: a pumped Sand Filter purifies it; compacting (see glossary/compacting) cactus gives clean water (see survival/thirst).
- Bees link in through Enchantment Industry's super experience (see food/bees).
- Related: Farmer's Delight (see food/farmers_delight), Seasons (see survival/seasons), Productive Bees (see food/bees).

## connections/adventure_loot | Loot Feeds the Factory
- Loot from dungeons and other dimensions often goes straight into Create machines.
- Boss gear: Cataclysm weapons and armor as Create assembly lines, crushing wheel recycles old gear (see exploration/cataclysm_create) (see create_basics/brass_and_assembly) (see create_basics/crushing).
- Magic: Iron's Spells inks and elixirs can be filled/emptied with Create spouts and drains (see create_basics/fluids). Create: Wizardry builds on Iron's Spells (see exploration/magic).
- Strange fluids: fan processing with dragon's breath, powder snow and more (see industry/dragons_plus) (see create_basics/fans); experience as pipeable fluid (see industry/enchanting).
- Other worlds' materials: Northstar planets give titanium and tungsten; Twilight Forest's fiery blocks heat a boiler (see glossary/boiler). Ore Guide (see materials/ore_guide).

## connections/side_effects | Side Effects to Plan For
- Systems react to what you build; not punishing alone but a big base notices all.
- Burning pollutes (see survival/pollution): furnaces, burners, stoves add smog to their chunk. Smog builds up fast and is punishing. Flues and air scrubbers fix it (see survival/pollution_control).
- Heat warms you: factories near you count toward body temperature (see survival/temperature).
- Height is cold: (see survival/temperature_create) matters for airships.
- Water isn't clean: drink purified water or build a filter (see survival/thirst).
- Seasons turn: plan farms around them (see survival/seasons).
- Cold Sweat and Thirst work together: Thirst Was Reclaimed uses Cold Sweat's temperature readings, so heat/cold around you feeds its thirst system. Dress for the weather, carry water.

## connections/balance | Balance Changes
- Policy: with many mods an item often has several recipes; where one route gave far more for less work it was cut or toned down so the Create machine route stays worth building. Better yield is fine if it costs a bigger build.
- Metals:
  - Steel needs heat: Create Nuclear's mix is heated (see create_basics/heat) now; Alloy Forge no longer makes steel or bronze.
  - Bronze comes from Alloyed (see industry/alloyed)'s mixer recipe only.
  - BetterEnd's smelter no longer turns 2 debris into 3 scrap.
  - Debris deposits need a Netherite Drill, lava and 2,048 SU (see create_basics/stress).
  - Crushing Limestone gives TFMG Limesand; crush cut or polished Limestone for Nuclear's Nitrate. Raw copper and zinc crush as in plain Create.
- Parts:
  - Steel plates: TFMG 3-press Heavy Plate (see materials/shared_metals)
  - Ion vector thrusters must be assembled, not converted.
  - Copper wire comes from the Rolling Mill, not the stonecutter.
  - Copycat (see glossary/copycat) Wings stonecut 1 to 1.
- Food:
  - Composter no longer triples dough.
  - Milling wheat kernels gives about 1.25 flour each.
  - Popsicle molds take 250 mB (see glossary/mb) of melon juice.
  - Mixed tomato sauce takes 2 tomatoes per 250 mB.
  - Blaze powder can't be stretched with pepper powder.
  - Channeling a sponge gives exactly one wet sponge.
  - Garnished stone slabs don't stonecut back into blocks or crush for ore.
- Bees and drills:
  - Bees (see food/bees) can't make alloys, rose quartz, rare metals (titanium, tungsten, platinum, uranium, mithril, netherite), sulfur, saltpeter or rare mob drops.
  - Deposit drills mine gems and glowstone at Ore Excavation (see industry/ore_excavation)'s own speed.
- Other:
  - No Uncrafting Table reversing.
  - Propulsion burners don't heat mixers or superheat.
  - TFMG engines pollute while running; Blaze Burners while they burn fuel. See Pollution (see survival/pollution).
  - Melon juice and cider hydrate like water.
  - Ferrets dig emeralds, not totems.
  - Granite gives uranium 10% of the time.
  - A Biomass Pellet takes 2 Biomass.
  - Mana Siphon never makes netherite.
