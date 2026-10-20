/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class ShootingStarfish extends AbstractTideFish {
    public ShootingStarfish(EntityType<? extends ShootingStarfish> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean doesFlop() {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (random.nextFloat() < 0.5f) {
            this.level().addParticle(ParticleTypes.END_ROD,
                    this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6),
                    0.0, 0.0, 0.0);
        }
    }
}
