package dev.creategalore.fishing.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the display fish (client-only entities that never tick) be told they're in water. */
@Mixin(value = Entity.class, remap = false)
public interface EntityAccessor {
    @Accessor("wasTouchingWater")
    void cgfish$setWasTouchingWater(boolean value);
}
