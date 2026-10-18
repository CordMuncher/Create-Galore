package dev.creategalore.bridge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Cataclysm's Nature Heal attribute (Vitality Ankh, the crab chestplate) speeds up natural regeneration by wrapping
 * the two Player.heal calls in FoodData.tick, but Thirst Was Reclaimed redirects both calls and heals a fixed amount,
 * so the bonus never arrived. The FoodData mixin marks the player while their food ticks; heals during that window get
 * the Nature Heal multiplier here instead (1 + value / 100, never below 0, as Cataclysm does).
 */
public final class NatureHeal {
    private static final ResourceLocation NATURE_HEAL = ResourceLocation.fromNamespaceAndPath("cataclysm", "nature_heal");
    private static final ThreadLocal<Player> FOOD_TICK = new ThreadLocal<>();

    private NatureHeal() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NatureHeal::onHeal);
    }

    public static void startFoodTick(Player player) {
        FOOD_TICK.set(player);
    }

    public static void endFoodTick() {
        FOOD_TICK.remove();
    }

    private static void onHeal(LivingHealEvent event) {
        if (FOOD_TICK.get() != event.getEntity()) return;
        var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(NATURE_HEAL).orElse(null);
        var instance = attribute == null ? null : event.getEntity().getAttribute(attribute);
        if (instance == null) return;
        event.setAmount(event.getAmount() * Math.max(0f, 1f + (float) instance.getValue() / 100f));
    }
}
