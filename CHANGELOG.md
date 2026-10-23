# Changelog

Each pack version gets a section here, with **Added**, **Fixed** and **Removed** where they apply. The release
workflow copies the section for the version in `pack/pack.toml` to the top of the GitHub release, and the build
fails when that section is missing.

## 0.4.55

### Fixed
- **The sea-exploring freeze fix now covers chunk edges fully.** Since 0.4.52, water at the edge of the loaded world only flows once the chunks around it are loaded. That check looked 2 blocks out, but one flow reaches further: the water lands a block away, wakes the blocks around it, and woken water looks at its own neighbours. Water two or three blocks from a chunk edge could still make the server wait for an unfinished chunk (a 2.3 s freeze while sailing). The check now looks 4 blocks out. Checked on a test server over 1,200 blocks of new coast and sea on 4 seeds: no waits for unloaded chunks from water, and no freezes while moving.
- Create Galore Bridge 1.79.0.

## 0.4.54

### Fixed
- **You stay dry inside a ship's hold.** Most boat decks sit a little under the waterline (the oak and dark oak motor launch about 0.3 blocks), and the sea only stays out for a player whose eyes or feet are in the hold's air. An open door, a stair or a slab belongs to the hull, so walking through a cabin doorway or standing on a stair counted as standing in the sea for a moment, and Cold Sweat soaked you on a dry deck. The hold now also counts the middle of your body, just above your feet, and any door, stair, slab or other partial block that opens onto the hold. Checked on a test server: walking back and forth through the launch's open cabin door used to soak you within seconds, now you stay dry, and swimming next to the boat still gets you wet.
- **Logging in on a ship no longer soaks you.** For the few ticks until a ship loads after you join, you stood at its spot in the sea, and one of those ticks soaked you as much as a swim. Until the ship loads, and for 2 seconds after while its hold is being looked at, you count as on board. Checked with a server restart and two relogs on the launch's deck: no wet tick.
- Create Galore Bridge 1.78.0.

## 0.4.53

### Fixed
- **Flyover planes no longer fly into sky islands.** Aeronautics Discovery's flyover planes held their heading and height with no idea what lay ahead, so they often flew straight into a floating island or sky building, crashed, and dropped their cargo where anyone could pick it up long before they had an aircraft to chase them with. Their pilots now plan a route: they know where sky islands and other structures stand along their course (worked out while the world generates, long before those blocks exist), look ahead for anything else solid in the air, and climb over or bank around it with room to spare, then turn back onto their old heading. Pillager patrols circling mansions and outposts and the Soaring Trader use the same planner. Nothing is loaded or generated for this, so it causes no freezes, and planes still spawn anywhere. Checked on a test server against a sky volcano island from 8 directions: without the change 6 of 8 planes crashed into it, with it none even entered its outline. A narrow sky tower, a pillager plane and a plain stone wall in the sky were crashed into before and flown around after. The Field Guide's Discovery page says so.
- Create Galore Bridge 1.77.0.

## 0.4.52

### Fixed
- **No more server freezes while sailing into new terrain.** Water (and lava) flowing near the edge of the loaded world woke the blocks where it landed, and when that spot was in a chunk still being generated, the server stopped and waited for it: freezes of 2 to 7 s, one after another, while exploring by sea, and up to 28 s in testing. Chunks there start ticking a moment before their neighbours are done. Water now only flows once the chunks within two blocks of it are loaded, and tries again a quarter second later otherwise. Everywhere else it flows exactly as before, and settled water ends up the same.
- **No more freezes from chimney smoke at the edge of the loaded world.** Advanced Chimneys follows the smoke of every fire, furnace and campfire (also the ones in villages and camps) through its connected chimney blocks every few seconds, and that search read blocks in chunks that were still generating, so the server waited for them: up to 28 s in testing. The search now stops at a chunk that isn't loaded yet and carries on a few seconds later.
- Checked on a test server travelling 1,200 blocks of new coast and sea at 10 blocks a second, on the same seeds and route: in 8 runs the old version made the server wait for unloaded chunks 185 times (135 s in all, the longest 6 s and whole freezes of up to 28 s), in 6 runs the new one waited once, for 0.03 s.
- Create Galore Bridge 1.76.0.

## 0.4.51

### Added
- **Pinned schematics: build a schematic by hand, together.** A placed Create Schematic only showed while someone held it in their hand, and only for them. Sneak-right-click a placed Schematic and press the new **Pin for everyone** button (or use `/schematic pin` while holding it): the build stays standing where it was placed as faint, see-through blocks, with nothing in your hands, and every player on the server sees it, including players who never had the schematic file. Place real blocks over the faint ones: the right block (facing the right way) simply fills its spot, and a wrong block turns red. Standing next to a pinned build shows how many blocks are left and how many are wrong. **Unpin** on the same screen, or `/schematic unpin` next to it, takes it down for everyone; `/schematic list` shows every pin with who pinned it. Pins are kept with the world, so they survive a restart. The Schematicannon works exactly as before and can still print a pinned build: the faint blocks fill in as it places them. Checked on a test server with two players: one pinned a hut from the placement screen, the other saw it straight away, both saw two wrong blocks turn red and right ones fill in, the pin came back after a server restart, a Schematicannon printed it, and unpinning took it away for both; the faint blocks stay faint with Complementary shaders too. Up to 120000 blocks per pin.
- Create Galore Bridge 1.75.0.
## 0.4.50

### Fixed
- **Trolling no longer pays out line.** With a rod in hand, the line out was simply how far you were from the rig, so trolling from a boat or walking backwards kept adding metres until the reel ran empty after a hundred metres or so without a bite. The reel is closed while the rig waits, so it now comes with you: sail off or walk backwards and you drag the rig along at the line you cast, on water and on land (rod machines on ships already did this). Walking back toward it just leaves slack, and winding takes the slack in before it moves the rig. Line only leaves the spool when a hooked fish pulls past the drag, and a boat speeding off with a fish on still tows a small one and gives a big one line through the drag. Checked in game: 207 m of trolling at boat speed kept the line at 25 m the whole way, and walking 40 m backwards on sand kept it at 15.5 m. The Field Guide's casting and trolling pages say so.
- Create Galore Fishing 0.3.16.

## 0.4.49

### Added
- **Field Guide: Nether Heat.** A new Survival page on surviving the Nether's heat, with numbers measured on a test server. The Nether's air is 130 to 180 °F, lava within 7 blocks adds up to 200 °F more, and it is about 40 °F cooler while it is night in the Overworld. With no heat insulation you overheat in 15 to 45 seconds. Iron, diamond and netherite armor have no heat insulation of their own. What works before your first trip: leather armor with Rabbit Hide sewn into all 19 slots lasts about 17 minutes in 166 °F air and about 6 minutes right next to lava. Four Blue Ice and four Packed Ice around you take 166 °F down to 86 °F, and neither melts. Waterskins chilled in an Icebox help, and Fire Resistance stops heat damage. Deep inside a Soul Sand Valley it is about 85 to 100 °F. Once you're in, a Hoglin set lasts about 8 minutes on its own. The Nether page now warns you to read it first, and Ask the Book knows all of it.

### Fixed
- **Ask the Book's Nether advice.** The guide said Cold Sweat only covers the Overworld and the Aether, so the book didn't know the Nether gets hot and told players to fetch Hoglin Hide, which only comes from the Nether, and go in quick bursts. Bursts were impossible without heat gear. The Temperature pages now say Cold Sweat covers the Overworld, Nether, End, Aether and Twilight Forest. They name Rabbit Hide as the best heat insulator outside the Nether (tied with Chameleon Molt once it has turned toward heat) and suggest it instead of Hoglin armor for hot days.

## 0.4.48

### Fixed
- **Reeling through a jump now keeps the fish on.** A jumping fish could throw the hook even while you wound the whole fight, with a message saying winding keeps the line tight. Winding only made it less likely, about one jump in ten still threw the hook. Now a jump throws the hook only on a loose line: when nobody is winding or lifting the rod, or when the fish swims at you faster than the reel takes the line up (the message then says so and tells you to lift the rod as well). A hook too small for the fish's mouth can still tear out on the jolt, with its own message. Checked in game: holding the reel through fights against a sailfish jumping three times a second, the old version threw the hook and the new one never did. Landing rates go up by about one point.
- Create Galore Fishing 0.3.15.

## 0.4.47

