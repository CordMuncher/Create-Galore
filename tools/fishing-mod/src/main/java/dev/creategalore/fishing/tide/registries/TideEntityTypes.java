/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.registries.entities.misc.StarArrow;
import dev.creategalore.fishing.tide.registries.entities.misc.LootCrateEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.FallingBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TideEntityTypes {
    public static final HashMap<String, EntityType<?>> ENTITY_TYPES = new HashMap<>();
    public static final List<EntityType<?>> FISH_ENTITIES = new ArrayList<>();

    public static final EntityType<StarArrow> STAR_ARROW = register(
            "star_arrow", EntityType.Builder.<StarArrow>of(StarArrow::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4).updateInterval(20));

    public static final EntityType<FallingBlockEntity> LOOT_CRATE = register(
            "loot_crate", EntityType.Builder.of(LootCrateEntity::new, MobCategory.MISC)
                    .sized(0.98f, 0.98f).clientTrackingRange(10)
                    .updateInterval(20));

    public static <T extends Entity> EntityType<T> register(String key, EntityType.Builder<T> builder) {
        EntityType<T> type = builder.build(key);
        ENTITY_TYPES.put(key, type);
        return register(key, type);
    }

    public static <T extends Entity> EntityType<T> register(String key, EntityType<T> type) {
        return Tide.PLATFORM.register(BuiltInRegistries.ENTITY_TYPE, Tide.resource(key), type);
    }

    public static void init() {
        TideFish.ENTITY_DATA.forEach(TideEntityTypes::buildFishEntity);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Mob> void buildFishEntity(String key, TideFish.FishEntityData<T> data) {
        // construct entity type
        EntityType<T> entityType = (EntityType<T>) register(key, ENTITY_TYPES
                .computeIfAbsent(key, k -> data.createEntityBuilder().build(k)));
        if (Tide.PLATFORM.isDevEnvironment()) FISH_ENTITIES.add(entityType);

        // build and register attributes
        TideEntityAttributes.registerAttributes(entityType, data.attributesBuilder().build());
    }
}