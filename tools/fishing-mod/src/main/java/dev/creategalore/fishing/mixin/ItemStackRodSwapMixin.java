package dev.creategalore.fishing.mixin;

import dev.creategalore.fishing.compat.RodSwap;
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
 * Every ItemStack (loaded, received, crafted or made in code) ends in this constructor, so this is where a vanilla or
 * Alloyed rod becomes ours ({@link RodSwap}). The stack keeps its changes (damage, enchantments, name) on top of our
 * rod's own defaults. The Bridge swaps knives the same way.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackRodSwapMixin {
    @Shadow
    @Final
    @Mutable
    private Item item;

    @Shadow
    @Final
    @Mutable
    PatchedDataComponentMap components;

    @Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("TAIL"))
    private void cgfish$swapRod(ItemLike itemLike, int count, PatchedDataComponentMap map, CallbackInfo ci) {
        Item ours = RodSwap.swap(item);
        if (ours == null) return;
        item = ours;
        components = PatchedDataComponentMap.fromPatch(ours.components(), components.asPatch());
    }
}
