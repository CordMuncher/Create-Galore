package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.dlss.ObjectMotion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: a ship draws its blocks; the pack's terrain program gets how the ship moved since last frame (see {@link ObjectMotion}). */
@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class SableShipMotionMixin {
    @Shadow
    @Final
    private ClientSubLevel subLevel;

    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"))
    private void create_galore_bridge$shipMotion(RenderType layer, ShaderInstance shader, Matrix4f modelView,
            double camX, double camY, double camZ, CallbackInfo ci) {
        Pose3dc pose = subLevel.renderPose();
        ObjectMotion.beginShipDraw(subLevel, shader.getId(), pose.position(), pose.orientation(), pose.rotationPoint(), camX, camY, camZ);
    }

    @Inject(method = "renderChunkedSubLevel", at = @At("RETURN"))
    private void create_galore_bridge$shipMotionDone(RenderType layer, ShaderInstance shader, Matrix4f modelView,
            double camX, double camY, double camZ, CallbackInfo ci) {
        ObjectMotion.endShipDraw(shader.getId());
    }
}
