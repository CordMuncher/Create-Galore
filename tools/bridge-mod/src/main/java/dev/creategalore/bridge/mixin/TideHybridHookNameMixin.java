package dev.creategalore.bridge.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tide gives a Hybrid Aquatic hook on its rod a bonus by comparing the item's name with "barbed_hook", "glowing_hook"
 * and "magnetic_hook", but on 1.21.1 an item's name includes its mod ("hybrid_aquatic:barbed_hook"), so the bonus
 * never applied. Hand Tide the bare name for Hybrid Aquatic items.
 */
@Mixin(targets = "com.li64.tide.registries.items.TideFishingRodItem", remap = false)
public abstract class TideHybridHookNameMixin {
    @Redirect(method = "castHook", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;toString()Ljava/lang/String;"))
    private String create_galore_bridge$bareHookName(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return "hybrid_aquatic".equals(id.getNamespace()) ? id.getPath() : item.toString();
    }
}
