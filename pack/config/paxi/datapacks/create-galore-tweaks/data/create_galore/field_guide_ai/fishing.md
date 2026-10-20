<!-- sources sha256:2b0eceb5081f384a84d027030c2bceeb04f7744fccb10a229f58ea6ff12e6496 -->
# Fishing (fishing)

## fishing/overview | Fishing
- **Tide** runs fishing: hold to cast, win a short timing game when a fish bites. Every plain Fishing Rod is a Tide rod, including ones made before Tide joined
- Where, when, how deep you fish decides what bites. Upgrade rods at an **Angling Table**; bait makes fish bite sooner. **Fishing Journal** records every fish caught
- 180+ fish across rivers, oceans, caves, lava, void, from Tide, Hybrid Aquatic, Aquaculture, Alex's Caves, a few others
- Pages: (see fishing/casting) (see fishing/rods) (see fishing/where_fish_live) (see fishing/bait) (see fishing/journal) (see fishing/fish_uses)
- Fishing Journal [tide:fishing_journal]: every player gets one on joining; catch a fish to unlock its page (where it lives, when it bites, your biggest)
- Tip: day one a plain rod (3 sticks, 2 string) is enough; any open river or sea gives fish to cook on a campfire (see food/first_food)
- Aquaculture's own rods and tackle are retired here (see fishing/aquaculture)

## fishing/casting | Casting and the Bite
- Casting: **hold** use to charge (bar fills over about 1 second), release to throw; full bar throws furthest
- Bite: bobber dips with a splash; you have a second or two to **right-click** to start reeling. Junk, treasure, crates reel straight in; a fish starts the timing game
- Timing game: bar over crosshair with coloured zone in middle, marker sweeping across; right-click while marker inside zone. Inside: **Good**, fish is yours. Dead centre: **Perfect Catch!** and more experience. Outside: **Miss**, fish escapes
- Each fish moves the marker its own way (smooth swings, pauses, jitters, sudden darts). Strong fish: narrow zone; fast fish: fast marker
- Never click: fish gives up after a few seconds, line stays in water. A miss costs 1 rod durability but no bait
- **Open water** = 5x5 patch of water at least 2 blocks deep with open air above around the bobber. Surface crates and best treasure only from open water; fish don't care
- Rain on bobber, or fishing under a roof, makes bites come a little faster in water
- Each bite rolls fish, junk, treasure or crate. Luck (Luck of the Sea, lucky bait, some rods and lines) shifts roll from junk toward treasure and rare fish. Lure and bait speed make bites sooner
- Operators see odds: cast, then `/fishing test fish` (or `loot`, `crate`)

## fishing/rods | Fishing Rods
- Craft Stone, then upgrade at a **Smithing Table** with string as template: iron, gold, amethyst, diamond. Better rods last longer and hold more **bait**. **Netherite** or **Blazing** rod fishes in lava. Six special rods are found, not made
- All rods take Lure, Luck of the Sea, Unbreaking, Mending; repaired with string on an anvil. A broken rod gives back its line, bobber, hook
- Bait slots = how many different baits the rod holds at once (see fishing/bait)
- Fishing Rod [minecraft:fishing_rod]: 3 sticks, 2 string; 32 uses, 1 bait slot; pre-Tide rods are these, enchantments kept
- Stone Fishing Rod [tide:stone_fishing_rod]: cobblestone and string; 48 uses, 1 slot; usual base to smith from
- Iron Fishing Rod [tide:iron_fishing_rod]: smith plain or stone rod with iron ingot; 64 uses, 1 slot
- Golden Fishing Rod [tide:golden_fishing_rod]: smith with gold ingot; 36 uses, 2 slots, +1 luck
- Crystal Fishing Rod [tide:crystal_fishing_rod]: smith with amethyst shard; 80 uses, 2 slots; chimes when a fish bites
- Diamond Fishing Rod [tide:diamond_fishing_rod]: smith with diamond; 128 uses, 2 slots; extra experience every catch
- Netherite Fishing Rod [tide:netherite_fishing_rod]: Diamond rod + netherite ingot + Netherite Upgrade; 512 uses, 2 slots, fireproof, **fishes in lava**
- **Found, not made** (256 uses each):
  - **Rod of Resonance**: ancient city chests; 3 slots; on a bite tells what is hooked
  - **Prismarine Fishing Rod**: sometimes dropped by Elder Guardians; 3 slots; +1 speed and +1 luck in rain
  - **Suncatcher**: rare catch in plains; 6 slots; +1 luck in sunshine
  - **Rod of the Hero**: expert Fisherman villager sells for 15 emeralds; 4 slots; extra village item every catch
  - **Blazing Fishing Rod**: nether fortress chests; 4 slots; fishes in lava; finds Nether lava fish anywhere
  - **Beeline**: sometimes found harvesting honeycomb from a hive; 5 slots
  - **Midas Fishing Rod**: smith a golden rod with a **Midas Fish**; 3 slots, +1 luck, bonus gold item every catch
