# Create Galore Fishing: handover

For whoever folds `emre/fishing-mod` into a release of the pack. This file is the handover: the state of the branch,
what it changes in the pack, what the pack's shared docs still say about the old setup (none of them were edited on
this branch), what players get, the last full test, the balance as the fight sim sees it, and what is open. The mod's
own docs are [README.md](README.md) (maintainers: build, tests, code layout) and [DESIGN.md](DESIGN.md) (the plan,
with "as built" notes where the build differs).

## The branch

- `emre/fishing-mod`: 14 commits on top of `4927d7a` (20 Oct 2026): milestones 1 to 10 of DESIGN.md, then this
  handover. It was kept apart from everyone else's work on purpose: nothing was pulled, merged or rebased from
  upstream, and there is no pull request. Fold it in with a merge.
- `pack/index.toml` and `pack/pack.toml` will conflict with any other change to the pack. Take either side and run
  `packwiz refresh` in `pack/` rather than merging hashes by hand.
- The mod is `tools/fishing-mod/`, built into `pack/mods/create-galore-fishing-0.1.0.jar` (the version is in its
  `neoforge.mods.toml`). It is required on both server and client. `tools/fishing-mod/build.sh <server>` rebuilds it
  (README.md, "Build").

## What the branch changes outside `tools/fishing-mod/`

| Path | Change |
|---|---|
| `pack/mods/create-galore-fishing-0.1.0.jar` | new |
| `pack/mods/tide.pw.toml`, `pack/mods/tide-extra-compatibility.pw.toml`, `pack/config/tide/` | removed |
| `pack/index.toml`, `pack/pack.toml` | refreshed by packwiz (hashes only) |
| `pack/config/paxi/datapacks/create-galore-tweaks/data/tide/fishing/fish/**` | the 12 species redirects removed |
| `.../create-galore-tweaks/data/tide/neoforge/biome_modifier/*` | the 10 `neoforge:none` spawn overrides removed |
| `.../create-galore-tweaks/data/tide/recipe/cooked_fish_slice_*` | the 3 disabled fish-slice cooking recipes removed |
| `.../create-galore-tweaks/data/aquaculture/bait/worm.json` | removed (Tide's bait format) |
| `.../create-galore-tweaks/data/c/tags/item/hidden_from_recipe_viewers.json` | its 38 `tide:` entries removed |
| `tools/bridge-mod/` | its three Tide mixins deleted from the source and mixin list (jar not rebuilt) |
| `tools/build_no_elytra.py` | reads the End crate table from our jar instead of Tide's |

Why each of these, under "Tide 2 is gone" below.

## What the pack's shared docs need (not touched here)

- **Version and CHANGELOG.md**: a new pack version (`pack/pack.toml` says 0.3.0). Players should back up their
  worlds and update mod and pack together: Tide's content converts on load and can't be converted back. The
  player-facing list is "What players get" below.
- **MODS.md**: drop the Tide 2 and Tide Extra Compatibility rows (lines ~226-227) and add Create Galore Fishing next
  to the Bridge's line at the top (in-house, both sides, `pack/mods/create-galore-fishing-<version>.jar`). Credit:
  fish, journal and extras ported from Tide 2 by Lightning-64 (MPL-2.0); other mods' fish data from Tide Extra
  Compatibility by Silent Autopsy (MIT). The Bridge's line (line 7) still lists its Tide fixes (bait that stays used
  up, Hybrid Aquatic hooks and the Ominous / CreeperMagnet summons on Tide rods): those are now this mod's own.
- **README.md**: the "Fishing runs on Tide 2" notes (lines ~200-206) and the table row "Aquaculture's rods and tackle
  → Tide 2" (line ~282) describe Tide's hold-to-cast and timing game; the mod count changes by one (two out, one in).
- **The Bridge's `neoforge.mods.toml` description** mentions "Tide bait that stays used up after a save" and "Hybrid
  Aquatic hooks … that work on Tide rods"; both are now this mod's own.
- **Bridge jar** (optional): rebuilding it drops the three Tide mixins from its jar too. Until then Mixin skips them
  with a warning at boot; nothing else is affected.
