<!-- sources sha256:2c8706ec436137b9f294feed41cd9267bf9684055de78c5bf9b63e4cbd6ccb40 -->
# Space (space)
Northstar (rockets) and Aeronautics ship routes to the Moon, Mars, Mercury, Venus and orbits; oxygen, suits, metals.

## space/reaching_space | Reaching Space
- Create: Northstar adds a solar system as dimensions: Moon, Mars, Mercury, Venus, plus an orbit above Earth, Mars, Mercury, Venus (Moon has none). Reach by a rocket built from Create parts, or by flying an Aeronautics ship upward. Either way first need a Stellar Core (see stars/why).
- Three ways up, all built around a Stellar Core: Northstar rocket (contraption (see create_basics/contraptions) glued (see glossary/super_glue) around a Rocket Station); Aeronautics ship with a Dimensional Drive or Stellar Navigator. See Rockets (see space/rocket_building), Ship Bridges (see space/aeronautics_bridge).
- Destinations come from the Space Atlas; planets unlock as you add science to it from Astronomical Readings. Landing on a planet needs an Interplanetary Navigator aboard; the Moon does not (it is a moon).
- Space Atlas [northstar:space_atlas]: holds coordinates of bodies and saved waypoints. Put in a Rocket Station to set destination. shows item northstar:space_atlas
  - shows recipe northstar:crafting/space_atlas: a Book and a Titanium Ingot.
- Telescope [northstar:telescope]: hold to take an astronomical reading. Needs night and 1 Paper. Does not work in every dimension. shows item northstar:telescope
- Astronomy Table [northstar:astronomy_table]: turns readings into science for the atlas. Readings of different planets are worth more; one planet gives one reading per day. shows item northstar:astronomy_table
- Titanium is the key metal, replacing iron plates in much of the tech (see space/space_materials). Oxygen and suits: see space/space_survival.
- Related: Why Space Is Locked (see stars/why), Building a Rocket (see space/rocket_building), Flying Ships to Space (see space/aeronautics_bridge), Planets (see space/planets), Oxygen and Suits (see space/space_survival), Space Metals (see space/space_materials), Airships (see flight/airships).

## space/rocket_building | Building a Rocket
- Rocket = glued (see glossary/super_glue) blocks around a Rocket Station; needs thrusters, fuel in fluid tanks, a seat, Rocket Controls. Trips to planets also need an Interplanetary Navigator. Super Glue holds it together.
- Rocket Station [northstar:rocket_station]: picks destination, assembles rocket, shows fuel/thruster/heat needs. After picking a destination press the save icon: closing with Esc throws the choice away. Built around a Stellar Core (see stars/stellar_core). shows item northstar:rocket_station
- Rocket Thruster [northstar:rocket_thruster]: number needed depends on rocket weight and gravity at both ends. Built from a Combustion Chamber, titanium plates, a Create Propeller. shows item northstar:rocket_thruster
- Fuel must sit in fluid tanks on the rocket. Accepted: Liquid Hydrogen, Hydrogen, Methane, Hydrocarbon, Biofuel; TFMG kerosene and naphtha (diesel and gasoline only run the Combustion Engine). Amount depends on gravity at each end and distance. See Oil (see industry/tfmg_oil).
- Making fuel: Hydrocarbon = heated mixing (see create_basics/heat) of Carbon with Sodium or a Sodium Catalyst. Carbon from compacting (see glossary/compacting) coal with heat, or from a planet's air. Hydrogen and Oxygen from the Electrolysis Machine. Check JEI (see start_here/tools) for each.
- Computer Rack [northstar:computer_rack]: holds Targeting Computers, which find better trajectories and cut fuel cost. shows item northstar:computer_rack
- Heat shielding: atmosphere friction heats a rocket; add metallic blocks anywhere on it. Tiers: titanium and iron, then Martian steel, then tungsten. Station lists supported blocks.
- Launching: right-click Rocket Controls to take control (as with train controls), press jump to start launch sequence; jump again cancels. Hold the slow key on descent. Landings aim at a Rocket Waypoint, recorded by using the atlas on it.
  - Rocket Controls [northstar:rocket_controls]: required to launch and steer; without them station reports no controls attached. shows item northstar:rocket_controls
  - Rocket Waypoint [northstar:rocket_waypoint]: marks landing coordinates; use a Space Atlas on it to save the position. shows item northstar:rocket_waypoint
  - Auto-Landing Station [northstar:auto_lander]: lands the rocket safely on its own. shows item northstar:auto_lander
- Related: Reaching Space (see space/reaching_space), Planets (see space/planets), TFMG Oil and Fuels (see industry/tfmg_oil), Burning Liquid Fuel (see industry/liquid_fuel).

