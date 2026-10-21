<!-- sources sha256:f34af7a6de252af7fb4b43e713799cf47b4fa72715b719dd8d16148322043bbb -->
# The Ocean (ocean)
Create Deep Seas (submarines, High Seas surface ships), Aquatic Ambitions, sea life mods, Aquamirae, diving, structures.

## ocean/submarines | Submarines
- Create Deep Seas adds submarines: Create-style contraption (see create_basics/contraptions), one solid physical vessel, built on Sable (see glossary/sub_level) and Aeronautics tech like airships (see flight/airships). Dives by changing own weight.
- Floats until Ballast Tanks fill with sea water. Pump in -> heavier, sinks; pump out -> rises.
- Ballast Vent [create_submarine:ballast_vent]: the sea valve; at least one face must touch the sea water. Every face takes a pipe. Pipe vent, pump and tanks together.
- Ballast Tank [create_submarine:ballast_tank]: holds the water that weighs the ship down. Read capacity with Engineer's Goggles (see create_basics/tools_wrench).
- Each empty tank lifts about 75 units of ship mass (`ballastLiftPerTank` in Deep Seas config (see glossary/config)). Plank ~1, iron block ~3.5, machines more; a small iron sub can need a dozen tanks.
- Tip: fit a few spare tanks; a sub with barely enough lift sinks slowly even with every tank empty.
- Onboard Computer [create_submarine:command_sub]: depth autopilot. Click its screen to set target depth and speed; it drives the pumps to hold depth. With a Sonar on board it can also follow the sea floor and keep the keel clear.
- Pump Controllers on the same ship are found automatically; pipe one between vent and tanks. More controllers or faster rotation = quicker dives.
- Sonar [create_submarine:sonar]: maps floor, rocks, ships and mines around you on the Computer's screen.
- Pumps and thrusters run on Create rotation (see create_basics/rotation) and fluids (see create_basics/fluids).
- Tip: press W over any Deep Seas block for its Ponder (see start_here/tools).
- Submarine Propeller [create_submarine:submarine_propeller]: strong in water, useless in air. 4 in a 2x2 square facing one way merge into one big propeller.
- Water Thruster [create_submarine:water_thruster]: uses water and rotation for thrust; more pressure and speed = faster.
- Hull strength, oxygen, airlocks: Hulls and Pressure (see ocean/hulls_and_pressure). Surface ships and sails: Sailing (see ocean/sailing). Where to dive: Underwater Places (see ocean/underwater_places).
- Deep Seas also holds an unfinished Create Abyss dimension; it only switches on in development builds, so it is not in this pack.
- Related: Diving Gear (see ocean/diving)

## ocean/hulls_and_pressure | Hulls and Pressure
- Deep water crushes blocks. Every block has a maximum depth; a ship is only as strong as its weakest block. Past its limit a block gives way: above a certain depth the room just floods through the hole; far deeper, a failing block implodes the whole ship.
- Windows are the usual weak point: use pressurized glass. Glass, Iron and Copper Pressurized Glass resist pressure; plain glass does not.
  - Iron Pressurized Glass [create_submarine:iron_pressurizer]; shows item create_submarine:iron_pressurizer
- Goggles (see create_basics/tools_wrench) on a block show its hull tier: fragile, standard or reinforced.
- Pressure Barometer [create_submarine:barometer]: shows depth and pressure, compares ship pressure to what the weakest block can take. Attach a Display Link (see create_basics/redstone_and_control) to broadcast depth and hull state to a Display Board.
- Industrial Alarm flashes and sounds faster every 5 m you sink past the safe depth.
- Tuning: Deep Seas config (see glossary/config) has a max depth multiplier scaling every block; per-block depths are in config/submarine_hull.json.
- Dry interior needs a sealed hull + Electrolyzer (water -> oxygen) + Oxygen Diffuser (spreads it through the ship; give it a redstone signal).
- Electrolyzer [create_submarine:electrolyzer]: accepts FE power (see industry/electricity), or put a Shaft in its module slot to spin it with plain Create rotation.
- Airlock to leave the ship underwater: two Decompression Chambers joined by pipes and a pump (see create_basics/fluids). Reverse the pump to dry it again.
  - Decompression Chamber [create_submarine:decompression_chamber]: airlock valve; pipe water in at the back to flood a room, or out to empty it.
  - Watch out: flood the airlock before opening the outer door deep down, or the sea crushes it in.
- Create: Deep Seas, Lava Fix: players inside a sealed submarine submerged in lava no longer take fire or lava damage (interior is dry). Nothing to set up.
- Back to Submarines (see ocean/submarines).

