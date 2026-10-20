<!-- sources sha256:aba6bdb81e2779378740a5666ac8541bde974df23508cd0cd4fa21f502f3dd94 -->
# Industry and Power (industry)
Rotation vs FE, Crafts & Additions electricity, TFMG, liquid fuel burners, nuclear, ore excavation, chunk loaders, alloys, enchanting, fan effects, cannons.

## industry/power_compared | Rotation vs Electricity
- Two power kinds: Rotation (RPM (see glossary/rpm) + stress units (see create_basics/stress)) drives Create machines via shafts/cogs; Forge Energy (FE) = generic electricity shared by many mods. They meet only through converters
- Rotation -> FE: Alternator (Crafts & Additions). FE -> rotation: Crafts & Additions Electric Motor, RPM you set; higher RPM draws more FE (see industry/electricity)
- Alternator [createaddition:alternator]: needs at least 32 RPM; output grows with speed; take FE out with a connector set to Pull
- Electric Motor [createaddition:electric_motor]: compact rotation source; scroll on its back to set RPM; FE used depends on RPM
- Rotation sources: TFMG engines burning oil products (see industry/tfmg_oil); Create Nuclear reactor (see industry/nuclear); Create's own water, wind, steam (see create_basics/power_sources)
- Chain: fuel -> rotation -> Alternator -> FE
- Kinetic Battery [create_connected:kinetic_battery]: stores rotation, not FE; charges from spare stress, releases when redstone-powered
- TFMG (see industry/tfmg_engines) runs own circuits (voltage, resistance, cable hubs), separate from FE; its cable connectors can switch FE import/export so it can still meet FE machines
- TFMG also has an Electric Motor: not the same block as Crafts & Additions one
- Other FE users in pack: Create Propulsion Ion Thruster, Flight Control vector ion thrusters, Create Submarine Electrolyzer (see flight/airships) (see ocean/submarines)
- Related: Electricity (see industry/electricity), TFMG Engines and Power (see industry/tfmg_engines), Nuclear Reactors (see industry/nuclear), Stress (see create_basics/stress)

## industry/electricity | Electricity
- Create Crafts & Additions adds FE: generate from rotation (see create_basics/rotation), carry on wires between connectors, spend in motors, pumps, chargers; compare (see industry/power_compared)
- Alternator [createaddition:alternator]: rotation -> FE from 32 RPM (see glossary/rpm) up; faster = more; place a connector on it set to Pull
- Small Connector [createaddition:connector]: links FE blocks with wire; wrench (see create_basics/tools_wrench) cycles Push, Pull, None; up to 4 wires
  - Pull: takes FE out of attached block (producers); Push: feeds FE into it (consumers); None: only relays between wires, can sit on any block
- Wire spools [createaddition:copper_spool]: right-click two connectors with filled spool to wire; Empty Spool on both ends removes wire and returns spool
  - Copper Spool: low current, fits Small Connectors; Gold and Electrum spools: high current, required by Large Connector
- Large Connector [createaddition:large_connector]: high current; needs Gold or Electrum spool; up to 6 wires; carries more; good for trunk lines between generators and storage
- Accumulator [createaddition:modular_accumulator]: multiblock (see glossary/multiblock) battery; configure one input connector and one output; also pushes FE into blocks on its top and bottom faces
- Tesla Coil [createaddition:tesla_coil]: charges FE items placed under it; with redstone signal zaps nearby mobs and players; some charging recipes make new items: check JEI (see start_here/tools)
- Rolling Mill [createaddition:rolling_mill]: takes rotation, rolls ingots and plates into rods and wires; automate with belt and two funnels
- Rods and wires feed spools, motors, connectors. Electrum (Crafts & Additions alloy) needed for high-current gear; recipe in JEI. Barbed Wire hurts anything walking through
- Servo Motor: turns attached blocks to exact angle from redstone; needs FE. Electric Pump: FE-powered fluid pump
- Burning fuel without rotation (see industry/liquid_fuel)
- Shared with TFMG: TFMG uses Crafts & Additions' Copper Wire (its recipes take it; leftover TFMG copper wire converts 1:1 in crafting grid). TFMG keeps own Copper Spool: only TFMG spools fit its Winding Machine and lay TFMG cable (see materials/shared_metals)
- This version of Crafts & Additions has only multiblock Accumulator; older single-block one removed
- Portable Energy Interface [createaddition:portable_energy_interface]: carries FE storage on moving contraption; swaps FE with a stationary one as it passes

