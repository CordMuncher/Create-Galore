# Create Galore Fishing: decisions for Emre's review

Judgment calls made while folding the mod into the pack, one section per area. Each entry: the question, what was
chosen, why, and what it takes to reverse it.

## Balance

Made in the balance pass on 20 Oct 2026 (mod 0.2.0), against the pack's principles: Create-centred, vehicles must
matter, combat not punishing, cozy homesteading, real seasons, one canonical item per thing with overpowered routes
cut, and old worlds must load unchanged. Nothing here removes or renames an id: every item, block and saved field a
0.1.0 or Tide world has still loads. Numbers come from the fight sim (`/cgfish simulate` code run offline on the
species exported from a pack server, 300 fights per row) and `tools/fishing-mod/pressure_model.py`; the self-tests
were run on a dedicated server before and after (362 checks before, 365 after, all pass).

### B1. Careless angling (the handover's open question)

**Overruled by Emre on 21 Oct (0.4.0), see "Emre's fight rules" below: no cap.**

- **Question**: careless play (drag high, reeling non-stop, rod pointed at the fish) lands about as often as careful
  play, and faster. Should it land less?
- **Found**: the real problem is the player who never touches the drag (it starts at a third of the reel's) and
  holds use the whole fight, the most natural thing for a new or cozy player. Holding use while the drag slips
  grinds the reel, and the grind grew with how far the fish out-pulled the drag, without limit. That player broke a
  whole Copper reel (300) on one cod (1,066 durability in one fight), a whole Iron reel (500) on one cod (691), and
  needed 4,315 durability for one salmon on starter gear. Careful play cost 1 to 38 per fight.
- **Chosen**: careless play keeps landing as it does (no extra snap risk), and one fight can never take more than a
  tenth of a reel's durability (`TackleWear.MAX_REEL_WEAR_PER_FIGHT = 0.1`; Copper 30, Iron 50, Brass and Steel 90,
  Trolling 150). So grinding still costs: a careless or drag-ignoring angler goes through a reel about every 10
  fights against 30 to 450 for careful play, and the grind sound and wear bar teach the lesson. Lines still snap as
  before (careless: 0-6% on starter and iron gear against 0-1%).
- **Why**: the pack says combat is not punishing and fishing is cozy play; skill should pay in tackle bought, not
  in a failed fight or a reel gone on the first fish. Landing rates (sim, careful / careless / drag untouched):
  cod on starter 91 / 95 / 91%, salmon on starter 66 / 83 / 48%, salmon on iron 91 / 97 / 86%.
- **Also**: the Basic Reel Drive (it cranks through a slipping drag) gets the same cap, which settles the handover's
  "can wear a reel out in one fight".
- **Reverse**: set `MAX_REEL_WEAR_PER_FIGHT` to 1 (no cap) in `cast/TackleWear.java`. To make careless play land
  less instead, raise the spike part of `sim/Fight.java` (the `peak` term) or lower line tests in `gen_tackle.py`.

### B2. Tide's grilled tuna

