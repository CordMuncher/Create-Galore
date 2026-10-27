<!-- sources sha256:e0119957947f860a5f4747bfe94cbd3ee570e75065893020677a2d4f6837dd5a -->
# Create: The Basics (create_basics)

## create_basics/rotation | Rotation
- Create runs on rotational force, not electricity or fuel at its core. Source (water, wind, steam, your own hand) spins a shaft; spin passes along shafts and gears to machines.
- Every spinning part has speed in RPM (see glossary/rpm) (rotations per minute) and direction (clockwise/counter-clockwise). Many machines work at any speed, some want fast, a few get better the faster they spin.
- Shaft [create:shaft]: carries rotation in a straight line, unchanged. Everything in the network (see glossary/kinetic_network) connects through shafts, gears or belts (see create_basics/transmission).
- Direction matters: touching cogwheels spin opposite ways. A drill or saw may work in one direction only; fans, pumps and bearings (see glossary/bearing) flip what they do if reversed.
- Network is shared: all machines on one connected set of shafts and gears spin at same speed (scaled by gear ratios) and draw from the same pool of power (see create_basics/stress).
- Speedometer [create:speedometer]: dial showing network speed; with goggles (see create_basics/tools_wrench) on, reads exact RPM.
- Ponder (see start_here/tools): hold W over any block to see parts in motion. Spin sources: (see create_basics/power_sources). Reading speed/stress live: (see create_basics/tools_wrench).

## create_basics/stress | Stress
- Every machine uses some of its network's (see glossary/kinetic_network) stress capacity, measured in su (see glossary/su) (stress units). Sources provide capacity, machines consume it.
- Overstressed (see glossary/overstressed) = machines ask more than sources give: the whole network stops, not just one machine. Fix: more sources, fewer machines, or slower speed.
- Faster costs more: a machine's stress cost rises with RPM (see glossary/rpm), and a source's capacity rises with RPM too. Fast network carries more, but each machine eats more.
- Reading: wear Goggles (see create_basics/tools_wrench), look at a machine for Stress Impact or a source for Stress Capacity. Item tooltips (hold Shift) say Low, Moderate or High stress impact.
- Stressometer [create:stressometer]: gauge of how much network capacity is in use; with goggles on, names exact numbers.
- Engineer's Goggles [create:goggles]: show Kinetic Stats on any machine (impact, capacity, speed). Steam boilers (see glossary/boiler) show their power level too.
- Large sources (large water wheels, steam engines) (see create_basics/power_sources) exist because many machines need much capacity. Exact numbers vary by config (see glossary/config): check goggles in your own world.

