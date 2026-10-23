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
- **The half second counts from when the fish starts taking line**, as Emre put it. The in-game test tried
  counting from each press of use instead (so a short press during a long run is free); that was reverted to the
  rule as given, since tapping gains no line while the drag slips anyway.
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

## Every part wears a little (21 Oct 2026, mod 0.3.1, pack 0.4.1)

Emre: "reels and all components should wear a bit regardless, no infinite rod, just not a lot and negligible".

- **Rod and reel** already paid 1 durability per fight on top of overloading and grinding; unchanged.
- **Line** frayed 0.01 a fight (plus half the fight's own fraying) but stopped at fully frayed and never went. Now a
  line frayed through wears out after the fight ("Your Mono Line wore out"). An easy fight leaves about 100 fights in
  a spool; a snapped line still cuts back to good line (its fraying resets).
- **Leader, hook, float, sinker and lure** never wore. They now get a `part_wear` component, +0.005 a fight (200
  fights), and wear out after the fight at 1. Bait is used up as before.
- **Judgment calls:** the numbers (200 fights for small parts, about 100 for a line) are my picks for "negligible",
  easy to change (`TackleWear.PART_WEAR_PER_FIGHT`, `LINE_WEAR_PER_FIGHT`). Wear is only added once a part has
  fished, so unused parts keep stacking and old worlds' parts are untouched; a used part no longer stacks with new
  ones (like a used tool). Hybrid Aquatic's special hooks keep their own durability for their effects and get the same
  per-fight wear on top. Wear doesn't weaken these parts until they go (the line already weakens as it frays).
  **Corrected by Emre in 0.4.2, see below.**
- Tooltips show "Worn: N%" and "Frayed: N%", worn parts show a durability bar, and the Tackle Bench warns when a
  rigged part is past 90%.

## Worn-through parts break mid-fight (21 Oct 2026, mod 0.3.2, pack 0.4.2)

Emre: "rods, reels and lines can be worn and break mid fight and the fish escapes, lures and other can break but they
dont make the fish leave if its already hook ... not fishing with a nearly broken rod is the same logic as not going
mining with a nearly broken pickaxe".

- Rod and reel already broke mid-fight (since 0.4.0). The line now does too: the fight knows how much fraying the line
  can still take (`Fight.wearLimits(reel, rod, line)`), and when the fight's own fraying uses it up the line parts
  (a normal snap: the fish, the frayed end and the terminal tackle go, good line stays on the spool).
- A line at its last bit of fraying parts as soon as the fish pulls, like a rod or reel on its last durability.
- Leaders, hooks, floats, sinkers and lures still wear out at the end of the fight and never free a hooked fish,
  which is exactly what Emre asked for.

## Rod lift, strikes, rod looks and reel sound (21 Oct 2026)

Emre: the reel audio is "kind of mid"; rods look too thick and should bend "almost to a U"; there is no way to lift
the rod, so no pull-and-reel, and no difficulty in the strike because winding a lure always hooks the fish ("you have
to lift the rod ... if it's a strike, it's like a flick. If the fish is light, it's like an upwards flick. If the fish
is heavy, it's like a slow lift", after Fishing Planet's mechanic, right click stays reeling, left click lifts); the
rod bobs like a re-equipped item whenever use is let go.

Judgment calls (none of them caps or assists, per Emre's fight rules):

- **Controls.** Attack (left click) held while your own line is out lifts the rod; let go and it comes down. While
  the line is out attack does nothing else (no punching or mining) and works while use is held too. It is read from
  the key itself, so it can be rebound with vanilla's Attack key.
- **Lift speed** is `Fight.liftRate(load / rod rating) = 3 / (1 + 6 x share)` full lifts a second: a third of a second
  with nothing on (the strike flick), about half a second with a light fish, 1.6 s at three quarters of the rod's
  rating, 2.3 s at its rating. Let go, it comes down at 0.8 lifts a second.
- **Strike** needs a lift that starts below 60% height and actually rises during the bite window. Reeling no longer
  strikes for players (the drives still strike on their own). Bite windows unchanged (1.4 s float/sinker, 1 s lure).
- **Pumping (the fight model).** A full lift draws 0.9 m of line per metre of rod (the tip's sweep). It drags the fish
  through the water at the tip's speed (the same water drag as boat towing), works against the drag but not the
  reel's crank power, so a fish the crank stalls on can still be pumped. Reeling while the rod comes down lowers it
  only as fast as the reel takes up line ("winding down"), with no load on the crank. Let down without reeling, the
  line goes slack by the tip's travel; the fish swims the slack out, and slack for over a second can throw the hook
  (the existing rule). FightRules self-test: lift 0.40 s (0.5 kg) vs 1.65 s (30 kg) on a 20 kg rod; an 8 kgf fish
  over a 6 kgf crank gains 0 m in 30 s reeling only and 20 m pumping.
- **Rod angle** follows the lift: 25 degrees above the look lowered, up to 60 more lifted (was a fixed 30 more with
  a fish on). Not lifting at all now soaks a little less and puts less side pressure on the fish than before.
- **Lures and floats**: a lift also twitches the rig toward you by the tip's travel.
- **Machines** never lift; nothing changes for the Rod Holder or the drives.
- **Rod looks.** Blank butt half-width 0.24-0.44 px (was 0.45-0.8), tip 30% of the butt; the pole 0.42 (was 0.75).
  The bend reaches most of the way to the line at a quarter of the rod's rating and can curl 172 degrees, so a rod
  lifted high over a close fish makes a U. Third person: the rod arm rises with the lift.
- **Re-equip bob.** Vanilla lowers and re-raises the held item each time use is pressed on it
  (`ItemInHandRenderer.itemUsed`); rods skip that (client mixin), so the rod stays put between casts and reels.
- **Sounds** (still synthesized in `art/make_sounds.py`, nothing borrowed): the crank loop is modelled on a spinning
  reel (main gear mesh swelling once per handle turn, the rotor's whirr, the line roller, line laying on the spool,
  bearings) instead of a 48 Hz click buzz; drag clicks are pawl snaps ringing the reel's metal; a fast run (from
  0.8 m/s) plays a new drag scream loop pitched by the run's speed; the grind is gear teeth crunching under load.
- HUD: "Bite! Lift the rod (<attack key>) to strike", and a small rod-height bar left of the tension bar.

## Loss reasons, teeth, no snaps below the test, stress bars (Fishing 0.3.6, pack 0.4.12)

Emre asked (2026-10-21) for a lost fish to say what broke and why; for toothy fish to wear the line rather than
snap it at once, scaled by how oversized they are; for a line never to break while the drag is below its test;
for Fishing Planet style stress bars; and reported a false "It's running!" and small fish lost while being reeled.

- **No snap below the test.** Winding adds 10% to the pull but is now capped at the drag (the drag slips first).
  Spikes from runs and jumps still pass part of themselves through a sticky drag, but with the drag under the
  breaking load the peak is capped at drag + 90% of the margin to the test: the line can come close, never past.
  A pole (no drag) and an empty spool (drag as good as locked) can still snap. A frayed line's test is already
  lower, so a worn line on the same drag can still go; that is wear. FightRules: a 12 kgf fish that runs and jumps
  on a 4 kg line at 3.6 kg drag, 300 fights, 0 snaps (highest 3.96 kg); at 5 kg drag all 300 snap.
- **Teeth.** The flat 25% bite-off on every run or jump is gone. Oversize = the fish's peak pull / the rig's
  breaking load. Without a steel leader teeth fray the line 0.002 x oversize per second of tension and
  0.05 x oversize^2 per run or jump (`Fight.TEETH_*`). The fraying counts against the line's remaining life like its
  other fraying (a line of unknown wear, in the simulator, counts as new: 1) and is saved on the line after the
  fight. At oversize 3 or more, a run or jump can bite straight through (25% at 3, up to 50%). FightRules: 0.3x frays
  0.037 a fight and never bites fresh mono; 1.5x frays 0.54 (22% of fights chewed through); 4x bites through in 198
  of 200; steel never. In game a 130 cm moray (3.3 kg) on 4 kg string line at 3.6 kg drag landed and frayed the
  line 16%.
- **Slack.** The hook-thrown slack rule now needs the fish not pulling AND the angler not winding. A limp 0.3 kg
  fish wound in: 0 of 200 lost; left on a slack line: all 200 shake free (unchanged rule).
- **Running.** The HUD's "It's running!" needs a run and line actually going out this tick (`Fight.takingLine`).
- **Loss reasons.** `sim/Loss` (12 causes) is set with every losing outcome; the hand-held hook sends its message
  to chat (grey) with numbers: drag vs test, peak vs rod rating, spool length, a hook size that holds (ideal - 2).
  The action bar keeps the short line. Machines are unchanged.
- **Stress bars.** Three 70 px bars at the right, centred: line = tension / breaking load (drag tick, red at the
  top when the drag is over the test, blue when slack); reel = max(tension / max drag, while winding tension /
  crank power), full when grinding, hidden on a pole (synced as -1); rod = peak / rating (the same number that bends
  the rod). The rod-height bar moved beside the rod's bar.
- **Spoon question.** Emre saw only small mackerel on a spoon. `/cgfish pool` showed big fish making up about 70%
  of spoon bites in normal, cold, lukewarm and warm oceans (sea bass, grouper, snapper, tuna), so his spot was
  special; he chose to switch spots rather than chase it.

## Reel sounds matched to real reels (Fishing 0.3.8, pack 0.4.15)

Emre (21 Oct 2026) found the 0.4.8 reel sound worse than before: "like the hum of an engine", "a rudder trying to
spin", and asked us to study real reels with spectrograms instead of guessing.

- **References** (studied only, none of them ship; every sound is still synthesized): BBC Sound Effects fly reel
  recordings NHU05039056 (paying out), NHU05039057/058 (reeling in); Red Library "Machines 2" R18-19 "Fishing Reel
  Spins" (CC0, archive.org); OpenGameArt "Fisheefects" reel.wav; SoundBible "Reeling In Catch" (CC BY 3.0); and the
  Tide reel sound the pack used before this mod. Freesound, YouTube and Pixabay were not reachable from the build
  machine.
- **What the real ones look like.** A spinning reel being wound is a dense, even, fizzy whirr: the waveform is
  filled, not a row of clicks; energy sits mostly between 2 and 8 kHz (spectral centroid about 4 to 5 kHz), with a
  narrow gear whine near 3.9 to 4 kHz, a swell once per handle turn (3 to 5 a second) and only about 1% of the
  energy below 300 Hz. A drag or fly reel clicker is a train of sharp broadband ticks ringing 2 to 3 ms each, about
  150 a second when line pays out fast, centroid near 10 kHz.
- **What ours was.** The 0.4.8 crank loop put 92% of its energy below 300 Hz (a 68 Hz gear-mesh tone with 11
  harmonics, centroid 215 Hz): that is the engine hum, and the game pitches it down further under load. The grind
  had 61% below 300 Hz (a 95 Hz groan). The drag clicks rang at about 3.4 kHz for 20 ms, more a beep than a tick.
- **What it is now** (`art/make_sounds.py`). Crank: three trains of 600 tiny ticks a second each, rung through
  small inharmonic metal resonances (2.6 to 8.8 kHz), a coarser tick 24 times a second (anti-reverse and roller), a
  band of noise at 3.8 to 4.15 kHz for the whine, and line-lay hiss, all swelling with four handle turns a second;
  centroid 4.0 kHz, 2% below 300 Hz. Drag clicks: a bright 2 ms pawl tick around 4.2 to 5.3 kHz with a softer lift
  tick before it. Drag scream: 160 of those a second, uneven, over line hiss. Grind: hard 1.4 to 6 kHz ticks every
  4 to 20 ms over a gritty scrape, no drone. Side-by-side spectrograms were posted to Emre with this change.
- The game code is unchanged (same events, pitches and volumes): the problem was the audio, not how it is played.
- Still unheard by us: the build machine has no audio output, so the comparison is by spectrogram and numbers only.

## What a line holds, and the Iron Leader (Fishing 0.3.9, pack 0.4.18)

Emre's String Line (4 kg test) snapped on a 3.6 kg drag: it was frayed 22.5% (toothy fish since 0.4.12), so it held
`RodStats.frayedTest = test x (1 - 0.5 x wear)` = 3.55 kg, and nothing he looked at showed that number (the spare
spool's tooltip said 4.0 kg test). The fray-weakens-the-line rule stays (it is wear and tear), but it is now visible
everywhere and computed in one place:

- `RodStats.frayedTest` is the only formula; the rod's stats, the fight's Tackle and the line item's tooltip use it.
- `RodStats.kgHolds` rounds what a line holds DOWN to 0.1 kg, so a drag shown at or under it is never over it.
- `RodStats.dragOverLine()` (drag > what it holds now) colours the drag message (red), the Tackle Bench drag line
  (red), the HUD drag readout (red) and the rod tooltip's new "Line holds X" line (gold), and drives the warning.
- The fight treats a drag exactly at the test as safe (the spike cap applies at drag <= test, was <).
- Self-tests: Emre's exact rig (frayed 0.225, 3.6 kg) shows "3.5 kg" and the warning; stats and fight agree on
  3.55 kg; every drag step shown as safe survives 40 hard fights each; a drag at exactly 3.55 on a 3.55 kg line,
  300 fights, 0 snaps.

**Iron Leader**: Emre asked for an intermediate between line and the Steel Leader (iron wire is late game). 12 kg
test, 50% visible, teeth-proof like steel; 3 iron nuggets + 1 string (c:strings) -> 2, shapeless.

## Fight feel: fish moods, a stretchy line, real slack (Fishing 0.3.10, pack 0.4.19)

Emre (21 Oct): the bars were jittery, dropped to almost nothing the moment a fish swam toward him, and it felt like
fighting a machine; slack from a fish swimming in is normal but was not shown, and the line never went slack. Fix the
cause, not only smooth the display. What changed in `sim/Fight.java`:

- **No per-tick noise.** The old pull carried a gaussian wobble every tick. Now the fish has a mood (`Fight.Mood`):
  HOLD (steady pull with two slow sways, periods 3-6 s and 1.3-2.2 s, ±10% and ±5%, drifting round on a slow arc it
  changes every 3-7 s), RUN (same chance and length as before: 1.5x pull, 1.8x speed, sweeps to one side), CHARGE
  (same chance; comes in at 30-80% of its speed, picked per charge), SHAKE (new, 0.12 bouts/s x intensity, a ±22%
  throb at 2.5-3.5 Hz for 0.6-1.4 s) and REST (new, tired fish under 45% stamina, 0.15 bouts/s, 1.5-3 s at 70% pull).
  The effort follows its target with a 0.3 s lag (0.12 s into a run) instead of stepping.
- **The line is a spring.** State: line out and stretch (fish distance = line out + stretch; below zero is slack).
  Give = 0.12 x stretch x line out / test (line) + (0.4 + 0.6 x rod absorb) / rod rating (rod), metres per kgf.
  Each tick one equation is solved by bisection for the tension: the fish swims at its speed x (1 - T/pull) when
  the tension is under its pull and is dragged back at sqrt((T - pull) / water drag) when over; the reel winds
  retrieve x (1 - T/crank); the rod's lift and an angler moving off add their bit; the drag caps the tension and
  the rest goes out as line. Implicit, so a stiff braid on a short line is as steady as long mono. Winding down
  (reel while the rod comes down) keeps its old meaning: the rod comes down at what the reel winds, unloaded.
- **Slack is real.** A fish coming in faster than you wind, or the rod let down, leaves slack metres; they go to the
  client (new synced value), the line hangs in a parabola (sag ~ sqrt(3 x span x slack / 8)) and the HUD says
  "Swimming at you! Reel in the slack" (new flag). The hook-throw rules are unchanged (slack, fish not pulling,
  not winding).
- **Display.** The client eases the three bars, the rod bend and the line: quick to rise (60% a tick, so a jolt
  shows at once), slower to fall (25% a tick). Statuses added: "Head shakes!", "It's sulking: gain line". The fish
  swims round on its mood's arc instead of a random walk, deep on runs and sulks, near the top while shaking.
- **Fixed on the way:** the fish's tilt and the reel-crank slowdown divided an already-normalised tension by the
  line test again, so they barely moved on strong lines.

Checks. All 25 fight rules pass (held-at-empty-spool, towing and walking-off-a-charge now measure line out, which
is what they meant). Monte Carlo, every species x 5 rigs x careful/careless, 200 fights each, against the 0.4.18
rules: landing rate moves 0.015 on average; per rig and angler the means move at most 1.8 points (pole careful
72.3 -> 74.1%). Biggest single gains are heavy fish on too-light rigs that used to time out after 10 minutes and
now often land, in about 7 (REST gives a patient angler a window): stingray on steel careful 25 -> 61%. Tension change
per tick halved (tuna on iron 0.011 -> 0.005 of test). In-game: salmon fight landed with the new moods, bars eased,
"Swimming at you" and "Slack line!" shown, the line sags in third person; 424 self-tests pass.

## 0.3.12: lines hold or snap, bait on a snap (pack 0.4.24)

Emre lost a tuna on a frayed line behind an Iron Leader. The fraying was not from teeth: every fight frayed a line
0.01 plus half its load wear (unbounded), and his hard tuna fights had taken the string line to 45% frayed, so it
held 3.1 kg against a 3.6 kg drag at the hook-set. Emre (22 Oct): lines don't get progressively weaker; they hold
or snap, and fraying (teeth) is the only thing that weakens them.

- **Line wear removed.** No 0.01 per fight and no load fraying (`Fight.lineWear`, `LINE_FRAY_PER_WEAR` and the
  `SNAP_WORN` loss are gone). `TackleWear.lineFray` is only the fight's teeth fraying; a fight without teeth leaves
  the line's component untouched. Lines therefore last until snapped, spooled or chewed: this replaces the
  "nothing lasts for ever" rule for lines only. Rod, reel, leader, hook and terminal wear are unchanged.
- **Snap keeps queued bait.** A tackle-losing outcome now takes one bait (the one on the hook), not the whole bait
  stack. The line left is unfrayed (already true, now tested).
- **Cast warning.** Casting with the drag over what the line holds now says so in red on the action bar; the HUD,
  tooltip and bench already showed it but were easy to miss.

Checks. Fight rules 25/25 (the "frayed line parts mid-fight" rule became "a nearly frayed line fought hard by a fish
without teeth holds"). Monte Carlo against 0.3.11, all species x 5 rigs x 2 anglers x 200 fights: mean landing rate
moves 0.001; no rig or angler mean moves more than 0.2 points. In-game self-tests 424 pass (new: snap keeps 11 of
12 bait and leaves a shorter unfrayed line; a fight without teeth leaves the line unfrayed).

## 0.3.13: the Fish Cleaner (pack 0.4.26)

Emre (22 Oct) asked for a Create block that cleans the fish that are too big and passes the rest on.

- **Too big = what the furnace refuses.** `FishCleanerBlockEntity.needsCleaning` is a fish the `whole_fish`
  ingredient refuses (known weight of 3 kg or more), the same line the cooking recipes and the tooltip use, so a
  fish that comes out whole can always be cooked whole.
- **Everything else passes**, not only small fish: a fish line also carries junk, treasure and bait, and a block that
  refused them would stall the belt. Passing doesn't need rotation; only the saw does.
- **Same strokes and parts as the table** (`Cleaning.strokes`, `Cleaning.yields`), one stroke per 320 RPM-ticks
  (two a second at 32 RPM, like a player at the table), 4 SU per RPM like Create's saw. No knife wear: Create's
  machines don't wear, and the knife is used up in the recipe.
- **No overflow.** One big fish at a time, cut only once the last one's parts have left; small items queue in the
  single input stack behind it. Out the front: Create's direct belt input (belt, depot, chute, funnel), else an
  inventory, else dropped on open ground (one stack every 8 ticks).
- Recipe: knife over shaft, andesite casing, shaft over a Cleaning Table.

Checks. Processing self-test (new): only 3 kg-plus fish need cleaning; belts feed it from back and top, not the
front; unpowered, 12 cod, 3 kelp and a 2.5 kg trophy salmon pass into a barrel unchanged (catch data kept); a 6 kg
salmon waits without rotation, then is cut in 6 strokes (61 ticks at 32 RPM) into 16 parts. In-world on a full pack
server with real Create belts, a hopper and a chute driven by creative motors: belt line, hopper-to-chest line and
chute-to-ground line each passed cod, kelp, tropical fish and a 2.5 kg salmon whole and cut a 6 kg salmon and a
12 kg cod. All 436 self-tests pass.

## 0.3.14: one line colour (pack 0.4.41)

Emre (23 Oct): the cast line looked black while the line strung along the rod looked white; pick one and match.

- **The line's own colour, everywhere.** The line through the guides, the spool, the dangling rig and a machine's
  rod already used the fitted line's `color`; only the cast line (`CastHookRenderer`) was hard-coded black, vanilla's
  colour. It now asks `RodModel.lineColor(rod)` too: string and mono near white, clear line pale blue, heavy line
  yellow, braided green, pale grey with no line. Realistic as well: real lines are those colours, not black.
- Both are drawn as Minecraft lines (unlit, flat colour), so shader packs treat them the same way and they stay
  matched with shaders on.

## 0.3.15: a wound-in jump keeps the hook (pack 0.4.48)

Emre (23 Oct): "It jumped and threw the hook. Winding through a jump keeps the line tight" while reeling the whole
fight. The message promised something the rule didn't do: winding only multiplied the jump's hook-throw chance by
0.4, so with an average hook about one jump in ten still threw it (one in five while the fish swam at the angler).

- **A jump throws the hook only on a loose line.** Loose = nobody winding and the rod not being lifted, or the line
  actually slack (stretch below -`SLACK_TIGHT`) because the fish swims at the angler faster than the reel winds.
  Same chance as before on a loose line (0.08 + 0.4 x (1 - hold), doubled while it swims at the angler); none on a
  tight one. A small hook can still tear out on the jump's jolt (`HOOK_HOLD`, which names a hook size that holds).
- New loss `HOOK_JUMP_SLACK` with its own message for the outswum case, so the message always matches what happened.
- FightRules check: a fish jumping 3 times a second never throws the hook while wound in (0 of 200), does with
  nobody winding (200 of 200), and can when it outswims a slow reel.
- Monte Carlo against the 94 exported species (400 fights each, every rig): landed 68.3% -> 69.0% (careful angler),
  69.5% -> 70.6% (careless), hook thrown 7.0% -> 6.2% and 4.1% -> 3.0%. Within the usual 1.5 point band.
- Client test (flat ocean, Create + this mod, a test datapack making the sailfish jump 4 times a second): holding use
  from strike to end, 0.3.14 threw the hook with the old message; 0.3.15 never did.
