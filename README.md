# Create Galore

A big, long-term Minecraft modpack built around the Create mod. Think "Minecraft, but better, plus Create": airships, submarines, trains, roller coasters and rockets to the Moon, with survival layers (thirst, temperature, pollution, seasons), homesteading and a lot of new world to explore.

| | |
|---|---|
| **Version** | 0.1.2 |
| **Minecraft** | 1.21.1 |
| **Loader** | NeoForge **21.1.259** (pinned, don't update it, see [Known issues](#known-issues)) |
| **Mods** | 222 from Modrinth + Twilight Forest added by hand, plus 4 shader packs and the Fresh Animations resource pack |
| **Full mod list** | [MODS.md](MODS.md) |
| **In-game help** | The Create Galore Field Guide, a searchable book every player gets on first join |

## Install (players)

1. Download [`Create-Galore-0.1.2.mrpack`](Create-Galore-0.1.2.mrpack) from this repo.
2. Import it:
   - **Prism Launcher:** Add Instance → Import → pick the `.mrpack`.
   - **Modrinth App:** drag the `.mrpack` onto the window (or File → Import).

   The launcher downloads every mod at its pinned version (about 860 MB) and unpacks the pack's configs and datapacks.
3. **Add Twilight Forest by hand.** It isn't on Modrinth, so it can't be inside the pack file. Put `twilightforest-1.21.1-4.8.3345-universal.jar` (CurseForge, version 4.8.3345) into the instance's `mods` folder.
4. Give the game **8 GB of RAM** (instance settings → Java → maximum memory 8192 MB). 10 GB is more comfortable with Distant Horizons.
5. Optional shaders are already in the `shaderpacks` folder: **Complementary Reimagined + Euphoria Patches** (recommended), Bliss, Photon and Solas (lightest). Turn one on in Options → Video Settings → Shader Packs.

Don't let the launcher update NeoForge past 21.1.259.

## Install (server)

A dedicated server takes one script plus two manual steps (add Twilight Forest, accept the EULA). The full guide, including hosting on your own PC vs Exaroton, memory, whitelisting and moving the world between the two, is in **[docs/server-setup.md](docs/server-setup.md)**.

```
cd server
install-server.bat C:\CreateGaloreServer        (Windows)
./install-server.sh ~/CreateGaloreServer        (Linux / macOS)
```

The server gets 181 mods plus Twilight Forest. The 41 client-only mods (Sodium, Iris, Distant Horizons, map add-ons, the ambiance layer and so on) are skipped automatically. Players still install the full `.mrpack`. Far terrain for Distant Horizons is sent from the server by Chunksmith, so Distant Horizons itself stays off the server.

## The Field Guide

Every player gets the **Create Galore Field Guide** on first join, and you can craft another from a Book and Andesite. It's a searchable wiki, not a quest book: 13 sections and about 130 entries on how things work and how the mods connect, written for someone who has never played Create.

Open the Index (last icon on the first page) and type to search. It matches entry names and the items shown in them. Editing notes: [docs/guidebook.md](docs/guidebook.md).

## What's different from the stock mods

Everything below ships as plain config files or a datapack in `pack/` (no mod jars are stored), so a fresh import plays exactly as tested.

**One item per material.** Where several mods added the same thing, one version is now the real one and every other mod's recipes make and use it. Leftover items convert 1:1 in a crafting grid.

| Material | Canonical mod |
|---|---|
| Steel | TFMG |
| Bronze | Alloyed |
| Lead (and lead ore) | Create Nuclear |
| Copper wire and spool | Create Crafts & Additions |
| Sulfur dust | TFMG |
| Salt, cocoa butter, cocoa powder | Create Ratatouille |

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

**Gentler where it should be, harsh where it should be.**
- Cataclysm and Mowzie's bosses have 70% health and 75% damage. All their structures still spawn.
- Hostile Skies airship raids are rarer (every 25 minutes instead of 15, 5% starting chance instead of 10%).
- Cold Sweat runs at 0.75x speed and deals half the temperature damage.
- Create: Smog pollution is left at full strength on purpose.
- Underground structures are spaced a bit wider, since the pack places far more than vanilla.
- Inventory Profiles Next auto-refill is off and the backpack magnet is one block shorter, so the game doesn't play itself.

**Fixes.**
- The Northstar–Aeronautics Dimensional Drive recipe uses a Northstar Circuit instead of a chip Northstar removed, so ships can be launched to the planets again.
- 19 broken recipes from Thirst, Create: Framed (radon lamps) and Create Deco (white placard) are rewritten in the current format.
- Chunksmith no longer pre-generates a 4096-block radius every time you enter a world. Use its commands to pregenerate when you want.
- The Deep Seas setup prompt and the BetterX welcome screen are switched off.

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
| Pinned **Animal Husbandry 0.4.2** | 0.5.0 crashes dedicated servers. |
| Pinned **Cold Sweat: Altitude 0.7.0** | 0.8.x crashes on join with the pack's Cold Sweat 2.4.3.1. |

## Known issues

None of these stop the game.

- **NeoForge must stay on 21.1.259.** On 21.1.262 The Aether's bundled Accessories library kicks players on join. 259 is the newest version that works (the pack needs at least 252). Revisit when The Aether updates.
- **Animal Husbandry is on 0.4.2**, so 0.5.0's rabbit genetics and Angora armor are missing until a fixed version ships.
- **First launch:** Deep Seas' photosensitivity question still shows once. Minecraft's accessibility prompt is turned off by the shipped `options.txt`, but launchers overwrite `options.txt` when the pack updates, which also resets the key rebinds above. If the Deep Seas warning's button sits off-screen in a small window, press Tab then Enter.
- **Log noise:** a few recipes and many loot tables log errors because they point at mods that aren't installed (Botania, Blood Magic, Mekanism, Steam 'n' Rails tracks for other wood mods, and so on). Sable also warns about a few copycat blocks that don't exist. All harmless, and only fixable in those mods.
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
| Real FPS, shaders, Distant Horizons visuals, gameplay feel | ❌ Needs a real PC with a GPU |

**Memory:** about 9.3 GB of process memory on the server and 8-9.6 GB on the client with an 8 GB heap. A laptop with 16 GB can play but shouldn't host and play at the same time.

![In-world screenshot from the test client](docs/in-world.png)

## Repo layout

- `Create-Galore-0.1.2.mrpack`: the importable pack, built from `pack/`
- `pack/`: [packwiz](https://packwiz.infra.link/) source, one `.pw.toml` per mod or shader, plus configs (`pack/config/`), the datapacks (`pack/config/paxi/datapacks/create-galore-tweaks`, always on, `create-galore-no-elytra`, and the opt-in `config/paxi/optional/create-galore-with-elytra`) and the Field Guide (`pack/patchouli_books/`)
- `MODS.md`: full mod list with versions and sides
- `server/`: server install scripts, memory settings and a starting `server.properties`
- `docs/`: server guide, Field Guide editing notes, screenshots
- `tools/`: scripts to extract item and recipe IDs from mod jars, check the Field Guide and rebuild the no-Elytra datapack

To rebuild the `.mrpack` after changing `pack/`:

```
cd pack
packwiz refresh
packwiz modrinth export -o ../Create-Galore-0.1.2.mrpack
```
