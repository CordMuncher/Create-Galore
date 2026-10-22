package dev.creategalore.bridge.mixin;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sable moves a waterlogged block into a ship with its water, so a slab or stair built in the sea sailed off holding a
 * block of sea water, which then spilled across the deck as a thin sheet. Do what Create's contraptions do: the water
 * stays in the sea and the block goes aboard dry; taking a ship apart in water waterlogs the blocks that land in it.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SableAssemblyWaterloggedMixin {
    @Unique
    private record create_galore_bridge$Move(boolean intoShip, List<BlockPos> olds, List<BlockPos> news) {
    }

    @Unique
    private static final ArrayDeque<create_galore_bridge$Move> create_galore_bridge$moves = new ArrayDeque<>();

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private static void create_galore_bridge$rememberWater(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                         Iterable<BlockPos> blocks, CallbackInfo ci) {
        List<BlockPos> olds = new ArrayList<>(), news = new ArrayList<>();
        boolean intoShip = true;
        for (BlockPos p : blocks) {
            boolean fromShip = Sable.HELPER.getContaining(level, p) != null;
            BlockPos to = transform.apply(p);
            if (fromShip) {
                // Taking a ship apart (or splitting it into another ship): remember where the sea is under the blocks.
                intoShip = false;
                if (Sable.HELPER.getContaining(transform.getLevel(), to) == null && transform.getLevel().getFluidState(to).isSourceOfType(Fluids.WATER)) {
                    olds.add(p.immutable());
                    news.add(to.immutable());
                }
            } else if (waterlogged(level.getBlockState(p))) {
                olds.add(p.immutable());
                news.add(to.immutable());
            }
        }
        create_galore_bridge$moves.push(new create_galore_bridge$Move(intoShip, olds, news));
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private static void create_galore_bridge$settleWater(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                       Iterable<BlockPos> blocks, CallbackInfo ci) {
        create_galore_bridge$Move move = create_galore_bridge$moves.poll();
        if (move == null) return;
        ServerLevel to = transform.getLevel();
        for (int i = 0; i < move.olds().size(); i++) {
            BlockPos old = move.olds().get(i), now = move.news().get(i);
            BlockState state = to.getBlockState(now);
            if (!state.hasProperty(BlockStateProperties.WATERLOGGED)) continue;
            if (move.intoShip()) {
                if (!state.getValue(BlockStateProperties.WATERLOGGED)) continue;
                to.setBlock(now, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (level.getBlockState(old).isAir()) level.setBlock(old, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            } else if (!state.getValue(BlockStateProperties.WATERLOGGED)) {
                to.setBlock(now, state.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
            }
        }
    }

    @Unique
    private static boolean waterlogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }
}
