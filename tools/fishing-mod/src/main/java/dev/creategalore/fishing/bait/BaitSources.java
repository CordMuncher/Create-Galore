package dev.creategalore.fishing.bait;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Bait found in the world: hoeing grass or dirt now and then turns up a worm (Aquaculture's), so a new player has
 * bait before a worm farm or a washing fan is running. The Create recipes for bait are data
 * ({@code data/create_galore_fishing/recipe/}).
 */
public final class BaitSources {
    private BaitSources() {
    }

    static final double WORM_WHEN_HOEING = 0.05;
    private static final ResourceLocation WORM = ResourceLocation.fromNamespaceAndPath("aquaculture", "worm");

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BaitSources::onTill);
    }

    private static void onTill(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || event.getItemAbility() != ItemAbilities.HOE_TILL || event.getFinalState() == null) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().is(BlockTags.DIRT)) return;
        if (level.random.nextDouble() >= WORM_WHEN_HOEING) return;
        BuiltInRegistries.ITEM.getOptional(WORM).ifPresent(worm ->
                Block.popResource(level, event.getPos().above(), new ItemStack(worm)));
    }
}
