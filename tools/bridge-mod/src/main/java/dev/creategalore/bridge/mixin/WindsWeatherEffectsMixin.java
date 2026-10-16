package dev.creategalore.bridge.mixin;

import com.enzonic.aeronauticswinds.WindsSim;
import com.enzonic.aeronauticswinds.weather.WeatherBlockEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every 5 s Aeronautics Winds places snow, sand, ice and puddles in random columns up to 32 blocks around each player,
 * reading the surface height with Level.getHeight. On a chunk that isn't loaded yet that call generates it on the
 * server thread and waits, so a player flying fast into new terrain (worse while Chunksmith pregenerates) froze the
 * server for up to 20 s. Skip the round unless the whole area is already loaded; it runs again 5 s later.
 */
@Mixin(value = WeatherBlockEffects.class, remap = false)
public abstract class WindsWeatherEffectsMixin {
    @Inject(method = "processPlayerArea", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$onlyLoadedChunks(ServerLevel level, WindsSim.Simulation sim, ServerPlayer player, CallbackInfo ci) {
        int px = player.blockPosition().getX();
        int pz = player.blockPosition().getZ();
        for (int cx = (px - 32) >> 4; cx <= (px + 31) >> 4; cx++)
            for (int cz = (pz - 32) >> 4; cz <= (pz + 31) >> 4; cz++)
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    ci.cancel();
                    return;
                }
    }
}