## industry/tfmg_steel | TFMG Steel
- TFMG (The Factory Must Grow) = heavy industry on Create; steel is gate to most of it. Main route: blast furnace fed hot air, cast into ingots in a basin
- First steel: heated mixer, 1 per iron ingot. Blast furnace gives 2 per raw iron, fed hot air by Blast Stove. Pump Blast Stove's CO2 out or it stalls
- Blast Stove [tfmg:blast_stove]: Hot Air from air + fuel; builds up Carbon Dioxide that stalls it; pump out to an Exhaust or Smokestack. Tip: burns furnace gas or creosote, so furnace can feed itself
- Blast Furnace [tfmg:blast_furnace_output]: cylinder of Fireproof Bricks around Output with one Hatch for hot air; Reinforcements speed it up; items in at top
  - Inputs: crushed raw iron (see materials/ore_doubling) (or raw iron) + flux = Limesand [tfmg:limesand]. Outputs: molten steel, molten slag, furnace gas
- Coke Oven [tfmg:coke_oven]: cooks coal slowly into Coal Coke, creosote, CO2; build long rows; coke = long-burning fuel and base of graphite electrodes
- Casting Basin [tfmg:casting_basin]: molten steel poured in sets into Steel Ingot (90 mB (see glossary/mb) each); also casts Big Cannons (see industry/cannons) metals
- TFMG's steel is the pack's steel: Alloyed, Create Nuclear, Big Cannons steel recipes now give it; heated mixer (see create_basics/heat) supplies first steel at 1 per iron ingot; blast furnace 2 per raw iron
- Old ingots from those mods convert in crafting grid (see materials/shared_metals)
- Steel unlocks pipes, tanks, casings, machines. Next: TFMG Oil (see industry/tfmg_oil)
- Second steel route: chemical vat with three Graphite Electrodes, from crushed raw iron + flux + coke dust, in large Fireproof Vat; exact sizes in JEI (see start_here/tools)

## industry/tfmg_oil | TFMG Oil and Fuels
- Chain: find crude oil, pump up, split in distillation tower, burn fractions or cook into plastic and rubber
- Surface Scanner [tfmg:surface_scanner]: powered from below; 5x5 grid shows nearby chunks (see glossary/chunk); chunks with oil deposit glow
- Pumpjack [tfmg:pumpjack_base]: Industrial Pipe from deposit to surface, base on top, then hammer parts (glued (see glossary/super_glue) together) and a crank driven through a Machine Input
- Distillation Tower [tfmg:steel_distillation_controller]: steel tanks, controller beside them, up to 6 outputs separated by Industrial Pipes; heat under tanks; oil goes into controller
- Crude splits into heavy oil, diesel, kerosene, naphtha, gasoline, LPG. Heavy oil distills again into more diesel and kerosene plus lubrication oil. Flarestack [tfmg:flarestack] burns whatever you do not need
- Large Engine [tfmg:large_engine]: burns diesel, kerosene, naphtha, furnace gas for rotation (see industry/tfmg_engines)
- Firebox [tfmg:firebox]: heats machines above like Blaze Burner; burns LPG, butane, propane, diesel, naphtha, kerosene; CO2 must be pumped out (see create_basics/heat)
- Plastic: naphtha in heated vat -> ethylene and propylene; either -> molten plastic; Casting Basin -> Plastic Sheets
- Rubber: heavy oil + sulfur
- Fuels also burn in Blaze Burners: piped diesel, gasoline, kerosene, naphtha, heavy oil heat one; napalm superheats it; Straw burner also takes crude oil and creosote (see industry/liquid_fuel)
- Pipes and pumps: Fluids (see create_basics/fluids)

## industry/tfmg_engines | TFMG Engines and Power
- Engines make rotation from fuel; electricity via TFMG's own circuit system; both plug into normal Create networks (see glossary/kinetic_network)
- Regular Engine [tfmg:regular_engine]: assembled (see glossary/assemble) from a body: click in its parts first (goggles name next one), then matching Engine Cylinders; click with a Shaft to set rotation output. Lubrication oil (Oil Can [tfmg:oil_can] or bucket) and cooling fluid optional; each cuts fuel use by 30%
  - cylinder sets fuel: Engine Cylinder = gasoline, kerosene, naphtha; Diesel Engine Cylinder = diesel; Autogas Engine Cylinder = LPG; Simple Engine Cylinder = creosote or furnace gas
  - Watch out: burnt fuel fills 8,000 mB CO2 tank; pump out to an Exhaust or engine stops
