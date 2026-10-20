/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish.lava;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class LavaBoundPathNavigation extends PathNavigation {
    private boolean allowBreaching;

    public LavaBoundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    protected PathFinder createPathFinder(int p_26598_) {
        this.allowBreaching = this.mob instanceof LavaAnimal;
        this.nodeEvaluator = new LavaSwimNodeEvaluator(this.allowBreaching);
        return new PathFinder(this.nodeEvaluator, p_26598_);
    }

    protected boolean canUpdatePath() {
        return this.allowBreaching || this.mob.isInWaterOrBubble() || mob.isInLava();
    }

    protected Vec3 getTempMobPos() {
        return new Vec3(this.mob.getX(), this.mob.getY(0.5), this.mob.getZ());
    }

    protected double getGroundY(Vec3 vec) {
        return vec.y;
    }

    protected boolean canMoveDirectly(Vec3 pos1, Vec3 pos2) {
        return isClearForMovementBetween(this.mob, pos1, pos2, false);
    }

    public boolean isStableDestination(BlockPos pos) {
        return !this.level.getBlockState(pos).isSolidRender(this.level, pos);
    }

    public void setCanFloat(boolean canSwim) {}
}
