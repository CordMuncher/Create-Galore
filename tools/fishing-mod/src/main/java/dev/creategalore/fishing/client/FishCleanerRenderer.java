package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.process.FishCleanerBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Fish Cleaner: the shaft through the casing as Create draws it (a Flywheel visual when Flywheel is on, this
 * renderer otherwise), Create's saw blade standing up through the slot in the top (spinning while the shaft turns), and
 * the big fish being cut lying along the blade, sliding forward as the cuts go.
 */
public class FishCleanerRenderer extends KineticBlockEntityRenderer<FishCleanerBlockEntity> {
    /** Longest fish drawn on the cleaner, blocks. */
    private static final float LONGEST = 1.4f;
    /** The top of the casing, blocks. */
    private static final float TOP = 13 / 16f;

    public FishCleanerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** The shaft's Flywheel visual; blade and fish are still drawn by this renderer. Call once the type exists. */
    static void registerVisual() {
        SimpleBlockEntityVisualizer.builder(FishingRegistry.FISH_CLEANER_ENTITY.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .neverSkipVanillaRender()
                .apply();
    }

    @Override
    protected void renderSafe(FishCleanerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.renderSafe(be, partialTick, pose, buffers, light, overlay);
        Direction front = be.front();
        blade(be, front, pose, buffers, light);
        ItemStack fish = be.input();
        if (be.needed() > 0 && !fish.isEmpty()) fish(be, fish, front, pose, buffers, light);
    }

    /**
     * Create's vertical saw blade is a flat strip lying at half height and sticking out of one side; stood up across
     * the shaft and lowered, it pokes four pixels out of the slot in the top.
     */
    private static void blade(FishCleanerBlockEntity be, Direction front, PoseStack pose, MultiBufferSource buffers, int light) {
        float speed = be.getSpeed();
        PartialModel model = speed > 0 ? AllPartialModels.SAW_BLADE_VERTICAL_ACTIVE
                : speed < 0 ? AllPartialModels.SAW_BLADE_VERTICAL_REVERSED : AllPartialModels.SAW_BLADE_VERTICAL_INACTIVE;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        pose.translate(0, -2 / 16f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.translate(-0.5f, -0.5f, -0.5f);
        CachedBuffers.partial(model, be.getBlockState()).light(light).renderInto(pose, buffers.getBuffer(RenderType.cutoutMipped()));
        pose.popPose();
    }

    private static void fish(FishCleanerBlockEntity be, ItemStack fish, Direction front, PoseStack pose, MultiBufferSource buffers, int light) {
        double cm = FishItemRenderer.lengthCm(fish);
        float length = Mth.clamp((float) cm / 100, 0.15f, LONGEST);
        double kg = FishItemRenderer.drawnKg(fish, cm, length * 100);
        float[] box = FishView.extent(fish);
        float lift = 0.6f * box[2] * length / Math.max(0.05f, box[0]) / 2;
        // From a little behind the blade to a little past it as the strokes go.
        float slide = (be.strokes() / (float) Math.max(1, be.needed()) - 0.5f) * 0.4f;
        pose.pushPose();
        pose.translate(0.5f + front.getStepX() * slide, TOP + 1 / 16f + lift, 0.5f + front.getStepZ() * slide);
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot() + 90));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        FishView.draw(fish, length * 100, kg, pose, buffers, light, 0);
        pose.popPose();
    }

    @Override
    protected BlockState getRenderedBlockState(FishCleanerBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}