- Large Engine [tfmg:large_engine]: burns less refined fuel (diesel, kerosene, naphtha, furnace gas); needs air in and CO2 pumped out to an Exhaust; Shaft goes on like a steam engine
- Radial and Turbine Engines are other types. Engine Controller linked to engine sets speed and direction. Fuel from TFMG Oil (see industry/tfmg_oil); engine rotation is ordinary and obeys Stress (see create_basics/stress)
- Generator [tfmg:generator]: source of TFMG electricity; source gives Voltage and Power; devices have Resistance; current = voltage / resistance
- Cable Hubs [tfmg:copper_cable_hub]: wire hubs and insulators with copper, aluminum or constantan spools; Voltmeter (Electric Gauge) shows what a part carries
- TFMG cable connectors can import and export FE, so TFMG circuits can touch FE machines; its wires separate from Crafts & Additions wires (see industry/power_compared)
- Blocks share a group: members of one group split the voltage, the rest keep all of it; change group with Configuration Wrench. Diode, Potentiometer, Switch or Transformer starts a subnetwork
- Large Transformer [tfmg:large_coil]: built in place: two wound Large Coils side by side, then click one with Laminated Magnetic Alloy Block, Block of Steel, Lubrication Oil bucket; mining returns both coils (turns kept) and the steel

## industry/liquid_fuel | Burning Liquid Fuel
- Blaze Burner normally eats solid fuel; two mods let it burn piped fluids -> oil products and biofuels reach Create heat (see create_basics/heat)
- Create: Liquid Fuel [create:blaze_burner]: plain Blaze Burner takes piped lava, Seed Oil, Bioethanol, Peanut Oil, TFMG diesel, gasoline, kerosene, naphtha, heavy oil, napalm
  - Bioethanol, Peanut Oil, TFMG napalm superheat (blue flame); others only heat. Seed Oil and Bioethanol from Crafts & Additions; Peanut Oil from Garnished (see food/garnished). Peanut Oil burns 5,000 ticks (see glossary/tick) per bucket in either burner
- Blaze Burner with Straw [createaddition:straw]: Crafts & Additions' version; add a Straw to a Blaze Burner; takes fuel from buckets or pipes
  - TFMG diesel, gasoline, crude oil, creosote all burn in Straw burner via shared fluid tags (see glossary/tags). Crude oil and creosote need Straw burner; TFMG's other fuels work in plain burner too
- Straw [createaddition:straw]: craft to convert a Blaze Burner. Biofuel chain: crops -> Biomass, seeds -> Seed Oil, then Bioethanol; check JEI (see start_here/tools)
- Watch out: Blaze Burner, with or without Straw, makes smog while burning fuel (see survival/pollution)
- Solid-fuel option: Biomass Pellets [createaddition:biomass_pellet]
- Straw burner burn time per 1000 mB (see glossary/mb): diesel, gasoline, biodiesel 24000 ticks; lava 20000; crude oil 9600; ethanol 8000; creosote and plant oil 4800. Bioethanol (biofuel tag) is superheated
- Related: TFMG Oil and Fuels (see industry/tfmg_oil), Electricity (see industry/electricity), Blaze Burner and Heat (see create_basics/heat)

## industry/nuclear | Nuclear Reactors
- Create Nuclear: uranium, thorium, multiblock (see glossary/multiblock) reactor, radiation. Reactor outputs ordinary rotation through a shaft; plugs into any Create network (see glossary/kinetic_network). Pack's source of lead
- Uranium chain [createnuclear:uranium_powder]: crush (see create_basics/crushing) raw uranium to powder, mix (see glossary/mixing) into liquid, compact (see glossary/compacting) to Yellowcake; thorium similar; check JEI (see start_here/tools)
- Enriching Campfire [createnuclear:enriching_campfire]: green Enriching Fire blown through by fan (see create_basics/fans) turns Yellowcake into Enriched Yellowcake; make it from Enriched Soul Soil
- Fuel rods [createnuclear:uranium_rod]: Enriched Yellowcake pressed in Mechanical Crafter -> Uranium Rods (fuel); Graphite Rods (from graphene and steel) = cooling rods
- Reactor build: Reactor Casing (Brass Casing + steel) forms T1, T2 or T3 tower per Ponder pattern; place Controller, then Rod Input, Fluid Input, Output, optional Alarm in casing spots
- Reactor Controller [createnuclear:reactor_controller]: right-click with filled Reactor Blueprint [createnuclear:reactor_blueprint_item] to start; need fuel and cooling rods plus water or liquid nitrogen in Fluid Input
  - Watch out: too hot with too little cooling -> melts down and explodes; put an Alarm nearby
  - two Rod Inputs needed: one heating rods, one cooling; shaft off Reactor Output for rotation
