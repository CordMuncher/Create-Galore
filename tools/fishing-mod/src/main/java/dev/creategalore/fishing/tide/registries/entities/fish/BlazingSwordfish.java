/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;

import dev.creategalore.fishing.tide.registries.entities.fish.lava.TideLavaFish;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.level.Level;

public class BlazingSwordfish extends TideLavaFish {
    public BlazingSwordfish(EntityType<? extends TideLavaFish> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new SmoothLavaSwimmingMoveControl(this, 85, 25,
                0.8f, 0.1f, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    static class SmoothLavaSwimmingMoveControl extends MoveControl {
        private final int maxTurnX;
        private final int maxTurnY;
        private final float inLavaSpeedModifier;
        private final float outsideLavaSpeedModifier;
        private final boolean applyGravity;

        public SmoothLavaSwimmingMoveControl(Mob mob, int maxTurnX, int maxTurnY, float inLavaSpeedModifier, float outsideLavaSpeedModifier, boolean applyGravity) {
            super(mob);
            this.maxTurnX = maxTurnX;
            this.maxTurnY = maxTurnY;
            this.inLavaSpeedModifier = inLavaSpeedModifier;
            this.outsideLavaSpeedModifier = outsideLavaSpeedModifier;
            this.applyGravity = applyGravity;
        }

        @Override
        public void tick() {
            if (this.applyGravity && this.mob.isInLava()) {
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0, 0.005, 0.0));
            }

            if (this.operation == Operation.MOVE_TO && !this.mob.getNavigation().isDone()) {
                double d0 = this.wantedX - this.mob.getX();
                double d1 = this.wantedY - this.mob.getY();
                double d2 = this.wantedZ - this.mob.getZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                if (d3 < 2.5000003E-7F) {
                    this.mob.setZza(0.0F);
                } else {
                    float f = (float)(Mth.atan2(d2, d0) * 180.0F / (float)Math.PI) - 90.0F;
                    this.mob.setYRot(this.rotlerp(this.mob.getYRot(), f, this.maxTurnY));
                    this.mob.yBodyRot = this.mob.getYRot();
                    this.mob.yHeadRot = this.mob.getYRot();
                    float f1 = (float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
                    if (this.mob.isInLava()) {
                        this.mob.setSpeed(f1 * this.inLavaSpeedModifier);
                        double d4 = Math.sqrt(d0 * d0 + d2 * d2);
                        if (Math.abs(d1) > 1.0E-5F || Math.abs(d4) > 1.0E-5F) {
                            float f3 = -((float)(Mth.atan2(d1, d4) * 180.0F / (float)Math.PI));
                            f3 = Mth.clamp(Mth.wrapDegrees(f3), (float)(-this.maxTurnX), (float)this.maxTurnX);
                            this.mob.setXRot(this.rotlerp(this.mob.getXRot(), f3, 5.0F));
                        }

                        float f6 = Mth.cos(this.mob.getXRot() * (float) (Math.PI / 180.0));
                        float f4 = Mth.sin(this.mob.getXRot() * (float) (Math.PI / 180.0));
                        this.mob.zza = f6 * f1;
                        this.mob.yya = -f4 * f1;
                    } else {
                        float f5 = Math.abs(Mth.wrapDegrees(this.mob.getYRot() - f));
                        float f2 = getTurningSpeedFactor(f5);
                        this.mob.setSpeed(f1 * this.outsideLavaSpeedModifier * f2);
                    }
                }
            } else {
                this.mob.setSpeed(0.0F);
                this.mob.setXxa(0.0F);
                this.mob.setYya(0.0F);
                this.mob.setZza(0.0F);
            }
        }

        private static float getTurningSpeedFactor(float degreesToTurn) {
            return 1.0F - Mth.clamp((degreesToTurn - 10.0F) / 50.0F, 0.0F, 1.0F);
        }
    }
}
