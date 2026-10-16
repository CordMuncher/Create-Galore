# Java flags and memory

What to give the game and the server, and why. Everything here is for Java 21, which NeoForge 21.1 needs.

## Short version

| Where | Memory (`-Xms` and `-Xmx`, same number) | Garbage collector |
|---|---|---|
| Server on your own PC (32 GB or more) | **10 GB** | G1 with the pack's flags (already in `user_jvm_args.txt`) |
| Server on Exaroton | **6 GB** works, 8 GB if it ever runs out | Whatever Exaroton uses; you only pick RAM |
| Game on a big PC (64 GB, 12+ cores) | **12 GB** | Generational ZGC |
| Game on a 16 GB laptop | **8 GB** | G1 with the pack's flags |

**Upper bounds when you host and play on the same 64 GB PC:** server 12 GB, game 16 GB. Above that nothing gets faster. Only about 2 to 2.5 GB of the server's memory is still in use after each garbage collection, so a bigger heap just means longer, rarer cleanups. At 10 GB server and 12 GB game, Java uses about 26 GB in total (a process takes 1.5 to 2 GB more than its heap), which leaves plenty for Windows, Discord and the browser.

## Benchmark (2026-10-16)

A dedicated server with the pack (0.1.2 mods, 181 server mods + Twilight Forest) on a cloud test machine (4 cores, 16 GB, Java 21.0.12), fresh world on the same seed each time. Each run booted the server, generated 1,681 chunks with Chunksmith, then kept 1,024 chunks force-loaded for two minutes and measured the Overworld's tick time. "Pauses" are times the whole game stood still for the collector.

| Setup | Chunk generation | Tick time (lower is better, 50 ms is the limit) | Longest pause while generating | Pauses over 50 ms | Process memory |
|---|---|---|---|---|---|
| **G1, pack's flags (Aikar's), 8 GB** | **413 s** | 23.0 ms | 288 ms | 23 | 9.7 GB |
| G1, pack's flags, 6 GB | 407 s | 22.7 ms | 258 ms | 22 | 7.6 GB |
| G1, Java defaults, 8 GB | 427 s | 23.3 ms | 1,224 ms | 87 | 9.6 GB |
| Generational ZGC, 8 GB | 469 s | 28.1 ms | **13 ms** | **0** | 10.2 GB |
| Shenandoah, 8 GB | 554 s | 18.6 ms | 2,094 ms | 2 | 10.1 GB |

What that means:

- **The pack's G1 flags are the best all-rounder for the server.** Fastest chunk generation, good tick times, and much shorter pauses than plain G1. A server pause of 100 to 300 ms only happens while lots of new chunks generate, and it's hidden by the tick budget; players don't feel it.
- **Generational ZGC never stops the game** (13 ms worst case), but its background threads take CPU from the game. On this 4-core box that cost about 15% generation speed and 20% tick time. On a 16-core 9950X3D there are spare cores, so that cost mostly disappears, and smoothness matters far more for the game (one frame is 7 to 16 ms) than for the server. That's why it's the pick for your game, not the server.
- **Shenandoah** had the best tick time once things settled, but the slowest generation and a 2-second freeze. Not worth it.
- **6 GB is enough** for a server with a couple of players. The heap after a collection never went above 2.5 GB.

The game itself wasn't benchmarked (the test machine has no GPU), so the game advice comes from these server numbers plus how the collectors behave: same mods, same Java.

## Server

The flags ship in `server/user_jvm_args.txt` and both the Server Setup app and the install scripts write them. The app suggests 10 GB on a PC with 32 GB or more and 8 GB otherwise; its slider stops at 16 GB.

To try Generational ZGC on your own PC anyway, replace everything below the two memory lines in `user_jvm_args.txt` with:

```
-XX:+UseZGC
-XX:+ZGenerational
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
```

and give it 2 GB more than with G1 (ZGC wants headroom: at 8 GB it ran at 95% full).

## Game (Prism Launcher or Modrinth App)

**Prism Launcher:** instance → Edit → Settings → Java. Tick **Memory**, set minimum and maximum to the same value (12288 MB on your PC, 8192 MB on the laptop). Tick **Java arguments** and paste the line below.

**Modrinth App:** instance → ⋯ → Settings → Java and memory. Set the memory slider (12288 MB or 8192 MB), tick custom Java arguments and paste the line below with `-Xms` added at the front, for example `-Xms12G` (the app only sets the maximum itself).

Your PC (64 GB, 9950X3D):

```
-XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+DisableExplicitGC
```

The laptop (16 GB):

```
-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
```

Don't give the laptop more than 8 GB: with 16 GB total, Windows, the GPU driver and Distant Horizons' own native memory need the rest. If it stutters every few seconds, that's the collector; dropping Distant Horizons' render distance helps more than more RAM.

## Why each flag

- **`-Xms` = `-Xmx`:** Java takes all the memory at start instead of growing into it, so there are no resize stalls and Windows can't hand that memory to something else halfway through a session.
- **`-XX:+AlwaysPreTouch`:** touches every page of that memory at start. Launch takes a few seconds longer; afterwards there are no hiccups from Windows mapping memory in the middle of play.
- **`-XX:+DisableExplicitGC`:** some mods call for a full garbage collection by hand, which freezes everything. This ignores them.
- **The G1 tuning lines (Aikar's flags):** give G1 a large space for short-lived objects (Minecraft makes millions per second), start background cleanup early (`InitiatingHeapOccupancyPercent=15`) and spread it over several small steps. That's the difference between the 288 ms and the 1,224 ms worst case in the table.
- **`-XX:+ZGenerational`:** in Java 21 ZGC is single-generation unless you ask; the generational mode is much better for Minecraft. (Java 23+ turns it on by default, but this pack needs 21.)

## Things that aren't worth it

- **Large pages (`-XX:+UseLargePages`):** on Windows it needs the "Lock pages in memory" policy (Windows Pro, `secpol.msc`) and a reboot, and Java silently falls back without it. A few percent at best. Not used.
- **More than 12 GB server / 16 GB game:** no gain, see above.
- **Other Java builds (GraalVM, newer Java versions):** NeoForge 21.1 and its mods are built for Java 21. Use any Java 21 (the launchers and the Server Setup app pick one for you).
- **Old guides' flags** like `-XX:+UseConcMarkSweepGC`, `-XX:+AggressiveOpts` or `-XX:+UseStringDeduplication`: removed from Java or no help here. Extra unknown flags can stop Java from starting.

## Things you might not have thought of

- **Pre-generate the world on the server** while nobody plays. Generating new chunks is where all the long pauses in the benchmark came from. In the server console: `cs center 0 0`, `cs radius 3000`, `cs start`, and let it run (overnight is fine; `cs pause` stops it, `cs continue` resumes). Chunksmith also builds Distant Horizons data as it goes, so far terrain shows up faster on both PCs.
- **Close the server window with `stop`,** not the X, so it saves cleanly.
- **The 9950X3D's two halves:** Windows (with AMD's chipset driver and Game Mode on) keeps the game on the half with the big cache. The server lands wherever there's room, which is fine. If the game ever stutters only while the server runs, you can push the server onto the other half by starting it with `start "" /affinity FFFF0000 start-server.bat` instead of double-clicking. Untested here; only bother if you see the problem.
- **`/spark tps` and `/spark health`** on the server, and `F3` in the game, show whether the collector is the problem before you change anything.
