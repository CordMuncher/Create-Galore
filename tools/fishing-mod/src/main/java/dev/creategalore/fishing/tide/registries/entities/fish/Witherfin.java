/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;

import dev.creategalore.fishing.tide.registries.entities.fish.lava.TideSchoolingLavaFish;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class Witherfin extends TideSchoolingLavaFish {
    public Witherfin(EntityType<? extends TideSchoolingLavaFish> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TURTLE_EGG_CRACK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_EGG_BREAK;
    }

    @Override
    public void playerTouch(Player player) {
        if (player instanceof ServerPlayer && player.hurt(this.damageSources().noAggroMobAttack(this), 1f)) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0), this);
            this.playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 1.0f, 1.0f);
        }
    }
}