- **Field Guide** (`create-galore-tweaks/data/create_galore/modonomicon/books/field_guide/entries/fishing/`): ten
  entries name `tide:` items (journal, crates, angling_table, bait, lava_and_void, overview, fish_uses,
  where_fish_live, gadgets, rods). They all load (the old rod, line and Angling Table ids resolve through the
  aliases), but the text describes Tide's minigame, the Angling Table and Tide's rods. Rewriting the fishing section
  is the integrators' job (no Field Guide text was written on this branch); the facts are below.

## What players get

All new ids are `create_galore_fishing:` unless they say `tide:`.

### Tackle and the Tackle Bench

- Rods are assembled from parts and drawn as blocky 3D models that bend toward the line under load (the Steel rod
  is telescopic and slides out on a cast). Blanks: Bamboo Pole (no reel, a short fixed line, for beginners), Wooden,
  Iron, Steel Telescopic, Heavy Boat Rod, Netherite (fishes lava). Reels: Copper Spinning, Iron Spinning, Brass
  Precision, Steel Baitcaster, Big-Game Trolling. Lines: String, Mono, Clear, Braided, Heavy Mono; leaders: Clear and
  Steel (toothy fish bite through anything else). Hooks: Small, Medium, Large, Big-Game, Golden (luck), Netherite
  (lava), plus Tide's and Hybrid Aquatic's special hooks with their effects. Terminals: Float, Lead Sinker, and lures
  (Spoon, Spinner, Crankbait, Deep Crankbait, Jig, Popper, Soft Plastic Worm); Tide's bobbers work as floats.
- Every part has ratings (a rod's length, load and action; a reel's maximum drag, speed and smoothness; a line's
  test, stretch and visibility; a hook's size). Rods, reels and lines wear; a line that snaps, is bitten through or
  runs out loses what was out plus a few metres, and the hook, rig, leader and bait with it.
- The **Tackle Bench** (iron sheet, string, planks, crafting table) has a slot per part, a live 3D preview, the
  rod's stats and what fish it is rated for, warnings for the weak link (a line weaker than the drag, a lure too
  heavy for the blank), and buttons for the drag and the float's depth. Parts and bait also go on by right-clicking
  the rod with them in the inventory; an empty cursor takes the bait back off.
- Recipes are crafting recipes from Create parts (cogwheels, shafts, precision mechanisms, andesite alloy, brass and
  copper sheets) and TFMG's steel, plastic and rubber; JEI shows them all.
- Vanilla's Fishing Rod and Alloyed's Steel Fishing Rod become our Wooden and Steel rods wherever one appears.

### Casting, bites and the fight

- Hold use to charge a cast and let go (a full charge takes a second); the distance comes from the rod, the charge
  and how well the rig's weight suits the blank. Hold use to reel.
- Fish bite by Tide's conditions (water, biome, depth, time, weather, moon, season) and by what's offered: the bait
  or lure each species likes, hook size against its mouth, line visibility, depth, lure speed, chum nearby. Catches
  tire a spot for a while, so moving pays. Lure and Luck of the Sea work as on vanilla rods.
- A bite shows as a dipping float or a tap on the rod tip; strike by using the rod within about a second.
- The fight: the drag gives line when a fish pulls harder than it (change the drag any time with the scroll wheel
  while a line is out or while sneaking, or with the Drag + / Drag - keys, `]` and `[` by default). Reeling while
  the drag slips grinds the reel. Looking at the fish keeps the rod bent and soaks jumps and runs; pointing the rod
  straight at it soaks nothing. Lines snap above their test, rods can crack above their load, a slack line lets the
  hook come loose, and a fish can take all the line on the reel.
- A HUD shows tension, drag and line out and left; the fight has its own sounds (drag ratchet, crank, line strain,
  snap, splashes).

### Catch and release, and the fish themselves

- A landed fish is held up (by the mouth, or in both hands from 4 kg), seen by everyone, with a Keep / Release
  screen showing species, size class, weight, length, rarity and the player's best. Keep gives the fish and XP;
  Release gives a quarter more XP and doesn't tire the spot. The fish lives two minutes, after which it can only be
  kept. A fish kept while alive stays alive for the rest of that time (see "Rod machines": dropped into water, it
  swims off).
