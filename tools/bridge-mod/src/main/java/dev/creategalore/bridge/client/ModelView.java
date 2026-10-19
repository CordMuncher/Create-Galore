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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The book's 3D models: real blocks drawn as a small hologram on the page, which the player turns by dragging and
 * zooms with the wheel. Each block is drawn from its own model (Create's shafts and cogs included, turned by their
 * axis), faces hidden behind a neighbour are skipped, chests and other entity-drawn blocks are drawn as their item,
 * and fluids stand in as stained glass.
 */
final class ModelView {
    private static final int CANVAS = 0xFFE6DABA, FRAME = 0xFF8A7250, INK = 0xFF2B2014, FADED = 0xFF6E5E48, PAPER = 0xEEF9EED2;
    private static final Direction[] SIDES = Direction.values();
    /** The angle a model is first seen from: from the front, a little to the right and above. */
    static final float YAW = -35, PITCH = 28;

    private record Label(String text, float x, float y, float z) {
    }

    final String title, caption;
    private final int sx, sy, sz;
    private final BlockState[] cells;
    private final List<Label> labels = new ArrayList<>();
    private final Map<BlockState, Boolean> hasQuads = new HashMap<>();
    /**
     * Create draws its shafts, cogs and other turning parts from their block entities, and its models hand out their
     * shapes only when asked for a "virtual" block (the way Ponder scenes draw them); ask the same way.
     */
    private static final ModelData DATA = virtualData();

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
    float yaw = YAW, pitch = PITCH, zoom = 1;

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
        for (JsonElement e : o.getAsJsonArray("blocks")) {
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
            BlockState s = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
            // Fluids have no block model; show them as glass of their colour.
            if (!s.getFluidState().isEmpty() && s.getRenderShape() == RenderShape.INVISIBLE) {
                if (s.getFluidState().is(FluidTags.LAVA)) return Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
                if (s.getFluidState().is(FluidTags.WATER)) return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
                return Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            }
            return s;
        } catch (Exception e) {
            return null;
        }
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
        draw(g, font, bx, by, bw, bh, yaw, pitch, zoom);
        if (hint != null) {
            int hw = font.width(hint) + 6;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(bx + bw - hw - 2, by + bh - 12, bx + bw - 2, by + bh - 2, PAPER);
            g.drawString(font, hint, bx + bw - hw + 1, by + bh - 11, FADED, false);
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

    void turn(double dx, double dy) {
        yaw += (float) dx * 0.9f;
        pitch = Math.max(-89, Math.min(89, pitch + (float) dy * 0.9f));
    }

    void zoom(double steps) {
        zoom = Math.max(0.5f, Math.min(4f, zoom * (float) Math.pow(1.15, steps)));
    }

    /** Draws the model filling the box (x, y, w, h) at this angle; labels on top. */
    void draw(GuiGraphics g, Font font, int x, int y, int w, int h, float yaw, float pitch, float zoom) {
        Minecraft mc = Minecraft.getInstance();
        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        PoseStack pose = g.pose();
        pose.pushPose();
        float radius = 0.5f * (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float scale = Math.min(w, h) / (2 * radius) * 0.95f * zoom;
        pose.translate(x + w / 2f, y + h / 2f, 50 + radius * scale);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-sx / 2f, -sy / 2f, -sz / 2f);
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();
        for (int by = 0; by < sy; by++)
            for (int bz = 0; bz < sz; bz++)
                for (int bx = 0; bx < sx; bx++) {
                    BlockState state = cells[index(bx, by, bz)];
                    if (state == null || hidden(bx, by, bz)) continue;
                    pose.pushPose();
                    pose.translate(bx, by, bz);
                    try {
                        block(mc, blocks, state, bx, by, bz, pose, buffers, random);
                    } catch (RuntimeException ignored) {
                        // a block whose model can't be drawn out of the world is left out
                    }
                    pose.popPose();
                }
        g.flush();
        // Where each label's cell lands on screen, while the model's transform is still in place.
        List<float[]> spots = new ArrayList<>();
        for (Label l : labels) {
            Vector3f v = pose.last().pose().transformPosition(new Vector3f(l.x, l.y, l.z));
            spots.add(new float[] {v.x, v.y});
        }
        pose.popPose();
        // The model is drawn in front of the page; later page parts and tooltips must draw over it, not behind it.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        for (int i = 0; i < labels.size(); i++) {
            float[] p = spots.get(i);
            int px = Math.round(p[0]), py = Math.round(p[1]), tw = font.width(labels.get(i).text);
            int lx = Math.min(px + 5, x + w - tw - 3), ly = py - 4;
            g.fill(px - 1, py - 1, px + 2, py + 2, INK);
            g.fill(lx - 2, ly - 2, lx + tw + 2, ly + font.lineHeight, PAPER);
            g.drawString(font, labels.get(i).text, lx, ly, INK, false);
        }
        g.disableScissor();
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

    private void block(Minecraft mc, BlockRenderDispatcher blocks, BlockState state, int x, int y, int z, PoseStack pose,
                       MultiBufferSource buffers, RandomSource random) {
        BakedModel model = blocks.getBlockModel(state);
        if (hasQuads.computeIfAbsent(state, s -> anyQuads(model, s))) {
            random.setSeed(42);
            for (RenderType type : model.getRenderTypes(state, random, DATA)) {
                VertexConsumer out = buffers.getBuffer(RenderTypeHelper.getEntityRenderType(type, false));
                for (Direction side : SIDES) {
                    if (occludes(at(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()), state, side)) continue;
                    quads(mc, model, state, side, type, random, pose, out);
                }
                quads(mc, model, state, null, type, random, pose, out);
            }
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

    private static boolean anyQuads(BakedModel model, BlockState state) {
        RandomSource random = RandomSource.create(42);
        for (RenderType type : model.getRenderTypes(state, random, DATA)) {
            for (Direction side : SIDES) if (!model.getQuads(state, side, random, DATA, type).isEmpty()) return true;
            if (!model.getQuads(state, null, random, DATA, type).isEmpty()) return true;
        }
        return false;
    }

    private static void quads(Minecraft mc, BakedModel model, BlockState state, Direction side, RenderType type, RandomSource random,
                              PoseStack pose, VertexConsumer out) {
        random.setSeed(42);
        for (BakedQuad quad : model.getQuads(state, side, random, DATA, type)) {
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
