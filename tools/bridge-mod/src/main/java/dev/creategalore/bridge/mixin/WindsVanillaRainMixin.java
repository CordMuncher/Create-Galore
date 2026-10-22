package dev.creategalore.bridge.mixin;

import com.enzonic.aeronauticswinds.weather.VanillaWeatherSync;
import com.enzonic.aeronauticswinds.weather.WeatherType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every 3 s Aeronautics Winds copies the weather of the wettest region any player stands in onto the overworld's
 * vanilla rain, and it does that both ways: with nobody in a wet region it switched rain off. So whenever the normal
 * weather cycle (or Serene Seasons, or /weather rain) started rain, it was stopped again within 3 s and the next try
 * came 5 minutes later: rain for a second or two, then nothing. Its own rain also had gaps, because it only topped up
 * the rain timer when under 20 ticks were left, checking every 60, so the timer often ran out between checks.
 * Let Winds start and keep up rain and thunder while a player is in a wet or stormy region, keeping at least its
 * 10-15 s timer so it never lapses between checks, and never end weather it didn't start: the normal cycle ends that,
 * and Winds' own rain runs out by itself a few seconds after the region dries. /weather clear still wins.
 * Only real rain, storms and snow start rain. Drizzle and light rain are three quarters of Winds' wet weather (about
 * 16% of the time at any spot) and each one turned on full rain across the whole world, so with two players it rained
 * 35-50% of the time; they now stay clouds. Sandstorms no longer start rain either.
 */
@Mixin(value = VanillaWeatherSync.class, remap = false)
public abstract class WindsVanillaRainMixin {
    @Inject(method = "setVanillaWeather", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$onlyAddWeather(ServerLevel level, WeatherType type, double intensity, CallbackInfo ci) {
        ci.cancel();
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        if (data.getClearWeatherTime() > 0) return;
        boolean rain = type == WeatherType.RAIN || type == WeatherType.STORM || type == WeatherType.THUNDERSTORM
            || type == WeatherType.MONSOON || type.isFrozen();
        boolean thunder = type == WeatherType.STORM || type == WeatherType.THUNDERSTORM
            || type == WeatherType.BLIZZARD || type == WeatherType.MONSOON;
        int hold = 200 + (int) (100.0 * intensity);
        if (rain) {
            if (!data.isRaining()) {
                data.setRaining(true);
                data.setRainTime(hold);
            } else if (data.getRainTime() < hold) {
                data.setRainTime(hold);
            }
        }
        if (thunder) {
            if (!data.isThundering()) {
                data.setThundering(true);
                data.setThunderTime(hold);
            } else if (data.getThunderTime() < hold) {
                data.setThunderTime(hold);
            }
        }
    }
}