- XP per catch: a base by rarity (common 3, uncommon 5, rare 9, very rare 16, legendary 40), times the fish's heft
  (1 + 0.6 ln(1 + kg)) and its size against its species' usual (0.6-2.5), 1.5 for a trophy or 2.5 for a unique,
  times how hard the fight was (0.6-2.6).
- Fish have real weights from their length. Size classes are Young, Common, Large, Trophy and Unique. Fish under 3 kg
  stack per species and class; heavier fish, trophies and uniques are single items carrying weight, length, angler
  and day. Catches go in the Fishing Journal, and the catch screen shows the player's best for the species.
- Every mod's fish item is drawn as its own mob's model (in hand, dropped, in frames, on Trophy Mounts, and as the
  inventory icon), at its catch's size; the hooked fish is drawn at the end of the line.

### Tide 2 is gone (milestone 6)

- `pack/mods/tide.pw.toml`, `pack/mods/tide-extra-compatibility.pw.toml` and `pack/config/tide/` are removed.
  Create Galore Fishing declares both mods `incompatible`, so a client or server that still has them refuses to
  start with a clear message.
- Everything players had from Tide is in Create Galore Fishing under the same `tide:` ids: 90 fish with their
  mobs, buckets and spawn eggs, natural spawns, the Fishing Journal (progress kept), Fishy Notes, the Fish Display
  (now called Trophy Mount in English; it shows the catch's weight, length and angler), the Fish Satchel, the three
  crates, the gadgets and their HUD, jelly torches, the Starlight Bow, Dragonfin Boots, the fish foods, Tide's hooks,
  baits and bobbers (as floats), and the seven special rods with their perks. Tide's advancements keep their ids.
- What our tackle replaces, or what the pack had already handed to other mods, loads as its replacement
  (`compat/TideAliases.java`): Tide's stone/iron/golden/crystal/diamond/netherite rods become our Wooden/Iron/Steel/
  Netherite rods, its five lines our lines, the Angling Table the Tackle Bench, fish bones Aquaculture's, fish slices
  Aquaculture's fillets, and the twelve duplicate species (arapaima, bluegill, carp, catfish, rainbow trout,
  smallmouth bass, tuna, coelacanth, mackerel, mahi-mahi, anglerfish, voidseeker) their Aquaculture / Hybrid Aquatic
  fish (Voidseeker: Darkness Eater). A rod rigged at Tide's Angling Table comes out with those parts on it, plus a
  reel to match.
- The vanilla Fishing Rod and Alloyed's Steel Fishing Rod are now our Wooden and Steel rods, wherever one appears.
  Recipes that use a fishing rod (carrot on a stick and others) take ours.
