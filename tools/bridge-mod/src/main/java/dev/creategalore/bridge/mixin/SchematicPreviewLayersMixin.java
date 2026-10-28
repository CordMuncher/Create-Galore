package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import dev.creategalore.bridge.client.SchematicLayers;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Create's placement preview of a held Schematic, on the entity layers while a shader pack is on ({@link SchematicLayers}). */
@Mixin(value = SchematicHandler.class, remap = false)
public abstract class SchematicPreviewLayersMixin {
    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), argsOnly = true)
    private SuperRenderTypeBuffer create_galore_bridge$entityLayers(SuperRenderTypeBuffer buffer) {
        return SchematicLayers.forShaders(buffer);
    }
}
