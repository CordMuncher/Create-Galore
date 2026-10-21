/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish.lava;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.PathType;

public abstract class LavaAnimal extends PathfinderMob {
    protected LavaAnimal(EntityType<? extends LavaAnimal> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.LAVA, 0f);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    protected void handleAirSupply(ServerLevel level, int airSupply) {
        if (this.isAlive() && !this.isInLava()) {
            this.setAirSupply(airSupply - 1);
            if (this.getAirSupply() == -20) {
                this.setAirSupply(0);
                this.hurt(this.damageSources().drown(), 2.0F);
            }
        }
        else this.setAirSupply(300);
    }

    @Override
    public void baseTick() {
        int i = this.getAirSupply();
        super.baseTick();
        Level level = this.level();
        if (level instanceof ServerLevel serverlevel) {
            this.handleAirSupply(serverlevel, i);
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected int getBaseExperienceReward() {
        if (isInLava()) return 0;
        return super.getBaseExperienceReward();
    }

    @Override
    public boolean canBeLeashed() {
        return
        false;
    }

}
