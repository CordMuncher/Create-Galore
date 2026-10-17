# Structure thinning: surface

Part of the 0.2.0 structure thinning (one thread per layer: surface, underground, underwater, sky; a final
audit checks them together). This file is the surface layer: the target, how it was reached, and every
judgment call, so Emre can see what was decided and why.

The numbers come from Structure Scan (`tools/structscan-mod`), which finds every structure from the seed
without generating chunks. The plan is worked out by `tools/structure-thinning/plan.py` and written into the
configs by `tools/structure-thinning/apply.py`. `policy-surface.txt` holds the hand-made exceptions,
`surface/plan.md` and `surface/plan.tsv` the resulting spacing for every set.

## Target

Emre (2026-10-17): the surface gets **2× vanilla's surface density**. Vanilla's surface on seed 12345 (144 km²,
mineshafts left out) is 2.76 structures per km², so the target is **5.53 per km²**: on average one surface
structure per 0.18 km², about 425 blocks between neighbours. Before this change the pack had 42.8 per km²
(15.5×).

Every kind of surface structure gets an equal share of the target, so a mod with 10 kinds turns up about five
times as often as a mod with 2. A kind that is already rarer than its share keeps the rate its mod gives it,
and the share it doesn't use goes to the others. Nothing is made more common than it was.

## Decisions log

Each entry: the problem, the call, and why. Newest last.

1. **Mineshafts don't count and aren't thinned** (Emre's call). Better Mineshafts land in the surface layer in
   the scan because their tall shafts push the middle of the bounding box up; they are left alone.
2. **Who owns which structure set.** Spacing is set per structure set, and some sets place structures in more
   than one layer. Agreed between the four threads: a set belongs to the layer where most of its structures
   land in the seed 12345 scan. Two mixed sets were handed to this thread by agreement with the sky thread:
   When Dungeons Arise's major structures and Create: Aeronautics Structures (surface crash sites plus sky
   balloons and airships). Their sky kinds are thinned to the sky thread's share per kind (3.7 per 64 km²).
3. **Ice Maze arches and spirals stay as they are** and don't count toward the target (agreed with the
   underwater thread, which owns that Aquamirae set). They are the Ice Maze itself: the biome's terrain is
   built from them, with no loot or mobs. Emre: a biome whose gimmick is its structures keeps them.
4. **Alex's Caves' ocean trench stays as it is** and doesn't count. It is the "structure" that carves the
   Abyssal Chasm, where the Hullbreaker (the Immortal Embryo for the Stellar Core) lives. Thinning it would
   break the biome.
5. **Wild cherry trees and raspberry bushes (Berries and Cherries) stay as they are** and don't count. They are
   foraging plants placed as structures, not buildings to discover (245 of them in 64 km², which alone would
   eat 70% of the surface budget).
6. **Supplementaries road signs and Terralith rubble piles count as structures.** They are small, but they are
   things you find and they have loot or point the way; they get the same share as any other kind.
7. **Room left for other layers' sets.** Beached shipwrecks, balloons that came down on land and the like are
   counted on the surface but belong to the other threads. They are about 0.85 per km² today and will be
   thinned by their owners, so 0.45 per km² of the target is left for them: this thread's sets aim at 5.08.
8. **Each mod's own config is used, not datapack overrides.** Cristel Lib reads every set's spacing from
   `config/cristellib/<mod>/structure_placement_config.json5` (vanilla from `config/vanilla_structures/`,
   Towns and Towers from `config/towns_and_towers/`) and applies it when the world loads, so a datapack
   override would be overwritten. Aquamirae's pirate builds come from `config/obscuria/aquamirae-common.toml`
   and Create Structures Arise from `config/create_structures_arise-server.toml`. The pack already thinned a
   few underground sets this way.
9. **Moog's structures read their config number differently** (about 1.66× the config value becomes the real
   spacing), so the first try left the Long Ship and Wanderer Raft 65% wider than planned. `apply.py` now
   scales each config number by the planned ratio instead of writing the scanned number.
10. **Biome balance.** Emre: no biome should be crowded or empty by accident. The scan now records every
    structure's biome, and `biome_check.py` groups biomes into families (forest, plains, snowy, desert,
    mountains, ...). Before thinning the land families ranged from 0.9× to 2.1× the average; with the plan,
    land families sit between about 0.7× (steep peaks and cliffs, where few buildings can stand) and 1.3×
    (forests, which many village and tavern kinds are made for) of the land average, rivers and beaches lower
    still because they are thin strips. No family needed its own correction; the spread comes from which
    biomes each mod builds for, not from one mod flooding a biome. Measured after thinning on three seeds
    (see the result below): forests, deserts and jungles 1.4× the land average, plains and savanna 0.9×,
    peaks and cliffs 0.6×, rivers 0.4×.
