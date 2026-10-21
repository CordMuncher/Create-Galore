/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.fish;

import dev.creategalore.fishing.tide.data.FishLengthHolder;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

public abstract class AbstractTideFish extends WaterAnimal implements Bucketable, FishLengthHolder {
    private static final EntityDataAccessor<Boolean> FROM_BUCKET = SynchedEntityData.defineId(AbstractTideFish.class, EntityDataSerializers.BOOLEAN);

    private final Item bucketItem;
    private double length;

    public AbstractTideFish(EntityType<? extends WaterAnimal> entityType, Level level) {
        super(entityType, level);

        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        Item fishItem = BuiltInRegistries.ITEM.getOptional(key).orElseThrow();
        this.bucketItem = BuiltInRegistries.ITEM.getOptional(key.withSuffix("_bucket")).orElseThrow();
        this.length = FishData.get(fishItem).map(data -> data.getRandomLength(getRandom())).orElse(0.0);
    }

    @Override
    public void travel(Vec3 movement) {
        if (this.isEffectiveAi() && this.isInWater()) {
            this.moveRelative(this.getSpeed() / 50f, movement);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
        }
        else super.travel(movement);
    }

    public boolean doesFlop() {
        return true;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.fromBucket();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !this.fromBucket() && !this.hasCustomName();
    }

    @Override
    public void aiStep() {
        if (this.doesFlop() && !this.isInLiquid() && this.onGround() && this.verticalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().add(
                    (this.random.nextFloat() * 2.0 - 1.0) * 0.05, 0.4,
                    (this.random.nextFloat() * 2.0 - 1.0) * 0.05)
            );
            this.setOnGround(false);
            this.hasImpulse = true;
            this.playSound(this.getFlopSound(), this.getSoundVolume(), this.getVoicePitch());
        }
        super.aiStep();
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
    }

    @Override
    public ItemStack getBucketItemStack() {
        return bucketItem.getDefaultInstance();
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_FISH;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FROM_BUCKET, false);
    }

    @Override
    public boolean fromBucket() {
        return this.entityData.get(FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
        this.entityData.set(FROM_BUCKET, fromBucket);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("FromBucket", this.fromBucket());
        compound.putDouble(tide$LENGTH_KEY, this.length);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setFromBucket(tag.contains("FromBucket") && tag.getBoolean("FromBucket"));
        this.length = tag.contains(tide$LENGTH_KEY) ? tag.getDouble(tide$LENGTH_KEY) : this.length;
    }

    @Override @SuppressWarnings("deprecation")
    public void saveToBucketTag(ItemStack stack) {
        Bucketable.saveDefaultDataToBucketTag(this, stack);

        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putDouble(tide$LENGTH_KEY, this.tide$getLength());
        });

    }

    @Override @SuppressWarnings("deprecation")
    public void loadFromBucketTag(CompoundTag tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        this.length = tag.contains(tide$LENGTH_KEY) ? tag.getDouble(tide$LENGTH_KEY) : this.tide$getLength();
    }

    @Override
    public double tide$getLength() {
        return this.length;
    }

    @Override
    public void tide$setLength(double length) {
        this.length = length;
    }
}