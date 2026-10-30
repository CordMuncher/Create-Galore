package dev.creategalore.bridge.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.client.SchematicEditScreen;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import com.simibubi.create.foundation.gui.widget.IconButton;
import dev.creategalore.bridge.PinnedSchematics;
import dev.creategalore.bridge.PinnedSchematics.Pin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Draws the server's pinned schematics ({@link PinnedSchematics}) for this player: every block still to place as a
 * faint copy of itself, a spot holding the wrong block tinted red, and blocks placed right simply gone from the ghost.
 * The ghost is cut into 16-block sections so placing a block redraws only its own section. Also puts Pin and Unpin
 * buttons on Create's Schematic placement screen.
 */
public final class PinnedSchematicView {
    private static final int RED = 0xE5484D, CHECK_EVERY = 10;
    /**
     * How faint the blocks still to place are drawn: without a shader pack, and with one (packs draw a translucent
     * surface fainter than its alpha says, Complementary's at 0.3 showed a tint with no blocks in it).
     */
    private static final float GHOST_ALPHA = 0.4f, GHOST_ALPHA_SHADERS = 0.65f, WRONG_ALPHA = 0.7f;
    /** Wrong blocks drawn red per pin (the outline still shows every one). */
    private static final int MAX_RED = 512;
    /** Pins further than this from the player (to the build's box) are neither checked nor drawn. */
    private static final double RANGE = 160;

    private static final Map<Integer, Shown> pins = new LinkedHashMap<>();
    private static int ticks;
    private static String lastSaid = "";

    private PinnedSchematicView() {
    }