## space/aeronautics_bridge | Flying Ships to Space
- Two bridge mods let an Aeronautics ship cross to Northstar worlds without a rocket: Dimensional Drive and Stellar Navigator; both built with a Stellar Core. Dimensional Sable lets Sable (see glossary/sub_level) ships change dimension at all.
- Dimensional Drive [cnbridge:dimensional_drive]: place on a ship. Right-click, put a Space Atlas in its slot, pick a planet, press Arm Drive, fly up to trigger altitude. shows item cnbridge:dimensional_drive
  - Recipe here: a Stellar Core (see stars/stellar_core), 4 Titanium Ingots, 4 Precision Mechanisms (see create_basics/brass_and_assembly). Press R over it in JEI (see start_here/tools). Can reach Moon, Mars, Venus, Mercury, Earth, Earth Orbit.
  - Must sit on an Aeronautics ship. No atlas in slot: always goes to Earth Orbit. Mars, Venus, Mercury also need an Interplanetary Navigator on board. Goggles (see create_basics/tools_wrench) show destination, speed, altitude and altitude needed.
  - Trigger altitude is fixed, not set in menu. From Earth: Earth Orbit and Moon 1750; Mars 2200; Venus 2600; Mercury 3000. From anywhere else 1750. Hold that height about 5 seconds while the drive spools; stay on a seat so you land with the ship.
