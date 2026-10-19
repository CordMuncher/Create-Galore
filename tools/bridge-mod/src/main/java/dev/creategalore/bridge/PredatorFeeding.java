package dev.creategalore.bridge;

import dev.hybridlabs.hapi.entity.base.aquatic.BaseWaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Hybrid Aquatic's hunters (orcas and the predatory fish) only pick a new prey while their hunger is below 300, and
 * hunger only ever counts down. Sharks, squid and otters fill it up again when they make a kill; orcas and fish never
 * do, so they hunt every fish in reach without a break. Their own fish then drop nothing (Hybrid Labs skips the loot
 * of anything its animals kill), but vanilla cod, salmon, tropical fish and turtles still drop theirs, which floats
 * up and covers the sea around an orca pod. Here every Hybrid Aquatic kill fills the hunter up as a shark kill does
 * (1200 ticks, about 45 s before it hunts again), and prey it kills drops nothing, as Hybrid Labs already does for
 * its own animals. Players and monsters it kills keep their drops.
 */
public final class PredatorFeeding {
    private static final int FED = 1200;

    private PredatorFeeding() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("hapi")) return;
        NeoForge.EVENT_BUS.addListener(PredatorFeeding::onDeath);
        NeoForge.EVENT_BUS.addListener(PredatorFeeding::onDrops);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getSource().getEntity() instanceof BaseWaterAnimal hunter && hunter.getHunger() < FED) {
            hunter.setHunger(FED);
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        var victim = event.getEntity();
        if (victim instanceof Player || victim instanceof Enemy || victim instanceof BaseWaterAnimal) return;
        if (event.getSource().getDirectEntity() instanceof BaseWaterAnimal) {
            event.setCanceled(true);
        }
    }
}
