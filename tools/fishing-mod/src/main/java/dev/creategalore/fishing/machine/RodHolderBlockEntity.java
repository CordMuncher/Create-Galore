package dev.creategalore.fishing.machine;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.sim.Controls;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.tackle.RodStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Rod Holder's block entity: a rod set in it waits for a bite, sounds the alarm and pulses redstone, and holds
 * the bite for a few seconds so someone can take the rod and strike. It never reels or strikes itself.
 */
public class RodHolderBlockEntity extends BlockEntity implements RodMount, IHaveGoggleInformation {
    /** A bite on a holder lasts five seconds (by hand it is a second and a half). */
    public static final int BITE_TICKS = 100;

    private final MountedRod rod = new MountedRod(this, this);

    public RodHolderBlockEntity(BlockPos pos, BlockState state) {
        super(FishingRegistry.ROD_HOLDER_ENTITY.get(), pos, state);
    }

    @Override
    public MountedRod mounted() {
        return rod;
    }

    @Override
    public Direction rodFacing() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public Vec3 rodBase() {
        return new Vec3(0.5, 0.8, 0.5);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, RodHolderBlockEntity be) {
        be.rod.tick();
    }

    @Override
    public BlockPos mountPos() {
        return worldPosition;
    }

    @Override
    public Vec3 tip() {
        return MountedRod.tip(Vec3.atLowerCornerOf(worldPosition).add(rodBase()), rodFacing(), rod.stats());
    }

    @Override
    public boolean reeling(CastHook hook) {
        return false;
    }

    @Override
    public int biteTicks() {
        return BITE_TICKS;
    }

    @Override
    public void bite(CastHook hook) {
        rod.alarm();
    }

    @Override
    public boolean strikes(CastHook hook) {
        if (hook.phase() == CastHook.Phase.BITE && level.getGameTime() % 20 == 0) rod.alarm();
        return false;
    }

    @Override
    public void fight(CastHook hook, Fight fight, RodStats stats, Controls controls) {
        // A holder only holds: the fish works against the drag until someone takes the rod.
    }

    @Override
    public void landed(CastHook hook, List<ItemStack> items, int handXp) {
        rod.landed(hook, items, handXp);
    }

    @Override
    public void lost(CastHook hook, Outcome outcome) {
    }

    @Override
    public String creditName() {
        return rod.ownerName();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        rod.removed();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return MachineInfo.describe(tooltip, rod, null);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        rod.write(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rod.read(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        rod.write(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