- Radiation [createnuclear:default_anti_radiation_chestplate]: hurts; irradiated mobs exist; Anti Radiation armor protects; dyeable with cloth
- Steel here: coal dust + iron ingot mixed in heated mixer (see create_basics/heat); gives TFMG steel; reactor accepts any steel (see materials/shared_metals)
- Create Nuclear's lead is the pack's only lead. Rotation to FE (see industry/power_compared)

## industry/ore_excavation | Ore Excavation
- Create Ore Excavation: hidden underground ore veins; locate vein, put Drilling Machine on it, produces raw ore while it has rotation (see create_basics/rotation). Veins here are finite and run out
- Ore Vein Finder [createoreexcavation:vein_finder]: right-click to scan; reports what is in your chunk (see glossary/chunk), nearby, or just traces further away
- Drilling Machine [createoreexcavation:drilling_machine]: needs rotation and a Drill (Iron, Diamond, Netherite); output depends on vein, with bonus items by chance
- Sample Drill [createoreexcavation:sample_drill]: pins down vein details into an Ore Vein Atlas; Atlas also filters Vein Finder to a chosen vein
- Deposits add-on supplies veins: coal, copper, iron, zinc, gold, redstone, lapis, diamond, emerald, quartz, glowstone; Nether gold, Nether quartz, ancient debris. Ancient debris needs Netherite Drill and lava; check Atlas or JEI (see start_here/tools)
- Create Ore Excavation Deposits replaces single-chunk veins with multi-chunk deposits: blobs of 2 to 40 chunks, each one ore
  - farther out = bigger and richer; core chunk richest, edges fade; each ore keeps to its biomes (e.g. iron in mountains and taiga)
  - on Xaero's World Map (see home/maps) a deposit is coloured chunks fading as it runs dry; hover for units left
  - /coedeposits share offers your deposit to others with an "Add to map" click
  - deposits show as markers on Xaero's maps (see home/maps), without a chat line each
- Pack changes gem and Nether drills: diamond, emerald, quartz, glowstone veins give the gem or dust itself, small chances of extras. Ancient debris gives Netherite Scrap; each cycle uses 500 mB lava with Netherite Drill
- Fluid Well Extractor [createoreexcavation:extractor]: pulls fluid from a vein instead of items; Deposits include Underground Spring yielding water
- Veins give raw ore, so crushing and washing still apply: Crushing (see create_basics/crushing), Ore Guide (see materials/ore_guide). Drills need stress (see create_basics/stress). Lead and TFMG ores are not in vein list

## industry/power_loader | Chunk Loaders
- Create: Power Loader keeps chunks (see glossary/chunk) loaded so machines work while away; cost is rotation: spinning shaft keeps chunk awake
- Empty loaders [create_power_loader:empty_andesite_chunk_loader]: come dead; right-click a Ghast with one to capture it; once powered, ghasts light the portal cores
- Andesite Chunk Loader [create_power_loader:andesite_chunk_loader]: loads own chunk while spun fast enough; slower rotation fails; comparator signal while active
- Brass Chunk Loader [create_power_loader:brass_chunk_loader]: loads 1x1, 3x3 or 5x5 chunks; bigger = faster rotation needed; set range on value panel (see glossary/value_panel)
- Brass loaders work on moving contraptions (always 3x3, no rotation needed once assembled (see glossary/assemble)) and Train Stations (area loaded only while a train is there) (see transport/trains) (see create_basics/contraptions)
- On power loss, short configurable (see glossary/config) delay before unloading, so brief stalls are fine
- Loaded chunks still tick (see glossary/tick) machines; big loader grid costs performance

## industry/alloyed | Alloyed: Steel and Bronze
- Alloyed adds two alloys and the forge; steel and bronze become tools, armor, building blocks; both shared with other mods (see materials/shared_metals)
- Alloy Forge [alloyed:forge]: forges Alloyed tools, armor, parts. Steel and bronze themselves come from heated (see create_basics/heat) Create mixer: bronze from copper, three zinc nuggets, cinder flour (see connections/balance)
- Steel [tfmg:steel_ingot]: Alloyed's recipes now give TFMG steel; forge into pickaxes, swords, armor, horse armor, shears, fishing rods; any steel works. Steel Fishing Rod fishes the old way, no timing game (see fishing/rods). Farmer's Delight (see food/farmers_delight) gets a steel knife
- Bronze [alloyed:bronze_ingot]: Alloyed's bronze is the pack's bronze; Big Cannons and TFMG bronze recipes give it too; used for casing, sheet, blocks; bronze blocks oxidize like copper, can be waxed
- Bronze Bell [alloyed:bronze_bell]: playable block; rings when hit or redstone-powered; Wrench (see create_basics/tools_wrench) tunes it; Deployer (see create_basics/deployer) can ring it
- Casings/building blocks: Steel Casing and Bronze Casing with encased (see glossary/encased) shafts and cogwheels (more tiers); steel catwalks, mesh fences, doors
- Old Alloyed steel ingots convert to TFMG steel in crafting grid (see industry/tfmg_steel)

