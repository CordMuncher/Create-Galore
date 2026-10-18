package dev.creategalore.bridge.mixin;

import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlockEntity;
import dev.creategalore.bridge.TransformerCoils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stores the turns of the coil each Large Transformer half replaced (see {@link TransformerCoils}). */
@Mixin(value = LargeTransformerBlockEntity.class, remap = false)
public abstract class LargeTransformerCoilsMixin implements TransformerCoils.Half {
    @Unique
    private int create_galore_bridge$coilTurns = -1;

    @Override
    public int create_galore_bridge$coilTurns() {
        return create_galore_bridge$coilTurns;
    }

    @Override
    public void create_galore_bridge$setCoilTurns(int turns) {
        create_galore_bridge$coilTurns = turns;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void create_galore_bridge$writeTurns(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (create_galore_bridge$coilTurns >= 0) tag.putInt("CreateGaloreCoilTurns", create_galore_bridge$coilTurns);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void create_galore_bridge$readTurns(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        create_galore_bridge$coilTurns = tag.contains("CreateGaloreCoilTurns") ? tag.getInt("CreateGaloreCoilTurns") : -1;
    }
}
