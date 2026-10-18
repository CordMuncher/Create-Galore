package dev.creategalore.bridge.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Berries and Cherries hurts you for 1.5 hearts every time you pick by hand without shears, on every plant it adds:
 * grapes, blueberries, strawberries and cherries too, none of which have thorns. Picking now never hurts, as with a
 * vanilla sweet berry bush; only walking through a raspberry bush still pricks (see {@link BerryBushNoThornsMixin}).
 * Shears behave as before.
 */
@Mixin(targets = {
    "net.mcreator.berriesandcherries.procedures.BlackGrapeBlockRightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.BlackGrapeBlockStage2RightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.GreenGrapeBlockRightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.GreenGrapeBlockStage2RightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.BlueberryBlockStage2RightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.StrawberryBlockRightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.RaspberryBlockRightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.RaspberryBlockStage2RightClickProcedure",
    "net.mcreator.berriesandcherries.procedures.CherryLeavesRightClickedProcedure"
}, remap = false)
public abstract class BerryPickNoThornsMixin {
    @Redirect(method = "execute", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private static boolean createGalore$noThorns(Entity entity, DamageSource source, float amount) {
        return false;
    }
}
