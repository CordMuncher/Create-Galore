/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;
import dev.creategalore.fishing.tide.data.FishingPlace;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.cast.FishingWater;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("unused")
public class TideFishEntity extends AbstractSchoolingFish {
    private final Item bucketItem;

    public TideFishEntity(EntityType<? extends AbstractSchoolingFish> entityType, Level level) {
        super(entityType, level);
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        this.bucketItem = BuiltInRegistries.ITEM.getOptional(key.withSuffix("_bucket")).orElseThrow();
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    public ItemStack getBucketItemStack() {
        return bucketItem.getDefaultInstance();
    }

    @Override
    public AbstractSchoolingFish startFollowing(AbstractSchoolingFish leader) {
        if (leader.getType() == this.getType()) return super.startFollowing(leader);
        return this;
    }

    @SuppressWarnings("deprecation")
    public static <T extends Mob> boolean checkWaterFishSpawnRules(EntityType<T> entityType, ServerLevelAccessor level,
                                                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (pos.getY() > level.getSeaLevel()
                || !level.getFluidState(pos.below()).is(FluidTags.WATER)
                || !level.getBlockState(pos.above()).is(Blocks.WATER)) return false;

        return checkTideFishSpawnRules(entityType, level, pos, random, "water");
    }

    @SuppressWarnings("deprecation")
    public static <T extends Mob> boolean checkLavaFishSpawnRules(EntityType<T> entityType, ServerLevelAccessor level,
                                                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (pos.getY() > level.getSeaLevel()
                || !level.getFluidState(pos.below()).is(FluidTags.LAVA)
                || !level.getBlockState(pos.above()).is(Blocks.LAVA)) return false;

        return checkTideFishSpawnRules(entityType, level, pos, random, "lava");
    }

    public static <T extends Mob> boolean checkVoidFishSpawnRules(EntityType<T> entityType, ServerLevelAccessor level,
                                                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (pos.getY() > FishingWater.voidSurface(level.getLevel())
                || !level.getBlockState(pos).isAir()) return false;

        return checkTideFishSpawnRules(entityType, level, pos, random, "void");
    }

    /**
     * Whether this fish may spawn here: its fish data's conditions pass at the spot (with no rod and no luck) and its
     * modifiers leave at least a tenth of its weight. Reads our species data (the same Tide-format files).
     */
    public static <T extends Mob> boolean checkTideFishSpawnRules(EntityType<T> entityType, ServerLevelAccessor level,
                                                                  BlockPos pos, RandomSource random, String medium) {
        FishData data = FishData.get(entityType).orElse(null);
        if (data == null || data.weight() <= 0) return false;
        FishingContext context = FishingPlace.context(level.getLevel(), pos.getCenter(), ItemStack.EMPTY, ItemStack.EMPTY,
                0, medium, 0, false, true, random);
        return data.canBiteAt(context) && data.weightAt(context) / data.weight() > 0.1;
    }
}