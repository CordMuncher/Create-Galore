package dev.creategalore.bridge;

import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * A Wheel Mount prints from a schematic with the Tire it holds. Create's Schematicannon copies only what a block entity
 * marks safe to copy, and an Offroad Wheel Mount marks its suspension but not the Tire in it, so every printed mount came
 * out bare and the player had to find each one's front face and put a Tire in by hand. The mount already charges its
 * Tire as a material (the cannon takes one from its chest for each mount that holds one), so copying the Tire hands out
 * nothing for free. Only a Tire is copied (one of Offroad's four sizes, one of it); anything else a schematic names is
 * left out, and the mount prints bare as before.
 */
public final class WheelMountSchematics {
    private static final ResourceLocation MOUNT = ResourceLocation.fromNamespaceAndPath("offroad", "wheel_mount");
    /** Where a Wheel Mount keeps the item on it. */
    private static final String STACK = "CurrentStack";

    private WheelMountSchematics() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(WheelMountSchematics::install));
    }

    private static void install() {
        BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(MOUNT).ifPresent(type -> SafeNbtWriterRegistry.REGISTRY.register(type,
                (be, tag, registries) -> {
                    // What the mount marks safe itself (its suspension), as before.
                    if (be instanceof PartialSafeNBT safe) safe.writeSafe(tag, registries);
                    CompoundTag full = be.saveWithoutMetadata(registries);
                    CompoundTag tire = tire(full.getCompound(STACK));
                    if (tire != null) tag.put(STACK, tire);
                }));
    }

    /** One of the Tire in {@code stack} (an item as a mount saves it), or null when it holds no Tire. */
    public static CompoundTag tire(CompoundTag stack) {
        if (!stack.contains("id", Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(stack.getString("id"));
        if (!isTire(id)) return null;
        CompoundTag out = new CompoundTag();
        out.putString("id", id.toString());
        out.putInt("count", 1);
        return out;
    }

    /** Whether {@code id} is one of Offroad's Tires (small, standard, large or monstrous). */
    public static boolean isTire(ResourceLocation id) {
        return id != null && id.getNamespace().equals("offroad") && id.getPath().endsWith("tire") && BuiltInRegistries.ITEM.containsKey(id);
    }
}
