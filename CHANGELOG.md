# Changelog

Each pack version gets a section here, with **Added**, **Fixed** and **Removed** where they apply. The release
workflow copies the section for the version in `pack/pack.toml` to the top of the GitHub release, and the build
fails when that section is missing.

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
