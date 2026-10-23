package dev.creategalore.bridge.mixin;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads Deep Seas' tracked ships, their dry hold cells and where they are, for ShipHold. */
@Mixin(value = CompartmentTracker.class, remap = false)
public interface CompartmentTrackerAccessor {
    @Accessor("SUBS")
    static Map<UUID, SubLevelAccess> create_galore_bridge$subs() {
        throw new AssertionError();
    }

    @Accessor("SEALED_UNION")
    static Map<UUID, Set<BlockPos>> create_galore_bridge$sealed() {
        throw new AssertionError();
    }

    @Accessor("WORLD_AABB")
    static Map<UUID, AABB> create_galore_bridge$worldBoxes() {
        throw new AssertionError();
    }

    @Accessor("sealedBounds")
    static AABB create_galore_bridge$sealedBounds() {
        throw new AssertionError();
    }
}
