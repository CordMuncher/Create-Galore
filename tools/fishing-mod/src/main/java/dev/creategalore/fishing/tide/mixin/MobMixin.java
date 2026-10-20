/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.mixin;

import dev.creategalore.fishing.tide.data.FreezableMob;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin implements FreezableMob {
    @Shadow @Final private static EntityDataAccessor<Byte> DATA_MOB_FLAGS_ID;

    @Override
    public boolean tide$isFrozen() {
        SynchedEntityData entityData = ((Entity)(Object)this).getEntityData();
        return (entityData.get(DATA_MOB_FLAGS_ID) & FROZEN_BIT) != 0;
    }

    @Override
    public void tide$setFrozen(boolean frozen) {
        SynchedEntityData entityData = ((Entity)(Object)this).getEntityData();
        byte b = entityData.get(DATA_MOB_FLAGS_ID);
        entityData.set(DATA_MOB_FLAGS_ID, frozen ? (byte)(b | FROZEN_BIT) : (byte)(b & -1 - FROZEN_BIT));
    }

    @Inject(method = "tick", at = @At(value = "HEAD"), cancellable = true)
    private void tick(CallbackInfo ci) {
        if (this.tide$isFrozen()) {
            Mob mob = ((Mob)(Object)this);
            if (mob.isDeadOrDying()) return;
            if (mob.tickCount > 0) mob.tickCount--;
            if (mob.hurtTime > 0) mob.hurtTime--;
            if (mob.invulnerableTime > 0) mob.invulnerableTime--;
            ci.cancel();
        }
    }
}
