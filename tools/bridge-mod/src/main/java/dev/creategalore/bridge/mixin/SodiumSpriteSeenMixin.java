package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.client.ShipAnimatedSprites;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every texture Sodium is told is on screen passes here; see {@link ShipAnimatedSprites}. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.texture.SpriteUtilImpl", remap = false)
public abstract class SodiumSpriteSeenMixin {
    @Inject(method = "markSpriteActive", at = @At("HEAD"))
    private void create_galore_bridge$seen(TextureAtlasSprite sprite, CallbackInfo ci) {
        ShipAnimatedSprites.seen(sprite);
    }
}
