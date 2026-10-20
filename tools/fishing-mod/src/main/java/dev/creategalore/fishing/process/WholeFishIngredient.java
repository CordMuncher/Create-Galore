package dev.creategalore.fishing.process;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * A fish small enough to use whole: whatever {@code base} matches, unless it is known to weigh
 * {@link #MAX_KG} or more. The pack's fish cooking recipes (furnace, smoker, campfire, and through them Create's fan)
 * use it, so a big catch has to go over the Cleaning Table first. A fish with no weight on it (bought, looted,
 * or a small catch that stacks by size class) always counts as small.
 *
 * <pre>{"type": "create_galore_fishing:whole_fish", "base": {"item": "minecraft:cod"}}</pre>
 */
public record WholeFishIngredient(Ingredient base) implements ICustomIngredient {
    public static final double MAX_KG = CatchInfo.EXACT_FROM_KG;

    public static final MapCodec<WholeFishIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("base").forGetter(WholeFishIngredient::base)
    ).apply(i, WholeFishIngredient::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WholeFishIngredient> STREAM_CODEC =
            Ingredient.CONTENTS_STREAM_CODEC.map(WholeFishIngredient::new, WholeFishIngredient::base);

    public static final IngredientType<WholeFishIngredient> TYPE = new IngredientType<>(CODEC, STREAM_CODEC);

    /** False for a fish known to be too big to cook or press whole. */
    public static boolean small(ItemStack stack) {
        return CatchInfo.read(stack).flatMap(CatchInfo::kg).map(kg -> kg < MAX_KG).orElse(true);
    }

    @Override
    public boolean test(ItemStack stack) {
        return base.test(stack) && small(stack);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Arrays.stream(base.getItems());
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return FishingRegistry.WHOLE_FISH.get();
    }
}
