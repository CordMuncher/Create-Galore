package dev.creategalore.bridge.mixin;

import com.mojang.serialization.Dynamic;
import dev.creategalore.bridge.StructureFixes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import umpaz.brewinandchewin.common.utility.dfu.BnCDataFixer;

/**
 * Brewin' and Chewin' does the same as Steam 'n' Rails (see RailsStructureFixSkipMixin): its own fixer runs after
 * every vanilla one, and structure files never carry its brewinandchewin:data_version stamp, so each structure from
 * every mod went through its whole chain (2 to 4 s for a big When Dungeons Arise file). Its fixes (up to version 100)
 * only rename brewinandchewin:scarlet_pierogies and its recipe, so a structure without a brewinandchewin: string
 * comes out unchanged: skip it.
 */
@Mixin(value = BnCDataFixer.class, remap = false)
public abstract class BrewinStructureFixSkipMixin {
    @Inject(method = "updateWithFixers", at = @At("HEAD"), cancellable = true)
    private <T> void skipStructuresWithoutBrewin(DataFixTypes type, Dynamic<T> dynamic, CallbackInfoReturnable<Dynamic<T>> cir) {
        if (type != DataFixTypes.STRUCTURE || !StructureFixes.BREWIN_CHECKED) return;
        if (dynamic.getValue() instanceof CompoundTag tag && !tag.contains("brewinandchewin:data_version")
                && !StructureFixes.mentions(tag, "brewinandchewin:")) {
            cir.setReturnValue(dynamic);
        }
    }
}
