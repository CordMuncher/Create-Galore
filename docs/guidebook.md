# Editing the Field Guide

The **Create Galore Field Guide** is a [Modonomicon](https://modrinth.com/mod/modonomicon) book. Each section is a map: entries are nodes, and a line from one entry to another means "this comes first". Every entry is open from the start; there are no quests, locks or rewards.

The book is data, not a mod. It lives in the always-on Paxi datapack, so the server sends it to every player and singleplayer loads it the same way:

```
pack/config/paxi/datapacks/create-galore-tweaks/data/create_galore/modonomicon/books/field_guide/
  book.json                       name, look, landing text
  categories/<id>.json            the 14 sections (tabs)
  entries/<category>/<entry>.json one node each
```

An entry's ID is `<category>/<entry>`. The book ID is `create_galore:field_guide`.

How players get it (same datapack, `data/create_galore/`):
- `advancement/give_field_guide.json` + `function/give_field_guide.mcfunction`: one copy on first join.
- `recipe/field_guide.json`: Book + Andesite, shapeless.
- By hand: `/give @s modonomicon:modonomicon[modonomicon:book_id="create_galore:field_guide"]`

Patchouli stays installed only because Iron's Spellbooks and Productive Bees ship their own Patchouli manuals.

## An entry

```json
{
  "category": "create_basics",
  "name": "Gearboxes",
  "description": "One line shown when hovering the node.",
  "icon": {"item": "create:gearbox"},
  "x": 2, "y": 1,
  "parents": [{"entry": "create_basics/transmission", "draw_arrow": true, "line_reversed": true}],
  "pages": [
    {"type": "modonomicon:text", "title": "Gearboxes", "text": "Markdown text. **Bold**, [links](entry://create_basics/rotation)."},
    {"type": "modonomicon:spotlight", "item": {"item": "create:gearbox"}, "title": "", "text": "..."},
    {"type": "modonomicon:crafting_recipe", "recipe_id_1": "create:crafting/kinetics/gearbox", "text": "..."}
  ]
}
```

- **Graph**: `x`/`y` are grid cells (30 px). Parents must be in the same category, and the parent sits to the left of its children. Keep one node per cell. A `line_reversed: true` line drops down the parent's column then turns right into the child; `false` runs along the parent's row then turns into the child's column. Use `true` for a parent's own children and `false` for a second parent, so lines don't run under other nodes.
- **Text** is Markdown: `**bold**`, `*italic*`, `- list item`, a blank line for a new paragraph, `\` at a line end for a line break. Links to other entries are `[text](entry://category/entry)`.
- Write `%` as `%%` and `_` as `\_` in text, or the line shows a format error or turns italic.
- The first text page carries the entry's name as its `title` (Modonomicon doesn't add it).
- A spotlight `item` is one item, or a list `[{"item": "a"}, {"item": "b"}]` that cycles.
- Only vanilla crafting-table recipes can be shown on a page. For Create machine recipes, point to JEI.
- Text that is too long for a page shrinks to fit rather than overflowing; split long pages anyway so it stays readable.

## Checking it

```
python3 tools/validate_guidebook.py            # structure, links, graph
python3 -I tools/extract_mod_data.py <instance>/mods /tmp/data
ITEMS=/tmp/data/items.tsv RECIPES=/tmp/data/recipes python3 tools/validate_guidebook.py   # plus item and recipe IDs
```

Vanilla items and recipes aren't in the mod jars: append them from the Minecraft client jar's `assets/minecraft/lang/en_us.json` and `data/minecraft/recipe/` the same way.

`items.tsv` comes from lang files, so it can miss real items whose names live under another key (`cold_sweat:hearth` is one) and can't catch an ID that has a name but no item. Boot the game and grep the log for `modonomicon` to be sure: a bad entry is logged and skipped. Entry text reloads with `/reload`.