## create_basics/power_sources | Power Sources
- Every Create network (see glossary/kinetic_network) needs at least one source of rotation. Sources differ in capacity, speed, setup effort.
- Small/simple: Hand Crank, Water Wheel, Windmill (with Windcatchers). Big jobs: Large Water Wheel, Steam Engine.
- Hand Crank [create:hand_crank]: you power it. Hold Use to rotate one way, sneak and hold Use for the other. For testing and tiny jobs.
- Water Wheel [create:water_wheel]: takes force from adjacent flowing water; extra sides in water do not add output. Low capacity, steady.
- Large Water Wheel [create:large_water_wheel]: half the speed of a normal wheel, much higher stress capacity (see create_basics/stress).
- Windmill Bearing [create:windmill_bearing]: Only Windcatchers make power: at least 8, glued (see glossary/super_glue) to the bearing. RPM follows wind (about 5 near ground, more up high and at sea). (see flight/wind_sailing)
- Windmill Sail [create:white_sail]: place on a sail frame or any block; right-click with dye to colour, shears turn back to a frame. DECORATION ONLY in this pack: windmill of these gives 0 RPM.
- Steam Engine [create:steam_engine]: sits on a boiler (see glossary/boiler) built from Fluid Tanks.
  - needs water and heat from a Blaze Burner; minimal boiler is 4 tanks
  - more heat -> higher power level; each level lets one more engine run at full strength; goggles (see create_basics/tools_wrench) show level
  - connect a shaft to the engine's output; heat from blaze burner under boiler (see create_basics/heat), (see create_basics/fluids)
  - shows the build: Steam Engine (4-tank boiler over Blaze Burners, filled with water; shaft on engine's output)
- Blaze burners and engines make pollution here (see survival/pollution). Creative Motor exists but not obtainable in survival.
- Add-on sources: Electricity (see industry/electricity), Liquid Fuel (see industry/liquid_fuel).

## create_basics/transmission | Transmission
- Shafts pass rotation straight on; cogwheels flip direction. Small->large cogwheel halves speed, large->small doubles it. With redstone: Clutch cuts rotation, Gearshift reverses it. Watch your stress when you branch a network (see create_basics/stress) (see glossary/kinetic_network).
- Shaft [create:shaft]: same speed and direction; place end to end.
- Cogwheel [create:cogwheel]: passes rotation to adjacent cogwheels in opposite direction; also works as a shaft.
- Large Cogwheel [create:large_cogwheel]: meshes with small cogwheels diagonally; small->large halves speed, large->small doubles it; also connects at right angles.
- Gearbox [create:gearbox]: turns rotation around a corner in a compact block; straight connections are reversed, corners mirror.
- Mechanical Belt [create:belt_connector]: right-click two shafts to link; same speed and direction; spans 2 to 20 blocks; also carries items.
- Encased variants: brass or andesite casing (see glossary/encased) hides shafts, cogwheels, belts. Components placed after encasing do not connect unless you toggle the connection with a wrench (see create_basics/tools_wrench).
- Clutch [create:clutch]: passes rotation in a line; redstone-powered it breaks the connection.
- Gearshift [create:gearshift]: passes rotation in a line; redstone-powered it reverses it.
- Rotation Speed Controller [create:rotation_speed_controller]: sits below a large cogwheel, relays rotation to it at speed set on its value panel (see glossary/value_panel).
- Sequenced Gearshift (in depth): runs a timed list of speed/direction instructions on each redstone pulse; configure by right-click; good for pistons, gantries, anything moving in steps.
- Chain drives pass rotation along a row with all shafts spinning same way. Ponder (see start_here/tools) for each part.

## create_basics/materials | Core Materials
- Create uses own materials made in steps; early ones need little, later ones need heat and machines. Exact recipes: JEI (see start_here/tools) with R.
- Andesite Alloy [create:andesite_alloy]: first Create material. Andesite + iron or zinc nuggets, crafted in a diagonal or mixed (see glossary/mixing) in a basin. Andesite found below about Y 60, or craft from diorite and cobblestone.
- Andesite Casing [create:andesite_casing]: right-click a stripped log with andesite alloy. For early machines and to encase (see glossary/encased) shafts.
- Zinc Ingot [create:zinc_ingot]: smelted from zinc ore. Mixed with copper over heat (see create_basics/heat) -> brass. Where to find: (see materials/ore_guide).
- Brass Ingot [create:brass_ingot]: copper + zinc mixed in a heated basin. Opens second tier of Create machines.
- Brass Casing [create:brass_casing]: right-click a stripped log with brass. Needed for most advanced machines (see create_basics/brass_and_assembly).
- Iron Sheet [create:iron_sheet]: press an iron ingot with a mechanical press (see create_basics/processing). Brass and copper also press into sheets.
- Electron Tube [create:electron_tube]: crafted from polished rose quartz [create:polished_rose_quartz] over an iron sheet. Needed for circuits and many brass machines.
- Copper: smelted from ore, pressed into sheets. Copper casing is for fluid pipes. Metals shared across mods: (see materials/ore_guide), (see create_basics/processing).

## create_basics/tools_wrench | Wrench and Goggles
- Wrench changes blocks; Engineer's Goggles show information.
- Wrench [create:wrench]: right-click rotates a component around the face clicked; sneak+right-click dismantles; also moves assembled (see glossary/assemble) minecart and train contraptions.
- Engineer's Goggles [create:goggles]: show stress impact, capacity, speed of kinetic parts (see glossary/kinetic_network) and contents of tanks and basins when looked at.
- Wrenches also toggle connections on encased (see glossary/encased) shafts and cogwheels, and open/close the window on fluid tanks.
- Value boxes: many machines show a small panel (see glossary/value_panel) on a face; look at it and right-click to change, or scroll mouse wheel to adjust value. Used by speed controllers, windmills, gearshifts.
- Tooltips: hold Shift on any Create item for summary of what it does/needs. Hold W for Ponder (see start_here/tools).
- Stress: (see create_basics/stress); Tools: (see start_here/tools).
  - shows recipe create:crafting/kinetics/wrench
  - shows recipe create:crafting/kinetics/goggles

## create_basics/processing | Press, Mixer and Basin
- Machines act on items sitting in front of them (on a belt, depot, or inside a basin), not through menus. Most need rotation (see create_basics/rotation). Hover machine + hold W for Ponder (see start_here/tools).
- Mechanical Press [create:mechanical_press]: hits whatever is under it. On belt/depot: presses ingots into sheets. Over a basin: compacts (see glossary/compacting) contents = any filled 2x2 or 3x3 crafting recipe plus some extras (some need heat, see create_basics/heat).
  - shows the build: Press on a Depot (press hits items on depot below: ingots become sheets)
  - Pressing a plain item makes a sheet: iron, gold, copper, brass sheets are building blocks for later machines.
- Mechanical Mixer [create:mechanical_mixer]: sits above a basin and stirs. Handles any shapeless crafting recipe plus extras like alloying.
- Basin [create:basin]: holds items and fluids for mixer/press above. No output until you give one.
- Brass (see create_basics/brass_and_assembly): classic mixer job: copper + zinc ingots in basin with heated (see create_basics/heat) burner under it. Exact mix in JEI (see start_here/tools) (see glossary/mixing).
- Tip: if two recipes fit the same contents, put an item in the machine's filter slot (see glossary/filter_slot) to pick one.
- Basin output: only into the spot one block down beside the basin: depot, belt, chute, or a funnel facing up on a chest (sneak-placed so it inserts). Block beside the basin at its own height must stay open (air). A chest/barrel/hopper in that spot gets NOTHING; chest goes one lower with the funnel on top in the spot. Then basin shows a faucet and pushes results out. No output = keeps results inside (useful when they're ingredients for next step).
- If you pull from a basin, use a filter (see create_basics/item_logistics) so unprocessed items stay.
- Related: (see create_basics/heat), (see create_basics/fans), (see create_basics/crushing), (see create_basics/brass_and_assembly)

## create_basics/heat | Blaze Burner and Heat
- Some recipes need heat, from the Blaze Burner; it also boosts basin (see create_basics/processing) recipes and powers steam engine boilers (see glossary/boiler).
- Burner is not crafted whole: capture a living blaze in an Empty Blaze Burner.
- Empty Blaze Burner [create:empty_blaze_burner]: right-click a blaze to capture it; blazes can also be collected from their spawners. Then put burner under a basin or boiler.
  - shows the build: Heated Mixer (burner under basin heats it for mixer recipes marked heated)
- Blaze Burner [create:blaze_burner]: feed flammable items by hand, or via a deployer (see create_basics/deployer) or mechanical arm. Cools down when fuel runs out.
  - Two levels: fed fuel = heated (see glossary/heated); fed Blaze Cake = superheated (see glossary/superheated), stronger tier some recipes need. Superheated also counts as heated.
- Blaze Cake [create:blaze_cake]: takes burner to stronger tier. JEI (R, U) for its recipe and recipes needing superheating (see start_here/tools).
- Uses of heat: mixer and press recipes marked heated/superheated; steam engine boilers (fluid tanks on a burner heat water to steam); fans (see create_basics/fans for fan catalysts).
- Other mods (in depth): the tag (see glossary/tags) Create reads for boiler heat also includes Farmer's Delight (see food/farmers_delight) Stove, Twilight Forest (see exploration/twilight_forest) fiery storage blocks, and lava, magma blocks, campfires, fire. Passive boiler heaters (see glossary/passive_heater), weaker than a burner.
- Burners can burn liquid fuel from pipes (see industry/liquid_fuel).
- Related: (see create_basics/processing), (see create_basics/fans), (see industry/liquid_fuel)

## create_basics/fans | Encased Fans
- Encased Fan [create:encased_fan] turns rotation into air current. Items caught in it (ground, belt, depot) get bulk processed (see glossary/bulk_processing) depending on what the air passes through. Speed only changes the range, not processing speed.
- Direction follows rotation. Air also pushes mobs, players, dropped items.
- Base types: Washing (see glossary/washing) = water; Smoking = fire or campfire; Blasting = lava; Haunting (see glossary/haunting) = soul fire or soul campfire. Washing: (see create_basics/crushing).
- Blaze Burner also works as blasting and smoking source; Lit Blaze Burner variant for haunting (see create_basics/heat).
- Bars, fences, leaves let air through; solid blocks stop it.
- Watch out: blasting works like a furnace and would burn food: smoke it instead.
- Added by other mods (in depth): Dragons Plus (see industry/dragons_plus): ending (dragon breath), freezing (powder snow), sanding (quicksand), coloring (liquid dye), enchanting (Golden Aercloud). Create Nuclear: enriching. Enchantment Industry (see industry/enchanting): salvaging.
- Enriching Campfire [createnuclear:enriching_campfire]: Create Nuclear's fan catalyst (see glossary/fan_catalyst) for bulk enriching. Enriched fire from Enriched Soul Soil (see industry/nuclear).
- Related: (see create_basics/processing), (see create_basics/crushing), (see create_basics/heat)

## create_basics/crushing | Crushing and Ore Doubling
- One ore -> more metal: crush ore blocks mined with Silk Touch, then wash or smelt the crushed result (see materials/ore_doubling). Two crushers: Millstone, Crushing Wheels.
- Millstone [create:millstone]: slow, cheap. Throw items in top, take result with right-click or automation. Good for flour, dyes, bone meal.
- Crushing Wheels [create:crushing_wheel]: used as a pair spinning toward each other. Fast, standard ore crusher. Drop items in at top.
  - shows the build: Crushing Wheels (two gearboxes give wheels opposite spins from one shaft)
- Crushing a raw ore block (e.g. iron ore) = a crushed item, 75% chance of a second, XP nuggets, sometimes cobble. Raw ore items = one crushed item + XP.
- Crushed Raw Iron [create:crushed_raw_iron]: wash (see glossary/washing) in a fan for nine nuggets and chance of redstone. Zinc, copper, gold give gunpowder, clay, quartz instead. Smelting works too.
- Tip: crush ore blocks, not raw items, for bonus crushed ore; then wash (see create_basics/fans) for byproducts.
- Ore locations: (see materials/ore_guide); (see create_basics/materials).
- Choosing a recipe: mods often add several recipes per input; Configurable Machine Outputs picks which a machine uses. Ctrl+right-click a Crushing Wheel, Press (see create_basics/processing) or Millstone with a wrench (see create_basics/tools_wrench); search item, choose recipe; remembered per machine.
- Related: (see create_basics/fans), (see materials/ore_guide), (see create_basics/materials)

## create_basics/item_logistics | Moving Items
- Items move by belts, chutes, funnels, arms. Machines work on items on the belt/depot in front of them.
- Mechanical Belt [create:belt_connector]: needs rotation (see create_basics/rotation); carries items and entities. Two shafts linked by belt; machines can work on items riding it.
- Depot [create:depot]: stationary belt tile; items sit on it, press/deployer/spout above can work on them.
- Brass Funnel [create:brass_funnel]: pulls from/pushes into an inventory. Place normally to pull, sneaking to push. Has filter slot (see glossary/filter_slot). Andesite Funnel moves items unfiltered.
- Chute [create:chute]: drops items down; needs no power. Smart Chute adds filter slot and extraction size.
- Mechanical Arm [create:mechanical_arm]: assign input/output blocks by right-clicking them while holding the arm, then place it. Moves items between any inputs and outputs in range.
- Filters go in funnel, tunnel or smart chute filter slots: List Filter [create:filter] matches chosen items; Attribute Filter [create:attribute_filter] matches by property (food, enchanted, etc.).
- Brass Tunnel [create:brass_tunnel]: covers a belt, splits items to sides or sorts by filter. Andesite Tunnels split one item at a time, no filters. (Brass funnels/tunnels can filter; andesite can't.)
- Storage: Item Vault = big multi-block (see glossary/multiblock) chest connecting to belts and funnels. Threshold Switch (see create_basics/redstone_and_control) reads container fullness -> redstone.
- Package delivery networks: (see create_basics/packages).
- Related: (see create_basics/packages), (see create_basics/deployer), (see home/storage)

## create_basics/fluids | Fluids, Pipes and Pumps
- Fluids flow through Fluid Pipes driven by a Mechanical Pump. Without a pump pipes only connect, don't move liquid.
- Mechanical Pump [create:mechanical_pump]: arrow shows flow direction, not rotation; wrench (see create_basics/tools_wrench) flips. Pulls from pipe behind, pushes into pipe in front, up to 16 blocks.
- Fluid Tank [create:fluid_tank]: stores fluid; join up to 3 wide and many layers tall. In survival cannot fill by hand: use pipes, spouts or drains.
- Spout [create:spout]: fills items beneath (buckets, potions) from fluid supply. Items sit on depot or belt (see create_basics/item_logistics) under it.
- Item Drain [create:item_drain]: empties fluid out of items (buckets, bottles) into pipes. Right-click to pour from hand.
- Big supplies: pipe end in a pool pulls source blocks; vanilla water pool with two sources refills itself.
- Tip: Hose Pulley lowered into a large enough body (e.g. ocean) gives/drains without running out.
- Hose Pulley [create:hose_pulley]: fills/drains whole pool from top down to hose end. Needs rotation (see create_basics/rotation) to lower.
- Pipe Wrench [cmparallelpipes:pipe_wrench] (Create More: Parallel Pipes): fluid pipe normally joins every pipe beside it; use wrench on a pipe to lock connections like an encased (see glossary/encased) pipe so pipes run side by side; use again to unlock. Holding wrench + locked pipe shows outlines; use on them to add/remove a connection. A pipe keeps at least two open ends.
  - shows recipe cmparallelpipes:pipe_wrench
- Related: (see create_basics/processing), (see create_basics/heat), (see industry/liquid_fuel)

## create_basics/deployer | Deployer
- Deployer [create:deployer]: mechanical player hand. Given rotation it places blocks, uses items, activates blocks, harvests, attacks mobs. Acts on position two blocks in front.
- Right-click its front to give it an item. Wrench (see create_basics/tools_wrench) switches right-click / left-click mode.
- In automation it applies items to other items: held item over belt or depot (see create_basics/item_logistics) item = assembly step (see create_basics/brass_and_assembly). How machines add cogwheels, sand paper [create:sand_paper], ingredients.
- Also feeds Blaze Burners fuel (see create_basics/heat).
- Negative redstone pulse triggers one cycle. On a contraption (see create_basics/contraptions), deployers act at every place they pass, pulling from storage on the machine.
- Loop deployer, press etc. to build precision mechanisms (see create_basics/brass_and_assembly).
- Related: (see create_basics/brass_and_assembly), (see create_basics/contraptions), (see create_basics/heat)

## create_basics/contraptions | Contraptions
- Contraption = group of blocks moved as one (drill platform, self-driving farm, train car). Glue (see glossary/super_glue) and chassis decide what comes along; a mover decides how. Drills, saws, deployers on it work as it moves.
- Super Glue [create:super_glue]: bonds blocks into a shape; blocks that hang on others need none; click two corners to define an area.
- Linear Chassis [create:linear_chassis]: glues a row along its axis. Radial Chassis grabs a disc around its axis. Both work with wrench (see create_basics/tools_wrench) settings and chassis ranges.
- Movers: Piston (pushes along a line); Rope Pulley (up/down); Gantry (slides on a shaft); Bearing (see glossary/bearing) (spins); Cart Assembler (rides a minecart).
- Mechanical Bearing [create:mechanical_bearing]: rotates a structure: windmills (see create_basics/power_sources), rotating drills, turntables. Clockwork Bearing makes clock hands.
- Builds that move: any build can have a moving part (sails that turn, door/drawbridge swinging on a bearing, turntable, wall a piston slides aside, lift on a rope pulley, gantry carrying a drill over a field). Most of a build stands still; make the part move that has a job moving.
- What comes along: the block touching the mover's face; blocks side by side inside one Super Glue area; blocks that hang on others (torches, doors, ladders, carpets); blocks on a chassis's sticky faces; a piston also pushes the whole line in front of it. At most 2048 blocks.
- Speed: Bearing 6 degrees/s per RPM (full turn 1.9 s at 32 RPM). Piston, pulley, gantry: RPM / 25.6 blocks/s, 9.8 at most. Bearings, pistons, pulleys take 4 SU per RPM.
- Starting and steering: Bearing: right-click it with an empty hand to start it; it turns while its shaft turns. Piston, pulley, gantry start on their own and run until the end of their travel. A Gearshift with a lever, in the line that drives it, sends it back; a Clutch stops it.
- Watch out: a turning part passes straight through blocks in its circle. A sliding part stops at the first solid block in its path; drills and saws break theirs as they go. A glue area takes every block in it that touches the part, so keep it off what stays put.
- In depth: piston needs one extension pole behind it per block of reach, up to 64. Run back, a plain piston leaves its blocks out; a sticky one pulls them back. Rope pulley reaches 384 blocks down. Chassis grabs 8 blocks (wrench sets 1 to 16). Stopping a bearing at a quarter turn takes a Sequenced Gearshift.
- Working parts: Mechanical Drills, Saws, Harvesters, Ploughs do their job to whatever they pass. Deployers (see create_basics/deployer) and funnels (see create_basics/item_logistics) on the structure pull from/push into storage that moves with it. Contraption storage = chest or vault attached to the structure.
- Mechanical Drill [create:mechanical_drill]: breaks blocks in front, also while moving. Harvesters cut crops, Ploughs till soil.
- Bigger: Create Aeronautics builds flying ships (see flight/airships); trains are contraptions too (see transport/trains).
- Related: (see flight/airships), (see transport/trains), (see create_basics/deployer)

## create_basics/brass_and_assembly | Brass and Assembly
- Brass = second metal tier, copper + zinc mixed in a heated basin (see create_basics/heat). Brass casings and parts open up funnels with filters (see create_basics/item_logistics), tunnels, mechanical arms, more.
- Brass Ingot [create:brass_ingot]: copper + zinc in mixer over heated burner (see create_basics/processing).
- Precision Mechanism: made by sequenced assembly (see glossary/sequenced_assembly): one item goes through a chain of steps on a belt, repeated several times; becomes an "incomplete" item in between. Steps use deployers (see create_basics/deployer) and a press.
- Precision Mechanism [create:precision_mechanism]: needed for many advanced machines in several mods. Recipe has small chance of giving other items. Loop in JEI (see start_here/tools).
- Mechanical Crafter [create:mechanical_crafter]: arrange several in a grid (see glossary/mechanical_crafting), connect paths to craft any recipe, even ones too big for the mixer. Fill every slot, then it crafts. Needs rotation (see create_basics/rotation). Wrench (see create_basics/tools_wrench) arranges paths; slot covers leave holes in the grid. Works with packages too (see create_basics/packages).
- Related: (see create_basics/processing), (see create_basics/deployer), (see create_basics/heat)

## create_basics/packages | Logistics: Packages
- Create 6 logistics: items packed into addressed Packages and delivered between points; runs on belts (see create_basics/item_logistics), chain conveyors, frogports.
- Packager [create:packager]: attaches to chest/machine; redstone power -> packs items; given a package -> unpacks into inventory. (A Packager on a chest packs items into addressed packages.)
- Package Frogport [create:package_frogport]: sends/receives packages; gives each port an address; chain conveyor links ports. (Frogports send/receive over chain conveyors or belts.)
- Chain Conveyor [create:chain_conveyor]: carries packages, relays rotation like shafts (see create_basics/transmission). Frogports attach for long-distance delivery.
- Stock Link [create:stock_link]: connects packagers to one network. Range unlimited, but packages still need transport.
- Stock Ticker [create:stock_ticker]: orders items from the whole network. A blaze burner (see create_basics/heat) or seated mob in front acts as the Stock Keeper you right-click.
- Redstone Requester [create:redstone_requester]: orders a preset set on each redstone pulse.
- Factory Gauge [create:factory_gauge]: keeps stock at a target amount by requesting ingredients, down to full recipes and crafting setups.
- Add-ons in this pack (in depth): Factory Logistics (network links, fluid gauges, a Bottler, jars for fluids); Mobile Packages (Robo Bee Port sends packages to players, portable stock ticker).
- Related: (see create_basics/item_logistics), (see create_basics/brass_and_assembly), (see home/storage)

## create_basics/redstone_and_control | Redstone and Displays
- Create adds ways to read/control machines without wires.
- Redstone Link [create:redstone_link]: wireless; pairs share a frequency (see glossary/redstone_frequency) set by two items; powered transmitter powers receivers anywhere.
- Display Link [create:display_link]: reads data from a block (e.g. stock level), writes to a Display Board or Nixie Tubes.
- Smart Observer [create:content_observer]: detects items in front of it, in an inventory, or on a belt.
- Threshold Switch [create:stockpile_switch]: outputs redstone based on how full a container is.
- Related: (see create_basics/item_logistics), (see create_basics/packages)
