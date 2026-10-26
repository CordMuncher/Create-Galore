# HUD split 1 on the RTX 4080 SUPER: the world without the HUD, and the HUD's coverage

PC session on 2026-10-26, 16:13–16:42, by a Claude Code session in `C:\Users\coolm\Documents\Claude\MC-CREATE`
(`$T`), with Emre at the keyboard. It followed "HUD split 1" in `tools/dlss-testkit/PC-RUNBOOK.md` at `93149df9`, with
Bridge 1.116.0 (test build), in one session with Pacer 3 (that one second, reported in `results/pacer3/`).
- One launch without `autorun`, Perfect Presentation on DirectX 12, Photon v1.3a + DLAA with DLSS at full resolution
  (preset K), 2560x1440.
- No crash. This is a test, not a fix: no code was changed.

## Summary

- **The split started as it should.** The log has `HUD split: on, checks every 5 s`, `HUD split: world copy taken after
  the vignette` and `HUD split: 101 HUD layers; drawn before the camera overlays (so in the world copy):
  [subtle_effects:debug_overlay, supplementaries:slimed]`.
- **189 checks, all 0 leaks, 0 under, 0 over.** That covers every scene of step 1, the 5 minutes of play in step 2 and
  the four views in step 3.
- **71 marks pictures were saved**, each time the scene changed. None has a single magenta, yellow or cyan pixel. I
  counted the exact colours the check paints, in every picture.
- **By eye (Emre, step 2):** "Looks good to me. Nothing unusual apart from the expected dips" (the checks' one-frame
  stalls every 5 s).
- **The four views show what they should:** `ui` only the HUD over a grey checkerboard, `marks` the world dimmed with no
  colour marks, `hudless` the world without any HUD, `alpha` white under the HUD and black elsewhere.
- **The cost of the split** (no checks), standing still and uncapped: **161 fps / 6.2 ms with it, 164 fps / 6.1 ms
  without**. That's about 2% or 0.1 ms per frame, from one reading each, so close to noise. With Emre's own 120 fps cap
  both sat at the cap (118 and 116 fps).
- **No `HUD split` WARN line, and no `Perfect Presentation` or `DLAA:` WARN or ERROR line.** Even the usual one-off
  `swap chain wait timed out` didn't come this time.

**In one line:** on the RTX 4080 at full speed, with Perfect Presentation on DirectX 12, Photon + DLSS and the whole HUD,
the world copy and the HUD coverage came out exact in all 189 checks, nothing looked different, and the split costs
about 0.1 ms per frame.

## Log lines

Every `HUD split` line that isn't a check:

```
16:18:23.978 HUD split: on, checks every 5 s
16:18:24.050 HUD split: 2560x1440, format 0x8058
16:18:24.050 HUD split: world copy taken after the vignette
16:18:24.464 HUD split: 101 HUD layers; drawn before the camera overlays (so in the world copy): [subtle_effects:debug_overlay, supplementaries:slimed]
16:18:24.480 HUD split: full-screen effect minecraft:shaders/post/blur.json during the HUD: whole frame counts as HUD
16:26:47.033 HUD split: world copy taken before the HUD layer brewinandchewin:tipsy
16:30:31.639 HUD split: on, view ui, checks every 5 s
16:31:16.874 HUD split: on, view marks, checks every 5 s
16:32:04.767 HUD split: on, view hudless, checks every 5 s
16:32:40.262 HUD split: on, view alpha, checks every 5 s
16:34:09.532 HUD split: on
16:37:45.887 HUD split: off
16:39:27.995 HUD split: on
16:40:36.549 HUD split: off
```

- The blur line came while the world loaded (a screen with the menu blur was up). The checks with a menu open
  (inventory, pause, options) count the whole frame as HUD, as the line says.
- **`world copy taken before the HUD layer brewinandchewin:tipsy`** came with F1 (checks 102–104). With the HUD hidden
  the vignette isn't drawn, so the copy point fell back to the first HUD layer that still runs (Brewin' and Chewin's
  tipsy overlay). Those checks counted 0 px of HUD, which is right with F1: the frame counter hides too, as this build
  makes it. Check 105, with the HUD back, was "after the vignette" again.
- **Perfect Presentation:** `DirectX 12 on NVIDIA GeForce RTX 4080 SUPER (OpenGL node mask 1, tearing allowed)` and
  `DXGI swap chain ready on DirectX 12 (OpenGL on a hidden context)`. **DLAA:** `NGX ready on NVIDIA GeForce RTX 4080
  SUPER (its own DirectX device; ...)` and `DLSS running at 2560x1440 (full resolution), preset K, the pack's exposure`.
