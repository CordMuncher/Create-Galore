package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.machine.ReelDriveBlockEntity;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Basic and Geared Reel Drives: the shaft through the casing as Create draws it (a Flywheel visual when
 * Flywheel is on, this renderer otherwise), and the mounted rod bending toward its line.
 */
public class ReelDriveRenderer extends KineticBlockEntityRenderer<ReelDriveBlockEntity> {
    public ReelDriveRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** The shaft's Flywheel visual; the rod is still drawn by this renderer. Call once the block entity type exists. */
    static void registerVisual() {
        SimpleBlockEntityVisualizer.builder(FishingRegistry.REEL_DRIVE_ENTITY.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .neverSkipVanillaRender()
                .apply();
    }

    @Override
    protected void renderSafe(ReelDriveBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.renderSafe(be, partialTick, pose, buffers, light, overlay);
        MountRenderer.renderRod(be, partialTick, pose, buffers, light);
    }

    @Override
    protected BlockState getRenderedBlockState(ReelDriveBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }

    @Override
    public boolean shouldRenderOffScreen(ReelDriveBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ReelDriveBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3);
    }
}
