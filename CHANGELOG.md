# Changelog

Each pack version gets a section here, with **Added**, **Fixed** and **Removed** where they apply. The release
workflow copies the section for the version in `pack/pack.toml` to the top of the GitHub release, and the build
fails when that section is missing.

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
