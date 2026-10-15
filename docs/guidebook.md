# Editing the Field Guide

The **Create Galore Field Guide** is a [Patchouli](https://modrinth.com/mod/patchouli) book. It is an *external* book: Patchouli reads it from the instance's `patchouli_books/` folder, so it ships inside the `.mrpack` as an override with no extra mod or resource pack.

```
pack/patchouli_books/create_galore/
  book.json                     name, landing text, look
  en_us/categories/<id>.json    the 13 sections
  en_us/entries/<category>/<entry>.json
```

An entry's ID is `<category>/<entry>`. Its book ID is `patchouli:create_galore`.

How players get it (in `pack/config/paxi/datapacks/create-galore-tweaks/data/create_galore/`):
- `advancement/give_field_guide.json` + `function/give_field_guide.mcfunction`: one copy on first join.
- `recipe/field_guide.json`: Book + Andesite, shapeless.

## Rules learned the hard way

One bad page makes Patchouli load the **whole book empty** ("Loading error!"), so check the log for `create_galore` after any change.

- **Item IDs must be registered items**, not just names in a lang file. Some blocks (Blaze Burner with Straw, Stuffed Hoglin, Ship Anchor) have no item.
- `patchouli:relations` pages need full IDs: `"patchouli:food/bees"`. Text links (`$(l:food/bees)`) don't.
- An entry `icon` is one item. Spotlight `item` may list several, comma-separated.
- Spotlights need `"link_recipe": true`, or searching for that item's name won't find the entry.
- Entry names over about 26 characters get cut off in the chapter list.
- Page size: about 30 characters a line. A full text page holds about 17 lines, a page with a title about 13, a spotlight about 9. `book.json` sets `text_overflow_mode: resize` as a safety net.
- `book.json` is only read at game start. Entry text reloads with F3+T.
- Only crafting, smelting and similar vanilla recipe types can be shown on a page. For Create machine recipes, point to JEI.

## Checking it

```
python3 -I tools/extract_mod_data.py <instance>/mods /tmp/data
ITEMS=/tmp/data/items.tsv RECIPES=/tmp/data/recipes python3 tools/validate_guidebook.py
```

Vanilla items and recipes aren't in the mod jars: append them from the Minecraft client jar's `assets/minecraft/lang/en_us.json` and `data/minecraft/recipe/` the same way.

The validator checks JSON, links, relations, item and recipe IDs against the jars, name length and approximate page length. `items.tsv` comes from lang files, so it can't catch an ID that has a name but no item. Boot the game to be sure.
