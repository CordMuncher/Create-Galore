package dev.creategalore.bridge.grid;

import com.enzonic.aeronauticswinds.WindsSim;
import com.enzonic.aeronauticswinds.weather.WeatherRegions;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Reads Winds & Weather's weather for the weather map ({@link WeatherMap}). Only loaded when the mod is there.
 *
 * What it reads is the mod's cloud field: the looks the mod gives its own clouds at a place, which follow its weather
 * regions smoothly (a region set by command included), where its weather types are steps. The game's own rain is left
 * out of it (the mod greys all its clouds while that falls); a pack has that as rainStrength.
 */
final class WeatherMapWinds {
    /** The mod's cloud amount runs from this (its clearest sky) over this span (to its heaviest thunderstorm). */
    private static final float AMOUNT_LOW = -0.35f;
    private static final float AMOUNT_SPAN = 1.25f;

    private WeatherMapWinds() {
    }

    /** The level's weather regions, or null while the mod has them switched off. */
    static Object regions(ClientLevel level) {
        WindsSim.Simulation sim = WindsSim.forLevel(level);
        WeatherRegions weather = sim == null ? null : sim.weather;
        return weather != null && weather.isEnabled() ? weather : null;
    }

    /** The word of the map (without its filled bit) for the block column x, z at the game time (ticks). */
    static int word(Object regions, int x, int z, double time) {
        WeatherRegions.CloudVisualProperties p = ((WeatherRegions) regions).sampleCloudProperties(null, x, z, time, 0f);
        int amount = unit((p.coverageBias() - AMOUNT_LOW) / AMOUNT_SPAN, 4095);
        int dark = unit(1f - (p.r() + p.g() + p.b()) / 3f, 127);
        int heaped = unit(p.puffiness() / 1.27f, 127);
        int thick = unit(p.thickness() / 62f, 31);
        return amount | dark << 12 | heaped << 19 | thick << 26;
    }

    private static int unit(float v, int top) {
        return Math.round(Math.max(0f, Math.min(1f, v)) * top);
    }
}
