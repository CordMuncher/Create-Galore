package dev.creategalore.datacheck;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/** /datacheck: lists recipes nobody can make (an ingredient matches nothing) and blocks that drop nothing with any tool. */
@Mod("create_galore_datacheck")
@EventBusSubscriber(modid = "create_galore_datacheck")
public class DataCheck {
    @SubscribeEvent
    static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("datacheck").requires(s -> s.hasPermission(2)).executes(c -> {
            Path out = run(c.getSource().getServer());
            c.getSource().sendSuccess(() -> Component.literal("Data check written to " + out), false);
            return 1;
        }));
    }

    static Path run(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        int recipes = 0, broken = 0;
        for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
            recipes++;
            List<String> problems = new ArrayList<>();
            walk(h.value(), 0, problems, Collections.newSetFromMap(new IdentityHashMap<>()));
            if (!problems.isEmpty()) {
                broken++;
                lines.add("RECIPE\t" + h.id() + "\t" + BuiltInRegistries.RECIPE_TYPE.getKey(h.value().getType()) + "\t" + String.join(" ; ", new LinkedHashSet<>(problems)));
            }
        }
        int blocks = 0, noLoot = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            ResourceKey<LootTable> key = b.getLootTable();
            if (key == BuiltInLootTables.EMPTY || b.asItem() == Items.AIR) continue;
            blocks++;
            LootTable t = server.reloadableRegistries().getLootTable(key);
            // A block can also drop from code (getDrops), so break it with every tool, with and without Silk Touch, a few times.
            String drops = null;
            // Beds, doors and tall plants only drop from one half, cakes only when whole: try the block's states (up to 64, spread out).
            var states = b.getStateDefinition().getPossibleStates();
            int step = Math.max(1, states.size() / 64);
            for (int i = 0; i < states.size() && (drops == null || drops.startsWith("!")); i += step) {
                String d = dropsSomething(server, states.get(i));
                if (d != null) drops = d;
            }
            if (drops == null) {
                noLoot++;
                lines.add("NODROP\t" + BuiltInRegistries.BLOCK.getKey(b) + "\t" + (t == LootTable.EMPTY ? "no loot table " : "loot table ") + key.location());
            } else if (b instanceof SlabBlock) {
                // A double slab is two slabs: it should give two back.
                int most = mostDropped(server, b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), b.asItem());
                if (most == 1) lines.add("SLAB1\t" + BuiltInRegistries.BLOCK.getKey(b) + "\tdouble slab drops 1");
            } else if (drops.startsWith("!")) {
                lines.add("DROPERR\t" + BuiltInRegistries.BLOCK.getKey(b) + "\t" + drops.substring(1));
            }
        }
        lines.add(0, "# recipes " + recipes + ", unmakeable " + broken + "; blocks with an item " + blocks + ", drop nothing with any tool " + noLoot);
        Path out = server.getServerDirectory().resolve("datacheck/report.txt");
        try {
            Files.createDirectories(out.getParent());
            Files.write(out, lines);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return out;
    }

    static List<ItemStack> tools;

    static String dropsSomething(MinecraftServer server, BlockState state) {
        ServerLevel level = server.overworld();
        if (tools == null) {
            tools = new ArrayList<>();
            var ench = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            for (var it : List.of(Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, Items.SHEARS, Items.NETHERITE_SWORD)) {
                tools.add(new ItemStack(it));
                ItemStack silk = new ItemStack(it);
                silk.enchant(ench.getOrThrow(Enchantments.SILK_TOUCH), 1);
                tools.add(silk);
            }
            tools.add(ItemStack.EMPTY);
        }
        String err = null;
        for (int round = 0; round < 6; round++) {
            for (ItemStack tool : tools) {
                try {
                    LootParams.Builder lp = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.ZERO)).withParameter(LootContextParams.TOOL, tool);
                    if (state.getBlock() instanceof EntityBlock eb) {
                        try {
                            BlockEntity be = eb.newBlockEntity(BlockPos.ZERO, state);
                            if (be != null) { be.setLevel(level); lp.withParameter(LootContextParams.BLOCK_ENTITY, be); }
                        } catch (Exception ignored) {
                        }
                    }
                    if (!state.getDrops(lp).isEmpty()) return "";
                } catch (Exception ex) {
                    err = "!" + ex;
                }
            }
        }
        return err;
    }

    static int mostDropped(MinecraftServer server, BlockState state, net.minecraft.world.item.Item item) {
        int most = 0;
        for (ItemStack tool : tools) {
            if (tool.isEmpty() || tool.isEnchanted()) continue;
            try {
                LootParams.Builder lp = new LootParams.Builder(server.overworld()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.ZERO)).withParameter(LootContextParams.TOOL, tool);
                for (ItemStack s : state.getDrops(lp)) if (s.is(item)) most = Math.max(most, s.getCount());
            } catch (Exception ignored) {
            }
        }
        return most;
    }

    // Looks through a recipe object (and the objects it holds, a few levels deep) for item and fluid ingredients.
    static void walk(Object o, int depth, List<String> problems, Set<Object> seen) {
        if (o == null || depth > 5 || !seen.add(o)) return;
        if (o instanceof Ingredient ing) { check(ing, problems); return; }
        if (o instanceof SizedIngredient s) { check(s.ingredient(), problems); return; }
        if (o instanceof FluidIngredient f) { checkFluid(f, problems); return; }
        if (o instanceof SizedFluidIngredient s) { checkFluid(s.ingredient(), problems); return; }
        if (o instanceof ItemStack || o instanceof CharSequence || o instanceof Number || o instanceof Enum<?> || o instanceof Boolean) return;
        if (o instanceof Iterable<?> it) { for (Object x : it) walk(x, depth + 1, problems, seen); return; }
        if (o instanceof Map<?, ?> m) { for (Object x : m.values()) walk(x, depth + 1, problems, seen); return; }
        if (o.getClass().isArray()) {
            if (o.getClass().getComponentType().isPrimitive()) return;
            for (int i = 0; i < Array.getLength(o); i++) walk(Array.get(o, i), depth + 1, problems, seen);
            return;
        }
        String cn = o.getClass().getName();
        if (cn.startsWith("java.") || cn.startsWith("com.mojang.") || cn.startsWith("com.google.")) return;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    walk(f.get(o), depth + 1, problems, seen);
                } catch (Exception ignored) {
                }
            }
        }
    }

    static void check(Ingredient ing, List<String> problems) {
        if (ing.isEmpty()) return; // a blank slot in a shaped recipe
        ItemStack[] items = ing.getItems();
        // NeoForge shows an empty tag as a barrier named "Empty Tag: ..."
        boolean none = Arrays.stream(items).allMatch(s -> s.isEmpty() || s.is(Items.BARRIER));
        if (none) problems.add("item " + describe(ing, items));
    }

    static void checkFluid(FluidIngredient f, List<String> problems) {
        if (f.hasNoFluids()) problems.add("fluid " + f);
    }

    static String describe(Ingredient ing, ItemStack[] items) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : items) sb.append(s.getHoverName().getString()).append(' ');
        if (sb.isEmpty()) sb.append(ing.toString());
        return sb.toString().trim();
    }
}
