package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.NameTagDefer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends name tags to {@link NameTagDefer} while an Iris shader pack is on; see there. */
@Mixin(targets = "net.minecraft.client.renderer.entity.EntityRenderer")
public abstract class NameTagDeferMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$skipInShadowPass(CallbackInfo ci) {
        if (NameTagDefer.inShadowPass()) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource create_galore_bridge$deferNameTag(MultiBufferSource buffers) {
        return NameTagDefer.redirect(buffers);
    }
}