### Fixed
- **Ask the Book's labels can name a part hidden inside a build.** A label naming a part (barrels, chest, engine, cannon...) only counted the blocks right beside where it points, so a "cargo barrels" label on a cabin roof with the barrels inside under it could be refused, though it was true. It now also counts the part anywhere straight below or above that spot (or within two blocks), so honest labels on roofs and decks stay, and a label naming a part the build doesn't have is still refused.
- Create Galore Bridge 1.74.1.

## 0.4.46

### Fixed
- **Ask the Book builds out of what you have.** When asked to design a build it never looked at what you carried, so it fell back on its own favourite look: spruce hulls with dark oak trim, even with no spruce anywhere near you. It now counts your building blocks before it plans (inventory and the chests you opened nearby), grouped by material: a wood counts its logs, planks, stairs, slabs and fences together, a stone its polished and brick forms, so 48 oak logs read as about 190 blocks of oak. It also sees which trees and ground lie open around you. Where one wood or plain stone would look as good as another, it uses the one you have most of, or what grows around you; it no longer spreads one hull across five woods just because you hold a few of each; and it never makes a build worse to fit your stock. When a design really needs something you lack (glass, a lantern, more planks than you hold) it uses it anyway and tells you what you still need and about how many. Every model it builds is measured against what you hold. Checked with a test inventory of 1 spruce plank, 150 oak planks, 48 oak logs and 96 birch planks: asked for a cozy fishing boat, the book built it in oak and birch and listed the lanterns, barrels and chain still to make.
- Create Galore Bridge 1.74.0.

## 0.4.45

### Fixed
- **Ask the Book no longer designs boats you have to hang off to steer.** It had mounted a Boat Engine backwards on a stern, so whoever took the tiller floated over the water outside the boat and dropped into the sea the moment they let go. A Boat Engine's helmsman stands two blocks out on the side the engine faces, so the book now only accepts a design with ship blocks under that spot: the engine goes at the stern facing into the boat, with deck under the driver. It tells you which way to turn an engine that faces out. Every Steering Wheel, Throttle Lever and lever on a vehicle also needs a spot on board to stand within reach of it. Checked on a test server with two real ships: with the engine facing in, the driver stood on the deck at the tiller and stayed aboard after letting go; with it facing out, the driver hung over the water and fell in, and the book's check refused that design.
- **The book's labels only name parts the build really has.** A boat model labelled a "cannon" that wasn't there. A label naming a working part (cannon, engine, tiller, rudder, sail, mast, propeller, anchor, seat, chest and so on) now has to point at that part or right next to it, or the model isn't shown.
- **Designed ships balance by their shape, not by ballast.** The book had levelled a boat by bolting an iron block to its stern. A ship the book designs may no longer carry iron, gold, copper or other metal blocks as dead weight; it has to sit level through its hull shape and where its rooms and machines go. Trimming your own ship with ballast is still up to you, and the book's tips for a boat that sits bow or stern down now point at the hull and layout.
- **The book designs for the player who will use the build.** Passing its own checks is the least a design must do, not proof that it is good: it now thinks about where you stand to steer and work each control, how you get aboard and move around, and that nothing makes you fall off.
- Create Galore Bridge 1.73.0.

## 0.4.44

### Added
- **Field Guide: shafts and pipes through a sealed wall.** The Pressure and Sealed Casings page now says how to bring power and fluid into a sealed room without letting smog out: put Andesite or Brass Casing on a shaft where it passes the wall, and Copper Casing on a fluid pipe. Gearboxes, Encased Chain Drives, clutches, gearshifts, encased cogwheels and Encased Fans seal like stone; bare shafts, cogwheels and pipes leak, and so does a shaft in a Metal Girder. Checked with smog shut in a test room: none got out past any encased block, and it poured out past a bare shaft. Ask the Book knows it too.

## 0.4.43

