package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.FilterFrame;
import com.endertech.minecraft.mods.adpother.blocks.Pollutant;
import dev.creategalore.bridge.Exhaust;
import dev.creategalore.bridge.FilterUpkeep;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Filter frames work without filter material or upkeep: every frame catches every kind of smog, iron 2 units a
 * second, gold 4, diamond 8 (see {@link FilterUpkeep}). Filtering leaves filtered exhaust behind, which has to get out
 * at the top of the filter stack, so a filter whose top is closed off takes nothing: you can't filter into a sealed box.
 */
@Mixin(value = FilterFrame.class, remap = false)
public abstract class PotrFilterExhaustMixin {
    private static final String FILL =
        "fill(Lcom/endertech/minecraft/mods/adpother/blocks/FilterFrame$BlockTile;Lcom/endertech/minecraft/mods/adpother/blocks/Pollutant;I)I";

    @Shadow
    @Final
    protected int slotLimit;

    @Inject(method = FILL, at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$fill(FilterFrame.BlockTile storage, Pollutant<?> pollutant, int amount, CallbackInfoReturnable<Integer> cir) {
        if (amount <= 0 || !(storage.getLevel() instanceof ServerLevel level)) {
            cir.setReturnValue(0);
            return;
        }
        BlockPos pos = storage.getBlockPos();
        int count = Exhaust.hasOutlet(level, pos)
            ? FilterUpkeep.take((FilterUpkeep.Budget) storage, slotLimit, amount, level.getGameTime())
            : 0;
        if (count > 0) {
            Exhaust.release(level, pos, count);
            new FilterFrame.PollutantFilteredMsg(pollutant, pos).sendToAllObservingChunk(level, pos);
        }
        cir.setReturnValue(count);
    }

    /** Always shown with its filter in: the frame's inner block is there for aiming and standing on too. */
    @Inject(method = "hasFilterMaterialInstalled", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$alwaysFilled(BlockGetter world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    /** Nothing to put in, so no screen: say what the frame does instead. */
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$noScreen(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("create_galore_bridge.filter_frame.rate", FilterUpkeep.rate(slotLimit))
                .withStyle(ChatFormatting.GRAY), true);
        }
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
