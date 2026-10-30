package dev.creategalore.bridge.encased;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/** A trapdoor in Create casing: redstone opens it (the iron trapdoor rules), and shut it seals against smog. */
public final class EncasedTrapdoorBlock extends TrapDoorBlock implements EntityBlock, IWrenchable {
    final EncasedDoors.Casing casing;

    public EncasedTrapdoorBlock(EncasedDoors.Casing casing, Properties properties) {
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

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>();
        drops.add(casing.stack());
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        BlockState original = be instanceof EncasedDoorBlockEntity cased ? cased.original() : null;
        if (original != null && !original.isAir()) drops.addAll(original.getDrops(params));
        else drops.add(new ItemStack(Blocks.OAK_TRAPDOOR));
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(EncasedDoors.original(level, pos, Blocks.OAK_TRAPDOOR.defaultBlockState()).getBlock());
    }

    /** Wrench: the casing comes off (back to the player) and the old trapdoor is back. Sneak-wrench picks it up. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        EncasedDoors.uncaseTrapdoor(level, context.getClickedPos(), state);
        if (player != null && !player.isCreative()) player.getInventory().placeItemBackInInventory(casing.stack());
        IWrenchable.playRemoveSound(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }
}
