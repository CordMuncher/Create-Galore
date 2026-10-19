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
 * for themselves: what's under their crosshair, what they carry, the weather they stand in, and the server's recipe
 * list. Nothing reveals map locations, hidden blocks or loot tables.
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
        tools.add(tool("get_inventory", "Everything the asking player carries: held item, hotbar, main inventory, armor, "
                + "off hand and Curios slots, with item ids, the adding mod, counts, durability and enchantments.", new JsonObject()));
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
        tools.add(tool("get_advancements", "The asking player's advancements, the game's record of how far they have got: "
                + "how many they have in each mod's tree, the most recent ones with their descriptions, every other one by "
                + "title, and the visible ones that come right after what they have done. Use it with their inventory and "
                + "surroundings to judge where they are in the game, for example when they ask what to do next.", new JsonObject()));
        tools.add(Diagrams.definition());
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
                case "get_surroundings" -> surroundings(player);
                case "get_advancements" -> advancements(player, server);
                case "lookup_recipes" -> recipes(server, GuideText.string(input, "item"), GuideText.string(input, "direction"));
                case "find_items" -> findItems(GuideText.string(input, "query"));
                default -> "Unknown tool " + name + ".";
            };
        } catch (RuntimeException | LinkageError e) {
            return "The tool failed on this server (" + e.getClass().getSimpleName() + ").";
        }
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
                // Next steps: not done yet, but the one before it is (or it starts a tree).
                var parent = holder.value().parent().map(manager::get).orElse(null);
                boolean open = parent == null || player.getAdvancements().getOrStartProgress(parent).isDone();
                if (open && parent != null && next.size() < MAX_NEXT)
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
        out.append("Next ones within reach (the one before is done):\n");
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
        if (total == 0) return uses ? "No recipe on this server uses " + name + "." : "No recipe on this server makes " + name
                + ". It may only come from mining, mobs, loot or trading.";
        return (uses ? "Recipes that use " : "Recipes that make ") + name + ", " + total + " found"
                + (total > found.size() ? ", first " + found.size() + " shown" : "") + ":\n" + String.join("\n", found);
    }

    private static Item resolveItem(String query) {
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

    private static List<ItemStack> outputs(Recipe<?> recipe, net.minecraft.core.HolderLookup.Provider registries) {
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
