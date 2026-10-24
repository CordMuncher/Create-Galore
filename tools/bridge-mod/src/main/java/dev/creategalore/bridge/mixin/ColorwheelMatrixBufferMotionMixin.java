package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.dlss.ColorwheelMotion;
import dev.djefrey.colorwheel.indirect.ClrwlMatrixBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: Colorwheel's indirect backend uploads and binds its matrices; last frame's go with them (see {@link ColorwheelMotion}). */
@Mixin(value = ClrwlMatrixBuffer.class, remap = false)
public abstract class ColorwheelMatrixBufferMotionMixin {
    @Inject(method = "flush", at = @At("TAIL"))
    private void create_galore_bridge$uploadPrevious(CallbackInfo ci) {
        ColorwheelMotion.upload();
    }

    @Inject(method = "bind", at = @At("TAIL"))
    private void create_galore_bridge$bindPrevious(CallbackInfo ci) {
        ColorwheelMotion.bind();
    }
}
