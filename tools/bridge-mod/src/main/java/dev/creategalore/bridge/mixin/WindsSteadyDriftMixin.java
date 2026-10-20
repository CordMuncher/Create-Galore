package dev.creategalore.bridge.mixin;

import com.enzonic.aeronauticswinds.weather.WeatherRegions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Winds & Weather slides its weather map along with a drift that itself changes over time, and moves it by
 * drift(time) * time instead of adding the drift up. The slow change in drift is multiplied by the world's age, so the
 * map scrolls faster the older the world gets: on a fresh world a rain spell lasts a minute or two, after
 * 1.5 million ticks (about 21 hours of the server running) 10-20 s, after 3 million about 5 s, up to a hundred times
 * an hour. Its rain, storms, lightning, snow and puddles flickered on and off with it (on the server, and its clouds on
 * every client the same way). Read the drift at time 0, so the map moves at the steady speed it was meant to:
 * weather spells last minutes again at any world age, and the share of wet weather stays the same.
 */
@Mixin(value = WeatherRegions.class, remap = false)
public abstract class WindsSteadyDriftMixin {
    @ModifyArg(
        method = {"sample", "sampleCloudProperties"},
        at = @At(value = "INVOKE", target = "Lcom/enzonic/aeronauticswinds/util/Noise3D;fbm(DDDIDD)D"),
        index = 2)
    private double create_galore_bridge$steadyDrift(double time) {
        return 0.0;
    }
}
