# Editing the Field Guide

The **Create Galore Field Guide** is a [Modonomicon](https://modrinth.com/mod/modonomicon) book. Each section is a map: entries are nodes, and a line from one entry to another means "this comes first". Every entry is open from the start; there are no quests, locks or rewards.

The book is data, not a mod. It lives in the always-on Paxi datapack, so the server sends it to every player and singleplayer loads it the same way:

```
pack/config/paxi/datapacks/create-galore-tweaks/data/create_galore/modonomicon/books/field_guide/
  book.json                       name, look, landing text
  categories/<id>.json            the 16 sections (tabs)
  entries/<category>/<entry>.json one node each
```

An entry's ID is `<category>/<entry>`. The book ID is `create_galore:field_guide`.

How players get it (same datapack, `data/create_galore/`):
- `advancement/give_field_guide.json` + `function/give_field_guide.mcfunction`: one copy on first join.
- `recipe/field_guide.json`: Book + Andesite, shapeless.
- Old copies: the Create Galore Bridge mod (`GuideCleanup`) takes outdated Field Guides (the old Patchouli book) out of every player's inventory and ender chest, on join and every two seconds, and puts the current book in their place if they have none. A future replaced book goes on its list.
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

- **Link nodes**: an entry with `"type": "modonomicon:entry_link"` and `"entry_to_open": "create_galore:<category>/<entry>"` (no pages) puts a node on one map that opens an entry from another section. `start_here/first_food` uses it so Food in Your First Days sits beside Your First Days.
- **Graph**: `x`/`y` are grid cells (30 px). Parents must be in the same category, and the parent sits to the left of its children. Keep one node per cell. A `line_reversed: true` line drops down the parent's column then turns right into the child; `false` runs along the parent's row then turns into the child's column. Use `true` for a parent's own children and `false` for a second parent, so lines don't run under other nodes.
- **Text** is Markdown: `**bold**`, `*italic*`, `- list item`, `\` at a line end for a line break. A blank line alone does **not** show as a gap: Modonomicon glues the two paragraphs together ("water.Flowing water"). For a paragraph gap end the line with `\` twice, in JSON `"First.\\\n\\\nSecond."`. Next to a list keep a plain blank line (a trailing `\` there shows up as a literal backslash); list items start on their own lines anyway. Links to other entries are `[text](entry://category/entry)`.
- Write `%` as `%%` and `_` as `\_` in text, or the line shows a format error or turns italic.
- The first text page carries the entry's name as its `title` (Modonomicon doesn't add it).
- A spotlight `item` is one item, or a list `[{"item": "a"}, {"item": "b"}]` that cycles.
- Only vanilla crafting-table recipes can be shown on a page. For Create machine recipes, point to JEI.
- Text that is too long for a page shrinks to fit rather than overflowing; split long pages anyway so it stays readable.
- **Page margins.** Modonomicon's default text box (124 px) runs into the spine stitching on left pages and the stacked page edges on right pages. `book.json` narrows it with `book_text_offset_x: 3` and `book_text_offset_width: -9` (112 px, starting 3 px in), which keeps every line on the clean paper. Titles ignore those settings, so the Create Galore Bridge mod caps them at 108 px instead of 120.
- **How much fits.** About 15 lines under a title, 17 without one, 12 on a spotlight page, at roughly 23 characters a line. `validate_guidebook.py` warns past that.

## Readability conventions

The guide keeps every fact, but long entries are shaped so a reader can tell what matters:

- **Key points.** An entry of roughly 1300+ characters starts with a page titled with the entry name whose text is a key icon, `**Key points**` and 2 to 4 bullets: the must-know facts, restated from the entry. The entry's own intro follows untitled.
- **In depth.** Niche material (exact numbers, config notes, edge cases, add-on interactions) sits at the end of the entry, and its first page starts with the magnifier icon and `**In depth.**`. Everything after it is optional.
- **Callouts.** A real danger or lost progress: warning icon + `**Watch out:**`. A useful trick: bulb icon + `**Tip:**`. One or two per entry at most.
- **Links.** Blue `[#](2E5AAC)[text](entry://category/entry)[#]()` for a thing with its own entry. Purple `[#](7A3C9A)[text](entry://glossary/<term>)[#]()` for jargon defined in **Words to Know** (the `glossary` section, one short entry per term plus an A to Z index). Purple, not green, because Modonomicon draws item links in green. `[Name](item://modid:item)` shows the item's tooltip on hover; leave the name empty and the item's own name is filled in. First mention per entry is enough.
- **Start Here > Welcome** explains these to players ("Reading the pages"). Change it if the conventions change.

### Icons

Small icons are glyphs in a font added by the always-on resource pack `pack/config/paxi/resourcepacks/create-galore-guide` (`tools/art/guide_icons.py` draws them and writes the font for both `minecraft:default` and `modonomicon:default`, the font book text uses). Book text tints glyphs black, so each icon is wrapped in white: `[#](FFFFFF)\ue100[#]()`. The validator flags unwrapped icons. An icon is about two characters wide.

| Code | Icon | Use |
|---|---|---|
| `\ue100` | key | Key points |
| `\ue101` | warning | Watch out |
| `\ue102` | bulb | Tip |
| `\ue103` | magnifier | In depth |
| `\ue104` | cog | rotation, Create machines |
| `\ue105` | gauge | stress, SU |
| `\ue106` | bolt | electricity, FE |
| `\ue107` | drop | fluids, water |
| `\ue108` | flame | heat |
| `\ue109` | snowflake | cold |
| `\ue10a` | cloud | pollution |
| `\ue10b` | heart | health, combat |
| `\ue10c` | drumstick | food |
| `\ue10d` | crafting grid | items, crafting |
| `\ue10e` | compass | travel, exploration |
| `\ue10f` | star | space, the capstone |

Topic icons go only at the start of a key-point bullet or a bold lead-in.

### Pictures and 3D builds

- **Image pages**: `{"type": "modonomicon:image", "images": ["create_galore:textures/gui/guide/<name>.png"], "border": true, "title": "", "text": "<caption>"}`. Images are 200x200 PNGs in the `create-galore-guide` resource pack, shown at half size, with a short caption below (about 3 lines). `tools/art/guide_diagrams.py` draws the current ones from item textures copied out of the jars (its docstring lists them).
- **Multiblock pages**: `{"type": "modonomicon:multiblock", "multiblock_id": "create_galore:<name>", "multiblock_name": "...", "text": "<caption>", "show_visualize_button": true}`, with the build in `data/create_galore/modonomicon/multiblocks/<name>.json` (`modonomicon:dense`: layers top to bottom, each row string steps east, each character steps south, `0` is the centre, `_` any block, space air; matchers `modonomicon:block`, `modonomicon:blockstateproperty`, `modonomicon:display`). The preview draws block models and block entities but **not Create's Flywheel parts**: shafts, cogwheels, water wheels and other rotating pieces are invisible in it. Only use builds whose point survives that (crushing wheels, casings, tanks, burners, depots). Keep captions to about 4 lines.

## Checking it

```
python3 tools/validate_guidebook.py            # structure, links, graph
python3 -I tools/extract_mod_data.py <instance>/mods /tmp/data
ITEMS=/tmp/data/items.tsv RECIPES=/tmp/data/recipes python3 tools/validate_guidebook.py   # plus item and recipe IDs
```

Vanilla items and recipes aren't in the mod jars: append them from the Minecraft client jar's `assets/minecraft/lang/en_us.json` and `data/minecraft/recipe/` the same way.

`items.tsv` comes from lang files, so it can miss real items whose names live under another key (`cold_sweat:hearth` is one) and can't catch an ID that has a name but no item. Boot the game and grep the log for `modonomicon` to be sure: a bad entry is logged and skipped. Entry text reloads with `/reload`.

## The book's voice reads compact notes

Ask the Book doesn't start conversations with the guide as players read it. It gets **AI notes**: every fact of the guide written once, densely, without the key points, cross-references and repeats a human reader needs. They live next to the book in `data/create_galore/field_guide_ai/<section>.md`, one file per section, one `## <entry id> | <name>` block per entry. The voice can still read any entry in full with its `read_guide` tool.

Each notes file starts with a stamp of the entry files it was written from. **When you change an entry, update its section's notes too**: put the changed facts in the entry's block, then

```
python3 tools/guide_digest.py --audit <section>   # every item id, recipe id, link and number of each entry is in its notes
python3 tools/guide_digest.py --stamp <section>   # stamp the notes against the current entries
```

Until then the server sends that section to the voice in full (nothing is lost, it just costs more) and logs a warning, and CI fails on `python3 tools/guide_digest.py --check`. `--audit` only checks ids, links and numbers, so read the changed entry against its block for the rest. The hub sections (Start Here, Connections, Glossary) point to the topic entries that hold a fact in full instead of repeating it.
