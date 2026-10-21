<!-- sources sha256:59f14db0e36af2a4c7105d04e806271a852e8f93ee7023e0ea697b057b2d3612 -->
# Trains and Rides (transport)
How you cross the world: travel by building.

## transport/getting_around | Getting Around
- Pack rule: you travel by building (trains, airships, submarines, cars, hypertubes); shortcuts that make them pointless are switched off. Short-range tricks that cost something (double jump, jetpack tank) stay in.
- Elytra are OFF: End City ships have an empty spot where the Elytra hung; no chest, mob or vault drops one; one that turns up anyway crumbles into Phantom Membrane. The Jetpack (see transport/jetpack) now takes a Shulker Shell instead.
- Also off:
  - Iron's Spells (see exploration/irons_spellbooks): Portal, Recall, Angel Wings, Portal Frames
  - Artifacts: Warp Drive
  - Alex's Caves (see exploration/alexs_caves): Subterranodon mount; Hullbreaker's Enigmatic Engine (so no buildable Submarine)
  - Void fishing (see fishing/lava_and_void): the Voidseeker, a fish that sent you to your spawn point
- Still in (each a short hop with a cost or time limit):
  - Ender Pearls
  - Aether (see exploration/aether) mounts and Valkyrie gear
  - Artifacts (see exploration/gear): Helium Flamingo, Cloud in a Bottle
  - Twilight Forest (see exploration/twilight_forest) gear
  - Alex's Caves: Cloak of Darkness
- Want Elytra back (in depth): world-wide switch for whoever runs the game. Close game/server, move config/paxi/optional/create-galore-with-elytra into config/paxi/datapacks, restart. Unexplored End Cities and loot have Elytra again. README has details.
- Intended ways to travel: Trains (see transport/trains), Airships (see flight/airships), Submarines (see ocean/submarines), Hypertubes (see transport/hypertubes), Jetpack (see transport/jetpack).

## transport/trains | Trains
- Create trains run on their own track network; cars are glued (see glossary/super_glue) blocks. Drive yourself or give a schedule. Tip: hold W over any item to see its Ponder (see start_here/tools).
- Train Track [create:track]: its own block, not a vanilla rail. Click a placed track, then another spot, to lay a straight, curve or slope in one go. Sprint while connecting for the longest bend that fits. Off-hand blocks are paved under the track.
  - Placed against a Nether portal, builds a matching line on the other side: trains cross dimensions.
- Train Station [create:track_station]: waypoint on the network. Place beside a track, open its screen. Assembly Mode (see glossary/assemble) builds a train; scheduled trains stay away while it is on. A train can ONLY be turned back into blocks at a station.
  - Use a map on a station to add a labelled marker there. Assembled trains can be nudged onto nearby track with a wrench (see create_basics/tools_wrench).
- Bogeys (see glossary/bogey), the wheel unit a car rides on: use Train Casing [create:railway_casing] on a track to place one; small and large bogeys exist. Click the track again to cycle designs. Glue blocks on with Super Glue.
- Train Controls [create:controls]: every train needs them. A second set facing the other way lets it leave a station in either direction. Right-click once assembled to drive: movement keys steer, mouse wheel sets top speed, sneak lets go.
- Fuel: train moves faster if it finds fuel in chests/barrels glued to it; vault fuel is not burned. Hold space while driving to roll up to a station.
- Track works in unloaded chunks (see glossary/chunk): trains still travel and stop, but on-board machines wait until a player is near.
- Related: Signals and Schedules (see transport/train_signals), Train Add-ons (see transport/train_addons), Contraptions (see create_basics/contraptions).

## transport/train_signals | Signals and Schedules
- Train Signal [create:track_signal]: splits track into segments, each holds ONE train at a time. Signals only govern trains nobody is driving. Placed beside a track like a station. A scheduled train never crosses one against its direction unless a second signal faces that way.
- Redstone forces a Train Signal red; a red signal gives comparator output. Nixie Tubes (see create_basics/redstone_and_control) on a signal make lights readable from afar.
- Two modes (in depth): wrench (see create_basics/tools_wrench) switches a signal to its second mode. Brass-mode signal is meant to lead into ordinary ones: holds a train back if, once inside, it could not leave the next segment at once; keeps queued trains out of a busy junction.
- Train Observer [create:track_observer]: select a track, place observer nearby. Fires when a train crosses the marker; can filter by cargo.
- Train Schedule [create:schedule]: list of stops and conditions to leave each. Right-click to edit, then give to a driver; can be taken back any time. Any mob, or a Blaze Burner (see create_basics/heat), seated in front of Train Controls (see transport/trains) counts as a conductor. Stops are stations; conditions: delay, cargo level, redstone signal and more.
- Going further: Steam 'n' Rails adds track switches and semaphores; Railways Navigator adds route planning and penalty anchors that make trains avoid stretches of track (see transport/train_addons).
- Related: Trains (see transport/trains), Train Add-ons (see transport/train_addons).

