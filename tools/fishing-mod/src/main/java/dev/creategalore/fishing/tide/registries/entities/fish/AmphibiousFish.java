/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AmphibiousFish extends AbstractTideFish {
    public AmphibiousFish(EntityType<? extends AmphibiousFish> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public float getSpeed() {
        return super.getSpeed() * (isInWater() ? 0.35f : 0.05f);
    }

    @Override
    public boolean doesFlop() {
        return false;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new AmphibiousFishGoToWaterGoal(this, 1.0f));
        this.goalSelector.addGoal(0, new RandomStrollGoal(this, 1.0f, 60));
    }

    static class AmphibiousFishGoToWaterGoal extends Goal {
        private final PathfinderMob mob;
        private double wantedX;
        private double wantedY;
        private double wantedZ;
        private final double speedModifier;
        private final Level level;

        public AmphibiousFishGoToWaterGoal(PathfinderMob mob, double speedModifier) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.level = mob.level();
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        public boolean canUse() {
            if (this.mob.isInWater()) return false;

            Vec3 waterPos = this.getWaterPos();
            if (waterPos == null) return false;

            this.wantedX = waterPos.x;
            this.wantedY = waterPos.y;
            this.wantedZ = waterPos.z;
            return true;
        }

        public boolean canContinueToUse() {
            return !this.mob.getNavigation().isDone();
        }

        public void start() {
            this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
        }

        private Vec3 getWaterPos() {
            RandomSource random = this.mob.getRandom();
            BlockPos pos = this.mob.blockPosition();
            for (int i = 0; i < 10; i++) {
                BlockPos offsetPos = pos.offset(random.nextInt(20) - 10, 2 - random.nextInt(8), random.nextInt(20) - 10);
                if (this.level.getBlockState(offsetPos).is(Blocks.WATER)) return Vec3.atBottomCenterOf(offsetPos);
            }
            return null;
        }
    }
}
