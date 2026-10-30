package dev.creategalore.bridge.encased;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Remembers the door or trapdoor under the casing, so a wrench or a pickaxe gives that same one back. */
public final class EncasedDoorBlockEntity extends BlockEntity {
    private BlockState original;

    public EncasedDoorBlockEntity(BlockPos pos, BlockState state) {
        super(EncasedDoors.BLOCK_ENTITY.get(), pos, state);
    }

    public BlockState original() {
        return original;
    }

    void setOriginal(BlockState original) {
        this.original = original;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (original != null) tag.put("Original", NbtUtils.writeBlockState(original));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        original = tag.contains("Original") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Original")) : null;
    }

    // Clients know it too, for pick-block and Jade.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
