package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.ShipAnimatedSprites;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With Sodium in the pack, vanilla's section compiler only builds Sable's ships and other sub-levels. Note which
 * animated textures each of their sections uses; see {@link ShipAnimatedSprites}.
 */
@Mixin(SectionCompiler.class)
public abstract class ShipSectionCompileMixin {
    // NeoForge's overload with the extra section renderers; the short one only hands over to it.
    private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;"
            + "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)"
            + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Inject(method = COMPILE, at = @At("HEAD"))
    private void create_galore_bridge$startSprites(SectionPos pos, RenderChunkRegion region, VertexSorting sorting,
            SectionBufferBuilderPack buffers, List<?> additionalRenderers, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ShipAnimatedSprites.startCompile();
    }

    @Inject(method = COMPILE, at = @At("RETURN"))
    private void create_galore_bridge$keepSprites(SectionPos pos, RenderChunkRegion region, VertexSorting sorting,
            SectionBufferBuilderPack buffers, List<?> additionalRenderers, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ShipAnimatedSprites.endCompile(pos);
    }
}