## ocean/sailing | Sailing and Boats
- Create High Seas (part of Deep Seas): surface ships; same kind of physical vessel as a submarine (see ocean/submarines) but they float on top and travel by wind, engine or oar.
- A boat floats on the air in its hold: hull with a floor and four walls, open on top or not. A hole below the waterline floods the hold and it stops giving lift.
- Sail [create_high_seas:white_sail]: all 16 dyes. Ship needs enough canvas for its size; big ships need more sails to get going.
- Wind Vane [create_high_seas:wind_vane]: shows wind direction; steer along the arrow for top speed. Goggles (see create_basics/tools_wrench) show alignment as a percent.
- Wind: baseline, gusts, extra strength in rain and thunderstorms. Terrain upwind can block it. Sailing straight into the wind keeps only part of your speed.
- Rudder at the stern tells sails which way is forward; mount it on a Swivel Bearing (see flight/ship_building) to steer.
- Boat Engine [create_high_seas:boat_engine]: compact fuel-burning propeller; mount at the stern with the propeller in the water. Burns fuel (open fuel screen to see what it takes).
  - Right-click: take the tiller; sneak: let go. Sneak-click: fuel screen. Hold use at the helm and move the mouse to set a speed limit 0 to 15.
- Anchor [create_high_seas:anchor]: light in the air, one ton once it touches the bottom. Use a Steel Cable on it to connect a rope winch.
- Oars give a stroke forward when used near water. Buoy: towable float you can ride and skip across the surface. Dyed Floaters from Deep Seas keep a plain boat on the surface.
- Seaglide [create_high_seas:seaglide]: handheld twin fans for swimming; runs on your hunger, not fuel. Break the surface at speed to leap out.
- Steel Cables and Pulleys let a ship ride a cable. Arresting Hook under a ship grabs a cable and brakes it (like a carrier landing). Underwater Mine on a contraption (see create_basics/contraptions) detonates when entities or boats come close.
- See also: Submarines (see ocean/submarines), Airships (see flight/airships), Trains (see transport/trains)

## ocean/conduits | Conduit Cages
- Create: Aquatic Ambitions ties Minecraft ocean items to Create. Centrepiece: Conduit Cage, a compact conduit fed a fluid through its bottom face; a fed cage is "awakened".
- Conduit Cage [create_aquatic_ambitions:mechanical_conduit]: awakened with water gives Conduit Power nearby; other fluids give other effects. Effects listed in the item tooltip.
- Mode slot picks who is affected: everyone, players, friendly mobs, players and friendly mobs, or monsters.
- Awakened cages also work as a fan (see create_basics/fans) processor: air blown through makes a Channeling stream; extra water layers dilute it into washing (see glossary/washing). Classic Conduit ring works the same way.
- Channeling: revives dead coral; flint -> Prismarine Shards; glowstone dust -> Prismarine Crystals; Ender Eye -> Heart of the Sea; ages copper blocks. Recipes in JEI (see start_here/tools) under Conduit Channeling.
- Prismarine Alloy [create_aquatic_ambitions:prismarine_alloy]: mixed from Prismarine and Copper Nuggets. Prismarine also smelts into Veridium.
- Suspicious Rock [create_aquatic_ambitions:suspicious_rock]: wash or channel it for Nautilus Shells, bones and Spiky Shells. Shells crush into bone meal and XP.
- Prismarine is also crushable for lapis and Create XP nuggets [create:experience_nugget]; ocean monuments become a materials source (see ocean/underwater_places).
- Gravel, Calcium Rich Powder and lava compact (see glossary/compacting) into calcite.

