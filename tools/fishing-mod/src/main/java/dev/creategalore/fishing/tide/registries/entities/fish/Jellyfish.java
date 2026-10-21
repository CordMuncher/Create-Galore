/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Jellyfish extends AbstractTideFish {
    public Jellyfish(EntityType<? extends Jellyfish> entityType, Level level) {
        super(entityType, level);
    }

    public boolean hasMovementVector() {
        return this.getDeltaMovement().multiply(1.0, 0.0, 1.0).lengthSqr() > 1.0E-5;
    }

    @Override
    public void travel(Vec3 movement) {
        if (this.isEffectiveAi() && this.isInWater()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.0035, 0.0));
        }
        else super.travel(movement);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new JellyfishRandomMovementGoal(this));
    }

    static class JellyfishRandomMovementGoal extends Goal {
        private final Jellyfish jellyfish;

        public JellyfishRandomMovementGoal(Jellyfish jellyfish) {
            this.jellyfish = jellyfish;
        }

        public boolean canUse() {
            return true;
        }

        public void tick() {
            if (!jellyfish.isInWater()) return;
            int i = this.jellyfish.getNoActionTime();
            if (i > 100) {
                this.jellyfish.setDeltaMovement(Vec3.ZERO);
            } else if ((this.jellyfish.getRandom().nextInt(reducedTickDelay(25)) == 0 || !this.jellyfish.wasTouchingWater) && !this.jellyfish.hasMovementVector()) {
                float f = this.jellyfish.getRandom().nextFloat() * ((float)Math.PI * 2F);
                this.jellyfish.setDeltaMovement(new Vec3(
                        Mth.cos(f) * 0.16f,
                        this.jellyfish.getRandom().nextFloat() * 0.32f,
                        Mth.sin(f) * 0.16f)
                );
            }
        }
    }
}
