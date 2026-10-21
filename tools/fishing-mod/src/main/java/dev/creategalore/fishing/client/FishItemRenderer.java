package dev.creategalore.fishing.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.sim.SizeClass;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.fishing.SizeData;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/**
 * Draws a fish item as its own mob's 3D model ({@link FishView}): held, dropped, in an item frame, on a head, and in
 * inventories as a baked icon ({@link FishIcons}). Its size follows the catch: the exact length of a big fish or a
 * trophy, otherwise the usual length of its size class, so a young fish is smaller than a large one. Whatever it
 * can't draw (no world yet, a mob that won't render, a dropped item far away) is the item's own sprite.
 */
final class FishItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static FishItemRenderer instance;

    /**
     * Placement per view: translation (blocks, from the item's centre), rotation about X, Y, Z (degrees), and the
     * shortest and longest the fish is drawn (blocks), between which its real length is kept. Mutable only through
     * the debug command, for tuning.
     */
    // First person: side on, head inward, low in the hand. Third person: hanging from the hand along the leg.
    // Dropped: on its side. In a frame: across it at the sprites' slant (frames draw their item at half size).
    static final float[] FIRST_PERSON = {0.05f, 0.3f, -0.2f, -10, 160, -15, 0.3f, 0.6f};
    static final float[] THIRD_PERSON = {0, 0.1f, 0.1f, 90, 0, 40, 0.35f, 0.8f};
    static final float[] GROUND = {0, -0.05f, 0, 90, 0, 0, 0.3f, 1.2f};
    static final float[] FIXED = {0, 0, 0.03f, 0, 0, 30, 1.4f, 1.4f};
    static final float[] HEAD = {0, 0.35f, 0, 0, 90, 0, 0.4f, 0.8f};

    /** Dropped fish farther than this (blocks) are drawn as sprites. */
    static final double FULL_DETAIL = 16;

    private FishItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    static FishItemRenderer get() {
        if (instance == null) instance = new FishItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        if (!drawFish(stack, context, pose, buffers, light)) sprite(stack, context, pose, buffers, light, overlay);
    }

    private static boolean drawFish(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                                    int light) {
        if (Minecraft.getInstance().level == null || FishView.mob(stack).isEmpty()) return false;
        if (context == ItemDisplayContext.GUI) return icon(stack, pose, buffers);
        float[] place = placement(context);
        if (place == null) return false;
        if (context == ItemDisplayContext.GROUND && distance(pose) > FULL_DETAIL) return false;
        double cm = lengthCm(stack);
        float length = Mth.clamp((float) cm / 100, place[6], place[7]);
        pose.pushPose();
        pose.translate(0.5f + place[0], 0.5f + place[1], 0.5f + place[2]);
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (left) pose.translate(-2 * place[0], 0, 0);
        pose.mulPose(Axis.XP.rotationDegrees(place[3]));
        pose.mulPose(Axis.YP.rotationDegrees(left ? -place[4] : place[4]));
        pose.mulPose(Axis.ZP.rotationDegrees(left ? -place[5] : place[5]));
        // Out of the water the fish lies still; the swimming animation is for the line and the tank.
        FishView.draw(stack, length * 100, drawnKg(stack, cm, length * 100), pose, buffers, light, 0);
        pose.popPose();
        return true;
    }

    private static float[] placement(ItemDisplayContext context) {
        return switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> FIRST_PERSON;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> THIRD_PERSON;
            case GROUND -> GROUND;
            case FIXED -> FIXED;
            case HEAD -> HEAD;
            default -> null;
        };
    }

    /** How far the item is from the camera: world items are drawn camera-relative, so this is the pose's offset. */
    private static double distance(PoseStack pose) {
        return pose.last().pose().transformPosition(new Vector3f(0.5f, 0.5f, 0.5f)).length();
    }

    /** The baked icon, filling the slot; false (sprite for now) while it's still waiting to be baked. */
    private static boolean icon(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
        ResourceLocation texture = FishIcons.icon(stack);
        if (texture == null) return false;
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose p = pose.last();
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        // In front of the sprite's own depth (its front face is at 8.5/16): screens that draw the sprite as a shadow
        // first, like the Fishing Journal, would otherwise hide the icon behind it.
        float z = 1.0f;
        v.addVertex(p, 0, 0, z).setColor(-1).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, 1);
        v.addVertex(p, 1, 0, z).setColor(-1).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, 1);
        v.addVertex(p, 1, 1, z).setColor(-1).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, 1);
        v.addVertex(p, 0, 1, z).setColor(-1).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(p, 0, 0, 1);
        return true;
    }

    /**
     * The item's own sprite, as it would have been drawn: the item renderer has already moved to the corner of the
     * item's box for us (and skipped the sprite's display transform), so undo that and draw the original model.
     */
    static void sprite(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        BakedModel sprite = FishItems.sprite(stack.getItem());
        if (sprite == null) return;
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        Minecraft.getInstance().getItemRenderer().render(stack, context, left, pose, buffers, light, overlay, sprite);
        pose.popPose();
    }

    /** The fish's length: exact for a big fish or trophy, else the usual length of its size class. */
    static double lengthCm(ItemStack stack) {
        CatchInfo info = stack.get(FishingRegistry.CATCH.get());
        if (info != null && info.cm().isPresent()) return info.cm().get();
        Double tide = stack.get(TideDataComponents.FISH_LENGTH);
        if (tide != null && tide > 0) return tide;
        SizeData size = FishData.get(stack).flatMap(FishData::size).orElse(new SizeData());
        double low = size.typicalLowCm(), high = size.typicalHighCm(), record = size.recordHighCm();
        SizeClass klass = info != null ? info.size() : SizeClass.COMMON;
        return switch (klass) {
            case YOUNG -> low * 0.8;
            case COMMON -> (low + high) / 2;
            case LARGE -> high + (record - high) * 0.25;
            case TROPHY -> high + (record - high) * 0.6;
            case UNIQUE -> record;
        };
    }

    /**
     * The weight to draw a fish with when it is drawn {@code drawnCm} long instead of its real {@code realCm}: a
     * shrunk fish keeps its build (weight goes with the cube of length) rather than getting a whole tuna's girth at
     * a third of its length. 0 when its weight isn't known.
     */
    static double drawnKg(ItemStack stack, double realCm, double drawnCm) {
        CatchInfo info = stack.get(FishingRegistry.CATCH.get());
        double kg = info != null ? info.kg().orElse(0.0) : 0;
        return realCm <= 0 ? kg : kg * Math.pow(drawnCm / realCm, 3);
    }

    /** A gold star on trophies and a purple one on uniques, in the slot's top-left corner. */
    static boolean decorate(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        CatchInfo info = stack.get(FishingRegistry.CATCH.get());
        if (info == null || !info.size().isTrophy()) return false;
        int color = info.size() == SizeClass.UNIQUE ? 0xffc77dff : 0xffffc83d;
        int shade = info.size() == SizeClass.UNIQUE ? 0xff6a2c8f : 0xff8a5a00;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        // A 5x5 pixel star with a dark rim, in Minecraft's own icon style.
        int[][] star = {{2, 0}, {1, 1}, {2, 1}, {3, 1}, {0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {1, 3}, {3, 3}, {0, 4}, {4, 4}};
        for (int[] p : star) g.fill(x + p[0], y + p[1] + 1, x + p[0] + 1, y + p[1] + 2, shade);
        for (int[] p : star) g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, color);
        g.pose().popPose();
        return false;
    }

    /**
     * A fish item's model: its sprite in every view, except that the views drawn as the fish go through this
     * renderer (the item renderer then calls {@link #renderByItem}).
     */
    static final class Model extends BakedModelWrapper<BakedModel> {
        private final BakedModel custom;

        Model(BakedModel sprite) {
            super(sprite);
            custom = new BakedModelWrapper<>(sprite) {
                @Override
                public boolean isCustomRenderer() {
                    return true;
                }

                @Override
                public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
                    return List.of();
                }

                @Override
                public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data,
                                                RenderType renderType) {
                    return List.of();
                }

                @Override
                public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
                    return this;
                }
            };
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            return context == ItemDisplayContext.NONE ? originalModel.applyTransform(context, pose, leftHand) : custom;
        }
    }
}
