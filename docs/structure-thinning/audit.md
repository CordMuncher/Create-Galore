# Structure thinning: audit

The final check of the 0.2.0 structure thinning, after all four layers were merged (surface #42, underground #39,
underwater #40 and #41, sky #38). It looks at the whole pack at once, on seeds no layer thread used, and fixes the one
gap the layer threads left (sky airships and blimps). The pack's targets and bars:

| Layer | Target | Bar |
|---|---|---|
| Surface | 2× vanilla's surface, per km² of land | ±10%, 385+ structures |
| Underground | 3× vanilla's underground, mineshafts not counted | ±10%, 385+ |
| Underwater | 3× vanilla's underwater, per km² of ocean | ±10%, 385+ |
| Sky | 1× vanilla's surface | ±15%, 170+ |

The script is `tools/structure-thinning/audit.py`; it judges every layer the way its own thread did. The scan
folders are in the project files under `modpack/structure-density/audit/`.

## Result

Pack on two fresh seeds (424242 and 8675309, ±4000 blocks each, 128 km²), with the sky fix below, against vanilla
pooled over ten seeds (640 km²):

| Layer | Pack structures | Pack | Vanilla | Target | Pack ÷ target | Verdict |
|---|---:|---:|---:|---:|---:|---|
| Surface | 614 | 8.87 per km² of land | 4.13 | 8.25 | **1.07×** (95%: 0.99–1.16) | pass |
| Underground | 1,916 | 14.96 per km² | 5.18 | 15.53 | **0.96×** (0.92–1.01) | pass |
| Underwater | 3,875 | 65.90 per km² of ocean | 22.85 | 68.56 | **0.96×** (0.93–0.99) | pass |
| Sky | 351 | 2.74 per km² | 2.75 (vanilla surface) | 2.75 | **1.00×** (0.89–1.10) | pass |

Real generation matches the scan: 194 of 194 structure starts (±800 blocks, seed 424242, before the fix) and 130 of
130 (±600 blocks, same seed, with it).

## Judgment calls

1. **Vanilla is pooled over ten seeds, not taken from the same seed.** The layer threads compared the pack with
   vanilla on the same seed. That pairing is loose: Terralith and Tectonic give the pack different biomes from
   vanilla on the same seed, and vanilla's own surface rate swings with its biome mix, from 3.33 to 5.14 structures
   per km² of land across the ten seeds. Seed 424242 is one of vanilla's sparsest (3.36), so against it alone the
   surface reads 1.36× while the pack's own density there (9.1 per km² of land) is close to the other seeds'. Same
   seed, both seeds pooled: surface 1.24×, underground 1.03×, underwater 1.06×, sky 1.04×. Against ten seeds of
   vanilla every layer is in range. The pack is steady: 8.4 to 9.1 surface structures per km² of land on these
   seeds, about the same as the surface thread's three seeds.
2. **Alex's Caves' acid pits don't count** toward the underground. They carve the Toxic Caves (one every ~100
   blocks inside that biome, no loot), the way the ocean trench carves the Abyssal Chasm, which the surface thread
   already left out. Seed 424242 has a Toxic Caves area near spawn with 111 of them; the underground thread's seeds
   had none. Counted, the underground is 1.09× against same-seed vanilla, still in range. Nothing was changed.
3. **The sky fix (see below) adds sets instead of reweighting the shared ones.** Raising the sky kinds' weights inside
   the Aeronautics Structures set was tried first: the crash sites (surface) doubled too, because a set tries its other
   kinds when the picked one can't stand on a spot.

## Sky fix: airships, blimps and heavenly fortresses