11. **Overworld only.** The targets are about the Overworld. Sets that are also used in the Nether or the End
    change there too, but no Nether or End set was planned on its own.
12. **The Moon path doesn't depend on surface structures.** The Stellar Core needs the Hullbreaker (Abyssal
    Chasm, kept), Twilight Forest and Aether items (their own dimensions, untouched), the Wither, the Ender
    Dragon (strongholds belong to the underground thread) and TFMG circuit boards (crafted). The boss
    compasses point at Twilight lairs, which this doesn't touch.
13. **Field Guide.** Structures gets a "How Far Apart" page (twice vanilla on the surface, shared by kind, the
    exceptions above). Hangars said they were about 500 blocks apart; now about 900.
14. **Create Structures Arise's three sky builds** (air drop, mini sky village, pillager steampunk airship) sit
    in the same config file as its surface builds, so this thread set them to the sky thread's numbers
    (193, and 291 for the rare steampunk airship). The sky kinds inside When Dungeons Arise's and Aeronautics Structures' mixed sets keep their
    mods' own weights; only the set spacing changes.
15. **Rare builds stay rarer** (Emre's clarification, 2026-10-17). Boss lairs and the big dungeons with the
    best loot are not flattened to an even share. They are thinned in proportion with the whole surface (each
    keeps 19% of what it had, the same as the layer overall), and never more than half an ordinary kind's
    share, so they always stay rarer than everyday builds. Treated as rare: When Dungeons Arise's major
    dungeons, Mowzie's Mobs lairs (Frostmaw, monastery, Umvuthana grove), Cataclysm's surface lairs, Iron's
    Spellbooks' towers, forts and huts, and Let the Adventure Begin's castle and grotto.
16. **Mansions and ocean monuments keep the rate they have.** They hold items found nowhere else (totems,
    sponges) and are already as rare as vanilla makes them, or rarer (YUNG's monuments are spaced 50 chunks
    against vanilla's 32), so they aren't thinned at all. They still count toward the target.
17. **Villages as a whole stay near vanilla's rate.** Each village kind gets an ordinary share, so vanilla's
    own villages become rarer, but Towns and Towers (14 village kinds), Dungeons and Taverns and Terralith
    add their own: 37 villages per 64 km² in the final scans, against vanilla's 51.
18. **Judged per km² of land.** How much ocean a 36 km² square holds swings a lot between seeds (the pack's
    squares on seeds 777 and 4242 were 45% and 56% land, vanilla's 70% and 74%), and surface buildings stand
    on land. So the check counts the structures standing on land, per km² of land, against vanilla's surface
    structures on land per km² of land on the same seeds (4.1 to 4.6 on the three seeds, a steady yardstick).
    Ships, oil rigs, ocean villages and monuments out at sea are counted separately: vanilla has almost
    nothing there to compare with, so they just get their ordinary share of the plan.
19. **Shared config files.** Shipping a mod's Cristel Lib file pins every set in it, including sets another
    thread changed with a datapack override. The underground thread's three overrides (Terralith's cave
    builds 18/5, Wrought Chambers 37/12, LTAB cave ruins 40/0) are written into the shipped files too, and
    the underwater thread's vanilla ocean ruins (15/5) too. A Cristel file left at defaults turned out not to override datapack values on the underwater thread's server, but matching both is safe either way.
20. **Aeronautics Structures was missed at first.** Its set lands 34 in the sky and 33 on the surface, so the
    majority rule gave it to the sky thread, while the sky thread had handed it to this one. Now owned here
    explicitly (`own` in the policy) and spaced 31 → 54 chunks.

## Result

Final check (`final_check.py`) on three seeds that were not used for planning, 777, 4242 and 31337, ±3000
blocks each (106 km² in all), against vanilla on the same seeds:

| | Structures | Target | Ratio |
|---|---:|---:|---:|
| This thread's sets, on land | 425 | 445 | **0.95×** (95% interval 0.86–1.04×) |
| Whole surface layer on land, kept sets left out (other threads' sets as they were on the test server) | 485 | 493 | 0.98× |

- Vanilla has 4.2 to 4.6 surface structures per km² of land on these seeds; the pack's own sets now have
  7.6 per km² of land (target 2× vanilla minus the room left for other layers' sets).
- Out at sea (ships, oil rigs, ocean villages, monuments): 2.5 per km² of ocean, not judged against vanilla.
- Rare kinds came out at 1.75 per kind on average against 3.6 for ordinary kinds, so they stay about half as
  common.
- Before: 42.8 per km² of square on seed 12345 (15.5× vanilla). After: 4.0 per km² of square for this
  thread's sets.

Scan folders are in the project files under `modpack/structure-density/` (`surface-final-*`, `vanilla-seed*-biomes`).
