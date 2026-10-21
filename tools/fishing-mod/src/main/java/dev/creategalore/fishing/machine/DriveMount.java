package dev.creategalore.fishing.machine;

import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.compat.SableShips;
import dev.creategalore.fishing.sim.Controls;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.tackle.RodStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * A Reel Drive's block entity: the {@link RodMount} answers come from its {@link ReelDrive} and {@link MountedRod}.
 * The rod sits on top of the drive and points the way the drive faces.
 */
public interface DriveMount extends RodMount {
    ReelDrive drive();

    BlockEntity blockEntity();

    @Override
    default BlockPos mountPos() {
        return blockEntity().getBlockPos();
    }

    @Override
    default Direction rodFacing() {
        return blockEntity().getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    default Vec3 rodBase() {
        return new Vec3(0.5, 1.05, 0.5);
    }

    @Override
    default Vec3 tip() {
        return SableShips.toWorld(blockEntity().getLevel(),
                MountedRod.tip(Vec3.atLowerCornerOf(mountPos()).add(rodBase()), rodFacing(), mounted().stats()));
    }

    @Override
    default boolean reeling(CastHook hook) {
        return drive().reeling(hook);
    }

    @Override
    default double retrieveScale(CastHook hook) {
        return drive().retrieveScale(hook);
    }

    @Override
    default int biteTicks() {
        return drive().biteTicks();
    }

    @Override
    default void bite(CastHook hook) {
        drive().bite(hook);
    }

    @Override
    default boolean strikes(CastHook hook) {
        return drive().strikes(hook);
    }

    @Override
    default void fight(CastHook hook, Fight fight, RodStats stats, Controls controls) {
        drive().fight(fight, controls);
    }

    @Override
    default void landed(CastHook hook, List<ItemStack> items, int handXp) {
        mounted().landed(hook, items, handXp);
    }

    @Override
    default void lost(CastHook hook, Outcome outcome) {
    }

    @Override
    default String creditName() {
        return mounted().ownerName();
    }
}
