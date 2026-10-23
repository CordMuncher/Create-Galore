package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.ShipAnimatedSprites;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A ship draws its sections: keep their fire, lava and other animated textures moving; see {@link ShipAnimatedSprites}. */
@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
public abstract class SableSectionSpritesMixin {
    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"))
    private void create_galore_bridge$markSprites(RenderType layer, ShaderInstance shader, Matrix4f modelView,
            double camX, double camY, double camZ, CallbackInfo ci) {
        for (SectionRenderDispatcher.RenderSection section : ((VanillaChunkedSubLevelRenderData) (Object) this).allRenderSections()) {
            ShipAnimatedSprites.markDrawn(SectionPos.asLong(section.getOrigin()));
        }
    }
}
