# Changelog

Each pack version gets a section here, with **Added**, **Fixed** and **Removed** where they apply. The release
workflow copies the section for the version in `pack/pack.toml` to the top of the GitHub release, and the build
fails when that section is missing.

## 0.2.21

### Added
- `/hostileskies resetkills <players>` (op, works from the server console) sets a player's pillager airship captain kills back to 0. Hostile Skies had no way to do this.

### Fixed
- A pillager airship captain that dies on its own (falls off, suffocates, its ship breaks up) no longer announces "Captain was killed!" as if you did it. The ship now says "The Captain died in an accident!" and still goes down.
- A captain kill only counts toward bigger airship tiers when a real player hurt the captain in its last 30 seconds, directly or with arrows and other shots. If you wounded it and it then fell to its death, the kill and the Captain's Orders are still yours. Create deployers no longer earn kills.
- Create Galore Bridge 1.21.0 (the captain fix on top of 0.2.20's rain fix).

## 0.2.20

### Fixed
- **Rain no longer stops after a second or two.** Winds & Weather copied its regional weather onto the normal rain every 3 seconds and switched rain off whenever no player stood in one of its wet regions. So rain from the normal weather cycle, the seasons or `/weather rain` was stopped almost as soon as it began, and tried again 5 minutes later. Now Winds & Weather can still bring rain and thunder while you are under one of its storms, but it never ends rain it didn't start: normal rain runs its full length again. Its own storm rain also no longer has short dry gaps, and ends a few seconds after you leave the storm. `/weather clear` still keeps the sky clear.
- Create Galore Bridge 1.20.0 (the rain fix on top of 0.2.19's Ask the Book).

## 0.2.19

### Added
- **Ask the Book.** The Field Guide can now answer questions in its own words. Press **Ask the Book** at the top right of the guide (or type `/askbook`) and ask anything about the pack. It looks at what you're holding, your inventory, what's around you, your advancements and the pack's recipes, so "what should I do next?" gets advice for where you actually are. Answers stream in with a typewriter, a quill sound you can turn off, and Minecraft-themed thinking lines. When a picture helps, it draws pixel-art diagrams on the page in the book's colours with real item icons. Each player keeps their own pages on the server.
- Works only on a home server whose owner saved their own Anthropic API key. The server asks for it at start (press K to change it, or type OFF). The key stays on the server machine, outside the server folder, and is never sent to players, written to logs, backups or the world. Setup: [docs/server-setup.md](https://github.com/emresahin0906-code/Create-Galore/blob/main-6fi2ef/docs/server-setup.md#the-field-guides-voice-your-own-api-key).
- Create Galore Bridge 1.19.0 (adds Ask the Book on top of 0.2.18's Tide fixes).
