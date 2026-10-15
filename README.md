# Create Galore

A big, long-term Minecraft modpack built around the Create mod: airships, submarines, trains, the Moon, roller coasters, survival layers (thirst, temperature, pollution, seasons), homesteading and a lot of new world to explore.

- **Minecraft** 1.21.1
- **Loader** NeoForge **21.1.259** (pinned on purpose, see "Changes" below)
- **Mods** 210 from Modrinth + Twilight Forest added by hand, plus 4 shader packs
- Full list with versions: [MODS.md](MODS.md)

## How to install

1. Download [`Create-Galore-0.1.0.mrpack`](Create-Galore-0.1.0.mrpack) from this repo.
2. **Prism Launcher:** Add Instance → Import → pick the `.mrpack` file.
   **Modrinth App:** drag the `.mrpack` onto the window (or File → Import).
   The launcher downloads every mod at the exact pinned version, about 860 MB. It also unpacks the pack's config tweaks (see "Config tweaks" below).
3. **Add Twilight Forest by hand.** It isn't on Modrinth, so it can't be inside the pack file. Put
   `twilightforest-1.21.1-4.8.3345-universal.jar` (CurseForge, version 4.8.3345) into the instance's `mods` folder.
4. Give the game at least **8 GB of RAM** (instance settings → Java → maximum memory 8192 MB; 10 GB is more comfortable with Distant Horizons).
5. Shaders are in the instance's `shaderpacks` folder: Complementary Reimagined (+ Euphoria Patches), Bliss, Photon and Solas. Turn them on in Options → Video Settings → Shader Packs.

