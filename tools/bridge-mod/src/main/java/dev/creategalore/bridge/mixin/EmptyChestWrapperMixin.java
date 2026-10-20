package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's item handler for vanilla chests is new InvWrapper(ChestBlock.getContainer(...)), and getContainer is null
 * when the block entity there isn't a vanilla chest. Jade asks for that handler with the block state the client sees,
 * so looking at a chest the server already turned into something else (a Lootr chest, a block on a moving ship, a
 * ghost block) gave Jade an InvWrapper around nothing: NullPointerException in InvWrapper.getSlots and an error file.
 * Treat a wrapper around nothing as "no item handler here", so the lookup moves on to the next provider.
 */
@Mixin(value = BlockCapability.class, remap = false)
public abstract class EmptyChestWrapperMixin {
    @WrapOperation(method = "getCapability", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/capabilities/IBlockCapabilityProvider;getCapability(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object dropEmptyWrapper(IBlockCapabilityProvider<?, ?> provider, Level level, BlockPos pos, BlockState state,
                                    BlockEntity blockEntity, Object context, Operation<Object> original) {
        Object handler = original.call(provider, level, pos, state, blockEntity, context);
        return handler instanceof InvWrapper wrapper && wrapper.getInv() == null ? null : handler;
    }
}
