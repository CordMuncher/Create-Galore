package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.dlss.ColorwheelMotion;
import dev.djefrey.colorwheel.engine.embed.EmbeddedEnvironment;
import dev.djefrey.colorwheel.engine.embed.EnvironmentStorage;
import dev.engine_room.flywheel.backend.engine.CpuArena;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DLAA: Colorwheel flushed its matrices for this frame; last frame's go beside them (see {@link ColorwheelMotion}). */
@Mixin(value = EnvironmentStorage.class, remap = false)
public abstract class ColorwheelStorageMotionMixin {
    @Shadow
    @Final
    protected ReferenceSet<EmbeddedEnvironment> environments;

    @Shadow
    @Final
    public CpuArena arena;

    @Inject(method = "flush", at = @At("TAIL"))
    private void create_galore_bridge$previousMatrices(CallbackInfo ci) {
        ColorwheelMotion.writePrevious(environments, arena.capacity());
    }
}
