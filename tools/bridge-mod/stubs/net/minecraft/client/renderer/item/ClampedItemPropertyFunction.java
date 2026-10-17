package net.minecraft.client.renderer.item;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Compile-only stub of the client interface. */
public interface ClampedItemPropertyFunction {
    float unclampedCall(ItemStack stack, ClientLevel level, LivingEntity entity, int seed);
}
