/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish.special;
import dev.creategalore.fishing.mixin.PathNavigationAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

public class VoidBoundPathNavigation extends PathNavigation {
    private boolean allowBreaching;

    public VoidBoundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    protected PathFinder createPathFinder(int p_26598_) {
        this.allowBreaching = this.mob instanceof WaterAnimal;
        this.nodeEvaluator = new VoidSwimNodeEvaluator(this.allowBreaching);
        return new PathFinder(this.nodeEvaluator, p_26598_);
    }

    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        if (targets.isEmpty()) return null;
        if (!this.canUpdatePath()) return null;
        if (this.path != null && !this.path.isDone() && targets.contains(nav().cgfish$getTargetPos())) return this.path;
        else {
            this.level.getProfiler().push("pathfind");
            BlockPos blockpos = offsetUpward ? this.mob.blockPosition().above() : this.mob.blockPosition();
            int i = (int)(followRange + (float)regionOffset);
            PathNavigationRegion pathnavigationregion = new PathNavigationRegion(this.level, blockpos.offset(-i, -i, -i), blockpos.offset(i, i, i));
            Path path = nav().cgfish$getPathFinder().findPath(pathnavigationregion, this.mob, targets, followRange, accuracy, nav().cgfish$getMaxVisitedNodesMultiplier());
            this.level.getProfiler().pop();
            if (path != null) {
                nav().cgfish$setTargetPos(path.getTarget());
                nav().cgfish$setReachRange(accuracy);
                nav().cgfish$resetStuckTimeout();
            }

            return path;
        }
    }

    protected boolean canUpdatePath() {
        return this.allowBreaching || this.mob.isInWaterOrBubble() || this.mob.isInLava();
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

    private PathNavigationAccessor nav() {
        return (PathNavigationAccessor) this;
    }
}