## transport/train_addons | Train Add-ons
- Mods: Steam 'n' Rails (new track and parts), Railways Navigator (route info), Train Utilities (doors), Xaero Train Map (map).
- Steam 'n' Rails adds:
  - Track: Wide and Narrow gauge, Monorail, Phantom track that fits any bogey (see glossary/bogey); track also in many wood and stone styles
  - Parts: many more bogey designs, couplers, buffers, a handcar
  - Styling: locomotive blocks such as boilers and smokestacks
  - Track Switch [railways:track_switch_brass]: use to cycle right, sneak-use to cycle left; redstone sets it; comparator reads 1 left, 0 straight, 2 right. shows item railways:track_switch_brass
  - Semaphore [railways:semaphore]: sits on a pole above a Train Signal (see transport/train_signals), shows its state; two stacked show if some or all exits are blocked. shows item railways:semaphore
  - Train Coupler [railways:track_coupler]: couples/decouples trains without taking them apart; wrench (see create_basics/tools_wrench) changes mode and gap between pads. shows item railways:track_coupler
  - Conductor's Cap [railways:black_conductor_cap]: conductors press buttons and flip levers when a player looks at them, unless that player wears another colour of cap. shows item railways:black_conductor_cap
  - Conductor Whistle [railways:conductor_whistle]: bind to a conductor riding a train, then use on a track or station to call that train; a deployer can use it on a station too. shows item railways:conductor_whistle
  - Remote Lens: bind to a conductor wearing goggles, use to look through their eyes.
  - Handcar: small rail cart; pick up with a wrench.
  - Smokestacks can be dyed; soul sand or soul soil makes soul smoke.
  - In depth: the pack uses an unofficial port of Steam 'n' Rails for 1.21.1, still in beta.
- Railways Navigator adds: Navigator item, Advanced Display boards, Train Station Clock, Lectern with Navigator, Penalty Anchors (add cost to a stretch of track).
  - Navigator [createrailwaysnavigator:navigator]: search a route to a station, see trains that run it with delays and changes; connections with stopovers and live data. Route overlay options on the \ key. shows item createrailwaysnavigator:navigator
  - Advanced Display [createrailwaysnavigator:advanced_display]: platform and train destination boards; wrench it for configuration screen. shows item createrailwaysnavigator:advanced_display
  - Penalty Anchor [createrailwaysnavigator:penalty_anchor]: trains pick the cheapest path; raise penalty with a wrench to push them to another route; redstone weakens it. shows item createrailwaysnavigator:penalty_anchor
- Train Utilities: sliding train doors in dozens of real-world styles, platform and line blocks, frameless glass. Some doors need small parts (Processing Units, Rubber Seals) made by Create assembly (see create_basics/brass_and_assembly). Doors open by hand or follow a moving contraption (see create_basics/contraptions).
  - Andesite Alloy Door [trainutilities:door_andesite_alloy]: one of many sliding doors; Create's own Train Door is a simpler option. shows item trainutilities:door_andesite_alloy
- Xaero Train Map draws trains on Xaero's World Map; affects only your own screen.
- Related: Trains (see transport/trains), Signals and Schedules (see transport/train_signals).

## transport/coasters | Roller Coasters
- Simulated Coasters: free-form coaster track with curves, banks, chain lifts; ride in carts. Carts are physics objects (see glossary/sub_level), same system as airships, NOT Create trains.
- Place Track Anchorpoints, link two with Coaster Track for a smooth curve. Wrench (see create_basics/tools_wrench) reshapes; banking and height tunable. Dye tints rails; axe removes. Tracks cannot split or cross over themselves.
  - Coaster Track [simulatedcoasters:coaster_track]: click one anchorpoint, then another. Smoothing automatic; hold left alt while placing to turn it off. shows item simulatedcoasters:coaster_track
  - Track Anchorpoint [simulatedcoasters:coaster_anchorpoint]: posts tracks hang between; each takes two connections. shows item simulatedcoasters:coaster_anchorpoint
