/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class Snail extends AmphibiousFish {
    public Snail(EntityType<? extends Snail> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void handleAirSupply(int airSupply) {
        this.setAirSupply(300);
    }

    @Override
    public float getSpeed() {
        return super.getSpeed() * (isInWater() ? 3.5f : 1.0f);
    }
}
