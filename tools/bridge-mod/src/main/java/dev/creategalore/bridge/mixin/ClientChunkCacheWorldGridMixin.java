package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.grid.WorldGrid;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The block grid (see {@link WorldGrid}) keeps each block's sky light: a section whose sky light the client worked out again is read again. */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheWorldGridMixin {
    @Inject(method = "onLightUpdate", at = @At("HEAD"))
    private void create_galore_bridge$gridLight(LightLayer layer, SectionPos pos, CallbackInfo ci) {
        if (layer == LightLayer.SKY) {
            WorldGrid.sectionChanged(pos.x(), pos.y(), pos.z());
        }
    }
}
