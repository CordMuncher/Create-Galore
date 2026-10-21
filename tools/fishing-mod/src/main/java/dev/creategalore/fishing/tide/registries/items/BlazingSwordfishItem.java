/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.items;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;

public class BlazingSwordfishItem extends SwordfishItem {
    public BlazingSwordfishItem(Tiers tier, int damageMod, float speedMod, Properties properties) {
        super(tier, damageMod, speedMod, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.igniteForTicks(80);

        return super.hurtEnemy(stack, target, attacker);
    }
}
