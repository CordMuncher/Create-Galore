/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.loaders.neoforge;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.TideCriteriaTriggers;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import dev.creategalore.fishing.tide.data.TideRecipeSerializers;
import dev.creategalore.fishing.tide.data.loot.modifiers.TideLootModifiers;
import dev.creategalore.fishing.tide.network.TideMessages;
import dev.creategalore.fishing.tide.registries.*;
import dev.creategalore.fishing.tide.registries.entities.TideSpawnConfigs;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers the ported Tide content under the {@code tide} namespace. Created from our mod's constructor (Tide had its
 * own {@code @Mod}); its deferred registers, payloads, spawn placements and attributes hang off our mod bus.
 */
public class NeoforgeEntrypoint {
    public static final PayloadRegistrar REGISTRAR = new PayloadRegistrar("1");
    public static ModContainer CONTAINER;

    public NeoforgeEntrypoint(IEventBus eventBus, ModContainer container) {
        eventBus.register(this);

        Tide.initialize();

        REGISTER_MAP.values().forEach(dr -> dr.register(eventBus));
        CONTAINER = container;
        NeoForge.EVENT_BUS.register(NeoforgeEventHandler.class);
    }

    private static Map<Registry<?>, DeferredRegister<?>> REGISTER_MAP;
    static {
        createDeferredRegister(BuiltInRegistries.ITEM);
        createDeferredRegister(BuiltInRegistries.BLOCK);
        createDeferredRegister(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        createDeferredRegister(BuiltInRegistries.ENTITY_TYPE);
        createDeferredRegister(BuiltInRegistries.TRIGGER_TYPES);
        createDeferredRegister(BuiltInRegistries.DATA_COMPONENT_TYPE);
        createDeferredRegister(BuiltInRegistries.SOUND_EVENT);
        createDeferredRegister(BuiltInRegistries.PARTICLE_TYPE);
        createDeferredRegister(BuiltInRegistries.LOOT_FUNCTION_TYPE);
        createDeferredRegister(BuiltInRegistries.RECIPE_SERIALIZER);
        createDeferredRegister(BuiltInRegistries.ARMOR_MATERIAL);

        REGISTER_MAP.put(
                NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                TideLootModifiers.GLOBAL_LOOT_MODIFIER_SERIALIZERS
        );
    }

    private static <T> void createDeferredRegister(Registry<T> registry) {
        DeferredRegister<T> register = DeferredRegister.create(registry, Tide.MOD_ID);
        if (REGISTER_MAP == null) REGISTER_MAP = new HashMap<>();
        REGISTER_MAP.put(registry, register);
    }

    @SuppressWarnings("unchecked")
    public static <T> T registerSomething(Registry<? super T> registry, String id, T toRegister) {
        ((DeferredRegister<T>) REGISTER_MAP.get(registry)).register(id, () -> toRegister);
        return toRegister;
    }

    @SuppressWarnings("unchecked")
    public static <T> Holder<T> registerSomethingForHolder(Registry<? super T> registry, String id, T toRegister) {
        DeferredRegister<T> defReg = (DeferredRegister<T>) REGISTER_MAP.get(registry);
        return defReg.register(id, () -> toRegister);
    }

    @SubscribeEvent
    public void onRegister(final RegisterEvent event) {
        event.register(Registries.ITEM, helper -> TideItems.init());
        event.register(Registries.BLOCK, helper -> TideBlocks.init());
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> TideBlockEntities.init());
        event.register(Registries.ENTITY_TYPE, helper -> TideEntityTypes.init());
        event.register(Registries.SOUND_EVENT, helper -> TideSoundEvents.init());
        event.register(Registries.PARTICLE_TYPE, helper -> TideParticleTypes.init());
        event.register(Registries.TRIGGER_TYPE, helper -> TideCriteriaTriggers.init());
        event.register(Registries.DATA_COMPONENT_TYPE, helper -> TideDataComponents.init());
        event.register(Registries.LOOT_FUNCTION_TYPE, helper -> TideLootFunctions.init());
        event.register(Registries.RECIPE_SERIALIZER, helper -> TideRecipeSerializers.init());
        event.register(Registries.ARMOR_MATERIAL, helper -> TideArmorMaterials.init());

        event.register(Registries.CREATIVE_MODE_TAB, helper -> Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB, Tide.resource("tide"),
                TideItems.getCreativeTab(CreativeModeTab.builder()).build()));
    }

    @SubscribeEvent
    public void registerPayloads(final RegisterPayloadHandlersEvent event) {
        TideMessages.init(Tide.NETWORK);
    }

    @SubscribeEvent
    public void registerSpawnPlacements(final RegisterSpawnPlacementsEvent event) {
        TideSpawnConfigs.onRegisterSpawnConfigs(config -> event.register(
                config.entityType(), config.placementType(),
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, config.spawnPredicate(),
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        ));
    }

    @SubscribeEvent
    public void registerEntityAttributes(final EntityAttributeCreationEvent event) {
        TideEntityAttributes.REGISTRY.forEach(reg -> event.put(reg.entityType(), reg.attributes()));
    }
}

