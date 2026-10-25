package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.dlss.ColorwheelCulling;
import dev.djefrey.colorwheel.indirect.ClrwlIndirectDrawManager;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** DLAA: Colorwheel binds a culling program and gives it its material filter; the render scale goes with it (see {@link ColorwheelCulling}). */
@Mixin(value = ClrwlIndirectDrawManager.class, remap = false)
public abstract class ColorwheelCullScaleMixin {
    @WrapOperation(method = "dispatchCull", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;setUInt(Ljava/lang/String;I)V"))
    private void create_galore_bridge$cullScale(GlProgram program, String name, int value, Operation<Void> original) {
        original.call(program, name, value);
        Vector2f scale = ColorwheelCulling.scale();
        program.setVec2(ColorwheelCulling.SCALE_UNIFORM, scale.x, scale.y);
    }
}