The sky thread measured these 8 kinds at a third of their share, because they live in sets the surface thread owns and
spaces for its own kinds. Per 256 km² (seeds 12345 and 20261017, ±8000 blocks, the sky thread's own seeds):

| Kind | Asked (sky thread) | Before | After |
|---|---:|---:|---:|
| Aeronautics airship, air balloon, pillager air balloon, pillager airship | ~22 each | 8 each | **22 each** (17 to 26) |
| Dungeons Arise heavenly challenger, conqueror, rider | ~7.5 each (rare) | 2 each | **8.3 each** (4 to 11) |
| Dungeons Arise small blimp | ~22 | 15 | **19** |

Three top-up sets in the create-galore-tweaks datapack (`data/create_galore/worldgen/structure_set/`) place only these
kinds, so the original sets and their surface kinds are untouched (the surface count is the same 614 before and
after): `aeronautics_sky` (4 kinds, spacing 63), `dungeons_arise_sky` (the 3 heavenly fortresses, spacing 108) and
`dungeons_arise_blimp` (spacing 360). Heavenly fortresses stay about a third as common as an ordinary sky kind. Sky
layer on the audit seeds: 0.90× before, 1.00× after.

## Other checks

- **Rare stays rarer.** Underground, underwater and sky: every rare-by-design kind is below the median of the layer's
  ordinary kinds (underground median 87 vs rare 33; underwater 378 vs 2; sky 10 vs 6.5, per 128 km²). Surface: rare
  kinds' median 1.5 against 3. Above the ordinary median: YUNG's ocean monuments (25; kept at their own rate on
  purpose, sponges and Elder Guardians are found nowhere else, and they are already rarer than vanilla's) and When
  Dungeons Arise's three ships out at sea (galley 9, corsair 5, pirate ship 4). The ships are thinned in proportion
  with the rest of the major set, but they take the spots its land dungeons can't use over the ocean, so they land at
  a common kind's rate. Left as is: the sea has few other surface kinds.
- **Biomes.** Surface: deserts and badlands 1.9× the land average, from five mods' desert builds (LTAB desert houses,
  Terralith outposts, Better Desert Temples, Cataclysm, Towns and Towers) on a small area (3.6 km²), not one mod
  flooding it; peaks 0.6×, rivers 0.5× (thin strips). Underwater: deep cold ocean 1.9× (Revamped Shipwrecks and
  ocean ruins), trenches and seagrass beds 0.2× to 0.5× (fewer kinds allowed there), as the underwater thread found.
  Sky: rivers 1.9× (small area), every other family 0.8× to 1.5×. Nothing crowded or empty by accident.
- **Layers don't interfere.** Other layers' sets make up 13% of the surface (35 beached shipwrecks, 35 ruined
  portals), 2% of the underground, 4% of the underwater and 0.3% of the sky, all counted in the totals above.
- **Ocean ruined portals.** 0.90 per km² of ocean under water, against vanilla's 2.48: the surface thread's portal
  thinning (spacing 40 → 65) already took them below vanilla. Nothing to do.
- **Ancient cities** are 4× vanilla's count on the same spacing (68 against vanilla's 17 on the same 128 km²), most
  likely because the pack's terrain holds more Deep Dark. Vanilla spacing kept, as the underground thread decided.
- **Boss compasses and the Moon path.** Overworld thinning doesn't touch the Twilight Forest, the Aether or the
  Nether; the Hullbreaker's Abyssal Chasm is kept whole. A fresh world with the full pack finds all four compass
  lairs and the Abyssal Chasm with `/locate` (below).

## Real world

A dedicated server with the full pack (0.2.0, Twilight Forest included) on a new world, seed 31415926:

- Started in 77 s and stopped cleanly; no world generation or structure errors in the log (the only errors are the
  known Sable copycat tags and a Cozy Cafe menu for a missing pie slice, both unrelated).
- `/locate` in the Twilight Forest: Naga Courtyard 81 blocks, Lich Tower 247, Labyrinth 468, Hydra's Lair 600. The boss
  compasses use the same search.
- Overworld: Abyssal Chasm (Hullbreaker) 532 blocks, nearest village 537, heavenly fortress 1,765, Aeronautics airship
  2,781, small blimp 5,506. Sky builds are meant to be the sparsest; you cover that ground by airship.
