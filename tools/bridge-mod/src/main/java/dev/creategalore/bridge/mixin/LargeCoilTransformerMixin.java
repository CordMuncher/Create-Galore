package dev.creategalore.bridge.mixin;

import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeCoilBlockEntity;
import dev.creategalore.bridge.TransformerCoils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ItemInteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turning two Large Coils into a Large Transformer replaces both coil block entities, losing their turns. Note the
 * turns of this coil and its horizontal coil neighbours before the swap, then hand each new transformer half the turns
 * of the coil it replaced (see {@link TransformerCoils}).
 */
@Mixin(value = LargeCoilBlockEntity.class, remap = false)
public abstract class LargeCoilTransformerMixin extends BlockEntity implements TransformerCoils.Coil {
    @Shadow
    int turns;

    @Unique
    private static final Map<BlockPos, Integer> create_galore_bridge$pending = new HashMap<>();

    private LargeCoilTransformerMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int create_galore_bridge$turns() {
        return turns;
    }

    @Inject(method = "createTransformer", at = @At("HEAD"))
    private void create_galore_bridge$noteTurns(Direction lookingDirection, CallbackInfoReturnable<ItemInteractionResult> cir) {
        create_galore_bridge$pending.clear();
        if (level == null || level.isClientSide()) return;
        BlockPos pos = getBlockPos();
        create_galore_bridge$pending.put(pos, turns);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(pos.relative(d)) instanceof TransformerCoils.Coil coil) {
                create_galore_bridge$pending.put(pos.relative(d), coil.create_galore_bridge$turns());
            }
        }
    }

    @Inject(method = "createTransformer", at = @At("RETURN"))
    private void create_galore_bridge$giveTurns(Direction lookingDirection, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (level == null || level.isClientSide()) return;
        for (Map.Entry<BlockPos, Integer> e : create_galore_bridge$pending.entrySet()) {
            if (level.getBlockEntity(e.getKey()) instanceof TransformerCoils.Half half) {
                half.create_galore_bridge$setCoilTurns(e.getValue());
                level.getBlockEntity(e.getKey()).setChanged();
            }
        }
        create_galore_bridge$pending.clear();
    }
}