## ocean/sea_life | Sea Life
- Hybrid Aquatic: well over a hundred new sea creatures (clownfish, seahorses, orcas, whale sharks, colossal squid...) plus new ocean biomes.
- Biomes: Coral Reef, Deep Coral Reef, Seagrass Bed, Tide Pools, Red Meadow, Sulfuric Caves, Warm/Cold/Frozen/Lukewarm Trenches, a Tropical River biome on land. New coral types and bleached variants.
- Hunters (orcas, sharks, squid, big predatory fish) hunt smaller fish and turtles; after a kill a hunter is full for about 45 seconds. Crab Eaters: otters, octopuses, cuttlefish and some fish hunt crabs and shrimp the same way. Hermit crabs are never hunted (Bridge tag create_galore_bridge:never_hunted), so otters on a beach won't wipe them out; a fishing net still catches them. Prey they kill leaves nothing, so orcas won't cover the sea in fish. For your own fish, use a rod or fishing net.
- Voltaic Sea Grass [garnished:voltaic_sea_grass]: glowing sea grass in cold oceans. Moving through it shocks players for 2 damage a hit, drowned too. Fish and other sea life swim through it unharmed. Fan bulk freezing turns seagrass into it.
- Ominous Conch [hybrid_aquatic:ominous_conch]: tied to Karkinos crab boss; check JEI (see start_here/tools) for uses.
- Magnetic Hook [hybrid_aquatic:magnetic_hook]: one of several special hooks: Barbed, Glowing, CreeperMagnet, Ominous, Magnetic.
- Naturalist: OFF here (Hybrid Aquatic covers them): anglerfish, blobfish, giant isopod, great white shark, piranha, jellyfish, starfish, crab, ray; their buckets and eggs hidden from JEI. Still spawn: whale, clam, bass, catfish and the rest.
- Diving Suit [hybrid_aquatic:diving_suit]: Hybrid Aquatic's underwater armor; compare options in Diving Gear (see ocean/diving).
- Whale [naturalist:whale_spawn_egg]: Naturalist whale still spawns. Clams drop Clam Meat (cooking).
- Aquamirae (see ocean/aquamirae): icy ocean region, own creatures and bosses. Critters and Companions: adds koi fish (its otter, Dumbo Octopus, Sea Bunny are off; Hybrid Aquatic's sea slug gives Sea Bunny Slime and its otters crack clams: see exploration/creatures). Structures (see ocean/underwater_places) give them homes. Fishing: (see fishing/overview).
- Related: Creatures (see exploration/creatures)

## ocean/aquamirae | Aquamirae and the Ice Maze
- Aquamirae adds the Ice Maze: frozen ocean region of pirate ships, outposts, shelters, shipwrecks locked in ice, with own creatures. Library mods Fragmentum and GeckoLib sit underneath.
- Three bosses: Ghost of Captain Cornelia (Shell Horn summons her near water in the Ice Maze); Maze Mother (deeper in); Shipbreaker (leviathan; its Ship Graveyard Echo leaves a trace the Echo Compass can follow). More: Bosses (see exploration/bosses).
- Shell Horn [aquamirae:shell_horn]: found in frozen pirate ships; summons Cornelia when used near water in the Ice Maze.
- Frozen Key [aquamirae:frozen_key]: dropped by Cornelia; opens a hidden chest in the depths of the Ice Maze.
- Tidepiercer [aquamirae:tidepiercer]: Cornelia's weapon, looted from her after the fight.
- Echo Compass [aquamirae:echo_compass]: points toward the nearest Ship Graveyard Echo.
- Salvager armor: supplies air underwater and stops freezing in the Ice Maze (see ocean/diving).
- Upgrade templates: Terrible template upgrades Iron armor with a Fin; Abyssal template upgrades Diamond armor with Abyssal Amethyst.
- Rune of the Storm [aquamirae:rune_of_the_storm]: found buried in the Ice Maze; attach at a smithing table: +40% damage, or -10% damage taken, in cold biomes.
- Fish and drops feed cooking: Spinefish, Esca, Jellyfish Jelly, Sea Stew; fit Farmer's Delight (see food/farmers_delight) style meals. Way to get there: Submarines (see ocean/submarines).

## ocean/diving | Diving Gear
- Several mods let you stay underwater; pick for the job: air, vision, speed, or a dry room.
- Copper Diving Helmet [create:copper_diving_helmet]: Water Breathing while a worn Backtank has air. Create's own set.
- Copper Backtank [create:copper_backtank]: wear for air; placed and spun by rotation (see create_basics/rotation) it refills itself. Netherite gear lets you dive in lava.
- Reinforced Diving Helmet [hybrid_aquatic:reinforced_diving_helmet]: Hybrid Aquatic's suit also has Reinforced and Glowing versions.
- Diving Weight [hybrid_aquatic:diving_weight]: sinks you faster.
- Salvager Suit [aquamirae:salvager_suit]: full set supplies air and stops freezing in the Ice Maze (see ocean/aquamirae).
- Seaglide [create_high_seas:seaglide]: swim fast on your own hunger; part of Deep Seas.
- Artifacts adds Snorkel, Flippers, Aqua-Dashers as wearable accessories, so they stack with armor (see exploration/gear).
- Dry rooms and ships: oxygen setup in Hulls and Pressure (see ocean/hulls_and_pressure).

## ocean/underwater_places | Underwater Places
- Structure mods rework the sea floor; destinations for a submarine (see ocean/submarines): ruins, wrecks, monuments with better loot than vanilla. Pressure matters: check depth with a Barometer (see ocean/hulls_and_pressure) first.
- Ocean Monuments [minecraft:prismarine_bricks]: YUNG's Better Ocean Monuments rebuilds the vanilla monument. Prismarine can be crushed for lapis and XP.
- Hopo Better Underwater Ruins and Revamped Shipwrecks replace vanilla ruins and shipwrecks with larger, more varied builds. MOS (Moog's Ocean Structures) adds many more: Atlantis ruins, temples, sunken ships, dragon skeletons, gardens, shrines.
- How often: sea floor is busy; a few hundred blocks of open ocean usually hold several places. Common finds (small ruins, shipwrecks, whale falls) each get about the same share. Best-loot builds stay rare on purpose: MOS temples, Atlantis ruins, monuments, Hopo sunken city.
- Smaller finds: whale falls (Hybrid Aquatic), ocean wreckage (Towns and Towers), sunken boats (Creat Aeronautics Structures), flooded ruins from Create: Let The Adventure Begin, conduit ruins. Ice Maze of Aquamirae (see ocean/aquamirae) keeps ice spires and arches at full density (they are the biome); its frozen wrecks are thinned like other common wrecks.
- Chests are normal loot, but Lootr (see exploration/lootr): in this pack loot chests are per player. Conduit and prismarine gear from here feeds Conduit Cages (see ocean/conduits).
- Conduit [minecraft:conduit]: a conduit in a monument can be worked with Aquatic Ambitions' fan channeling.
- Related: Hulls and Pressure (see ocean/hulls_and_pressure), Structures (see exploration/structures)