The repo does not contain any mod jars. The `pack/` folder is a [packwiz](https://packwiz.infra.link/) project that pins every file by hash; the `.mrpack` is built from it.

## What was tested (2026-10-15)

Everything ran in a headless cloud machine (4 CPU cores, 16 GB RAM, no GPU), so speed numbers are pessimistic compared with a gaming PC.

| Test | Result |
|---|---|
| Dedicated server boot (188 server-side mods incl. Twilight Forest) | ✅ Clean. About 2-2.5 min from launch |
| World generation in Overworld, Moon, Mars, Twilight Forest, Nether, End | ✅ All generated a chunk with no errors |
| Server shutdown | ✅ Clean |
| Client boot to title screen (all 211 mods) | ✅ About 2 min under software rendering |
| Client joins a world and plays for 90 s+ | ✅ (see screenshot below) |
| Real FPS, shaders, Distant Horizons visuals, actual gameplay feel | ❌ Can't be judged without a GPU. Test these on a real PC. |

Rendering used Mesa llvmpipe (software OpenGL) under a virtual display. The client joined the same world the server generated:

![In-world screenshot from the test client](docs/in-world.png)

### Diagnostics

- **Download size:** about 860 MB (mods 853 MB, shaders 7 MB). Biggest: BetterEnd 98 MB, Alex's Caves 75 MB, L_Ender's Cataclysm 73 MB, The Aether 40 MB, Mowzie's Mobs 36 MB, Create Aeronautics 33 MB.
- **Memory:** the server used about 9.3 GB of process memory with an 8 GB heap; the client about 8.1-9.6 GB. Plan for 8 GB heap minimum.
- **First world creation** is slow (the Overworld spawn chunk took about 45 s on the test machine) because of the amount of world-gen mods. Normal after that.
- **Client-only mods** (Sodium, Iris, JEI UI bits, Xaero's maps, etc.) are tagged `client` in `pack/`, so a server pack can be cut from the same folder later.

## Config tweaks shipped in the pack

These live in `pack/config/` and land in the instance's `config/` folder on import, so the game is tested exactly as it will be played.

- **TFMG lead ore is off** (`config/tfmg-common.toml`, `lead_ore = false`). Create Nuclear also adds lead ore, and both mods' recipes accept any lead from the shared `c:ingots/lead` tag, so one lead ore is enough. Create Nuclear's is kept because its config can only switch off all its ores at once (uranium too). TFMG's galena is a decorative stone, not an ore, so it stays. Verified: a test world had TFMG lead in 133 of 452 chunks before the change and 0 after, with Create Nuclear lead in 182.
- **Naturalist's 9 sea creatures that Hybrid Aquatic already has are off**: anglerfish, blobfish, giant isopod, great white shark, piranha, jellyfish, starfish, crab and ray. A small datapack (`config/paxi/datapacks/create-galore-tweaks`) empties their spawn-biome lists, and Paxi loads it into every world automatically. Verified with `/locate biome`: those 9 find nothing, while Naturalist's other spawns (whales, clams and the land animals) still work. Hybrid Aquatic's versions spawn as normal.

## Changes made to get it running

Every change from the curated list and why:

| Change | Why |
|---|---|
| **Removed Create: Ballast** | Its jar only allows NeoForge below 21.1.227, while Create Aeronautics needs 21.1.228+. They can never load together. |
| **Removed Create: Northstar Structures** | Built for the old Northstar 0.5.x. With Northstar 0.6.6 it references Moon features that no longer exist, and the world refuses to load. The mod page itself says it isn't compatible with Northstar 0.6 yet. |
| **Removed Chunky** | Distant Horizons calls into Chunky before Chunky has started, which crashes world creation at spawn. Distant Horizons generates distant terrain on its own, so nothing is lost. |
| **Swapped Create: AeroStar → Create: Northstar–Aeronautics Compatibility 3.0.0** | AeroStar was built against Northstar 0.5 and crashes the game about 40 s into a world (missing Northstar class). The original compat mod it forked from was updated for Northstar 0.6 and does the same job (fly Aeronautics ships to Northstar planets). |
| **Pinned NeoForge to 21.1.259** (not the newest 21.1.262) | On 21.1.262 joining any world kicks you out with "Failed to encode packet custom_payload" from The Aether's bundled Accessories library. Tested Aether alone: works on 21.1.248, 252, 257 and 259, fails on 262. The pack needs at least 21.1.252 (Cold Sweat Altitude), so 259 is the newest version that works. Revisit when Aether or Accessories updates. |
| **Added roller coasters**: Create: Coasters Simulated, Coasters Simulated: Track Styles, Create: Coaster Seats | Requested, purely for fun. Coasters Simulated is the Create-native one and uses Sable physics, which the pack already has. |
| **Added Twilight Forest** (manual, CurseForge) | Requested. Not on Modrinth, so it's a manual add (see install step 3). |
| **Swipe batch, removed Create: Diesel Generators** | Overlaps with TFMG's oil and diesel chain. |
| **Swipe batch, added** Chunksmith, Weightworks, Winds & Weather, Hostile Skies, Hangars, Flight Control, Aquamirae (+ Fragmentum), Productive Bees, Naturalist, Create Nuclear, TFMG Community Edition, Dungeons and Taverns, YUNG's Better Caves, Paxi, Northstar Aeronautics Compat and Dimensional Sable | Chosen in the swipe deck. Northstar Aeronautics Compat is a separate mod from the Northstar–Aeronautics bridge above, so both are in. Dimensional Sable is a required library. Tested as its own step: server boot, all 6 dimensions and client in-world passed. |
| **Swipe batch, left out Create: Northstar Chemistry** | Version 1.0.0 was built for Northstar 0.6.4 and crashes the game on startup with Northstar 0.6.6 (broken mixin in the Atmospheric Concentrator). Going back to Northstar 0.6.4 isn't an option because 0.6.6 fixes a crash with TFMG. Add it back when it updates. |
| **Added the wishlist batch**: Create: Let The Adventure Begin, Create Cafe, Critters and Companions, Cozy Cafe, plus their libraries YACL, Placebo and LibTooltips | Requested. Tested as a separate step after the base pack was clean: server boot, all 6 dimensions and client in-world all passed with no new crashes. Create Confectionery and Create: Coasters Simulated from the same list were already in. |

## Known issues worth knowing

None of these stop the game. They're listed so they don't surprise anyone later.

- **Animal Husbandry crashes a dedicated server** on startup (it loads a client-only class). It's fine in singleplayer and is in the client pack. It must be left out of the future server pack, or replaced.
- **First launch shows a few one-time screens:** the BetterX welcome screen, Create Deep Seas' setup prompt and photosensitivity warning, and Minecraft's accessibility prompt. At small window sizes the Deep Seas warning's button can sit off-screen; press Tab then Enter.
- **Cold Sweat ↔ Thirst Was Taken compat is off:** Cold Sweat wants Thirst 3.0+, and Modrinth only has 2.1.5 for NeoForge 1.21.1. Both mods work, they just don't talk to each other.
- **Some recipes and loot tables log errors** because they point at mods that aren't installed (Alex's Caves radon lamps in Create: Framed, Thirst's Create compat recipes, Steam 'n' Rails tracks for other wood mods, many Design n Decor loot tables). Harmless.
- **Wishlist batch log noise:** Create Cafe has compat recipes for Blood Magic and Botania (not installed), and Cozy Cafe can't read one menu file for Create Central Kitchen's pumpkin pie slice. Harmless.
- **Sable warns about `copycats:copycat_catwalk`** (a block Copycats+ doesn't have in this version). Harmless.
- **The Northstar–Aeronautics bridge's Dimensional Drive recipe doesn't load**: it asks for `northstar:computer_chip`, which Northstar 0.6.6 no longer has. So the drive likely can't be crafted in survival (not checked in-game yet). Northstar Aeronautics Compat is unaffected. This was already true before the swipe batch.
- **Chunksmith warns that Distant Horizons is installed on the server.** It's only a warning; the future server pack should leave Distant Horizons out and let Chunksmith send LODs to clients.
- **The Aether is pinned to an older NeoForge** (see above). Don't let a launcher auto-update NeoForge past 21.1.259 until that's fixed.

## Repo layout

- `pack/` – packwiz source (one `.pw.toml` per mod or shader, `pack.toml`, `index.toml`)
- `Create-Galore-0.1.0.mrpack` – importable pack built from `pack/`
- `MODS.md` – full mod list

To rebuild the `.mrpack` after changing `pack/`:

```
cd pack
packwiz refresh
packwiz modrinth export -o ../Create-Galore-0.1.0.mrpack
```
