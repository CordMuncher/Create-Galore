package dev.creategalore.bridge.mixin;

import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Logging out on a ship saves a login point: where you stood on the ship, plus a pointer to the ship's last save.
 * A ship assembled since the last world save has no such pointer, so Sable keeps only the world spot you stood on.
 * If the ship has unloaded by the time you log back in (any world save does that once nobody is near it), Sable puts
 * you at that bare spot and lets you go: you are in mid air while the ship has drifted on or loads a moment later, and
 * a player who logged out at y 288 fell to the ground. With the ship's id known, hold the player the way Sable does
 * for a saved ship instead (frozen in place until the ship loads, then put back on the deck where they stood). If the
 * ship never loads, Sable's own 8 s limit lets them go as before.
 */
@Mixin(value = SubLevelTrackingPointSavedData.class, remap = false)
public abstract class SableLoginOnShipMixin {
    @Shadow
    @Final
    private Map<UUID, TrackingPoint> trackingPoints;

    @Unique
    private TrackingPoint create_galore_bridge$taking;

    @Inject(method = "take", at = @At("HEAD"))
    private void create_galore_bridge$remember(UUID uuid, boolean remove,
                                               CallbackInfoReturnable<SubLevelTrackingPointSavedData.TakenLoginPoint> cir) {
        create_galore_bridge$taking = trackingPoints.get(uuid);
    }

    @Inject(method = "take", at = @At("RETURN"), cancellable = true)
    private void create_galore_bridge$holdForShip(UUID uuid, boolean remove,
                                                  CallbackInfoReturnable<SubLevelTrackingPointSavedData.TakenLoginPoint> cir) {
        TrackingPoint point = create_galore_bridge$taking;
        create_galore_bridge$taking = null;
        SubLevelTrackingPointSavedData.TakenLoginPoint taken = cir.getReturnValue();
        // Only Sable's "no pointer, use the placeholder" answer: a ship point that came back without a ship.
        if (taken == null || taken.subLevelId() != null || point == null || !point.inSubLevel()
                || point.subLevelID() == null || point.lastSavedSubLevelPointer() != null) return;
        cir.setReturnValue(new SubLevelTrackingPointSavedData.TakenLoginPoint(
                taken.position(), point.subLevelID(), new Vector3d(point.point())));
    }
}
