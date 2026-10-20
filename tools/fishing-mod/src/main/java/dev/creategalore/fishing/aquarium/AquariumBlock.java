package dev.creategalore.fishing.aquarium;

import com.mojang.serialization.MapCodec;
import dev.creategalore.fishing.FishingRegistry;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An Aquarium Tank block: glass walls in a steel frame. Blocks that fill a box join into one tank
 * ({@link AquariumTanks}); each side property says whether that face joins another block of the same tank, which
 * the block model uses to leave out the glass and frame there. The top of a tank is open.
 *
 * <p>Right-clicking any block of the tank works the whole tank ({@link AquariumUse}).
 */
public class AquariumBlock extends Block implements EntityBlock {
    public static final MapCodec<AquariumBlock> CODEC = simpleCodec(AquariumBlock::new);
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;

    /** The client's per-tick update for the swimming fish (set by the client setup; does nothing on a server). */
    public static BiConsumer<Level, AquariumBlockEntity> clientTick = (level, be) -> {
    };

    public AquariumBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES.values()) state = state.setValue(side, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AquariumBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != FishingRegistry.AQUARIUM_ENTITY.get()) return null;
        if (level.isClientSide) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<AquariumBlockEntity>) (l, p, s, be) -> {
                if (be.isController()) clientTick.accept(l, be);
            };
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<AquariumBlockEntity>) AquariumBlockEntity::serverTick;
    }

    /** The block entity only exists once the block is in the world, so joining a tank waits a tick. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && !old.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        AquariumTanks.form(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof AquariumBlockEntity be) {
            AquariumTanks.removed(server, pos, be);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AquariumBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return AquariumUse.useItem(be, stack, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AquariumBlockEntity be)) return InteractionResult.PASS;
        return AquariumUse.useEmpty(be, player);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) && state.getValue(SIDES.get(side));
    }
}
