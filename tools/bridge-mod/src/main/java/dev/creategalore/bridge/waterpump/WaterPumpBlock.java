package dev.creategalore.bridge.waterpump;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A water pump of one metal; right-click it to see what it reaches and what it is missing. */
public final class WaterPumpBlock extends Block implements EntityBlock {
    public final WaterPumps.Tier tier;

    public WaterPumpBlock(WaterPumps.Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterPumpBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != WaterPumps.BLOCK_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<WaterPumpBlockEntity>) (l, pos, s, pump) -> pump.serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WaterPumpBlockEntity pump) {
            player.displayClientMessage(pump.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
