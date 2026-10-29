package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Schematicannon waiting on a missing block when its chunk unloaded (or the server stopped) never printed again: it
 * saves the item it waits for, and on loading it starts the build over from the first position, but it only moves on
 * to the next position once nothing is missing, so it stayed on the empty spot before the build ("searching") for
 * good, even after the item was brought and Play pressed. The build loads again from the start, so the old wait is over.
 */
@Mixin(value = SchematicannonBlockEntity.class, remap = false)
public abstract class SchematicannonResumeMixin {
    @Shadow
    public SchematicPrinter printer;
    @Shadow
    public ItemStack missingItem;

    @Inject(method = "initializePrinter", at = @At("TAIL"))
    private void create_galore_bridge$forgetOldWait(ItemStack blueprint, CallbackInfo ci) {
        if (printer.isLoaded() && !printer.isErrored()) missingItem = null;
    }
}
