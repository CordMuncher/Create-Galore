package dev.creategalore.fishing.compat;

import com.forsteri.createliquidfuel.core.LiquidFuel;
import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;

/**
 * Which fluids the Fuel Reel Drive burns, and for how long: anything in Create: Liquid Fuel's fuel map (lava, seed
 * oil, TFMG's fuels, our fish oil and whatever the pack adds), at the rate that mod gives a Blaze Burner. Without that
 * mod, lava and fish oil only.
 */
public final class LiquidFuels {
    private LiquidFuels() {
    }

    public static final String MOD = "createliquidfuel";

    /** Engine ticks at full load that one mB of {@code fluid} gives; 0 if it isn't a fuel. */
    public static double ticksPerMb(Fluid fluid) {
        if (ModList.get().isLoaded(MOD)) return FromMod.ticksPerMb(fluid);
        if (fluid.isSame(Fluids.LAVA)) return 20;
        if (fluid.isSame(FishingRegistry.FISH_OIL.get())) return 5;
        return 0;
    }

    /** Only loaded when Create: Liquid Fuel is. */
    private static final class FromMod {
        static double ticksPerMb(Fluid fluid) {
            LiquidFuel fuel = com.forsteri.createliquidfuel.core.LiquidFuels.get(fluid);
            return fuel == null ? 0 : fuel.burnTime() / (double) Math.max(1, fuel.amountConsumedPerTick());
        }
    }
}
