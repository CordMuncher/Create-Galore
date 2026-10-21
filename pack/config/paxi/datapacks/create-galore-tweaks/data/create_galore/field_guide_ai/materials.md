<!-- sources sha256:3b9d2d6012e0ab89fb0e4c20e7f2e43fb9ac2c357a2996e967de75cee0d43e32 -->
# Ores and Metals (materials)

## materials/ore_guide | Ore Guide
- Where each metal comes from, read from mods' worldgen data. Depths are Y levels (see glossary/y_level), Overworld unless noted.
- Ores go further crushed (see materials/ore_doubling). Unknown block: What's This Block? (see materials/whats_this_block). Drillable veins: Ore Excavation (see industry/ore_excavation).
- Summary: zinc for brass: Overworld Y -63 to 70; lead ore is Create Nuclear's (galena gives a little); titanium and tungsten exist only on Northstar's planets.
- Vanilla and Create: coal, copper, iron, gold, redstone, lapis, diamond, emerald are vanilla; terrain mods such as Tectonic change where they end up.
- Zinc from Create: Y -63 to 70. Brass (see create_basics/brass_and_assembly) = copper + zinc.
- Zinc Ore [create:zinc_ore]: Overworld Y -63 to 70, stone and deepslate. Spread evenly, no depth beats another: look in caves. Northstar's planets have own zinc ores; both count as zinc ore in recipes.
- Create Nuclear: Lead Y -16 to 60; Uranium Y -63 to 16, sparse; Thorium Y -16 to 55; Nitrate Y -63 to 64 (reactor coolant). Autunite [createnuclear:autunite]: building stone, striated veins below Y 16.
- Lead Ore [createnuclear:lead_ore]: the only lead ore. Galena also crushes to a little lead. Mining drops Raw Lead [createnuclear:raw_lead]. Ore block itself has no crushing recipe; crush the raw lead.
- Uranium Ore [createnuclear:uranium_ore]: deep, rare; feeds the reactor (see industry/nuclear). Alex's Caves (see exploration/alexs_caves) adds own uranium in Toxic Caves.
- TFMG: Nickel Y -63 to 20; Lithium Y -63 to -5, sparse; Bauxite: stone that crushes to powder, start of aluminum; Galena: crushes to some lead; Lead: off. Oil: Oil (see industry/tfmg_oil).
- Nickel Ore [tfmg:nickel_ore]: Y -63 to 20; nickel goes into TFMG alloys such as constantan. Lithium ore deeper, below Y -5.
- Other Overworld ores: Platinum (Create Propulsion) Y -63 to about 16; Mithril (Iron's Spells 'n Spellbooks (see exploration/irons_spellbooks)) Y -63 to -38, very deep.
- Platinum Ore [createpropulsion:platinum_ore]: Create Propulsion's metal; crushes and washes (see glossary/washing) like any Create ore. Deep Overworld.
- Mithril Ore [irons_spellbooks:mithril_ore]: Y -63 to -38. Crushing gives Crushed Mithril [create_wizardry:crushed_mithril] from Create: Wizardry (see exploration/create_wizardry), which can be washed or smelted.
- Northstar planets (Planets (see space/planets)): titanium and tungsten exist only off-world, in planet-specific blocks. Titanium: Moon, Mars, Mercury, Venus. Tungsten: Mercury only. Zinc, copper, iron, gold, redstone, diamond: all four planets.
- Titanium Ore [northstar:mars_titanium_ore]: Moon, Mars, Mercury, Venus each have own block. Drops raw titanium, smelts to titanium ingot.
- More planet ores: Glowstone: Moon, Mercury, Venus; Lapis: Moon, Mercury; Nether quartz: Mars, Venus; Coal: Venus. Mercury's tungsten ore is plentiful; crushes to Crushed Raw Tungsten [northstar:crushed_raw_tungsten].
- Other dimensions' ores: Aether: zanite, gravitite, ambrosium, icestone in holystone. Alex's Caves: galena iron in Magnetic Caves, uranium in Toxic Caves, coal and redstone in Forlorn Hollows. Better End: thallasium, ender, amber ore.
- Zanite Ore [aether:zanite_ore]: Aether (see exploration/aether) holystone. Zanite and ambrosium ores have Create crushing recipes, so a Crushing Wheel (see create_basics/crushing) doubles them too.
- Not mined (no own ore generation): Bronze (Alloyed (see industry/alloyed)) and steel (TFMG (see industry/tfmg_steel)) are alloys; Create Crafts & Additions electrum is an alloy; Cataclysm's metals are not mined. Twilight Forest (see exploration/twilight_forest) grows Iron, Copper and Gold Oreberry bushes instead of veins.
- Related: Ore Doubling (see materials/ore_doubling), Shared Metals (see materials/shared_metals), Core Materials (see create_basics/materials), Dimensions (see exploration/dimensions)

