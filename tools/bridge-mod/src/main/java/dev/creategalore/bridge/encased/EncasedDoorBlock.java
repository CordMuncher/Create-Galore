package dev.creategalore.bridge.encased;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** A door in Create casing: redstone opens it (the iron door rules), and shut it seals against smog. */
public final class EncasedDoorBlock extends DoorBlock implements EntityBlock, IWrenchable {
    final EncasedDoors.Casing casing;

    public EncasedDoorBlock(EncasedDoors.Casing casing, Properties properties) {
        super(BlockSetType.IRON, properties);
        this.casing = casing;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EncasedDoorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(Component.translatable("create_galore_bridge.encased_door.redstone"), true);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** The casing and the door it covers; the top half drops nothing (the bottom half drops for both). */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return List.of();
        List<ItemStack> drops = new ArrayList<>();
        drops.add(casing.stack());
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        BlockState original = be instanceof EncasedDoorBlockEntity cased ? cased.original() : null;
        if (original != null && !original.isAir()) drops.addAll(original.getDrops(params));
        else drops.add(new ItemStack(Blocks.OAK_DOOR));
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(EncasedDoors.original(level, pos, Blocks.OAK_DOOR.defaultBlockState()).getBlock());
    }

    /** Wrench: the casing comes off (back to the player) and the old door is back. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        EncasedDoors.uncaseDoor(level, context.getClickedPos(), state);
        if (player != null && !player.isCreative()) player.getInventory().placeItemBackInInventory(casing.stack());
        IWrenchable.playRemoveSound(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    /** Sneak-wrench picks the whole door up, casing and all, from whichever half was clicked. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? context.getClickedPos() : context.getClickedPos().below();
        BlockState lowerState = level.getBlockState(lower);
        if (!lowerState.is(this)) return InteractionResult.PASS;
        Player player = context.getPlayer();
        var event = new BlockEvent.BreakEvent(level, lower, lowerState, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return InteractionResult.SUCCESS;
        if (player != null && !player.isCreative()) {
            Block.getDrops(lowerState, server, lower, level.getBlockEntity(lower), player, context.getItemInHand())
                    .forEach(stack -> player.getInventory().placeItemBackInInventory(stack));
        }
        level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.destroyBlock(lower, false);
        IWrenchable.playRemoveSound(level, lower);
        return InteractionResult.SUCCESS;
    }
}