- Owning all 14 rods earns **Hooked on Variety**
- Two other rods keep old cast-and-wait fishing, no timing game: **Alloyed**'s Steel Fishing Rod (see industry/alloyed) and any Aquaculture rod you own. Hybrid Aquatic off-hand hooks work with both

## fishing/angling_table | Angling Table and Tackle
- **Angling Table** fits a line, hook, bobber to any Tide rod. **Lines** make timing game easier; **Hooks** change what bites or allow lava/void; **Bobbers** only looks
- Angling Table [tide:angling_table]: 2 string, 2 copper ingots, 2 planks. Put rod in, add tackle, take upgraded rod out; putting a fitted rod back returns its tackle
- **Lines** (two ingots or gems with a Fishing Line between): **Copper** marker 10% slower; **Golden** marker 5% slower, +1 luck; **Iron** fish 15% easier to land; **Diamond** fish 25% easier to land. Fishing Line (3 string in a column) does nothing by itself
- **Tide hooks**: **Fiery** (blaze rod): water counts warmer, warm-water fish bite; **Permafrost** (blue ice): water counts colder; **Twilight** (clock): fish act as if 12 hours later, night fish bite by day; **Lavaproof** (gold and obsidian): fish in lava with any rod; **Hook of the Depths** (crying obsidian, dragon's breath): fish in the void (see fishing/lava_and_void)
- **Hybrid Aquatic hooks** also fit: **Barbed** bites sooner by day; **Glowing** sooner at night; **Magnetic** +1 luck; **CreeperMagnet** next catch is a creeper, breaks after one use; **Ominous** (9 different crab claws) next catch pulls up **Karkinos**, Hybrid Aquatic's giant crab boss, breaks after one use
- Watch out: Karkinos lands at your feet; fish from somewhere you can fight
- **Bobbers**: look only; 16 dye colours, apples, ingots, gems, a duck, more; each = slime ball + plank + the item
- Aquaculture hooks, lines, bobbers don't fit Tide rods

## fishing/bait | Bait
- Bait goes **inside** the rod: pick up bait stack on cursor, right-click the rod; right-click rod with empty cursor to take bait out
- Each fish caught uses 1 bait from every slot; used only when you land a **fish** (junk, treasure, crates, misses cost nothing)
- Each slot holds one kind of bait (whole stack); a 2-slot rod carries two different baits, both work
- **Bait**: bites much sooner. **Worm**: bites sooner. **Lucky Bait**: more luck. **Magnetic Bait**: crates 5x as often
- recipe tide:bait: crop, mushroom or small flower, rotten flesh, seeds, bone meal make 4 Bait
  - shows recipe tide:bait
- Worm Farm [aquaculture:worm_farm]: still makes **Worms**, work as bait in Tide rods; feed compostable scraps
- **Lucky Bait**: 8 Bait around a rabbit's foot make 16. **Magnetic Bait**: lapis, 2 Bait, redstone, iron nuggets, prismarine make 4
- **Incandescent** and **Abyss Bait** come from void fish, bites sooner
- Naturalist's **Butterflies** and **Caterpillars** (caught with Capture Net) work like worms

## fishing/where_fish_live | Where Fish Live
- **Ocean and beach** water is salt; rivers, lakes, everything else fresh. **Biome** and its temperature decide most fish. **Depth**: below y40 you catch cave fish. **Time, weather, moon, season** decide rare ones
- Journal page lists what a fish wants once caught; before that a **Fishy Note** (from a crate, or crafted from a fish and paper) shows the page
- Most fish have favourite **temperature**: wrong climate they still bite, rarely. Fiery and Permafrost hooks nudge it
- **Fresh water** (rivers, lakes, swamps): trout, pike, perch, crappie, bass, walleye, carp, catfish, more; swamps, jungles, deserts, cherry groves, frozen lakes each have own
- **Salt water**: cod, snapper, flounder, anchovy, tuna, sharks, rays, sailfish, Hybrid Aquatic reef fish
- **Underground** (water below y40): cave eels, glowfish, groupers, ore fish **Iron Tetra**, **Gilded Minnow**, **Lapis Lanternfish**, **Crystal Shrimp**; deeper = stranger; deep dark, dripstone caves, trial chambers have own. Alex's Caves biomes have own fish
- **Rare fish**: five planet fish each bite in ocean under one moon phase; **Shooting Starfish**: deep ocean, full moon, night; **Coelacanth**: deep ocean, night in rain; **Bull Shark**: rivers evening to morning
- **Legendary**: Shooting Starfish, Coelacanth, Devils Hole Pupfish (narrow cave band y20 to y30), Midas Fish (underground, needs 5 luck), Alpha Fish (overworld void), Dragon Fish (End void). Catch one for **One in a Million**
- **Seasons** count: some bite only in some seasons (brook trout skip autumn, sturgeon come autumn and winter); journal shows which
- Gadgets showing time, moon, depth, temperature, weather: (see fishing/gadgets)

## fishing/lava_and_void | Lava and Void Fishing
- **Lava**: Netherite or Blazing rod, or **Lavaproof Hook** on any rod. **Void**: fit **Hook of the Depths**, cast into open void of the End. Bites slower in both
- Lava fishing works in any lava pool; catches can't burn. Overworld lava: ember koi, magma mackerel, obsidian pike, more; Nether adds witherfin, soulscale, Blazing Swordfish. **Blazing Fishing Rod** catches Nether lava fish anywhere
- Lava junk: obsidian fragments (4 make obsidian), fish bones (bone meal). Lava crates are **Obsidian Crates**; break with diamond pickaxe or better
- Watch out: **Witherfin** gives Wither when eaten; most other lava fish give Fire Resistance
- **Void fishing**: bobber below world's void line: in **The End** anything below y50 (cast off an island edge); Overworld y-70 and below, under bedrock floor
- End void fish: Enderfin (ender pearl), Endergazer (eye of ender), Elytrout (phantom membrane), legendary **Dragon Fish**
- Overworld void has own fish, Alpha Fish among them. None for the Nether void
- **Voidseeker** doesn't send you anywhere when eaten: no way home but the ride (see transport/getting_around)

## fishing/gadgets | Fishing Gadgets
- Tide gadgets show conditions fish care about. Hold one, wear in a **Curios** slot, or place block versions nearby; reading shows in top corner. Right-click to read once
- Pocket Watch [tide:pocket_watch]: exact time; a clock and a chain
- Lunar Calendar [tide:lunar_calendar]: tonight's moon phase; paper, ink sac, spyglass; can be placed
- Climate Gauge [tide:climate_gauge]: biome temperature; glass, iron nugget, redstone
- Depth Meter [tide:depth_meter]: height above/below sea level; iron ingots, stick, redstone
- Weather Radio [tide:weather_radio]: when weather changes next; lightning rod, redstone, copper; placed as a block
- Fish Finder [tide:fish_finder]: all five readings at once; made from all five gadgets
- Enchanted Pocket Watch [tide:enchanted_pocket_watch]: Pocket Watch + **Coelacanth**; right-click a mob to freeze it in place (not bosses), again to free; 10-second cooldown

## fishing/journal | Fishing Journal and Trophies
- **Fishing Journal** fills a page per new fish. Every caught fish has a **length**; journal keeps biggest and smallest. **Fishy Notes** show a page before catching. Mount fish on a **Fish Display**
- Lost journal: craft from any fish and a book. Also works on a lectern
- Uncaught fish are black outlines. Page shows where/when it bites (biome, depth, time, weather, moon, season, temperature), then count caught, when, record sizes
- Fish caught alive can go in a bucket: combine with a water bucket in inventory (lava fish need a lava bucket); length stays
- Fill half the journal for **Aquatic Archivist**, all for **A Reel Completionist**
- Fishy Note [tide:fishy_note]: every crate holds one, usually for an unfound fish; or craft any fish with paper; right-click to see that fish's page
- Fish Display [tide:fish_display]: sticks around a log; hang on wall, right-click with a fish to mount at real size; empty hand takes it back
- Fish Satchel [tide:fish_satchel]: 4 leather around a fish; right-click to open; while open, picked-up fish go straight in; holds 64 fish
- Aquaculture's fish mounts still work for its fish. Every caught fish carries its own length, so fish of different lengths don't stack

## fishing/crates | Crates and Treasure
- Now and then a **crate** flies out of the water and lands as a block; open like a barrel. Crates and best treasure need **open water**. Magnetic Bait brings far more crates. **Neptune's Bounty** is how you find Neptunium
- **Wooden Crates** vary by where you fish: fresh or salt water at surface, underground (y0 to y40), or deep (below y0, with diamonds). Each holds 6 to 10 stacks and a Fishy Note
- **Obsidian Crates** from lava; **Purpur Crates** from The End (shulker shells and End loot; the Elytra they used to hold are shulker shells here)
- **Hybrid Aquatic crates** and crab pots come up too; break with an **axe** for loot
- Other treasure: name tags, saddles, enchanted books and gear, Neptunium nuggets, leeches, Critters and Companions' clams in rivers. Ocean monuments, trial chambers, desert wells have own catches
- Neptune's Bounty [aquaculture:neptunes_bounty]: rare open-water chest; under the sea junk is Neptunium: 1 to 4 ingots, or a Neptunium tool, armor piece, bow or fillet knife
- Neptunium [aquaculture:neptunium_ingot]: Aquaculture's sea metal for tools, armor, bow; nuggets also come as treasure; 9 make an ingot

## fishing/fish_uses | What Fish Are For
- Cook any fish on campfire, smoker or furnace. **Ore fish** craft into raw iron, raw gold, lapis, amethyst. Some fish become **gear** (swords, a bow, boots). A few have **effects** eaten
- Tide fish cook into **Small**, plain or **Large Cooked Fish** by size. Knife on a Farmer's Delight board cuts any into 2 **raw fillets** and bone meal, same fillets Aquaculture fish give. Meals: (see fishing/sea_food)
- **Craft a fish into**: Iron Tetra: 2 raw iron; Gilded Minnow: 2 raw gold; Lapis Lanternfish: 2 lapis; Crystal Shrimp: 4 amethyst; Glowfish: 2 glow ink sacs; Enderfin: ender pearl; Endergazer: eye of ender; Elytrout: phantom membrane; Luminescent Jellyfish or Sun Emblem: 8 **Jelly Torches** (burn underwater); Incandescent Larva and Pale Clubfish: bait
- **Gear**: Sailfish, Swordfish, Blazing Swordfish are swords (last sets things on fire). Shooting Starfish + bow: **Starlight Bow**. Dragon Fish + netherite boots: **Dragonfin Boots**, a jump in mid-air
- **Eaten**: lava fish give Fire Resistance; Uranias Pisces 10 minutes of Luck; Ender Glider and Mantyvern Slow Falling; Chorus Cod teleports like chorus fruit
- Watch out: Witherfin gives Wither; several Hybrid Aquatic fish poison you raw
- **Chasm Eel** can't eat through bedrock. Cats eat cookable fish. Tide's Fish Slices are replaced by Aquaculture's fillets

## fishing/sea_food | Sea Food
- Two Farmer's Delight (see food/farmers_delight) add-ons use its pot, cutting board, knives; meals follow the pack's normal food rules
- **Ocean's Delight** cooks vanilla ocean mobs: squid, cod, kelp, sea pickles, pufferfish, guardians, Elder Guardians
- **Aquaculture Delight** cooks Aquaculture (see fishing/aquaculture) fish: perch, halibut, pollock, tuna, catfish, bass, turtle meat; caught on any rod now
- Guardian Soup [oceansdelight:guardian_soup]: one-pot soup from Guardian parts
- Stuffed Squid [oceansdelight:stuffed_squid]: squid meals; Cut Tentacles and Squid Rings come from cutting squid
- Fish and Chips [aquaculturedelight:fish_and_chips]: Aquaculture Delight meals use fillets and fish; also includes a Neptunium Knife
- Neptunium Knife [aquaculturedelight:neptunium_knife]: Farmer's Delight style knife made with Neptunium, for cutting fish
- Any Tide fish cut on the board gives same **raw fillets**, so every catch can go into these meals (see fishing/fish_uses)
- Same fish feed automated kitchens (see food/kitchen_automation). Hybrid Aquatic clams cook into Cooked Clam; Naturalist's clams give Clam Meat

## fishing/aquaculture | Aquaculture Fish and Old Rods
- Aquaculture's **fish** now bite on any rod, each in own biome. Its **rods, hooks, lines, bobbers, Tackle Box** can't be crafted any more. Rods you **already own** still work the old way. Fillet knives, Worm Farm, Neptunium and its food all stay
- Why: Tide makes every new rod and its tackle doesn't fit Aquaculture rods (or vice versa); two tackle systems would confuse. Retired items hidden from JEI; owned copies stay in inventory and keep working
- **Where Aquaculture fish bite**: **Jungle rivers**: arapaima, piranha, tambaqui, Arrau turtle; **Hot rivers** (desert, badlands, savanna): bayad, boulti, capitaine, synodontis; **Swamps**: perch, box turtle; **Rivers**: gar, minnow, pink salmon; **Fresh water** by season and temperature: bluegill, smallmouth bass, rainbow and brown trout, muskellunge, carp; catfish at night; **Ocean and beach**: Atlantic cod, halibut, herring, blackfish, Pacific halibut, pollock, red grouper, tuna, jellyfish; **Mushroom fields**: red and brown shrooma; **Twilight Forest**: starshell turtle
- Bluegill, carp, catfish, rainbow trout, smallmouth bass, tuna, arapaima also existed in Tide; piranha, carp, tuna in Hybrid Aquatic. Each is one fish now, Aquaculture's: journal lists it once, recipes take one item
- **Old Rods**: owned Aquaculture rod keeps Aquaculture's fishing: cast, wait for splash, reel in; no timing game; catches from Aquaculture's tables, not Tide's. Its hook, line, bobber, bait still go in via the rod's menu or an old Tackle Box. Aquaculture hooks don't fit Tide rods
- **Hybrid Aquatic hooks** held in **off hand** while casting work with old Aquaculture rods and Alloyed (see industry/alloyed)'s Steel Fishing Rod (also old-way). On a Tide rod they go on at the Angling Table (see fishing/angling_table)
- Fillet Knife [aquaculture:iron_fillet_knife]: Iron, Gold, Diamond, Neptunium Fillet Knives still craft; craft one with a fish for Raw Fillets; a Farmer's Delight knife on cutting board does the same
- Worm Farm [aquaculture:worm_farm]: still makes **Worms**, bait in Tide rods (see fishing/bait)
- Neptunium still from Neptune's Bounty (see fishing/crates), now reeled in on any rod in open water; the Neptunium rod is out of that loot
- Create **Integrated Farming**'s Fishing Nets (see food/integrated_farming) are not rods; they fish on moving contraptions exactly as before
