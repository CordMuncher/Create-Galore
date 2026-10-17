package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.maxenonyme.highseas.BoatManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Deep Seas 3.4.0 (Create High Seas) ticks its boats once per dimension on a dedicated server, but every dimension
 * shares one table and each pass drops the boats it did not see in its own dimension. Any other dimension with ships
 * in it (the Northstar orbits) wiped the Overworld's boats every tick, before their async hold scan could land, so
 * no hull ever got buoyancy and every boat sank. Singleplayer is unaffected (it runs the client-side pass).
 * Give each dimension its own table.
 */
@Mixin(value = BoatManager.class, remap = false)
public abstract class HighSeasBoatLevelsMixin {
    @Unique
    private static final Map<ResourceKey<Level>, Map<UUID, Object>> create_galore_bridge$boatsByLevel = new HashMap<>();

    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "FIELD", target = "Lcom/maxenonyme/highseas/BoatManager;SERVER:Ljava/util/Map;", opcode = Opcodes.GETSTATIC))
    private static Map<UUID, Object> create_galore_bridge$boatsOfThisLevel(Map<UUID, Object> shared, @Local(argsOnly = true) Level level) {
        return create_galore_bridge$boatsByLevel.computeIfAbsent(level.dimension(), k -> new HashMap<>());
    }

    // clearSide(false) walks the table to release registered boats, then clears it: hand it every dimension's boats.
    @ModifyExpressionValue(
        method = "clearSide",
        at = @At(value = "FIELD", target = "Lcom/maxenonyme/highseas/BoatManager;SERVER:Ljava/util/Map;", opcode = Opcodes.GETSTATIC))
    private static Map<UUID, Object> create_galore_bridge$allLevels(Map<UUID, Object> shared) {
        Map<UUID, Object> all = new HashMap<>(shared);
        create_galore_bridge$boatsByLevel.values().forEach(all::putAll);
        create_galore_bridge$boatsByLevel.clear();
        return all;
    }
}
