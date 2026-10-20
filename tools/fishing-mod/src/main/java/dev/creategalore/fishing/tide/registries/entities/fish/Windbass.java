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

public class Windbass extends TideFishEntity {
    public Windbass(EntityType<? extends TideFishEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (random.nextFloat() < 0.8f) {
            this.level().addParticle(ParticleTypes.WHITE_SMOKE,
                    this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6),
                    0.0, 0.0, 0.0);
        }
    }
}
