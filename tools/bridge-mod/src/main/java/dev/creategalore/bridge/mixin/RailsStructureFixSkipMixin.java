package dev.creategalore.bridge.mixin;

import com.railwayteam.railways.base.datafixerapi.DataFixesInternals;
import com.railwayteam.railways.base.datafixerapi.DataFixesInternalsImpl;
import dev.creategalore.bridge.StructureFixes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Steam 'n' Rails runs its own data fixer after every vanilla one (MixinDataFixTypes). Chunks carry a
 * Railways_DataVersion stamp so that pass is instant for them, but structure files from other mods never do: each
 * one went through Rails' whole fixer chain, which reads and rewrites the entire file through DFU. On the server
 * thread that is 5 to 20 s for the big ones (When Dungeons Arise keeps, Incendium's sanctum) the first time a chunk
 * needs them, which froze the server. Every Rails fix (versions 1 and 2) only renames or reshapes block states named
 * railways:..., so a structure without a single railways: string comes out of the pass unchanged: skip it.
 */
@Mixin(value = DataFixesInternalsImpl.class, remap = false)
public abstract class RailsStructureFixSkipMixin {
    /** The Rails fixer version whose fixes were checked (CRDataFixers in Rails 0.3.0-beta.2). */
    private static final int CHECKED_RAILS_FIXER_VERSION = 2;

    @Inject(method = "updateWithAllFixers", at = @At("HEAD"), cancellable = true)
    private void skipStructuresWithoutRails(DataFixTypes type, CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (type != DataFixTypes.STRUCTURE || tag.contains("Railways_DataVersion")) return;
        DataFixesInternals.DataFixerEntry entry = ((DataFixesInternals) (Object) this).getFixerEntry();
        if (entry == null || entry.currentVersion() != CHECKED_RAILS_FIXER_VERSION) return;
        if (!StructureFixes.mentions(tag, "railways:")) cir.setReturnValue(tag);
    }
}
