<!-- sources sha256:146f15624b340c4acc193826f899a618b608362e0db63593217d0f248c71f549 -->
# Fishing (fishing)

## fishing/overview | Fishing
- Fishing runs on Create Galore Fishing. Rods are **built from parts**: a blank, a reel, line, a hook and a float, sinker or lure, fitted at a Tackle Bench (see fishing/tackle_bench) or by right-clicking the rod with a part in the inventory. A rod holds fish up to a weight set by its weakest part
- Hold use to cast, **strike** by lifting the rod (attack) on a bite, fight with the **drag**, the reel and the rod. What bites depends on water, time and bait. Fish have real weights
- 180+ fish across rivers, oceans, caves, lava, void, from Create Galore Fishing, Hybrid Aquatic, Aquaculture, Alex's Caves, a few others
- Start here: (see fishing/casting) (see fishing/the_fight) (see fishing/landing) (see fishing/rods) (see fishing/tackle) (see fishing/bait) (see fishing/where_fish_live). Further: (see fishing/cleaning) (see fishing/rod_machines) (see fishing/trolling) (see fishing/aquarium) (see fishing/journal)
- Fishing Journal [tide:fishing_journal]: every player gets one on joining; catch a fish to unlock its page (where it lives, when it bites, your longest and heaviest)
- Tip, first trip: a Bamboo Pole [create_galore_fishing:bamboo_pole] (3 bamboo or sugar cane) needs no reel; right-click it with a String Line [create_galore_fishing:string_line] and a Small Hook [create_galore_fishing:small_hook] in the inventory, add worms, cast into any river (see food/first_food)
- Aquaculture's own rods: (see fishing/aquaculture)

## fishing/casting | Casting and the Bite
- **Hold** use to charge, let go to cast; full charge takes a second. A full cast goes 8 m plus 7 m per metre of rod; a rig far lighter/heavier than the rod's casting range ("Casts 5-30 g best" tooltip) falls short. A pole reaches only its 8 m of line; no cast goes past the line on the reel
- A rod casts only with line and a hook (or a lure), and a reel unless it is a pole
- Rigs: **float** holds bait at the surface, dips on a bite; **sinker** takes bait to the bottom, rod tip taps on a bite; **lure** needs no bait: wind it back by holding use, it dives to its depth and works at the speed it likes; lifting the rod twitches it toward you
- Bite: plop on the float or tap on the rod, "Bite! Lift the rod to strike" blinks above the hotbar. **Strike** by lifting the rod (hold attack, left click) within the window: about 1.4 s on a float or sinker, 1 s on a lure. Reeling never sets the hook. The strike is a flick; a rod already held high has nothing left to lift, let it down first. Miss: the fish lets go, and 3 times in 10 takes the bait
- Junk, treasure, crates are struck the same way and come in as a dead weight
- Enchantments: Lure and Luck of the Sea work on every rod; each Lure level cuts the wait by about 15%; luck shifts the roll from junk toward treasure and rare fish. Unbreaking and Mending work
- Wait times: a rig fish want in a good spot gets a bite about every 17 s on average; poor match about once a minute; bare hook nothing wants two minutes or more; no wait past four minutes. Rain shortens waits 15%. Lava waits 1.6 times as long, void 1.8 times
- **Open water** = 5x5 patch of water at least 2 blocks deep with open air above around the rig. Treasure and crates only from open water; fish don't care
- Catches tire a spot (see fishing/where_fish_live). Operators: cast, then `/cgfish hook` shows how many species can bite there and the average wait

