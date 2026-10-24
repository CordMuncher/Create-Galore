package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * What the book can see. Every tool reads only the player who asked, and only what that player could already see
 * for themselves: what's under their crosshair or on their screen, what they carry, the weather they stand in, the
 * builds players made around them (Builds), the server's recipe list and what a block drops when mined (Reach).
 * Nothing reveals map locations, hidden natural blocks or chest and structure loot.
 */
final class WorldTools {
    static final double REACH = 8.0;
    private static final int MAX_RECIPES = 12;

    private WorldTools() {
    }

    /** The tool definitions sent with every request; fixed text and order, so they stay in the cache. */
    static JsonArray definitions() {
        JsonArray tools = new JsonArray();
        tools.add(tool("look_at", "The block or creature under the asking player's crosshair (up to 8 blocks away): its name, "
                + "the mod that adds it, its block state, how it is mined (tool tier, whether the player's held item can harvest it), "
                + "and for Create machines what Engineer's Goggles would show (speed, stress, contents).", new JsonObject()));
        tools.add(tool("get_view", "What was on the asking player's screen when they asked: every kind of block they could see "
                + "out to " + Vision.RANGE + " blocks (through glass and water) with counts, nearest distance and where on screen, "
                + "the exact x y z of placed blocks and machines in view, fluid surfaces, creatures and dropped items, and what "
                + "the crosshair was on. Use it when the question is about something they are looking at, such as a build that "
                + "doesn't work; then trace_build or read_area for the details.", new JsonObject()));
        Builds.definitions(tools);
        tools.add(tool("get_inventory", "Everything the asking player has: held item, hotbar, main inventory, armor, "
                + "off hand and Curios slots, with item ids, the adding mod, counts, durability and enchantments, then the totals in "
                + "storage they have opened within " + TouchedStorage.RANGE + " blocks, which count as theirs too.", new JsonObject()));
        tools.add(tool("building_materials", "The building blocks the asking player holds (inventory and storage they have "
                + "opened within " + TouchedStorage.RANGE + " blocks), grouped by material, each one block and everything a "
                + "crafting table or stonecutter makes out of it alone (Oak Log: its planks, stairs, slabs, fences; Andesite: "
                + "polished andesite and its stairs), with how many full blocks each makes; then the other building blocks "
                + "they hold, and the trees and ground open to the sky within " + Materials.AROUND + " blocks. Call it before "
                + "building a model for them to copy, to choose its materials.", new JsonObject()));
        JsonObject storageProps = new JsonObject();
        JsonObject filter = new JsonObject();
        JsonArray filterType = new JsonArray();
        filterType.add("string");
        filterType.add("null");
        filter.add("type", filterType);
        filter.addProperty("description", "Part of an item's English name or id to look for, such as 'iron' or 'create:andesite_alloy'; null lists everything.");
        storageProps.add("item", filter);
        tools.add(tool("search_storage", "The chests, barrels, vaults and other storage the asking player has opened that are within "
                + TouchedStorage.RANGE + " blocks of them, in this dimension. Their contents count as the player's own items. With "
                + "an item it finds that item and says which container holds it and where (direction and distance from the player); "
                + "with null it lists every item with its total.", storageProps));
        tools.add(tool("get_surroundings", "Where the asking player is and what they feel: dimension, biome, position, light, "
                + "time and day, weather, season, body and world temperature (Cold Sweat), thirst, health, hunger and active effects.",
                new JsonObject()));
        JsonObject recipeProps = new JsonObject();
        recipeProps.add("item", prop("string", "An item id such as create:brass_ingot, or an item's English name."));
        JsonObject direction = prop("string", "made_from: the recipes that make this item. used_in: the recipes that use it as an ingredient.");
        JsonArray values = new JsonArray();
        values.add("made_from");
        values.add("used_in");
        direction.add("enum", values);
        recipeProps.add("direction", direction);
        tools.add(tool("lookup_recipes", "This server's real recipes for an item, including Create processing (mixing, pressing, "
                + "crushing, sequenced assembly and so on) with heat and fluids. The pack changes many recipes, so this beats memory.",
                recipeProps));
        JsonObject findProps = new JsonObject();
        findProps.add("query", prop("string", "Part of an item's English name or id, such as 'nitrate' or 'steel'."));
        tools.add(tool("find_items", "Item ids whose name or id contains the query, with the mod that adds each. Use it to "
                + "turn a name the player typed into ids for lookup_recipes.", findProps));
        JsonObject creatureProps = new JsonObject();
        creatureProps.add("query", prop("string", "A creature's name or id ('naga', 'mowziesmobs:frostmaw'), words for how "
                + "it looks or acts ('green flies', 'hostile swims', 'boss'), a mod's name, 'nearby' for the creatures around "
                + "the player now, or 'all' for every creature by mod."));
        tools.add(tool("find_creatures", "Every creature on this server, from every mod. For a match: its name, id and mod, "
                + "whether it flies, swims, is hostile or a boss, the main colours of its skin, health, attack, armor, size, "
                + "the biomes it spawns in by itself and what a player's kill drops. 'nearby' lists the creatures within "
                + Creatures.AROUND + " blocks of the player right now (how many, how far, how high, in sight or not), even "
                + "ones that flew off screen. Use it whenever a player asks about a creature, above all one they describe "
                + "by its looks or one your notes don't describe.", creatureProps));
        tools.add(tool("get_advancements", "The asking player's advancements, a record of things they have done: how many "
                + "they have in each mod's tree, the most recent ones with their descriptions, every other one by title, and "
                + "the next steps in each tree. A next step only means the one before it is done, not that they can do it "
                + "yet; whether they can make something is what recipe_tree shows.", new JsonObject()));
        JsonObject treeProps = new JsonObject();
        treeProps.add("item", prop("string", "The item's id, such as 'create:mechanical_crafter', or its exact English name."));
        treeProps.add("amount", prop("integer", "How many they want; 1 when unsure."));
        tools.add(tool("recipe_tree", "Follows this server's recipes for an item all the way down to raw materials, measured "
                + "against the asking player: how many of each part they already have (inventory and storage they have "
                + "opened nearby), whether they have ever had one, the machine or station each step needs and whether they "
                + "have used or made one, and the raw materials still to gather. Use it before telling a player they can "
                + "make or reach something, or when they ask how far off it is.", treeProps));
        JsonObject dropProps = new JsonObject();
        dropProps.add("block", prop("string", "The block's id, such as 'minecraft:iron_ore', or its exact English name."));
        tools.add(tool("block_drops", "What a block really drops on this server, rolled from its loot table many times: by "
                + "hand or with the right tool, with Silk Touch, and with Fortune I to III, as the average and range per "
                + "item. Use it for any claim about what mining a block gives or which enchantment gives more.", dropProps));
        JsonObject guideProps = new JsonObject();
        guideProps.add("query", prop("string", "One or more entry ids from the guide notes, such as 'create_basics/rotation "
                + "industry/tfmg_steel', or words to search the whole guide for, such as 'brass funnel'."));
        tools.add(tool("read_guide", "The Field Guide's own words: the full text of entries by id (up to 6), as players read "
                + "them, or, given words instead, the entries that contain all of them. The notes in your instructions already "
                + "hold every fact of the guide; read an entry when its notes are too short on what the player needs, when "
                + "you want the guide's exact wording, or to search for something you can't place.", guideProps));
        tools.add(Diagrams.definition());
        tools.add(Models.definition());
        return tools;
    }