## materials/ore_doubling | Ore Doubling
- Crushing gets more metal from an ore block than smelting; main reason to build Crushing Wheels (see create_basics/crushing).
- One ore block -> 1 crushed ore, 75% chance of a second, experience nuggets, sometimes stone back. About 1.75 crushed ores per block; 2.25 from deepslate ore. Mine blocks with Silk Touch.
- Crushing Wheel [create:crushing_wheel]: feed it ore blocks (Silk Touch).
- Raw ore (what normal mining drops) crushes to one crushed ore: no gain. Only the ore block itself doubles.
- Silk Touch or Fortune (both need an enchanted pickaxe), per iron, gold or zinc ore: Fortune III about 2.2 raw ore (Fortune II 1.75, Fortune I 1.33); Silk Touch then crush 1.75, or 2.25 from deepslate. Copper drops 2-5 raw: about 7.7 with Fortune III vs 5.25 crushed (7.25 deepslate).
- Then smelt or wash (see glossary/washing) the crushed ore. Washing = Fan (see create_basics/fans) blowing through water: 9 nuggets + chance of byproduct (iron may add redstone; check JEI (see start_here/tools) per metal). Blasting: one ingot.
- Crushed Raw Iron [create:crushed_raw_iron]: blast into one ingot, or wash with a Fan into nuggets plus byproduct chance. Crushed zinc works the same.
- Which metals: Create covers copper, iron, gold, zinc. Tag (see glossary/tags) recipes also cover platinum, uranium, nickel, raw lead when a mod supplies them. Create's recipe for lead ore blocks is off.
- TFMG (see industry/tfmg_steel): nickel, lithium, bauxite to powder, galena to a little lead; its metals get a smaller bonus, about 1.25. Create Nuclear (see industry/nuclear): crushed uranium to powder, thorium, nitrate.
- Crushed Raw Lithium [tfmg:crushed_raw_lithium]: TFMG lithium ore crushes to this, then blasts to an ingot. Nickel ore crushes to Crushed Raw Nickel [create:crushed_raw_nickel].
- Create Propulsion adds platinum; Iron's Spells mithril. Northstar planet ores crush at the full rate into raw items (e.g. raw titanium; smelts straight to an ingot). Venus coal ore crushes to extra coal.
- Raw Titanium [northstar:raw_titanium_ore]: crush any planet's titanium ore for about 1.75, then smelt or blast. Planets (see space/planets)
- Aether zanite and ambrosium ores give 1.75 gems or shards each. No crushing recipes for Alex's Caves, Better End or Twilight Forest ores; smelt normally.
- Related: Ore Guide (see materials/ore_guide), Crushing and Ore Doubling (see create_basics/crushing), Encased Fans (see create_basics/fans), Shared Metals (see materials/shared_metals), Ore Excavation (see industry/ore_excavation)

