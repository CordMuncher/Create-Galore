package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import com.momosoftworks.coldsweat.util.math.CSMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cold Sweat 2.4.4 caps how wet rain makes you at Max Rain Soak (-0.2), but only from the dry side: once swimming
 * has soaked you past it, the rain step picks the smaller of -Rain Soak Speed and the (now positive) gap to the cap,
 * which is always -Rain Soak Speed, and you never dry while it rains. A swim followed by rain kept chilling players
 * without limit (seen at -2.27, about -100 °F, which no campfire can beat). In rain, ease anyone wetter than the cap
 * back to it at Rain Soak Speed instead.
 */
@Mixin(value = WaterTempModifier.class, remap = false)
public abstract class RainSoakCapMixin {
    @ModifyExpressionValue(
        method = "calculate",
        at = @At(value = "INVOKE", target = "Lcom/momosoftworks/coldsweat/util/math/CSMath;smallest([D)D"))
    private double create_galore_bridge$capRainSoak(double addAmount) {
        double temperature = ((WaterTempModifier) (Object) this).getTemperature();
        double cap = -ConfigSettings.MAX_RAIN_SOAK.get();
        return temperature < cap ? Math.min(ConfigSettings.RAIN_SOAK_SPEED.get(), cap - temperature) : addAmount;
    }
}
