package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.NatureHeal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks whose food is ticking, so NatureHeal can apply Cataclysm's Nature Heal to natural regeneration. */
@Mixin(FoodData.class)
public abstract class FoodTickHealMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void create_galore_bridge$markFoodTick(Player player, CallbackInfo ci) {
        NatureHeal.startFoodTick(player);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void create_galore_bridge$unmarkFoodTick(Player player, CallbackInfo ci) {
        NatureHeal.endFoodTick();
    }
}
