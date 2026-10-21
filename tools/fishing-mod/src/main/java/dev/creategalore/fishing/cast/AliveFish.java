package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.sim.SizeClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A caught fish stays alive for {@link CatchHolding#ALIVE_TICKS} after the catch. The time it dies is on the item
 * ({@link FishingRegistry#ALIVE_UNTIL}): fish kept by hand while still alive, and fish a rod machine puts on a belt,
 * carry it. While it lasts, a fish item that ends up in water (dropped, off the end of a belt, out of a chute, thrown
 * by a Mechanical Arm) is let go: species with a live mob swim off as it, the rest splash away. That gives nothing (no
 * XP, no release bonus); it lets a sorting line send unwanted fish back. Once the time is up the marker is taken off
 * the item (on the ground or in a player's inventory), so the fish stacks again.
 */
public final class AliveFish {
    private AliveFish() {
    }

    /** Most mobs one dropped stack of small fish turns back into. */
    private static final int MAX_RELEASED = 8;

    public static void register() {
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Post event) -> {
            if (event.getEntity() instanceof ItemEntity item && !item.level().isClientSide) update(item);
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            Player player = event.getEntity();
            if (!player.level().isClientSide && player.tickCount % 20 == 0) stripExpired(player);
        });
    }

    /** {@code fish} marked alive until {@code until} (game time). */
    public static ItemStack mark(ItemStack fish, long until) {
        ItemStack out = fish.copy();
        out.set(FishingRegistry.ALIVE_UNTIL.get(), until);
        return out;
    }

    /** Game ticks the fish has left; 0 if it isn't marked alive or the time is up. */
    public static long ticksLeft(ItemStack stack, Level level) {
        Long until = stack.get(FishingRegistry.ALIVE_UNTIL.get());
        return until == null ? 0 : Math.max(0, until - level.getGameTime());
    }

    /**
     * Each tick for an item on the ground: an alive fish in water goes back (true), a dead one loses its marker.
     */
    public static boolean update(ItemEntity item) {
        ItemStack stack = item.getItem();
        Long until = stack.get(FishingRegistry.ALIVE_UNTIL.get());
        if (until == null) return false;
        ServerLevel level = (ServerLevel) item.level();
        if (level.getGameTime() >= until) {
            ItemStack dead = stack.copy();
            dead.remove(FishingRegistry.ALIVE_UNTIL.get());
            item.setItem(dead);
            return false;
        }
        if (!item.isInWater() && !level.getFluidState(item.blockPosition()).is(FluidTags.WATER)) return false;
        Species species = SpeciesManager.of(stack).orElse(null);
        if (species == null) return false;
        CatchInfo info = CatchInfo.read(stack).orElse(null);
        double cm = info == null ? species.size().usualLengthCm(SizeClass.COMMON)
                : info.cm().orElse(species.size().usualLengthCm(info.size()));
        BlockPos at = item.blockPosition();
        for (int i = 0; i < Math.min(stack.getCount(), MAX_RELEASED); i++) CatchHolding.swimOff(level, at, species, cm);
        item.discard();
        return true;
    }

    private static void stripExpired(Player player) {
        long now = player.level().getGameTime();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            Long until = stack.get(FishingRegistry.ALIVE_UNTIL.get());
            if (until != null && now >= until) stack.remove(FishingRegistry.ALIVE_UNTIL.get());
        }
    }
}