    static JsonObject tool(String name, String description, JsonObject properties) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        properties.keySet().forEach(required::add);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.add("input_schema", schema);
        tool.addProperty("strict", true);
        return tool;
    }

    static JsonObject prop(String type, String description) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", description);
        return p;
    }

    /** Runs one tool call; call it on the server thread (the world and the recipe list are not thread-safe). */
    static String run(String name, JsonObject input, ServerPlayer player, MinecraftServer server) {
        try {
            return switch (name) {
                case "look_at" -> lookAt(player);
                case "get_inventory" -> inventory(player);
                case "building_materials" -> Materials.describe(player, server);
                case "get_surroundings" -> surroundings(player);
                case "get_advancements" -> advancements(player, server);
                case "lookup_recipes" -> recipes(server, GuideText.string(input, "item"), GuideText.string(input, "direction"));
                case "find_items" -> findItems(GuideText.string(input, "query"));
                case "find_creatures" -> Creatures.find(player, server, GuideText.string(input, "query"));
                case "recipe_tree" -> Reach.tree(player, server, GuideText.string(input, "item"), Math.max(1, Diagrams.intOr(input, "amount", 1)));
                case "block_drops" -> Reach.drops(player, GuideText.string(input, "block"));
                case "read_guide" -> GuideText.read(GuideText.string(input, "query"));
                case "search_storage" -> {
                    TouchedStorage storage = GuideAi.storage();
                    yield storage == null ? "Storage isn't tracked on this server." : storage.describe(player,
                            input.has("item") && !input.get("item").isJsonNull() ? input.get("item").getAsString() : null, 150);
                }
                case "get_view" -> {
                    Vision.Snapshot view = GuideAi.view(player.getUUID());
                    yield view == null ? "Nothing was taken in of what they see for this question." : view.text();
                }
                case "find_placed_blocks", "trace_build", "read_area" -> {
                    PlacedBlocks placed = GuideAi.placed();
                    if (placed == null) yield "Builds aren't tracked on this server.";
                    Vision.Snapshot view = GuideAi.view(player.getUUID());
                    yield switch (name) {
                        case "find_placed_blocks" -> Builds.find(player, placed, GuideText.string(input, "query"));
                        case "trace_build" -> Builds.trace(player, placed, view, pos(input, "x", "y", "z"));
                        default -> Builds.area(player, placed, view, pos(input, "x1", "y1", "z1"), pos(input, "x2", "y2", "z2"));
                    };
                }
                default -> "Unknown tool " + name + ".";
            };
        } catch (RuntimeException | LinkageError e) {
            return "The tool failed on this server (" + e.getClass().getSimpleName() + ").";
        }
    }

    private static BlockPos pos(JsonObject input, String x, String y, String z) {
        return new BlockPos(Diagrams.intOr(input, x, 0), Diagrams.intOr(input, y, 0), Diagrams.intOr(input, z, 0));
    }

    // ---------- get_advancements ----------

    private static final int RECENT_ADVANCEMENTS = 15, MAX_NEXT = 25;

    static String advancements(ServerPlayer player, MinecraftServer server) {
        var manager = server.getAdvancements();
        record Done(net.minecraft.advancements.AdvancementHolder holder, java.time.Instant when) {
        }
        List<Done> done = new ArrayList<>();
        List<String> next = new ArrayList<>();
        Map<String, int[]> perMod = new java.util.TreeMap<>();
        for (var holder : manager.getAllAdvancements()) {
            var display = holder.value().display();
            if (display.isEmpty()) continue; // recipe unlocks and other hidden bookkeeping
            // A tree's root is usually granted the moment they join (BetterEnd's "enter a world that has a better End"), so
            // it says nothing about how far they have got.
            if (holder.value().parent().isEmpty()) continue;
            String mod = modName(holder.id().getNamespace());
            int[] count = perMod.computeIfAbsent(mod, k -> new int[2]);
            var progress = player.getAdvancements().getOrStartProgress(holder);
            if (!display.get().isHidden()) count[1]++;
            if (progress.isDone()) {
                count[0]++;
                if (display.get().isHidden()) count[1]++;
                java.time.Instant when = progress.getFirstProgressDate();
                done.add(new Done(holder, when == null ? java.time.Instant.EPOCH : when));
            } else if (!display.get().isHidden()) {
                // Next steps: not done yet, but the one before it is, and that one is a real step rather than a root.
                var parent = holder.value().parent().map(manager::get).orElse(null);
                boolean open = parent != null && parent.value().parent().isPresent()
                        && player.getAdvancements().getOrStartProgress(parent).isDone();
                if (open && next.size() < MAX_NEXT)
                    next.add(display.get().getTitle().getString() + " (" + mod + "): " + display.get().getDescription().getString());
            }
        }
        done.sort((a, b) -> b.when.compareTo(a.when));
        StringBuilder out = new StringBuilder("Advancements done per mod (done of visible):\n");
        perMod.forEach((mod, c) -> {
            if (c[0] > 0) out.append("- ").append(mod).append(": ").append(c[0]).append(" of ").append(c[1]).append('\n');
        });
        if (done.isEmpty()) out.append("- none yet\n");
        out.append("Most recent first:\n");
        for (int i = 0; i < done.size(); i++) {
            var d = done.get(i).holder.value().display().get();
            String mod = modName(done.get(i).holder.id().getNamespace());
            if (i < RECENT_ADVANCEMENTS)
                out.append("- ").append(d.getTitle().getString()).append(" (").append(mod).append("): ").append(d.getDescription().getString()).append('\n');
            else if (i == RECENT_ADVANCEMENTS)
                out.append("Earlier: ").append(d.getTitle().getString());
            else
                out.append(", ").append(d.getTitle().getString());
        }
        if (done.size() > RECENT_ADVANCEMENTS) out.append('\n');
        out.append("Next steps in their trees (the one before is done; not a sign they can do it yet):\n");
        if (next.isEmpty()) out.append("- none listed\n");
        next.forEach(n -> out.append("- ").append(n).append('\n'));
        return out.toString().strip();
    }

    // ---------- look_at ----------

    static String lookAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1f);
        Vec3 end = eye.add(view.scale(REACH));
        HitResult block = player.pick(REACH, 1f, false);
        double blockDist = block.getType() == HitResult.Type.MISS ? REACH * REACH : block.getLocation().distanceToSqr(eye);
        AABB box = player.getBoundingBox().expandTowards(view.scale(REACH)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && !e.isInvisibleTo(player), blockDist);
        if (entity != null) return describeEntity(entity.getEntity());
        if (block instanceof BlockHitResult hit && block.getType() == HitResult.Type.BLOCK) return describeBlock(player, hit.getBlockPos());
        return "The player is not looking at any block or creature within " + (int) REACH + " blocks.";
    }

    private static String describeBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        StringBuilder out = new StringBuilder();
        out.append("Block: ").append(state.getBlock().getName().getString()).append(" (").append(id).append(", from ")
                .append(modName(id.getNamespace())).append("), ").append(String.format(Locale.ROOT, "%.1f", Math.sqrt(player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))))).append(" blocks away.\n");
        if (!state.getValues().isEmpty()) {
            out.append("State:");
            for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet())
                out.append(' ').append(e.getKey().getName()).append('=').append(e.getValue());
            out.append('\n');
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {
            out.append("Mining: unbreakable.\n");
        } else {
            List<String> tools = new ArrayList<>();
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) tools.add("pickaxe");
            if (state.is(BlockTags.MINEABLE_WITH_AXE)) tools.add("axe");
            if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) tools.add("shovel");
            if (state.is(BlockTags.MINEABLE_WITH_HOE)) tools.add("hoe");
            String tier = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? "diamond" : state.is(BlockTags.NEEDS_IRON_TOOL) ? "iron"
                    : state.is(BlockTags.NEEDS_STONE_TOOL) ? "stone" : "";
            out.append("Mining: hardness ").append(hardness).append(", tool ").append(tools.isEmpty() ? "any" : String.join(" or ", tools));
            if (!tier.isEmpty()) out.append(", needs at least a ").append(tier).append(" tool");
            out.append(state.requiresCorrectToolForDrops() ? ", drops nothing without the right tool" : "");
            ItemStack held = player.getMainHandItem();
            out.append(". The held item (").append(held.isEmpty() ? "empty hand" : held.getHoverName().getString())
                    .append(player.hasCorrectToolForDrops(state) ? ") can harvest it" : ") can't harvest it").append(".\n");
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IHaveGoggleInformation goggles) {
            List<Component> lines = new ArrayList<>();
            try {
                goggles.addToGoggleTooltip(lines, player.isShiftKeyDown());
            } catch (RuntimeException ignored) {
                // Some add-ons only fill this on the client.
            }
            if (!lines.isEmpty()) {
                out.append("Engineer's Goggles show:\n");
                for (Component c : lines) {
                    String s = c.getString().strip();
                    if (!s.isEmpty()) out.append("  ").append(s).append('\n');
                }
            }
        }
        out.append("Biome here: ").append(biomeName(level, pos)).append('.');
        return out.toString();
    }

    private static String describeEntity(Entity e) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        StringBuilder out = new StringBuilder("Creature: ").append(e.getType().getDescription().getString());
        if (e.hasCustomName()) out.append(" named ").append(e.getCustomName().getString());
        out.append(" (").append(id).append(", from ").append(modName(id.getNamespace())).append(")");
        if (e instanceof LivingEntity living)
            out.append(String.format(Locale.ROOT, ", health %.0f of %.0f", living.getHealth(), living.getMaxHealth()));
        out.append(e instanceof Enemy ? ", hostile" : ", not hostile").append('.');
        return out.toString();
    }

    // ---------- get_inventory ----------

    static String inventory(ServerPlayer player) {
        var inv = player.getInventory();
        StringBuilder out = new StringBuilder();
        out.append("Held: ").append(stack(player.getMainHandItem())).append('\n');
        out.append("Off hand: ").append(stack(player.getOffhandItem())).append('\n');
        out.append("Hotbar:");
        for (int i = 0; i < 9; i++) if (!inv.items.get(i).isEmpty()) out.append("\n  ").append(stack(inv.items.get(i)));
        out.append("\nInventory:");
        boolean any = false;
        for (int i = 9; i < inv.items.size(); i++) {
            if (inv.items.get(i).isEmpty()) continue;
            out.append("\n  ").append(stack(inv.items.get(i)));
            any = true;
        }
        if (!any) out.append(" empty");
        out.append("\nArmor:");
        for (ItemStack s : inv.armor) if (!s.isEmpty()) out.append("\n  ").append(stack(s));
        String curios = Curios.list(player);
        if (!curios.isEmpty()) out.append("\nCurios:").append(curios);
        TouchedStorage storage = GuideAi.storage();
        if (storage != null) out.append("\nStorage nearby, which counts as theirs: ").append(storage.describe(player, null, 60));
        return out.toString();
    }

    static String stack(ItemStack s) {
        if (s.isEmpty()) return "nothing";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        StringBuilder out = new StringBuilder();
        out.append(s.getCount()).append("x ").append(s.getHoverName().getString()).append(" (").append(id);
        if (!id.getNamespace().equals("minecraft")) out.append(", ").append(modName(id.getNamespace()));
        out.append(')');
        if (s.isDamageableItem()) out.append(", durability ").append(s.getMaxDamage() - s.getDamageValue()).append('/').append(s.getMaxDamage());
        if (s.isEnchanted()) {
            List<String> names = new ArrayList<>();
            for (var e : s.getTagEnchantments().entrySet()) names.add(Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
            out.append(", enchanted: ").append(String.join(", ", names));
        }
        return out.toString();
    }

    /** Curios only when the mod is there; the class is loaded lazily, so the API is never touched otherwise. */
    private static final class Curios {
        static String list(ServerPlayer player) {
            if (!ModList.get().isLoaded("curios")) return "";
            try {
                StringBuilder out = new StringBuilder();
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(h -> {
                    var items = h.getEquippedCurios();
                    for (int i = 0; i < items.getSlots(); i++)
                        if (!items.getStackInSlot(i).isEmpty()) out.append("\n  ").append(stack(items.getStackInSlot(i)));
                });
                return out.toString();
            } catch (RuntimeException | LinkageError e) {
                return "";
            }
        }
    }

    // ---------- get_surroundings ----------

    static String surroundings(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        long time = level.getDayTime();
        int hour = (int) ((time % 24000 / 1000 + 6) % 24);
        int minute = (int) (time % 1000 * 60 / 1000);
        StringBuilder out = new StringBuilder();
        out.append("Dimension: ").append(level.dimension().location()).append('\n');
        out.append("Biome: ").append(biomeName(level, pos)).append('\n');
        out.append("Position: x ").append(pos.getX()).append(", y ").append(pos.getY()).append(", z ").append(pos.getZ())
                .append(level.canSeeSky(pos.above()) ? " (open sky above)" : " (under cover)").append('\n');
        out.append("Light: ").append(level.getMaxLocalRawBrightness(pos)).append(" of 15\n");
        out.append(String.format(Locale.ROOT, "Time: day %d, %02d:%02d\n", time / 24000 + 1, hour, minute));
        out.append("Weather: ").append(level.isThundering() ? "thunderstorm" : level.isRaining() ? "rain or snow" : "clear")
                .append(level.isRainingAt(pos.above()) ? ", and it is falling on the player" : "").append('\n');
        String season = Seasons.describe(level);
        if (!season.isEmpty()) out.append("Season: ").append(season).append('\n');
        String temperature = Temperatures.describe(player);
        if (!temperature.isEmpty()) out.append(temperature).append('\n');
        String thirst = Thirst.describe(player);
        if (!thirst.isEmpty()) out.append(thirst).append('\n');
        out.append(String.format(Locale.ROOT, "Health: %.0f of %.0f, hunger %d of 20, saturation %.1f, air %d of %d\n",
                player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
                player.getAirSupply(), player.getMaxAirSupply()));
        if (!player.getActiveEffects().isEmpty()) {
            out.append("Effects:");
            for (MobEffectInstance e : player.getActiveEffects())
                out.append(' ').append(e.getEffect().value().getDisplayName().getString()).append(e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "")
                        .append(" (").append(e.isInfiniteDuration() ? "lasting" : e.getDuration() / 20 + " s").append(')');
            out.append('\n');
        }
        out.append("Experience level: ").append(player.experienceLevel).append(", game mode: ").append(player.gameMode.getGameModeForPlayer().getName());
        return out.toString();
    }

    private static final class Seasons {
        static String describe(ServerLevel level) {
            if (!ModList.get().isLoaded("sereneseasons")) return "";
            try {
                var state = sereneseasons.api.season.SeasonHelper.getSeasonState(level);
                String sub = state.getSubSeason().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                int dayInSub = state.getDay() % Math.max(1, state.getSubSeasonDuration() / Math.max(1, state.getDayDuration())) + 1;
                return sub + " (day " + dayInSub + " of this part of the season)";
            } catch (RuntimeException | LinkageError e) {
                return "";
            }
        }
    }

    private static final class Temperatures {
        static String describe(ServerPlayer player) {
            if (!ModList.get().isLoaded("cold_sweat")) return "";
            try {
                double body = com.momosoftworks.coldsweat.api.util.Temperature.get(player, com.momosoftworks.coldsweat.api.util.Temperature.Trait.BODY);
                double world = com.momosoftworks.coldsweat.api.util.Temperature.get(player, com.momosoftworks.coldsweat.api.util.Temperature.Trait.WORLD);
                double min = com.momosoftworks.coldsweat.api.util.Temperature.get(player, com.momosoftworks.coldsweat.api.util.Temperature.Trait.FREEZING_POINT);
                double max = com.momosoftworks.coldsweat.api.util.Temperature.get(player, com.momosoftworks.coldsweat.api.util.Temperature.Trait.BURNING_POINT);
                var F = com.momosoftworks.coldsweat.api.util.Temperature.Units.F;
                var MC = com.momosoftworks.coldsweat.api.util.Temperature.Units.MC;
                double wf = com.momosoftworks.coldsweat.api.util.Temperature.convert(world, MC, F, true);
                double minF = com.momosoftworks.coldsweat.api.util.Temperature.convert(min, MC, F, true);
                double maxF = com.momosoftworks.coldsweat.api.util.Temperature.convert(max, MC, F, true);
                String feel = body <= -100 ? "freezing (taking damage)" : body < -50 ? "cold" : body >= 100 ? "overheating (taking damage)"
                        : body > 50 ? "hot" : "comfortable";
                return String.format(Locale.ROOT, "Temperature: air %.0f F, comfortable range here %.0f to %.0f F; body %.0f on Cold Sweat's "
                        + "scale (0 is ideal, -100 or 100 hurts), feels %s", wf, minF, maxF, body, feel);
            } catch (RuntimeException | LinkageError e) {
                return "";
            }
        }
    }

    private static final class Thirst {
        static String describe(ServerPlayer player) {
            if (!ModList.get().isLoaded("thirst")) return "";
            try {
                var data = player.getData(cn.mlus.thirst.foundation.common.capability.ModAttachment.PLAYER_THIRST.get());
                return "Thirst: " + data.getThirst() + " of 20, quenched " + data.getQuenched();
            } catch (RuntimeException | LinkageError e) {
                return "";
            }
        }
    }

    // ---------- lookup_recipes and find_items ----------

    static String findItems(String query) {
        String q = query.toLowerCase(Locale.ROOT).strip();
        if (q.length() < 2) return "Give at least two letters.";
        List<String> hits = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String name = item.getDescription().getString();
            if (id.toString().contains(q) || name.toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(name + " = " + id + " (" + modName(id.getNamespace()) + ")");
                if (hits.size() >= 30) break;
            }
        }
        return hits.isEmpty() ? "No item matches '" + query + "'." : String.join("\n", hits);
    }

    static String recipes(MinecraftServer server, String itemQuery, String direction) {
        Item item = resolveItem(itemQuery);
        if (item == null) return "No item called '" + itemQuery + "'. Try find_items first.";
        ItemStack probe = new ItemStack(item);
        boolean uses = "used_in".equals(direction);
        var registries = server.registryAccess();
        List<String> found = new ArrayList<>();
        int total = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            boolean match;
            try {
                match = uses ? recipe.getIngredients().stream().anyMatch(i -> !i.isEmpty() && i.test(probe))
                        : outputs(recipe, registries).stream().anyMatch(s -> s.is(item));
            } catch (RuntimeException e) {
                continue;
            }
            if (!match) continue;
            total++;
            if (found.size() < MAX_RECIPES) found.add(describeRecipe(holder, registries));
        }
        String name = item.getDescription().getString() + " (" + BuiltInRegistries.ITEM.getKey(item) + ")";
        if (total == 0 && !uses) {
            String colours = otherColours(server, item);
            if (colours != null) return colours;
        }
        if (total == 0) return uses ? "No recipe on this server uses " + name + "." : "No recipe on this server makes " + name
                + ". It may only come from mining, mobs, loot or trading.";
        return (uses ? "Recipes that use " : "Recipes that make ") + name + ", " + total + " found"
                + (total > found.size() ? ", first " + found.size() + " shown" : "") + ":\n" + String.join("\n", found);
    }

    /**
     * For a coloured item no recipe makes directly (a mod that crafts one colour and recolours it with a special
     * recipe): the colour that is crafted, and the special recipes that change it. Null when that isn't the case.
     */
    private static String otherColours(MinecraftServer server, Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        String suffix = null;
        for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values())
            if (key.getPath().startsWith(c.getName() + "_")) suffix = key.getPath().substring(c.getName().length() + 1);
        if (suffix == null) return null;
        var registries = server.registryAccess();
        List<String> special = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes())
            if (holder.id().getNamespace().equals(key.getNamespace()) && holder.id().getPath().contains(suffix)
                    && holder.value() instanceof net.minecraft.world.item.crafting.CustomRecipe) special.add(holder.id().toString());
        for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values()) {
            ResourceLocation other = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), c.getName() + "_" + suffix);
            if (other.equals(key) || !BuiltInRegistries.ITEM.containsKey(other)) continue;
            Item o = BuiltInRegistries.ITEM.get(other);
            List<String> made = new ArrayList<>();
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                try {
                    if (outputs(holder.value(), registries).stream().anyMatch(st -> st.is(o))) made.add(describeRecipe(holder, registries));
                } catch (RuntimeException e) {
                    // skip
                }
                if (made.size() >= 2) break;
            }
            if (made.isEmpty()) continue;
            return "No recipe makes " + item.getDescription().getString() + " (" + key + ") directly; it is one colour of "
                    + "a block that is crafted as " + o.getDescription().getString() + " (" + other + ")"
                    + (special.isEmpty() ? "" : " and changed to the other colours by the special recipe " + String.join(", ", special)
                    + " (one the recipe list can't show; its name says what it does)")
                    + ". Recipes that make " + o.getDescription().getString() + ":\n" + String.join("\n", made);
        }
        return null;
    }

    static Item resolveItem(String query) {
        String q = query.strip();
        ResourceLocation id = ResourceLocation.tryParse(q.toLowerCase(Locale.ROOT));
        if (id != null && q.contains(":") && BuiltInRegistries.ITEM.containsKey(id)) return BuiltInRegistries.ITEM.get(id);
        Item partial = null;
        for (Item item : BuiltInRegistries.ITEM) {
            String name = item.getDescription().getString();
            if (name.equalsIgnoreCase(q)) return item;
            if (partial == null && id != null && BuiltInRegistries.ITEM.getKey(item).getPath().equals(id.getPath())) partial = item;
        }
        return partial;
    }

    static List<ItemStack> outputs(Recipe<?> recipe, net.minecraft.core.HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        if (recipe instanceof ProcessingRecipe<?, ?> processing) {
            for (ProcessingOutput o : processing.getRollableResults()) out.add(o.getStack());
        } else {
            out.add(recipe.getResultItem(registries));
        }
        return out;
    }

    private static String describeRecipe(RecipeHolder<?> holder, net.minecraft.core.HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        StringBuilder out = new StringBuilder("- ").append(type == null ? "?" : type).append(" (").append(holder.id()).append("): ");
        List<String> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) if (!ingredient.isEmpty()) inputs.add(ingredient(ingredient));
        if (recipe instanceof ProcessingRecipe<?, ?> processing) {
            try {
                for (SizedFluidIngredient f : processing.getFluidIngredients()) {
                    var stacks = f.getFluids();
                    inputs.add(f.amount() + " mB " + (stacks.length == 0 ? "fluid" : stacks[0].getHoverName().getString()));
                }
            } catch (RuntimeException ignored) {
                // Fluid ingredients are a bonus; the item side is what matters most.
            }
        }
        out.append(inputs.isEmpty() ? "no item inputs" : String.join(" + ", inputs)).append(" -> ");
        List<String> results = new ArrayList<>();
        if (recipe instanceof ProcessingRecipe<?, ?> processing) {
            for (ProcessingOutput o : processing.getRollableResults())
                results.add(o.getStack().getCount() + "x " + o.getStack().getHoverName().getString()
                        + (o.getChance() < 1f ? String.format(Locale.ROOT, " (%.0f%% chance)", o.getChance() * 100) : ""));
            try {
                var heat = processing.getRequiredHeat();
                if (heat != null && !heat.name().equals("NONE")) out.insert(0, "[needs " + heat.name().toLowerCase(Locale.ROOT) + " heat] ");
            } catch (RuntimeException | LinkageError ignored) {
                // Only basin recipes carry heat.
            }
        } else {
            ItemStack r = recipe.getResultItem(registries);
            if (!r.isEmpty()) results.add(r.getCount() + "x " + r.getHoverName().getString());
        }
        out.append(results.isEmpty() ? "(special result)" : String.join(", ", results));
        return out.toString();
    }

    private static String ingredient(Ingredient ingredient) {
        ItemStack[] options = ingredient.getItems();
        if (options.length == 0) return "?";
        Set<String> names = new LinkedHashSet<>();
        for (ItemStack s : options) names.add(s.getHoverName().getString());
        List<String> list = new ArrayList<>(names);
        if (list.size() == 1) return list.get(0);
        if (list.size() <= 3) return String.join(" or ", list);
        return list.get(0) + " or " + (list.size() - 1) + " others";
    }

    // ---------- shared ----------

    static String modName(String namespace) {
        if (namespace.equals("minecraft")) return "Minecraft";
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    static String biomeName(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey()
                .map(k -> Component.translatable(Util.makeDescriptionId("biome", k.location())).getString() + " (" + k.location() + ")")
                .orElse("unknown");
    }
}
