# Running a Create Galore server

This guide covers the two servers the pack is meant to run on:

| Who is playing | Where the world runs | Why |
|---|---|---|
| Emre (alone or together) | **Local dedicated server** on Emre's PC | Fastest CPU, no cost, plenty of RAM (64 GB) for server and game side by side |
| Only the second player | **Exaroton**, 8 GB to start (try 6 GB later) | Her laptop has 16 GB and the pack needs about 8 GB per side, so it can't host and play at once |

Never host on the laptop while also playing on it. Use Exaroton for that.

The world is one folder, so it can move between the two (see [Moving the world](#moving-the-world-between-your-pc-and-exaroton)).

## What the server runs

The server gets **181 mods from the pack plus Twilight Forest**. The 41 client-only mods are skipped automatically because they're tagged `client` in `pack/`. That's the ambiance layer (Sound Physics, AmbientSounds, Sounds, Drip Sounds, Presence Footsteps and its Sable add-on, Subtle Effects, Falling Leaves, Make Bubbles Pop, LambDynamicLights, Contraption Lights, Not Enough Animations, EMF, ETF, plus their libraries CreativeCore, Fzzy Config and MRU) and these: Sodium, Sodium Extra, Iris, Euphoria Patches, Iris Veil Compat, Colorwheel, Distant Horizons, Entity Culling, ImmediatelyFast, Controlling, Mouse Tweaks, Searchables, Inventory Profiles Next (+ libIPN), LibTooltips, Create JEI Compat, Create: Nowheel, Create Smart Bounds, Create - Xaero's map, Xaero Train Map, Jade Sable Compat, Guide Shelf, Aeronautics Hot Air Fix, Better Farming Right Click. Players still need the full pack (the `.mrpack`) on their own PC.

**Distant Horizons stays off the server on purpose.** The pack's Chunksmith mod already sends far-away terrain (LODs) from the server to each player's Distant Horizons, without Distant Horizons on the server. Putting DH on the server as well makes Chunksmith complain and doubles the work.

Versions that must match on the server and on every player's PC: Minecraft **1.21.1**, NeoForge **21.1.259** (not newer, The Aether breaks on 21.1.262), and the same pack version.

## Part 1: Local dedicated server (Emre's PC)

You need **Java 21** (for example [Microsoft Build of OpenJDK 21](https://learn.microsoft.com/java/openjdk/download) or [Adoptium Temurin 21](https://adoptium.net/)) and a copy of this repo (on GitHub: **Code → Download ZIP**, then unzip).

### Build the server folder (one command)

Open a terminal in the repo's `server` folder and run:

```
install-server.bat C:\CreateGaloreServer
```

(On Linux or macOS: `./install-server.sh ~/CreateGaloreServer`.)

The script:
1. Installs the NeoForge 21.1.259 server into that folder.
2. Downloads every server-side mod and the pack's configs and datapacks at the exact pinned versions (about 830 MB), using `packwiz-installer` with the pack in this repo.
3. Copies in the pack's memory settings (`user_jvm_args.txt`, 8 GB) and starting `server.properties` the first time. Later runs keep your edits.

Then two manual steps:
1. Copy `twilightforest-1.21.1-4.8.3345-universal.jar` (CurseForge, same jar as the client) into `C:\CreateGaloreServer\mods`.
2. Read the [Minecraft EULA](https://aka.ms/MinecraftEULA) and put `eula=true` in `C:\CreateGaloreServer\eula.txt` (the file appears after the first start; you can also create it yourself).

### Start it

Double-click `run.bat` in the server folder (or run `run.bat nogui` in a terminal for no window). The first start creates the world and takes a few minutes; later starts are faster. It's ready when the console says `Done (...)! For help, type "help"`.

Stop it by typing `stop` in the console. Don't close the window, or the last few minutes might not be saved.

### First-time settings

Type these in the server console once:

```
whitelist add <your Minecraft name>
whitelist add <her Minecraft name>
op <your Minecraft name>
```

The shipped `server.properties` turns the whitelist on, keeps `online-mode=true` (real Minecraft accounts, so player data matches on Exaroton too), sets view distance 8 and simulation distance 6, and sets `allow-flight=true`. That last one matters: without it the server kicks players for "flying" when they ride airships, jetpacks or contraptions.

### Elytra (off unless you switch them on)

Elytra are off in this pack so trains, airships and submarines stay worth building. To allow them on your server, stop it, move `config/paxi/optional/create-galore-with-elytra` into `config/paxi/datapacks/`, and start it again. Re-running the install script doesn't undo this. Delete that folder from `datapacks/` to turn them off again. Details are in the README under "You travel by building".

### Joining

- **On the same PC:** Multiplayer → Add Server → address `localhost`.
- **Another PC at home:** use your PC's local address, for example `192.168.1.23`. Find it with `ipconfig` (the "IPv4 Address" line). When Windows asks whether Java may use the network, allow **Private networks**.
- **From outside your home:** not needed for this plan. It would need port forwarding of TCP 25565 on your router, or a tool like Tailscale.

Optional: Chunksmith also opens TCP **25566** for faster LOD downloads. Allowing it through the Windows firewall helps Distant Horizons fill in faster for the second PC; everything works without it.

### Memory

`user_jvm_args.txt` gives the server 8 GB (`-Xms8G -Xmx8G`). On a 64 GB PC you can raise both to 10G. Your own game keeps its own 8 to 10 GB in the launcher, so server plus game uses about 20 GB in total.

## Part 2: Exaroton (when she plays alone)

Exaroton bills by RAM per hour while the server runs, and can start the server when a whitelisted player tries to join. These steps follow Exaroton's documented features; their exact screens weren't tested from here, so names in the panel may differ slightly.

1. **Build the server folder on your PC first** (Part 1). Exaroton gets a copy of it.
2. Create a server on Exaroton. Under **Software**, choose **NeoForge**, Minecraft **1.21.1**, NeoForge **21.1.259**. Custom uploads need bought credits; the free trial can't do it.
3. Set **RAM to 8 GB**. After a few sessions, try 6 GB (see the test numbers below). Ignore `user_jvm_args.txt` there, the panel's RAM setting replaces it.
4. In the **Files** section, upload from your server folder: `mods/`, `config/`, `patchouli_books/` (the Field Guide) and `server.properties`. Then upload the world folder (`world/`) if you want to continue your world (see the next section).
5. In **Options**, keep online mode on, whitelist both accounts and op yourself. Turn on **auto-start** for whitelisted players if she should be able to start it by joining, and **auto-stop** after a few empty minutes so it doesn't run up credits.
6. She adds the Exaroton address (shown on the server page, like `yourname.exaroton.me`) in Multiplayer.

The storage limit is 10 GB per server. The mods are about 0.8 GB and a fresh world is small, but it grows as you explore. Chunksmith's LOD store (`world/chunksmith/lod/`) is optional and can be left out of uploads to save space.

## Moving the world between your PC and Exaroton

Only one copy is "live" at a time. Always **stop the server before copying** in either direction, and keep a zip of the old copy until the new one has started fine.

- **PC → Exaroton:** stop your local server, stop the Exaroton server, upload your server's `world/` folder in Exaroton's file manager (replace the old one).
- **Exaroton → PC:** stop the Exaroton server, download its `world/` folder, replace `C:\CreateGaloreServer\world` with it.

Skip `session.lock` and anything named `*.old`. Both sides must run the same pack version; if the pack changed, update both servers first (next section).

## Updating the pack

When the pack in this repo changes:

1. Download the repo again (or `git pull`).
2. Stop the server and run `install-server.bat C:\CreateGaloreServer` again. It updates the mods and configs in place and removes mods that left the pack. Your world, whitelist, ops and settings are kept.
3. For Exaroton: delete its `mods/` folder and upload the new `mods/` and `config/` folders.
4. Players update their own instance from the new `.mrpack`.

Back up `world/` before updates that add or remove mods. Blocks and items from removed mods disappear from the world.

## What was tested (2026-10-15)

On a cloud test machine (4 CPU cores, 16 GB RAM, no GPU) with this pack (0.1.1, 181 server mods + Twilight Forest):

| Test | Result |
|---|---|
| Server folder built from `pack/` with `packwiz-installer -s server` | ✅ Exactly the 181 server mods, client-only mods skipped |
| Dedicated server first boot and world creation, 6 GB heap | ✅ Clean, 83 s, about 7 GB of process memory |
| Restart with a 4.5 GB heap | ✅ 33 s, about 5.6 GB of process memory with one player |
| Player joins from the full client pack (Distant Horizons, Sodium, Iris and all client mods) | ✅ No mod mismatch, Field Guide handed out on join, server at 20 TPS |
| Player moved through Overworld, Moon, Nether, Aether and Twilight Forest on the server | ✅ Stayed connected in every dimension |
| Without `allow-flight=true` | ❌ Player was kicked ("Flying is not enabled on this server") while falling into a dimension, so the shipped settings turn it on |
| `install-server.sh` on an empty folder, then first boot with the shipped 8 GB settings | ✅ World created in 67 s, about 9 GB of process memory |
| Chunksmith sends LOD data to the joining client's Distant Horizons | ✅ Client log: "the server has LOD data for [minecraft_overworld, northstar_moon]" |

![Test client in the world on the dedicated server](server-join.png)

Real performance on your PC and on Exaroton's hardware wasn't measured. Use `/spark tps` and `/spark health` on the server while playing to check.

## Troubleshooting

- **"Mod rejections" or "missing registry" when joining:** the player's pack and the server's mods don't match. Update both to the same pack version.
- **"Failed to encode packet" right after joining:** NeoForge is newer than 21.1.259. Reinstall the server with the script (it pins the version) and check the launcher hasn't updated NeoForge.
- **Kicked for flying:** set `allow-flight=true` in `server.properties`.
- **Server crashes with `java.lang.OutOfMemoryError`:** raise the RAM (the `-Xmx` value, or the Exaroton RAM slider).
- **Animal Husbandry is on 0.4.2 on purpose.** Version 0.5.0 crashes dedicated servers. Don't let anything update it.
