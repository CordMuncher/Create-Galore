<!-- sources sha256:a1e3680d742ecd713a5964702dd9917d26113570d4114d273b8d04aa4230d0dc -->
# Flight (flight)
Create Aeronautics ships, lift, thrust, steering, wind, dangers and sky destinations.

## flight/airships | Airships
- Ship = glued blocks turned into a moving physics object (sub-level: chunk of world with mass) by Physics Assembler [simulated:physics_assembler]; blocks float, tilt, fall, collide
- needs 3 things: assembled body (see glossary/assemble) (see glossary/sub_level); lift; thrust
- Lift: sealed balloon envelopes with hot air or steam, Levitite [aeronautics:levitite] blocks, or sails/wings moving through air. Lift fades with altitude; ship settles where lift = weight (see flight/balloons_and_lift)
- Thrust: Propeller Bearings spin sails; Create Propulsion adds fuel and electric thrusters (see flight/propellers) (see flight/propulsion). Steering: wheels, throttles, control consoles (see flight/controls)
- shows item simulated:physics_assembler: glue (see glossary/super_glue) a group of blocks, hold right-click on assembler, pull its lever
- Aviator's Goggles [aeronautics:aviators_goggles]: like Engineer's Goggles (see create_basics/tools_wrench); show stress (see create_basics/stress) and capacity on kinetic parts (see glossary/kinetic_network) plus extra readouts on placed blocks
- In this pack: ships show on Xaero's maps via Sable Xaero Maps; Jade tooltips work on them; carry Create machines; burn TFMG fuel in thrusters; can leave the planet via Northstar bridges (see flight/destinations) (see space/reaching_space)
- Physics from Sable library. Aeronautics bundles Simulated (general physics parts: bearings (see glossary/bearing), sensors) and Offroad (tires, wheel mounts, drilling heads for ground vehicles)
- Subpages: Building a Ship (see flight/ship_building), Balloons and Lift (see flight/balloons_and_lift), Propellers (see flight/propellers), Steering and Controls (see flight/controls), Weather and Danger (see flight/weather_and_danger), Destinations (see flight/destinations)

## flight/ship_building | Building a Ship
- Ship = normal build turned into Simulated Contraption (see glossary/sub_level) by Physics Assembler [simulated:physics_assembler]; glue (see glossary/super_glue) decides membership: only glued groups move together
- shows recipe simulated:physics_assembler; more Simulated parts in JEI (see start_here/tools)
- Assemble: select group with Super Glue or Honey Glue, hold right-click on assembler, pull lever
  - blocks along diagonal edges included
  - once built, any Physics Assembler can disassemble (see glossary/assemble) the ship
  - ship stays fully usable: walk on it, open chests, run machines