- Tweaks datapack: the twelve `tide/fishing/fish` redirects, the ten `neoforge:none` spawn overrides, the three
  disabled fish-slice cooking recipes and `aquaculture/bait/worm.json` (Tide's bait format) are removed, and the 38
  `tide:` entries are out of `c:hidden_from_recipe_viewers` (through the aliases they would have hidden Aquaculture's
  and Hybrid Aquatic's own fish). The Enchanted Pocket Watch recipe, `fish_slice_cutting.json`, `legendary_fish` and
  the other mods' fish files stay.
- `tools/build_no_elytra.py` reads the End crate table from our jar instead of Tide's (the output is unchanged).
- Bridge: its three Tide mixins (`TideBaitCopyMixin`, `TideHybridHookNameMixin`, `TideSummoningHookMixin`) are
  deleted from its source and mixin list, since it no longer compiles against Tide. Its jar was not rebuilt; the jar
  in `pack/mods` still lists them, and with Tide absent Mixin skips them with a warning at boot. They aren't needed:
  our rods keep their own bait, and Hybrid Aquatic's hooks (summons included) work on them.

### Bait and fish processing (milestone 8)

- **Bait from Create**: washing dirt, coarse or rooted dirt with a fan gives mud and sometimes worms; washing mud
  gives clay and sometimes leeches. Maggots: mixing rotten flesh with dirt. Boilies: heated mixing of fish meal,
  wheat flour, an egg and water. Hoeing grass or dirt now and then turns up a worm.
- **Chum**: thrown into water (or fired from a dispenser), it brings bites up to 1.75 times faster within 8 blocks
  for three minutes. Made by mixing two fish scraps with fish meal.
- **Live-Bait Bucket** (water bucket + iron bars): holds up to 64 baitfish and leeches like a bundle. Right-clicking
  a rod in the inventory with it loads live bait, which predators take far more readily than cut bait. Baitfish are
  the `#create_galore_fishing:baitfish` tag (minnows, anchovies, guppies, danios, tetras, goldfish, bluegill,
  herring).
- **Cleaning Table** (three smooth stone slabs over logs and andesite alloy): lay a fish on it and cut it with a
  knife (`#c:tools/knife`); heavier fish take more strokes and give more: fillets, steaks from 3 kg (Hybrid
  Aquatic's), roe (sturgeon, salmon, herring, carp), bones, scraps, and shark skin from sharks. A Deployer works it
  and hoppers or funnels empty it.
- **Fish too big to cook whole**: a fish caught at 3 kg or more can't go in a furnace, smoker, campfire or Create's
  fan; its tooltip says to clean it first. Smaller fish cook as before.
- **Fish scraps, fish meal, fish oil, fish roe, shark skin**: scraps are cut bait and mill into fish meal (as do
  Aquaculture's fish bones); scraps or a small oily fish press into fish oil, which burns in a Blaze Burner like
  seed oil (Create: Liquid Fuel). Roe is food; shark skin smokes into leather.
- **Fishing Nets** (Create: Integrated Farming) catch this mod's fish again, as they did Tide's.
- Squid bait: Ocean's Delight tentacles count as squid bait.

### Rod machines and ships (milestone 9)

- **Rod Holder** (an iron sheet over a log over three smooth stone slabs, makes two): right-click with a rod to
  set it in (a cast line stays out), with an empty hand to take it out. A bite sounds an alarm, pulses redstone and
  lights a comparator (which otherwise reads the line's tension); the bite lasts five seconds so someone can take
  the rod and strike. Catches made from machines count for whoever set the rod in (journal, personal bests).
- **Basic Reel Drive** (Rod Holder over an andesite casing between two shafts): a shaft runs through it side to side,
  so a row of drives along a jetty runs off one shaft, a water wheel included. It only winds: hook the fish by hand,
  set the rod in, and it fights at a speed from the shaft. It keeps winding while the drag slips, so the reel wears
  fast.
- **Geared Reel Drive** (Rod Holder, brass casing, two cogwheels, an electron tube): winds through an automatic
  gearbox, never while the drag slips, strikes bites itself and casts. Its value boxes (back face) set the mode
  (Cast and wait, the default, or Cast and retrieve, which winds a lure back slowly and casts again), the cast
  distance and the top gear. A bait slot (by hand, hopper or funnel; a Live-Bait Bucket works) re-baits the rod
  before each cast.
- **Fuel Reel Drive** (Geared Reel Drive, two copper casings, a fluid tank, a furnace): the geared drive with its own
  engine and no shafts, for boats. It burns furnace fuel from a slot, or lava, fish oil and any other Create: Liquid
  Fuel fuel from a small tank, more the harder it works.
- **Drag against rotation**: winding against a fish costs stress, more the harder it pulls. The drag set on the
  rod (the most a fish can pull before line slips) has to suit the rotation: too much for it, and a fish leaning on
  the drag overstresses the whole network until it tires (on one water wheel about 7.5 kg is the limit in normal
  gear; the Geared drive gears down and copes with about four times that). The Fuel drive's engine stalls the same
  way above its rating.
- **Catches**: a drive or holder puts its catch into a chest, hopper, funnel or belt below or beside it; with nowhere
  to go, the fish hangs from the rod tip for 30 seconds (an empty hand takes it). Machines earn half the XP a
  player would, banked and paid out whenever a rod goes in or out (the goggles show it). No release bonus.
- **Alive fish**: a fish kept from the hold-up screen while still alive, or put on a belt by a machine, stays alive
  for the rest of its two minutes (its tooltip counts down). Dropped into water in that time (off a belt, out of a
  chute, by hand) it swims away, for no XP: sorting lines can throw back what isn't wanted.
- **Engineer's Goggles** on any rod machine: rod, bite or fish on and its tension, line out, drag, bait (and the
  bait in store), reel wear, gear, what it can still wind against or that it stalled, fuel, banked XP.
- **Trolling**: drives and holders work on Sable ships (Create: Aeronautics). The rig is towed behind at its line
  length, a lure dives and works when the ship's speed suits it, and the fish bite where the ship passes.

### Aquarium tanks (milestone 10)

- **Aquarium Tank** (four iron sheets and four glass, makes four): tank blocks that fill a box, up to 8 blocks on
  each side, join into one tank with an open top and a sand floor. Fill it with water by bucket or pipe; once it is
  at least half full, fish go in from any mod's fish bucket, and a fish just caught and held up goes in alive
  (counted as kept). An empty bucket scoops a fish back out into its bucket. Fish swim inside as their real models
  at their real size; a fish needs room by its size and a tank longer than itself.
- **Baitfish breeding**: two or more of one baitfish species, fed fish meal (by hand, funnel or hopper), raise a young
  one every two minutes. The Live-Bait Bucket scoops out the spare ones (the tank keeps two of each kind); sneaking
  with it lets its own fish into the tank. Hoppers and funnels can take spare baitfish out too, so live bait can
  be farmed automatically.
- Breaking a wall of a tank spills its fish; breaking off a whole end keeps them in what's left. An empty hand on
  a tank shows its size, water, fish and food.

## Compatibility

- Required: NeoForge 21.1, Create 6. Declared incompatible: Tide and Tide Extra Compatibility (the game says so at
  start rather than loading both).
- Used when present, with nothing breaking without them: Aquaculture (the pack's canonical fish, worms, leeches,
  fillets and bones; its weight-based systems get our weights), Hybrid Aquatic (fish, steaks, hooks and their
  summons), Create: Integrated Farming (Fishing Nets), Create: Liquid Fuel (fuels for the Fuel Reel Drive; without
  it, lava and fish oil), Sable (rod machines on ships, through its companion library), Naturalist, Alex's Caves,
  Aquamirae and Better End (their fish, with cooking limited by weight), Ocean's Delight (squid bait), Farmer's
  Delight knives (the Cleaning Table takes `#c:tools/knife`).
- The hook is a vanilla `FishingHook` underneath, so mods listening for fishing events and advancements
  (`ItemFishedEvent`, `minecraft:fishing_rod_hooked`) see this mod's catches.
- TFMG supplies steel, plastic and rubber for several tackle recipes; those recipes need it (it is in the pack).

## Last full test (milestone 11, 20 Oct 2026)

On a dedicated server installed from this branch's pack (its 185 server-side mods and the in-house jars, no Tide),
with a client joined to it:

- **Boot**: 641 ERROR lines, all from other mods (the pack's usual set); none from this mod.
- **Self-tests** (`/cgfish selftest all`): 12 suites, 362 checks, all pass: aliases 64, aquarium 21, boot 1,
  components 16, crates 27, fight 10, machines 36, processing 48, spawns 66, species 10, tackle 42, tide 21.
- **`/datacheck`**: 64 recipes in the pack can't be made; 3 of them are this mod's (Tide's grilled tuna, see "Known
  issues"). None of this mod's blocks is among those that drop nothing.
- **Client**: no missing model or texture from `create_galore_fishing` or `tide`; one Sodium notice (Known issues).
- **Migration from Tide**: last run at milestone 6 (README.md, "Test"): 0 of 496 Tide ids lost (394 items, Tide's
  blocks and block entities, its fish mobs, journal progress). The alias table and the rod conversion haven't
  changed since.
- **Played through on the test client**, milestone by milestone: casting, bites, the fight, catch and release, HUD
  and sounds (seen by Emre at the milestone 5 stop); Tide's fish, journal, Trophy Mount, satchel, crates and gadgets;
  3D fish items; fan-washed worms and leeches, chum, the Live-Bait Bucket, the Cleaning Table and the cooking limit;
  aquarium tanks and breeding; the Rod Holder, the three drives (casting, striking, landing into a chest, a water
  wheel overstressed by a 10 kg drag, the geared drive gearing down instead), alive fish, and trolling from an
  assembled Sable raft.
- **Not measured**: the performance checks in DESIGN.md's plan (a chest of fish stacks, 20 drives on a ship, a full
  5x5x5 aquarium).

## Balance, as the fight sim sees it

`/cgfish simulate all` runs the real fight code, 300 fights per species and rig, for a careful angler (drag at a
third of the line, reels only while the drag holds, rod well off the line) and a careless one (drag high, reels
non-stop, rod pointed at the fish). Share of bitten fish landed by the careful angler, from the 20 Oct run:

| Fish | Pole | Starter | Iron | Steel | Boat |
|---|---|---|---|---|---|
| Cod | 70% | 91% | 99% | 99% | 100% |
| Salmon | 17% | 66% | 91% | 96% | 100% |
| Carp | 70% | 94% | 98% | 99% | 100% |
| Pike | 65% | 85% | 88% | 87% | 100% |
| Catfish | 90% | 98% | 98% | 100% | 100% |
| Tuna | 1% | 8% | 9% | 10% | 87% |
| Arapaima | 0% | 7% | 6% | 16% | 98% |
| Great White Shark | 0% | 0% | 0% | 0% | 97% |

(Small fish such as bluegill and bass don't bite the boat rig's big hooks at all.) Starter to steel gear lands most
river and lake fish; tuna, arapaima and sharks need the boat rig. Every species but one (Tide's Dragon Fish) is landed
at least half the time on some rig.

Open question for Emre (answered in the balance pass, see [DECISIONS.md](DECISIONS.md), "Balance"): careless play
isn't punished much. On starter to steel gear the careless angler lands as often (85-93% against 83-87%) and faster
(20-42 s against 33-52 s); what it pays is snapped lines (9-11% on starter and iron, against 0-1%) and reel wear 20
to 40 times higher. If careless fishing should land less, raise the snap risk or the reel wear's cost.

## Known issues and open items

- **Tide's grilled tuna**: its three cooking recipes never fired (`#tide:cookable/grilled_tuna` is empty, and any
  tuna is over the 3 kg cook-whole limit). Removed in the balance pass (0.2.0); the item stays registered and is
  hidden from JEI.
- **Bridge jar**: still lists its three removed Tide mixins (a warning at boot until it is rebuilt).
- **Jade** names the mod of ported `tide:` blocks as "tide" (item tooltips and JEI say Create Galore Fishing).
- **Sodium** logs once that `client/VertexBounds` (which measures fish models for their icons) doesn't use its fast
  vertex path. Harmless.
- **Alive fish in chests**: a fish a machine puts on a belt and a funnel then moves into a chest keeps its alive
  marker until a player picks it up, so it doesn't stack with other fish of its kind there. Belt-to-water release is
  tested by code and by dropping fish into water; a full belt line wasn't built in game.
- **Weak rotation and big fish**: a geared drive on one water wheel (8 RPM) needs several minutes to wind in a big
  tuna in its lowest gear. The Basic drive grinding a strong fish through a slipping drag wears its reel about 40
  times faster than the geared drive (in the sim, on a tuna); since 0.2.0 one fight takes at most a tenth of a reel.
- **Translations**: this mod's own strings are English only; the strings ported from Tide keep Tide's eight locales.
- **By design**: a line out isn't saved, so logging out or the chunk unloading reels it in (a fish on is lost).
  Machines on a ship fish only while the ship is loaded.
- Seen while testing, not caused by this mod: Aeronautics Discovery's flyover despawn froze the server for 14-20 s in
  `ForcedChunkManager.forceChunk` (the stack is in Lag Watch's `logs/lagwatch.log`).

## Not built from the plan

- The Landing Net (heavy fish never need one at the surface).
- Repairing rods and reels at the Tackle Bench.
- Tackle made by Create processing (sequenced assembly, pressing, mixing): tackle uses crafting recipes from Create
  parts instead.
- Setting the float's depth with sneak and scroll before casting: it is set at the Tackle Bench instead.
- The performance measurements (see "Last full test").

## Credits and licences

Our code is MIT. Code, models, textures and data ported from Tide 2 by Lightning-64 keep Tide's MPL-2.0 and carry its
notice; other mods' fish data comes from Tide Extra Compatibility by Silent Autopsy (MIT). All the licence texts and
a `NOTICE` are in the jar's `META-INF` (README.md, "Licences").