## industry/enchanting | Enchantment Industry
- Create: Enchantment Industry turns experience into fluid Liquid Experience; automates enchanting, disenchanting, book copying
- Experience Hatch [create_enchantment_industry:experience_hatch]: right-click to store own XP; sneak-click to take XP out; filter slot (see glossary/filter_slot) lets other mods' fluids count as XP
- Other XP sources: Mechanical Grindstone over Grindstone Drain crushes Experience Nuggets into liquid; Crushing Wheels (see create_basics/crushing) and Deployers (see create_basics/deployer) drop nuggets when killing mobs; Experience Lantern collects nearby orbs
- Blaze Enchanter [create_enchantment_industry:blaze_enchanter]: enchanting table fed Liquid Experience; set enchant level on panel (see glossary/value_panel); template item limits result to that item's enchantments
- Blaze Forger [create_enchantment_industry:blaze_forger]: anvil with no repair cost; merges enchantments; applies or extracts them using Enchanting Templates
- Printer [create_enchantment_industry:printer]: copies written books and enchanted books, names items, copies train schedules, clipboards, package addresses; recipes in JEI (see start_here/tools)
- Super enchanting: feed a Blaze a Cake o' Enchanting -> goes past vanilla level limits. Watch out: comes with lightning strikes; keep a Lightning Rod nearby
- Apotheosis blocks (Affix Augmentor, Gem Cutter, Blaze Composer) need Apotheosis, which is not in this pack

## industry/dragons_plus | Dragons Plus Fan Effects
- Create: Dragons Plus adds Fan (see create_basics/fans) effects: blow air through a catalyst (see glossary/fan_catalyst), items in stream processed in bulk
- Freezing (Powder Snow): ice -> packed ice -> blue ice; magma cream -> slime ball; blaze rod -> breeze rod
- Ending (Dragon Head or Dragon's Breath): cobblestone -> end stone; apple -> chorus fruit; leather -> phantom membrane
- Dragon's Breath [create_dragons_plus:dragon_breath_bucket]: real fluid, usable in cauldrons; turns to End Stone where it meets lava
- Coloring: Liquid Dye (mix a dye with water) colors items, mobs, armor it touches; fans blowing through it color items in bulk; dye meeting lava makes matching concrete
- Sanding (quicksand): sandpaper recipes in bulk
- Enchanting (Golden Aercloud): runs Aether Altar recipes, incubates Moa Eggs (see exploration/dimensions)
- Fragile Fluid Tank [create_dragons_plus:fragile_fluid_tank]: breaks on impact, splashes fluid onto surroundings; Fluid Hatches let tanks be filled/emptied by hand
- Create Connected has matching fan catalyst blocks (see home/create_decor)

## industry/cannons | Big Cannons
- Create Big Cannons: artillery built as contraptions: cast or built-up barrels, breeches, mounts, loaders, munitions. Ponder (see start_here/tools) explains each step; this page covers only pack connections
- Cannon Mount [createbigcannons:cannon_mount]: rotating base aiming a cannon; cannons assembled (see glossary/assemble) onto it as contraption
- Cannon Loader [createbigcannons:cannon_loader]: contraption part pushing charges and shells in; cartridges and powder charges set range, shells set effect
- Basin Foundry Lid [createbigcannons:basin_foundry_lid]: melts bronze, cast iron, nethersteel in heated basin (see create_basics/heat) for casting cannon parts
- TFMG (see industry/tfmg_steel) ties in: its Casting Basin casts Big Cannons bronze, cast iron, nethersteel; its vat with graphite electrodes turns netherite scrap and molten steel into nethersteel
- Steel: Big Cannons' own steel recipes (mixing iron and coal, compacting molten steel) now give TFMG steel (see industry/tfmg_steel)
- Bronze comes from Alloyed (see industry/alloyed)'s mixer recipe (Big Cannons' cheaper bronze mixes are off)
- Old Big Cannons steel and bronze convert in crafting grid (see materials/shared_metals)
- Cannons are built from contraptions (see create_basics/contraptions), so mountable on moving builds
- Watch out: handle with care, shells destroy blocks