- No check line has leaks, under or over above 0, so there are no check pictures to copy here. All 71 picture sets
  stay on the PC (`$T\instance\screenshots\hud-split\`).

## Step 1: the HUD, scene by scene

The checks per scene: the share of the screen the HUD covers ("HUD", any coverage) and covers fully ("opaque"), and
leaks / under / over. The whole list of 189 checks is at the end.

| Scene | Checks | Screen | HUD | opaque | leaks / under / over |
| --- | --- | --- | --- | --- | --- |
| The HUD as it is (creative) | 6–8 | none | 8.9–11.4% | 6.6–7.2% | 0 / 0 / 0 |
| `/gamemode survival`, on land: hearts and food | 10–12 | none | 13.3–16.1% | 9.3–9.9% | 0 / 0 / 0 |
| F3 | 20–23 | none | 69.8–71.5% | 17.9–18.3% | 0 / 0 / 0 |
| Chat open, a few letters typed | 24–26 | ChatScreen | 28.7% | 7.1–7.3% | 0 / 0 / 0 |
| Boss bar `HUD test` | 41–43 | none | 12.9% | 7.7% | 0 / 0 / 0 |
| `/title @s title "Title"` | 50–51 | none | 14.3% | 7.7–9.1% | 0 / 0 / 0 |
| Carved pumpkin on the head | 62–64 | none | 98.0% | 30.0–31.9% | 0 / 0 / 0 |
| Spyglass held to the eye | 76–79 | none | 100% | 48.0–48.7% | 0 / 0 / 0 |
| `/effect give @s nausea 20` | 87–90 | none | 9.5–12.4% | 7.2–7.3% | 0 / 0 / 0 |
| Inventory (E) | 95–97 | CreativeModeInventoryScreen | 100% (menu blur) | 47.6% | 0 / 0 / 0 |
| Pause menu (Esc) | 98 | PauseScreen | 100% | 100% | 0 / 0 / 0 |
| Options screen | 99–100 | OptionsScreen | 100% | 100% | 0 / 0 / 0 |
| F1 | 102–104 | none | 0 px | 0 px | 0 / 0 / 0 |

- The chat checks in between (Emre typing the commands) also all read 0 / 0 / 0. ChatScreen came up 32 times in all.
- The nausea wobble is in the world copy (it's drawn before the HUD), so the HUD's share didn't change under it.
- The pumpkin and the spyglass are HUD layers in this game: they cover nearly the whole screen, partly see-through.
- None of the 71 marks pictures is coloured, so none is copied here.

## Step 2: by eye

Emre played normally for about 5 minutes (checks 106–145, all 0 / 0 / 0): "Looks good to me. Nothing unusual apart from
the expected dips." No flicker, no wrong colour, and the HUD and the menu blur looked as usual.

## Step 3: the views

`hudSplitView` added to the settings file and F8 each time; the log confirmed each view. Emre's F2 screenshots:

| View | What it should show | What it showed | Screenshot |
| --- | --- | --- | --- |
| `ui` | only the HUD, over a grey checkerboard; no world | the minimap, the block tooltip, the frame counter, the crosshair, the chat line, the hotbar over the checkerboard; no world | [view-ui.png](view-ui.png) |
| `marks` | the world dimmed, no magenta, yellow or cyan | the world dimmed with the HUD on top; Emre: "I see no magenta yellow or cyan" | [view-marks.jpg](view-marks.jpg) |
| `hudless` | the world with no HUD at all | the world only: no hotbar, crosshair, chat, minimap or frame counter (the first-person hand and shield are the world's) | [view-hudless.jpg](view-hudless.jpg) |
| `alpha` | white where the HUD is, black elsewhere | white under the minimap, crosshair, hotbar and counter, grey under the chat's see-through background, black elsewhere; Emre: "Very trippy looking but yeah it works" | [view-alpha.png](view-alpha.png) |

`marks` and `hudless` are saved here as JPEG to keep the repository small (the PNGs are on the PC). The checks during
the views (146–189) were all 0 / 0 / 0 too.

## Step 4: the cost

Standing still at one spot looking at the lake. The frame counter's readings (fps and frame time over its window, and
the 1% and 0.1% lows of the last 10 seconds), from Emre's F2 screenshots:

| | Split on (`hudSplit=true`, no checks) | Split off |
| --- | --- | --- |
| As the runbook says (Emre's own settings: VSync on, Max Framerate 120) | 118 fps, 8.5 ms (still); 116 fps, 8.7 ms (moving) | 116 fps, 8.6 ms |
| Uncapped (Max Framerate Unlimited, VSync off), about 30 s still | **161 fps, 6.2 ms** (1% low 87, 0.1% low 54) | **164 fps, 6.1 ms** (1% low 87, 0.1% low 70) |

- With the cap both sit at it, so the runbook's comparison can't show a cost. Emre then uncapped the frame rate and
  turned VSync off himself and ran both again (his call, not in the runbook). His settings were put back afterwards.
- Uncapped, the split costs about 3 fps (2%), 0.1 ms per frame. That's one reading each, at the edge of the noise.
- The capped split-off reading's lows (6 and 1) include the F8 reload's hitch, which was still in the counter's
  10-second window.

## Method

**Update.** As Pacer 3's Update, which covers both:
- `git fetch` and `checkout -B claude/project-thread-u2wmhh origin/claude/project-thread-u2wmhh` (`93149df9`);
- `create-galore-bridge-1.114.0.jar` removed and `create-galore-bridge-1.116.0.jar` (1,408,236 B) copied in;
- the test kit jar (55,751 B) copied in.

**Settings, and what they were before.** All back at the end, checked byte for byte against copies taken first.
- `create_galore_bridge-display.properties`:
  - `dlaa=true` → `dlaa=dlss`;
  - `perfectPresentation=false` → `true`;
  - added `presenter=dx12`, `fpsCounter=detailed` (neither was in the file) and `hudSplitCheck=true`;
  - then `hudSplitView=...` in step 3, and `hudSplit=true` or nothing in step 4.
  - The file was put back as it was, so none of these is left.
- Shader pack: Photon v1.3a + DLAA was already selected.
- Nothing else changed, as the runbook says. Distant Horizons kept its 32 threads, and `options.txt` kept Emre's
  settings (VSync on, 120 fps) until he changed them for the uncapped readings. `options.txt` was put back afterwards.

**The launch.**
- The usual launch without `autorun`, 16:16:18. Emre opened `dlaa-test` and used `/gamemode creative`.
- The world's backup mod made its usual backup at load (16:18:26–16:18:29).
- Emre did every step at the keyboard while I read the log after each one. I took no screenshots of the screen: the
  pictures here are his F2 screenshots and the check pictures.
- Emre quit the game himself at 16:41:53.

**What was running.**
- The PC had restarted at 16:04, so the ChatGPT/Codex sandbox service and Wallpaper Engine were running again.
- Emre stopped the Codex service. Its startup type is still Automatic, which he can change in Windows Services.
- Emre paused the wallpaper. During step 4, only the game used the GPU.
- No PresentMon, no nvidia-smi, no game server.

No crash, and no new crash log.

## Raw data, on the PC only

- `$T\instance\screenshots\hud-split\`: 71 check picture sets (`check-NNN-frame`, `-hudless`, `-alpha`, `-ui`,
  `-marks`).
- In `$T\hud-split1\`:
  - the game log `latest.log` and `checks-table.md`;
  - all of Emre's F2 screenshots as PNG (`shots\`);
  - the settings copies, the display settings file as 1.116.0 left it, and `options.txt` as Emre left it uncapped;
  - this session's notes.

<details>
<summary>All 189 checks</summary>

| check | time | scene | screen | copy taken | HUD | opaque | leaks / under / over |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 16:18:26 | loading the world | ReceivingLevelScreen | after vignette | 100.00% | 100.00% | 0 / 0 / 0 |
| 2 | 16:18:31 | arrival, chat | none | after vignette | 14.74% | 3.42% | 0 / 0 / 0 |
| 3 | 16:18:36 | arrival, chat | ChatScreen | after vignette | 23.51% | 7.41% | 0 / 0 / 0 |
| 4 | 16:18:39 | arrival, chat | ChatScreen | after vignette | 23.08% | 6.91% | 0 / 0 / 0 |
| 5 | 16:18:44 | arrival, chat | ChatScreen | after vignette | 23.08% | 6.90% | 0 / 0 / 0 |
| 6 | 16:18:51 | 1: HUD as it is (creative) | none | after vignette | 8.92% | 6.57% | 0 / 0 / 0 |
| 7 | 16:18:54 | 1: HUD as it is (creative) | none | after vignette | 8.90% | 6.60% | 0 / 0 / 0 |
| 8 | 16:19:01 | 1: HUD as it is (creative) | none | after vignette | 11.38% | 7.15% | 0 / 0 / 0 |
| 9 | 16:19:06 | chat | ChatScreen | after vignette | 24.23% | 7.51% | 0 / 0 / 0 |
| 10 | 16:19:11 | 1: survival, hearts and food | none | after vignette | 16.14% | 9.91% | 0 / 0 / 0 |
| 11 | 16:19:16 | 1: survival, hearts and food | none | after vignette | 13.25% | 9.32% | 0 / 0 / 0 |
| 12 | 16:19:19 | 1: survival, hearts and food | none | after vignette | 13.25% | 9.31% | 0 / 0 / 0 |
| 13 | 16:19:26 | chat | ChatScreen | after vignette | 27.50% | 9.63% | 0 / 0 / 0 |
| 14 | 16:19:31 | creative | none | after vignette | 11.82% | 6.65% | 0 / 0 / 0 |
| 15 | 16:19:34 | creative | none | after vignette | 11.82% | 6.64% | 0 / 0 / 0 |
| 16 | 16:19:39 | creative | none | after vignette | 11.82% | 6.65% | 0 / 0 / 0 |
| 17 | 16:19:46 | creative | none | after vignette | 10.99% | 6.87% | 0 / 0 / 0 |
| 18 | 16:19:51 | creative | none | after vignette | 8.90% | 6.57% | 0 / 0 / 0 |
| 19 | 16:19:54 | creative | none | after vignette | 8.90% | 6.57% | 0 / 0 / 0 |
| 20 | 16:20:01 | 2: F3 | none | after vignette | 71.50% | 18.30% | 0 / 0 / 0 |
| 21 | 16:20:04 | 2: F3 | none | after vignette | 69.79% | 17.92% | 0 / 0 / 0 |
| 22 | 16:20:09 | 2: F3 | none | after vignette | 69.88% | 17.92% | 0 / 0 / 0 |
| 23 | 16:20:14 | 2: F3 | none | after vignette | 69.77% | 17.88% | 0 / 0 / 0 |
| 24 | 16:20:21 | 2: chat, letters typed | ChatScreen | after vignette | 28.73% | 7.13% | 0 / 0 / 0 |
| 25 | 16:20:24 | 2: chat, letters typed | ChatScreen | after vignette | 28.71% | 7.30% | 0 / 0 / 0 |
| 26 | 16:20:29 | 2: chat, letters typed | ChatScreen | after vignette | 28.71% | 7.29% | 0 / 0 / 0 |
| 27 | 16:20:36 | chat, boss bar made | none | after vignette | 8.91% | 6.57% | 0 / 0 / 0 |
| 28 | 16:20:39 | chat, boss bar made | none | after vignette | 8.90% | 6.57% | 0 / 0 / 0 |
| 29 | 16:20:44 | chat, boss bar made | none | after vignette | 8.90% | 6.57% | 0 / 0 / 0 |
| 30 | 16:20:49 | chat, boss bar made | none | after vignette | 8.94% | 6.58% | 0 / 0 / 0 |
| 31 | 16:20:54 | chat, boss bar made | none | after vignette | 8.94% | 6.59% | 0 / 0 / 0 |
| 32 | 16:21:00 | chat, boss bar made | none | after vignette | 8.94% | 6.58% | 0 / 0 / 0 |
| 33 | 16:21:06 | chat, boss bar made | ChatScreen | after vignette | 29.17% | 7.45% | 0 / 0 / 0 |
| 34 | 16:21:09 | chat, boss bar made | ChatScreen | after vignette | 29.15% | 7.51% | 0 / 0 / 0 |
| 35 | 16:21:16 | chat, boss bar made | ChatScreen | after vignette | 32.20% | 8.49% | 0 / 0 / 0 |
| 36 | 16:21:21 | chat, boss bar made | ChatScreen | after vignette | 28.93% | 7.54% | 0 / 0 / 0 |
| 37 | 16:21:26 | chat, boss bar made | none | after vignette | 11.81% | 6.64% | 0 / 0 / 0 |
| 38 | 16:21:30 | chat, boss bar made | none | after vignette | 11.81% | 6.64% | 0 / 0 / 0 |
| 39 | 16:21:36 | chat, boss bar made | ChatScreen | after vignette | 31.92% | 7.54% | 0 / 0 / 0 |
| 40 | 16:21:40 | chat, boss bar made | ChatScreen | after vignette | 32.45% | 8.70% | 0 / 0 / 0 |
| 41 | 16:21:46 | 3: boss bar | none | after vignette | 12.88% | 7.72% | 0 / 0 / 0 |
| 42 | 16:21:50 | 3: boss bar | none | after vignette | 12.87% | 7.73% | 0 / 0 / 0 |
| 43 | 16:21:55 | 3: boss bar | none | after vignette | 12.87% | 7.73% | 0 / 0 / 0 |
| 44 | 16:22:01 | chat | none | after vignette | 10.00% | 7.63% | 0 / 0 / 0 |
| 45 | 16:22:06 | chat | ChatScreen | after vignette | 36.75% | 9.87% | 0 / 0 / 0 |
| 46 | 16:22:11 | chat | ChatScreen | after vignette | 39.09% | 9.22% | 0 / 0 / 0 |
| 47 | 16:22:16 | chat | ChatScreen | after vignette | 35.60% | 8.49% | 0 / 0 / 0 |
| 48 | 16:22:21 | chat | ChatScreen | after vignette | 39.09% | 9.47% | 0 / 0 / 0 |
| 49 | 16:22:26 | chat | ChatScreen | after vignette | 35.90% | 8.65% | 0 / 0 / 0 |
| 50 | 16:22:31 | 3: title | none | after vignette | 14.25% | 9.05% | 0 / 0 / 0 |
| 51 | 16:22:35 | 3: title | none | after vignette | 14.25% | 7.68% | 0 / 0 / 0 |
| 52 | 16:22:41 | chat | ChatScreen | after vignette | 39.41% | 9.35% | 0 / 0 / 0 |
| 53 | 16:22:45 | chat | ChatScreen | after vignette | 38.72% | 8.77% | 0 / 0 / 0 |
| 54 | 16:22:51 | between scenes | none | after vignette | 11.81% | 6.64% | 0 / 0 / 0 |
| 55 | 16:22:56 | between scenes | none | after vignette | 14.35% | 7.18% | 0 / 0 / 0 |
| 56 | 16:23:01 | between scenes | none | after vignette | 8.90% | 6.57% | 0 / 0 / 0 |
| 57 | 16:23:05 | between scenes | none | after vignette | 8.90% | 6.57% | 0 / 0 / 0 |
| 58 | 16:23:11 | between scenes | ChatScreen | after vignette | 40.63% | 7.73% | 0 / 0 / 0 |
| 59 | 16:23:15 | between scenes | ChatScreen | after vignette | 41.43% | 9.19% | 0 / 0 / 0 |
| 60 | 16:23:21 | between scenes | ChatScreen | after vignette | 51.10% | 12.66% | 0 / 0 / 0 |
| 61 | 16:23:25 | between scenes | ChatScreen | after vignette | 51.10% | 12.82% | 0 / 0 / 0 |
| 62 | 16:23:31 | 4: carved pumpkin | none | after vignette | 98.03% | 31.92% | 0 / 0 / 0 |
| 63 | 16:23:35 | 4: carved pumpkin | none | after vignette | 98.03% | 30.75% | 0 / 0 / 0 |
| 64 | 16:23:40 | 4: carved pumpkin | none | after vignette | 98.03% | 29.99% | 0 / 0 / 0 |
| 65 | 16:23:46 | 4: pumpkin, chat | ChatScreen | after vignette | 98.19% | 37.23% | 0 / 0 / 0 |
| 66 | 16:23:50 | 4: pumpkin, chat | ChatScreen | after vignette | 98.19% | 37.09% | 0 / 0 / 0 |
| 67 | 16:23:55 | 4: pumpkin, chat | ChatScreen | after vignette | 98.19% | 37.65% | 0 / 0 / 0 |
| 68 | 16:24:01 | between scenes | none | after vignette | 11.86% | 6.65% | 0 / 0 / 0 |
| 69 | 16:24:05 | between scenes | none | after vignette | 11.82% | 6.64% | 0 / 0 / 0 |
| 70 | 16:24:10 | between scenes | none | after vignette | 11.81% | 6.64% | 0 / 0 / 0 |
| 71 | 16:24:16 | between scenes | none | after vignette | 8.94% | 6.57% | 0 / 0 / 0 |
| 72 | 16:24:20 | between scenes | none | after vignette | 8.90% | 6.57% | 0 / 0 / 0 |
| 73 | 16:24:26 | between scenes | ChatScreen | after vignette | 46.54% | 8.13% | 0 / 0 / 0 |
| 74 | 16:24:30 | between scenes | ChatScreen | after vignette | 47.00% | 8.19% | 0 / 0 / 0 |
| 75 | 16:24:36 | between scenes | none | after vignette | 16.31% | 11.53% | 0 / 0 / 0 |
| 76 | 16:24:41 | 5: spyglass to the eye | none | after vignette | 100.00% | 48.66% | 0 / 0 / 0 |
| 77 | 16:24:45 | 5: spyglass to the eye | none | after vignette | 100.00% | 48.00% | 0 / 0 / 0 |
| 78 | 16:24:50 | 5: spyglass to the eye | none | after vignette | 100.00% | 48.00% | 0 / 0 / 0 |
| 79 | 16:24:55 | 5: spyglass to the eye | none | after vignette | 100.00% | 48.00% | 0 / 0 / 0 |
| 80 | 16:25:01 | between scenes | none | after vignette | 8.94% | 6.66% | 0 / 0 / 0 |
| 81 | 16:25:05 | between scenes | none | after vignette | 8.89% | 6.65% | 0 / 0 / 0 |
| 82 | 16:25:11 | between scenes | none | after vignette | 11.43% | 7.20% | 0 / 0 / 0 |
| 83 | 16:25:15 | between scenes | none | after vignette | 11.43% | 7.20% | 0 / 0 / 0 |
| 84 | 16:25:20 | between scenes | none | after vignette | 11.43% | 6.80% | 0 / 0 / 0 |
| 85 | 16:25:26 | between scenes | ChatScreen | after vignette | 49.17% | 7.83% | 0 / 0 / 0 |
| 86 | 16:25:30 | between scenes | ChatScreen | after vignette | 50.88% | 8.52% | 0 / 0 / 0 |
| 87 | 16:25:36 | 6: nausea | none | after vignette | 12.37% | 7.27% | 0 / 0 / 0 |
| 88 | 16:25:40 | 6: nausea | none | after vignette | 12.37% | 7.27% | 0 / 0 / 0 |
| 89 | 16:25:46 | 6: nausea | none | after vignette | 9.45% | 7.21% | 0 / 0 / 0 |
| 90 | 16:25:50 | 6: nausea | none | after vignette | 9.45% | 7.21% | 0 / 0 / 0 |
| 91 | 16:25:56 | between scenes | none | after vignette | 8.89% | 6.65% | 0 / 0 / 0 |
| 92 | 16:26:00 | between scenes | none | after vignette | 8.90% | 6.66% | 0 / 0 / 0 |
| 93 | 16:26:06 | between scenes | none | after vignette | 11.46% | 7.26% | 0 / 0 / 0 |
| 94 | 16:26:11 | between scenes | none | after vignette | 8.90% | 6.66% | 0 / 0 / 0 |
| 95 | 16:26:16 | 7: inventory | CreativeModeInventoryScreen | after vignette | 100.00% | 47.56% | 0 / 0 / 0 |
| 96 | 16:26:20 | 7: inventory | CreativeModeInventoryScreen | after vignette | 100.00% | 47.62% | 0 / 0 / 0 |
| 97 | 16:26:25 | 7: inventory | CreativeModeInventoryScreen | after vignette | 100.00% | 47.61% | 0 / 0 / 0 |
| 98 | 16:26:31 | 7: pause menu | PauseScreen | after vignette | 100.00% | 100.00% | 0 / 0 / 0 |
| 99 | 16:26:36 | 7: options | OptionsScreen | after vignette | 100.00% | 100.00% | 0 / 0 / 0 |
| 100 | 16:26:40 | 7: options | OptionsScreen | after vignette | 100.00% | 100.00% | 0 / 0 / 0 |
| 101 | 16:26:46 | 7: back in game | none | after vignette | 8.90% | 6.66% | 0 / 0 / 0 |
| 102 | 16:26:52 | 7: F1 | none | before brewinandchewin:tipsy | 0% | 0% | 0 / 0 / 0 |
| 103 | 16:26:55 | 7: F1 | none | before brewinandchewin:tipsy | 0% | 0% | 0 / 0 / 0 |
| 104 | 16:27:00 | 7: F1 | none | before brewinandchewin:tipsy | 0% | 0% | 0 / 0 / 0 |
| 105 | 16:27:07 | 7: HUD back | none | after vignette | 8.91% | 6.68% | 0 / 0 / 0 |
| 106 | 16:27:12 | step 2: play | none | after vignette | 11.38% | 7.24% | 0 / 0 / 0 |
| 107 | 16:27:17 | step 2: play | none | after vignette | 8.90% | 6.66% | 0 / 0 / 0 |
| 108 | 16:27:20 | step 2: play | none | after vignette | 8.90% | 6.65% | 0 / 0 / 0 |
| 109 | 16:27:25 | step 2: play | none | after vignette | 8.90% | 6.66% | 0 / 0 / 0 |
| 110 | 16:27:30 | step 2: play | none | after vignette | 8.94% | 6.66% | 0 / 0 / 0 |
| 111 | 16:27:35 | step 2: play | none | after vignette | 8.94% | 6.67% | 0 / 0 / 0 |
| 112 | 16:27:40 | step 2: play | none | after vignette | 8.94% | 6.66% | 0 / 0 / 0 |
| 113 | 16:27:45 | step 2: play | none | after vignette | 8.94% | 6.69% | 0 / 0 / 0 |
| 114 | 16:27:50 | step 2: play | none | after vignette | 8.92% | 6.69% | 0 / 0 / 0 |
| 115 | 16:27:55 | step 2: play | none | after vignette | 8.91% | 6.66% | 0 / 0 / 0 |
| 116 | 16:28:00 | step 2: play | none | after vignette | 8.92% | 6.68% | 0 / 0 / 0 |
| 117 | 16:28:05 | step 2: play | none | after vignette | 8.91% | 6.67% | 0 / 0 / 0 |
| 118 | 16:28:10 | step 2: play | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 119 | 16:28:15 | step 2: play | none | after vignette | 8.91% | 6.66% | 0 / 0 / 0 |
| 120 | 16:28:20 | step 2: play | none | after vignette | 8.94% | 6.69% | 0 / 0 / 0 |
| 121 | 16:28:25 | step 2: play | none | after vignette | 8.94% | 6.67% | 0 / 0 / 0 |
| 122 | 16:28:30 | step 2: play | none | after vignette | 8.91% | 6.66% | 0 / 0 / 0 |
| 123 | 16:28:35 | step 2: play | none | after vignette | 8.91% | 6.67% | 0 / 0 / 0 |
| 124 | 16:28:40 | step 2: play | none | after vignette | 8.91% | 6.67% | 0 / 0 / 0 |
| 125 | 16:28:45 | step 2: play | none | after vignette | 8.92% | 6.65% | 0 / 0 / 0 |
| 126 | 16:28:51 | step 2: play | none | after vignette | 11.04% | 7.14% | 0 / 0 / 0 |
| 127 | 16:28:55 | step 2: play | none | after vignette | 11.52% | 7.63% | 0 / 0 / 0 |
| 128 | 16:29:00 | step 2: play | none | after vignette | 11.03% | 7.14% | 0 / 0 / 0 |
| 129 | 16:29:05 | step 2: play | none | after vignette | 11.02% | 6.96% | 0 / 0 / 0 |
| 130 | 16:29:10 | step 2: play | none | after vignette | 11.51% | 7.56% | 0 / 0 / 0 |
| 131 | 16:29:15 | step 2: play | none | after vignette | 11.04% | 7.12% | 0 / 0 / 0 |
| 132 | 16:29:20 | step 2: play | none | after vignette | 11.04% | 7.13% | 0 / 0 / 0 |
| 133 | 16:29:25 | step 2: play | none | after vignette | 11.07% | 7.13% | 0 / 0 / 0 |
| 134 | 16:29:30 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 135 | 16:29:35 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 136 | 16:29:40 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 137 | 16:29:45 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 138 | 16:29:50 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 139 | 16:29:55 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 140 | 16:30:00 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 141 | 16:30:05 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 142 | 16:30:10 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 143 | 16:30:15 | step 2: play | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 144 | 16:30:20 | step 2: play | none | after vignette | 11.04% | 7.12% | 0 / 0 / 0 |
| 145 | 16:30:25 | step 2: play | none | after vignette | 11.04% | 7.12% | 0 / 0 / 0 |
| 146 | 16:30:33 | step 3: view ui | none | after vignette | 13.99% | 7.08% | 0 / 0 / 0 |
| 147 | 16:30:36 | step 3: view ui | none | after vignette | 13.94% | 7.16% | 0 / 0 / 0 |
| 148 | 16:30:41 | step 3: view ui | none | after vignette | 13.94% | 7.16% | 0 / 0 / 0 |
| 149 | 16:30:46 | step 3: view ui | none | after vignette | 14.21% | 7.25% | 0 / 0 / 0 |
| 150 | 16:30:51 | step 3: view ui | none | after vignette | 13.96% | 7.12% | 0 / 0 / 0 |
| 151 | 16:30:58 | step 3: view ui | none | after vignette | 11.04% | 7.12% | 0 / 0 / 0 |
| 152 | 16:31:01 | step 3: view ui | none | after vignette | 11.02% | 7.11% | 0 / 0 / 0 |
| 153 | 16:31:06 | step 3: view ui | none | after vignette | 11.02% | 7.11% | 0 / 0 / 0 |
| 154 | 16:31:11 | step 3: view ui | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 155 | 16:31:18 | step 3: view ui | none | after vignette | 13.99% | 7.11% | 0 / 0 / 0 |
| 156 | 16:31:21 | step 3: view marks | none | after vignette | 13.94% | 7.15% | 0 / 0 / 0 |
| 157 | 16:31:26 | step 3: view marks | none | after vignette | 13.94% | 7.15% | 0 / 0 / 0 |
| 158 | 16:31:31 | step 3: view marks | none | after vignette | 14.21% | 7.25% | 0 / 0 / 0 |
| 159 | 16:31:36 | step 3: view marks | none | after vignette | 14.21% | 7.12% | 0 / 0 / 0 |
| 160 | 16:31:43 | step 3: view marks | none | after vignette | 11.07% | 7.14% | 0 / 0 / 0 |
| 161 | 16:31:46 | step 3: view marks | none | after vignette | 11.02% | 7.11% | 0 / 0 / 0 |
| 162 | 16:31:51 | step 3: view marks | none | after vignette | 11.02% | 7.11% | 0 / 0 / 0 |
| 163 | 16:31:56 | step 3: view marks | none | after vignette | 11.07% | 7.12% | 0 / 0 / 0 |
| 164 | 16:32:01 | step 3: view marks | none | after vignette | 11.04% | 7.12% | 0 / 0 / 0 |
| 165 | 16:32:06 | step 3: view hudless | none | after vignette | 13.96% | 7.10% | 0 / 0 / 0 |
| 166 | 16:32:11 | step 3: view hudless | none | after vignette | 11.81% | 6.69% | 0 / 0 / 0 |
| 167 | 16:32:16 | step 3: view hudless | none | after vignette | 14.98% | 6.82% | 0 / 0 / 0 |
| 168 | 16:32:21 | step 3: view hudless | none | after vignette | 12.06% | 6.79% | 0 / 0 / 0 |
| 169 | 16:32:26 | step 3: view hudless | none | after vignette | 8.90% | 6.65% | 0 / 0 / 0 |
| 170 | 16:32:29 | step 3: view hudless | none | after vignette | 8.90% | 6.65% | 0 / 0 / 0 |
| 171 | 16:32:34 | step 3: view hudless | none | after vignette | 8.90% | 6.66% | 0 / 0 / 0 |
| 172 | 16:32:42 | step 3: view alpha, then no view | none | after vignette | 11.86% | 6.65% | 0 / 0 / 0 |
| 173 | 16:32:45 | step 3: view alpha, then no view | none | after vignette | 11.81% | 6.69% | 0 / 0 / 0 |
| 174 | 16:32:52 | step 3: view alpha, then no view | none | after vignette | 14.98% | 6.83% | 0 / 0 / 0 |
| 175 | 16:32:57 | step 3: view alpha, then no view | none | after vignette | 12.06% | 6.79% | 0 / 0 / 0 |
| 176 | 16:33:00 | step 3: view alpha, then no view | none | after vignette | 12.06% | 6.79% | 0 / 0 / 0 |
| 177 | 16:33:07 | step 3: view alpha, then no view | none | after vignette | 8.90% | 6.65% | 0 / 0 / 0 |
| 178 | 16:33:10 | step 3: view alpha, then no view | none | after vignette | 8.90% | 6.65% | 0 / 0 / 0 |
| 179 | 16:33:15 | step 3: view alpha, then no view | none | after vignette | 8.90% | 6.65% | 0 / 0 / 0 |
| 180 | 16:33:20 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 181 | 16:33:25 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 182 | 16:33:30 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 183 | 16:33:35 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 184 | 16:33:40 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 185 | 16:33:45 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.65% | 0 / 0 / 0 |
| 186 | 16:33:50 | step 3: view alpha, then no view | none | after vignette | 8.94% | 6.66% | 0 / 0 / 0 |
| 187 | 16:33:55 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 188 | 16:34:00 | step 3: view alpha, then no view | none | after vignette | 8.92% | 6.66% | 0 / 0 / 0 |
| 189 | 16:34:05 | step 3: view alpha, then no view | none | after vignette | 8.94% | 6.66% | 0 / 0 / 0 |

</details>