### Fixed
- **Looking at a ship no longer freezes the game.** Since 0.4.36, aiming at any block of an assembled ship (a Physics Assembler's lever included) dropped the game to about one frame every few seconds, for every player looking at it, with or without shaders, until the ship was taken apart. The look-up that lets Jade name smog in front of you followed the aim out to where the ship mod really keeps a ship's blocks, far outside the world, and read every block on the way each tick. It now stops at the ship you see and never looks past your reach; Jade still names smog hanging in front of a ship.
- Create Galore Bridge 1.72.0.

## 0.4.42

### Added
- **Ask the Book designs ships that sit level.** When the book designs or reads a boat, it now works out how the boat sits at rest with nobody aboard: bow down, stern down or leaning to one side, in degrees, and how much weight to move which way to level it. For an airship it works out how the ship hangs under its balloon. A boat that sits 5° or more off level, dips its low end near the water, or an airship top-heavy enough to turn over counts as a problem the book fixes before showing the design, so asking for a balanced ship gets one that sits level. Checked against 6 boats and 3 balloon ships on a test server: every one sat or hung within 0.4° of the book's figure.
- Create Galore Bridge 1.71.0.

## 0.4.41

### Fixed
- **The cast fishing line now has the same colour as the line on the rod.** It was always drawn black, while the line through the rod's guides showed the colour of the line you fitted. Now both show your line: string and mono near white, clear line pale blue, heavy line yellow, braided line green. With or without shaders.
- Create Galore Fishing 0.3.14.

## 0.4.40

### Added
- **One accessory screen.** Rings, necklaces, gloves, capes, backpacks, charms and everything else you wear now go in one Accessories screen, opened with the ring button next to your player in the inventory (in creative too) or with **G**. It shows your player with armor and off-hand on the left, the accessory slots beside them from head to feet, and your inventory below. Shift-click puts an accessory in its slot. Every mod still sees its items exactly where it expects them, so every effect works as before.
- **Limits on what a person can wear:** one hat, one pair of goggles or glasses, one mask or respirator, up to 3 necklaces, one cape, one backpack, one body piece, two hands (a pair of gloves fills both, a single glove or bracer one), one shield accessory, up to 10 rings, one belt, one quiver, one spellbook, one pair of shoes, up to 3 charms and up to 3 gadgets. No two of the same necklace, charm or gadget; at most 2 of the same ring or hand item. Hover an empty slot to see what goes there and how many of that kind you wear.
- **Show or hide each accessory on your character:** hover a worn item and click the small eye in its corner. Hidden items keep working.

### Fixed
- Accessories were split across three screens (Curios, Accessories and The Aether's), each with its own button and its own slots, so the same kind of item could be worn several times over (Aether rings plus Curios rings, two necklace slots in two screens).
- **Your worn accessories carry over.** Everything that fits the new limits just shows up in the new screen. What doesn't fit (more of a kind than the limit, look-only cosmetic slots, or slots the new screen doesn't have) goes to your inventory with a chat message; if your inventory is full nothing is dropped: it is kept for you, and a chat button (shown at every join until you use it) gives it back once you have room.
- Create Galore Bridge 1.70.0.

### Removed
- The Curios, Accessories and Aether accessory screens and their three inventory buttons (their keys open the new screen if you bound them again). Look-only cosmetic slots.

## 0.4.39

### Fixed
- **It no longer rains most of the time.** Two things added up. Winds & Weather's drizzle and light rain, which cover about 16% of the map at any moment, each switched on full rain over the whole world while any player stood under them, so with two players online that alone added 10 to 20 points. Serene Seasons' default winter starts a new rain or snow spell 0.5 to 1.5 days after the last one ends (precipitation 42% of the time), and its default summer is wetter than autumn. Now only Winds & Weather's real rain, storms and snow bring rain (drizzle and light rain stay as clouds, sandstorms no longer bring rain), and rain follows the seasons: spring about 24% of the time, summer about 16% (the driest), autumn and winter about 20%. Before, a two-player server got wet weather 35-50% of the time.
- Create Galore Bridge 1.69.0.

## 0.4.38

### Fixed
- **The Farm Magnifying Glass tells you why animals aren't breeding.** Tamed animals only mate on their own, when a healthy pair of a male and a female within 8 blocks both have happiness 80 or more. The glass showed neither the sex nor the happiness and said "Breeding: Ready" whenever there was no cooldown, so a pen of all ewes, or a crowded pen, looked ready and never bred. It now shows the animal's sex (♂ Male or ♀ Female), its happiness, and on the Breeding line what is missing: happiness 80, a tame mate of the other sex within 8 blocks (or that mate's happiness), or health. It also warns when the animal is crowded (6 or more animals within 4 blocks, which takes about 40 happiness a day, more than care gives) or hungry. The Farm Ledger's symbol at the top left also shows the sex, and females are a little smaller than males.
- **A Fertility Potion no longer goes to waste on an old pick.** The first animal you give it to waits 30 seconds for its partner, but it stayed picked after that, and a ram stayed picked after mating, so a later potion paired with it, was used up and nothing happened. The pick now ends with those 30 seconds (a pregnant ewe keeps it for her lamb).
- **The Field Guide and Ask the Book had it wrong:** they said the Farm Magnifying Glass shows if an animal is male or female. The Animals entry now says where the sex shows, what lowers happiness (crowding counts animals in the next pen too, through the fence), how to use the Fertility Potion, and what to check when a pen won't breed.
- Create Galore Bridge 1.68.0.

## 0.4.37

### Added
- **Filter frames need no upkeep.** Every Iron, Gold and Diamond Filter Frame now catches all three kinds of smog (carbon, sulfur and dust) with nothing put in it, and never fills up or runs out. Iron filters 2 units of smog a second, gold 4 and diamond 8; right-click a frame to see its rate. When smog comes faster than a stack filters, the rest waits below it, and none slips through unfiltered. Frames show filter media inside, so they never look empty. A pump moves about 1.5 units a second, so an iron frame keeps up with one pump; gold and diamond pay off in a sealed room's roof, where pressure pushes harder. Pressure now keeps working on a sealed room until it is empty.

### Fixed
- **Polluted water looks like water with shader packs.** Shader packs drew it as a bright, flat yellow block, and underwater it showed as a solid cube. Shader packs now draw it as water (see-through, with waves and reflections) with its yellow tint, and there are no faces where it meets clean water.
- Create Galore Bridge 1.67.0.

### Removed
- Filter material in filter frames. Leaves, wool, paper, dye and cartridges already in a frame are used up when the world loads, and hoppers can no longer put anything in. Leaves, wool, paper and Smog's cartridges are still the filters you craft into a respirator.

## 0.4.36

### Added
- **Jade shows smog.** Look at a gas block to see what it is (Carbon, Sulfur, Dust or Filtered Exhaust) and how thick it is: light, medium or heavy, 1 to 3 units of pollution. Gas still doesn't stop your clicks, and crouching looks through it to the block behind.

### Fixed
- **Bulk smelting and cooking make real smog.** A fan's finished items now puff out 1 carbon each right where they are, on a belt, depot or dropped (plus 1 sulfur for every 5 items blasted), for blasting over lava or a Blaze Burner and smoking over fire or a campfire. It was 1 carbon per 4 items blasted and per 8 smoked, so small batches often looked like they made none.
- Create Galore Bridge 1.66.0.

## 0.4.35

### Added
- **Ask the Book's 3D builds can be saved as Create schematics and traced in the world.** Hovering a model in the book shows a **Save as schematic** button, which writes it into your own `schematics` folder (no Litematica needed). An Empty Schematic in a Schematic Table turns it into a Schematic like any other, which you place, turn and mirror with Create's tools. Create shows a schematic only while it is in your main hand, so with a placed Schematic in your **off hand** the build now stays shown while you place blocks: each block drops out once the right one is in its spot, a wrong block gets a red outline, and a line above the hotbar counts the blocks left. Works on a dedicated server: each player saves and uploads their own copy. The Field Guide's Welcome entry explains it.
- Create Galore Bridge 1.65.0.

## 0.4.33

### Fixed
- **Boats with a slab floor no longer sit bow-down.** A boat floats on the air in its hold, but the air above a slab floor (or beside a stair, trapdoor or other partial block in the hull) gave no lift, as if the floor were a full block. That air sits in the middle and bow of most boats, while a solid stern kept all its lift, so slab-floored boats floated nose-down. It now lifts like the rest of the hold: Emre's slab-floored boat went from 2.4 degrees bow-down to 1.5 at rest, and from 1.3 to 0.6 at full speed (16.6 blocks/s). A partial block counts only where its open side faces the hold and nowhere else outside, and a flooded hold or a waterlogged block still lifts nothing, so overloaded boats still sink. Ask the Book's boat check counts the same air.
- Create Galore Bridge 1.63.0.

## 0.4.31

### Fixed
- **Ask the Book talks about your build, not about how it worked it out.** After designing a boat it could say things like "it passed the ship mods' own vehicle check with no faults". It now just says what the build does and what to change, such as how it floats and how fast it goes.
- **Item names in Ask the Book answers show properly.** An item written in bold, or with its name written straight after it, showed as raw text such as "{create:attribute_filter}Attribute Filter". It now shows as the item's name, once.
- Create Galore Bridge 1.61.0.

## 0.4.30

### Fixed
- **Boats driven by a Boat Engine no longer nose-dive or rear up.** The engine pushed from its own block at the stern, always level with the world and over three times as hard as the boat's weight, so the moment a boat pitched at all the push tipped it further: a hull reaching deeper than the engine dug its bow in about 18 degrees and crawled at 2.4 blocks/s, and an engine set low on a flat hull reared the boat up onto its end within 6 seconds of full throttle. Every boat came out bow-heavy or flipped, whatever its shape. The engine now pushes through the boat's centre of mass, at the height of its submerged hull, and still steers from the propeller. A boat lifts its bow a little as it speeds up and runs slightly bow-up: a plank boat runs 2.6 degrees bow-up at 17 blocks/s, and the same boat with a two-block-deep hull runs 4 degrees bow-up while speeding up and settles at about 1 degree at 19 blocks/s. Boats built before keep working, with nothing to change.
- Create Galore Bridge 1.60.0.

## 0.4.29

### Added
- **Smog you can see, and exhaust systems to deal with it.** Pollution of the Realms, Advanced Chimneys and ForgeEndertech join the pack. Burning and crushing make real gas blocks (grey carbon, yellow sulfur, dust) that rise, fill rooms from the ceiling down and gather in a cloud over the chunk that made them; a thick cloud poisons, slows and blinds. Vents, ducts, pumps and chimneys carry it to filter frames, and an Advanced Chimneys pump runs on Create power: a shaft, cog or gearbox under it at 16 RPM or more, or an Encased Fan facing up or down.
- **Furnaces and fan bulk smelting pollute.** A furnace, smoker or blast furnace makes 1 carbon and 0.1 sulfur per coal. A fan makes no smog by blowing through lava or a Blaze Burner, only for each item it finishes: 1 carbon per 4 items blasted plus 1 sulfur per 20, and 1 carbon per 8 items smoked.
- **Pressure.** Smog shut in a room is forced up through the filter frames in its ceiling with no pump, up to 6 units a second per frame, faster the fuller the room. A sealed space that can't vent blows up the machine inside it; a full fan casing explodes.
- **Filtered Exhaust.** A filter lets out a pale, harmless haze that isn't pollution but still has to rise into the open sky before it thins away, so you can't filter into a closed room and forget it. A filter with its top closed off lets nothing through.
- **Sealed belts.** A Create belt with Andesite or Brass Casing on it carries items through a wall but stops smog, so a bulk smelter can be boxed in with a belt in, a belt out and a filter on top.
- Smog's Filter Cartridges (plain, Brass, Precision) work in filter frames. Put a hopper under a frame to take out what it catches.
- Field Guide: Pollution rewritten, Exhaust Systems (was Pollution Control) rewritten, and a new page, Pressure and Sealed Casings.
- Create Galore Bridge 1.59.0.

### Removed
- Smog's Air Scrubber, Flue, Respirator and Aerometer recipes. Pollution of the Realms' Respirator and Aerometer and Advanced Chimneys' parts take their place. Scrubbers already placed stay as decoration.
- Acid rain no longer damages builds (cobblestone, paths, hay); it still changes plants and soil.
- Pollution of the Realms' gas ignition, smog from lava meeting water, and smog from eating and feeding animals.

## 0.4.28

### Fixed
- **Ask the Book no longer designs boats that can't be glued.** Super Glue only joins blocks that touch face to face and refuses a selection where a block touches the rest only along an edge or at a corner, but the book's vehicle check counted those as joined, so it could suggest a stepped, diagonal hull. Blocks must now share a face, and the book shapes hull curves with stairs and slabs.
- **Ask the Book no longer puts a Boat Engine where nobody can take the tiller.** It now runs the engine's own test: the spot two blocks out from the side the engine faces must be clear at the engine's height and the block above (it reaches a sliver into the column on the helmsman's left; carpets, a deck and a top slab up high are fine), or the engine says there is no room to sit at the tiller. It also catches an engine set on top of a block, which can't be placed because its propeller hangs in the block below.
- Create Galore Bridge 1.58.0.

## 0.4.27

### Added
- **Glue check for ships.** Hold Super Glue or Honey Glue near a Physics Assembler (or wear Goggles and look at it) and the ship it would make is outlined before you pull the lever: blue around every block that goes aboard, red on every block inside the glue that stays behind, with a count above the hotbar. It updates about once a second, so you see each new glue box take effect.

### Fixed
- **One glue box around a ship is enough.** Blocks inside the glue used to join the ship only when a single glue box held both of two touching blocks, so wherever two boxes met side by side or overlapped the ship was cut, and big ships needed a pile of overlapping boxes that still lost parts. Now every glue box that overlaps or touches the ship's glue counts as one glue: draw one Honey Glue box (up to 48 blocks a side) around the hull, or several Super Glue boxes side by side, and every block touching the ship by a face or an edge inside it comes along. A block touching only at a corner, or floating free, still stays behind (it would fall off as its own piece), and the glue check shows it in red.
- Create Galore Bridge 1.57.0.

## 0.4.26

### Added
- **Fish Cleaner**, a Create machine for fish lines. Fish come in from a belt, chute, hopper or funnel and leave from the front. Only fish too big to use whole (3 kg or more, the ones no furnace or smoker takes) are cut up, into the same fillets, steaks, roe, bones and scraps as at the Cleaning Table; every other fish and item passes through untouched. The saw runs on rotation (two cuts a second at 32 RPM, 4 SU per RPM). Crafted from a knife, two shafts, an andesite casing and a Cleaning Table. New Field Guide page in Fishing.
- Create Galore Fishing 0.3.13.

## 0.4.25

### Fixed
- **Water and ice look right with shader packs again.** Since the Deep Seas update in 0.4.20, with Complementary, Bliss or any other shader pack the sea drew as flat grey without reflections, and ice or anything else floating on it looked like dark glass sinking into the water. Deep Seas' submarine water hiding took over texture slots the shader pack was using; it now keeps to its own slots again.
- Create Galore Bridge 1.56.0.

## 0.4.24

### Fixed
- **A line only gets weaker by fraying from teeth.** Hard and long fights and plain use no longer weaken a line: it holds or it snaps. A line behind an Iron or Steel Leader never frays. A line already frayed keeps what it has until it snaps or you replace it.
- **A snapped line no longer takes all your bait.** Only the bait on the hook goes with the leader, hook and float or lure; bait still waiting in the rod stays. The line left after a snap is unfrayed (the frayed part is what parted).
- **A warning when you cast with the drag over what your line holds.** The action bar says, in red, that the line will snap and to lower the drag.
- Create Galore Fishing 0.3.12.

## 0.4.23

### Fixed
- **No more thin sheet of water stuck to ships.** A waterlogged block built in the sea, such as a stair, slab or fence on the hull, went aboard holding its sea water, and that water could spill across the deck as a thin layer that sailed with the ship. Waterlogged blocks now go aboard dry and their water stays in the sea, and taking a ship apart in water waterlogs the blocks that land in it again. Every ship assembled before this version is cleaned once, the first time it loads: all loose water on it is removed (including water that had spread past the glue, which the 0.4.16 clean missed) and every waterlogged block on it is dried. Ships assembled from now on are never touched, so water you put on a ship later stays.
- Create Galore Bridge 1.55.0.

## 0.4.22

### Fixed
- **The Tackle Bench drag readout no longer runs under the - and + buttons.** It is now two short lines, "Drag 3.6 kg of 6.0 kg" and "Line holds 4.0 kg", both left of the buttons (red when the drag is over what the line holds).
- Create Galore Fishing 0.3.11.

## 0.4.21

The vehicle bug pass: airships, boats, submarines, trains, coasters and cars driven by a player on a dedicated server. Most of these fixes were already inside the 0.4.20 files (Bridge 1.50.0 to 1.53.0 and Deep Seas 3.4.4 landed before that release) but its notes didn't list them, so they are listed here.

### Fixed
- **A propeller on a Swivel Bearing keeps its power on a ship**, so a steering wheel can turn a powered propeller. On a ship held by Super Glue the swivel took the whole ship with it (the motor ended up on the turning side and the propeller dropped to 0 su), and the Physics Assembler left smaller glue boxes, such as the swivel's own, behind in the world.
- **You stay in your seat when you log back in.** Seats on ships, cars and coaster carts, and on any Create contraption (turning platforms, carts, gantries, train carriages), put you back down if the seat is still free. A train that drove on while you were away is found wherever it is in your dimension and you are taken back to your seat; while the game finds it you fall slowly if there is no ground under you.
- **Logging back in on a ship built since the last world save no longer drops you from the sky.** You are held in place until the ship loads, then put back on its deck (a player who logged out at y 288 used to fall to the ground).
- **Server freezes from Aeronautics Discovery.** Flyover airships no longer generate land inside the server tick when they despawn (2 to 14 s freezes), and a Soaring Trader no longer freezes the server for about 6 s looking for the structure on its map; the map joins its trades a moment later.
- **Hypertubes show their name** when you look at them.
- Create Deep Seas 3.4.0 to 3.4.4: ship checks no longer load chunks, the hull analysis of large ships no longer restarts forever, less server lag with many dry-hold ships. It adds the Vortex Engine coloured lights (off whenever a shader pack is on); if the game runs slow without shaders, switch it off with `enabled = false` in `config/create_submarine-vortex.toml`.
- Create Galore Bridge 1.54.0 (these fixes came in 1.50.0, 1.52.0, 1.52.1 and 1.53.0).

### Added
- **Field Guide pages from driving the vehicles:** Steering a propeller; the steps that make a Flight Control craft fly and steer by mouse (Default ON, Apply Thrust, Terminal on, a Redstone Proxy button on Mouse Control Switch); docking connectors pass items through Create chutes and funnels (not hoppers) and fluids through pumps; how much a Levitite block holds up (10 kpg) and what happens past it; the Aeroworks Gyroscope needs rotation (full strength at 256 RPM); which end of a Thruster takes fuel and how to feed it from a chest; submarine tanks lift about 75 mass units, pumps only push the way their arrow points (turn them with a Wrench, a Gearshift doesn't reverse them), full tanks drop a sub fast, rated depths of hull blocks, hatches and doors; a switched-on Boat Engine burns fuel even moored and empty (switch it off in its fuel screen); riding trains (sit when fast, a portal lets go of the controls), a conductor at each end for two-way schedules, and a station left in Assembly Mode blocks scheduled trains; backtank and jetpack refills and hypertube placement corrected.

## 0.4.20

### Added
- **Ask the Book checks the vehicles it designs.** Before a boat, submarine, airship or car reaches your page, the server sums the ship mods' own rules over its blocks: weight against buoyancy or lift, whether the hull rights itself when it heels, the balloon and burner, thrust from engines, propellers, thrusters and sails, the Levitite brake, top speed, turn rate, climb and ceiling. A design that would sink, capsize, fail to lift, split into loose pieces, crawl or barely turn goes back to the book to fix, and you get its real numbers. Ask about a vehicle you built and it reads the same numbers off your build. Measured on a server: a plank boat with a Boat Engine ran 16.4 blocks/s (check 17.4), a balloon ship topped out at y 200 (check about 210), and a Levitite raft with two small propellers crept at 0.07 blocks/s (check 0.09).

### Fixed
- **The book no longer says a Portable Engine can't be crafted.** Only the red one has a recipe and the other colours are dyed from it; the book now shows that recipe.
- Create Galore Bridge 1.54.0.

## 0.4.19

### Fixed
- **Fighting a fish feels like a fish, not a machine.** The tension no longer jitters tick to tick and no longer drops to nothing the moment a fish turns. A hooked fish now holds and sways as it works, drifts round you, runs off to one side, shakes its head (the line throbs), swims at you, and sulks for a moment when tired (pull in line then). The line stretches and the rod bends, so the tension builds and eases smoothly: string and mono give a lot, braid hardly any.
- **Slack line shows.** A fish swimming at you faster than you wind, or a rod let down, leaves slack: the line hangs in a curve and the HUD says "Swimming at you! Reel in the slack". The stress bars and the rod bend ease instead of flickering.
- Create Galore Fishing 0.3.10.

## 0.4.18

### Added
- **Iron Leader.** An early leader that teeth can't cut: 3 iron nuggets and a string make 2. It holds 12 kg, between line and the Steel Leader (60 kg), so saltwater fishing with pike, barracuda and other toothy fish no longer has to wait for iron wire.
- **What your line holds now is shown everywhere.** A frayed line holds less than its test. The rod's tooltip now has a "Line holds 3.5 kg (frayed)" line, a frayed spool says "Frayed 23%: holds 3.5 kg now", and the drag message, the Tackle Bench and the HUD show the drag against that number, in red when the drag is over it.
- Create Galore Fishing 0.3.9.

### Fixed
- **Line snapping on a drag that looked safe.** A String Line frayed by toothy fish held 3.55 kg, the 3.6 kg drag was over that, and nothing showed it. All the readouts now use the same number as the fight, rounded down so a drag shown as equal or lower always holds, and a drag set exactly at what the line holds no longer snaps it.

## 0.4.17

### Fixed
- **Ask the Book checks Create's real rules before it explains why a machine doesn't work.** It used to guess. Asked why a lava fan didn't smelt, it said the 16 RPM fan was too slow to reach the belt, when the air reached it fine and the belt was simply carrying items out of the air too fast. Reading a build now runs Create's own fan rules on the real machines: how far each fan's air reaches and where a block stops it, what each cell processes, which belts and depots it sweeps, and how long an item stays in the air on that belt against the 7.6 s processing needs, with the slowest belt speed that works.
- **Ask the Book knows what blocks really drop.** It rolls a block's own loot table with a bare tool, Silk Touch and each Fortune level, so it no longer guesses whether Fortune or Silk Touch plus crushing gives more ore.
- **Ask the Book judges what you can reach from the real recipe tree, not from advancements.** A new recipe tree follows an item's recipes down to raw materials and shows what you already have (inventory and nearby storage you opened), whether you ever had each part, the machines each step needs and whether you ever made one, what still has to be gathered and which dimensions you have been to. Advancements granted just for joining no longer count as progress, and the next ones in a tree are no longer called "within reach".
- **The Field Guide's Ore Doubling page compares Silk Touch with Fortune.** Per iron, gold or zinc ore, Fortune III mines about 2.2 raw ore, more than the 1.75 crushed ores from a Silk Touched stone ore block (deepslate gives 2.25); copper gets about 7.7 with Fortune III against 5.25 crushed. Crushing and Ore Doubling no longer says to crush raw ore.
- Create Galore Bridge 1.47.0.

## 0.4.16

### Fixed
- **Ships built in the water no longer leave a hole in the sea.** Assembling a ship (Physics Assembler or `/sable assemble`) left air in every spot its blocks had filled, and the water around never flowed back in, so a hull built with its planks in the water left a ship-shaped hole that stayed through restarts. A boat still sitting over that hole floated about 2 blocks too low and could lean hard to one side, with its rim under the waterline, and then sink. The sea now flows back into those spots within a few seconds of assembly, and the boat floats at its normal height where it was built. Holes left before this update stay: an operator can fill one with `/fill <from> <to> minecraft:water replace minecraft:air`, using a box from the bottom of the hole up to the water surface.
- **Water no longer gets glued into ships.** Super glue spread over a hull built in the sea glued the water inside it to the ship along with the planks, so a block-shaped lump of water sailed along under and beside the boat (and inside its hold, which then counted as flooded). The Physics Assembler and Swivel Bearing now leave plain water and lava where they are; waterlogged blocks still come along. Ships built before this update are cleaned automatically, once, the first time each one loads: the water inside their glue is removed, nothing else on the ship changes, and water you put on a ship later is never touched.
- Create Galore Bridge 1.41.0.

## 0.4.15

### Fixed
- **The reel sounds like a reel again.** Winding a reel hummed like an engine. The sounds were rebuilt by comparing them with recordings of real spinning and fly reels: winding is now a fine, fizzy whirr with a light gear whine, the drag clicks are sharp ticks, a fast run makes the drag scream as a blur of those ticks, and cranking against a slipping drag sounds like gears skipping instead of a low groan.
- Create Galore Fishing 0.3.8.

## 0.4.14

### Fixed
- **The Tackle Bench no longer flickers.** Parts of its model (the reel's side plates, the spool, the brass tin, the tackle box lid, the vise clamp and the spare blanks) had faces lying on top of each other, which flicker as you move. Every part now has a single face in each place.
- Create Galore Fishing 0.3.7.

## 0.4.13

### Fixed
- **Oceans no longer freeze over outside frozen ocean biomes.** Cold oceans were dotted with ice that made boating hard. Serene Seasons leaves oceans out of its seasons, so ice left there by earlier Winds & Weather blizzards or Cold Sweat's freezing never melted, and Cold Sweat's freezing, where it is still switched on, kept adding more. Now only frozen and deep frozen oceans freeze, and ice floating on any other ocean under open sky melts within a few minutes while someone is nearby, in any season. Lakes and rivers still freeze where it is cold enough. Player-placed ice on open ocean melts too, so use packed or blue ice for ice roads at sea.
- Create Galore Bridge 1.40.0.

## 0.4.12

### Added
- **Lost fish say why.** When a fish gets away, chat now says what broke and why, with the numbers: the drag was set over the line's test (and by how much), its teeth chewed through a line with no steel leader, the reel seized from cranking while the drag slipped, the rod was loaded past its rating, the line went slack and it shook the hook, it jumped, the hook was too small for its mouth (with a hook size that holds), it took all your line, or a part was worn through.
- **Stress bars for line, reel and rod.** The single tension bar above the hotbar is replaced by three upright bars at the right of the screen, one each for the line (against its test, with a tick where the drag slips), the reel (against its drag and crank) and the rod (against its rating). You can see which part is close to giving out and how evenly the rig is loaded. The rod-height bar sits next to the rod's.
- **Toothy fish fray the line instead of snapping it at once.** Pike, sharks, morays and other toothy fish on a line with no steel leader now wear it down a little all fight and more on every run or jump, faster the bigger the fish is for the line. A small pike barely marks it; one that pulls over three times the line's test can still bite straight through. The fraying stays on the line, as other wear does.
- Create Galore Fishing 0.3.6, and the operator tool `/cgfish pool <player>` (who would bite on that line and how big).

### Fixed
- **A line never snaps while the drag is below its test.** With the drag set under the line's test, the drag now always slips before the line breaks, through runs, jumps and winding. A line set this way breaks only when it is worn or frayed through, bitten through, or the drag is turned up past its test.
- **"It's running!" only while the fish takes line.** It showed during any run, even with the drag holding and the fish coming in.
- **Small fish no longer throw the hook while you reel them in.** A light or tired fish pulls next to nothing, and that counted as a slack line. Slack now means the fish isn't pulling and you aren't reeling, so a fish being wound in stays hooked.

## 0.4.11

### Fixed
- **Ask the Book builds sails the right way up.** In the book's 3D models, sails lay flat like shelves and Yards floated as loose stubs beside the Mast. The book now checks rigging with Winds & Weather's own rules before it shows a model: Windcatcher panels stand upright, Yards run out from the Mast and join up, and Sail Kit cloth hangs below its Yard. Every model also lists the blocks it is made of, so a block the answer says is gone can't stay behind.
- **The Field Guide's Sailing and Wind entry** no longer says only Windcatchers push ships. Windcatchers and Sail Kit cloth both catch wind on a ship, and only Windcatchers drive windmill bearings. A new page shows how a rig sits: which way a Windcatcher faces when placed, how Yards join, and where the cloth hangs.
- Create Galore Bridge 1.39.0.

## 0.4.10

### Added
- **A new look for the Tackle Bench.** The tackle on the bench is now real 3D: a rod lying on its guides with its tip over the edge, a reel on its foot, a spool of line, a small vise holding a blank with a fresh wrap, and a brass tin of floats and sinkers. The bench itself gets an andesite-cased top, andesite feet and corner brackets, and a tackle box and spare blanks on its shelf. It still fills one block with the same hitbox, so placed benches and builds around them stay as they are.
- Create Galore Fishing 0.3.5.

## 0.4.9

### Fixed
- **Dead fish floating up.** Voltaic sea grass no longer shocks fish, squid, dolphins, turtles and other sea life. Fish schools swam into its beds on the sea floor and died by the dozen, so dead fish kept floating up around you. It still shocks players, drowned and anything from land that falls in. The Field Guide (Ocean, Sea Life) now has a page on it.
- Create Galore Bridge 1.38.0.

## 0.4.8

### Added
- **Lift the rod with left click.** While your line is out, hold left click to lift the rod and let go to lower it. It flicks up when a fish is light and lifts slowly under a heavy one. A short bar next to the tension bar shows how high the rod is.
- **Strike by lifting.** A bite is hooked only by lifting the rod within the window ("Bite! Lift the rod to strike"). Reeling through a bite no longer sets the hook, and a rod already held high can't strike until it comes down.
- **Pumping.** A lift draws a hooked fish back by most of the rod's length, against the drag but not the reel's crank. Reel while the rod comes down and it winds that line in with no load on the reel: lift, wind down, lift again brings in a fish too heavy to reel. Let the rod drop without reeling and the line goes slack, which can throw the hook. A lift also twitches a lure or float toward you.
- **New reel sounds.** A smoother spinning-reel crank, sharper drag clicks, a drag scream when a fish runs fast, and a harsher grind.
- Create Galore Fishing 0.3.4.

### Fixed
- **Rods look and bend like rods.** Blanks are slimmer and bend much further under load, up to a U when you lift high over a close fish, and the tip bends toward where the line really pulls. In third person the arm comes up with the lift.
- **No rod bob.** The rod no longer drops and comes back up each time you press or let go of right click.
- Left click with the line out no longer punches or mines.

## 0.4.7

### Fixed
- **Fishing line with shaders.** With a shader pack on, the line in first person no longer runs off into the sky: it starts at the rod tip again, and the rod bends toward the fish instead of the wrong way. Without shaders and in third person nothing changes.
- **Fish weight in one unit.** Aquaculture fish no longer also show "Weight: N lbs" (it was the kilogram figure with the wrong unit); the catch line's kilograms are the weight. Small Aquaculture fish caught from now on stack again.
- Create Galore Fishing 0.3.3.

## 0.4.6

### Added
- **The Field Guide glows while an answer waits.** When the book has answered and you haven't read it yet, the Field Guide item glows green with a dot in its corner in your hotbar and inventory, and the Ask the Book ribbon in the open guide glows too. It stays lit after you rejoin and goes out once you open the answer.
- Create Galore Bridge 1.37.0.

## 0.4.5

### Fixed
- **Typing in JEI's search box no longer sorts your inventory.** Inventory Profiles Next sorts on R, and it didn't notice JEI's search box, so every R you typed there sorted the inventory (and R with T or M could move or throw everything). Its hotkeys now stay quiet while you type in JEI's search or any other text box. R still sorts when you aren't typing.
- Create Galore Bridge 1.36.0.

## 0.4.4

### Fixed
- **Asking the book to change a model it built no longer fails.** After an answer where the book had tried out a build first, the next question on that page was refused with an error. Pages that already hit the error work again.
- Create Galore Bridge 1.35.0.

## 0.4.3

### Added
- **Ask the Book improves your own builds.** Ask it to improve or fix something you built and it copies the build into a 3D model, makes its changes there and outlines them: green for blocks to add or change, red for blocks to take out. The answer says where each change goes.
- **Up to three questions at once.** Ask on one page, start another and ask there while the first is still being answered. Answers no longer pull you to the page they land on.
- **Answer notices.** When an answer lands on a page you aren't reading, or with the book closed, a notice slides in, and that page glows green under All pages until you open it.
- Create Galore Bridge 1.34.0.

## 0.4.2

### Fixed
- **A worn-through line breaks mid-fight.** Like a rod or reel, a line frayed through now parts during the fight and the fish is gone, so check your tackle before going after a big one. Leaders, hooks, floats, sinkers and lures can wear out mid-fight too, but a fish already hooked stays on.
- Create Galore Fishing 0.3.2.

## 0.4.1

### Added
- **Every fishing part wears a little.** Each fight, even an easy catch, wears every part of the rod by a small amount: the rod and reel lose 1 durability, the line frays until it wears out (about 100 easy fights), and leaders, hooks, floats, sinkers and lures each last about 200 fights. A part's tooltip shows how worn or frayed it is, a durability bar shows on worn parts, and the Tackle Bench warns when a part is close to giving out. A part that wears out is gone after the fight, with a message. Unused parts are untouched and still stack.
- Create Galore Fishing 0.3.1.

## 0.4.0

### Added
- **Moving away pulls on a hooked fish.** Walking off along the bank or sailing away tows the fish through the water: a small fish on a strong rig is dragged along, a big one takes line through the drag, and if you keep going the line holds at the end, snaps or runs out. This counts for reel drives trolling from ships too.

### Fixed
- **Reel wear follows the drag.** A reel wears from being cranked while line runs out through the drag, and only after half a second of it (time to let go of use). The higher the drag, the faster: at half the reel's drag it grinds a quarter as fast as at full drag. Letting line run out without cranking costs nothing extra.
- **An empty spool holds.** When a fish has taken every metre, the reel has nothing left to give: the fish is held at the end of the line, and the line snaps if it pulls harder than the line holds.

### Removed
- **The cap on tackle wear.** One fight can now wear a reel or rod right through, and a part that gives out mid-fight loses the fish: a reel that seizes parts the line (the hook, rig and bait go with it), a broken rod hands its parts back. The Field Guide explains how to avoid it: keep the drag below your line's test, and stop cranking while line runs out.
- Create Galore Fishing 0.3.0.

## 0.3.9

**Back up your world before updating, and update the pack on the server and every client together.** Tide 2's things convert to Create Galore Fishing when a world loads and can't be converted back.

### Added
- **Create Galore Fishing, the pack's own fishing mod.** Rods are built from parts at a new Tackle Bench: a blank (Bamboo Pole, Wooden, Iron, Steel Telescopic, Heavy Boat Rod, Netherite for lava), a reel, a line, a leader, a hook and a float, sinker or lure. The bench shows the rod's stats, the fish it suits and its weak link. Tackle is made from Create parts and TFMG steel, plastic and rubber.
- **A real fight.** Hold use to cast, strike when the float dips or the rod tip taps, then fight the fish against the drag (scroll wheel, or `]` and `[`) while the HUD shows tension and line left. Lines snap above their test, rods crack above their load, toothy fish bite through anything but a leader, a slack line lets the hook come loose, and a big fish can strip the reel. Rods, reels and lines wear, but one fight never takes more than a tenth of a reel.
- **Catch and release.** A landed fish is held up for everyone to see with a Keep or Release choice; release pays a quarter more XP and doesn't tire the spot. Fish have real weights and lengths in five size classes, heavy fish and trophies are single items carrying their weight, length, angler and day, and the Fishing Journal keeps each player's best.
- **Bait from Create.** A fan washing dirt or mud turns up worms and leeches, mixing makes maggots, heated mixing makes boilies, and chum thrown in the water (or from a dispenser) brings bites faster nearby. The Live-Bait Bucket holds baitfish and leeches for live bait, which predators prefer. The float's depth, set at the Tackle Bench, decides where the bait hangs.
- **Cleaning Table.** Cut fish with a knife (or a Deployer) into fillets, steaks, roe, bones, scraps and shark skin. Fish of 3 kg and more must be cleaned before they cook. Scraps mill into fish meal and press into fish oil, which a Blaze Burner burns.
- **Rod machines.** The Rod Holder rings and pulses redstone on a bite; the Basic, Geared and Fuel Reel Drives wind, strike and cast on Create rotation (or their own engine), and a pulling fish costs stress. They work on Aeronautics ships, towing a lure behind for trolling, so the big sea fish come to boats. Many drives in one spot share its fish, so a bank of them doesn't out-fish moving.
- **Aquarium tanks** of up to 8 blocks a side, with fish swimming at their real size, and baitfish that breed when fed fish meal.
- Every fish item is drawn as its own 3D fish, at its catch's size.
- Field Guide: the Fishing section is rewritten for all of this (tackle, the fight, landing, bait, live bait, cleaning, rod machines, trolling, aquariums), and Ask the Book's notes with it.
- Create Galore Fishing 0.2.0, Create Galore Bridge 1.33.0.

### Fixed
- **Hooked on Variety can be earned.** It asked for a plain Fishing Rod, which the pack always turns into another rod.
- The Bamboo Pole can be made from sugar cane too, so a first rod doesn't depend on finding bamboo.

### Removed
- **Tide 2 and Tide Extra Compatibility.** Everything players had from them stays, under the same ids: the fish and their mobs, the Fishing Journal and its progress, the crates, gadgets, special rods, Fish Displays (now Trophy Mounts), foods, hooks, baits and bobbers (as floats). Tide's plain rods become the matching new rods with their enchantments, name, damage and bait kept, the Angling Table becomes the Tackle Bench, and the vanilla Fishing Rod and Alloyed's Steel Fishing Rod become the Wooden and Steel rods.
- Tide's grilled tuna recipes, which never worked (the pack's tuna is Aquaculture's).
- The Enchanted Pocket Watch no longer freezes bosses from other mods (Cataclysm, Twilight Forest, Aether, Aquamirae's Captain Cornelia and Maze Mother and others), only ordinary mobs.

## 0.3.8

### Fixed
- **No more long freezes near old dungeons and ruins.** When new or saved land near a big structure (When Dungeons Arise keeps, Incendium's sanctum and others) loaded, the server could stop for 5 to 20 s: most structure files from other mods were saved by older Minecraft versions and get upgraded on the server thread the first time they are needed, and Steam 'n' Rails and Brewin' and Chewin' each ran their own full upgrade pass over every structure file again, though they only ever change their own blocks. Those two passes now skip files that hold none of their blocks, and the server upgrades all old structure files once in the background after it starts (about 3 minutes on the first start, 114 MB in `cache/create_galore`, a few seconds on later starts) and reads the upgraded copy from then on. In a test, loading the chunks around a When Dungeons Arise keep froze the server for 21.3 s before and 2.3 s after.
- **Jade no longer writes an error file when you look at some chests.** Looking at a chest the server had already turned into something else (a Lootr chest, a chest on a moving ship, a ghost block) made Jade ask for a chest inventory that wasn't there and fail with a NullPointerException. Such a chest now simply shows no inventory.
- **Ask the Book's drawings use the strict format again.** The drawing tool's colour choices were described in a way the API rejects, so it always fell back to the slower loose format.
- Create Galore Bridge 1.32.0.

## 0.3.7

### Added
- **Every snail can be your friend.** Naturalist's snails couldn't be tamed (they hide in their shell whenever someone comes near), and only Critters and Companions' snails gave Snail Slime. Naturalist's snails now turn into Critters and Companions' snails as they appear, in new and old worlds alike (also from eggs, spawn eggs or a released caught snail), keeping their name and age. So every snail can be tamed with leaves (1 try in 10) and gives a Snail Slime bottle every 5 minutes. They still drop Naturalist's snail shells and slime balls, so dyed shells and Shellstone stay craftable.
- **Every dragonfly can be tamed too.** Naturalist's dragonflies become Critters and Companions' ones the same way (tame them with a spider eye, give them armor). Frogs still eat wild ones and leave froglass, but never a tamed one.
- **Slime from every snail.** A glass bottle on Hybrid Aquatic's scalyfoot snail or the Moon's snail (Northstar) gives Snail Slime, and on Hybrid Aquatic's sea slug Sea Bunny Slime (Critters and Companions' sea bunny is switched off in the pack, so its slime and Sea Bunny Slime Blocks had no source). Each animal refills every 5 minutes, like the Critters snail.
- **Otters crack clams.** Hand a Critters and Companions clam (a river fishing treasure) to a Hybrid Aquatic otter and it breaks it open, with a pearl 1 time in 4, as the switched-off Critters otter did. Pearls and Pearl Necklaces had no other source.
- Field Guide: Exploration > Creatures has a page on which animals now match.
- Create Galore Bridge 1.31.0.

### Fixed
- **Twilight Forest animals drop what their Overworld cousins drop.** Its deer drop Naturalist's venison (the one the rest of the pack cooks with) and antlers, its boars drop fat and hide as well as porkchops, and its helmet crabs drop raw crab instead of cod. Venison jerky dries from either venison, and Naturalist's venison counts for cabbage rolls and Alex's Caves' raw meat like Twilight's.
- **Froglass can be had.** Naturalist's dragonflies were meant to leave froglass when a frog ate them, but their loot looked for an old variant number and never dropped any. Frogs now leave one of the three froglass colours.

### Removed
- Naturalist's snails no longer slow down crops next to them (that came with Naturalist's own snail, which no longer stays in the world).

## 0.3.6

### Added
- **Ask the Book builds proper builds.** Ask it to show you a boat, a cottage or a bridge and it now builds it the way a good Minecraft builder would: up to 32 blocks a side (was 16), with stairs, slabs, trapdoors and fences for curves and trim, written out layer by layer so a detailed build fits in one go. Before it shows a build it tries it as a draft and gets pictures of it (two corner views, the front and the side) to check the shape and colours, and fixes what looks off. In a test asking for "a proper boat", the old tool gave a brick box with a wool sheet for a sail; the new one gave a two-sail sloop with a curved hull, a stern cabin, a raised foredeck and a bowsprit.
- Builds and explanations are kept apart: models that explain machines still keep to the blocks that matter.
- Fences, walls, panes and bars in the book's models join their neighbours and stairs turn corners, as when you place them.
- Create Galore Bridge 1.30.0.

### Fixed
- **The book talks about the world as it is.** It no longer compares things with how a mod is set up elsewhere ("bosses have 0.7x health", "Cold Sweat is tuned gentler than its default", "three times vanilla's rate"). The Field Guide now gives the real values instead: each boss's health (the Netherite Monstrosity has 420), how many degrees each season shifts the air, and how often structures turn up per square kilometre. The book is told to do the same in its answers.
- Water and lava in large models are drawn in the right place.

## 0.3.5

### Fixed
- **Rain no longer comes and goes in short bursts.** Winds & Weather's map of weather regions scrolled faster the longer the world had existed (it multiplied its drift by the world's age instead of adding it up). After a day or two of a server running, the weather at one spot changed every few seconds, so its rain started, fell for 20 to 40 seconds and stopped, several times every few minutes. The map now moves at the steady speed it was built for: a rain spell from Winds lasts several minutes at any world age, and it rains just as often overall. Its storms, lightning, snow and puddles stop flickering the same way. On a test world aged like the server's, the weather at one spot changed 91 times in 6 minutes before (5 rain bursts) and 4 times after, and rain under a Winds rain region fell steadily for the whole 6 minutes. Normal and seasonal rain were already fixed in 0.2.20 and are unchanged.
- Create Galore Bridge 1.29.0.

## 0.3.4

### Fixed
- **Otters no longer wipe out the hermit crabs.** Hybrid Aquatic's otters live on the same sandy beaches as its hermit crabs and count every crab as food, hunting a new one each time they get hungry (about every 3 minutes). A hermit crab dies to a single bite and only a handful spawn around you, so a pair of otters kept a beach empty of them. Hermit crabs are now off the menu of every Hybrid Aquatic hunter (otters, octopuses, cuttlefish, wrasses, stingrays and the other crab eaters). Hunters still eat the other crabs, shrimp and fish, and fishing nets still catch hermit crabs. On a test server two hungry otters used to eat all 4 hermit crabs in a pen within 3 minutes; now all 4 live, while the fiddler crabs next to them still get eaten.
- Create Galore Bridge 1.28.0.

## 0.3.3

### Fixed
- **Lake ice in summer now melts instead of spreading.** Cold Sweat decided when water freezes, and it counts ice as a cold block: a few ice blocks chilled the water next to them below freezing, that water froze, and the ice crept across the lake even in Mid Summer, faster than the seasons could melt it. Water now freezes by biome and season (Serene Seasons), the same rule that decides when it snows, so ice can no longer feed itself. Ice already on a lake in a warm biome melts within a few minutes while you're nearby. Ice still cools you down as before, and blizzards still freeze lakes in winter and in cold biomes.

## 0.3.2

### Added
- **Ask the Book tries its 3D builds before showing them.** The server now places every model's blocks in a scratch copy and runs Create's own connection rules on them: which faces of each machine take a shaft, cogs meshing, belts, chain drives, water on a water wheel, and where rotation actually reaches. A model whose machines wouldn't work as placed is sent back to the book with the fault spelled out ("the shaft ends against the west face of the saw, which takes rotation only through its south face, its back"), and the book fixes it before you see it. It can still show a broken build on purpose, for example to show you what is wrong with yours.
- **3D models show flowing water and lava** the way the world draws them, with their moving texture and sloping surface, instead of tinted glass cubes.
- **Middle-drag moves a 3D model** around its frame (left-drag still turns it, scroll zooms).
- Create Galore Bridge 1.27.0.

### Fixed
- **Labels no longer cover 3D models.** They sit in columns beside the model, each joined to its block by a line.
- **Machines fed from the wrong side.** The book was told that a saw, press or drill takes its shaft at its back (the face opposite where it points), and the check above catches it when it gets that wrong.

## 0.3.1

### Added
- **Ask the Book's first question each hour costs less.** The book used to start every conversation by reading the whole Field Guide as players read it, with the key points, cross-references and repeats a person dipping into one entry needs. It now reads compact notes of the guide instead: every fact, item, recipe, number and link the guide holds, written once (about 18% shorter). When the notes are thin on something, the book reads the guide's own pages with a new lookup and can search the guide's full text. The cached copy still makes later questions within the hour cheap, as before.
- If a guide entry changes and its notes aren't updated, the server sends that section of the guide in full instead, so the book never misses anything.
- Create Galore Bridge 1.26.0.

## 0.3.0

### Fixed
- **Lakes no longer ice over in summer.** Winds & Weather places its blizzard and hail regions with no regard for biome or season, and while one hung over you it froze open water up to 32 blocks around you every 5 seconds, so a forest lake could ice over in Mid Summer. Its freezing now asks the game first: water only freezes where it would freeze anyway (Cold Sweat's temperature for that biome, season and time of day), and never next to a light. Blizzards still freeze lakes in snowy biomes and other cold places. Ice that already formed in a warm biome melts over the warm seasons as usual.
- **Fewer sky whales.** Out over the ocean, Sky Whales kept up to 3 whales around every player (its own default), and they only leave once you're 120 blocks away, so they piled up over coastal bases. Now at most one whale is near you at a time, and a new one takes a few minutes at sea to turn up. Whales already in your world drift off once you move away.
- Create Galore Bridge 1.25.0 (the ice fix on top of 0.2.24's Ask the Book update).

## 0.2.24

### Added
- **Ask the Book sees your screen.** When you ask, the book gets a description of what you are looking at: the blocks in view out to 48 blocks (through glass), water and lava surfaces, creatures and dropped items with their health, and the block or creature under your crosshair. Look at a mob or a machine and ask "what is this?".
- **The book finds your builds.** Ask "why doesn't my water wheel turn?" and the book looks up the blocks players placed near you, follows the build block by block with coordinates until it trails off, and reads what matters: rotation speed and direction, which network a part is on, stress against capacity, what the Goggles would show, and the water or lava around it (where it flows, how far it has spread, and which sources a player poured). It only searches the chunks the server simulates around you, and blocks any player placed there count, so it is intended for cooperative play.
- **It asks which build you mean** when more than one fits your question, naming each by where it is.
- **It only sees what players built.** Natural blocks, ores, loot and structures are invisible to it, so it can't be used to find diamonds. Creatures are only seen on your screen, never searched for.
- Placed blocks keep counting when they change (copper weathering, crops growing, machines switching state), when a piston pushes them, while they ride a Create contraption (they count again once it stops, also across a restart), and when they are assembled into or out of a Sable ship. Deployers, buckets, belts and the schematicannon count as placing.
- `/fieldguide builds add <from> <to>` (operators) adds builds made before this update: the box is outlined and the blocks count once you click Confirm in chat. `/fieldguide builds remove <from> <to>` takes blocks off the list.
- Create Galore Bridge 1.24.0.

### Known gaps
- Blocks placed before 0.2.24 need the operator command before the book can find them.
- Sand and gravel that fall after being placed are no longer counted where they land.

## 0.2.22

### Added
- **Ask the Book looks like the book now.** Its screen sits in the guide's own frame on a sheet of its paper, with book-style buttons, and it opens from a new speech-bubble ribbon on the guide's right edge (next to the bookmarks and search ribbons) instead of a plain button.
- **Chat layout.** Your questions sit on the right in a note with your face; the book's answers sit on the left under the guide's icon.
- **Answers you can skim.** The book now writes short sections with headings, bullets, bold words and real item icons, instead of walls of text.
- **Recipes on the page.** When the book explains how something is made, it shows the real recipe from this server: the crafting grid, or the Create machine with its inputs, outputs, chances and heat.
- **Your chests count.** The book remembers the chests, barrels and vaults you open, and counts what's in the ones within 50 blocks of you as yours. It can also tell you which chest holds something. Chests further away are left out.
- **3D models.** When the third dimension matters (how shafts and gearboxes connect, which way a block faces, the shape of a build) or you ask for it, the book builds a small model out of real blocks: drag it to turn it around, scroll over it to zoom. Create machines show as they look placed, moving parts included (water wheels, press heads, mixer whisks, fan blades), and a drawing can hold a model with notes and arrows around it. The book only builds one when it helps, since models cost more to write.
- **Save drawings and models.** Right-click any drawing or model in an answer to keep it as a page of its own in your pages list, to come back to without the conversation around it. Saved pages never drop out when your book fills up with conversations.
- Pages started before 0.2.22 keep working as they are, with a note suggesting a new page for the new features.
- Create Galore Bridge 1.22.0 (all of this on top of 0.2.21's captain fix).

### Fixed
- Labels in the book's drawings no longer run off the edge, cross the edges of boxes, or sit under arrows. The server now measures every label as the book paints it and sends the drawing back to be fixed when words would clip, and labels keep the same size on small screens.
- The Field Guide no longer reopens on the last page you read after you went back to the map. It reopens on a page only if you closed the book while reading it. This was a Modonomicon bug: it forgot that you had left the page.

## 0.2.21

### Added
- `/hostileskies resetkills <players>` (op, works from the server console) sets a player's pillager airship captain kills back to 0. Hostile Skies had no way to do this.

### Fixed
- A pillager airship captain that dies on its own (falls off, suffocates, its ship breaks up) no longer announces "Captain was killed!" as if you did it. The ship now says "The Captain died in an accident!" and still goes down.
- A captain kill only counts toward bigger airship tiers when a real player hurt the captain in its last 30 seconds, directly or with arrows and other shots. If you wounded it and it then fell to its death, the kill and the Captain's Orders are still yours. Create deployers no longer earn kills.
- Create Galore Bridge 1.21.0 (the captain fix on top of 0.2.20's rain fix).

## 0.2.20

### Fixed
- **Rain no longer stops after a second or two.** Winds & Weather copied its regional weather onto the normal rain every 3 seconds and switched rain off whenever no player stood in one of its wet regions. So rain from the normal weather cycle, the seasons or `/weather rain` was stopped almost as soon as it began, and tried again 5 minutes later. Now Winds & Weather can still bring rain and thunder while you are under one of its storms, but it never ends rain it didn't start: normal rain runs its full length again. Its own storm rain also no longer has short dry gaps, and ends a few seconds after you leave the storm. `/weather clear` still keeps the sky clear.
- Create Galore Bridge 1.20.0 (the rain fix on top of 0.2.19's Ask the Book).

## 0.2.19

### Added
- **Ask the Book.** The Field Guide can now answer questions in its own words. Press **Ask the Book** at the top right of the guide (or type `/askbook`) and ask anything about the pack. It looks at what you're holding, your inventory, what's around you, your advancements and the pack's recipes, so "what should I do next?" gets advice for where you actually are. Answers stream in with a typewriter, a quill sound you can turn off, and Minecraft-themed thinking lines. When a picture helps, it draws pixel-art diagrams on the page in the book's colours with real item icons. Each player keeps their own pages on the server.
- Works only on a home server whose owner saved their own Anthropic API key. The server asks for it at start (press K to change it, or type OFF). The key stays on the server machine, outside the server folder, and is never sent to players, written to logs, backups or the world. Setup: [docs/server-setup.md](https://github.com/emresahin0906-code/Create-Galore/blob/main-6fi2ef/docs/server-setup.md#the-field-guides-voice-your-own-api-key).
- Create Galore Bridge 1.19.0 (adds Ask the Book on top of 0.2.18's Tide fixes).
