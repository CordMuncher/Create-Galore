package dev.creategalore.fishing.compat;
import java.util.Map;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.BiteSampler;
import dev.creategalore.fishing.cast.Catches;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.FishingPlace;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import plus.dragons.createintegratedfarming.common.fishing.net.FishingNetCatchContext;
import plus.dragons.createintegratedfarming.common.fishing.net.FishingNetCatchProviders;
import plus.dragons.createintegratedfarming.common.fishing.net.FishingNetMedium;

/**
 * Create: Integrated Farming's Fishing Nets catch this mod's fish: a catch provider for water and one for lava, in
 * the place Tide's own provider had. The net picks among its providers at random for each catch, so about half its
 * catches are ours (fish of the spot, biased to small ones, see {@link BiteSampler#netCatch}) and half come from
 * vanilla's fishing loot, as with Tide. Only loaded when that mod is.
 */
public final class IntegratedFarmingNets {
    private IntegratedFarmingNets() {
    }

    public static final String MOD = "create_integrated_farming";

    public static void register() {
        FishingNetCatchProviders.register(CreateGaloreFishing.id("fishing_net"), FishingNetMedium.WATER, c -> catchAt(c, "water"));
        FishingNetCatchProviders.register(CreateGaloreFishing.id("fishing_net"), FishingNetMedium.LAVA, c -> catchAt(c, "lava"));
    }

    /** True if our provider is among the net's water providers (the self-test reads the mod's private table). */
    @SuppressWarnings("unchecked")
    public static boolean registered() {
        try {
            var field = FishingNetCatchProviders.class.getDeclaredField("PROVIDERS");
            field.setAccessible(true);
            Map<?, ? extends Map<?, ?>> providers = (Map<?, ? extends Map<?, ?>>) field.get(null);
            return providers.get(FishingNetMedium.WATER).containsKey(CreateGaloreFishing.id("fishing_net"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static List<ItemStack> catchAt(FishingNetCatchContext net, String medium) {
        int luck = Mth.floor(net.lootParams().getLuck());
        FishingContext context = FishingPlace.context(net.level(), net.origin(), net.fishingRod(), ItemStack.EMPTY, luck,
                medium, 0, false, net.openFluid(), net.random());
        return BiteSampler.netCatch(context, net.random())
                .map(bite -> List.of(Catches.fishItem(bite.species(), bite, "", net.level().getDayTime() / 24000)))
                .orElse(List.of());
    }
}
