package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.KnifeUnify;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every ItemStack (loaded from a save, sent over the network, or made in code) ends in this constructor, so this is
 * where a Berries and Cherries Iron Knife becomes the Farmer's Delight one. Damage, enchantments and names carry over;
 * the knife's own defaults (durability, attack) come from the Farmer's Delight knife. See {@link KnifeUnify}.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackKnifeMixin {
    @Shadow
    @Final
    @Mutable
    private Item item;

    @Shadow
    @Final
    @Mutable
    PatchedDataComponentMap components;

    @Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("TAIL"))
    private void createGalore$unifyKnife(ItemLike itemLike, int count, PatchedDataComponentMap map, CallbackInfo ci) {
        Item removed = KnifeUnify.removed;
        if (removed == null || item != removed) return;
        item = KnifeUnify.canonical;
        components = PatchedDataComponentMap.fromPatch(item.components(), components.asPatch());
    }
}
