package dev.creategalore.fishing.process;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The Cleaning Table: lay a fish on it (right-click with the fish), then cut it with a knife ({@code #c:tools/knife})
 * until it comes apart into fillets, steaks, roe, bones and scraps ({@link Cleaning}). Heavier fish take more
 * strokes. An empty hand takes back an uncut fish, or whatever parts are waiting. A Deployer works it like a player
 * (one holding a fish lays it down, one holding a knife cuts), and hoppers and funnels feed and empty it.
 */
public class CleaningTableBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CleaningTableBlock> CODEC = simpleCodec(CleaningTableBlock::new);
    public static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/knife"));
    /** The slab top and its four legs, as the model draws them. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 15, 16),
            Block.box(1, 0, 1, 4, 12, 4), Block.box(12, 0, 1, 15, 12, 4), Block.box(1, 0, 12, 4, 12, 15),
            Block.box(12, 0, 12, 15, 12, 15));

    public CleaningTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CleaningTableBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CleaningTableBlockEntity table)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // A Deployer holding something mustn't fall through to the empty-hand take below: the parts it cut are left
        // in the outputs for hoppers and funnels.
        ItemInteractionResult pass = player instanceof FakePlayer ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(KNIVES)) {
            if (table.fish().isEmpty()) return pass;
            if (!level.isClientSide && table.stroke(player)) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (table.fish().isEmpty() && !table.hasOutputs() && Cleaning.isFish(stack)) {
            if (!level.isClientSide) table.place(player.getAbilities().instabuild ? stack.copy() : stack);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return pass;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CleaningTableBlockEntity table)) return InteractionResult.PASS;
        if (!table.hasOutputs() && table.fish().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (table.hasOutputs()) table.takeOutputs().forEach(s -> give(player, s));
            else give(player, table.takeFish());
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CleaningTableBlockEntity table) {
            table.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