    static void init() {
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(PinnedSchematicView::tick);
        bus.addListener(PinnedSchematicView::render);
        bus.addListener(PinnedSchematicView::addButtons);
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> Minecraft.getInstance().execute(() -> onRemoved(-1)));
    }

    public static void onAdded(Pin pin) {
        Minecraft.getInstance().execute(() -> {
            Shown old = pins.put(pin.id(), new Shown(pin));
            if (old != null) old.forget();
            ticks = 0;
        });
    }

    public static void onRemoved(int id) {
        Minecraft.getInstance().execute(() -> {
            if (id < 0) {
                pins.values().forEach(Shown::forget);
                pins.clear();
            } else {
                Shown old = pins.remove(id);
                if (old != null) old.forget();
            }
        });
    }

    /** Whether this Schematic placement is pinned already (the off-hand trace then leaves it to the pin). */
    static boolean isPinned(String placementKey) {
        for (Shown s : pins.values()) if (s.pin.key().equals(placementKey)) return true;
        return false;
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || pins.isEmpty()) return;
        boolean check = ticks++ % CHECK_EVERY == 0;
        Shown near = null;
        for (Shown s : pins.values()) {
            double dist = Pin.distanceSqr(s.box, s.pin.anchor(), player.blockPosition());
            boolean here = s.pin.dim() == mc.level.dimension() && dist < RANGE * RANGE;
            if (!here) {
                s.hide();
                continue;
            }
            if (!s.wrong.isEmpty()) Outliner.getInstance().keep(s.outlineKey);
            if (check) s.compare(mc);
            if (dist <= 8 * 8 && (near == null || s.left.size() > 0)) near = s;
        }
        if (!check || SchematicTrace.active()) return;
        if (near == null) {
            if (!lastSaid.isEmpty()) {
                lastSaid = "";
                player.displayClientMessage(Component.empty(), true);
            }
            return;
        }
        int left = near.left.size(), wrong = near.wrong.size();
        say(player, left == 0 ? "Pinned schematic " + near.pin.name() + ": done, every block is in place"
                : "Pinned schematic " + near.pin.name() + ": " + left + (left == 1 ? " block" : " blocks") + " left to place"
                        + (wrong == 0 ? "" : ", " + wrong + " in the wrong spot (red)"));
    }

    private static void say(LocalPlayer player, String text) {
        if (text.equals(lastSaid)) return;
        lastSaid = text;
        player.displayClientMessage(Component.literal(text), true);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || pins.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack ms = event.getPoseStack();
        SuperRenderTypeBuffer inner = DefaultSuperRenderTypeBuffer.getInstance();
        // Two passes over the same ghost: the first only fills the depth buffer, the second draws colour where a ghost
        // face is the nearest one. So a pin shows as one see-through skin (its nearest faces), not every face of every
        // block stacked behind each other (a block's own back faces culled too), and the bottom of a pin standing on the
        // ground no longer fights the ground's top face for the same pixels.
        float alpha = SchematicLayers.shadersOn() ? GHOST_ALPHA_SHADERS : GHOST_ALPHA;
        drawGhosts(ms, camera, new GhostBuffer(inner, GhostBuffer.DEPTH, 1, 1, 1, alpha));
        inner.draw();
        drawGhosts(ms, camera, new GhostBuffer(inner, GhostBuffer.COLOR, 1, 1, 1, alpha));
        inner.draw();
        GhostBuffer red = new GhostBuffer(inner, GhostBuffer.COLOR, 1, 0.22f, 0.22f, WRONG_ALPHA);
        for (Shown s : pins.values()) {
            // A wrong block is drawn again over itself in red, a hair bigger so it covers the real faces.
            int drawn = 0;
            for (BlockPos pos : s.wrong) {
                if (drawn++ >= MAX_RED) break;
                BlockState have = mc.level.getBlockState(pos);
                if (have.getRenderShape() != RenderShape.MODEL) continue;
                ms.pushPose();
                ms.translate(pos.getX() - camera.x + 0.5, pos.getY() - camera.y + 0.5, pos.getZ() - camera.z + 0.5);
                ms.scale(1.01f, 1.01f, 1.01f);
                ms.translate(-0.5, -0.5, -0.5);
                mc.getBlockRenderer().renderSingleBlock(have, ms, red, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, null);
                ms.popPose();
            }
        }
        inner.draw();
    }

    private static void drawGhosts(PoseStack ms, Vec3 camera, GhostBuffer buffer) {
        for (Shown s : pins.values()) {
            for (Map.Entry<Long, Section> e : s.sections.entrySet()) {
                SchematicRenderer renderer = e.getValue().renderer;
                if (renderer == null) continue;
                BlockPos origin = SectionPos.of(e.getKey()).origin().offset(s.pin.anchor());
                ms.pushPose();
                ms.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
                try {
                    renderer.render(ms, buffer);
                } catch (RuntimeException ex) {
                    e.getValue().renderer = null;
                }
                ms.popPose();
            }
        }
    }

    /** Pin and Unpin on Create's placement screen (sneak-right-click a Schematic). */
    private static void addButtons(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SchematicEditScreen screen)) return;
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = CreateClient.SCHEMATIC_HANDLER.getActiveSchematicItem();
        if (stack == null || mc.player == null || mc.getConnection() == null) return;
        IconButton confirm = null;
        for (var listener : event.getListenersList()) if (listener instanceof IconButton b) confirm = b;
        int y = confirm == null ? screen.height - 30 : confirm.getY() + confirm.getHeight() + 12;
        Shown pinned = null;
        String prefix = stack.get(AllDataComponents.SCHEMATIC_OWNER) + "/" + stack.get(AllDataComponents.SCHEMATIC_FILE) + "@";
        for (Shown s : pins.values()) {
            if (s.pin.dim() != mc.level.dimension() || !s.pin.key().startsWith(prefix)) continue;
            if (pinned == null || s.pin.distanceSqr(mc.player.blockPosition()) < pinned.pin.distanceSqr(mc.player.blockPosition())) pinned = s;
        }
        int width = 110, gap = 6;
        int x = pinned == null ? screen.width / 2 - width / 2 : screen.width / 2 - width - gap / 2;
        event.addListener(Button.builder(Component.literal("Pin for everyone"), b -> {
                    // Closing places the Schematic where the screen says; send that first, then pin it.
                    screen.onClose();
                    CreateClient.SCHEMATIC_HANDLER.sync();
                    mc.getConnection().sendCommand("schematic pin");
                }).bounds(x, y, width, 20)
                .tooltip(Tooltip.create(Component.literal("Leaves this build standing here as faint blocks that every player sees, "
                        + "without holding the Schematic. Build it by hand together: blocks placed right fill in, a wrong block turns red.")))
                .build());
        if (pinned != null) {
            int id = pinned.pin.id();
            event.addListener(Button.builder(Component.literal("Unpin"), b -> {
                        screen.onClose();
                        mc.getConnection().sendCommand("schematic unpin " + id);
                    }).bounds(x + width + gap, y, width, 20)
                    .tooltip(Tooltip.create(Component.literal("Takes down the pinned copy of this Schematic (#" + id + ", pinned by "
                            + pinned.pin.by() + ") for everyone.")))
                    .build());
        }
    }

    /** One pin as this client shows it. */
    private static final class Shown {
        final Pin pin;
        final BoundingBox box;
        final Object outlineKey = new Object();
        final Map<Long, Section> sections = new HashMap<>();
        Set<BlockPos> left = Set.of();
        List<BlockPos> wrong = List.of();
        boolean compared;

        Shown(Pin pin) {
            this.pin = pin;
            this.box = pin.box();
            pin.blocks().forEach((pos, state) ->
                    sections.computeIfAbsent(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4), k -> new Section())
                            .wanted.put(pos, state));
        }

        /** What is still to place and what is in the wrong spot; redraws only the sections that changed. */
        void compare(Minecraft mc) {
            Set<BlockPos> todo = new HashSet<>();
            List<BlockPos> bad = new ArrayList<>();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (Map.Entry<Long, Section> e : sections.entrySet()) {
                Section section = e.getValue();
                Set<BlockPos> sectionTodo = new HashSet<>(), ghost = new HashSet<>();
                for (Map.Entry<BlockPos, BlockState> b : section.wanted.entrySet()) {
                    at.setWithOffset(b.getKey(), pin.anchor());
                    BlockState have = mc.level.getBlockState(at);
                    if (SchematicTrace.same(b.getValue(), have)) continue;
                    sectionTodo.add(b.getKey());
                    // A wrong block is tinted red instead; a ghost inside it would share every face with it and flicker.
                    if (!have.isAir() && !have.canBeReplaced()) bad.add(at.immutable());
                    else ghost.add(b.getKey());
                }
                todo.addAll(sectionTodo);
                if (!ghost.equals(section.left) || !compared) section.redraw(mc, SectionPos.of(e.getKey()).origin(), ghost);
            }
            compared = true;
            left = todo;
            Outliner outliner = Outliner.getInstance();
            if (bad.isEmpty()) outliner.remove(outlineKey);
            else outliner.showCluster(outlineKey, bad).colored(RED).disableLineNormals().lineWidth(1 / 16f);
            wrong = bad;
        }

        /** Out of range or in another dimension: drop the drawn ghost (the blocks stay known). */
        void hide() {
            if (!compared) return;
            forget();
            compared = false;
            left = Set.of();
        }

        void forget() {
            Outliner.getInstance().remove(outlineKey);
            wrong = List.of();
            for (Section s : sections.values()) {
                s.renderer = null;
                s.left = Set.of();
            }
        }
    }

    /** A 16-block cube of a pin: its blocks (relative to the pin's anchor) and the ghost drawn of those still to place (left; a wrong block is red, not ghost). */
    private static final class Section {
        final Map<BlockPos, BlockState> wanted = new HashMap<>();
        Set<BlockPos> left = Set.of();
        SchematicRenderer renderer;

        void redraw(Minecraft mc, BlockPos origin, Set<BlockPos> todo) {
            left = todo;
            if (todo.isEmpty()) {
                renderer = null;
                return;
            }
            // Blocks sit relative to the section's corner, so the drawn box stays one section big.
            SchematicLevel level = new SchematicLevel(mc.level);
            for (BlockPos p : todo) level.setBlock(p.subtract(origin), wanted.get(p), 0);
            renderer = new SchematicRenderer(level);
        }
    }

    /**
     * Create's schematic renderer draws into the usual block layers at full strength; this sends every block layer to one
     * of two ghost layers on the block atlas and scales each vertex's colour, the way Create draws its own ghost blocks.
     * Colour and alpha ride on the vertices, and shader packs keep vertex alpha on translucent entities (they drop it on
     * translucent terrain, which drew the ghost solid under Complementary), so both layers use the entity translucent
     * shader, which shader packs map to their own translucent entity program (lit by open sky there, see
     * {@link SchematicLayers}). DEPTH writes depth only and draws nothing else (block entities included); COLOR draws where
     * DEPTH left the nearest face. Both cull faces turned away.
     */
    private record GhostBuffer(SuperRenderTypeBuffer inner, RenderType ghost, float r, float g, float b, float alpha)
            implements SuperRenderTypeBuffer {
        static final RenderType DEPTH = Layers.ghost("depth", Layers.DEPTH_ONLY), COLOR = Layers.ghost("color", Layers.COLOR_ONLY);

        private VertexConsumer consumer(VertexConsumer target, RenderType type) {
            if (ghost == DEPTH && !RenderType.chunkBufferLayers().contains(type)) return NOTHING;
            VertexConsumer faint = new ColoringVertexConsumer(target, r, g, b, alpha);
            return SchematicLayers.shadersOn() ? SchematicLayers.skyLit(faint) : faint;
        }

        private RenderType layer(RenderType type) {
            return RenderType.chunkBufferLayers().contains(type) ? ghost : type;
        }

        @Override
        public VertexConsumer getEarlyBuffer(RenderType type) {
            return consumer(inner.getEarlyBuffer(layer(type)), type);
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return consumer(inner.getBuffer(layer(type)), type);
        }

        @Override
        public VertexConsumer getLateBuffer(RenderType type) {
            return consumer(inner.getLateBuffer(layer(type)), type);
        }

        @Override
        public void draw() {
            inner.draw();
        }

        @Override
        public void draw(RenderType type) {
            inner.draw(layer(type));
        }
    }

    /** Swallows what a block entity draws during the depth pass. */
    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };

    /** Holder for the ghost layers (RenderType's state pieces are only reachable from a subclass). */
    private static final class Layers extends RenderType {
        static final WriteMaskStateShard DEPTH_ONLY = DEPTH_WRITE, COLOR_ONLY = COLOR_WRITE;

        private Layers() {
            super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {
            }, () -> {
            });
        }

        static RenderType ghost(String name, WriteMaskStateShard mask) {
            return create("create_galore_pinned_ghost_" + name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
                    true, true, CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(mask)
                            .createCompositeState(true));
        }
    }
}
