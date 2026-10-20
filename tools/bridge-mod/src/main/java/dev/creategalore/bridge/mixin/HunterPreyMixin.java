package dev.creategalore.bridge.mixin;

import dev.hybridlabs.hapi.entity.ai.MobTargetConfiguration;
import kotlin.jvm.functions.Function1;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hybrid Aquatic's otters hunt every crab in Hybrid API's otter_prey tag, hermit crabs included, and pick a new one
 * each time their hunger runs low (about every 3 minutes). Hermit crabs share the sandy beaches with them, have 3
 * health and die to a single bite, and the crustacean spawn cap only allows a handful near a player, so a pair of
 * otters keeps a beach empty of them. Many sea hunters (octopus, cuttlefish, wrasse, stingrays...) also hunt all
 * crustaceans. Every one of them picks its prey through this one check, so creatures in the
 * create_galore_bridge:never_hunted tag (hermit crabs) are never chosen as prey. Hunters still eat every other crab,
 * shrimp and fish as before, and the tags themselves stay as they are (a fishing net still catches hermit crabs).
 */
@Mixin(value = MobTargetConfiguration.class, remap = false)
public abstract class HunterPreyMixin {
    @Unique
    private static final TagKey<EntityType<?>> create_galore_bridge$NEVER_HUNTED =
        TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "never_hunted"));

    @Inject(method = "addAttackTarget$lambda$1", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$spare(MobTargetConfiguration config, Function1<?, ?> hunger, Mob hunter,
                                                   int hungerLimit, LivingEntity prey, CallbackInfoReturnable<Boolean> cir) {
        if (prey.getType().is(create_galore_bridge$NEVER_HUNTED)) cir.setReturnValue(false);
    }
}
