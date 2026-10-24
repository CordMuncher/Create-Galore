package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.creategalore.bridge.dlss.ColorwheelMotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** DLAA: Colorwheel's whole shader source, just before it compiles; see {@link ColorwheelMotion}. */
@Pseudo
@Mixin(targets = "dev.djefrey.colorwheel.compile.core.ClrwlCompilation", remap = false)
public abstract class ColorwheelSourceMotionMixin {
    @ModifyExpressionValue(method = "compile", at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;", ordinal = 0))
    private String create_galore_bridge$motionSource(String source) {
        return ColorwheelMotion.patchVertexSource(source);
    }
}
