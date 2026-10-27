# Mod list

Create Galore 0.5.7, NeoForge 21.1.262 for Minecraft 1.21.1. Everything below comes from Modrinth and is pinned by the files in [`pack/`](pack/).

"Side" is how the pack installs it: **both** means it goes on the client and the server; **client** means client only. Server-only world generation mods are marked **both** on purpose, because singleplayer runs them on your own machine.

Built for this pack and shipped inside it: **Create Galore Bridge** 1.124.0 (both), `pack/mods/create-galore-bridge-1.124.0.jar`. It turns Berries and Cherries' Iron Knife into Farmer's Delight's wherever one loads and gives knives back (one point more worn) after crafting with them, keeps a block tag working when one mod lists a block that doesn't exist (Create Deep Seas 3.4 names Abyss blocks it never adds, which emptied the pickaxe, axe, shovel and logs tags, so no tool worked and leaves decayed), makes Jade name the smog or filtered exhaust you look at and how thick it is (crouch to look through it), builds the pack's exhaust system on Pollution of the Realms (smog pressing up out of a closed room is forced through the filter frames in its ceiling with no pump needed, every filter frame catches carbon, sulfur and dust with no filter material or upkeep (iron 2 units a second, gold 4, diamond 8; old filter contents are used up), a filter lets out Filtered Exhaust, a harmless pale haze that has to rise into the open sky before it thins away, a filter whose top is closed off lets nothing through, a Create belt with casing on it carries items through a wall but no smog, bulk smelting and smoking with a fan puff out 1 carbon for every item done (plus 1 sulfur per 5 blasted), a sealed fan casing that fills up blows open, and an Advanced Chimneys pump runs on an Encased Fan facing up or down under it), adds the Stellar Core, the item that gates space travel, stops Aeronautics Winds' weather effects from generating chunks on the server thread, lets its blizzards and hail freeze water only where the game would freeze it anyway (they iced over lakes in summer), keeps oceans from freezing outside the frozen ocean biomes and melts ice left floating on them (scattered ice made boating hard), lets its regional weather start and keep up rain (only real rain, storms and snow; its drizzle stays clouds) but never cut short the normal rain (it stopped every rain after 3 s), moves its weather map at a steady speed (it scrolled faster the older the world, so its rain came in short bursts), makes the Dimensional Drive read and jump its own ship (it used to pick whichever ship in the dimension loaded first), holds a player who logs back in on a ship built since the last world save in place until the ship loads, then puts them back on its deck (Sable dropped them from where the ship had been, so a player who logged out at y 288 fell to the ground), sits a player who logs out on a seat back down on it when they log in, on a ship, car or coaster cart (Sable's return to the ship dropped the seat) and on a Create contraption such as a turning platform, cart or train carriage (Create never saved those seats), as long as the seat is still free: a train that drove on while they were away is found wherever it is in their dimension and they are taken back to their seat, and a player left in the air while waiting falls slowly, and keeps Deep Seas boats afloat on a dedicated server (each dimension gets its own boat list), makes the Deep Seas Boat Engine push through the boat's centre of mass at the height of its submerged hull (from the stern block it dug bows in or reared boats up onto their ends), lets the air above a slab floor or beside a stair inside a Deep Seas hull lift the boat like the rest of its hold (it lifted nothing, so slab-floored boats sat bow-down), lets the sea flow back into the spots a ship's blocks left when it is assembled (a hull built in the water left a ship-shaped hole in the sea for good, and a boat over it sat too low, leaned and sank), keeps Create Simulated's Physics Assembler and Swivel Bearing from gluing water and lava into a ship (and removes the water already glued into older ships, once per ship), takes waterlogged blocks aboard dry and leaves their water in the sea (their water used to spill across the deck as a thin sheet), waterlogs blocks set down in water when a ship is taken apart, makes a Swivel Bearing on a ship turn only its own part (it took the whole ship when the ship was held by Super Glue) and makes the Physics Assembler carry every glue box inside the ship aboard (smaller boxes, such as a swivel's own, were left behind in the world), makes all glue boxes that overlap or touch a ship's glue act as one (the seam between two boxes used to cut the ship), shows a player holding glue near a Physics Assembler which blocks will go aboard (blue) and which stay behind (red), and stops a server crash when Aquamirae's freeze check reads a biome at the edge of a generating chunk (Alex's Caves' Primordial Caves lakes triggered it), and stops Cold Sweat's rain from soaking a player who came out of water past its usual light chill (it kept chilling them without limit), and stops Create Dupe Fix from deleting fruit picked from leaves (its ship check matched every server action), makes Cataclysm's Nature Heal attribute speed up healing from food, makes a mined TFMG Large Transformer give back the two Large Coils it was built from (with their turns) and its Block of Steel (it used to drop nothing), makes items used from a seat on a turned ship aim where you look on a dedicated server, stops Berries and Cherries plants from hurting you when you pick them or destroying items that fall into them (only raspberry bushes still prick, and only living things), makes a Hostile Skies airship captain count as killed only when a player hurt it in its last 30 seconds (one that dies by accident says so instead of "Captain was killed!") and adds `/hostileskies resetkills`, and makes Hybrid Aquatic's orcas and predatory fish rest after a kill, as its sharks already do, and leave no drops from the prey they kill (vanilla cod, salmon and tropical fish used to float up by the dozen around an orca pod), keeps hermit crabs off the menu of every Hybrid Aquatic hunter (otters emptied the beaches of them), stops Garnished's voltaic sea grass from shocking fish and other sea life (schools of herring swam into it and died by the dozen; players, drowned and land mobs still get shocked), turns Naturalist's snails and dragonflies into Critters and Companions' ones as they appear (so every snail and dragonfly can be tamed and every snail bottled for Snail Slime), lets a glass bottle take Snail Slime from Hybrid Aquatic's scalyfoot snail and Northstar's Moon snail and Sea Bunny Slime from Hybrid Aquatic's sea slug, lets a Hybrid Aquatic otter crack a Critters clam for a pearl (Critters' own otter is off), keeps frogs from eating tamed animals, shows an animal's sex, happiness and why it isn't mating yet on Animal Husbandry's Farm Magnifying Glass (it showed neither, and said a pen of all ewes was ready to breed), lets a Fertility Potion's first pick run out with its 30 s of love (an old pick used up the next potion for nothing), skips Steam 'n' Rails' and Brewin' and Chewin' structure upgrade passes for structure files that hold none of their blocks and upgrades old structure files once in the background into `cache/create_galore` (each big structure used to freeze the server for 5 to 20 s the first time it loaded), treats a fence and its Diagonal Fences twin (the block a fence item places, which can also join diagonally) as the same block, so Ask the Book's models join fences diagonally as placed fences do and a pinned or traced schematic takes either one, and gives the twin its fence's weight aboard a ship (it weighed as much as a full block), keeps Jade from crashing on a chest whose block entity is no longer a vanilla chest, and stops chunks from being generated inside the server tick (which froze the server) when Aeronautics Discovery builds a flyover airship or clears one away as it despawns, when a Soaring Trader looks for the structure to put on its map (the search now runs in spare tick time and the map joins its trades a moment later), when a ship with nobody aboard flies over new land, when Create Offroad's wheel mounts or Create Deep Seas' afloat check read land that is still generating under a ship, when Sable updates a changed block's collision next to land that is still generating, stops a chunk unload that can no longer happen from retrying forever (it could keep a server on "Stopping the server"), when Critters and Companions looks for a lush cave spawn spot, or when Create Simulated checks the neighbours of a kinetic block at the edge of the loaded area. On the client it keeps Create Deep Seas' hull culling from swapping a shader pack's own textures (since Deep Seas 3.4.2 the sea drew as flat grey and ice looked like dark glass with any shader pack), makes shader packs draw Pollution of the Realms' polluted water as water (it was a bright, flat block) with no faces where it meets clean water, keeps name tags readable under Iris shader packs (they are drawn after the shader, so Bliss and Photon no longer turn them into black boxes), keeps water and stained glass in shader packs' shadows while a Sable ship is in view (Aeronautics' levitite left Iris drawing Sodium's terrain as tessellation patches, which the driver refused), keeps Distant Horizons' far terrain from being drawn over the world after the window changes size with a shader pack on (Iris handed it the world's depth buffer), keeps Inventory Profiles Next's hotkeys from firing while you type in a text box (an R typed into JEI's search sorted the inventory), keeps long Field Guide page titles on the paper, shows only as many Field Guide section tabs as fit on screen (the arrows scroll to the rest), moves Create Flight Control's tool gun status line off the health bar, makes Aeronautics Winds' third-person glider camera (and its three camera options) work on 1.21.1, moves the nine 0.2.1 key rebinds into games installed before 0.2.1 (once, only keys still on their mod's default) and drops two join-chat notices that have no setting (Create Train Utilities' v4 rename and Transmission & Linkage's Toolgun recommendation). It also has experimental display options in `config/create_galore_bridge-display.properties`, also on its Upscaling & Display screen (Mods, Create Galore Bridge, Config, or the Upscaling & Display... button in Iris's shader pack menu): a 10-bit game window (`tenBitWindow`, off), Perfect Presentation (`perfectPresentation`, off, Windows only), a NeoForge port of [Perfect Presentation](https://github.com/sidit77/perfect_presentation) by sidit77 (MIT license, included in the jar as `LICENSE-perfect_presentation.txt`), shown through DirectX 11 or, experimental, DirectX 12 (`presenter`, dx11; dx12 through a native helper, `cg_present.dll`, that shares the frame from OpenGL with DirectX 12), NVIDIA Reflex (`reflex`, off; on or boost, through NVIDIA's NVAPI on Perfect Presentation's DirectX device, which the settings screen turns on with it), GPU pacing (`gpuPacing`, off; on or lowest, any graphics card: the game waits just before drawing each frame so it doesn't queue on the card), a frame counter (`fpsCounter`, off; basic or detailed), and DLAA (`dlaa`, Auto): at launch it makes the shader pack Photon v1.3a + DLAA from Photon + Clrwl, whose TAA pass hands its frame, motion vectors (with those of moving mobs, items, contraptions and ships), depth and exposure to NVIDIA DLAA (DLSS at full resolution, through a native helper, `cg_ngx.dll`, built on NVIDIA's DLSS SDK, notice in the jar as `NOTICE-cg_ngx.txt`) or AMD FSR 3.1 at full resolution (a port of AMD's FidelityFX FSR 3.1 shaders to OpenGL, MIT license, in the jar as `LICENSE-FidelityFX-SDK.txt`). Any other shader pack made for it (one with `shaders/create_galore_dlss.properties`) works the same way, its own upscaling options setting the render resolution. Below full resolution (Quality, Balanced, Performance, Ultra Performance, or a custom 25 to 100%) Photon renders the scene smaller and DLSS or FSR 3.1 upscale it to the window, and Colorwheel's check for Create machines hidden behind nearer blocks reads that smaller picture where Photon draws it (it hid shafts and cogwheels in plain view). DLSS uses the player's own `nvngx_dlss.dll`, which the pack doesn't ship; the settings screen's Find on this PC copies it, when pressed, from where the NVIDIA app keeps its DLSS downloads. It also runs the pack's one Accessories screen and its wear limits (the groups and the eye toggle, with worn items from older versions moved to the inventory), takes an Artifacts accessory's effects off a player who no longer wears it (a Charm of Shrinking taken off once left a player half size), lets any player pin a placed Create Schematic so everyone on the server sees it (Pin for everyone, `/schematic pin`), looks up every creature for Ask the Book (by name or looks, where it spawns, what it drops, what is around you), keeps an Ask the Book page answering when the API no longer accepts the page's earlier reasoning, works out Ask the Book's vehicle checks, where the basins and millstones in its builds send their results, which machines turn too slowly to work, builds from the blocks a player carries and has around them, counts what is in the storage a player opened aboard a ship wherever the ship has sailed, and its moving parts and planes (wings and sails lift, a short flight shows if a plane stays level; how fast an airship's propellers push it against its envelope's drag and whether it tips at speed), stops looking at an assembled ship from freezing the client, keeps Aeronautics Discovery's flyover planes off floating islands by planning their route, makes water flowing at the edge of the loaded world, and Advanced Chimneys' smoke search there, wait for the chunks around it to load (they froze the server while sailing into new terrain), keeps the game's block and model texture sheets from being removed or replaced while it runs (with shaders a lost sheet crashed Iris), keeps a player dry in a ship's hold doorways and on login, hides the sea under a ship only where it reaches the hull (also seen from inside a cabin or hold), lands rain splashes on a ship's own deck and roof (on a deck below the waterline they splashed on the hidden sea above it), pops bubbles that reach a hull or rise into a dry hold, keeps fire and other animated blocks on ships animating, keeps a ship's daylight level steady as it bobs at the waterline (the item in your hand flickered while standing on a sailing boat), draws polluted and clean water as one flush surface, takes down a Farm Magnifying Glass panel when its animal dies, has Animal Husbandry's tamed animals eat from the troughs they can reach for the days their farm stood still (they came back from every trip starving, unhappy and sick beside full troughs) and walk to the nearest trough they can reach within 20 blocks, and says on the glass when none has an animal's food, and loads a fixed copy of Create Deep Seas' Pixel light style shader (its variable `packed` is a reserved GLSL word, so NVIDIA and Mesa refused it), and keeps Cold Sweat from emptying its list of temperature effects at a single player join while the world is using it (the world crashed with "Ticking player"), lets mods built at the same time during loading number their entities' synced data one at a time (two at once could corrupt the shared list, and the game stopped while loading mods, seen with Hybrid Aquatic), and leaves options.txt alone when a start fails to load its mods (it lost every mod key binding and the resource pack list). Source and build script in [`tools/bridge-mod/`](tools/bridge-mod/).

Also built for this pack: **Create Galore Fishing** 0.3.17 (both), `pack/mods/create-galore-fishing-0.3.17.jar`, the pack's fishing: rods assembled from parts at a Tackle Bench, a real fight with drag, line and rod limits, catch and release with real fish weights, bait from Create (fan-washed worms and leeches, boilies, chum, live bait), a Cleaning Table and a Create Fish Cleaner for fish lines, Create-driven Rod Holders and Reel Drives that also troll from ships, and aquarium tanks with baitfish breeding. It carries Tide 2's fish, Fishing Journal, crates, gadgets and special rods under their old `tide:` ids, so Tide-era worlds keep them; fish, journal and extras are ported from [Tide 2](https://modrinth.com/mod/tide) by Lightning-64 (MPL-2.0) and other mods' fish data from [Tide Extra Compatibility](https://modrinth.com/mod/tide-extra-compatibility) by Silent Autopsy (MIT). Source, build script and handover in [`tools/fishing-mod/`](tools/fishing-mod/).

Also built for this pack: **Create Galore Lag Watch** 1.1.0 (dedicated server only; it ships in `pack/mods/create-galore-lagwatch-1.1.0.jar` for both sides but does nothing on a client), says in chat when TPS stays under 15 and records a spark profile automatically, and writes down what the server was stuck on during any single freeze of 2 s or more. How to use it: [docs/server-setup.md](docs/server-setup.md#lag-alerts-and-profiles). Source and build script in [`tools/lagwatch-mod/`](tools/lagwatch-mod/).

Not on Modrinth, add by hand: **Twilight Forest** `twilightforest-1.21.1-4.8.3345-universal.jar` (CurseForge). See the README.

Resource pack, turned on by default: [Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) 1.10.4 (client, needs EMF + ETF).

| Mod | Version | Side |
|---|---|---|
| [Advanced Chimneys](https://modrinth.com/mod/advanced-chimneys) | 11.1.13.0 | both |
| [Aeronautics Hot Air Fix](https://modrinth.com/mod/aeronautics-hot-air-fix) | 1.1.1 | client |
| [Aeronautics: Winds & Weather](https://modrinth.com/mod/aeronautics-winds-and-weather) | 1.3.1 | both |
| [Alex's Caves Continued](https://modrinth.com/mod/alexs-caves-continued) | 1.1.1+neoforge-1.21.1 | both |
| [Alloyed](https://modrinth.com/mod/create-alloyed) | 3.0.12+1.21.1-neoforge | both |
| [AmbientSounds](https://modrinth.com/mod/ambientsounds) | 6.3.10 | client |
| [Animal Husbandry](https://modrinth.com/mod/animalhusbandry) | 0.4.2 | both |
| [AppleSkin](https://modrinth.com/mod/appleskin) | 3.0.9+mc1.21 | both |
| [Aquaculture 2](https://modrinth.com/mod/aquaculture) | 2.7.21 | both |
| [Aquaculture Delight](https://modrinth.com/mod/aquaculture-delight) | 1.2.0 | both |
| [Aquamirae](https://modrinth.com/mod/aquamirae) | 7.2.12 | both |
| [Architectury API](https://modrinth.com/mod/architectury-api) | 13.0.11+neoforge | both |
| [Artifacts](https://modrinth.com/mod/artifacts) | 13.2.5 | both |
| [BaguetteLib](https://modrinth.com/mod/baguettelib) | 2.0.7 | both |
| [Balm](https://modrinth.com/mod/balm) | 21.0.66+neoforge-1.21.1 | both |
| [BCLib: New Dawn](https://modrinth.com/mod/bclib-neoforge) | 21.0.27 | both |
| [Berries and Cherries Delight](https://modrinth.com/mod/berries-and-cherries) | 1.1 | both |
| [Berry Good](https://modrinth.com/mod/berry-good) | 8.0.2 | both |
| [Better Farming Right Click  + Right Click Harvest](https://modrinth.com/mod/better-farming-right-click) | 1.1.1 | client |
| [BetterEnd: New Dawn](https://modrinth.com/mod/betterend-neoforge) | 21.0.37 | both |
| [Blueprint](https://modrinth.com/mod/blueprint) | 8.2.0 | both |
| [Brewin' And Chewin'](https://modrinth.com/mod/brewin-and-chewin) | 4.5.0+1.21.1-neoforge | both |
| [Cataclysm x Create Integration](https://modrinth.com/mod/cataclysm_create) | 1.21.1-Neoforge+mod | both |
| [Chunksmith](https://modrinth.com/mod/chunksmith) | 4.6.0+1.21.1-neoforge | both |
| [Cloth Config API](https://modrinth.com/mod/cloth-config) | 15.0.140+neoforge | both |
| [Coasters Simulated: Track Styles](https://modrinth.com/mod/coaster-track-styles) | 1.2.0 | both |
| [CodxLib](https://modrinth.com/mod/codxlib) | 1.6.1+neoforge-1.21.1 | both |
| [Cold Sweat](https://modrinth.com/mod/cold-sweat) | 2.4.4 | both |
| [Cold Sweat: Altitude](https://modrinth.com/mod/cold-sweat-altitude) | 0.8.1 | both |
| [Collective](https://modrinth.com/mod/collective) | 1.21.1-8.57-fabric+forge+neo | both |
| [Colorwheel](https://modrinth.com/mod/colorwheel) | 1.3.0+mc1.21.1 | client |
| [Colorwheel Patcher](https://modrinth.com/mod/colorwheel-patcher) | 1.0.5+mc1.21.1 | client |
| [Contraption Lights (Bi-directional Coloured Dynamic Lights)](https://modrinth.com/mod/contraption-lights) | 1.5.4 | client |
| [Controlling](https://modrinth.com/mod/controlling) | 19.0.5 | client |
| [Cozy Cafe](https://modrinth.com/mod/cozycafe) | 2.3-1.21.1 | both |
| [Crafting Tweaks](https://modrinth.com/mod/crafting-tweaks) | 21.1.11+neoforge-1.21.1 | both |
| [Creat Aeronautics Structures](https://modrinth.com/mod/creat-aeronautics-structures) | 1.1.0-Beta-3.1 | both |
| [Create](https://modrinth.com/mod/create) | 6.0.10+mc1.21.1 | both |
| [Create - Xaero's map](https://modrinth.com/mod/create-xaeros-map) | 1.4.1 | client |
| [Create Aeronautics](https://modrinth.com/mod/create-aeronautics) | 1.3.2+mc1.21.1 | both |
| [Create Aeronautics Discovery](https://modrinth.com/mod/create-aeronautics-discovery) | 2.2.0 | both |
| [Create Aeronautics: Compatibility](https://modrinth.com/mod/create-aeronautics-compatability) | 1.1.3 | both |
| [Create Aeronautics: Encased Fluid Pipes](https://modrinth.com/mod/create-aeronautics-encased-fluid-pipes) | 1.0.7 | both |
| [Create Aeronautics: Transmission & Linkage](https://modrinth.com/mod/create-aeronautics-transmission-linkage) | 0.2.8 | both |
| [Create Big Cannons](https://modrinth.com/mod/create-big-cannons) | 5.11.7 | both |
| [Create Cafe](https://modrinth.com/mod/create-cafe) | 1.4.3 | both |
| [Create Collision Crashfix](https://modrinth.com/mod/create-collision-crashfix) | 1.0.0 | both |
| [Create Confectionery](https://modrinth.com/mod/create-confectionery) | 1.1.3.b | both |
| [Create Crafts & Additions](https://modrinth.com/mod/createaddition) | neoforge-1.21.1-1.7.2 | both |
| [Create Deco](https://modrinth.com/mod/create-deco) | 2.1.3 | both |
| [Create Deep Seas](https://modrinth.com/mod/create-deep-seas) | 3.4.4 | both |
| [Create Dupe Fix](https://modrinth.com/mod/create-dup-fix) | 1.21.1-1.2 | both |
| [Create Encased](https://modrinth.com/mod/create-encased) | 1.21.1-1.9.0-ht3 | both |
| [Create Factory Logistics](https://modrinth.com/mod/create_factory_logistics) | 1.6.1 | both |
| [Create JEI Compat](https://modrinth.com/mod/create-jei-compat) | 1.0.3 | client |
| [Create Jetpack](https://modrinth.com/mod/create-jetpack) | 5.2.1 | both |
| [Create More: Parallel Pipes](https://modrinth.com/mod/create-more-parallel-pipes) | 2.0.2 | both |
| [Create Nuclear](https://modrinth.com/mod/createnuclear) | 2.0.0 | both |
| [Create Ore Excavation](https://modrinth.com/mod/create-ore-excavation) | 1.21.1-1.6.8 | both |
| [Create Ore Excavation Deposits](https://modrinth.com/mod/create-ore-excavation-deposits) | 0.2.0 | both |
| [Create Propulsion: Simulated](https://modrinth.com/mod/create-propulsion-simulated) | 1.1.5 | both |
| [Create Railways Navigator](https://modrinth.com/mod/create-railways-navigator) | 1.21.1-beta-0.10.0-C6 | both |
| [Create Ratatouille](https://modrinth.com/mod/create-ratatouille) | 1.4.0 | both |
| [Create Slice & Dice](https://modrinth.com/mod/slice-and-dice) | 4.3.4 | both |
| [Create Smart Bounds](https://modrinth.com/mod/create-smart-bounds) | 1.0.0 | client |
| [Create Train Utilities (Create Train Doors)](https://modrinth.com/mod/create-trainutilities) | 3.1.0 | both |
| [Create: Aeronautics Weightworks](https://modrinth.com/mod/weightworks) | 1.0.1 | both |
| [Create: Aeroworks](https://modrinth.com/mod/create-aeroworks) | 1.5.0+mc1.21.1 | both |
| [Create: Aquatic Ambitions](https://modrinth.com/mod/create-aquatic-ambitions) | 1.21.1-6.0.10-2.0.4 | both |
| [Create: Central Kitchen](https://modrinth.com/mod/create-central-kitchen) | 2.6.2b | both |
| [Create: Coaster Seats](https://modrinth.com/mod/create-coaster-seats) | 1.2+build.12 | both |
| [Create: Coasters Simulated](https://modrinth.com/mod/create-coasters-simulated) | 0.1.5 | both |
| [Create: Cold Sweat](https://modrinth.com/mod/create-cold-sweat) | 1.1.2 | both |
| [Create: Configurable Machine Outputs](https://modrinth.com/mod/create-configurable-crushing-wheel) | 1.3.0 | both |
| [Create: Connected](https://modrinth.com/mod/create-connected) | 1.3.3-mc1.21.1 | both |
| [Create: Copycats+](https://modrinth.com/mod/copycats) | 3.0.9+mc.1.21.1-neoforge | both |
| [Create: Deep Seas - Lava Fix](https://modrinth.com/mod/create-deep-seas-lava-fix) | 1.0.1 | both |
| [Create: Design n' Decor](https://modrinth.com/mod/create-design-n-decor) | 2.2b | both |
| [Create: Dragons Plus](https://modrinth.com/mod/create-dragons-plus) | 1.11.9 | both |
| [Create: Enchantment Industry](https://modrinth.com/mod/create-enchantment-industry) | 2.5.4b | both |
| [Create: Flight Control](https://modrinth.com/mod/create-flight-control) | 0.7.7 | both |
| [Create: Framed](https://modrinth.com/mod/create-framed) | 1.8.2+1.21.1 | both |
| [Create: Garnished](https://modrinth.com/mod/create-garnished) | 2.1.9.2+1.21.1-neoforged | both |
| [Create: Hangars (Aeronautics)](https://modrinth.com/mod/create-hangars) | V1.0.0+mod | both |
| [Create: Hostile Skies](https://modrinth.com/mod/create-hostile-skies) | 0.2.6 | both |
| [Create: Hypertubes](https://modrinth.com/mod/hypertube) | 0.6.0 | both |
| [Create: Integrated Farming](https://modrinth.com/mod/create-integrated-farming) | 1.4.3 | both |
| [Create: LazyTick](https://modrinth.com/mod/createlazytick) | 1.21.1-2.7.31-6.0.10 | both |
| [Create: Let The Adventure Begin](https://modrinth.com/mod/create-let-the-adventure-begin) | 4.1.0 | both |
| [Create: Liquid Fuel](https://modrinth.com/mod/create-liquid-fuel) | 3.0.0-1.21.1 | both |
| [Create: Mobile Packages](https://modrinth.com/mod/create-mobile-packages) | 1.21.1-0.7.8 | both |
| [Create: Northstar - Redux](https://modrinth.com/mod/northstar-redux) | 0.6.6+1.21.1 | both |
| [Create: Northstar-Aeronautics Compatibility](https://modrinth.com/mod/create-northstar-aeronautics-compatibility) | 3.0.0 | both |
| [Create: Nowheel](https://modrinth.com/mod/create-nowheel) | 2.1.1+1.21.1neoforge | client |
| [Create: Power Loader](https://modrinth.com/mod/create-power-loader) | 2.0.5-mc1.21.1 | both |
| [Create: Sky Village](https://modrinth.com/mod/create-sky-village) | 0.0.38 | both |
| [Create: Smog](https://modrinth.com/mod/create-smog) | 0.4.1 | both |
| [Create: Structures Arise](https://modrinth.com/mod/create-structures-arise) | 176.49.49 | both |
| [Create: TFMG Community Edition](https://modrinth.com/mod/tfmg-community-edition) | 1.3.2a | both |
| [Create: Wizardry](https://modrinth.com/mod/create-wizardry) | 1.21.1-0.5.1-pre1 | both |
| [CreativeCore](https://modrinth.com/mod/creativecore) | 2.13.51 | client |
| [Cristel Lib](https://modrinth.com/mod/cristel-lib) | neoforge-1.21.1-3.1.7 | both |
| [Critters and Companions](https://modrinth.com/mod/critters-and-companions) | 2.7.0 | both |
| [Curios API](https://modrinth.com/mod/curios) | 9.5.1+1.21.1 | both |
| [Delightful Creators](https://modrinth.com/mod/delightful-creators) | 1.2.1 | both |
| [Diagonal Fences](https://modrinth.com/mod/diagonal-fences) | v21.1.1-1.21.1-NeoForge | both |
| [Dimensional Sable](https://modrinth.com/mod/dimensional-sable) | 1.0.5 | both |
| [Distant Horizons](https://modrinth.com/mod/distanthorizons) | 3.3.3-1.21.1 | client |
| [DragonLib](https://modrinth.com/mod/dragonlib) | 3.0.29+1.21.1-neoforge | both |
| [Drip Sounds](https://modrinth.com/mod/dripsounds) | 0.6.3+1.21.8-neoforge | client |
| [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) | v4.4.4+mod | both |
| [[EMF] Entity Model Features](https://modrinth.com/mod/entity-model-features) | 3.3.12-neoforge-1.21 | client |
| [Entity Culling](https://modrinth.com/mod/entityculling) | 1.11.3 | client |
| [[ETF] Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) | 7.2.6-neoforge-1.21 | client |
| [Euphoria Patches](https://modrinth.com/mod/euphoria-patches) | 1.10.5-r5.9.3-neoforge | client |
| [Falling Leaves (NeoForge/Forge)](https://modrinth.com/mod/fallingleavesforge) | 1.21.1-2.6.0 | client |
| [Farmer's Delight](https://modrinth.com/mod/farmers-delight) | 1.21.1-1.3.4 | both |
| [Farmer's Delight: Extended](https://modrinth.com/mod/farmers-delight-extended) | 1.21.1-0.2.2 | both |
| [FerriteCore](https://modrinth.com/mod/ferrite-core) | 7.0.3-neoforge | both |
| [ForgeEndertech](https://modrinth.com/mod/forgeendertech) | 12.1.4.0 | both |
| [Fragmentum](https://modrinth.com/mod/fragmentum) | 5.4.0 | both |
| [Fruits Delight](https://modrinth.com/mod/fruits-delight) | 1.2.14 | both |
| [Fungi Delight (A Farmer's Delight Add-on)](https://modrinth.com/mod/fungi-delight) | 1.3 | both |
| [Fzzy Config](https://modrinth.com/mod/fzzy-config) | 0.7.7+1.21+neoforge | client |
| [Geckolib](https://modrinth.com/mod/geckolib) | 4.9.3 | both |
| [GlitchCore](https://modrinth.com/mod/glitchcore) | 2.1.0.2 | both |
| [GraveStone Mod](https://modrinth.com/mod/gravestone-mod) | neoforge-1.21.1-1.0.40 | both |
| [Gravestone x Curios API Compat](https://modrinth.com/mod/gravestone-x-curios-api-compat) | 4.2.0 | both |
| [Guide Shelf](https://modrinth.com/mod/guide-shelf) | 1.3.6 | client |
| [Handcrafted](https://modrinth.com/mod/handcrafted) | 4.0.3 | both |
| [Hopo Better Underwater Ruins](https://modrinth.com/mod/hopo-better-underwater-ruins) | 1.2.1b | both |
| [Hybrid API [HAPI]](https://modrinth.com/mod/hybrid-api) | mc1.21.1-1.1.3-neoforge | both |
| [Hybrid Aquatic](https://modrinth.com/mod/hybrid-aquatic) | mc1.21.1-1.7.4-neoforge | both |
| [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) | 1.6.15+1.21.1-neoforge | client |
| [Incendium Legacy](https://modrinth.com/mod/incendium) | 5.4.4 | both |
| [Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) | neoforge-1.21.1-2.2.5 | client |
| [Iris Shaders](https://modrinth.com/mod/iris) | 1.8.14-beta.1+1.21.1-neoforge | client |
| [Iris Veil Compat](https://modrinth.com/mod/iris-veil-compat) | 1.21.1+0.4.0-alpha | client |
| [Iron's Lib](https://modrinth.com/mod/irons-lib) | 1.21.1-2.2.0 | both |
| [Iron's Spells 'n Spellbooks](https://modrinth.com/mod/irons-spells-n-spellbooks) | 1.21.1-3.16.3 | both |
| [Jade Addons (Neo/Forge)](https://modrinth.com/mod/jade-addons-forge) | 6.1.2+neoforge | both |
| [Jade Sable Compat](https://modrinth.com/mod/jade-sable-compat) | 1.3.0 | client |
| [Jade 🔍](https://modrinth.com/mod/jade) | 15.10.6+neoforge | both |
| [Just Enough Items (JEI)](https://modrinth.com/mod/jei) | 19.64.0.455 | both |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | 5.12.0 | both |
| [L_Ender's Cataclysm](https://modrinth.com/mod/l_enders-cataclysm) | 3.33 | both |
| [LambDynamicLights - Dynamic Lights](https://modrinth.com/mod/lambdynamiclights) | 4.8.11+1.21.1 | client |
| [libIPN](https://modrinth.com/mod/libipn) | neoforge-1.21.1-6.6.3 | client |
| [LibTooltips](https://modrinth.com/mod/libtooltips) | 2.3.2-NEOFORGE-1.21.1 | client |
| [Lionfish-API](https://modrinth.com/mod/lionfish-api) | 3.1 | both |
| [Lithium](https://modrinth.com/mod/lithium) | mc1.21.1-0.15.4-neoforge | both |
| [Lithostitched](https://modrinth.com/mod/lithostitched) | 1.8.1-neoforge-21.1 | both |
| [Lootr](https://modrinth.com/mod/lootr) | 1.21.1-1.11.38.127 | both |
| [Macaw's Doors](https://modrinth.com/mod/macaws-doors) | 1.1.5 | both |
| [Macaw's Roofs](https://modrinth.com/mod/macaws-roofs) | 2.3.2 | both |
| [Macaw's Windows](https://modrinth.com/mod/macaws-windows) | 2.4.2 | both |
| [Make Bubbles Pop](https://modrinth.com/mod/make_bubbles_pop) | 0.4.0-beta.2 | client |
| [MezzConfig](https://modrinth.com/mod/mezzconfig) | 0.6.8 | both |
| [ModernFix](https://modrinth.com/mod/modernfix) | 5.27.26+mc1.21.1 | both |
| [Modonomicon](https://modrinth.com/mod/modonomicon) | 1.21.1-1.120.7 | both |
| [Moog's Structure Lib (moogs_structures)](https://modrinth.com/mod/moogs-structure-lib) | 3.4.2-neoforge-1.21.1 | both |
| [Moonlight Lib](https://modrinth.com/mod/moonlight) | 1.21.1-3.7.2 | both |
| [MOS - Moog's Ocean Structures](https://modrinth.com/mod/mos-moogs-ocean-structures) | 1.1.1 | both |
| [Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks) | 1.21-2.26.1-neoforge | client |
| [Mowzie's Mobs](https://modrinth.com/mod/mowzies-mobs) | 1.8.2 | both |
| [MRU](https://modrinth.com/mod/mru) | 1.0.41+1.21.1-neoforge | client |
| [MSS - Moog's Soaring Structures](https://modrinth.com/mod/mss-moogs-soaring-structures) | 2.2.0 | both |
| [Mushroom Quest](https://modrinth.com/mod/mushroom-quest) | 4.3.2 | both |
| [My Nether's Delight](https://modrinth.com/mod/my-nethers-delight) | 1.10.4.1 | both |
| [Naturalist](https://modrinth.com/mod/naturalist) | 2.0.5+1.21.1-neoforge | both |
| [NoisiumForked](https://modrinth.com/mod/noisiumforked) | 2.7.0+mc1.21-1.21.1 | both |
| [Northstar Aeronautics Compat](https://modrinth.com/mod/northstar-aeronautics-compat) | 0.1.9 | both |
| [Not Enough Animations](https://modrinth.com/mod/not-enough-animations) | 1.12.6 | client |
| [Ocean's Delight](https://modrinth.com/mod/oceans-delight) | 1.0.4 | both |
| [oωo (owo-lib)](https://modrinth.com/mod/owo-lib) | 0.12.15.6-beta.1+1.21 | both |
| [Patchouli](https://modrinth.com/mod/patchouli) | 1.21.1-93-neoforge | both |
| [Paxi](https://modrinth.com/mod/paxi) | 1.21.1-NeoForge-5.1.3 | both |
| [Placebo](https://modrinth.com/mod/placebo) | 1.21.1-9.9.4 | both |
| [playerAnimator](https://modrinth.com/mod/playeranimator) | 2.0.4+1.21.1-forge | both |
| [Pollution of the Realms](https://modrinth.com/mod/pollution-of-the-realms) | 9.1.12.0 | both |
| [Presence Footsteps (NeoForge)](https://modrinth.com/mod/pf-neoforge) | 1.21.1-1.12.0-beta.1 | client |
| [Presence Footsteps x Sable (Aeronautics Compat)](https://modrinth.com/mod/presence-footsteps-x-sable) | 1.0 | client |
| [Productive Bees](https://modrinth.com/mod/productivebees) | 1.21.1-13.13.0 | both |
| [Puzzles Lib](https://modrinth.com/mod/puzzles-lib) | 21.1.65 | both |
| [Resourceful Lib](https://modrinth.com/mod/resourceful-lib) | 3.0.12 | both |
| [Revamped Shipwrecks](https://modrinth.com/mod/revamped-shipwrecks) | 1.1.0 | both |
| [Ritchie's Projectile Library](https://modrinth.com/mod/rpl) | 2.1.2 | both |
| [Sable](https://modrinth.com/mod/sable) | 2.0.6+mc1.21.1 | both |
| [Searchables](https://modrinth.com/mod/searchables) | 1.0.2 | client |
| [Seasonal Integration](https://modrinth.com/mod/seasonal-integration) | 1.6.3 | both |
| [Separate Sable Render Distance (Create Aeronautics Addon)](https://modrinth.com/mod/ssrd) | 1.8.7 | both |
| [Serene Seasons](https://modrinth.com/mod/serene-seasons) | 10.1.0.9 | both |
| [Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip) | 5.1.9+1.21.1-neoforge | both |
| [Simple Backups](https://modrinth.com/mod/simple-backups) | 1.21-4.0.30 | both |
| [Sky Whales](https://modrinth.com/mod/sky-whales) | 0.1.8 | both |
| [Sniffers Sniffs Delights! (SSD) (A Farmer's Delight Add-on)](https://modrinth.com/mod/sniffers-sniffs-delights) | neoforge-1.1+mc1.21 | both |
| [Sodium](https://modrinth.com/mod/sodium) | mc1.21.1-0.8.13-neoforge | client |
| [Sodium Extra](https://modrinth.com/mod/sodium-extra) | mc1.21.1-0.9.4+neoforge | client |
| [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) | 1.21.1-3.26.12.2208 | both |
| [Sophisticated Backpacks Create Integration](https://modrinth.com/mod/sophisticated-backpacks-create-integration) | 1.21.1-0.2.3.194 | both |
| [Sophisticated Core](https://modrinth.com/mod/sophisticated-core) | 1.21.1-1.5.13.2404 | both |
| [Sophisticated Storage](https://modrinth.com/mod/sophisticated-storage) | 1.21.1-1.6.7.2184 | both |
| [Sophisticated Storage Create Integration](https://modrinth.com/mod/sophisticated-storage-create-integration) | 1.21.1-0.1.23.246 | both |
| [Sound Physics: Aeronautics](https://modrinth.com/mod/sound-physics-aeronautics) | 2.0.1 | client |
| [Sounds](https://modrinth.com/mod/sound) | 2.4.22+lts+1.21.1-neoforge | client |
| [spark](https://modrinth.com/mod/spark) | 1.10.124-neoforge-1.21.1 | both |
| [Starter Kit](https://modrinth.com/mod/starter-kit) | 1.21.1-8.4-fabric+forge+neo | both |
| [Steam 'n' Rails Neoforge](https://modrinth.com/mod/create-steam-n-rails-1.21.1) | 0.3.0-beta.2+neoforge-mc1.21.1 | both |
| [Subtle Effects](https://modrinth.com/mod/subtle-effects) | 1.15.1 | client |
| [Supplementaries](https://modrinth.com/mod/supplementaries) | 1.21.1-4.0.2 | both |
| [Tectonic](https://modrinth.com/mod/tectonic) | 3.0.28-neoforge-21.1 | both |
| [Terralith](https://modrinth.com/mod/terralith) | 2.6.2 | both |
| [The Aether](https://modrinth.com/mod/aether) | 1.21.1-1.5.10-neoforge | both |
| [Thirst Was Reclaimed](https://modrinth.com/mod/thirst-was-reclaimed) | 1.21.1-3.0.6 | both |
| [Towns and Towers](https://modrinth.com/mod/towns-and-towers) | 1.13.11 | both |
| [When Dungeons Arise](https://modrinth.com/mod/when-dungeons-arise) | 2.1.68 | both |
| [WorldWeaver: New Dawn](https://modrinth.com/mod/worldweaver-neoforge) | 21.0.27 | both |
| [WunderLib: New Dawn](https://modrinth.com/mod/wunderlib-neoforge) | 21.0.11 | both |
| [Xaero Train Map](https://modrinth.com/mod/xaero-train-map) | 1.0.0 | client |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | neoforge-1.21.1-26.6.0 | both |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | neoforge-1.21.1-1.47.0 | both |
| [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) | 3.8.2+1.21.1-neoforge | both |
| [YUNG's API](https://modrinth.com/mod/yungs-api) | 1.21.1-NeoForge-5.1.9 | both |
| [YUNG's Better Caves](https://modrinth.com/mod/yungs-better-caves) | 1.21.1-NeoForge-3.1.6 | both |
| [YUNG's Better Desert Temples](https://modrinth.com/mod/yungs-better-desert-temples) | 1.21.1-NeoForge-4.1.5 | both |
| [YUNG's Better Dungeons](https://modrinth.com/mod/yungs-better-dungeons) | 1.21.1-NeoForge-5.1.4 | both |
| [YUNG's Better End Island](https://modrinth.com/mod/yungs-better-end-island) | 1.21.1-NeoForge-3.1.2 | both |
| [YUNG's Better Jungle Temples](https://modrinth.com/mod/yungs-better-jungle-temples) | 1.21.1-NeoForge-3.1.2 | both |
| [YUNG's Better Mineshafts](https://modrinth.com/mod/yungs-better-mineshafts) | 1.21.1-NeoForge-5.1.1 | both |
| [YUNG's Better Ocean Monuments](https://modrinth.com/mod/yungs-better-ocean-monuments) | 1.21.1-NeoForge-4.1.2 | both |
| [YUNG's Better Strongholds](https://modrinth.com/mod/yungs-better-strongholds) | 1.21.1-NeoForge-5.1.3 | both |
| [YUNG's Better Witch Huts](https://modrinth.com/mod/yungs-better-witch-huts) | 1.21.1-NeoForge-4.1.1 | both |
