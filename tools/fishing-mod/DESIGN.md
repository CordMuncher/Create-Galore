# Create Galore Fishing — a realistic-lite fishing mod that replaces Tide 2

## Ground rules (from Emre, binding for the whole implementation)
- Work ONLY on branch `emre/fishing-mod`, from its current state (base commit 4927d7a).
- Never push to any other branch. No PRs / merge requests unless Emre asks.
- Never pull, merge or rebase upstream changes, even if others push. Stay on our own version.
- Don't touch other people's work; the pack is actively worked on by others.
- Document the code (Javadoc on public types, comments where logic is non-obvious, a maintainer
  README). Do NOT write Field Guide entries; leave the guide's Fishing section alone. Hand the facts to
  the integrating agents in `tools/fishing-mod/INTEGRATION.md`.
- Keep compatibility, and test the pack with Tide removed. Stay in scope.

## Context
The pack fishes with Tide 2 (2.1.1) + Tide Extra Compatibility: hold-to-cast, a timing-bar minigame,
2D sprite rods and fish, an Angling Table for bonus parts. Emre wants Fishing Planet-style fishing:
a rod, reel, line and hook you assemble and see as a 3D model, ratings and stress on every part,
sensible Minecraft bait with Create ways to get it, a fight where the rod bends and you steer, set
drag and reel, real fish weights, real processing of big fish, and Create machines (powered rod
holders, trolling from Sable ships). Arcade simplifications are fine; it must not become a full sim.
The new mod replaces Tide: Tide leaves the pack and every Tide item in existing worlds converts.

What the exploration established:
- In-house mods compile with plain `javac` against an installed server (Mojang names, no Gradle, no
  refmaps; mixins OK; no ATs → `@Accessor`/`@Invoker`); jar committed to `pack/mods/`, listed in
  `pack/index.toml` (`tools/bridge-mod/build.sh`). Bridge has no blocks/BEs/entities/BEWLRs/
  components/kinetics, so those are new; payload/command/screen/render patterns are reused from
  Bridge (`guideai/GuideAiNet.java`, `client/ClaimBoxView.java`, `guideai/Ships.java`,
  `mixin/ItemStackKnifeMixin.java` for swapping stacks on creation).
- Tide 2 is **MPL-2.0**; Tide Extra Compatibility (data only) is MIT. Tide's 102 species each have an
  item, bucket, spawn egg and a vanilla-ModelPart entity (90 model classes, ~13 shared entity classes,
  no GeckoLib). Port ≈ 250 files / 18k lines of mostly mechanical work + ~1,100 assets. The source
  uses Stonecutter with Fabric checked in, so NeoForge branches get resolved during the port.
- NeoForge 21.1.262 registry aliases (`DeferredRegister#addAlias`, called in the mod constructor)
  work for items, blocks, BE types, entity types and data component types, across namespaces, and
  are used by codecs, entity/BE loading **and tags**. Without them, unknown item ids empty the slot,
  unknown components are dropped, unknown entities/BEs are deleted, with no warning screen.
- Create 6.0.10 supports live stress (`calculateStressApplied` + `getOrCreateNetwork()
  .updateStressFor`), value boxes (`ScrollValueBehaviour`), base impact via `BlockStressValues.IMPACTS`.
  Sable 2.0.6's companion API (`SableCompanion.INSTANCE`) gives server `logicalPose()`, client
  `renderPose(partialTick)`, `projectOutOfSubLevel`, `getVelocity`. Create: Liquid Fuel takes fuels
  via the data map `data/createliquidfuel/data_maps/fluid/liquid_fuel.json`.
