package dev.creategalore.bridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Tracing a schematic by hand. Create shows a placed schematic only while the Schematic is in the main hand, so it
 * goes away as soon as a block is picked up to place. With a placed Schematic in the off hand (and none in the main
 * hand) this keeps the build shown where it was placed, turned and mirrored as placed, with Create's own schematic
 * renderer. Blocks already in place (the same block facing the same way) drop out of it, so what is left is what is
 * still to do, and a different block in a spot gets a red outline. Client only: the server and other players see
 * nothing of it; {@link PinnedSchematicView} shows a pinned one to everyone.
 */
public final class SchematicTrace {
    private static final Object WRONG = new Object();
    private static final int RED = 0xE5484D, CHECK_EVERY = 10;
    /** Properties that decide how a block sits; the rest (power, joins to neighbours, waterlogging) settle by themselves. */
    private static final Set<String> PLACEMENT = Set.of("facing", "horizontal_facing", "axis", "half", "type", "face",
            "attach_face", "hinge", "rotation", "part", "slope");

    /** The Schematic shown: its file, owner, anchor, turn and mirror; a change to any loads it again. */
    private static String shownKey;
    private static BlockPos anchor;
    private static StructureTemplate template;
    private static StructurePlaceSettings settings;
    /** Every block of the build, relative to the anchor, as placed (turned and mirrored). */
    private static Map<BlockPos, BlockState> wanted = Map.of();
    private static Set<BlockPos> left = Set.of();
    private static SchematicRenderer renderer;
    private static int ticks;
    private static String lastSaid = "";

    private SchematicTrace() {
    }

    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ItemStack off = player == null ? ItemStack.EMPTY : player.getOffhandItem();
        if (player == null || mc.level == null || AllItems.SCHEMATIC.isIn(player.getMainHandItem()) || !AllItems.SCHEMATIC.isIn(off)
                || !off.has(AllDataComponents.SCHEMATIC_FILE) || !off.getOrDefault(AllDataComponents.SCHEMATIC_DEPLOYED, false)) {
            clear();
            return;
        }
        String key = dev.creategalore.bridge.PinnedSchematics.placementKey(off, mc.level.dimension().location());
        // Pinned right here already: the pin shows it, for everyone.
        if (PinnedSchematicView.isPinned(key)) {
            clear();
            return;
        }
        if (!key.equals(shownKey)) {
            clear();
            shownKey = key;
            load(mc, off);
            ticks = 0;
        }
        if (template == null) return;
        Outliner.getInstance().keep(WRONG);
        if (ticks++ % CHECK_EVERY != 0) return;
        if (player.position().distanceTo(Vec3.atCenterOf(anchor)) > 160) return;
        compare(mc);
    }

    private static void load(Minecraft mc, ItemStack stack) {
        StructureTemplate t = SchematicItem.loadSchematic(mc.level, stack);
        if (t.getSize().equals(Vec3i.ZERO)) {
            say(mc, "This Schematic's file isn't in your schematics folder, so it can't be shown from the off hand");
            return;
        }
        template = t;
        anchor = stack.getOrDefault(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
        settings = SchematicItem.getSettings(stack, false);
        SchematicLevel scratch = place(mc);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        scratch.getBlockMap().forEach((pos, state) -> {
            if (!state.isAir()) blocks.put(pos.immutable(), state);
        });
        wanted = blocks;
        left = Set.of();
    }

    /** The build placed in a scratch level at the anchor's spot (0, 0, 0), turned and mirrored as the Schematic says. */
    private static SchematicLevel place(Minecraft mc) {
        SchematicLevel level = new SchematicLevel(mc.level);
        template.placeInWorld(level, BlockPos.ZERO, BlockPos.ZERO, settings, level.getRandom(), 2);
        return level;
    }

    /** Which blocks are still to place, which spots hold the wrong block; redraws the build when that changed. */
    private static void compare(Minecraft mc) {
        Set<BlockPos> todo = new java.util.HashSet<>();
        List<BlockPos> wrong = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Map.Entry<BlockPos, BlockState> e : wanted.entrySet()) {
            at.setWithOffset(e.getKey(), anchor);
            BlockState have = mc.level.getBlockState(at);
            if (same(e.getValue(), have)) continue;
            todo.add(e.getKey());
            if (!have.isAir() && !have.canBeReplaced()) wrong.add(at.immutable());
        }
        Outliner outliner = Outliner.getInstance();
        if (wrong.isEmpty()) outliner.remove(WRONG);
        else outliner.showCluster(WRONG, wrong).colored(RED).disableLineNormals().lineWidth(1 / 16f);
        if (!todo.equals(left) || renderer == null) {
            left = todo;
            SchematicLevel shown = place(mc);
            for (BlockPos p : wanted.keySet()) if (!todo.contains(p)) shown.setBlock(p, Blocks.AIR.defaultBlockState(), 0);
            renderer = todo.isEmpty() ? null : new SchematicRenderer(shown);
        }
        say(mc, todo.isEmpty() ? "Schematic traced: every block is in place"
                : "Schematic: " + todo.size() + (todo.size() == 1 ? " block" : " blocks") + " left to place"
                        + (wrong.isEmpty() ? "" : ", " + wrong.size() + " in the wrong spot (red)"));
    }

    /** The right block, sitting the right way; joins, power and water settle by themselves once placed. */
    static boolean same(BlockState want, BlockState have) {
        if (want.getBlock() != have.getBlock()) return false;
        for (Property<?> p : want.getProperties())
            if (PLACEMENT.contains(p.getName()) && have.hasProperty(p) && !Objects.equals(want.getValue(p), have.getValue(p))) return false;
        return true;
    }

    /** Whether an off-hand Schematic is being traced (it then has the action bar). */
    static boolean active() {
        return template != null;
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || renderer == null || anchor == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
        ms.pushPose();
        ms.translate(anchor.getX() - camera.x, anchor.getY() - camera.y, anchor.getZ() - camera.z);
        try {
            renderer.render(ms, buffer);
        } catch (RuntimeException e) {
            renderer = null;
        }
        buffer.draw();
        ms.popPose();
    }

    private static void say(Minecraft mc, String text) {
        if (text.equals(lastSaid) || mc.player == null) return;
        lastSaid = text;
        mc.player.displayClientMessage(Component.literal(text), true);
    }

    private static void clear() {
        if (shownKey == null) return;
        shownKey = null;
        template = null;
        renderer = null;
        wanted = Map.of();
        left = Set.of();
        lastSaid = "";
        Outliner.getInstance().remove(WRONG);
    }
}
