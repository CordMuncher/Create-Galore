package dev.creategalore.bridge.mixin;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A ship that is broken up, emptied or flies out of the world is only marked removed during a tick; Sable takes it out
 * of its list at the next tick. Anything that touches it in between (Create Deep Seas wakes every ship with sails or
 * a rudder every 5 s, and Sable saves every ship on autosave and on shutdown) hit "Body has been removed", which crashed
 * the server, and the same throw on the way down stopped the world from saving at all. For such a ship there is
 * nothing left to move: nudges, wake-ups and teleports do nothing, and it reads as standing still. Sable's own checks
 * for adding a ship or reading its pose still throw, since those would be real bugs.
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class SableRemovedBodyMixin {
    @Unique
    private static final Logger create_galore_bridge$LOG = LogUtils.getLogger();
    @Unique
    private static boolean create_galore_bridge$told;

    @Unique
    private static boolean create_galore_bridge$gone(PhysicsPipelineBody body, String what) {
        if (body == null || !body.isRemoved()) return false;
        if (!create_galore_bridge$told) {
            create_galore_bridge$told = true;
            create_galore_bridge$LOG.info("Create Galore: skipped {} on a ship Sable is removing (would have crashed the server)", what);
        }
        return true;
    }

    @Inject(method = "wakeUp", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$wakeUp(PhysicsPipelineBody body, CallbackInfo ci) {
        if (create_galore_bridge$gone(body, "wakeUp")) ci.cancel();
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$teleport(PhysicsPipelineBody body, Vector3dc position, Quaterniondc orientation, CallbackInfo ci) {
        if (create_galore_bridge$gone(body, "teleport")) ci.cancel();
    }

    @Inject(method = "applyImpulse", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$impulse(PhysicsPipelineBody body, Vector3dc position, Vector3dc force, CallbackInfo ci) {
        if (create_galore_bridge$gone(body, "applyImpulse")) ci.cancel();
    }

    @Inject(method = "applyLinearAndAngularImpulse", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$impulses(PhysicsPipelineBody body, Vector3dc force, Vector3dc torque, boolean wakeUp, CallbackInfo ci) {
        if (create_galore_bridge$gone(body, "applyLinearAndAngularImpulse")) ci.cancel();
    }

    @Inject(method = "addLinearAndAngularVelocity", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$addVelocity(PhysicsPipelineBody body, Vector3dc linear, Vector3dc angular, CallbackInfo ci) {
        if (create_galore_bridge$gone(body, "addLinearAndAngularVelocity")) ci.cancel();
    }

    @Inject(method = "getLinearVelocity", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$linear(PhysicsPipelineBody body, Vector3d dest, CallbackInfoReturnable<Vector3d> cir) {
        if (create_galore_bridge$gone(body, "getLinearVelocity")) cir.setReturnValue(dest.zero());
    }

    @Inject(method = "getAngularVelocity", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$angular(PhysicsPipelineBody body, Vector3d dest, CallbackInfoReturnable<Vector3d> cir) {
        if (create_galore_bridge$gone(body, "getAngularVelocity")) cir.setReturnValue(dest.zero());
    }
}
