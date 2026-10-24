package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.dlss.ColorwheelMotion;
import dev.creategalore.bridge.dlss.ObjectMotion;
import dev.djefrey.colorwheel.compile.ClrwlProgram;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: a Colorwheel program is bound; it gets how far Flywheel's clock moved since last frame (see {@link ColorwheelMotion}). */
@Mixin(value = ClrwlProgram.class, remap = false)
public abstract class ColorwheelProgramMotionMixin {
    @Inject(method = "bind", at = @At("TAIL"))
    private void create_galore_bridge$tickDelta(CallbackInfo ci) {
        ((GlProgram) (Object) this).setFloat(ColorwheelMotion.TICK_DELTA_UNIFORM, ObjectMotion.flywheelTickDelta());
    }
}