- Honey Glue [simulated:honey_glue]: click two corners, ctrl-scroll to resize; only joins ships; also takes overlapping Super Glue; Super Glue does not take Honey Glue
- Weight/splitting: blocks differ in weight (light, super heavy, slippery, fragile). Sections losing connection split off as separate ships; a Slime Ball merges any two
- Swivel Bearing [simulated:swivel_bearing]: turns glued group in front into rotating sub-ship; rotation (see create_basics/rotation) passes through shaft; redstone unlocks it to spin freely
- Steering a propeller: before assembling the ship, give the part the swivel should turn its own small glue box (it can sit inside the ship's glue); without one only the block on the swivel's face turns. Engine behind the bearing: rotation goes through it to the part. A Steering Wheel on a cogwheel next to the bearing turns it
- Docking Connector [simulated:docking_connector]: two connectors align and lock into rigid link; items and fluids pass both ways; retract either to undock
- Passing items across: once docked, move items with Create chutes or funnels (one feeding into the connector on one ship, one taking out of the connector on the other); vanilla hoppers don't pass items through them
- Rope Winch [simulated:rope_winch]: link ships with Rope Coupling between winch and connector; rotation reels rope in/out; shears cut it
- Watch out: bearings (see glossary/bearing) must be told not to disassemble when used with Steering Wheels or Torsion Springs. Springs let a bearing swing within an angle and return when input stops
- Related: Airships (see flight/airships), Ship Hardware (see flight/ship_hardware), Contraptions (see create_basics/contraptions)

## flight/balloons_and_lift | Balloons and Lift
- Hot air or steam in sealed envelopes lifts ship; more sealed volume = more lift for burners to work with. Envelopes must be airtight
- Hot Air Envelope [aeronautics:white_envelope]: airtight; blocks inside shrink volume; dyeable; shafts can be encased (see glossary/encased) in envelope blocks to stay sealed
- Hot Air Burner [aeronautics:adjustable_burner]: redstone fills balloon above from top down; max output set in its panel (see glossary/value_panel); several burners can feed one balloon
- Steam Vent [aeronautics:steam_vent]: steam lift; like Steam Engine needs heat, water, boiler (see glossary/boiler) space; (see create_basics/heat)
- Altitude: lift weakens with altitude; ship settles where lift = weight; changing lift changes altitude. Altitude Sensor outputs redstone signal for height and, with goggles (see create_basics/tools_wrench), shows air pressure. Other worlds have very different air (see space/planets)
- Levitite Blend [aeronautics:levitite_blend_bucket]: fluid from heated mixing (see glossary/mixing) of End Stone Powder, zinc nuggets, water; heat sets it into solid Levitite
- Levitite [aeronautics:levitite]: keeps ship afloat but cannot gain height alone (needs other forces); resists slow motion strongly, less at speed
- Sources: End Stone Powder from crushing End Stone; sky whale bone dust can replace it; Pearlescent Levitite = pink variant of same crystal; certain blocks break when Levitite sets (useful for casting molds)
- Related: Airships (see flight/airships), Weather and Danger (see flight/weather_and_danger), Blaze Burner and Heat (see create_basics/heat), Fluids, Pipes and Pumps (see create_basics/fluids)

## flight/propellers | Propellers
- Propeller Bearing (see glossary/bearing) spins a structure of sails; any structure with at least 2 sail-like blocks counts; more sails = more thrust
- Propeller Bearing [aeronautics:propeller_bearing]: attaches to block in front; reversing rotation reverses thrust (wrench (see create_basics/tools_wrench) does too); goggles show thrust and airflow
- Gyroscopic Propeller Bearing [aeronautics:gyroscopic_propeller_bearing]: keeps propeller upright (helicopters); facing down steadies top-heavy ships; redstone resets tilt
- Smart Propeller [aeronautics:smart_propeller]: thrust when spun; tilts along one axis to try to face upward; wrench reverses thrust
- Wooden Propeller [aeronautics:wooden_propeller]: ready-made small propeller; Andesite Propeller is sturdier twin
- Limits: propeller already moving through air pushes less; thrust fades with altitude; little use where no air
- Symmetric Sail [simulated:white_symmetric_sail]: only drag, never lift; angle for turning/stabilising; on a windmill bearing gives 0 RPM here; windmills need Windcatchers
- Related: Thrusters and Engines (see flight/propulsion), Steering and Controls (see flight/controls), Airships (see flight/airships)

## flight/controls | Steering and Controls
- Steering turns redstone and rotation into thrust and angles. Simulated supplies wheels, levers, sensors; Aeroworks adds control consoles and servos
- Steering Wheel [simulated:steering_wheel]: hold right-click and move mouse; sneak snaps to 45 degrees; comparator reads angle
- Throttle Lever [simulated:throttle_lever]: compact analog redstone source; right-click and drag; wrench (see create_basics/tools_wrench) inverts signal
- Navigation Table [simulated:navigation_table]: outputs redstone toward a destination, weaker when not facing it; different Navigation Items give different targets
- Sensors (redstone signals): Altitude, Velocity, Gimbal Sensors (height, speed, tilt); Laser Pointers, Laser Sensors, Optical Sensors (detect objects); Modulating and Directional Linked Receivers (distance/angle to Redstone Links (see create_basics/redstone_and_control))
- Aeroworks consoles hold modules: Yoke, Joystick, Throttle Quadrant, Pedal, Lever, Wheel, buttons
  - sneak-right-click: bind keys and Redstone Link frequencies (see glossary/redstone_frequency)
  - right-click empty-handed: take control; Esc lets go
  - touching consoles join into one deck
- Control Desk [aeroworks:control_desk]: mount modules in sockets; wrench removes them; they keep settings; styles change with materials
- Mechanical Servo [aeroworks:mechanical_servo]: redstone strength -> angle within set range; Stepper Servos move in fixed steps, either direction
- Aeroworks Gyroscope [aeroworks:gyroscope]: helps hold ship level; hover for status and stability
- Related: Flight Control (see flight/flight_control), Propellers (see flight/propellers), Thrusters and Engines (see flight/propulsion)

## flight/flight_control | Flight Control
- Create: Flight Control adds computers deciding how much each thruster pushes; link parts with tool gun, not redstone wiring
- Flight Control Tool Gun [create_flight_control:flight_control_tool_gun]: select an engine (see flight/propulsion) or controller, then right-click computer to link; also links tanks, sensors, seats
- Flight Control Computer [create_flight_control:flight_control_computer]: allocates thrust to meet demand of linked controllers; goggles (see create_basics/tools_wrench) show how many engines/controllers active
- Smart Attitude Stabilizer [create_flight_control:attitude_hold]: modes Level, Level plus Heading, Hold Attitude; needs shaft stress (see create_basics/stress) from below and a linked computer
- Altitude Hold Block [create_flight_control:altitude_hold]: holds current height, a set height, or only lifts when below a minimum; needs shaft power
- Flight Control Terminal [create_flight_control:mouse_flight_controller]: mouse-driven flight, pitch, yaw, auto bank; bind a seat to it with tool gun first
- Getting it to fly: a new computer only previews thrust and moves nothing; open it empty-handed, set Default: ON and press Apply Thrust. Craft keeps its last course when you leave the seat; set computer to Default: OFF to stop it
- Mouse steering: open the Terminal empty-handed, turn it on, press Apply; link a Redstone Proxy to the Terminal, map one face to Mouse Control Switch, put a button on that face; each press turns mouse steering on or off
- Smart Vector Thruster [create_flight_control:smart_vector_thruster]: nozzle swings inside a cone to push in direction computer asks
- Other parts: Flight HUD Projector, Attitude Display, Navigation Terminal, Profile Builder; couplers for speed and distance; Redstone Proxy (redstone toggles modes). Fuel tanks link to thrusters within a short range
- Related: Steering and Controls (see flight/controls), Thrusters and Engines (see flight/propulsion), Airships (see flight/airships)

## flight/propulsion | Thrusters and Engines
- Create Propulsion: Simulated adds fuel thrusters, electric ion thrusters, steerable vector thrusters, wings, heat engines; need Aeronautics physics to be useful
- Thruster [createpropulsion:thruster]: burns piped liquid fuel, throttled by redstone; oxidizer adds efficiency bonus; build 2x2 or 3x3 blocks of them and feed only the rear
- Fuels accepted: Turpentine (from Pine Resin); TFMG diesel, gasoline, kerosene, naphtha; Northstar hydrocarbon, methane, biofuel, liquid hydrogen. Wrong fluid = no thrust (see industry/tfmg_oil) (see space/rocket_building)
- Solid Fuel Thruster [createpropulsion:solid_fuel_thruster]: burns items not fluid: coal, charcoal, wood, Blaze Cake; easy start (coal, charcoal, planks all work); holds 1 fuel item, coal lasts ~3 min at full power (longer throttled); wrench opens its hatch, then it pulls the next item from a chest/container against its intake end (opposite the flame)
- Ion Thruster [createpropulsion:ion_thruster]: Forge energy, no fuel; stores energy; generators can feed it from below (see industry/electricity)
- Vector Thruster [createpropulsion:vector_thruster]: tilts to push any direction under redstone control; needs platinum (this mod adds it as an ore)
- Wing [createpropulsion:wing]: plain wing blocks; smelt one -> Tempered Wing; Copycat (see glossary/copycat) versions copy another block's look
- Stirling Engine [createpropulsion:stirling_engine]: rotation from heat below it; Solid and Liquid Burners supply heat; can warm a boiler (see glossary/boiler) but cannot heat mixers or superheat anything (that still needs a Blaze Burner (see create_basics/heat))
- Smaller parts: Redstone Transmission changes speed from signal (directly or stepwise); Tilt Adapters turn left/right difference into angle. Hold W (see start_here/tools) over Stirling Engine and burners for Ponders
- Related: TFMG Oil and Fuels (see industry/tfmg_oil), Electricity (see industry/electricity), Blaze Burner and Heat (see create_basics/heat), Ore Guide (see materials/ore_guide)

## flight/ship_hardware | Ship Hardware
- Transmission and Linkage: links ships with joints, rods, bearings (moves force between ships). Encased Fluid Pipes: carry fluid through balloon wall. Weightworks: Copycats nearly weightless (almost no mass). Aeronautics Compatibility: other mods work on moving ships
- Universal Joint Head [aeronautics_utility_objects:brass_universal_joint]: two heads + rod link structures, pass rotation (see create_basics/rotation) across; brass rod elastic; andesite rod reaches 15 blocks, breaks if stretched too far, pulls back after 1 block of stretch; right-click two matching joint heads with a rod; ends must be on different structures (one may be the world)
- Pneumatic Rod [aeronautics_utility_objects:hydraulic_rod]: configurable strut between two pneumatic heads; set target length, return force, stretch resistance; Free mode lets it slide
- Pneumatic Regulator [aeronautics_utility_objects:hydraulic_regulator]: place behind pneumatic head; redstone strength sets rod length, shaft speed sets move speed
- Hinge Head [aeronautics_utility_objects:brass_hydraulic_hinge_head]: pneumatic rod ends in hinge head swinging around one axis, rod sets only length; brass one has adjustable min/max angles
- Giant Hydraulic Rod [aeronautics_utility_objects:giant_hydraulic_rod]: heavy-duty; Valve Flow limits speed, Supply Pressure limits force; overloaded rod slows or stalls; made by mechanical crafting (see glossary/mechanical_crafting)
- Kinetic Conversion Bearing [aeronautics_utility_objects:damping_stress_bearing]: outputs structure's real rotation speed to a shaft; adjustable resistance; stress (see create_basics/stress) output capped at 200,000 SU
- Envelope Encased Pipe [aeroencasedpipe:white_envelope_encased_pipe]: fluid pipe (see create_basics/fluids) sealed in envelope material so pipes pass through balloon wall; made from fluid pipe + envelope block, all 16 colours; counts as part of balloon (hot air stays sealed); dye recolours in place; sneak with wrench to strip casing and get it back
- Compatibility: Aeronautics Compatibility keeps blocks from mods unaware of sub-levels (see glossary/sub_level) working on ships; Jade Sable Compat does same for Jade tooltips; a Copycats (see glossary/copycat) patch fixes an item duplication bug
- Weightworks gives Copycat blocks almost no mass
- Related: Building a Ship (see flight/ship_building), Balloons and Lift (see flight/balloons_and_lift), Fluids, Pipes and Pumps (see create_basics/fluids)

## flight/wind_sailing | Sailing and Wind
- Aeronautics: Winds & Weather adds real wind field and cloth sails. Wind has speed and heading, stronger over open ocean and at altitude, pushes ships rigged to catch it
- Only Windcatchers drive windmill bearings. Ships are pushed by Windcatchers and Sail Kit cloth (both catch wind; cloth can be trimmed and reefed, Windcatcher panels can't); ordinary Create sails are decoration. Keel stops sideways slip; Rudder on Create shaft steers
- Anemometer [aeronauticswinds:anemometer]: measures local wind speed, outputs as shaft rotation; goggles (see create_basics/tools_wrench) show exact figure
- Wind Vane [aeronauticswinds:wind_vane]: settles on wind direction, turns lower shaft; goggles show compass bearing
- Windcatcher [aeronauticswinds:windcatcher_canvas]: only Windcatchers drive windmill bearings (see glossary/bearing); on a ship they catch wind like cloth sails; don't stick on their own: glue (see glossary/super_glue) to the bearing; face broad side into wind; facing = way its broad face points: placed from the side it stands upright facing you (facing north/south/east/west), placed looking up/down it lies flat (facing up/down, catches no wind); panels side by side with same facing join into one sail
- Rigging a sail: stack Masts (straight up), run a Yard out sideways from them (axis = way the run goes; joins Masts and Yards at its two ends); use packed Cotton or Canvas Sail on Yard, click lower opposite corner: cloth [aeronauticswinds:cotton_sail_cloth] fills cells straight below the Yard run, in its line, facing across it; empty-hand clicks trim yard in 8 steps; sneak-use cloth to reef; Cotton light and quick, Canvas heavy and durable
- Yard [aeronauticswinds:yard]: run a rope from an Aeronautics Rope Coupling to a Rope Winch for continuous trim; shears detach it
- Keel [aeronauticswinds:keel]: fits under hull fore and aft; stops sideways slip, turns angled wind into forward travel; extend for more grip
- Rudder [aeronauticswinds:rudder]: driven by Create shaft setting steady angle; stack blades for deeper rudder; sneak-click to centre
- Ship Anchor: secures ship in place; lower with top shaft; Light and Heavy Anchors also exist
- Wind Rider [aeronauticswinds:wind_rider]: hang glider; jump while falling to open, jump again or attack to stow; campfire thermals carry you up; third person (F5) camera pulls back to show whole glider, distance in Aeronautics Winds' client config
- Related: Airships (see flight/airships), Weather and Danger (see flight/weather_and_danger), Steering and Controls (see flight/controls)

## flight/weather_and_danger | Weather and Danger
- All from add-ons needing Aeronautics: storms/lightning push ships, pillager airships hunt them, whales drift above oceans
- Weather: Winds & Weather adds weather regions with storms and lightning that can strike a ship; wind stronger over open ocean, grows with altitude; extreme wind pushes ships hard. Rain follows normal weather: rain counts as rain for drinking, crops, cauldrons. Check wind with Anemometer (see flight/wind_sailing)
- Hostile Skies (Create Hostile Skies): pillager airships hunt you in tiers Scout, Patrol, Warship, Flagship; ships can be captured
  - your captain kills open higher tiers (accidents don't count)
  - rarer here: a try every 25 minutes, from 5% chance
  - Bad Omen raises crew and loot
- Captain's Orders [hostile_skies:captains_orders]: one of two Hostile Skies items (other: Peace Treaty); no crafting recipe in pack; read tooltip
- Sky Whales: rare, drift high over oceans; dead one leaves slowly sinking corpse of whale bone and meat blocks acting as physics objects (see glossary/sub_level); crushing bone gives Whale Bone Dust -> End Stone Powder or, mixed, Levitite Blend (see flight/balloons_and_lift)
- Whale Bone Dust [skywhales:whale_bone_dust]: crush or mill whale bone; heated mixing (see create_basics/heat) with zinc nuggets + water -> Levitite Blend, bypassing the End
- Cooked Whale Meat [skywhales:cooked_whale_meat]: raw whale meat can be cooked into food
- Related: Balloons and Lift (see flight/balloons_and_lift), Sailing and Wind (see flight/wind_sailing), Hangars (see flight/hangars)

## flight/hangars | Hangars
- Create: Hangars scatters small aircraft sheds across Overworld; chests hold starter Aeronautics parts
- Where: eight biome styles (plains, birch, taiga, dark oak, cherry, jungle, bamboo, savanna); jungle and plains most common; spaced roughly 900 blocks apart
- Loot: Shelf chests: Hot Air Burners, Hot Air Envelopes, Propellers, Windmill Sails, Symmetric Sails, Springs, Redstone Magnets, basic Create gear. Pulley chests: Rope Couplings and Andesite Casing [create:andesite_casing]
- Hot Air Burner [aeronautics:adjustable_burner]: common on hangar shelves with envelopes and propellers (see flight/propellers)
- Rope Coupling [simulated:rope_coupling]: in pulley chests; links a Rope Winch to a Rope Connector
- Related: Airships (see flight/airships), Destinations (see flight/destinations), Structures (see exploration/structures)

## flight/destinations | Destinations
- Map of what pack puts in sky and on far worlds
- Sky Villages (Create: Sky Village): villages between about 150 and 250 blocks up in Overworld; two kinds: Sky Village and Highwind Village; villagers and Create loot
- Aeronautics builds (Creat Aeronautics Structures): airships, airballoons, crashed planes, boats, engineering house with Engineer villager, pillager versions
- Discovery (Aeronautics Discovery): events: planes and balloons fly over Overworld at height, balloons appear in villages; adds Pillager Aviators, Soaring Traders, maps to temples, igloos, ancient cities, mineshafts
- Pin Wand [aeronauticsdiscovery:pin_wand]: from Aeronautics Discovery; check tooltip in game for use
- Soaring Trader [aeronauticsdiscovery:soaring_trader_spawn_egg]: trader from Aeronautics Discovery; Pillager Aviator is hostile counterpart
- Floating islands (Moog's Soaring Structures): vanilla-style islands: houses, towers, ruins, trees, volcanoes, an arena, floating Nether portal; loot and mobs
- Island loot [minecraft:chest]: chests and mobs; bring a ship that can land
- How often: sky sparser than ground (you cover ground faster in air); about 3 sky structures per km2, half the ground's rate: one every 600 blocks or so
- Common/rare: most sky structures equally common, so mods with many kinds (Moog's islands) show up more. Rarer prizes: island arena, volcano, towers, big Sky Village, steampunk airship, heavenly fortresses. Some islands only float over their own biome
- The Aether (see exploration/aether): separate dimension of floating islands; portal = glowstone frame lit with water. Sky whales drift over Overworld ocean (see flight/weather_and_danger)
- Ambrosium Shard [aether:ambrosium_shard]: basic Aether resource; Dimensions (see exploration/dimensions)
- Beyond sky: fly Aeronautics ship upward with a Dimensional Drive (see space/aeronautics_bridge), or build a rocket. Northstar has Moon, Mars, Mercury, Venus (see space/reaching_space) (see space/planets)
- Related: Hangars (see flight/hangars), Weather and Danger (see flight/weather_and_danger), Dimensions (see exploration/dimensions), Structures (see exploration/structures), Reaching Space (see space/reaching_space)
