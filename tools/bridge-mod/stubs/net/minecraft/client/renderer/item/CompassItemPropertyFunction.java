package net.minecraft.client.renderer.item;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Compile-only stub of the client class (vanilla's lodestone compass needle). */
public class CompassItemPropertyFunction implements ClampedItemPropertyFunction {
    public CompassItemPropertyFunction(CompassTarget target) {
    }

    @Override
    public float unclampedCall(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        return 0;
    }

    public interface CompassTarget {
        GlobalPos getPos(ClientLevel level, ItemStack stack, Entity entity);
    }
}
