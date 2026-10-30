package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.client.ParticleOrdering;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The shader pack's particle ordering, followed (see {@link ParticleOrdering}). NeoForge draws particles twice in a
 * frame (outside Fabulous graphics, which Iris never uses): the opaque ones right after the solid world, then the
 * see-through ones after the translucent world.
 */
@Mixin(LevelRenderer.class)
public abstract class ParticleOrderingMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V"))
    private void create_galore_bridge$solidParticles(ParticleEngine engine, LightTexture light, Camera camera, float partialTick, Frustum frustum,
            Predicate<ParticleRenderType> types, Operation<Void> original) {
        int asked = ParticleOrdering.asked();
        if (asked == ParticleOrdering.AFTER) return;
        original.call(engine, light, camera, partialTick, frustum, asked == ParticleOrdering.BEFORE ? (Predicate<ParticleRenderType>) type -> true : types);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 2,
        target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V"))
    private void create_galore_bridge$translucentParticles(ParticleEngine engine, LightTexture light, Camera camera, float partialTick, Frustum frustum,
            Predicate<ParticleRenderType> types, Operation<Void> original) {
        int asked = ParticleOrdering.asked();
        if (asked == ParticleOrdering.BEFORE) return;
        original.call(engine, light, camera, partialTick, frustum, asked == ParticleOrdering.AFTER ? (Predicate<ParticleRenderType>) type -> true : types);
    }
}
