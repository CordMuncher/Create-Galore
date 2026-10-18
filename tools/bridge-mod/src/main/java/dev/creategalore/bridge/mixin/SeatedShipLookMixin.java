package dev.creategalore.bridge.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player sitting on a Sable ship sends a look direction relative to the ship. Sable turns it into a world direction
 * in its own Entity.calculateViewVector mixin, but that mixin is client-only, so on a dedicated server the look vector
 * stays unrotated: seated on a ship turned 90 degrees, a water bucket aimed at the deck placed nothing (the server
 * traced the ray off to the side). Singleplayer was fine because the integrated server shares the client's patched
 * Entity class, so this only runs on a dedicated server, and only for an entity riding something on a ship, the same
 * case Sable's client mixin covers.
 */
@Mixin(Entity.class)
public abstract class SeatedShipLookMixin {
    @Shadow
    private Level level;

    @Shadow
    public abstract Entity getVehicle();

    @Inject(method = "calculateViewVector", at = @At("RETURN"), cancellable = true)
    private void create_galore_bridge$rotateBySeatShip(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        if (!FMLEnvironment.dist.isDedicatedServer() || this.level == null || this.level.isClientSide()) return;
        Entity vehicle = getVehicle();
        if (vehicle == null) return;
        SubLevel ship = Sable.HELPER.getContaining(vehicle);
        if (ship == null) return;
        Quaterniondc orientation = ship.logicalPose().orientation();
        Vec3 v = cir.getReturnValue();
        Vector3d r = orientation.transform(new Vector3d(v.x, v.y, v.z));
        cir.setReturnValue(new Vec3(r.x, r.y, r.z));
    }
}
