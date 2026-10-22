package dev.creategalore.bridge.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.creategalore.bridge.guideai.Models;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The book's 3D models: real blocks drawn as a small hologram on the page, which the player turns by dragging and
 * zooms with the wheel. Each block is drawn from its own model, faces hidden behind a neighbour are skipped, blocks
 * with moving parts (Create's shafts, cogs, water wheels, press heads, mixer whisks) also get their block entity
 * renderer as in the world, chests and other entity-drawn blocks are drawn as their item, and water and lava flow
 * as in the world (stained glass where they can't be drawn). Labels sit beside the model with a line to their spot,
 * so they never cover it; middle-drag moves the model in its box.
 */
final class ModelView {
    private static final int CANVAS = 0xFFE6DABA, FRAME = 0xFF8A7250, INK = 0xFF2B2014, FADED = 0xFF6E5E48, PAPER = 0xEEF9EED2;
    private static final Direction[] SIDES = Direction.values();
    /** The angle a model is first seen from: from the front, a little to the right and above. */
    static final float YAW = -35, PITCH = 28;

    private record Label(String text, float x, float y, float z) {
    }

    /** A run of cells the book changed on a build copied from the world: added or replaced, or taken out. */
    private record Change(int x, int y, int z, int x2, int y2, int z2, boolean removed) {
    }

    private static final int ADDED = 0xFF2FA12F, REMOVED = 0xFFD03A2A;

    final String title, caption;
    private final int sx, sy, sz;
    private final BlockState[] cells;
    private final List<Label> labels = new ArrayList<>();
    private final List<Change> changes = new ArrayList<>();
    private final Map<BlockState, Boolean> hasQuads = new HashMap<>(), hasWorldQuads = new HashMap<>();
    /**
     * Block entities for the blocks whose moving parts are drawn by a block entity renderer (Create's shafts, cogs, water
     * wheels, press heads, mixer whisks, fan blades, belts), set in a small world of their own so their renderers draw
     * them as they look placed; null where a block has none. Made on the first draw.
     */
    private BlockEntity[] movers;
    private boolean moversMade;
    /** The model's blocks in a small world of their own (Create's), so water and lava flow and look as placed; null without it. */
    private net.minecraft.world.level.BlockAndTintGetter world;
    /**
     * Create draws its shafts, cogs and other turning parts from their block entities, and its models hand out their
     * shapes only when asked for a "virtual" block (the way Ponder scenes draw them); ask the same way.
     */
    private static final ModelData DATA = virtualData();
    /** What a block's model gets in a real world: Create's kinetic models then leave their turning parts to the renderer. */
    private static final ModelData WORLD = ModelData.EMPTY;

    private static ModelData virtualData() {
        try {
            return Ponder.data();
        } catch (LinkageError e) {
            return ModelData.EMPTY;
        }
    }

    /** Kept apart so Ponder's classes are only touched when it is there (it comes inside Create). */
    private static final class Ponder {
        static ModelData data() {
            return net.createmod.ponder.render.VirtualRenderHelper.VIRTUAL_DATA;
        }
    }
    float yaw = YAW, pitch = PITCH, zoom = 1, panX, panY;

    private ModelView(String title, String caption, int sx, int sy, int sz) {
        this.title = title;
        this.caption = caption;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.cells = new BlockState[sx * sy * sz];
    }

    static boolean isModel(String json) {
        return json.startsWith("{\"type\":\"model\"");
    }

    /** The model in the book's JSON; a block this game doesn't know is left out rather than failing the page. */
    static ModelView parse(String json) {
        try {
            return parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            return new ModelView("", "(a model that couldn't be read)", 1, 1, 1);
        }
    }

    static ModelView parse(JsonObject o) {
        JsonArray size = o.getAsJsonArray("size");
        int sx = clamp(size.get(0).getAsInt()), sy = clamp(size.get(1).getAsInt()), sz = clamp(size.get(2).getAsInt());
        ModelView m = new ModelView(str(o, "title"), str(o, "caption"), sx, sy, sz);
        Map<String, BlockState> parsed = new HashMap<>();
        if (o.has("palette") && o.get("palette").isJsonArray()) {
            // Compact form: a palette of states and runs [palette index, x, y, z, x2, y2, z2].
            JsonArray palette = o.getAsJsonArray("palette");
            BlockState[] states = new BlockState[palette.size()];
            for (int i = 0; i < states.length; i++) states[i] = state(palette.get(i).getAsString());
            for (JsonElement e : o.getAsJsonArray("blocks")) {
                try {
                    JsonArray r = e.getAsJsonArray();
                    BlockState state = states[r.get(0).getAsInt()];
                    if (state == null) continue;
                    for (int x = Math.max(0, r.get(1).getAsInt()); x <= Math.min(sx - 1, r.get(4).getAsInt()); x++)
                        for (int y = Math.max(0, r.get(2).getAsInt()); y <= Math.min(sy - 1, r.get(5).getAsInt()); y++)
                            for (int z = Math.max(0, r.get(3).getAsInt()); z <= Math.min(sz - 1, r.get(6).getAsInt()); z++)
                                m.cells[m.index(x, y, z)] = state.isAir() ? null : state;
                } catch (RuntimeException ignored) {
                    // skip the run
                }
            }
        } else for (JsonElement e : o.getAsJsonArray("blocks")) {
            try {
                JsonObject b = e.getAsJsonObject();
                BlockState state = parsed.computeIfAbsent(str(b, "state"), ModelView::state);
                if (state == null) continue;
                for (int x = b.get("x").getAsInt(); x <= Math.min(sx - 1, b.get("x2").getAsInt()); x++)
                    for (int y = b.get("y").getAsInt(); y <= Math.min(sy - 1, b.get("y2").getAsInt()); y++)
                        for (int z = b.get("z").getAsInt(); z <= Math.min(sz - 1, b.get("z2").getAsInt()); z++)
                            if (x >= 0 && y >= 0 && z >= 0) m.cells[m.index(x, y, z)] = state.isAir() ? null : state;
            } catch (RuntimeException ignored) {
                // skip the entry
            }
        }
        if (o.has("changes") && o.get("changes").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("changes")) {
                try {
                    JsonArray r = e.getAsJsonArray();
                    m.changes.add(new Change(r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt(),
                            r.get(4).getAsInt(), r.get(5).getAsInt(), r.get(6).getAsInt() == 1));
                } catch (RuntimeException ignored) {
                    // skip the run
                }
            }
        }
        if (o.has("labels") && o.get("labels").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("labels")) {
                try {
                    JsonObject l = e.getAsJsonObject();
                    m.labels.add(new Label(str(l, "text"), l.get("x").getAsInt() + 0.5f, l.get("y").getAsInt() + 0.5f,
                            l.get("z").getAsInt() + 0.5f));
                } catch (RuntimeException ignored) {
                    // skip the label
                }
            }
        }
        return m;
    }

    private static BlockState state(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (Exception e) {
            return null;
        }
    }

    /** A fluid block has no model; where the fluid can't be drawn as in the world, show it as glass of its colour. */
    private static BlockState glassFor(BlockState s) {
        if (s.getFluidState().is(FluidTags.LAVA)) return Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
        if (s.getFluidState().is(FluidTags.WATER)) return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        return Blocks.WHITE_STAINED_GLASS.defaultBlockState();
    }

    private static boolean pureFluid(BlockState s) {
        return !s.getFluidState().isEmpty() && s.getRenderShape() == RenderShape.INVISIBLE;
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(Models.MAX_SIZE, v));
    }

    private int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    private BlockState at(int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz ? null : cells[index(x, y, z)];
    }

    // ---------- on the page ----------

    /** Height of the box the model turns in, for a page this wide. */
    static int boxHeight(int width) {
        return Math.max(80, Math.min(170, width * 3 / 5));
    }

    int screenHeight(Font font, int width) {
        int h = boxHeight(width) + 6;
        if (!title.isEmpty()) h += font.lineHeight + 2;
        if (!caption.isEmpty()) h += font.split(Component.literal(caption), width).size() * font.lineHeight + 2;
        return h;
    }

    /** Where the box was last painted, for dragging and zooming: x, y, w, h. */
    final int[] box = new int[4];

    /** Paints title, the turning model in its box, and the caption; with hint, says what the mouse can do. */
    void paint(GuiGraphics g, Font font, int left, int top, int width, String hint) {
        int y = top;
        if (!title.isEmpty()) {
            g.drawString(font, title, left + (width - font.width(title)) / 2, y, INK, false);
            y += font.lineHeight + 2;
        }
        int bw = width - 4, bh = boxHeight(width), bx = left + 2, by = y + 2;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, FRAME);
        g.fill(bx, by, bx + bw, by + bh, CANVAS);
        box[0] = bx;
        box[1] = by;
        box[2] = bw;
        box[3] = bh;
        draw(g, font, bx, by, bw, bh, yaw, pitch, zoom, panX, panY);
        int buttonW = schematicButton(g, font, bx, by, bw, bh, hint != null);
        if (hint != null) {
            List<FormattedCharSequence> lines = font.split(Component.literal(hint), Math.max(60, bw - buttonW - 16));
            int hw = 0;
            for (FormattedCharSequence l : lines) hw = Math.max(hw, font.width(l));
            hw += 6;
            int hh = lines.size() * 10;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(bx + bw - hw - 2, by + bh - hh - 2, bx + bw - 2, by + bh - 2, PAPER);
            for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), bx + bw - hw + 1, by + bh - hh - 1 + i * 10, FADED, false);
            g.pose().popPose();
        }
        y = by + bh + 4;
        if (!caption.isEmpty()) {
            for (FormattedCharSequence s : font.split(Component.literal(caption), width)) {
                g.drawString(font, s, left + (width - font.width(s)) / 2, y, FADED, false);
                y += font.lineHeight;
            }
        }
    }

    // ---------- saving as a Create schematic ----------

    /** Where the schematic button was last painted (x, y, w, h); w is 0 while it isn't shown. */
    private final int[] saveButton = new int[4];
    /** What the button says for a while after a save (the file name), and until when. */
    private String saved;
    private long savedUntil;

    /**
     * "Save as schematic" in the box's bottom left corner while the mouse is over the model, and the file name for a
     * while after a save; its width, or 0 when not shown.
     */
    private int schematicButton(GuiGraphics g, Font font, int bx, int by, int bw, int bh, boolean over) {
        boolean justSaved = saved != null && System.currentTimeMillis() < savedUntil;
        saveButton[2] = 0;
        if (!over && !justSaved) return 0;
        String text = justSaved ? "Saved: " + saved : "Save as schematic";
        int w = Math.min(bw - 8, font.width(text) + 8), x = bx + 2, y = by + bh - 14;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + w, y + 12, FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + 11, justSaved ? 0xFFDCEBC8 : PAPER);
        g.drawString(font, font.plainSubstrByWidth(text, w - 6), x + 4, y + 2, INK, false);
        g.pose().popPose();
        if (!justSaved) {
            saveButton[0] = x;
            saveButton[1] = y;
            saveButton[2] = w;
            saveButton[3] = 12;
        }
        return w;
    }

    boolean overSchematicButton(double mx, double my) {
        return saveButton[2] > 0 && mx >= saveButton[0] && mx < saveButton[0] + saveButton[2] && my >= saveButton[1]
                && my < saveButton[1] + saveButton[3];
    }

    void showSaved(String fileName) {
        saved = fileName;
        savedUntil = System.currentTimeMillis() + 5000;
    }

    /**
     * The model as a structure file (the format Create's Schematic and Quill writes and its Schematic Table reads): every
     * block but air, so a placed schematic never asks to clear what is around it. Flowing water and lava are left out:
     * their sources make them again once placed. Null for a model with nothing in it.
     */
    net.minecraft.nbt.CompoundTag structure() {
        Map<BlockState, Integer> index = new HashMap<>();
        net.minecraft.nbt.ListTag palette = new net.minecraft.nbt.ListTag(), blocks = new net.minecraft.nbt.ListTag();
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    BlockState state = cells[index(x, y, z)];
                    if (state == null || state.isAir() || !state.getFluidState().isEmpty() && pureFluid(state)
                            && !state.getFluidState().isSource()) continue;
                    Integer i = index.get(state);
                    if (i == null) {
                        i = index.size();
                        index.put(state, i);
                        palette.add(net.minecraft.nbt.NbtUtils.writeBlockState(state));
                    }
                    net.minecraft.nbt.CompoundTag b = new net.minecraft.nbt.CompoundTag();
                    b.put("pos", ints(x, y, z));
                    b.putInt("state", i);
                    blocks.add(b);
                }
        if (blocks.isEmpty()) return null;
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("size", ints(sx, sy, sz));
        tag.put("palette", palette);
        tag.put("blocks", blocks);
        tag.put("entities", new net.minecraft.nbt.ListTag());
        return net.minecraft.nbt.NbtUtils.addCurrentDataVersion(tag);
    }

    private static net.minecraft.nbt.ListTag ints(int... values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int v : values) list.add(net.minecraft.nbt.IntTag.valueOf(v));
        return list;
    }

    void turn(double dx, double dy) {
        yaw += (float) dx * 0.9f;
        pitch = Math.max(-89, Math.min(89, pitch + (float) dy * 0.9f));
    }

    void pan(double dx, double dy) {
        panX += (float) dx;
        panY += (float) dy;
    }

    void zoom(double steps) {
        zoom = Math.max(0.5f, Math.min(4f, zoom * (float) Math.pow(1.15, steps)));
    }

    void draw(GuiGraphics g, Font font, int x, int y, int w, int h, float yaw, float pitch, float zoom) {
        draw(g, font, x, y, w, h, yaw, pitch, zoom, 0, 0);
    }

    /**
     * Draws the model in the box (x, y, w, h) at this angle, moved by (panX, panY). With labels and room for them, the
     * model keeps to the middle and the labels stand in columns either side, each with a line to its spot.
     */
    void draw(GuiGraphics g, Font font, int x, int y, int w, int h, float yaw, float pitch, float zoom, float panX, float panY) {
        Minecraft mc = Minecraft.getInstance();
        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        PoseStack pose = g.pose();
        pose.pushPose();
        int gutter = labels.isEmpty() || w < 150 ? 0 : Math.min(w / 4, labelWidth(font) + 10);
        float radius = 0.5f * (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float scale = Math.min(w - 2 * gutter, h) / (2 * radius) * 0.95f * zoom;
        pose.translate(x + w / 2f + panX, y + h / 2f + panY, 50 + radius * scale);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-sx / 2f, -sy / 2f, -sz / 2f);
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();
        if (!moversMade) {
            moversMade = true;
            makeMovers(mc);
        }
        for (int by = 0; by < sy; by++)
            for (int bz = 0; bz < sz; bz++)
                for (int bx = 0; bx < sx; bx++) {
                    BlockState state = cells[index(bx, by, bz)];
                    if (state == null || hidden(bx, by, bz)) continue;
                    if (!state.getFluidState().isEmpty() && !fluid(mc, state, bx, by, bz, pose, buffers) && pureFluid(state)) {
                        state = glassFor(state);
                    }
                    if (pureFluid(state)) continue;
                    pose.pushPose();
                    pose.translate(bx, by, bz);
                    try {
                        BlockEntity mover = movers == null ? null : movers[index(bx, by, bz)];
                        if (mover != null && !drawMover(mc, mover, state, bx, by, bz, pose, buffers, random)) movers[index(bx, by, bz)] = null;
                        if (movers == null || movers[index(bx, by, bz)] == null) block(mc, blocks, state, bx, by, bz, pose, buffers, random);
                    } catch (RuntimeException ignored) {
                        // a block whose model can't be drawn out of the world is left out
                    }
                    pose.popPose();
                }
        g.flush();
        if (!changes.isEmpty()) outlineChanges(pose, buffers);
        // Where each label's cell lands on screen, while the model's transform is still in place.
        List<float[]> spots = new ArrayList<>();
        for (Label l : labels) {
            Vector3f v = pose.last().pose().transformPosition(new Vector3f(l.x, l.y, l.z));
            spots.add(new float[] {v.x, v.y});
        }
        pose.popPose();
        // The model is drawn in front of the page; later page parts and tooltips must draw over it, not behind it.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        if (gutter == 0) {
            for (int i = 0; i < labels.size(); i++) {
                float[] p = spots.get(i);
                int px = Math.round(p[0]), py = Math.round(p[1]), tw = font.width(labels.get(i).text);
                int lx = Math.min(px + 5, x + w - tw - 3), ly = py - 4;
                g.fill(px - 1, py - 1, px + 2, py + 2, INK);
                g.fill(lx - 2, ly - 2, lx + tw + 2, ly + font.lineHeight, PAPER);
                g.drawString(font, labels.get(i).text, lx, ly, INK, false);
            }
        } else {
            sideLabels(g, font, spots, x, y, w, h, gutter, x + w / 2f + panX);
        }
        if (!changes.isEmpty()) legend(g, font, x + 3, y + 3);
        g.disableScissor();
    }

    /**
     * Green boxes round the blocks the book adds or replaces on a build copied from the world, red ones round the
     * blocks to take out, a little outside the block so the box isn't hidden in its faces.
     */
    private void outlineChanges(PoseStack pose, MultiBufferSource.BufferSource buffers) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Change c : changes) {
            int color = c.removed ? REMOVED : ADDED;
            float r = (color >> 16 & 255) / 255f, gr = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
            for (int bx = c.x; bx <= c.x2; bx++)
                net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, lines, bx - 0.03, c.y - 0.03, c.z - 0.03,
                        bx + 1.03, c.y2 + 1.03, c.z2 + 1.03, r, gr, b, 1f);
        }
        buffers.endBatch(RenderType.lines());
    }

    /** What the outlines mean, in the box's corner. */
    private void legend(GuiGraphics g, Font font, int x, int y) {
        boolean anyRemoved = changes.stream().anyMatch(Change::removed), anyAdded = changes.stream().anyMatch(c -> !c.removed);
        int at = x;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        if (anyAdded) at = legendItem(g, font, at, y, ADDED, "new or changed");
        if (anyRemoved) legendItem(g, font, at, y, REMOVED, "take out");
        g.pose().popPose();
    }

    private static int legendItem(GuiGraphics g, Font font, int x, int y, int color, String text) {
        int w = font.width(text) + 14;
        g.fill(x, y, x + w, y + 11, PAPER);
        g.renderOutline(x + 2, y + 2, 7, 7, color);
        g.drawString(font, text, x + 12, y + 2, INK, false);
        return x + w + 3;
    }

    private int labelWidth(Font font) {
        int most = 0;
        for (Label l : labels) most = Math.max(most, font.width(l.text));
        return most;
    }

    /**
     * Labels in a column on the side their spot is on, top to bottom in the order of their spots, never overlapping,
     * each joined to its spot by a thin line.
     */
    private void sideLabels(GuiGraphics g, Font font, List<float[]> spots, int x, int y, int w, int h, int gutter, float middle) {
        record Placed(int index, List<FormattedCharSequence> lines, float spotY) {
        }
        List<Placed> left = new ArrayList<>(), right = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            List<FormattedCharSequence> lines = font.split(Component.literal(labels.get(i).text), gutter - 6);
            (spots.get(i)[0] < middle ? left : right).add(new Placed(i, lines, spots.get(i)[1]));
        }
        for (List<Placed> side : List.of(left, right)) {
            boolean onLeft = side == left;
            side.sort((a, b) -> Float.compare(a.spotY, b.spotY));
            int total = 0;
            for (Placed p : side) total += p.lines.size() * font.lineHeight + 4;
            int top = y + 3, room = h - 6;
            int[] tops = new int[side.size()];
            int next = top;
            for (int i = 0; i < side.size(); i++) {
                int lh = side.get(i).lines.size() * font.lineHeight + 4;
                // As near its spot as the labels above it and the room left below allow.
                int want = Math.round(side.get(i).spotY) - lh / 2;
                int below = 0;
                for (int j = i + 1; j < side.size(); j++) below += side.get(j).lines.size() * font.lineHeight + 4;
                int maxTop = total > room ? next : top + room - below - lh;
                tops[i] = Math.max(next, Math.min(maxTop, want));
                next = tops[i] + lh;
            }
            for (int i = 0; i < side.size(); i++) {
                Placed p = side.get(i);
                float[] spot = spots.get(p.index);
                int tw = 0;
                for (FormattedCharSequence line : p.lines) tw = Math.max(tw, font.width(line));
                int lx = onLeft ? x + 3 : x + w - tw - 3, ly = tops[i] + 2;
                int ax = onLeft ? lx + tw + 2 : lx - 2, ay = ly + font.lineHeight / 2;
                line(g, ax, ay, Math.round(spot[0]), Math.round(spot[1]), FADED);
                g.fill(Math.round(spot[0]) - 1, Math.round(spot[1]) - 1, Math.round(spot[0]) + 2, Math.round(spot[1]) + 2, INK);
                g.fill(lx - 2, ly - 2, lx + tw + 2, ly + p.lines.size() * font.lineHeight, PAPER);
                for (int k = 0; k < p.lines.size(); k++) g.drawString(font, p.lines.get(k), lx, ly + k * font.lineHeight, INK, false);
            }
        }
    }

    /** A one-pixel line. */
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sxs = x0 < x1 ? 1 : -1, sys = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int n = 0; n < 2000; n++) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sxs;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sys;
            }
        }
    }

    /** True when every side touches a solid full block, so nothing of this block can be seen. */
    private boolean hidden(int x, int y, int z) {
        for (Direction d : SIDES) if (!occludes(at(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()), cells[index(x, y, z)], d)) return false;
        return true;
    }

    private static boolean occludes(BlockState neighbour, BlockState self, Direction towards) {
        if (neighbour == null) return false;
        return neighbour.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || self.skipRendering(neighbour, towards);
    }

    /**
     * A block with moving parts, drawn the way the world draws it: its model as a placed block shows it (Create's casings
     * and frames) and its block entity renderer for the rest. False when the renderer fails, so it is drawn plainly from
     * then on.
     */
    private boolean drawMover(Minecraft mc, BlockEntity mover, BlockState state, int x, int y, int z, PoseStack pose,
                              MultiBufferSource buffers, RandomSource random) {
        BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        if (hasWorldQuads.computeIfAbsent(state, s -> anyQuads(model, s, WORLD))) modelQuads(mc, model, state, x, y, z, pose, buffers, random, WORLD);
        try {
            var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(mover);
            if (renderer == null) return false;
            renderer.render(mover, 0, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private void makeMovers(Minecraft mc) {
        if (mc.level == null) return;
        try {
            Movers.Made made = Movers.make(mc, cells, sx, sy, sz);
            world = made.world();
            movers = made.movers();
        } catch (RuntimeException | LinkageError e) {
            world = null;
            movers = null;
        }
    }

    /**
     * Water or lava drawn the way the world draws it, flowing towards lower levels, with its animated texture. The
     * fluid renderer writes positions within a 16-block section, so the pose moves to that section's corner. False when it can't.
     */
    private boolean fluid(Minecraft mc, BlockState state, int x, int y, int z, PoseStack pose, MultiBufferSource buffers) {
        if (world == null || fluidFailed) return false;
        try {
            // The fluid renderer writes positions within the block's 16-block section; move to that section's corner.
            pose.pushPose();
            pose.translate(x & ~15, y & ~15, z & ~15);
            VertexConsumer out = new PoseConsumer(buffers.getBuffer(net.minecraft.client.renderer.Sheets.translucentCullBlockSheet()), pose.last());
            try {
                mc.getBlockRenderer().renderLiquid(new BlockPos(x, y, z), world, out, state, state.getFluidState());
            } finally {
                pose.popPose();
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            fluidFailed = true;
            return false;
        }
    }

    private boolean fluidFailed;

    /** Passes the fluid renderer's vertices through the model's transform, lit fully, in the entity vertex format. */
    private record PoseConsumer(VertexConsumer out, PoseStack.Pose pose) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(pose, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setOverlay(OverlayTexture.NO_OVERLAY);
            out.setLight(LightTexture.FULL_BRIGHT);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(pose, x, y, z);
            return this;
        }
    }

    /** Kept apart so Create's classes are only touched when it is there. */
    private static final class Movers {
        record Made(net.minecraft.world.level.BlockAndTintGetter world, BlockEntity[] movers) {
        }

        static Made make(Minecraft mc, BlockState[] cells, int sx, int sy, int sz) {
            // Not handed to Flywheel: Create's renderers then draw the moving parts themselves, as they do with Flywheel off.
            var world = new com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld(mc.level, 0, sy, net.minecraft.core.Vec3i.ZERO, () -> {
            }) {
                @Override
                public boolean supportsVisualization() {
                    return false;
                }
            };
            BlockEntity[] out = new BlockEntity[cells.length];
            boolean any = false;
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    for (int x = 0; x < sx; x++) {
                        BlockState s = cells[(y * sz + z) * sx + x];
                        if (s != null) world.setBlock(new BlockPos(x, y, z), s, 0);
                    }
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    for (int x = 0; x < sx; x++) {
                        int i = (y * sz + z) * sx + x;
                        BlockState s = cells[i];
                        // Entity-drawn blocks (chests and the like) already draw through their item renderer.
                        if (s == null || s.getRenderShape() != RenderShape.MODEL || !(s.getBlock() instanceof EntityBlock eb)) continue;
                        try {
                            BlockEntity be = eb.newBlockEntity(new BlockPos(x, y, z), s);
                            if (be == null || mc.getBlockEntityRenderDispatcher().getRenderer(be) == null) continue;
                            be.setLevel(world);
                            world.setBlockEntity(be);
                            out[i] = be;
                            any = true;
                        } catch (RuntimeException | LinkageError e) {
                            // drawn plainly
                        }
                    }
            return new Made(world, any ? out : null);
        }
    }

    private void block(Minecraft mc, BlockRenderDispatcher blocks, BlockState state, int x, int y, int z, PoseStack pose,
                       MultiBufferSource buffers, RandomSource random) {
        BakedModel model = blocks.getBlockModel(state);
        if (hasQuads.computeIfAbsent(state, s -> anyQuads(model, s, DATA))) {
            modelQuads(mc, model, state, x, y, z, pose, buffers, random, DATA);
        } else if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
            // Chests, beds and the like are drawn by their item renderer.
            blocks.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, DATA, null);
        } else {
            ItemStack item = new ItemStack(state.getBlock());
            if (item.isEmpty()) return;
            pose.translate(0.5f, 0.5f, 0.5f);
            mc.getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose,
                    buffers, null, 0);
        }
    }

    private void modelQuads(Minecraft mc, BakedModel model, BlockState state, int x, int y, int z, PoseStack pose,
                            MultiBufferSource buffers, RandomSource random, ModelData data) {
        random.setSeed(42);
        for (RenderType type : model.getRenderTypes(state, random, data)) {
            VertexConsumer out = buffers.getBuffer(RenderTypeHelper.getEntityRenderType(type, false));
            for (Direction side : SIDES) {
                if (occludes(at(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()), state, side)) continue;
                quads(mc, model, state, side, type, random, pose, out, data);
            }
            quads(mc, model, state, null, type, random, pose, out, data);
        }
    }

    private static boolean anyQuads(BakedModel model, BlockState state, ModelData data) {
        RandomSource random = RandomSource.create(42);
        for (RenderType type : model.getRenderTypes(state, random, data)) {
            for (Direction side : SIDES) if (!model.getQuads(state, side, random, data, type).isEmpty()) return true;
            if (!model.getQuads(state, null, random, data, type).isEmpty()) return true;
        }
        return false;
    }

    private static void quads(Minecraft mc, BakedModel model, BlockState state, Direction side, RenderType type, RandomSource random,
                              PoseStack pose, VertexConsumer out, ModelData data) {
        random.setSeed(42);
        for (BakedQuad quad : model.getQuads(state, side, random, data, type)) {
            int tint = -1;
            if (quad.isTinted()) {
                try {
                    tint = mc.getBlockColors().getColor(state, null, null, quad.getTintIndex());
                } catch (RuntimeException e) {
                    tint = -1;
                }
            }
            float r = (tint >> 16 & 255) / 255f, gr = (tint >> 8 & 255) / 255f, b = (tint & 255) / 255f;
            out.putBulkData(pose.last(), quad, r, gr, b, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
