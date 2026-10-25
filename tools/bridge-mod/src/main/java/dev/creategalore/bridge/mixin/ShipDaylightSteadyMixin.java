package dev.creategalore.bridge.mixin;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The light on the item in your hand flickered while you stood on a sailing boat. Sable gives each ship a daylight
 * level (0 to 15) every tick, from the world's sky light at the middle of the ship's bounding box, and scales the sky
 * light of everything drawn on the ship by it: the held item, mobs and players aboard, block entities, particles (and
 * without shaders the ship's blocks too). The middle of a boat's box sits near the waterline, and the top block of
 * water has sky light 14, so as the boat bobbed the level flipped between 14 and 15 several times a second and the hand
 * dimmed and brightened with it (shaders light the hand from that value, and Photon's ambient light goes with its
 * square, so one level shows; the ship's own blocks don't use it under shaders, so only the hand seemed to flicker).
 *
 * Here the level also counts the daylight just above the top of the ship, so a boat on open water is in full daylight
 * (a ship under water, underground or under a roof still reads dark there), and it only moves by one or two levels
 * once the new level has held for a second, so bobbing at any edge no longer flips it. A bigger change (into a cave,
 * a dive) still applies at once.
 */
@Mixin(value = ClientSubLevel.class, remap = false)
public abstract class ShipDaylightSteadyMixin {
    /** Ticks a small change must hold before it's used. */
    @Unique
    private static final int create_galore_bridge$HOLD_TICKS = 20;

    @Unique
    private int create_galore_bridge$steady = -1;
    @Unique
    private int create_galore_bridge$candidate = -1;
    @Unique
    private int create_galore_bridge$candidateTicks;

    @Inject(method = "computeSubLevelSkyLight", at = @At("RETURN"), cancellable = true)
    private void create_galore_bridge$steadyDaylight(Pose3dc pose, CallbackInfoReturnable<Integer> cir) {
        ClientSubLevel self = (ClientSubLevel) (Object) this;
        int level = cir.getReturnValueI();
        BoundingBox3dc box = self.boundingBox();
        if (box.volume() >= 9.0) {
            // Sable's own small-ship case samples around the ship's position already, at its height.
            double x = (box.minX() + box.maxX()) / 2, z = (box.minZ() + box.maxZ()) / 2;
            level = Math.max(level, self.getLevel().getBrightness(LightLayer.SKY, BlockPos.containing(x, box.maxY() + 0.1, z)));
        }
        int steady = create_galore_bridge$steady;
        if (steady < 0 || level == steady || Math.abs(level - steady) > 2) {
            create_galore_bridge$steady = level;
            create_galore_bridge$candidate = -1;
        } else if (level == create_galore_bridge$candidate) {
            if (++create_galore_bridge$candidateTicks >= create_galore_bridge$HOLD_TICKS) {
                create_galore_bridge$steady = level;
                create_galore_bridge$candidate = -1;
            }
        } else {
            create_galore_bridge$candidate = level;
            create_galore_bridge$candidateTicks = 1;
        }
        cir.setReturnValue(create_galore_bridge$steady);
    }
}
