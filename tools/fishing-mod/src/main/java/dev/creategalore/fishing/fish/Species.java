package dev.creategalore.fishing.fish;
import dev.creategalore.fishing.tide.data.journal.FishRarity;
import dev.creategalore.fishing.bait.BaitKind;
import dev.creategalore.fishing.sim.FishFight;
import dev.creategalore.fishing.sim.SizeModel;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * One catchable fish species with everything resolved: Tide's data for where and when it bites, plus the fight,
 * size, diet and processing values from our extension data or {@link SpeciesDefaults}.
 *
 * @param id       the fish item's id, which is also the species id
 * @param tide     where, when and how often it bites; journal and display data
 * @param fight    how it fights
 * @param size     lengths and weights
 * @param feeding  broad feeding habit
 * @param diet     preference 0..1 per bait kind
 * @param wary     0..1, how much it shies away from visible line
 * @param fillet   fillets per kg
 * @param oily     yields fish oil when processed
 */
public record Species(ResourceLocation id, Item item, FishData tide, FishFight fight, SizeModel size,
                      Feeding feeding, Map<BaitKind, Double> diet, double wary, double fillet, boolean oily) {

    public FishRarity rarity() {
        return tide.profile().rarity();
    }

    /** "water", "lava" or "void". */
    public String medium() {
        return tide.medium();
    }

    public double preference(BaitKind kind) {
        return diet.getOrDefault(kind, 0.1);
    }
}