- **Question**: its three cooking recipes take `#tide:cookable/grilled_tuna`, which is empty (the pack's tuna is
  Aquaculture's), and any tuna is far over the 3 kg cook-whole limit, so they never fire.
- **Chosen**: removed the three recipes and their recipe-unlock advancements (and `port_resources.py` skips them on
  a re-port). Grilled Tuna stays registered (old worlds keep any they have; it is still 8 food, 0.7 saturation) and
  is hidden from JEI (`c:hidden_from_recipe_viewers` in the tweaks datapack). `/datacheck`: 61 unmakeable recipes in
  the pack, none of them this mod's (was 64, 3 of them ours).
- **Why**: making it from tuna steaks would need a new tuna steak item (the Cleaning Table gives Hybrid Aquatic's
  generic Fish Steak, which already cooks into Cooked Fish Steak; a second smelting output for it would clash).
  Big tuna are meant to be cleaned first, and the steaks cover tuna as food.
- **Reverse**: restore the three `data/tide/recipe/grilled_tuna_*.json` files (git history) with an ingredient that
  exists, drop them from `SKIP_FILES`, and remove `tide:grilled_tuna` from the hidden tag. Old worlds may log
  "ignored advancement tide:recipes/food/grilled_tuna_*" once; harmless.

### B3. Braided line and the tackle tiers

- **Question**: are the recipes in step with Create progression (andesite, brass, then TFMG steel and oil)?
- **Found**: Braided Line (18 kg, the strongest line short of the boat rig's Heavy Mono) was 8 vanilla string and a
  stick, while the weaker Mono (8 kg) and Clear (10 kg) lines need TFMG plastic from oil. In the sim an iron rod
  with a brass reel and braid, all pre-oil, out-fished the whole steel tier: salmon 97% in 27 s against 96% in 50 s.
- **Chosen**: Braided Line takes 8 TFMG Synthetic String (2 Rubber Sheets on a stonecutter) and a stick, so it sits
  in the oil age with the Steel Rod (rubber grip) as the sim's steel preset assumes. Everything else stays:
  bamboo or sugar-cane pole, string line and iron hooks on day one; Copper and Iron reels from Create cogwheels and
  sheets; Brass reel from a precision mechanism; steel, plastic and rubber for the steel tier; the boat rig (Boat
  Rod, Trolling Reel, Heavy Mono, Big-Game Hook, Steel Leader) from steel, brass, rubber and a large cogwheel.
- **Why**: Create-centred; a modern braid is synthetic fibre; pre-oil fishing still lands most fish (string line on
  an iron rod and brass reel: cod 93%, carp 90%, pike 83%, trout 97%, bass 98%, salmon 62%).
- **Left open**: with braid moved, the iron rod and reels have no stronger line before oil than String (4 kg). That
  is what the presets already assumed for "starter", and the drag lets a 4 kg line land fish far heavier than 4 kg,
  but if Emre wants a pre-oil middle line, a new "Twisted Line" (string through a Create press, about 7 kg) is the
  place for it.
- **Old worlds**: braided lines already made keep working; only the recipe changed.
- **Reverse**: `minecraft:string` back in `data/create_galore_fishing/recipe/braided_line.json`.

### B4. Day-one rod without bamboo (asked by the guide writer)

- **Question**: the Bamboo Pole is the only rod without a Create reel, and bamboo grows only in jungles.
- **Chosen**: the Bamboo Pole recipe also takes sugar cane (same diagonal of three; no other recipe in the pack uses
  that shape with sugar cane). Sugar cane grows by most water, where a new player fishes anyway. Day-one rig: pole,
  String Line (4 string and a stick), Small Hooks (1 iron nugget makes 3). Name kept ("Bamboo Pole"); a cane pole is
  the same thing.
- **Not changed**: the vanilla Fishing Rod recipe still gives a Wooden Rod blank that needs a reel; its tooltip says
  so.
- **Reverse**: drop the sugar cane entry from `recipe/bamboo_pole.json`.

### B5. XP per catch, machines and AFK farms

- **Question**: can fishing become an XP or loot farm (reel drives, baitfish breeding, release bonus)?
- **XP kept as Emre set it**: base by rarity (common 3, uncommon 5, rare 9, very rare 16, legendary 40) x heft
  (1 + 0.6 ln(1 + kg)) x size against the species (0.6-2.5) x trophy 1.5 / unique 2.5 x fight effort (0.6-2.6);
  release by hand x1.25; machines bank half, paid out only when a rod goes in or out. The javadoc and INTEGRATION.md
  said 2/4/8/15/40 and unique x2; they now match the code. A cod is about 5 XP by hand, so hand fishing is close to
  vanilla fishing, and a machine's half rate needs a player to collect. Create: Enchantment Industry already makes XP
  a Create resource, so this is not the pack's best XP source.
- **Fishing pressure floor 0.2 -> 0.05** (`FishingPressure.FLOOR`). With 0.2 a chunk never ran dry: 20 drives on a
  perfect rig landed about 600 fish an hour, 30 each, linear in drives, against the code's own design ("camping one
  spot with a dozen reel drives yields less and less, and moving on (or trolling) pays"). With 0.05: 1 rod 42/h
  (unchanged, it never reached the floor), 5 drives 112/h, 20 drives about 250/h (12 each), and a rod trolled from a
  ship through unfished water keeps the full rate, about 70/h. That is the "vehicles matter" tie-in: trolling from a
  ship is the best way to fish in bulk. (`pressure_model.py` reproduces these.)
- **Midas and Village rod bonus off for machines**: those rods add a bonus item to every catch (Midas: gold ingots,
  gold blocks 13%, golden apples, enchanted golden apple 0.4%, Totem of Undying 0.4%; Village: emeralds, Totem 0.4%).
  A bank of drives would print totems and god apples unattended; the pack already cut ferret Totems for that reason.
  By hand they keep Tide's behaviour.
- **No Nether treasure from machines**: treasure in the Nether is ancient debris or netherite scrap (50/50). By hand
  that is about 1 in 50 bites (1 in 15 with Luck of the Sea III); from unattended drives it would be a debris farm,
  a gated metal the pack already keeps from bees and the Mana Siphon. Overworld treasure (books, name tags, saddles)
  and crates still come from machines at the hand rates (treasure 2% + 1.5% per luck, crates 1.3%).
- **Baitfish breeding**: one young per tank every 2 minutes for fish meal; left alone (bait and small food only).
- **Reverse**: `FishingPressure.FLOOR = 0.2`; restore the `Catches.perkBonus` line in `CastHook.finishOnMachine`;
  remove the `MachineAngler` Nether line in `CastHook.rollBite`.

### B6. Travel rule and combat

- **Checked**: no ported item teleports or gives free flight. Voidseeker (eat to return to spawn) was already folded
  into Darkness Eater by the aliases, so it can't be obtained. Chasm Eel's bedrock eating stays off
  (`enableBedrockBreakingItems = false`, as the pack shipped Tide). Dragonfin Boots give one mid-air jump, like
  Cloud in a Bottle, which the travel rule allows. The Starlight Bow, gadgets (pocket watch, depth meter, climate
  gauge, fish finder, lunar calendar, weather radio) and special rods' perks (luck, bonus loot, echo bite message,
  lava fishing) don't move anyone. The End crate's Elytra stays Shulker Shells through the no-Elytra datapack.
- **Changed**: the Enchanted Pocket Watch freezes a mob until unfrozen, and only Tide's four vanilla bosses were
  exempt, so any modded boss (Cataclysm, Twilight Forest, Aether, Mowzie's, Iron's, Alex's Caves) could be frozen
  and killed for free. Added `#c:bosses` to `tide:ignores_pocket_watch` (tweaks datapack; checked on the server:
  Ignis is in the tag, a cow is not). Normal mobs still freeze:
  combat stays forgiving.
- **Reverse**: delete `data/tide/tags/entity_type/ignores_pocket_watch.json` from the tweaks datapack.

### B7. Seasons

- **Checked**: season conditions read Serene Seasons' `SeasonHelper` (no stand-in), verified by a new self-test
  check (`tide` suite) on a pack server in spring, and after `/season set mid_winter` (WINTER) and `early_autumn` (AUTUMN, fish data "fall"). 19 fish files carry season
  conditions; the rest bite all year. Fish temperature comes from Tide's climate noise, not the season. Left as is;
  a season-wide bite factor (slower bites in winter) would be a new mechanic for Emre to want first.

### B8. Float depth (asked by the guide writer)

- **Question**: the Tackle Bench's float depth (0.5-8 m, 1.5 by default) was saved on the rod but never used.
- **Chosen**: wired in. Under a float, bites are now rolled where the bait hangs (that depth below the float,
  stopping just above the bottom), so Tide's height conditions (`above` / `below` a y) and biome / temperature at the
  bait see the real depth. No new fish preference was added: a "bottom feeders like bait on the bottom" factor would
  change every carp, catfish and cod's bite rate and is Emre's call.
- **Reverse**: `context(level, position(), ...)` in `CastHook.rollBite` instead of `baitPosition(rod)`.

### B9. Hooked on Variety (asked by the guide writer)

- A bug, fixed: its "wood" criterion named `minecraft:fishing_rod`, which never exists as itself (it becomes the
  Wooden Rod), so the advancement could never be earned. It now names the Wooden Rod; the criterion keeps its name,
  so progress in old worlds stays. `port_resources.py` writes the same on a re-port.

### B10. Midas rod recipe (asked by the guide writer)

- Checked: its smithing base `tide:golden_fishing_rod` loads as the Iron Rod through the aliases, so the recipe is
  Iron Rod + Midas fish with a string template. `/datacheck` lists it as makeable. Left as is.

### B11. Duplicates with other mods

- Worms and leeches are Aquaculture's (canonical); fillets and fish bones are Aquaculture's; steaks are Hybrid
  Aquatic's. Fish scraps, fish meal, fish oil, fish roe, shark skin, maggots, boilies and chum exist in no other pack
  mod (Garnished's Meat Scraps are a different thing; Farmer's Delight's "earthworm" is a leftover lang key with no
  item). Fish oil and Create Additions' seed oil are different fluids that both burn. Nothing merged.
- **Found, not changed**: the tweaks datapack's Farmer's Delight cutting recipe turns any `#tide:cookable_fish` into 2
  fillets and bone meal whatever its size, so a 10 g bred guppy or anchovy gives as much as a 3 kg fish. It predates
  this mod; capping it would need a weight-aware cutting ingredient. Food is plentiful in the pack, so low priority.

### B12. The 3 kg cook-whole limit and food values

- **Kept**: fish of 3 kg or more must be cleaned before cooking. The tooltip says so; the Cleaning Table needs only
  andesite alloy, and Farmer's Delight's cutting board already takes vanilla cod and salmon and Tide's fish whatever
  their weight. Small hooks bias toward smaller fish.
- **Food values** of ported Tide foods are Tide's (cooked fish 6 / 0.6, large 10 / 1.2, small 4 / 0.4, roe 2 / 0.4)
  and unchanged from the Tide-era pack.

## Integration (lead)

- **Pack version 0.3.9, not 0.4.0.** You asked for 0.3 numbering on 20 Oct; a feature this big could justify 0.4.0,
  but staying on the agreed numbering was the safer reading. Reverse: bump `pack/pack.toml` and the CHANGELOG heading.
- **Bridge rebuilt as 1.33.0** without the three Tide mixins (the handover called it optional). A boot log without
  "mixin target not found" warnings for a removed mod is worth one rebuild; its description no longer lists the Tide
  fixes, which this mod now owns. Its `build.sh` also got the Mojang-library fallback for LWJGL that this mod's build
  already had (Maven Central answered HTTP 429 here).

## Field Guide

- **The Fishing section went from 13 entries to 21**: the fight, landing, tackle, Tackle Bench, live bait, aquarium,
  cleaning, rod machines and trolling each have their own entry so search finds them. The Angling Table entry became
  the Tackle Bench entry.
- **One neutral In-depth note in Rods explains converted rods**, because upgraded worlds hold them and a player who
  finds a changed rod needs that. It describes what happens without comparing mods (no meta talk).
- **XP is given as worked examples**, not a formula.
- **Early food** now teaches the Bamboo Pole (bamboo or sugar cane), String Line, an iron-nugget hook and worms from
  hoeing.
- **`/cgfish hook`** is shown where the old guide showed Tide's `/fishing test`.
- Everything the guide writer asked (Hooked on Variety, float depth, XP bases, day-one rod, Midas recipe, grilled
  tuna) was settled in the balance pass above, and the guide matches it.

## Old-world upgrade

Tested on a played two-player 0.3.8 world (real Tide catches, journal, advancements, every kind of container, a Create
contraption, a Sable ship, item frames, villagers, a far chunk left unloaded, a player mid-cast at shutdown), upgraded
with `install-server.sh` and packwiz on the clients, restarted and checked again; nothing lost. Report:
`modpack/fishing-mod/upgrade-test.md` in the project folder.

- **Fishing lines stack to 64** (were 1). Tide's lines stacked to 64 and load as ours; any container that limits
  stacks on load (ender chest and others) cut an old stack to one item. A spool that has been on a rod carries its
  metres and doesn't stack with fresh ones. Reverse: `stacksTo(1)` in FishingRegistry (old stacks get cut again).
- **Tide's spawn eggs for the duplicate species become Aquaculture buckets**, as the handover set it, so a stack of
  them drops to one. They are creative-only, so this was left.
- **Recipe-book entries and recipe advancements for removed Tide recipes are dropped** (grilled tuna, fish-bone bone
  meal, Tide's rod smithing): the log says so once per player on the first load; players see nothing.

## Play test

Two players on a dedicated server, every feature by real input where player behaviour was the point; then a
follow-up on trolling catches and belts. Reports: `modpack/fishing-mod/playtest.md` in the project folder (the full
list of calls is in `DECISIONS-playtest.md` next to it).

- **Pocket Watch: Aquamirae's Captain Cornelia and Maze Mother added to `tide:ignores_pocket_watch`** (optional
  entries). They are arena bosses outside `#c:bosses`; the watch froze Cornelia solid. Illager Invasion's Archevoker
  and Alex's Mobs' Tremorzilla and Forsaken were left freezable as big mobs rather than arena bosses.
- **Boilies kept as they are.** The heated mixing works when the four inputs reach the basin together (funnel, chute
  or one thrown stack); dropped in one at a time, Create's flour + water -> dough and Ratatouille's egg -> egg yolk
  fire first. That is ordinary Create basin behaviour and changing it means removing other mods' recipes.
- **XP orbs from Keep/Release spawn at the angler**, as vanilla fishing XP does; a player standing next to them can
  pick some up.
- **The pole's HUD shows "Line 9 / 8 m" while the float drifts.** Cosmetic, left.
- **A boat's speed doesn't pull on a hooked fish** (settled by Emre in 0.4.0, see below). The fight kept the fish at the line's own distance wherever the
  rod tip goes, so a boat speeding away tows the fish with no extra line or tension. Making speed count would change
  the fight model and its balance, so it is yours to decide.
- **A big fish on a light drag can beat a drive** (settled by Emre in 0.4.0: drives keep the rod's drag; the guide
  says only to keep the drag below the line's test). Geared and Fuel drives never wind while line slips (by design);
  a 16.5 kg tuna at a third-of-line drag was worn out but still out-pulled it, and line crept out until it was lost
  (8.7 kg tuna and a 19.3 kg opah landed on the same drag). The Field Guide's trolling page now says tuna-sized fish
  want about two thirds of the line. Alternative for you: drives that tighten the drag on a tired fish.
- **Alive fish in chests stay separate stacks** (the handover's known issue) until a player picks them up. A cheap
  fix if wanted: round belt output's `alive_until` up to the next 30 s so close catches stack.

## Emre's fight rules (21 Oct 2026, mod 0.3.0, pack 0.4.0)

Emre's own calls, which replace B1 and the two play-test notes above.

- **No cap on wear.** A careless angler can destroy a whole rod or reel in one fight; the guide explaining it is the
  punishment. "Combat not punishing" means easier by degree, not by caps. `MAX_REEL_WEAR_PER_FIGHT` is gone.
- **Reel wear comes from reeling while line runs out**, not from letting the drag slip. After half a second of slip
  (`Fight.GRIND_GRACE_TICKS = 10`, time to let go of use) it grinds at `GRIND_PER_SECOND = 4` wear (12 durability) a
  second times (drag / reel's top drag)^2, so half drag grinds a quarter as fast as full drag. Cranking against a
  fish on an empty spool counts as full drag.
- **A part worn through breaks mid-fight and the fish is gone.** The fight knows how much durability the rod and
  reel have left; a reel ground through seizes (new outcome "reel seized", the line parts and the terminal tackle is
  lost), a rod worn through breaks (its parts come back).
- **An empty spool is a locked drag.** The fish is held at the end of the line; if it pulls more than the line
  holds, the line snaps ("spooled").
- **Moving away tows the fish.** Walking or sailing off adds the water's drag on the fish (0.07 kgf x kg^(2/3) x
  speed^2) to its pull: a small fish on a strong rig is towed along, an evenly matched big one takes line through the
  drag until the line holds, snaps or runs out. Smoothed over a few ticks so a flick of the rod isn't a yank.
- **Reel drives never tighten the drag themselves.** The guide no longer gives fractions ("a third", "two thirds"):
  it says to keep the drag below the line's test, and the Tackle Bench warns when it isn't.
- In the sim, careful anglers still pay 1 durability a fight; a careless one (drag at 3/4 of the line, cranking
  non-stop) pays about 22 a fight on cod and 88 on salmon with a Copper reel (300), and can lose a whole Steel reel
  to one arapaima.
