/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.blocks.entities;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.tide.data.fishing.DisplayData;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.item.TideItemData;
import dev.creategalore.fishing.tide.registries.TideBlockEntities;
import dev.creategalore.fishing.tide.registries.blocks.FishDisplayBlock;
import dev.creategalore.fishing.tide.registries.blocks.FishDisplayShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FishDisplayBlockEntity extends BlockEntity {
    private ItemStack fish;
    private DisplayData displayData;
    private Entity renderedEntity;
    private double lengthCm;
    private double baseLengthCm;

    public FishDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(TideBlockEntities.FISH_DISPLAY, pos, state);
    }

    public boolean isEmpty() {
        return this.fish == null || displayData == null;
    }

    public DisplayData getDisplayData() {
        return this.displayData;
    }

    public ItemStack getDisplayStack() {
        return this.fish;
    }

    public double getFishLength() {
        return this.lengthCm;
    }

    public double getBaseLength() {
        return this.baseLengthCm;
    }

    public boolean setDisplayStack(ItemStack stack) {
        if (!isEmpty()) return false;
        var dataOp = FishData.getExact(stack);
        var displayOp = dataOp.flatMap(FishData::display);
        if (dataOp.isEmpty() || displayOp.isEmpty()) return false;

        this.fish = stack;
        this.displayData = displayOp.get();
        this.baseLengthCm = dataOp.get().getAverageLength();
        // Our catch record has the fish's real length; Tide's own length otherwise.
        CatchInfo info = stack.get(FishingRegistry.CATCH.get());
        this.lengthCm = info != null && info.cm().isPresent() ? info.cm().get()
                : TideItemData.FISH_LENGTH.getOrDefault(stack, baseLengthCm);

        this.updateShape(displayData.shape());
        this.markUpdated();
        return true;
    }

    public ItemStack takeDisplayStack() {
        ItemStack stack = this.fish;

        this.fish = null;
        this.displayData = null;
        this.renderedEntity = null;
        this.lengthCm = 0;
        this.baseLengthCm = 0;

        this.updateShape(FishDisplayShape.SHAPE_1x1);
        this.markUpdated();
        return stack;
    }

    private void updateShape(FishDisplayShape shape) {
        if (this.level == null) return;
        this.level.setBlock(getBlockPos(), getBlockState().setValue(FishDisplayBlock.SHAPE, shape), 1);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.fish != null) tag.put("fish", this.fish.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("fish")) ItemStack.parse(registries, tag.get("fish")).ifPresent(this::setDisplayStack);
        else {
            this.fish = null;
            this.displayData = null;
            this.renderedEntity = null;
            this.lengthCm = 0;
            this.baseLengthCm = 0;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        // Clients skip an empty update, so an emptied mount would keep showing its fish: say it's empty.
        if (tag.isEmpty()) tag.putBoolean("empty", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    protected void markUpdated() {
        this.setChanged();
        if (this.getLevel() != null) {
            this.getLevel().sendBlockUpdated(
                    this.getBlockPos(),
                    this.getBlockState(),
                    this.getBlockState(), 3);
        }
    }

    public Entity getRenderedEntity() {
        return this.renderedEntity;
    }

    public void setRenderedEntity(Entity entity) {
        this.renderedEntity = entity;
    }
}
