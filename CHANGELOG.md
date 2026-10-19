# Changelog

Each pack version gets a section here, with **Added**, **Fixed** and **Removed** where they apply. The release
workflow copies the section for the version in `pack/pack.toml` to the top of the GitHub release, and the build
fails when that section is missing.

## 0.2.21

### Added
- **Ask the Book looks like the book now.** Its screen sits in the guide's own frame on a sheet of its paper, with book-style buttons, and it opens from a new speech-bubble ribbon on the guide's right edge (next to the bookmarks and search ribbons) instead of a plain button.
- **Chat layout.** Your questions sit on the right in a note with your face; the book's answers sit on the left under the guide's icon.
- **Answers you can skim.** The book now writes short sections with headings, bullets, bold words and real item icons, instead of walls of text.
- **Recipes on the page.** When the book explains how something is made, it shows the real recipe from this server: the crafting grid, or the Create machine with its inputs, outputs, chances and heat.
- **Your chests count.** The book remembers the chests, barrels and vaults you open, and counts what's in the ones within 50 blocks of you as yours. It can also tell you which chest holds something. Chests further away are left out.
- Pages started before 0.2.21 keep working as they are, with a note suggesting a new page for the new features.
- Create Galore Bridge 1.21.0.

### Fixed
- The Field Guide no longer reopens on the last page you read after you went back to the map. It reopens on a page only if you closed the book while reading it. This was a Modonomicon bug: it forgot that you had left the page.

## 0.2.19

### Added
- **Ask the Book.** The Field Guide can now answer questions in its own words. Press **Ask the Book** at the top right of the guide (or type `/askbook`) and ask anything about the pack. It looks at what you're holding, your inventory, what's around you, your advancements and the pack's recipes, so "what should I do next?" gets advice for where you actually are. Answers stream in with a typewriter, a quill sound you can turn off, and Minecraft-themed thinking lines. When a picture helps, it draws pixel-art diagrams on the page in the book's colours with real item icons. Each player keeps their own pages on the server.
- Works only on a home server whose owner saved their own Anthropic API key. The server asks for it at start (press K to change it, or type OFF). The key stays on the server machine, outside the server folder, and is never sent to players, written to logs, backups or the world. Setup: [docs/server-setup.md](https://github.com/emresahin0906-code/Create-Galore/blob/main-6fi2ef/docs/server-setup.md#the-field-guides-voice-your-own-api-key).
- Create Galore Bridge 1.19.0 (adds Ask the Book on top of 0.2.18's Tide fixes).
