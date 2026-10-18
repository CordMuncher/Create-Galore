package dev.creategalore.bridge.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Berries and Cherries bush damage hurts every entity that isn't a player or villager, dropped items included. An
 * item has 5 health, so anything that lands in a bush is destroyed within a quarter second, and picked fruit spawns
 * right above the plant and falls into it. Thorns now only hurt living things, like a vanilla sweet berry bush.
 */
@Mixin(targets = {
    "net.mcreator.berriesandcherries.procedures.RaspberryBushDamageProcedure",
    "net.mcreator.berriesandcherries.procedures.BlueberryBushDamageProcedure"
}, remap = false)
public abstract class BerryThornsLivingOnlyMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void createGalore$livingOnly(LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity)) ci.cancel();
    }
}