## materials/shared_metals | Shared Metals
- Several mods add the same metal; each has one canonical item. Other mods' recipes produce and use it, so only one kind exists in practice. Leftovers convert 1:1 in a crafting grid.
- Steel is TFMG's; lead Create Nuclear's; bronze Alloyed's. Steel plates are TFMG's Heavy Plate.
- Steel: TFMG [tfmg:steel_ingot]: canonical steel; Alloyed, Big Cannons, Nuclear recipes now give this. See TFMG Steel (see industry/tfmg_steel).
- Old steel: Alloyed (see industry/alloyed), Big Cannons (see industry/cannons), Create Nuclear (see industry/nuclear) still have own steel items but no recipe makes them. Put old ingot, nugget or block in a crafting grid -> TFMG steel. Recipes asking for the steel tag (see glossary/tags) still take either.
- Steel plates: TFMG's Heavy Plate (three presses) is the one you make; Alloyed's one-press Steel Sheet is off. Both in steel plate tag, so old sheets still work in recipes.
- Lead: Create Nuclear's; TFMG lead converts; only Nuclear's ore generates; galena crushes to some. Bronze: Alloyed's; Big Cannons bronze converts. Cast iron: still TFMG and Big Cannons, shared by tag.
- Lead Ingot [createnuclear:lead_ingot]: smelt Raw Lead from Create Nuclear's ore; TFMG's lead recipes accept this ingot.
- Cast Iron Ingot [createbigcannons:cast_iron_ingot]: shared by TFMG and Create Big Cannons via cast iron tag; either works in tag-based recipes.
- Copper wire is Create Crafts & Additions' (see Electricity (see industry/electricity)); TFMG's converts. Each mod keeps own copper spool (they wire different machines). Sulfur dust is TFMG's; Alex's Caves sulfur converts. Salt and cocoa also unify, in Ratatouille (see food/ratatouille).
- Not shared (each from one mod): titanium, tungsten, platinum, mithril, zinc. Northstar's Martian Steel has own tag, not generic steel. Raw uranium joins Create Nuclear and Alex's Caves.
- Related: Ore Guide (see materials/ore_guide), TFMG Steel (see industry/tfmg_steel), Alloyed: Steel and Bronze (see industry/alloyed), Nuclear Reactors (see industry/nuclear), Electricity (see industry/electricity), Core Materials (see create_basics/materials)

## materials/whats_this_block | What's This Block?
- Odd ore/stone you dig up has a use. Most are metal ore, or stone a Crushing Wheel (see create_basics/crushing) turns into something useful; rest are building stone. JEI shows recipes for what a block drops: look up the drop, not the ore block.
- Finding Zinc [create:zinc_ore]: Y -63 to 70, spread evenly, about as common as iron. No best depth; caves anywhere Y -60 to 60 best. Blue asurine also crushes to zinc.
- Finding Andesite [minecraft:andesite]: Terralith keeps andesite out of hillsides; big patches below about Y 60. Or craft: diorite + cobblestone makes two. Diorite common around Y 0 to 30.
- Nitrate [createnuclear:nitrate]: drops from Nitrate Ore. Smelt it, blow a fan through powder snow onto it, then mix (see glossary/mixing) with ice into Liquid Nitrogen. A reactor cooled with it handles 4 times the heat water can. Nuclear (see industry/nuclear)
- Raw Thorium [createnuclear:raw_thorium]: drops from Thorium Ore; can't be smelted: crush to dust, mix into fluid, compact into ingots for Thorium Rods. Nuclear (see industry/nuclear)
- Stones that give metal (Create striated stones, crush (see create_basics/crushing)): Crimsite (red) -> iron; Veridium (green) -> copper; Ochrum (yellow) -> gold; Asurine (blue) -> zinc. TFMG galena -> lead. Each gives a little per block.
- Stones that give powder (TFMG): Bauxite -> bauxite powder (start of aluminum); Lignite -> coal; Fireclay -> fireclay balls that smelt into Fireproof Bricks for the blast furnace (see industry/tfmg_steel). Nether sulfur crushes to sulfur dust.
- Building stone (stonecutter shapes): Scoria and scorchia (Create); Limestone (also crushes to TFMG limesand); Autunite (Create Nuclear); tuff, calcite, dripstone. Cut or polished limestone crushes to nitrate and lead.
- Other ores: lead, uranium, nickel, lithium, platinum, mithril smelt or crush like any metal. Depths and uses: Ore Guide (see materials/ore_guide).
- In depth: crushed nickel has no washing recipe; smelt it.
