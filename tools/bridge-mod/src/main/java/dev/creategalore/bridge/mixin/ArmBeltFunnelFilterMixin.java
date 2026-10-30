package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes.BeltPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Mechanical Arm dropping onto a belt block that carries a Brass Belt Funnel only drops what the funnel's filter
 * passes. Only that one belt block counts, not the rest of the belt. The arm asks every output this way (simulated)
 * before it picks anything up, so it leaves items no output will take where they are.
 */
@Mixin(value = ArmInteractionPoint.class, remap = false)
public abstract class ArmBeltFunnelFilterMixin {
    @Shadow protected Level level;
    @Shadow @Final protected BlockPos pos;

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$funnelFilter(ArmBlockEntity arm, ItemStack stack, boolean simulate,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!((Object) this instanceof BeltPoint) || level == null || stack.isEmpty()) {
            return;
        }
        BlockPos above = pos.above();
        if (!(level.getBlockState(above).getBlock() instanceof BeltFunnelBlock)) {
            return;
        }
        FilteringBehaviour filter = BlockEntityBehaviour.get(level, above, FilteringBehaviour.TYPE);
        if (filter != null && !filter.test(stack)) {
            cir.setReturnValue(stack);
        }
    }
}
