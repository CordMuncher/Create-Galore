# Create Galore Fishing: what the pack's docs need

For whoever folds `emre/fishing-mod` into a release. The mod's own docs are [README.md](README.md) (maintainers)
and [DESIGN.md](DESIGN.md) (the plan). This file lists what the pack's shared files still say about the old setup;
none of those files were changed on this branch. It grows with each milestone until the handover.

## Milestone 6: Tide 2 is gone

What changed on the branch:

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

What the shared docs still need (not touched here):

- **Version and CHANGELOG**: a new pack version. Players should back up their worlds and update the pack as a whole
  (mod and pack together): Tide's content converts on load and can't be converted back.
- **MODS.md**: drop the Tide 2 and Tide Extra Compatibility rows (lines ~226-227) and add Create Galore Fishing next
  to the Bridge's line at the top (in-house, both sides, `pack/mods/create-galore-fishing-<version>.jar`). Credit:
  fish, journal and extras ported from Tide 2 by Lightning64 (MPL-2.0); other mods' fish data from Tide Extra
  Compatibility by Silent Autopsy (MIT).
- **README.md**: the "Fishing runs on Tide 2" notes (lines ~200-206) and the table row "Aquaculture's rods and tackle
  → Tide 2" (line ~282) describe Tide's hold-to-cast and timing game; the mod count changes by one (two out, one in).
- **Field Guide** (`create-galore-tweaks/data/create_galore/modonomicon/books/field_guide/entries/fishing/`): ten
  entries name `tide:` items (journal, crates, angling_table, bait, lava_and_void, overview, fish_uses,
  where_fish_live, gadgets, rods). They all load (the old rod, line and Angling Table ids resolve through the
  aliases), but the text describes Tide's minigame, the Angling Table and Tide's rods. The fishing section is the
  integrators' to rewrite; the facts are in DESIGN.md §1-2b and README.md.
- **Bridge `neoforge.mods.toml` description**: mentions "Tide bait that stays used up" and "Hybrid Aquatic hooks …
  that work on Tide rods"; both are now Create Galore Fishing's.
- Cosmetic, known: Jade's look-at panel names the mod of ported `tide:` blocks as "tide" (item tooltips and JEI say
  Create Galore Fishing).

## Milestone 8: bait and fish processing

New content (all `create_galore_fishing:`), for the Field Guide's fishing section and the changelog:

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

## Milestone 10: aquarium tanks

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