- Things that must keep working: Fishy Business (vanilla `FISHING_ROD_HOOKED` needs a `FishingHook`),
  Aquaculture's `ItemFishedEvent` weight handler and fillet-by-weight, Create: Integrated Farming's
  Fishing Net, Hybrid Aquatic hooks (incl. Bridge's Ominous/CreeperMagnet summons), the pack's
  canonical items (`aquaculture:worm`, `leech`, `fish_fillet_raw/cooked`, `fish_bones`).

Decisions confirmed with Emre: ported Tide fish keep swimming (spawns as today); small fish stack by
size class, fish over ~3 kg and trophies are unique; port every Tide extra players may own; push
milestones as they land and stop for his feedback after the core loop (milestone 5). Added by Emre:
live-bait bucket and aquarium tanks; three reel drives (dumb shaft-driven, geared shaft-driven,
fuel-powered geared) where drag must be chosen against the build's torque or it stalls.
Also added by Emre: vanilla XP per catch scaled by fish size, fight difficulty and rarity; reel drives
bank XP at 50% of the hand rate and pay it out when a rod is placed or removed; drag adjustable on
the fly whenever the rod is in hand (before casting, waiting, mid-fight); drives never change drag.
Catch and release (approved by Emre, details in § 2b): a fish landed by hand is held up for up to 2
minutes with a Minecraft-styled Keep / Release screen; releasing by hand gives a bonus, machines
never do; drives bank 50% XP the moment they successfully reel a fish in, whatever happens after.
Everything stays Minecraft-styled: rods are built from rectangular sections, only some are telescopic.
My reading of "can be told to cast (reel in slowly)": the Geared drive's Cast mode lobs the rig out
and retrieves slowly to work a lure, recasting when it returns; Wait mode holds for other rigs.

## The mod
`tools/fishing-mod/` → `pack/mods/create-galore-fishing-<ver>.jar`, mod id `create_galore_fishing`,
licence "MIT AND MPL-2.0" (ported Tide files keep MPL Exhibit A headers, in package
`dev.creategalore.fishing.tide` and their own asset folders; Tide credited in mods.toml/README).
Required on both sides; declared `incompatible` with `tide` from milestone 6 on. Build script copied
from Bridge's. Hard deps: neoforge, create. Soft deps (holder-class + `ModList.isLoaded`): sable,
aquaculture, hybrid_aquatic, create_integrated_farming, createliquidfuel, serene seasons, tfmg,
createnuclear, alexscaves, naturalist, crittersandcompanions.

Packages under `dev.creategalore.fishing`: `sim` (pure Java fight model, no Minecraft imports, so the
Monte Carlo runs outside the game), `tackle`, `cast` (hook entity, bites, fight driver), `fish`
(species data, weight, catch component), `bait`, `process`, `create` (rod holders), `compat`
(aliases/migration, Sable, Hybrid hooks, Aquaculture, Integrated Farming), `client` (Dist-only:
renderers, HUD, item extensions, Flywheel visuals), `tide` (ported MPL code), `net`, `command`.

### 1. Tackle — parts you assemble and see
Every part's stats live in datapack JSON (`data/<ns>/fishing_tackle/<kind>/*.json`), so balance is
tuned without code. An assembled rod is one item; immutable record components hold its parts
(blank, reel, line + meters, leader, hook, terminal, bait list) and wear. Fight state is never
written to the stack (it lives on the hook entity).

| Part | Stats | Tiers / tie-ins |
|---|---|---|
| Rod blank | length (cast range), max load kg, action fast/moderate/slow (where it bends, how much shock it soaks), lure weight range, telescopic sections | Bamboo Pole (no reel, fixed line, beginner) → Wooden → Iron → Steel telescopic (TFMG steel) → Heavy Boat rod (holders/trolling) → Netherite (lava) |
| Reel | max drag kg, drag steps, gear ratio (speed vs power), line capacity m, smoothness (spike damping) | Copper spinning → Iron → Brass precision (precision mechanism, sequenced assembly) → Steel baitcaster → Big-game trolling reel |
| Line spool (meters left) | test kg, stretch (shock absorption), visibility (wary fish) | String (stretchy, weak) → TFMG-plastic mono → braided (Create mixing; strong, no stretch, visible) → clear line (low visibility) → steel leader (needed vs toothy fish) |
| Hook | size #12 … 8/0 (fits which mouths, selects which size class), material | pressed iron; gold (luck); netherite (lava). Tide's special hooks (lavaproof, fiery, permafrost, twilight, void) and Hybrid Aquatic's (barbed, glowing, magnetic, creepermagnet, ominous, manglerfish; summons moved over from Bridge) keep their effects |
| Terminal | float (depth setting, bites show as dips), sinker (bottom rig; Create Nuclear lead), lure (spoon/spinner/crankbait with dive depth/jig/popper/soft plastic; reusable, can be lost) | Tide's 33 bobbers become floats (ported models) |

- **Tackle Bench** (replaces Angling Table): a slot per part, live 3D preview of the assembled rod,
  stats panel with weakest-link warnings ("line 4 kg < drag 6 kg: lower the drag"), "rated for fish
  up to ~X kg", disassembly, rod/reel repair with materials. Bait/terminal quick swap by cursor
  right-click on the rod in the inventory, as in Tide.
- **Stress & wear**: rods lose condition while loaded above ~70% of max load and can crack above
  it; reels wear while the drag slips, much faster when you reel *while* it slips (overload); line
  frays per fight and loses meters plus hook, terminal and bait when it snaps.
- **Rod item**: not a `FishingRodItem` subclass (Hybrid Aquatic's mixin there would misfire);
  answers `ItemAbilities.FISHING_ROD_CAST`, sits in `#minecraft:enchantable/fishing` (Luck of the Sea
  → treasure/trophy odds, Lure → bite speed), no re-equip bob on component changes.
- **3D rod model** (BEWLR), blocky like the rest of Minecraft: rectangular sections (no cylinders);
  telescopic only for telescopic blanks (others are fixed sections), guides, handle, the reel model,
  line colour, the terminal hanging at the tip. Sections slide out on cast and collapse when reeled
  in fully. Under load the sections rotate toward the line: bend = load / max load, spread by action
  (fast bends the tip, slow the whole blank), spring-lagged. Same renderer in hand (1st/3rd person),
  in the bench preview and in holders. Rods are drawn shorter than life (0.55 × length + 0.45 blocks)
  so they fit the view; the line leaves from the tip as drawn. The bench preview is sized from the
  rod's measured geometry so the reel never leaves its frame.

### 2. Casting, bites and the fight (cheap proxy, server-authoritative)
- **Hook entity extends vanilla `FishingHook`** (own `tick()` calling `baseTick()`, safe
  `retrieve()`, `isOpenWaterFishing()`), so `player.fishing`, `FISHING_ROD_HOOKED`, `ItemFishedEvent`,
  `Stats.FISH_CAUGHT` and other mods' hooks keep working. Hook is not saved; it is removed on unload.
- **Cast**: hold to charge, release. Distance = rod length × charge × how well the terminal's weight
  fits the rod's lure range; limited by line on the reel. Float depth / retrieve speed set with
  sneak+scroll before casting.
- **Bites**: rolled at the hook (on Create's lazy-tick cadence, species pool cached per position
  bucket) from Tide-style conditions (fluid, biome, y/depth, fresh/salt, time, weather, moon, season,
  structure, temperature), weighted by bait/lure preference, hook size vs mouth size, line visibility
  vs wariness, depth zone of the presentation, lure speed when trolling, chum in the area, and
  **fishing pressure** (catches dim a spot for a while, so moving and trolling pay). Bite shows as
  float dips, rod-tip twitches or a strike; strike = right-click within a window ≥ ping + 300 ms.
- **Size on the hook**: Tide's typical/record cm → weight by `W = a·L^b` (`a` by body shape), skewed
  by hook and bait size; classes Young / Common / Large / Trophy, plus rare Unique individuals.
- **Per-species fight values** (the cheap ones Emre described): `fight_per_kg`, `stamina`, and a
  `profile` = intensity over progress (1 − distance / start distance), as a preset (`early`,
  `middle`, `late`, `second_wind`, `steady`, `sulker`) or custom points; plus `runs`, `jumps`
  (head shakes that can throw the hook), `toothy` (bites through non-steel line), `wary`. Defaults are
  derived from Tide's `strength`/`speed`/`behavior` for all ~180 fish, then notable species are tuned
  by hand (tuna and sharks long runs, pike early, carp middle runs, salmon second wind, catfish
  sulkers, bass and trout jumpers).
- **Each tick**: `pull = fight_per_kg × effectiveWeight(w) × profile(progress) × stamina × jitter`
  (`effectiveWeight` tapers above ~20 kg, so monsters are hard, not impossible). If pull > drag, line
  pays out and tension = drag; otherwise holding right-click reels in (gear ratio) and tension =
  pull + reel effort. Spikes from runs and jumps are soaked by line stretch, reel smoothness and rod
  bend, and the bend depends on **rod angle**: holding the rod 30–90° off the line soaks spikes and
  adds side pressure (drains stamina faster); pointing straight at the fish soaks nothing. Snap if
  tension > line test (or a toothy fish on non-steel), rod damage above max load, hook thrown after
  sustained slack. Stamina drains with tension, recovers when slack. A tired fish within ~2 m lands;
  heavy fish need a Landing Net in the off hand or may shake off at the surface.
- **Sync**: reeling and strikes use vanilla hold-to-use (server sees `isUsingItem`); drag changes are
  one validated, rate-limited packet; tension is a byte on the hook's synced data; the owner gets a
  HUD packet. Payloads are required (not optional), the mod being on both sides.
- **XP**: every catch drops vanilla XP orbs at the angler: base by rarity (common 3 → legendary 40) ×
  weight (absolute, 1 + 0.6 ln(1 + kg), and relative to the species' typical weight) × size class
  (trophy 1.5, unique 2.5) × fight effort (peak pull vs the tackle's limits and fight time): a small
  panfish ~2, a 4 kg carp ~6, big rare sea fish dozens. Junk gives none. Reel drives earn 50% of that
  and bank it (shown by Goggles), paying it out as orbs whenever a rod is placed in or taken out.
- **Drag on the fly**: scrolling with the rod in hand (rebindable keys too) changes the drag in reel
  steps at any time: before casting, while waiting, mid-fight. It is stored on the rod. Holders and
  drives never change it, so players set a level that is safe for their line *and* their machine.
- **HUD**: tension bar (blue slack / green / yellow / red), drag setting, line out / left, rod and reel
  load. Scroll while a fish is on = drag (rebindable). After landing, a catch card (species, weight,
  length, class, personal best).
- The hooked fish is drawn at the line end with its own 3D model (swimming, surfacing, jumping).
  On a catch: advancement trigger, `ItemFishedEvent` (then our weight overwrites
  `aquaculture:fish_weight` so Aquaculture's own systems agree), vanilla junk/treasure tables still
  roll (Aquaculture/Critters treasure keeps working).

### 2b. Catch and release
- **By hand**: when a fish lands, the angler lifts it and holds it, one-handed by the mouth for small
  fish, in both hands for big ones (4 kg and up), seen by everyone in third person. The fish is its own
  mob's model at its real length (a block is a metre) and at least the girth its weight implies. A
  Minecraft-styled screen (vanilla panel look, the fish's 3D model turning in it) shows species,
  size class, weight, length, rarity, personal best, and two buttons clicked with the mouse:
  **Keep** (the fish item + XP, amount shown) and **Release** (XP + a release bonus, +25%, amount
  shown; the fish swims off, no fishing pressure added). The angler may close the screen and keep
  holding the fish; right-click reopens it. The fish lives 2 minutes from the catch: after that the
  Release button greys out ("it didn't make it") and the fish is kept automatically. No casting while
  holding a fish.
- **Fish items stay alive 2 minutes** after the catch (catch time on the item, countdown in the
  tooltip). An alive fish item that ends up in water (dropped, off a belt, out of a chute, thrown by
  a Mechanical Arm) is released: species with a live mob swim off as that mob, others splash away. No
  XP and no bonus for that; it lets sorting lines send unwanted fish back.
- **Drives**: two states only. A fish escaping, snapping the line or throwing the hook before it is
  reeled in banks nothing. A successful reel-in banks 50% of the hand XP at once, whatever happens
  next: the fish goes onto a belt through a funnel, else into a connected inventory (chest, hopper),
  else it dangles at the line's end waiting for a player (taking it with an empty hand = keeping it),
  and after a while (~30 s) wriggles free and is gone. No release bonus ever for machine catches,
  and a fish later returned to the water from a belt changes nothing.

### 2c. Sound design (feel matters)
Our own sounds, synthesized by `tools/fishing-mod/art/make_sounds.py` (numpy, encoded to Ogg Vorbis with
ffmpeg), so nothing borrowed; each is a sound event with a subtitle, replaceable by resource packs.
Continuous sounds are client-side tickable sound instances driven by the hook's synced state; one-shots
come from the server.
- **Drag ratchet**: one click per few centimetres of line actually leaving the spool, so the click rate
  *is* the fish's run speed (a slow tick when it leans on the drag, a buzz on a run); pitch by reel
  (copper rattly, brass smooth, big-game deep).
- **Reel crank**: a looping gear whirr while reeling, pitched by retrieve speed, dropping and grinding
  as the load nears what the crank can turn; an ugly grind when cranking against a slipping drag.
- **Line strain**: a creak that fades in above ~65% of the breaking load and tightens toward a snap;
  a hard *twang* on a snap; a soft whip on a thrown hook; a slack-line slap.
- **Cast**: a whoosh scaled by the charge, line zipping off the spool in flight, the splash/plop of the
  rig by its weight; the bail clack when reeling starts.
- **Bites**: float plop and bob ticks, a rod-tip tap for sinkers, a sharp tug for lures; strike swish.
- **Fight**: fish splashes and jump splashes by fish size, a big thrash when a fish bolts at the bank.
- **Landing / catch and release**: lift splash, flop sounds while held, the release splash.
- **Machines**: holder bite alarm (a clicker-style ratchet alarm, plus the bell), drive motor hum by RPM,
  gear-shift clunk, stall groan, fuel-engine putter.
- **Tackle Bench**: click of the drag knob, snap-on sounds for parts.

### 3. Fish
- **Data**: the loader reads Tide's fish JSON format (so Tide's data, Tide Extra Compat's and the pack's
  overrides carry over with path changes) plus an extension file per species for length-weight
  coefficients, fight values, diet, mouth size and flags. Covers ported Tide fish, Aquaculture,
  Hybrid Aquatic, Alex's Caves, Naturalist, Critters & Companions, Aquamirae, BetterEnd, Cataclysm,
  vanilla. `tools/fishing-mod/gen_species.py` builds the extension files from one overlay CSV.
- **Ported Tide species: 90** (102 minus the 11 the pack hands to Aquaculture/Hybrid Aquatic and
  Voidseeker, which folds into Darkness Eater): entities, models, textures, buckets, spawn eggs and
  natural spawns as today. The 12 skipped ones alias to their canonical items/entities.
- **Catch component** {length (1 cm steps), weight, class, trophy/unique, catcher, day}. Fish below
  ~3 kg keep only species + class (stack per class); heavier fish and trophies keep exact numbers
  (stack size 1). Processing strips the component.
- **3D fish items everywhere** (ours and every other mod's fish mapped to an entity): entity model in
  hand, on the ground, in frames and on mounts, scaled by size. Inventories also show the 3D model,
  via a render-to-texture icon cache (one bake per species + class), because Hybrid Aquatic's fish are
  GeckoLib and a chest of live renders would be too slow. Implementation: wrap baked item models in
  `ModelEvent.ModifyBakingResult` (override `applyTransform` to return the wrapper), register item
  extensions at `EventPriority.LOWEST` only for items without their own, fall back to the original
  sprite when an entity can't render or there's no client level. World item entities beyond ~16
  blocks draw the sprite.
- **Ported extras**: Fishing Journal (Tide's `TidePlayerData` NBT read once into a `copyOnDeath`
  attachment, plus weight records), Fish Display → **Trophy Mount** (keeps displayed fish; shows
  weight/length/catcher), Satchel, Fishy Notes, crates (rare snag catches; loot tables keep the
  `tide:chests/crates/*` paths, so the pack's Elytra→Shulker Shell override still applies), gadgets
  (pocket watch, depth meter, climate gauge, fish finder, lunar calendar, weather radio), jelly
  torches, Starlight Bow, Dragonfin Boots, fish swords, Enchanted Pocket Watch. Tide's advancements
  are shipped under `data/tide/advancement/…` with the same criterion names so progress survives.
  `tide:fish_bone` → `aquaculture:fish_bones`, `tide:fish_slice`/`cooked_fish_slice` → fillets.

### 4. Bait (Minecraft-sensible) and how you get it
Before adding any item, check `tools/extract_mod_data.py`'s `items.tsv` for an existing one.

| Bait | Who likes it | Getting it (Create tie-in) |
|---|---|---|
| Worms (`aquaculture:worm`) | most freshwater fish | Aquaculture Worm Farm; **fan washing (`create:splashing`) dirt / coarse / rooted dirt → mud + chance of worms**; small chance when hoeing grass |
| Leeches (`aquaculture:leech`) | walleye, catfish, pike | fishing treasure as now; washing mud |
| Maggots | panfish, trout | unheated mixing of rotten flesh + dirt |
| Insects (Naturalist caterpillar/butterfly) | trout, bass | Capture Net as now |
| Dough (`create:dough`), boilies | carp, catfish | boilies: heated mixing of fish meal + `create:wheat_flour` + egg, then compacting |
| Cut bait | sea fish, catfish, sharks | Cleaning Table byproduct |
| Live bait (small baitfish, leeches) | pike, bass, tuna, sharks; live beats dead | catch minnows/anchovies on small hooks and keep them in a **Live-Bait Bucket** (water-filled, holds a few dozen); bait taken from it counts as live, a loose fish item only as cut bait. Bred in aquarium tanks (§ 6b) |
| Squid strips | saltwater predators | cutting squid / Ocean's Delight tentacles |
| Chum / groundbait | raises bites in an area for a few minutes | fish meal + scraps, mixing; thrown or in a feeder |
| Incandescent / Abyss bait | lava / void fish | ported Tide recipes |

Tide's `tide:bait` converts to a generic mixed bait; Lucky and Magnetic bait stay as special baits.

### 5. Processing big fish
- **Cleaning Table**: place a fish (shown as its 3D model), knife strokes (`#c:tools/knives`; more for
  heavier fish). Yields scale with weight × species yield: fillets (`aquaculture:fish_fillet_raw`),
  steaks for big fish (raw/cooked, more filling), roe (sturgeon, salmon, in season), bones
  (`aquaculture:fish_bones`), scraps (→ cut bait, fish meal), shark skin (→ leather), fish oil from
  oily species. A Deployer with a knife works it; funnels/hoppers take the outputs.
- **Fish meal**: milling scraps/bones. **Fish oil**: compacting scraps/oily fish in a basin → fluid;
  Blaze Burner fuel via the Liquid Fuel data map (same rate as seed oil).
- Small fish (< ~1 kg) keep today's paths (cook whole; cutting board → 2 fillets via our
  `#create_galore_fishing:cookable_fish` tag replacing `tide:cookable_fish`). Fish over ~3 kg are too
  big to cook whole: cooking recipes for fish use a weight-limited custom ingredient (overriding other
  mods' fish cooking recipes in data), which covers furnace, smoker, campfire and Create's fan with no
  mixin; the tooltip points to the table.

As built in milestone 8, where it differs from the above: boilies are one heated mixing step (fish meal, flour,
egg, water), with no compacting step and no extra item. Chum is thrown or fired from a dispenser; there's no feeder
block. Yields follow the weight with one meat fraction for every species, and the per-species part is by tag (roe
fish, oily fish, sharks). Roe comes by chance, not by season. Fish oil comes from pressing scraps or a whole small
oily fish (the table gives oily fish more scraps). The knife tag is 1.21's `#c:tools/knife`. Tide's own
`tide:cookable/*` tags are kept, since its ids stayed after milestone 6, so there's no new cookable tag. The 3 kg
limit matches the catch data: a catch under 3 kg keeps no exact weight, so the limit only ever refuses fish that
carry one.

### 6. Create machines, ships and trolling
Every holder takes an assembled rod on top (the line stays out when you put a cast rod in), keeps
the rod's own drag setting (scrolled in hand before you mount it; holders cannot change it), plays a bite
alarm, pulses redstone on a bite, and gives comparator output = tension. Landed fish go into the
container below/beside, else onto the deck. Holders fish as a fake player tied to whoever mounted
the rod, so catches, stats and advancements credit them. Goggles show tension, drag, gear, line
out, reel wear, bait left and torque headroom.

**Torque vs drag (the core rule for all powered holders).** Reeling against a fish needs torque
∝ tension × gear. On shaft-driven holders that torque is Create stress: impact (SU per RPM) =
k × tension × gear ratio, updated live (throttled to every 10–20 ticks / ≥10% change). A drag that
is safe for the line and rod can still be more than the build can turn: when the fish pulls up to
the drag, the network overstresses and **stalls** (the whole network, as Create does), resumes when
the fish eases off. So players pick drag against their rotational strength, not just their line.

| Block | Power | Behaviour |
|---|---|---|
| **Rod Holder** | none | Holds the rod, bite alarm only; take the rod out to strike and fight by hand. |
| **Basic Reel Drive** (early) | shaft in on one side, shaft out on the other for chaining a row of drives along a rail; any rotation works, a water wheel turns it at water-wheel speed | Only reels, at a fixed speed from input RPM. No strike, no speed changes: you hook the fish yourself, then put the rod in and it fights dumbly. It **keeps reeling even while the drag is giving line**, so a good drag setting protects line and rod but the reel wears much faster (overload). |
| **Geared Reel Drive** (later) | shaft in + shaft out, like the basic one | Built-in gearbox that **shifts automatically**: steps down (slower, more torque, less stress) when it nears a stall, steps up when the fish tires; gear range limited by a value box. **Never reels while the fish takes line.** Strikes by itself when it feels a pull. Modes (value box): **Cast** — lobs the rig out to a set distance and retrieves slowly to work a lure, recasting when it's back; **Wait** — holds still for float, bottom and trolling rigs. Bait slot fed by funnels for re-baiting and recasting after a catch. |
| **Fuel Reel Drive** (standalone) | no shafts at all; burns fuel: furnace fuels in a slot, or any fluid in Create: Liquid Fuel's data map (TFMG fuels, seed oil, our fish oil) in a small tank | Always has the geared behaviour above. Its engine has a fixed torque rating instead of network stress: it bogs and stalls when tension × gear exceeds it at the lowest gear. Fuel use scales with load (near idle while waiting). Natural fit for boats. |

- **Trolling**: holders on Sable ships. The hook follows the rod tip's world position
  (`projectOutOfSubLevel`, server `logicalPose`) and trails behind at line length; crankbaits dive to
  their depth with speed; bites depend on boat speed (`getVelocity`) vs the lure's best speed and on
  the water passed, so you troll across areas. Only while a player is aboard (unmanned ships unload).
  The line is drawn in world space from `renderPose(partialTick)`. Trains/carts are out of scope.
- **Fishing Net** (Integrated Farming): register water/lava catch providers so nets catch our fish
  (biased to small ones).
- Recipes in Create style: reels by sequenced assembly, hooks/lures by pressing, braided line by
  mixing, bench/holders from Create parts. JEI shows everything.

As built in milestone 9, where it differs from or fills in the above: k is 4 SU/RPM per kgf at gear 1 plus
1 SU/RPM idle, so one water wheel (8 RPM, 256 SU) winds against about 7.75 kgf in gear 1 and 31 in the lowest; the
reel turns at its own speed on a 32 RPM shaft (a quarter to three times that on slower or faster ones). The
Geared drive's gearbox shifts by the larger of the fish's pull and what a fish that fresh can still put on, so a
fish easing off for a moment doesn't put it in a gear the next run would stall. Wait is the default mode. The
Fuel drive's engine is rated 12 kgf in gear 1 with a 4 B tank. Machine catches never go into another rod machine,
and the first fish with nowhere to go hangs at the tip for 30 s. Fish kept alive from the hold-up screen are alive
items too, not only those a machine puts on a belt. Trolling: instead of following ship velocity, a machine's rig
simply can't get further from the tip than the line it was set with, so any moving machine tows it (the lure's
speed through the water is then its real speed); clients draw the rod and line through the block entity renderer,
which Sable already runs under the ship's transform.

### 6b. Live-bait bucket and aquarium tanks
- **Live-Bait Bucket**: item holding baitfish/leeches (bundle-style storage, needs water to fill);
  right-click a rod in the inventory with it to load live bait; Geared/Fuel Reel Drives can be fed
  from a bucket placed in their bait slot.
- **Aquarium Tank**: glass tank blocks that join into one rectangular tank. As built (milestone 10) this is
  our own multiblock rather than Create's `ConnectivityHandler`, whose shapes have a square base: an aquarium is
  any box up to 8 on a side, so long, shallow tanks work. The top is open. Release fish from buckets
  into it; they swim inside as client-rendered models (stored in the block entity, not real
  entities, so big tanks cost little), capacity by volume and fish size; take them back out with a
  bucket. Show off live trophies, and **breed baitfish**: two or more of a small species, fed fish
  meal (funnels work), slowly produce baitfish you scoop into the Live-Bait Bucket, so live bait can
  be farmed and automated.

### 7. Tide removal and migration (nothing lost)
As built in milestone 6 (details in README.md, "Ported Tide content", and INTEGRATION.md):
- **Ported content keeps Tide's ids.** This mod registers it under the `tide` namespace with its assets and data
  at `assets/tide`, `data/tide`, so saved items, buckets, swimming fish, displays, journal progress, advancements,
  loot paths and the pack's `tide:` references load unchanged. Tide is declared `incompatible`. Code in
  `dev.creategalore.fishing.tide` (MPL-2.0 notice on every file), copied by `port/port_tide.py` and fixed by hand
  onto our registries, network and fish data; assets and data copied by `port/port_resources.py`.
- **Aliases only for what isn't ported** (`compat/TideAliases.java`): the 12 handed-off species (item, bucket,
  spawn egg, mob) to Aquaculture / Hybrid Aquatic / Darkness Eater; Tide's tier rods to our rods (stone → wooden,
  iron/golden → iron, crystal/diamond → steel, netherite → netherite), its lines to ours by strength, the Angling
  Table to the Tackle Bench, fish bones and slices to Aquaculture's.
- **Rods**: a rod carrying Tide's `fishing_line/hook/bobber/bait_contents` components converts to our parts in a
  player's inventory or at the bench (Tide's defaults fill gaps, a reel matching the rod is added); the 7 special
  rods are our rod blanks with Tide's perks. Vanilla `minecraft:fishing_rod` and Alloyed's steel rod become our
  Wooden / Steel rods at stack creation; recipes that take a fishing rod take ours.
- Tide fish keep `tide:fish_length` (our catch data falls back to it); displays keep their fish and show our
  catch data as a Trophy Mount.
- **Pack changes on this branch**: `tide.pw.toml`, `tide-extra-compatibility.pw.toml` and `pack/config/tide/`
  removed; in `create-galore-tweaks` the 12 fish redirects, 10 spawn overrides, 3 fish-slice recipes and
  `aquaculture/bait/worm.json` removed and the 38 `tide:` entries taken out of `c:hidden_from_recipe_viewers`;
  `tools/build_no_elytra.py` reads our jar; `index.toml` / `pack.toml` refreshed.
- **Bridge**: its 3 Tide mixins deleted from the source and mixin json only. No Bridge version bump or jar
  rebuild, since others are actively working on Bridge.
- Left to the integrators (listed in `INTEGRATION.md`): pack version, CHANGELOG (incl. "back up first; update
  mod and pack together"), MODS.md, README, Field Guide.

## Milestones (each ends with a commit + push to `emre/fishing-mod`)
1. **Scaffold & test rig**: `build.sh`, server install for compiling/testing, empty mod boots on a
   dedicated server and a client; `/cgfish selftest` skeleton.
2. **Fight model + data**: pure-Java `sim` package + Monte Carlo runner; species data format, loader
   and generator; data for all non-Tide fish (ported from Tide's compat + Tide Extra Compat).
3. **Tackle**: parts and data, rod component, Tackle Bench, ratings, wear.
4. **Hook & fight in game**: cast, bites, strike, fight, catch, events/advancements, Aquaculture
   weight, Hybrid hooks.
5. **Client visuals and sound**: bending blocky rod, line, HUD, the hold-up pose and Keep / Release
   screen, fish at the line end, the fishing sounds (§ 2c). Tide is
   still in the pack at this point (our rods run alongside), so it is safe to try on a copy of a real
   world. **→ Stop for Emre's feedback with screenshots.**
6. **Tide port & migration**: species/entities/spawns, journal, trophy mount, satchel, crates, gadgets
   and extras; alias table; rod conversion; Tide and Tide Extra Compat removed; tag cleanup; Bridge
   mixin removal; `incompatible` flag; migration test.
7. **3D fish items** for all mods' fish, icon cache, catch classes polish.
8. **Bait & processing**: Create bait recipes, chum, Live-Bait Bucket, Cleaning Table,
   weight-limited cooking, fish meal/oil + Liquid Fuel, Integrated Farming net providers.
9. **Create & ships**: Rod Holder, Basic / Geared / Fuel Reel Drives with the torque-vs-drag stall
   rule, catch handling (funnel/belt, inventory, dangling), banked XP, alive fish released from belts
   into water, Goggles, trolling on Sable.
10. **Aquarium tanks**: multiblock tanks, swimming display, baitfish breeding.
11. **Handover**: balance pass from the sim, docs, `INTEGRATION.md`, final full-pack test.

## Verification
- **Build**: `tools/fishing-mod/build.sh <server>` compiles cleanly; dedicated server boots with no
  missing-registry / codec / recipe / loot / tag errors; no client classes load on the server.
- **`/cgfish selftest <suite>`** (console, PASS/FAIL to a file): aliases (every Tide id resolves to
  its target; no tag entry resolves through an alias), components (NBT + network round trips),
  species (item, entity, bucket, data present; each fish catchable somewhere by sampling biome,
  depth, time, medium), recipes (plus `tools/datacheck-mod`), fight (model over real data).
- **Monte Carlo** (`sim` compiled alone, fixed seed): CSV per species × tackle tier of land / snap /
  crack / throw rates and fight time; fails outside agreed bands (e.g. starter gear lands panfish
  > 90% and never a big tuna; big-game gear lands tuna 60–80%).
- **Migration test**: a scratch server installed from the base commit (with Tide); a datapack
  function fills chests, shulkers, item frames, fish displays, summons Tide fish, gives a fixture
  player Tide rods with every part kind, bait, sized fish, a filled satchel, journal progress; record
  an item-id census from playerdata and region files. Swap to this branch's pack and boot: zero
  "Tried to load invalid item" / "Skipping Entity" / "Skipping BlockEntity" lines, `/cgfish
  migrationcheck` passes, census matches after mapping through the aliases.
- **Client**: Xvfb + Mesa client joins the test server; scripted screenshots of rods (idle, cast,
  bent), HUD mid-fight (forced via a debug command), 3D fish in hand / inventory / mount, the bench
  preview, a powered holder on a ship. Sent to Emre at milestone 5 and at the end.
- **Reel drives** (command-built test rigs with a debug "fake fish" of chosen pull): a water wheel
  turns a Basic Reel Drive and lands a small fish; the basic drive's reel wears faster than the
  geared one on the same fish with the drag slipping; a high drag stalls a weak network (overstress)
  and it recovers when the pull drops; the geared drive shifts down instead of stalling where it
  can, never reels while line goes out, strikes on its own in Wait mode and retrieves/recasts in Cast
  mode; the Fuel Reel Drive burns fuel in proportion to load and stalls above its torque rating;
  landed fish arrive in the chest; a row of chained drives shares one shaft.
- **Aquarium**: tanks merge/split correctly when placed/broken, fish survive save/load and chunk
  unload, baitfish appear with fish meal fed by a funnel.
- **Performance**: a chest of 54 fish stacks + rods in the hotbar (frame time via spark), 20 reel
  drives on a ship (tick cost via Lag Watch), a 5×5×5 aquarium full of fish (frame time).

## Ideas beyond the brief (not in scope unless Emre wants them)
A hooked monster towing a small boat (Sable impulse at the rod holder), ice fishing through holes,
server leaderboards / tournaments for heaviest fish.
