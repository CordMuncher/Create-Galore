package dev.creategalore.bridge;

import dev.hybridlabs.hapi.entity.base.aquatic.BaseWaterAnimal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Garnished's voltaic sea grass shocks anything moving through it (2 lightning damage per hit for mobs). It grows in
 * beds on the sea floor, and fish schools swim straight into it: whole schools of herring died within seconds and
 * floated up. Sea life (fish, squid, dolphins, turtles, axolotls, frogs and every Hybrid Aquatic animal that is not
 * a monster) no longer takes that shock. Players, drowned, guardians and land mobs that fall in are still
 * shocked, and lightning from the sky still hurts sea life away from the grass.
 */
public final class VoltaicSeaGrass {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("garnished", "voltaic_sea_grass");
    private static final boolean HAPI = ModList.get().isLoaded("hapi");
    private static Block grass;

    private VoltaicSeaGrass() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("garnished")) return;
        NeoForge.EVENT_BUS.addListener(VoltaicSeaGrass::onDamage);
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_LIGHTNING) || event.getSource().getEntity() != null) return;
        LivingEntity victim = event.getEntity();
        if (!isSeaLife(victim)) return;
        if (grass == null) grass = BuiltInRegistries.BLOCK.getOptional(ID).orElse(Blocks.AIR);
        // The shock comes from the block the mob is touching, as Garnished's entityInside sees it.
        if (victim.level().getBlockStates(victim.getBoundingBox().deflate(1.0E-7)).anyMatch(s -> s.is(grass))) {
            event.setCanceled(true);
        }
    }

    // Hybrid Aquatic's animals breathe through their own air code and spawn in their own mob categories.
    private static boolean isSeaLife(LivingEntity entity) {
        if (entity instanceof Player || entity instanceof Enemy) return false;
        MobCategory category = entity.getType().getCategory();
        return entity.canBreatheUnderwater() || category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS
                || HAPI && entity instanceof BaseWaterAnimal;
    }
}