- Getting that high: air pressure fades to nothing by top of world (Y 320), so balloons and propellers stop lifting. Thrusters (see flight/propulsion) keep full power in thin air: exhaust pointing down, one fuelled Thruster lifted a small test ship past Y 1750 in under 10 seconds. Bring enough fuel to hold the height while the drive spools.
- Climbing safely: fixed thrusters at full power with nobody steering can tip a ship and drop it. Steer them (vector thrusters, or trim each one's power) and climb steadily. Ride in a closed cabin: open decks freeze you above Y 192 (see Temperature and Create (see survival/temperature_create)). A very fast climb can make a busy server throw riders off.
- Stellar Navigator [northstar_aeronautics_compat:stellar_navigator]: from Northstar Aeronautics Compat; brings an Aeronautics vessel to another planet. Can be turned off in config (see glossary/config). shows item northstar_aeronautics_compat:stellar_navigator
  - shows recipe northstar_aeronautics_compat:stellar_navigator: a Stellar Core and a bucket of Levitite Blend (see flight/balloons_and_lift).
- Gravity and air: arriving ships take on the world's gravity and air. Moon and Mercury have no air, Mars almost none, Venus crushingly dense. Watch out: propellers and burners need air, so plan thrusters (see flight/propulsion); see Propellers (see flight/propellers).
- Related: Reaching Space (see space/reaching_space), Planets (see space/planets), Airships (see flight/airships), Balloons and Lift (see flight/balloons_and_lift).

## space/planets | Planets
- Four landable worlds: Moon, Mars, Mercury, Venus. Earth, Mars, Mercury, Venus also have an orbit dimension; Moon has none. Other bodies are sky scenery in the atlas.
- Titanium ore on all four; tungsten only on Mercury. Temperatures from -270 C in orbit to 464 C on Venus.
- Gravity, temperature, air: Moon 1.6, -183 C, none; Mars 3.71, -100 C, thin; Mercury 3.7, -200 to 434 C, none; Venus 8.87, 464 C, dense. Orbits: no gravity, -270 C.
- Moon: Lunar Plains, hills, crater fields, caves of ice, glowstone, asurine. Ores: titanium, zinc, copper, iron, gold, diamond, redstone, lapis, glowstone. Mobs: Moon Eel, Lunargrade, Snail. Abandoned lunar bases can be explored.
  - Moon Titanium Ore [northstar:moon_titanium_ore]: smelt directly into a Titanium Ingot. shows item northstar:moon_titanium_ore
- Mars: dunes, highlands, peaks, caverns with crimsite and overgrown plants. Ores: titanium, iron, copper, zinc, gold, diamond, quartz, redstone. Mobs: Cobra, Devil Moth, Root Toad, Echo Worm. Martian iron and ash make Martian steel (see space/space_materials). Also palms, tulips, Martian strawberry bushes.
  - Mars Titanium Ore [northstar:mars_titanium_ore]. shows item northstar:mars_titanium_ore
- Mercury: hills, basins, icy or magmatic caverns, huge day/night swing. Only world with tungsten ore; also titanium, glowstone, lapis, gold, diamond. Mobs: Mercurian Raptor, Roach, Tortoise.
  - Mercury Tungsten Ore [northstar:mercury_tungsten_ore]: only source of tungsten. Crushed raw tungsten is washed (see glossary/washing) into nuggets. shows item northstar:mercury_tungsten_ore
- Venus: fungal forests, lava, sulfuric caverns, very dense atmosphere. Ores: coal, quartz, glowstone, titanium and others. Mobs: Mimic, Scorpion, Stone Bull, Vulture. Watch out: iron suits burn here; wear Martian steel (see space/space_survival). Sulfuric Acid kills swimmers.
  - Venus Gravel [northstar:venus_gravel]: crushing gives the most Rutile Concentrate of any source, for making titanium on Earth. shows item northstar:venus_gravel
- Related: Oxygen and Suits (see space/space_survival), Space Metals (see space/space_materials), Ore Guide (see materials/ore_guide), Dimensions (see exploration/dimensions).

## space/space_survival | Oxygen and Suits
- Off Earth you need air and the right temperature; Northstar handles both with sealed rooms and spacesuits (death messages: ran out of oxygen, forgot their spacesuit).
- Iron Space Suit [northstar:iron_space_suit_chestpiece]: made from iron armour, iron plates, tinted glass. Chestplate carries the oxygen supply; fill at an Oxygen Filler. shows item northstar:iron_space_suit_chestpiece
- Martian Steel Space Suit [northstar:martian_steel_space_suit_chestpiece]: tougher suit, the one that stops you burning. shows item northstar:martian_steel_space_suit_chestpiece
- Heat: above 300 C (Venus, Mercury by day) you burn unless all four armour pieces are tagged (see glossary/tags) heat resistant: Martian Steel Space Suit, or Create's netherite diving helmet, backtank and boots with netherite leggings.
- Cold: below -32 C all four must be insulating: either space suit, or leather armour.
- On Earth both suits count as warm clothing for body temperature (see survival/temperature): iron against cold, Martian steel against cold and heat.
- Oxygen Filler [northstar:oxygen_filler]: right-click in a suit, pump Oxygen Gas in to fill it, or extract it out. shows item northstar:oxygen_filler
- Oxygen Sealer [northstar:oxygen_sealer]: Oxygen from behind, rotation (see create_basics/rotation) from below; fills the sealed area around it. Shows how much it can seal, warns if room leaks. shows item northstar:oxygen_sealer
- Large Fan [northstar:large_fan]: raises area a Sealer or Temperature Regulator can cover. Needs an Encased Chain Drive (see create_basics/transmission); wrench (see create_basics/tools_wrench) sets chain side. shows item northstar:large_fan
- Atmospheric Concentrator [northstar:atmospheric_concentrator]: collects Oxygen or Carbon from air; does nothing on worlds without atmosphere. shows item northstar:atmospheric_concentrator
- Electrolysis Machine [northstar:electrolysis_machine]: splits water into hydrogen and oxygen, which fuel rockets and fill suits. shows item northstar:electrolysis_machine
- Temperature Regulator [northstar:temperature_regulator]: sets temperature in a box you size. Vents let air pass; wool makes an insulated vent that blocks heat but not air. shows item northstar:temperature_regulator
- Solar Panel [northstar:solar_panel]: sunlight into rotation. A Combustion Engine burns rocket fuel for rotation and needs oxidizer. shows item northstar:solar_panel
- Related: Temperature (see survival/temperature), Planets (see space/planets), Power Sources (see create_basics/power_sources), Stress (see create_basics/stress).

## space/space_materials | Space Metals
- Northstar adds three metals: Titanium, Tungsten, Martian Steel. Titanium is the base of most tech; ore on all four worlds, but also makeable on Earth.
- Titanium Ingot [northstar:titanium_ingot]: smelt or blast titanium ore from Moon, Mars, Mercury, Venus. Also pressed into Titanium Sheets. shows item northstar:titanium_ingot
- Titanium on Earth: Rutile Concentrate mixed superheated (see create_basics/heat) with zinc, chlorine, carbon -> Titanium Tetrachloride; applied to a Sturdy Sheet in a sequenced assembly (see create_basics/brass_and_assembly) with lava -> Titanium Ingot. Chlorine from electrolysing Brine.
- Rutile Concentrate [northstar:rutile_concentrate]: crush sand, Create's limestone (best of the two) or Venus Gravel. shows item northstar:rutile_concentrate
- Salt: Northstar's brine chain makes salt. In this pack it is Create Ratatouille's Salt: brine compacts (see glossary/compacting) down to it; Mars Gravel and Basalt crush into it. Other mods' salt converts 1:1 (see materials/shared_metals) in a crafting grid.
- Tungsten Ingot [northstar:tungsten_ingot]: only from Mercury ore; top tier of heat shielding. shows item northstar:tungsten_ingot
- Martian Steel Ingot [northstar:martian_steel_ingot]: heated compacting of Titanium, Raw Martian Iron, Volcanic Ash gives two ingots. shows item northstar:martian_steel_ingot
- Circuit [northstar:circuit]: sequenced assembly; used in rockets, oxygen gear, most space machines. shows item northstar:circuit
- Related: Ore Guide (see materials/ore_guide), Brass and Assembly (see create_basics/brass_and_assembly), Planets (see space/planets), Core Materials (see create_basics/materials).
