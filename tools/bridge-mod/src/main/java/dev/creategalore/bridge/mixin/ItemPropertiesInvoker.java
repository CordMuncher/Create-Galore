package dev.creategalore.bridge.mixin;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's per-item model property registration is private; the boss compass needle needs it. */
@Mixin(value = ItemProperties.class, remap = false)
public interface ItemPropertiesInvoker {
    @Invoker("register")
    static void create_galore_bridge$register(Item item, ResourceLocation name, ClampedItemPropertyFunction property) {
        throw new AssertionError();
    }
}
