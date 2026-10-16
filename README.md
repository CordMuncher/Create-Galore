<img src="docs/icon-512.png" alt="Create Galore icon" width="128" align="right">

# Create Galore

A big, long-term Minecraft modpack built around the Create mod. Think "Minecraft, but better, plus Create": airships, submarines, trains, roller coasters and rockets to the Moon, with survival layers (thirst, temperature, pollution, seasons), homesteading and a lot of new world to explore.

| | |
|---|---|
| **Version** | 0.1.8 |
| **Minecraft** | 1.21.1 |
| **Loader** | NeoForge **21.1.259** (pinned, don't update it, see [Known issues](#known-issues)) |
| **Mods** | 225 from Modrinth + the pack's own small Create Galore Bridge and Lag Watch + Twilight Forest added by hand, plus 4 shader packs and the Fresh Animations resource pack |
| **Full mod list** | [MODS.md](MODS.md) |
| **In-game help** | The Create Galore Field Guide, a searchable book every player gets on first join |

## Install (players)

1. Download `Create-Galore-0.1.8.mrpack` from the [latest release](https://github.com/emresahin0906-code/Create-Galore/releases/latest) (or [from this repo](Create-Galore-0.1.8.mrpack)).
2. Import it:
   - **Prism Launcher:** Add Instance → Import → pick the `.mrpack`.
   - **Modrinth App:** drag the `.mrpack` onto the window (or File → Import).

   The launcher downloads every mod at its pinned version (about 860 MB) and unpacks the pack's configs and datapacks.
3. **Add Twilight Forest by hand.** It isn't on Modrinth, so it can't be inside the pack file. Put `twilightforest-1.21.1-4.8.3345-universal.jar` (CurseForge, version 4.8.3345) into the instance's `mods` folder.
4. Give the game its memory and Java flags in the instance's Java settings: **12 GB with Generational ZGC** on a PC with 32 GB or more, **8 GB with G1** on a 16 GB laptop. Set minimum and maximum memory to the same value. The exact lines to paste for Prism and the Modrinth App, the benchmark behind them and the upper limits are in **[docs/java-flags.md](docs/java-flags.md)**.
5. Optional shaders are already in the `shaderpacks` folder: **Complementary Reimagined + Euphoria Patches** (recommended), Bliss, Photon and Solas (lightest). Turn one on in Options → Video Settings → Shader Packs. For Bliss or Photon, pick the copy whose name ends in **+ Clrwl** (Colorwheel Patcher makes it at launch), so Create contraptions get shaded properly.

6. Optional: give the instance the pack icon. Download `Create-Galore-icon.png` from the release and pick it in the launcher (Prism: click the instance icon; Modrinth App: instance settings). A `.mrpack` can't carry an icon, so launchers show their default one.

Don't let the launcher update NeoForge past 21.1.259.

## Install (server)

**On Windows, use the Server Setup app.** Download `Create-Galore-Server-Setup-<version>.exe` from the [latest release](https://github.com/emresahin0906-code/Create-Galore/releases/latest) and run it. Pick a folder and the memory, drop in the Twilight Forest jar (it shows a green check when it's the right version), tick the EULA and press Install. It installs NeoForge, downloads the server-side mods (or copies them from a mods folder you drop in, skipping client-only ones), applies the pack's configs, memory and whitelist, and writes a `start-server.bat`, which checks for pack updates each start and asks before applying them. If your PC has no Java 21 it downloads one into the server folder. Run a newer version of the app on the same folder to update the server (see [Updating](#updating)).

**Or use the script** (any OS). A dedicated server takes one script plus two manual steps (add Twilight Forest, accept the EULA). The full guide, including hosting on your own PC vs Exaroton, memory, whitelisting and moving the world between the two, is in **[docs/server-setup.md](docs/server-setup.md)**.

```
cd server
install-server.bat C:\CreateGaloreServer        (Windows)
./install-server.sh ~/CreateGaloreServer        (Linux / macOS)
```

The server gets 182 mods, the pack's Create Galore Bridge and Lag Watch, and Twilight Forest. The 42 client-only mods (Sodium, Iris, Distant Horizons, map add-ons, the ambiance layer and so on) are skipped automatically. Players still install the full `.mrpack`. Far terrain for Distant Horizons is sent from the server by Chunksmith, so Distant Horizons itself stays off the server.

The server opens a small window with a live memory graph and the player list, and warns in chat when TPS stays under 15, recording a spark profile you can open later. See [Lag alerts and profiles](docs/server-setup.md#lag-alerts-and-profiles).

## Updating

You don't have to reinstall the pack for a new version. Both the game and the server can update in place: only mods and configs that changed are downloaded or replaced, mods that left the pack are removed, and everything else (Minecraft, NeoForge, every unchanged mod, your worlds) stays as it is. The full `.mrpack` stays the way to make a new instance.

There are two modes. Pick one by adding or removing `--clean`:

| Mode | Command ends with | Configs | Use it while |
|---|---|---|---|
| **Soft update** | `create-galore-updater.jar` | Your edits are kept unless the pack changed that file | playing a world you care about |
| **Clean** ⚠️ | `create-galore-updater.jar --clean` | **Every config is deleted and reset to the pack's on every launch**: `config/`, `defaultconfigs/`, `options.txt` (keybinds, video settings) and each world's `serverconfig/`. Distant Horizons, Subtle Effects and all other settings go back to the pack's or the mod's defaults | hunting bugs and want a clean slate |

Neither mode deletes or re-downloads mods that didn't change, and neither touches Minecraft, NeoForge or your worlds. `--clean` keeps world folders on purpose: a world's own data (terrain, structures, mod saved data) can't be rebuilt from the pack, so for a fully clean world just make a new one. It resets only each world's config folder.

**Game (Prism Launcher), one-time setup.** The pack ships a small updater built on [packwiz-installer](https://packwiz.infra.link/tutorials/installing/packwiz-installer/) that updates the instance from the pack on GitHub each time you press Play.

1. If the instance's `minecraft` folder (Prism: right-click the instance → Folder) has no `create-galore-updater.jar`, download `Create-Galore-Updater.zip` from the [latest release](https://github.com/emresahin0906-code/Create-Galore/releases/latest) and unzip its three jars there. Instances imported from a release that already has the updater have them.
2. Edit the instance → Settings → Custom commands, tick the box and paste this as the **pre-launch command** (add ` --clean` at the end for the clean mode):
   ```
   "$INST_JAVA" -jar create-galore-updater.jar
   ```
3. Press Play. A small window checks the pack. The first run on an instance you already have just checks the files and downloads nothing if they match the latest release. After that, soft launches with no pack change take a couple of seconds. Clean launches fetch the pack's configs (a few hundred small files) each time.

The command reads the pack from GitHub, which only works while the repo is public. To update from a local copy instead (a private repo, or no internet), unzip the release's `Create-Galore-<version>-server-kit.zip` somewhere and put the path of its `pack\pack.toml` after the jar, for example `"$INST_JAVA" -jar create-galore-updater.jar C:\CreateGalorePack\Create-Galore-0.1.8-server-kit\pack\pack.toml --clean`. Unzip a newer kit over it to update.

If the pack can't be read, the updater stops before changing anything. `--clean` moves the configs aside first and puts them back if the update fails, so a failed launch never leaves the instance without configs.

The updater also keeps NeoForge on the pack's pinned version. To freeze the instance on its current version, clear the pre-launch command. If GitHub can't be reached, the window offers to continue without updating.

**What the soft update keeps.** Files the pack doesn't ship are never touched: map and minimap data, `servers.dat`, your worlds, shaders' settings, the Twilight Forest jar and the Elytra switch. Your keybinds and video settings (`options.txt`), Distant Horizons' settings, Subtle Effects' `blocks.toml` and Inventory Profiles Next's settings are kept forever (if the pack ships one of them it is marked `preserve`), so the pack only writes them when they're missing. Any other config you edit stays until the pack itself changes that file; then the pack's version wins, because that's usually a fix. `--clean` throws all of this away, the Elytra switch included.

**Before the first switch**, update the instance to the latest release once if it's older (import the new `.mrpack` as usual). The updater only removes mods it knows it installed, so mods dropped before the switch would linger. Tested with a real 0.1.5 instance (227 files, edited keybinds and Subtle Effects, a Distant Horizons config, a world): the switch checked every file in about 4 seconds, downloaded nothing and changed nothing; an update then removed a dropped mod, fetched one changed and one new config, kept the edited files and moved NeoForge in the instance to the pack's version. A `--clean` run then deleted the configs and the world's `serverconfig/`, put back the pack's 293 config files, downloaded no mods and left the world in place.

**Modrinth App:** instance → Settings → Launch hooks → Pre-launch, the same command with `java` (or the full path to a Java 21 `java.exe`) in place of `"$INST_JAVA"`, after putting the three jars in the instance folder. Prism is the tested route.

**Server.** `start-server` checks for a newer pack every time it starts and asks in the console: no, yes, or back up the world first and then update. It never updates on its own. Details in [docs/server-setup.md](docs/server-setup.md#update-check-at-start). To update by hand, run the newer Server Setup app on the same folder; its button reads "Update server" when it finds one there. It keeps mods whose checksum still matches, downloads the changed ones, removes the ones the pack dropped and keeps config edits the pack didn't change. Tick **Reset all configs to the pack's** for the clean mode: it deletes `config/`, `defaultconfigs/` and the world's `serverconfig/` before installing, and keeps the world, `server.properties`, memory, whitelist and ops. The scripts do the same: run `install-server` again from the new server kit, with `--clean` after the folder for the clean mode. They also reinstall NeoForge if the pack moves to a new version. Details in [docs/server-setup.md](docs/server-setup.md#updating-the-pack).

## The Field Guide

Every player gets the **Create Galore Field Guide** on first join, and you can craft another from a Book and Andesite. It's a wiki, not a quest book: 14 sections and about 140 entries on how things work and how the mods connect, written for someone who has never played Create.

A new player should open **Start Here > Your First Days** first: how to drink, where to find clean water, food and staying warm, the things that can kill you before you know the pack.

Each section is a map. Entries are nodes, and the arrows show what comes first, so to reach something you follow its lines back (the six prizes all lead into the Stellar Core, for example). Everything is open from the start, with no locks, tasks or rewards. The magnifying glass on the right searches the text of every page. Editing notes: [docs/guidebook.md](docs/guidebook.md).

In a world started before the guide moved to Modonomicon, the old Patchouli Field Guide no longer opens it: put it in a crafting grid on its own to turn it into the new one.

![The Road to the Stars section: the six prizes lead into the Stellar Core](docs/field-guide.png)

## What's different from the stock mods

Everything below ships as plain config files or a datapack in `pack/` (no mod jars are stored), so a fresh import plays exactly as tested.

**One item per material.** Where several mods added the same thing, one version is now the real one and every other mod's recipes make and use it. Leftover items convert 1:1 in a crafting grid.

| Material | Canonical mod |
|---|---|
| Steel | TFMG |
| Bronze | Alloyed |
| Lead (and lead ore) | Create Nuclear |
| Copper wire | Create Crafts & Additions |
| Sulfur dust | TFMG |
| Salt, cocoa butter, cocoa powder | Create Ratatouille |

Copper spools stay separate: TFMG's spool lays TFMG cable and is the only kind its Winding Machine takes, so TFMG and Crafts & Additions each keep their own.

**Duplicates trimmed.**
- Naturalist's 9 sea creatures that Hybrid Aquatic already has don't spawn, and their fish items are hidden from JEI. Naturalist's whales, clams and land animals are unchanged.
- Critters and Companions' otter, Dumbo Octopus and Sea Bunny are off (Hybrid Aquatic covers them).
- Create Connected's copycats are off in favour of Create: Copycats+. The rest of Create Connected stays.

**You travel by building.** Trains, airships, submarines, cars and hypertubes are the way around, so shortcuts that make them pointless are off. Sidegrades that cost something (Ender Pearls, a double jump, short Aether flights, the air-limited Jetpack) stay.
- **No Elytra.** End City ships no longer get one, BetterEnd and Dungeons and Taverns loot gives Shulker Shells instead, and Incendium's paratroopers stop dropping theirs. If one still reaches a player in survival (old chunks, a mod we missed), it crumbles into 2 Phantom Membrane with a chat message. The Create Jetpack recipe takes a Shulker Shell where it used to take an Elytra. Iron's Spells' Angel Wings (Elytra flight as a spell) is off too.
- **Iron's Spells:** Portal (permanent two-way teleport links) and Recall (home from anywhere) are off, and Portal Frames and Create Wizardry's Hither-Thither Wand can't be crafted. The spell switches live in `config/irons_spellbooks_spell_config/irons_spellbooks/`. Iron's applies them as soon as the first player joins a world, with no reload needed.
- **Artifacts:** the Warp Drive (endless free Ender Pearls) no longer appears in loot and does nothing if you have one.
- **Alex's Caves:** the Subterranodon, a tamed flyer with no real limit on level flight, doesn't spawn. The Hullbreaker no longer drops the Enigmatic Engine, so its fuel-free Submarine can't be built (wrecks in the Abyssal Chasm ruins can still be repaired).

**Want Elytra back?** It's one switch for the whole world, set by whoever runs it (the singleplayer instance or the server). Stop the game or server, then move the folder `config/paxi/optional/create-galore-with-elytra` into `config/paxi/datapacks/`, and start again. It loads after `create-galore-no-elytra` and puts every original file back: Elytra return in End Cities you haven't explored yet and in loot, the old Jetpack recipe comes back, and nothing gets removed from inventories. Angel Wings stays off. Pack updates don't undo it, because the updater leaves folders it didn't install alone. To turn Elytra off again, delete that folder from `datapacks/`. The other travel switches stay off either way. `tools/build_no_elytra.py` rebuilds both folders from the mod jars after mod updates.

**Space is the capstone.** Rockets and space ships come last. Every way off the planet (the Northstar Rocket Station, the Dimensional Drive and the Stellar Navigator) is built around a **Stellar Core**, the pack's own item, made in a 5x5 Mechanical Crafter from one prize per branch of the world:
- an Immortal Embryo (a Hullbreaker in Alex's Caves' Abyssal Chasm, deep under the ocean)
- 4 Fiery Ingots (Twilight Forest's Hydra or Ur-Ghast)
- 4 Enchanted Gravitite (the Aether)
- a Nether Star (the Wither)
- 4 Dragonskin (the Ender Dragon, via Iron's Spells)
- 2 TFMG Circuit Boards (oil, plastic and acid industry)
- plus a Hardened Precision Mechanism and 4 Titanium Plates

Nothing on the list comes from space, every prize can be found again, and nothing on Earth needs a space item, so there's no dead end. The Field Guide's **The Road to the Stars** section explains why each prize is needed and where to get it, and an advancement tab of the same name ticks them off. One core builds one station or drive, and those are reused for every trip.

**Gentler where it should be, harsh where it should be.**
- Cataclysm and Mowzie's bosses have 70% health and 75% damage. All their structures still spawn.
- Hostile Skies airship raids are rarer (every 25 minutes instead of 15, 5% starting chance instead of 10%).
- Cold Sweat runs at 0.75x speed and deals half the temperature damage.
- Thirst: you can drink straight from water by crouching and right-clicking it with both hands empty (Thirst Was Reclaimed has this off by default). Raw water still makes you sick, so bottles and boiling stay the real answer. Drinking rain (look straight up) also works.
- Create: Smog pollution is left at full strength on purpose.
- Underground structures are spaced a bit wider, since the pack places far more than vanilla.
- Inventory Profiles Next auto-refill is off and the backpack magnet is one block shorter, so the game doesn't play itself.

**One fair route per item.** Where two mods make the same thing and one way gives far more for far less work, that way is cut or toned down, so the Create machine route stays worth building. A better yield that costs a bigger build (TFMG's blast furnace makes 2 steel per raw iron, a mixer makes 1 per ingot) is left alone. All of it is recipe and tag overrides in `create-galore-tweaks`; the Field Guide lists them under How Things Connect → Balance Changes.
- **Metals.** Steel always needs heat: Create Nuclear's coal-dust steel mix is heated now, and the Alloyed Forge no longer makes steel or bronze (its tools and armor are unchanged). Big Cannons' cheaper bronze mixes are off, so bronze comes from Alloyed's mixer recipe. BetterEnd's smelter no longer turns 2 ancient debris into 3 scrap. Ancient debris deposits now need a Netherite Drill, 500 mB lava per cycle and 2,048 SU (they were 33x faster than Ore Excavation's own netherite vein with any drill).
- **Recipe clashes.** Five mods had a crushing recipe for plain Limestone and only one can run, which could hide TFMG's Limesand (the steel flux). Limestone now crushes to Limesand only; Northstar's rutile still comes from sand and Venus gravel, Create Nuclear's nitrate from crushing cut or polished limestone, and Garnished's crushed salt from milling. Create Nuclear's faster raw copper and zinc crushing (with free lead) is off, so Create's own recipe runs.
- **Parts.** Alloyed's one-press steel sheet is off, so steel plates are TFMG's 3-press Heavy Plate. Flight Control's ion vector thrusters can't be made by converting a plain thruster in the grid (that skipped 3 accumulators). Copper wire comes from the Rolling Mill, not 2 per ingot at the stonecutter. Copycat Wings stonecut 1:1 instead of 1:4.
- **Food and farming.** Ratatouille's composter no longer triples dough, its kernel milling gives about 1.25 flour instead of 3 (wheat still gives about twice Create's plain mill), and popsicle molds take 250 mB of melon juice instead of 100. Delightful Creators' tomato sauce takes 2 tomatoes per 250 mB instead of 1 per 333. My Nether's Delight can't stretch blaze powder with pepper powder, and channeling a sponge gives exactly one wet sponge (it was an endless 1.25x loop).
- **Fuel.** Garnished Peanut Oil burns 5,000 ticks a bucket in the Crafts & Additions burner too (it was 24,000, a cheap superheat fuel next to Bioethanol).
- **Loops.** Garnished stone slabs (3 blocks make 6) no longer stonecut back into full blocks or crush for ore, which doubled unstable stone and zultanite and their gold, zinc, iron and copper.
- Create Wizardry's Create recipe for Iron's Portal Frame is off as well, matching the travel rules.
- **Productive Bees.** Bees still make honey, wax, plain metals (iron, copper, gold, zinc, lead, nickel), gems and cheap mob drops. The combs that skipped whole progression chains give nothing from the centrifuge: steel, brass, constantan, rose quartz, titanium, tungsten, platinum, uranium, netherite, mithril, ender pearls, blaze and breeze rods, ghast tears, echo shards, wither skull chips, draconic dust, sulfur and saltpeter.
- **Ore deposits.** Deposit drills mine diamonds, emeralds, quartz and glowstone at Ore Excavation's own speed (1,200 ticks, 512 SU for gems). Metals stay fast.
- **Twilight Forest:** the Uncrafting Table no longer reverses recipes (`disableUncrafting = true`), since stonecutter recipes let it multiply copper, bronze and Create Deco metal up to 4x. It still repairs and does its special recipes.
- **Create Propulsion:** Solid and Liquid Burners can't heat mixers or superheat boilers. They still heat boilers to normal level and run thrusters and Stirling engines.
- **Pollution:** TFMG engines (6 per block at full throttle, 4 for large engines), fireboxes (8) and blast furnaces (30) add smog while they run. The pack's small Create Galore Bridge mod tells Smog when they're actually burning fuel, so an idle, empty or switched-off machine is clean and a throttled engine pollutes less. Blaze Burners work the same way: 16 per second while fuelled, 32 superheated, nothing when the blaze has no fuel.
- **Smaller ones:** Farmer's Delight melon juice and apple cider hydrate like a water bottle (6 and 8 instead of 8 and 13). Create Wizardry's Mana Siphon never turns an arcane ingot into netherite. Ferrets dig up an emerald instead of a Totem of Undying. Crushing granite gives uranium powder 10% of the time instead of 50%. A Biomass Pellet takes 2 Biomass.

**Fixes.**
- Create's Blaze Burner makes smog. Smog's default list named `create:lit_blaze_burner`, the decorative flint-and-steel burner, which it can never see, so the real burner (and with it most Create factories) was pollution-free.
- The Northstar–Aeronautics Dimensional Drive recipe no longer asks for a chip Northstar removed (it now takes a Stellar Core, see above), so ships can be launched to the planets again.
- 19 broken recipes from Thirst, Create: Framed (radon lamps) and Create Deco (white placard) are rewritten in the current format.
- TFMG's diesel, gasoline, kerosene, naphtha and heavy oil can be piped into a Blaze Burner again, and napalm superheats it (TFMG used Create Liquid Fuel's old file name).
- Create Cafe's fruit teas work with the pack's fruits. Fruits Delight, Berries and Cherries, Twilight Forest and Northstar fruits now count for 16 teas (orange, lemon, kiwi, lychee, mango, peach, persimmon, pineapple, fig, durian, blueberry, raspberry, blackberry, strawberry, cherry, grape) plus the raspberry and strawberry syrups. Blood Orange comes from haunting an orange with an Encased Fan, and Mana Berries from filling sweet berries with 25 mB of liquid experience from Create: Enchantment Industry. Before, both needed Blood Magic or Botania.
- My Nether's Delight adds its loot to bastion treasure and hoglin stable chests again (golden eggs, hoglin trophies, strider eggs and so on).
- The chest in Aeronautics Structures' engineering house is no longer empty; its leather uniform is now navy blue instead of a random colour.
- **Quieter chat on join.** Eight kinds of notice no longer fill chat when you join: Creat Aeronautics Structures' Telegram channel ad (`config/cr_aero_structures-client.toml`); Alex's Caves' warning that its world generation clashes with Tectonic (`warn_generation_incompatibility` in `config/alexscaves-general.toml`; a test world with the same seed found its cave biomes about as often with Tectonic as without it); Terralith's welcome message (`config/terralith.json`); one "Discovered ... at ..." line per ore deposit, about 50 in a new world (`discovery_chat` in `config/coedeposits-common.toml`; the deposits still show as map markers); and Distant Horizons' "Alex's Cave detected" and G1 garbage collector notes (`config/DistantHorizons.toml`). DH's file is `preserve`, so existing instances keep their DH settings: switch both off under DH settings → Advanced → Logging. The fix DH asks for is shipped too: Alex's Caves' coloured ambient light in toxic caves is off (`biome_ambient_light_coloring` in `config/alexscaves-client.toml`), because it stops DH from drawing far terrain. Create Train Utilities (its v4 rename) and Create Aeronautics: Transmission & Linkage (a Toolgun recommendation) have no setting, so the pack's Create Galore Bridge 1.4.0 drops just those two lines on the client.
- **Bliss and Photon work with Colorwheel.** Colorwheel (Create contraptions under shaders) only supports shader packs that carry its patch, so Bliss and Photon showed a red "not compatible" alert. Colorwheel Patcher now writes a patched copy of each at launch, named `... + Clrwl_1.0.5.zip`; pick that one. Photon is held at **v1.3a**, the newest version the patcher knows (v1.3b only fixed a setting on Minecraft 1.21.11). Complementary + Euphoria Patches and Solas support Colorwheel on their own.
- Chunksmith no longer pre-generates a 4096-block radius every time you enter a world. Use its commands to pregenerate when you want.
- **No more server freezes while flying into new terrain.** Every 5 seconds Aeronautics Winds put snow, sand and puddles in random spots up to 32 blocks around each player, and looked up the ground height there even when that chunk wasn't generated yet. The server then generated it on the spot and waited, which froze it for up to 20 seconds and rubber-banded fast fliers, worst while Chunksmith was pregenerating. The pack's Create Galore Bridge (1.2.0) now skips that round until the area around the player is loaded; it simply runs again 5 seconds later.
- **Chunksmith pregeneration yields to players.** Its defaults let a pregen run slow the server to 150 ms per tick (about 7 TPS) and keep 200 chunks in flight, so anything else that needed a chunk waited behind them. Now it backs off above 54 ms per tick (60 at most), keeps at most 32 chunks in flight, and pauses itself after 30 seconds of struggling (`config/chunksmith/config.json`). For a faster run while nobody plays, see [docs/java-flags.md](docs/java-flags.md#things-you-might-not-have-thought-of).
- **10-bit monitors and NVIDIA.** On an NVIDIA card the driver treats a game window that fills the screen as exclusive fullscreen, so a 10-bit or HDR monitor switches to 8-bit on Alt-Tab and the Snipping Tool kicks you out. Fix it once in the NVIDIA Control Panel: Manage 3D settings → Program Settings → add `javaw.exe` from the Java your launcher uses (or use Global Settings) → **Vulkan/OpenGL present method** → **Prefer layered on DXGI Swapchain** → Apply, then restart the game. **Catch:** with shaders on, this setting held an RTX 4080 Super at about 55% GPU use (100-140 FPS where 200 was expected); setting it back to Auto gave 100% GPU use again. The pack no longer ships Borderless Window (see [Mods removed](#mods-removed-or-swapped-and-why)); F11 is the game's normal fullscreen.
- **Distant Horizons draws from closer in** (Overdraw Prevention 0.1 instead of auto), so terrain the server hasn't sent yet shows as far terrain instead of a hole. This ships in `config/DistantHorizons.toml` only for new instances; existing ones keep their DH settings (set it under DH settings → Advanced → Graphics → Culling).
- **Frosty breath only where it's cold.** Subtle Effects showed breath in every biome, deserts included, from late autumn to early spring of Serene Seasons, and new worlds start in early spring. It now follows the biome only (snowy biomes and high peaks).
- **No water droplets running down the screen.** Cold Sweat drew trickling droplets over your view every time you came out of water. That overlay is off now; wet players still drip water particles (`config/coldsweat/client.toml`, Show Water Effect 1).
- The Deep Seas setup prompt and the BetterX welcome screen are switched off.
- The title screen shows the pack's own panorama again. Aquamirae's themed menu was drawn over it (`config/obscuria/aquamirae-early.json`).

**Key rebinds** (only these keys are changed): Backpack J, Jetpack toggle N, Mobile Packages stock ticker `.` and networks `;`, Accessories `,`, Cataclysm abilities Home/End/Insert/Delete, Aether invisibility `=`, Supplementaries quiver `` ` ``, Iris shader reload F8, Railways Navigator overlay options `\`.

## Ambiance (client only)

A set of sound and small-visual mods that make the world feel more alive without changing how anything plays. All of them are client-only, so the dedicated server is untouched.

| Mod | What you notice |
|---|---|
| Sound Physics: Aeronautics | Echo in caves and halls, muffled sound through walls. It's the fork of Sound Physics Remastered built for this pack's mods: Create machines, trains and Aeronautics ships block and carry sound too, with a Doppler effect on passing craft. |
| AmbientSounds | Wind, birds, insects, water and cave ambience that follow biome, time and weather. |
| Presence Footsteps (+ Sable compat) | Footsteps that match the block and the mob, also on Aeronautics ships. |
| Sounds | Small UI, item and block sounds (inventory, hotbar, bow pull, cake and more). |
| Drip Sounds | Water and lava drips make a sound when they land. |
| Subtle Effects | Sparks from fires, waterfall mist, fireflies, steam, dust clouds, snoring, and more small particles. |
| Falling Leaves | Leaves drift from trees, more in autumn (reads Serene Seasons). |
| Make Bubbles Pop | Underwater bubbles rise and pop at the surface. |
| LambDynamicLights + Contraption Lights | Held torches and glowing items light up the area; lights on Create contraptions and Aeronautics ships move with them. |
| Not Enough Animations | Third-person animations for eating, maps, ladders and more. |
| Create Galore title screen | The pack's own menu backdrop: a spinning pixel-art scene with a Create town, a train on a viaduct, a submarine, a farm and airships. It's a small built-in resource pack (`create-galore-menu`), on by default; sources in [tools/art](tools/art).; [screenshot](docs/title-screen.png) |
| EMF + ETF + Fresh Animations | Livelier vanilla mob animations (blinking, breathing, head turns). Fresh Animations is turned on by default in the resource pack list. |

**Kept from doubling up.** Subtle Effects' chest and barrel underwater bubbles and its cake-eating sound are turned off (`pack/config/subtle_effects/blocks.toml`), because Make Bubbles Pop and Sounds already do them.

**Looked at and left out.** Particle Rain (shaders already draw rain and snow, and Serene Seasons switches them), Continuity (needs Sinytra Connector on NeoForge), Fancy World Animations (Supplementaries already swings lanterns, and it would also animate modded doors), Better Clouds (shaders and Distant Horizons draw the sky), Cool Rain and Euphonium (AmbientSounds covers them). Wakes, Visuality, Eating Animation and the original Falling Leaves have no NeoForge 1.21.1 build. Sodium Dynamic Lights was swapped for LambDynamicLights because it predates this pack's Sodium 0.8.

**Tips.** Sound Physics: Aeronautics runs a medium quality preset; turn it down in its config on the laptop if sound stutters. With a shader that has its own handheld light (Complementary + Euphoria Patches has one), you can switch one of the two off if held torches look too bright.

## Mods removed or swapped, and why

| Change | Why |
|---|---|
| Thirst Was Taken → **Thirst Was Reclaimed 3.0.6** | Drop-in replacement that works with Cold Sweat. Fruits Delight juices still count as drinks. |
| Create: AeroStar → **Create: Northstar–Aeronautics Compatibility** | AeroStar crashed with Northstar 0.6. The compat mod does the same job. |
| Removed **Create: Interiors** | Its cushions duplicated Handcrafted's. |
| Removed **Mama's Herbs and Harvest** | Its crops and foods duplicated the Delight mods. |
| Removed **Abyss** | A second abyss dimension next to the one Create: Deep Seas ships (which is off in normal play). |
| Removed **Create: Aeronautics Structures** (small one) | Creat Aeronautics Structures covers it with 12 structures instead of 2. |
| Removed **Create: Diesel Generators** | Overlaps TFMG's oil and diesel chain. |
| Removed **Smarter Contraption Storage** | Crashed alongside Sophisticated Backpacks' own Create integration, which does the same job. |
| Removed **Chunky** | Crashes Distant Horizons at world creation. Chunksmith replaces it. |
| Removed **Create: Ballast** | Its jar refuses NeoForge 21.1.227+, which Aeronautics needs. Deep Seas has ballast tanks for subs. |
| Removed **Create: Northstar Structures** | Built for the old Northstar 0.5. The world won't load with Northstar 0.6. |
| Left out **Create: Northstar Chemistry** | 1.0.0 crashes on startup with Northstar 0.6.6. Add it back when it updates. |
| Removed **Borderless Window** | It did nothing on NVIDIA: the driver still treated its window as exclusive fullscreen, and the NVIDIA present setting below keeps a 10-bit monitor at 10-bit with or without it. One less mod to suspect when something goes wrong. |
| Pinned **Animal Husbandry 0.4.2** | 0.5.0 crashes dedicated servers. |
| Pinned **Cold Sweat: Altitude 0.7.0** | 0.8.x crashes on join with the pack's Cold Sweat 2.4.3.1. |

## Known issues

None of these stop the game.

- **NeoForge must stay on 21.1.259.** On 21.1.262 The Aether's bundled Accessories library kicks players on join. 259 is the newest version that works (the pack needs at least 252). Revisit when The Aether updates.
- **Animal Husbandry is on 0.4.2**, so 0.5.0's rabbit genetics and Angora armor are missing until a fixed version ships.
- **First launch:** Deep Seas' photosensitivity question still shows once. Minecraft's accessibility prompt is turned off by the shipped `options.txt`. Re-importing a `.mrpack` over an instance overwrites `options.txt` and resets the key rebinds above; the in-place updater (see [Updating](#updating)) keeps it. If the Deep Seas warning's button sits off-screen in a small window, press Tab then Enter.
- **Letters missing after opening the resource pack screen.** On an RTX 4080 Super, leaving the resource pack screen once left most letters blank in every menu. ImmediatelyFast's font atlas resizing is turned off in `config/immediatelyfast.json` to stop it. If it ever happens anyway, restart the game and the letters come back.
- **Log noise:** a few recipes and many loot tables log errors because they point at mods that aren't installed (Dye Depot colours for Design n' Decor and Create Connected, Steam 'n' Rails tracks for other wood mods, Botania, Blood Magic, Mekanism and so on). Sable also warns about a few copycat blocks that don't exist, and TFMG still ships its Blaze Burner fuel list under Create Liquid Fuel's old name (the pack's datapack adds it under the new one). All of this was checked line by line: none of those mods would add anything worth having, and the leftover lines are harmless.
- **Black strobing with the DXGI present method (some GPUs and drivers).** After setting NVIDIA's Vulkan/OpenGL present method to Prefer layered on DXGI Swapchain (see 10-bit monitors and NVIDIA above), the whole screen can strobe black. Seen on an RTX 4080 Super; it may not happen on your card or driver. It only happens with VSync (or adaptive VSync) on **and** Max Framerate set to Unlimited. Any one of these fixes it:
  - Turn VSync off and keep Max Framerate at Unlimited.
  - Keep VSync on and set Max Framerate in Video Settings to your monitor's refresh rate or lower.
  - Keep Max Framerate at Unlimited, VSync off, and cap FPS with an external limiter such as RivaTuner Statistics Server. Minecraft's own limiter can stutter and may not reach its cap, so this gives the smoothest frame times.
- **First world creation is slow** (about 45 s for the spawn area on the test machine) because of all the world-gen mods. Normal after that.

## What was tested

All tests ran headless in a cloud machine (4 cores, 16 GB RAM, no GPU), so speed numbers are pessimistic.

| Test | Result |
|---|---|
| Dedicated server built by `server/install-server.sh` boots | ✅ About 2-2.5 min, clean shutdown |
| World generation in the Overworld, Moon, Mars, Nether, End and Twilight Forest | ✅ No errors |
| Full client joins that server and visits the Overworld, Moon, Nether, Aether and Twilight Forest | ✅ No mod mismatch, 20 TPS |
| Singleplayer: client boots and plays in a world | ✅ |
| Ambiance mods: client boots into a world with a sound device, Sound Physics' EFX starts, a running Create fan, animals, villager and held torch render and play with no new errors | ✅ |
| Field Guide from a fresh `.mrpack` import: handed out, opens, search and links work, recipe shows in JEI | ✅ |
| 0.1.2 travel changes on a dedicated server with a client joined: End City ship generates without its Elytra, mod loot and Lootr give Shulker Shells, the Hullbreaker drops no Enigmatic Engine, an Elytra given to a survival player crumbles into Phantom Membrane, 527 random spell scrolls with no Portal, Recall or Angel Wings, the opt-in folder brings Elytra back, Field Guide opens on the new Getting Around page | ✅ |
| Combined 0.1.2 (ambiance and travel changes together) on a fresh dedicated server with the full client joined and sound on: a `/reload` while playing with Sound Physics running causes no errors, an Elytra crumbles by default, the opt-in folder keeps Elytra after a restart, Field Guide opens | ✅ |
| Without the automatic `/reload`: on a fresh dedicated server, after a restart, after a manual `/reload` and in a new singleplayer world, about 1,100 random spell scrolls per run once a player has joined, none of them Portal, Recall or Angel Wings, and the client receives the same switches | ✅ |
| Log-noise fixes on a dedicated server, boot and two `/reload`s: 22 more recipes load (16 Create Cafe teas, 2 syrups, blood tea, mana tea, Blood Orange, Mana Berries), and the My Nether's Delight, Aeronautics Structures, blood tea and syrup errors are gone. In-world, a pump feeds TFMG diesel into a Blaze Burner and it burns, while water is refused | ✅ |
| 0.1.3 balance pass on a dedicated server: boots clean, 37 fewer recipes load (22,338 together with the log-noise fixes), configs load without corrections, no recipe, tag or Field Guide errors, Garnished stone tags list no slabs | ✅ |
| Create Galore Bridge on a dedicated server, over 20 s per check: an idle regular engine and an idle large engine add no smog; a large engine running on diesel (shaft at 128 RPM, fuel draining) adds smog, and stops adding it once its air runs out with diesel still in the tank; a blast furnace adds smog while smelting and none once the charge stops | ✅ |
| 0.1.4 Blaze Burner smog on a dedicated server: a burner with no fuel adds no smog, a fuelled one adds 16 per second and a superheated one 32 | ✅ |
| 0.1.4 Field Guide in-world: the book loads with all 13 categories and no errors of its own, the changed pages show the new text | ✅ |
| 0.1.4 title screen: the Create Galore panorama shows instead of Aquamirae's menu | ✅ |
| 0.1.5 space gate on a dedicated server with the full client joined: the Stellar Core, Rocket Station, Dimensional Drive and Stellar Navigator recipes load (JEI shows the core in all four, and the Rocket Station has no other recipe), a Hullbreaker's loot gives an Immortal Embryo, a killed Ender Dragon drops Dragonskin, all 8 Road to the Stars advancements unlock (each prize, the core, and arriving on the Moon), the Field Guide loads with all 14 sections and the new pages render | ✅ |
| Lag Watch on a dedicated server, with lag forced by summoning thousands of zombies: under 15 TPS for 10 s gives the chat alert with an entity count ("15,162 Zombie in the Overworld"), a 60 s spark profile of only the slow ticks is saved to `config/spark/lagwatch-*.sparkprofile` and spark's background profiler restarts after it, `logs/lagwatch.log` gets the snapshot, and the all-clear comes once TPS is back over 18. `/lagwatch test` works at full speed. The server window (memory graph, player list) opens on start | ✅ |
| Field Guide on Modonomicon in a new singleplayer world: handed out on join, all 14 section maps draw with their arrows, search finds page text, entry links open, a `/reload` picks up edited pages | ✅ |
| 0.1.6 playtest fixes on a dedicated server with the full client joined and flying in creative while `/cs start` pregenerates: with 0.1.5's settings the server froze for 20 s (Aeronautics Winds loading chunks on the main thread) and kicked the player back with "moved too quickly"; with Bridge 1.2.0 and the gentler Chunksmith settings there was no rubber-banding and Winds no longer shows up in the stalls (worst hitch 8.4 s right at join on a 4-core test box running server and client together, then 2.5 to 5.5 s). Borderless Window, the Distant Horizons overdraw setting and the frosty-breath setting load in the client and keep their values | ✅ |
| Update check at server start, on a server built with `install-server.sh` (182 mods, Twilight Forest, a generated world): with the pack on GitHub changed it showed the N/Y/B prompt; N started the server unchanged; B zipped the world, mods and configs (776 MB) into `backups/`, updated to 0.1.7 with packwiz-installer, and the updated server booted the same world. Up to date, offline and no-console runs start the server without asking; a failed update stops the start. The app's new `--update` (what app servers run) kept memory, port, Elytra and an edited config | ✅ |
| Field Guide pass in the full client (singleplayer, NeoForge 21.1.259): Bridge 1.3.0's title mixin applies to Modonomicon, the text box stays clear of the spine and page edges (same Tankard page as the bug report), paragraph gaps show, Your First Days opens from Start Here, and crouch + right-click on water with empty hands raised thirst 6 → 15 | ✅ |
| 0.1.8 join chat in the full client (singleplayer, fresh world): Colorwheel Patcher writes `Bliss_v2.1.2_(Chocapic13_Shaders_edit) + Clrwl_1.0.5.zip` and `photon_v1.3a + Clrwl_1.0.5.zip` at launch; Bridge 1.4.0 removes the Train Utilities and Toolgun notices; the Telegram ad, Alex's Caves Tectonic warning, Terralith welcome, deposit lines and DH Alex's Caves note are gone; only advancements remain. Alex's Caves on a dedicated server, same seed with and without Tectonic: `/locate biome` found 3 of the 6 cave biomes each time | ✅ |
| Real FPS, shaders, Distant Horizons visuals, gameplay feel | ❌ Needs a real PC with a GPU |

**Memory:** about 9.3 GB of process memory on the server and 8-9.6 GB on the client with an 8 GB heap. A laptop with 16 GB can play but shouldn't host and play at the same time. A garbage collector benchmark on the server (pack's G1 flags vs Java defaults, Generational ZGC and Shenandoah, plus a 6 GB run) is in [docs/java-flags.md](docs/java-flags.md).

![In-world screenshot from the test client](docs/in-world.png)

## Repo layout

- `Create-Galore-0.1.8.mrpack`: the importable pack, built from `pack/`
- `pack/`: [packwiz](https://packwiz.infra.link/) source, one `.pw.toml` per mod or shader, plus configs (`pack/config/`), the datapacks (`pack/config/paxi/datapacks/create-galore-tweaks`, always on, `create-galore-no-elytra`, and the opt-in `config/paxi/optional/create-galore-with-elytra`) and the Field Guide (a Modonomicon book inside `create-galore-tweaks`)
- `MODS.md`: full mod list with versions and sides
- `server/`: server install scripts, memory settings and a starting `server.properties`
- `docs/`: server guide, Field Guide editing notes, screenshots
- `tools/`: scripts to extract item and recipe IDs from mod jars, check the Field Guide and rebuild the no-Elytra datapack
- `tools/updater/`: the pre-launch updater (`create-galore-updater.jar`, plain Java): runs packwiz-installer against the pack on GitHub, and with `--clean` resets every config first
- `tools/bridge-mod/`, `tools/lagwatch-mod/`: source and build scripts for the pack's two small mods
- `tools/server-setup/`: the Windows Server Setup app (C#, .NET Framework 4.8, so it needs nothing installed). `tools/build_server_bundle.py` turns `pack/` into the mod list and config bundle the app carries, so the app always matches the pack it was built with
- `.github/workflows/release.yml`: builds the GitHub release (the `.mrpack`, the Server Setup app, a server kit zip with the scripts and docs, and this README) whenever the pack, server files, app or docs change on `main-6fi2ef`. The release is tagged with the pack version; pushing again without a version bump replaces that release's files

To rebuild the `.mrpack` after changing `pack/`:

```
cd pack
packwiz refresh
packwiz modrinth export -o ../Create-Galore-0.1.8.mrpack
```

The release workflow then adds `packwiz-installer-bootstrap.jar` and `packwiz-installer.jar` (pinned, hash-checked) and `create-galore-updater.jar` (built from `tools/updater/` by `tools/updater/build.sh`) to the `.mrpack`'s `overrides/` so new instances can use the pre-launch updater. A config players are expected to tweak in game gets `preserve = true` under its entry in `pack/index.toml` (`packwiz refresh` keeps the flag), so the updater never overwrites it.