- Chain lift: use a Chain to pick start and end on connected track. Shaft at the start takes Create rotation (see create_basics/rotation) to drive it. Sneak-use a wrench anywhere on the span to remove.
- Coaster Cart [simulatedcoasters:coaster_cart]: place on track. Use cart item on a placed cart, then place another to couple them. Shears cut links; wrench changes length. Build on the carts. shows item simulatedcoasters:coaster_cart
- Rivet [simulatedcoasters:rivet]: lets a block sit at any angle/offset. Tap rotate key for 15 degree steps, hold for free rotation. shows item simulatedcoasters:rivet
- Red Balloon [simulatedcoasters:red_balloon]: lifts a ship slightly off the ground, keeps it upright. Popping one pops its neighbours, dropping the ship. shows item simulatedcoasters:red_balloon
- Create: Coaster Seats: Lapbar and Restrictor seats in all 16 colours. Restrictor locks rider by Redstone Link frequency (see glossary/redstone_frequency) or redstone signal.
  - Restrictor Seat [createcoasterseats:red_restrictor_seat]: lock mode set with wrench: link only, link and redstone, or redstone only. shows item createcoasterseats:red_restrictor_seat
- Coasters Simulated: Track Styles: Coaster Style Tool and Anchor Style Tool for extra looks.
  - Coaster Style Tool [coasterssimulatedextratypes:coaster_style_tool]: changes look of existing track. shows item coasterssimulatedextratypes:coaster_style_tool
- Related: Airships (see flight/airships) same physics; Trains (see transport/trains), Contraptions (see create_basics/contraptions) for comparison.

## transport/hypertubes | Hypertubes
- Tubes you travel through at speed; connect entrances with tube blocks, anything stepping in is carried down the line. Create rotation (see create_basics/rotation) powers the entrance.
- Build: hold a tube, right-click a placed tube, then place the next (placed tubes can be linked the same way). Wrench (see create_basics/tools_wrench) changes how many segments a span uses. Angle and distance between ends are limited. Hypertube [create_hypertube:hypertube]; shows item create_hypertube:hypertube
- Entrance needs at least 16 RPM (see glossary/rpm) to admit you; faster rotation = faster travel. Wrench changes mode: manual lock (sneak to enter) or automatic lock. Place it facing the way the tube runs; you enter from your side. Escape key gets you out of a tube.
  - Hypertube Entrance [create_hypertube:hypertube_entrance]: turns like a small cogwheel: mesh a cogwheel beside it; if it says not enough speed, spin faster. shows item create_hypertube:hypertube_entrance
  - Hypertube Accelerator [create_hypertube:hypertube_accelerator]: speeds up whoever passes; right-click for Brake mode instead. shows item create_hypertube:hypertube_accelerator
  - Hypertube Splitter [create_hypertube:hypertube_junction]: joins up to three tubes. Travellers steer with movement keys. Wrench cycles Continue (sends them down side tubes) and Center (straight). shows item create_hypertube:hypertube_junction
  - Tube Scanner [create_hypertube:tube_scanner_attachment]: signal while something travels the tube, 8 for babies, 15 for adults. Redstone Detector Attachments change modes remotely. shows item create_hypertube:tube_scanner_attachment
- Related: Rotation (see create_basics/rotation), Stress (see create_basics/stress) power the entrance; Trains (see transport/trains) are the long-haul option.

## transport/jetpack | Jetpack
- Create Jetpack [create_jetpack:jetpack]: a flyable backtank, built around a Copper Backtank (see ocean/diving); spends pressurised air, runs dry. Refill like the backtank: sneak-use it on a block to place it, turn the shaft on its top. shows item create_jetpack:jetpack
- Controls: jump rises, sneak descends, N toggles engine (this pack's key; mod's own is G), H toggles hover. Rebind in Controls.
- Netherite Jetpack [create_jetpack:netherite_jetpack]: same flight, built around a Netherite Backtank. Check JEI for the upgrade recipe. shows item create_jetpack:netherite_jetpack
- Making: both crafted by mechanical crafting (see glossary/mechanical_crafting) from a Shulker Shell, Chutes, Brass plates, Precision Mechanisms and the backtank. Press R over one in JEI (see start_here/tools) for layout.
- For something larger than one person: Airships (see flight/airships). Related: Thrusters and Engines (see flight/propulsion).