## fishing/the_fight | The Fight
- **Drag** gives line when a fish pulls harder than it; set it below what the line holds. **Hold use** to reel (slower the harder it pulls); when it pulls more than the drag, the drag slips and line runs out at the fish's speed
- What a hooked fish does: holds and pulls (swaying, drifting round the angler on a slow arc); **runs** (more pull and speed, sweeping off to one side); **head shakes** (the line throbs ~3 times a second); **swims at you** (line goes slack unless reeled in faster than it comes; HUD: Swimming at you! Reel in the slack); tired, it **sulks** a few seconds pulling less (HUD: It's sulking: gain line), the moment to gain line
- Stretch: line stretch and rod bend make tension build and ease smoothly instead of jumping; string and mono give a lot (soften lunges), braid hardly gives (every lunge felt at once). Slack line hangs in a visible curve from the rod tip
- **Lifting**: hold attack (left click) to lift the rod, let go and it comes back down; flicks up with a light fish, lifts slowly under a heavy one; a thin bar beside the Rod stress bar shows the height. A lift draws the fish back by most of the rod's length (0.9 m of line per metre of rod), against the drag but not against the reel's crank
- **Pumping**: reel while the rod comes down and it comes down as fast as the reel takes up line, no load on the crank. Lift, wind down, lift again: brings in a fish too heavy to reel. Watch out: letting the rod down without reeling leaves slack; slack over a second can throw the hook
- A fish tires against the line's tension, so a drag set too light never wears it out. A tired fish within 1.5 m is landed; a fresh one bolts again
- Change drag any time a rod is in hand (before casting, waiting, mid-fight): **Drag +** / **Drag -** keys, default **]** and **[** (rebind under Create Galore Fishing in Controls), or scroll wheel while a line is out or while sneaking. Setting stays on the rod. A pole has no drag
- Rod angle: rod held a little above where you look, up to 60° higher while lifted. Look at the fish: the rod bends and soaks spikes of runs and jumps. Look away or up: side pressure, tires the fish faster. Watch out: pointing the rod straight down the line soaks nothing; every jump hits the line in full
- HUD: three upright stress bars at the right of the screen: **Line** against its test (tick where the drag slips), **Reel** against its drag and crank (grinding fills it), **Rod** against its rating; green, yellow from 60%, red from 85%, line blue when slack. Lets you see which part is overstressed and how even the rig is. Above hotbar: drag, line out, line left; alerts Drag slipping, Slack line!, Swimming at you! Reel in the slack, It's running! (only while it takes line), Head shakes!, It's sulking: gain line. Bars ease a little (quick up, slower down)
- Losses (chat then says what broke and why, with the numbers): **snapped** line (drag set at or over the line's or leader's test; a drag below the test always slips first, so a line never snaps below its test except frayed through by teeth); **bitten off** (teeth fray line without an Iron or Steel Leader, faster the bigger the fish for the line; one far too big bites through at once); **rod broke** (load well over the blank's rating, or rod worn through); **reel seized** (cranked while the drag slipped until it ground through; the line parts); **threw the hook** (slack over a second: fish not pulling and you not reeling; a fish being reeled in keeps the hook however small; a jump on a loose line; a hook too small for its mouth, chat names a size that holds); **spooled** (took every metre, then snapped the line against the empty reel)
- Snapped/bitten/spooled/seized: lose the line that was out plus a few metres, plus leader, hook, float or lure and the one bait on the hook; bait still waiting in the rod stays, and the line left is unfrayed (the frayed part went). A broken rod gives its parts back
- Wear: every fight wears the rod, reel, leader, hook and float, sinker or lure a little, even an easy catch: rod and reel lose 1 durability, leader, hook, float, sinker or lure each last about 200 fights (tooltip shows Worn %; the tackle bench warns above 90%). A line never wears from use, load or long hard fights: it holds or it snaps. Only teeth fray it (line shows Frayed %); an Iron or Steel Leader keeps teeth off it, so a line behind one never frays. A rod or reel worn through, or a line frayed through, breaks mid-fight and the fish is gone; a leader, hook, float, sinker or lure can wear out mid-fight too, but a hooked fish stays on. Check tackle before going after a big one (like a pickaxe before a long dig). Reeling while line runs out grinds the reel: after half a second it wears fast, with the square of the drag (half the reel's drag grinds a quarter as fast)
- Walking or sailing away pulls on a hooked fish: a small one is towed along, a big one takes line through the drag
- In depth: rod loaded above 70% of its rating wears, above its rating can crack. Worn rod holds less (down to 60% when nearly broken); frayed line holds less (down to half its test); the rod's tooltip (Line holds X), the line's tooltip (Frayed N%: holds X now), the drag message, the Tackle Bench and the HUD all show what it holds now, red when the drag is over it; a drag at or under that number never snaps the line; casting with the drag over it puts a red warning on the action bar; worn reel's drag gets jerky. Toothy fish: pike, muskellunge, gar, piranha, barracuda, sharks, morays, anglerfish, lionfish, Dragon Fish. Without an iron or steel leader teeth fray the line a little all fight and more on each run or jump, with the square of their pull against the line's test; one pulling over three times the test can bite through at once
- Sounds: drag ratchet clicks as line leaves the spool (slow tick when leaning, a scream on a fast run), reel cranks and grinds, line creaks above about 65% of breaking load, snaps with a twang
- Letting go of the rod, logging out or the chunk unloading reels the line in: a fish on is lost
- Related: (see fishing/landing) (see fishing/tackle) (see fishing/tackle_bench)

## fishing/landing | Keep or Release
- Landed fish is **held up** for everyone to see (by the mouth, or both hands from 4 kg); screen shows species, size class, weight, length, rarity, your best of the species. **Keep**: the fish + its experience. **Release**: experience +25%, fish swims off, spot not tired
- Close the screen and keep holding; right-click reopens. Can't cast while holding. Fish lives two minutes from the catch; then Release greys out ("It didn't make it") and it is kept. Logging out keeps it
- Experience per catch: base by rarity x fish's weight (also against its species' usual) x 1.5 trophy or 2.5 unique x fight difficulty. Small common panfish about 2, a 4 kg carp about 6, big rare sea fish dozens. Junk and treasure a little
- Size classes from the species' usual size range: **Young** below it; **Common** inside; **Large** above; **Trophy** well toward the species' record; **Unique** close to the record, very rare
- Every fish has real **length and weight**. Under 3 kg: stack by species and size class. Heavier fish, trophies, uniques: single items carrying weight, length, catcher, day. Trophy names aqua, uniques purple. Each catch goes into the journal (see fishing/journal)
- Alive fish: a fish kept while alive stays alive the rest of its two minutes (tooltip counts down); dropped into water then (by hand, off a belt, out of a chute) it swims away, no experience; sorting lines can throw back unwanted fish. A fish just caught goes into an Aquarium Tank alive (see fishing/aquarium)
- Fish items of every mod are drawn as their own animal's model at catch size (hand, dropped, item frames, Trophy Mount, inventory icon); the fish on the line too. Fishing events reach other mods, so Fishy Business and Aquaculture's weights work

## fishing/rods | Rods
- A **rod blank** is the base: length (longer casts further), load it holds before overload, **action**, casting range in grams. Bamboo Pole needs no reel; all other rods do. Heavy Boat Rod lands tuna and sharks. Netherite Rod fishes lava
- Action: fast bends at the tip, slow along the whole blank; slow soaks shocks best, fast puts load through quicker. Rods bend toward the line as load grows
- Bamboo Pole [create_galore_fishing:bamboo_pole] (shows recipe create_galore_fishing:bamboo_pole): 3 bamboo or 3 sugar cane; 4 m, holds 4 kg, slow, casts 1-15 g; no reel, no drag, fixed 8 m of line; 150 uses
- Wooden Rod (shows recipe create_galore_fishing:wooden_rod): 2.1 m, 5 kg, moderate, 2-20 g; 250 uses. A plain rod of 3 sticks and 2 string is one too
- Iron Rod (shows recipe create_galore_fishing:iron_rod): 2.4 m, 9 kg, fast, 5-30 g; 450 uses
- Steel Telescopic Rod (shows recipe create_galore_fishing:steel_rod): 3 m, 16 kg, fast, 8-45 g; slides out on the cast; 800 uses; any steel works
- Heavy Boat Rod (shows recipe create_galore_fishing:boat_rod): 2.1 m, 45 kg, moderate, casts 30-200 g; for tuna, arapaima, sharks, by hand or on a boat; 1200 uses
- Netherite Rod [create_galore_fishing:netherite_rod]: smith Steel Telescopic Rod + netherite ingot + Netherite Upgrade; 2.7 m, 30 kg, fast, 10-80 g; fireproof, **fishes in lava**; 2000 uses
- Found, not made (600 uses each): **Rod of Resonance**: ancient city chests; on a bite tells what took the bait. **Prismarine Fishing Rod**: Elder Guardian drop; in rain faster bites and +1 luck. **Suncatcher**: rare catch in plains; +1 luck in sunshine. **Rod of the Hero**: expert Fisherman villager, 15 emeralds; bonus village item every catch reeled in by hand. **Blazing Fishing Rod**: nether fortress chests; fishes lava, catches Nether lava fish in any lava. **Beeline**: 1 time in 20 when harvesting honeycomb from a full hive. **Midas Fishing Rod**: smith an Iron Rod with a **Midas Fish**, string as template; +1 luck, bonus gold item every catch reeled in by hand. Special rods take the same parts
- Special blanks: Resonance 2.4 m 10 kg; Prismarine 2.7 m 12 kg; Suncatcher and Rod of the Hero 2.1 m 7 kg; Blazing 2.7 m 20 kg; Beeline 2.1 m 8 kg slow; Midas 2.4 m 12 kg
- Alloyed's Steel Fishing Rod is a Steel Telescopic Rod wherever one turns up
- Older rods convert when a world loads them: Stone Fishing Rods -> Wooden Rods; Iron and Golden -> Iron Rods; Crystal and Diamond -> Steel Telescopic Rods; Netherite -> Netherite Rods; line, hook, bobber, bait come along with a matching reel
- Related: (see fishing/tackle) (see fishing/tackle_bench) (see industry/tfmg_steel) (see fishing/lava_and_void)

## fishing/tackle | Reels, Lines and Hooks
- Reel: top drag, speed, line held. Line: **test** (pull it snaps at), stretch, visibility. Hook size picks the fish's size. Floats, sinkers, lures set how bait is shown
- Tackle is crafted from Create parts (cogwheels, shafts, precision mechanisms, andesite alloy, iron/copper/brass sheets) plus TFMG steel, plastic, rubber; JEI (see start_here/tools) shows every recipe
- Reels [create_galore_fishing:copper_reel] (max drag, line held): Copper Spinning 3 kg 100 m; Iron Spinning 6 kg 150 m; Brass Precision 10 kg 200 m; Steel Baitcaster 14 kg 180 m; Big-Game Trolling 30 kg 600 m. Better reels wind faster (1 to 1.8 m/s), crank harder, smoother drag soaks spikes (30% Copper, 85% Brass Precision). Durability: Copper 300, Iron 500, Brass and Steel 900, Big-Game 1500; a broken reel is gone
- Lines [create_galore_fishing:string_line]: spool goes in the line slot; tooltip shows test, stretch, visibility, metres left. Test, stretch, visibility, metres: String 4 kg 70% 70% 100; Mono 8 kg 50% 40% 150; Clear 10 kg 30% 10% 150; Braided 18 kg 10% 80% 200; Heavy Mono 50 kg 45% 50% 600
- Stretch soaks shocks (braid barely stretches, jumps hit hard). Shy fish (trout, salmon, carp, koi, char, grayling, bonefish) mind a visible line. Braided Line is spun from 8 TFMG **Synthetic String** (a Rubber Sheet on a stonecutter makes 4) around a stick. Under 5 m of line left the rod can't cast
- Leaders [create_galore_fishing:clear_leader]: optional, at the hook. Clear Leader 12 kg, 10% visible, hides strong line from shy fish. Iron Leader [create_galore_fishing:iron_leader] 12 kg, crafted from 3 iron nuggets and a string (makes 2), early; teeth can't cut it. Steel Leader 60 kg, same for big fish. Teeth fray anything else
- Hooks [create_galore_fishing:small_hook]: Small (#10) panfish; Medium (#4); Large (1/0) pike, bass, carp; Big-Game (6/0) tuna, sharks. Far too big scares a fish off; too small holds badly
- Golden and Netherite Hooks [create_galore_fishing:golden_hook]: Golden medium size, +1 luck; Netherite large, survives lava, any rod with it fishes lava
- Special hooks: Fiery (water counts warmer, warm-water fish bite); Permafrost (colder); Twilight (fish act as if 12 hours later); Lavaproof (fishes lava); Hook of the Depths (fishes the void)
- Hybrid Aquatic hooks fit: Barbed (bites a quarter sooner by day, holds better); Glowing (a quarter sooner at night); Magnetic (+1 luck, twice the treasure and crates); CreeperMagnet (next bite is a creeper flung at you); Ominous (9 different crab claws; next bite brings up **Karkinos**, Hybrid Aquatic's giant crab boss). CreeperMagnet and Ominous wear with each use. Watch out: Karkinos lands at your feet; fish where you can fight
- Float and Lead Sinker [create_galore_fishing:float]: Float 4 g, bait at surface, dips on a bite; Lead Sinker 15 g, bait on the bottom. Bobbers of every look (dyes, apples, gems, a duck, more) work as floats
- Lures [create_galore_fishing:spoon] (dive depth, best speed m/s): Spinner 0.6 m 0.3-1.8; Spoon 1 m 0.4-2.5; Crankbait 2 m 0.6-3; Deep Crankbait 5 m 0.8-3.5; Jig 3 m 0.1-1 worked slowly near the bottom; Popper on the surface 0.2-1.5; Soft Plastic Worm 1 m 0.1-1.2. Kept between bites, lost only with the line; predators like them best (see fishing/bait)
- Hook sizes run #12 (smallest) through #1, then 1/0 up to 8/0; ideal hook grows with the fish (#12 for a 5 cm fish, 4/0 for a 2 m one). Bait size counts: big bait finds bigger fish
- Lure weights: Soft Plastic Worm 6 g, Spinner 8 g, Jig and Popper 10 g, Spoon 12 g, Crankbait 14 g, Deep Crankbait 20 g

## fishing/tackle_bench | The Tackle Bench
- Put a rod in, fit a part per slot; **3D preview** of the rod; shows what it is **rated** for and warns about the **weak link**; buttons set the **drag** and how deep the bait hangs under the float (0.5 to 8 m, 1.5 m at first; bites are rolled where the bait hangs, just above the bottom at most, so fish that keep above or below a height see its real depth). Parts also go on by right-clicking the rod with them in the inventory
- Tackle Bench (shows recipe create_galore_fishing:tackle_bench): iron sheets, string, planks, crafting table. Keeps nothing: the rod comes back with its parts on close
- Slots: rod or pole, reel, line, leader (optional), hook, float/sinker/lure, bait. A pole has no reel slot. Taking a part out of its slot takes it off the rod. Panel shows the rating, "fish to about" a weight, longest cast, metres of line
- Rating = weakest of blank's load, line's (or leader's) test, reel's top drag. "Fish to about" = heaviest average fighter that rating lands with a sensible drag. A new rod's drag starts at a third of the reel's range
- Weak-link warnings: drag over the line's test (hard pull snaps it); drag over what the rod holds (overloaded); a pole's line stronger than the pole (big fish breaks the pole); rig too light/heavy for the rod (casts fall short); worn rod or reel, frayed line, nearly worn-out leader, hook or terminal (above 90%)
- Without the bench: in the inventory pick up a part or bait and right-click the rod; it goes in its slot. Empty cursor takes the bait back off. A rod missing a part says so on its tooltip: "Needs a reel", "Needs line", "Needs a hook, or a lure"
- Tip: keep the drag below the line's test (the bench warns when it isn't). A careful angler lands most river and lake fish on Iron or Steel gear; tuna, arapaima, sharks need the Heavy Boat Rod with heavy line and a Big-Game reel. Rods and reels aren't repaired at the bench

## fishing/bait | Bait
- Each fish has a **diet**; the right bait or lure brings far more bites. Bait goes in the rod's bait slot (right-click the rod with the bait stack on the cursor, or the Tackle Bench (see fishing/tackle_bench)); one used per fish landed; a lure is never used up. A missed bite takes the bait 3 times in 10. A bare hook catches something now and then, very slowly
- Who eats what: panfish: worms, maggots, insects. River predators (pike, bass, walleye): live fish, soft plastics, spinners, spoons, crankbaits. Bottom feeders (carp, catfish): dough, boilies, worms. Sea predators (tuna, sharks, mackerel): live fish, squid, cut bait, spoons. Sea bottom fish: shrimp and crab, cut bait, squid. Reef fish: shrimp and crab, squid, cut bait. Lava and void fish: their own baits. A fish's journal page shows where it lives
- Bait items: Worms, Bait, Lucky and Magnetic Bait = worm; Maggots; Leeches; Caterpillars, Butterflies, spider eyes = insect; Dough, bread = dough; Boilies; raw fillets, fish meat, Fish Scraps, rotten flesh, cod, salmon, loose baitfish = cut bait; shrimp, crayfish, crab meat = crustacean; Tentacles, Cut Tentacles = squid. Bait size: small baits find small fish, big ones bigger
- Bait from Create: Washing (see glossary/washing) dirt -> mud + worms (1 in 5); coarse dirt 12%, rooted dirt 30%. Washing mud -> clay (half the time) + leeches (15%). Mixing (see glossary/mixing) rotten flesh + dirt -> 4 Maggots. Heated (see glossary/heated) mixing of Fish Meal, wheat flour, an egg, 250 mB water -> 4 Boilies. Hoeing grass or dirt: a worm 1 time in 20. Aquaculture Worm Farm: worms from compostable scraps. See (see create_basics/fans) (see create_basics/processing)
- Bait (shows recipe tide:bait): crop, mushroom or small flower + rotten flesh + seeds + bone meal -> 4 Bait; counts as worms
- Lucky Bait: 8 Bait around a rabbit's foot -> 16; +2 luck. Magnetic Bait: lapis, 2 Bait, redstone, iron nuggets, prismarine -> 4; twice the treasure and crates. Incandescent Bait (from Incandescent Larva) for lava fish; Abyss Bait (from Pale Clubfish) for void fish
- Diet strength, best first: panfish worms, then maggots, insects, dough. River predators: live fish, soft plastic, spinner and spoon, crankbait, jig, then topwater, leech, worm, cut bait. Bottom feeders: dough and boilies, then worms, maggots. Lava and void fish take other bait a quarter as well as their own
- Related: (see fishing/live_bait) (see fishing/cleaning) (see fishing/where_fish_live)

## fishing/live_bait | Live Bait and Chum
- **Live-Bait Bucket** keeps baitfish and leeches alive; bait loaded from it is **live** (predators take it far more readily). **Chum** in water: faster bites nearby for three minutes. Baitfish breed in an Aquarium Tank
- Live-Bait Bucket (shows recipe create_galore_fishing:crafting/live_bait_bucket): water bucket + iron bars; holds up to 64 baitfish and leeches, same clicks as a bundle
- Loading: right-click a rod in the inventory with the bucket; loads one kind of bait, marked live. A loose baitfish on the rod is only cut bait. Live fish please river and sea predators half again as much as their favourite bait otherwise would. Taking live bait off the rod makes it ordinary
- Baitfish: minnows, anchovies, guppies, danios, tetras, goldfish, bluegill, herring; catch on small hooks with worms or maggots. Aquaculture leeches count as live bait. Geared or Fuel Reel Drive takes the bucket in its bait slot
- Chum [create_galore_fishing:chum]: mixing (see glossary/mixing) 2 Fish Scraps + 1 Fish Meal -> 2. Throw into water or fire from a dispenser; within 8 blocks bites up to 1.75 times faster for three minutes. Strongest at the centre and first two minutes, then thins. Several chum spots together: up to 2.5 times faster
- Tip: chum the water beside a row of reel drives (see fishing/rod_machines)
- Related: (see fishing/aquarium) (see fishing/bait) (see fishing/cleaning)

## fishing/aquarium | Aquarium Tanks
- Tank blocks filling a **box** up to 8 on a side join into one tank (open top, sand floor; long shallow tanks work). Aquarium Tank (shows recipe create_galore_fishing:crafting/aquarium_tank): four iron sheets + four glass -> 4
- Water by bucket or pipe. At least half full: fish go in from any mod's fish bucket, or a fish just caught and held up (counts as kept). Empty bucket scoops a fish back into its bucket. Fish swim inside at real size
- A fish needs room by its size and a tank longer than itself. Empty hand shows size, water, fish, food. Breaking a wall spills the fish; breaking off a whole end keeps them in what is left
- Breeding: two or more of one baitfish species, fed Fish Meal (hand, funnel, hopper), raise a young one every two minutes. Live-Bait Bucket scoops spares (tank keeps two of each kind); sneak with it to let its fish in
- Automatic live bait: hoppers and funnels feed meal in and take spare baitfish out; a funnel into a Geared Reel Drive's bait slot keeps it baited from the tank (see create_basics/item_logistics)

## fishing/where_fish_live | Where Fish Live
- **Ocean and beach** water is salt; rivers, lakes, everything else fresh. **Biome** and its temperature decide most fish. **Depth**: below y40 cave fish. **Time, weather, moon, season** decide rare ones
- Journal page lists what a fish wants once caught; before that a **Fishy Note** shows the page. Most fish have a favourite **temperature** (wrong climate: still bite, rarely); Fiery and Permafrost hooks nudge it. A lake counts as the land biome around it
- **Fresh water** (rivers, lakes, swamps): trout, pike, perch, crappie, bass, walleye, carp, catfish, more; swamps, jungles, deserts, cherry groves, frozen lakes each have own. **Salt water**: cod, snapper, flounder, anchovy, tuna, sharks, rays, sailfish, Hybrid Aquatic reef fish
- **Underground** (water below y40): cave eels, glowfish, groupers, ore fish **Iron Tetra**, **Gilded Minnow**, **Lapis Lanternfish**, **Crystal Shrimp**; deeper = stranger; deep dark, dripstone caves, trial chambers have own. Alex's Caves biomes have own fish
- **Rare fish**: five planet fish each bite in the ocean under one moon phase; **Shooting Starfish**: deep ocean, full moon, night; **Coelacanth**: deep ocean, night in rain; **Bull Shark**: rivers evening to morning
- **Legendary**: Shooting Starfish, Coelacanth, Devils Hole Pupfish (narrow cave band y20 to y30), Midas Fish (underground, needs 5 luck), Alpha Fish (Overworld void), Dragon Fish (End void). Catch one for **One in a Million**
- **Seasons** count (brook trout skip autumn, sturgeon come autumn and winter); journal shows which. Gadgets for time, moon, depth, temperature, weather: (see fishing/gadgets)
- What you offer decides which fish living there bite: bait or lure each species likes; hook and bait size against its mouth; line visibility for shy fish; depth the rig sits at (a sinker reaches bottom fish); a lure's speed
- Fished-out spots: every fish landed tires its chunk (see glossary/chunk); after four quick catches bites come half as often, a fished-out spot drops to a twentieth (a bite never takes over 4 minutes); tiredness halves every 10 minutes, so moving on or trolling pays. Released fish don't tire the spot. Chum (see fishing/live_bait) brings bites back up

## fishing/lava_and_void | Lava and Void Fishing
- **Lava**: Netherite or Blazing rod, or a **Netherite** or **Lavaproof Hook** on any rod. **Void**: fit a **Hook of the Depths**, cast into the open void. Bites slower in both
- Lava fishing in any lava pool; catches can't burn. Overworld lava: ember koi, magma mackerel, obsidian pike, more; Nether adds witherfin, soulscale, Blazing Swordfish. **Blazing Fishing Rod** catches Nether lava fish in any lava
- Watch out: casting into lava without a lava-proof rod or hook burns the rig (hook, float or lure, bait gone). **Incandescent Bait** is lava fish's favourite. Lava bites take 1.6 times as long
- Lava junk: obsidian fragments (4 make obsidian), fish bones. Lava crates: **Obsidian Crates**, break with a diamond pickaxe or better. Watch out: **Witherfin** gives Wither eaten; most other lava fish give Fire Resistance
- **Void fishing**: rig below the world's void line: in **The End** anything below y50 (cast off an island edge); Overworld y-70 and below, under the bedrock floor. End void fish: Enderfin (ender pearl), Endergazer (eye of ender), Elytrout (phantom membrane), legendary **Dragon Fish**
- Overworld void has own fish (Alpha Fish, Darkness Eater among them); none for the Nether void. **Abyss Bait** is void fish's favourite; void bites take 1.8 times as long. No fish sends you home when eaten (see transport/getting_around)

## fishing/gadgets | Fishing Gadgets
- Gadgets show conditions fish care about. Hold one, wear in a Gadgets slot of the Accessories (see glossary/curios) screen, or place block versions nearby; reading shows in the top corner. Right-click to read once
- Pocket Watch [tide:pocket_watch]: exact time; a clock and a chain
- Lunar Calendar [tide:lunar_calendar]: tonight's moon phase; paper, ink sac, spyglass; can be placed
- Climate Gauge [tide:climate_gauge]: biome temperature; glass, iron nugget, redstone; weather and seasons don't change its reading
- Depth Meter [tide:depth_meter]: height above/below sea level; iron ingots, stick, redstone
- Weather Radio [tide:weather_radio]: when the weather changes next; lightning rod, redstone, copper; placed as a block
- Fish Finder [tide:fish_finder]: all five readings at once; made from all five gadgets
- Enchanted Pocket Watch [tide:enchanted_pocket_watch]: Pocket Watch + **Coelacanth**; right-click a mob to freeze it (not bosses), again to free; 10-second cooldown

## fishing/journal | Fishing Journal and Trophies
- **Fishing Journal** fills a page per new fish; keeps your **longest, shortest, heaviest** of each. **Fishy Notes** show a page before catching. Show catches on a **Trophy Mount**
- Lost journal: craft from any fish and a book; works on a lectern. Uncaught fish are black outlines. Page: where/when it bites (biome, depth, time, weather, moon, season, temperature), then count caught, when, records
- Catches by your rod machines (see fishing/rod_machines) count as yours. Fill half the journal for **Aquatic Archivist**, all for **A Reel Completionist**. Own every rod blank and special rod for **Hooked on Variety**. Best weight per species also shows on the Keep or Release screen
- Fishy Note [tide:fishy_note]: every crate holds one, usually for an unfound fish; or craft any fish with paper; right-click to see that fish's page
- Trophy Mount [tide:fish_display]: sticks around a log; hang on a wall, right-click with a fish to mount it at real size with weight, length, catcher; empty hand takes it back
- Fish Satchel [tide:fish_satchel]: 4 leather around a fish; right-click to open; while open picked-up fish go straight in; holds 64 fish
- Aquaculture's fish mounts still work for its fish. Fish under 3 kg stack by species and size class; bigger fish and trophies keep their numbers, don't stack. Swimming fish can be caught in a water bucket as usual (lava fish need a lava bucket)

## fishing/crates | Crates and Treasure
- Now and then a bite is a **crate**: flies out of the water, lands as a block, open like a barrel. Crates and treasure need **open water**. **Neptune's Bounty** is how you find Neptunium. Luck and magnetic tackle bring more of both
- About 1 bite in 75 is a crate, more with luck; a Magnetic Hook or Magnetic Bait doubles it
- **Wooden Crates** vary by where you fish: fresh or salt water at surface, underground (y0 to y40), deep (below y0, with diamonds); each 6 to 10 stacks and a Fishy Note. **Obsidian Crates** from lava; **Purpur Crates** from The End (shulker shells and End loot; the Elytra they used to hold are shulker shells here). **Hybrid Aquatic crates** and crab pots too; break with an **axe** for loot
- **Treasure**: about 1 bite in 50 in open water, more with luck, twice with magnetic tackle: name tags, saddles, enchanted books and gear, Neptunium nuggets, leeches, Critters and Companions' clams in rivers. Ocean monuments, trial chambers, desert wells have own catches
- Neptune's Bounty [aquaculture:neptunes_bounty]: rare open-water chest; under the sea junk is Neptunium: 1 to 4 ingots, or a Neptunium tool, armor piece, bow or fillet knife
- Neptunium [aquaculture:neptunium_ingot]: Aquaculture's sea metal for tools, armor, bow; nuggets also come as treasure; 9 make an ingot

## fishing/fish_uses | What Fish Are For
- Cook fish under **3 kg** whole (campfire, smoker, furnace, a fan's smoking air); 3 kg or more won't cook, its tooltip says to clean it at a Cleaning Table (see fishing/cleaning) or a Fish Cleaner (see fishing/fish_cleaner). Many cook into **Small**, plain or **Large Cooked Fish** by kind; others into their own cooked fish. The 3 kg limit holds for every mod's fish and every way of cooking
- Farmer's Delight cutting board + knife: fish that cook into Cooked Fish -> 2 **raw fillets** + bone meal (same fillets as Aquaculture fish). Meals: (see fishing/sea_food)
- **Craft a fish into**: Iron Tetra: 2 raw iron; Gilded Minnow: 2 raw gold; Lapis Lanternfish: 2 lapis; Crystal Shrimp: 4 amethyst; Glowfish: 2 glow ink sacs; Enderfin: ender pearl; Endergazer: eye of ender; Elytrout: phantom membrane; Luminescent Jellyfish or Sun Emblem: 8 **Jelly Torches** (burn underwater); Incandescent Larva and Pale Clubfish: 4 bait
- **Gear**: Sailfish, Swordfish, Blazing Swordfish are swords (last sets things on fire). Shooting Starfish + bow: **Starlight Bow**. Dragon Fish + netherite boots: **Dragonfin Boots**, a jump in mid-air
- **Eaten**: lava fish give Fire Resistance; Uranias Pisces 10 minutes of Luck; Ender Glider and Mantyvern Slow Falling; Chorus Cod teleports like chorus fruit. Watch out: Witherfin gives Wither; several Hybrid Aquatic fish poison you raw
- **Chasm Eel** can't eat through bedrock. Cats eat cookable fish. Inferno Guppy burns in a furnace for 160 seconds

## fishing/cleaning | Cleaning Table and Fish Products
- Lay a fish on the **Cleaning Table** (right-click with the fish), cut with any **knife**; heavier fish take more strokes and give more. A Deployer (see create_basics/deployer) with a knife works it; hoppers and funnels empty it (see create_basics/item_logistics). Scraps become Fish Meal and Fish Oil
- Cleaning Table (shows recipe create_galore_fishing:crafting/cleaning_table): three smooth stone slabs over logs and andesite alloy. The fish lies on it as itself; look at it to see strokes done. Strokes: two for a minnow, five at 3 kg, sixteen at 50 kg. By hand the parts go to you; with a Deployer they wait in the table's output, and the next fish goes on once it is empty. For a fish line the Fish Cleaner (see fishing/fish_cleaner) cleans only big fish and passes the rest
- Yields by weight: raw **fillets** about 3 from 1 kg, 8 from 10 kg; from 3 kg most of those are Hybrid Aquatic **fish steaks**; **bones** 1 plus 1 per 4 kg (up to 8); **Fish Scraps** 1 plus more with weight (up to 10); **roe** 4 times in 10 from sturgeon, salmon, herring, carp; **shark skin** from sharks. Oily fish (salmon, herring, mackerel, tuna, anchovy, trout) give half again as many scraps. A fish without a weight on it counts as a usual one of its size class
- Fish Scraps [create_galore_fishing:fish_scraps]: cut bait; millstone or crushing wheels -> 1 Fish Meal, sometimes 2. Aquaculture fish bones mill into Fish Meal + bone meal
- Fish Meal [create_galore_fishing:fish_meal]: for Boilies, Chum, feeding an Aquarium Tank (see create_basics/crushing)
- Fish Oil [create_galore_fishing:fish_oil_bucket]: compacting (see glossary/compacting) 4 Fish Scraps, or one small oily fish -> 100 mB; heats a Blaze Burner from a pipe; runs the Fuel Reel Drive
- Fish Roe [create_galore_fishing:fish_roe]: food, restores 2 hunger points; from roe fish at the table
- Shark Skin [create_galore_fishing:shark_skin]: smoke it or campfire-cook into leather
- Related: (see fishing/fish_cleaner) (see industry/liquid_fuel) (see fishing/bait) (see fishing/live_bait) (see fishing/sea_food)

## fishing/fish_cleaner | Fish Cleaner
- A saw for a fish line: items go in the back, sides or top, out the front. A fish of **3 kg or more** (the ones furnace, smoker, campfire, fan won't cook; tooltip says so) is cut up like on the Cleaning Table (see fishing/cleaning); everything else (smaller fish, trophies under 3 kg, stacked common catches, non-fish) passes through **untouched**, catch data kept, even with the shaft stopped. The saw needs rotation (see create_basics/rotation)
- Fish Cleaner (shows recipe create_galore_fishing:crafting/fish_cleaner): a knife over an andesite casing between two shafts, over a Cleaning Table
- Same strokes as the table (five at 3 kg, sixteen at 50 kg) and same parts (fillets, steaks, roe, bones, scraps, shark skin). Two strokes a second at 32 RPM (see glossary/rpm), faster shafts cut faster. 4 SU per RPM, like Create's saw (see create_basics/stress)
- Faces away from you when placed; shaft through it side to side. In: belt ending at back or sides, chute or hopper above, funnel. Out: belt, depot or chute in front, chest in front, or the ground; funnels and hoppers can pull from it too (see create_basics/item_logistics)
- One big fish at a time; the next starts only once the last one's parts have left, so a blocked output stops the line, nothing lost. Small fish behind a big one wait. Empty hand takes out what is inside, uncut; goggles show the fish and strokes done
- Related: (see fishing/cleaning) (see fishing/fish_uses) (see fishing/rod_machines) (see fishing/sea_food)

## fishing/sea_food | Sea Food
- Two Farmer's Delight (see food/farmers_delight) add-ons use its pot, cutting board, knives; meals follow the pack's normal food rules
- **Ocean's Delight** cooks vanilla ocean mobs: squid, cod, kelp, sea pickles, pufferfish, guardians, Elder Guardians
- **Aquaculture Delight** cooks Aquaculture (see fishing/aquaculture) fish: perch, halibut, pollock, tuna, catfish, bass, turtle meat; caught on any rod
- Guardian Soup [oceansdelight:guardian_soup]: one-pot soup from Guardian parts
- Stuffed Squid [oceansdelight:stuffed_squid]: squid meals; Cut Tentacles and Squid Rings come from cutting squid
- Fish and Chips [aquaculturedelight:fish_and_chips]: Aquaculture Delight meals use fillets and fish; also includes a Neptunium Knife
- Neptunium Knife [aquaculturedelight:neptunium_knife]: Farmer's Delight style knife made with Neptunium, for cutting fish
- Fillets from the cutting board or the Cleaning Table (see fishing/cleaning) go into these meals, so every catch can (see fishing/fish_uses)
- Same fish feed automated kitchens (see food/kitchen_automation). Hybrid Aquatic clams cook into Cooked Clam; Naturalist's clams give Clam Meat

## fishing/rod_machines | Rod Holder and Reel Drives
- Rod Holder: alarm and redstone pulse on a bite. Reel drives fight fish with rotation, or an engine; winding costs **stress**, so pick a drag the network can turn. Catches go into a chest, funnel or belt
- Right-click any machine with a rod to set it in (a cast line stays out); empty hand takes it back with its line, bite or fight. Catches count for whoever set the rod in (journal, personal bests). The machine keeps the rod's own drag, never changes it
- Rod Holder (shows recipe create_galore_fishing:crafting/rod_holder): iron sheet over a log over three smooth stone slabs -> 2. Only holds; take the rod out to strike and fight by hand. On a bite: bite alarm rattles, redstone pulse, comparator at full (otherwise comparator reads line tension); the bite lasts five seconds there (see create_basics/redstone_and_control)
- Basic Reel Drive (shows recipe create_galore_fishing:crafting/reel_drive): Rod Holder over an andesite casing between two shafts; a shaft runs through side to side. Only winds: hook the fish by hand, set the rod in, it fights at a speed from the shaft (reel's own speed at 32 RPM (see glossary/rpm)). A row along a jetty runs off one shaft, a water wheel's included. Watch out: keeps winding while the drag slips, reel wears fast
- Geared Reel Drive (shows recipe create_galore_fishing:crafting/geared_reel_drive): Rod Holder, brass casing, two cogwheels, electron tube; shafts through like Basic. Casts, strikes bites itself, automatic gearbox (shifts down when the fish pulls hard, up when it tires), never winds while the drag slips. Value panels (see glossary/value_panel) on the back: mode, cast distance (3 to 48 blocks), top gear
- Modes: **Cast and wait** (default; casts once and holds; float, bottom, trolling rigs); **Cast and retrieve** (winds a lure back slowly, casts again). Bait slot (hand, hopper, funnel; a Live-Bait Bucket works) re-baits before each cast
- Fuel Reel Drive (shows recipe create_galore_fishing:crafting/fuel_reel_drive): Geared Reel Drive, two copper casings, fluid tank, furnace; geared behaviour, own engine, no shafts; for boats. Burns furnace fuel from a slot, or lava, Fish Oil, other liquid fuels (see industry/liquid_fuel) from a 4-bucket tank; a tenth as much while waiting, full rate under full load, only with a rod in. Engine winds against 12 kg in normal gear, four times that in its lowest
- Drag against rotation: winding against a fish costs stress (see glossary/su): 1 SU per RPM idle, plus 4 SU per RPM per kg of pull in normal gear. A fish leaning on a drag heavier than the network can turn overstresses (see glossary/overstressed) it until the fish eases off
- On one water wheel: 6 kg drag never stalls a Basic drive, 10 kg does; about 7.5 kg is the limit in normal gear. Geared drive's lowest gear copes with about four times that, more slowly. Fuel drive's engine stalls the same way above its rating (see create_basics/stress)
- Catches go into a chest, hopper, funnel or belt below or beside, never another rod machine; with nowhere to go the fish hangs from the rod tip for 30 seconds (empty hand takes it). A fish a machine puts on a belt is alive two minutes; off the belt into water it swims away
- Experience: a machine earns half the hand amount per catch, no release bonus; banked, paid out whenever a rod goes in or out. Machines get no bonus item from the Midas rod or Rod of the Hero and catch no treasure in the Nether; Overworld treasure and crates as by hand. Goggles (see glossary/goggles) show rod, bite or fish on and its tension, line out, drag, bait and bait in store, reel wear, gear, what it can still wind against, fuel, banked experience
- In depth: a Geared drive on one water wheel (8 RPM) can take several minutes to wind in a big tuna in lowest gear; a Basic drive grinding a strong fish through a slipping drag wears its reel fast and can wear it through in one fight, which loses the fish. Machines fish only while their chunk is loaded. Integrated Farming's Fishing Nets (see food/integrated_farming) catch the same fish, small ones most
- Related: (see fishing/trolling) (see create_basics/power_sources) (see fishing/live_bait) (see create_basics/item_logistics)

## fishing/trolling | Trolling from Ships
- Rod Holders and reel drives work on physics **ships** (a sub-level (see glossary/sub_level), e.g. a sailing ship or a surfaced submarine). The rig is **towed** behind at its line length (it can't get further from the tip than the line it was cast with). A small hooked fish is towed along; a big one takes line through the drag. See (see flight/ship_building) (see ocean/sailing)
- A towed crankbait dives and works between its best speeds: 0.6 to 3 m/s; Deep Crankbait 0.8 to 3.5 m/s, down to 5 m. Too slow or fast: fish mostly ignore it. Fish that bite follow the water passed, so trolling spreads catches instead of fishing out one spot
- Fuel Reel Drive needs no shafts, suits boats; a Geared drive works off the ship's own rotation. Tip: for tuna and sharks: Heavy Boat Rod, Big-Game Trolling Reel, Heavy Mono line, Steel Leader, Big-Game hook or Deep Crankbait
- Watch out: machines on a ship fish only while the ship is loaded; stay aboard. Put a chest or belt beside the machines, or catches hang at the rod tip. Set the drag before setting the rod in (the machine keeps the rod's own and never winds while line slips); keep it below the line's test

## fishing/aquaculture | Aquaculture Fish and Old Rods
- Aquaculture's **fish** bite on any rod, each in own biome. Its **rods, hooks, lines, bobbers, Tackle Box** can't be crafted; owned rods still work the classic way. Fillet knives, Worm Farm, Neptunium, its food stay
- Retired gear: one tackle system, not two; hidden from JEI; owned copies keep working. Aquaculture worms and leeches are bait on every rod
- **Where Aquaculture fish bite**: **Jungle rivers**: arapaima, piranha, tambaqui, Arrau turtle; **Hot rivers** (desert, badlands, savanna): bayad, boulti, capitaine, synodontis; **Swamps**: perch, box turtle; **Rivers**: gar, minnow, pink salmon; **Fresh water** by season and temperature: bluegill, smallmouth bass, rainbow and brown trout, muskellunge, carp; catfish at night; **Ocean and beach**: Atlantic cod, halibut, herring, blackfish, Pacific halibut, pollock, red grouper, tuna, jellyfish; **Mushroom fields**: red and brown shrooma; **Twilight Forest**: starshell turtle
- Arapaima, bluegill, carp, catfish, rainbow trout, smallmouth bass, tuna are Aquaculture's fish; mackerel, coelacanth, anglerfish, mahi-mahi Hybrid Aquatic's: one item each (journal lists once, recipes take one item); weights match the catch
- **Old Rods**: owned Aquaculture rod fishes Aquaculture's way (cast, wait for splash, reel in; no fight, no drag; Aquaculture's tables). Its hook, line, bobber, bait go in via the rod's menu or an old Tackle Box, fit no other rod
- **Hybrid Aquatic hooks** in the **off hand** while casting work with old Aquaculture rods; on every other rod they go in the hook slot (see fishing/tackle)
- Fillet Knife [aquaculture:iron_fillet_knife]: Iron, Gold, Diamond, Neptunium Fillet Knives still craft; craft one with a fish for Raw Fillets by its weight; a Farmer's Delight knife on the cutting board, or the Cleaning Table, works too
- Worm Farm [aquaculture:worm_farm]: still makes **Worms** from compostable scraps (see fishing/bait)
- Neptunium still from Neptune's Bounty (see fishing/crates), reeled in on any rod in open water; Neptunium rod out of that loot. Integrated Farming's Fishing Nets (see food/integrated_farming) are not rods; they fish on moving contraptions
