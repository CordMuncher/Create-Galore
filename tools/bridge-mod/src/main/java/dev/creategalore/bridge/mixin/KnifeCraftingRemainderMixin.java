package dev.creategalore.bridge.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A knife in a crafting grid (pitting cherries, Ratatouille's chef hat) is a tool, not an ingredient: it comes back
 * one point more worn instead of being used up. The Berries and Cherries knife it replaces already worked this way.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.item.KnifeItem")
public abstract class KnifeCraftingRemainderMixin {
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack worn = stack.copyWithCount(1);
        worn.setDamageValue(worn.getDamageValue() + 1);
        return worn.getDamageValue() >= worn.getMaxDamage() ? ItemStack.EMPTY : worn;
    }
}
