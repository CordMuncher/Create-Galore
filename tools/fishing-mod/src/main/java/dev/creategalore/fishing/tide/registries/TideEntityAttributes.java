/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.ArrayList;

public class TideEntityAttributes {
    public static final ArrayList<Registration<?>> REGISTRY = new ArrayList<>();

    public record Registration<T extends LivingEntity>(EntityType<T> entityType, AttributeSupplier attributes) {}

    public static void init() {}

    public static <T extends LivingEntity> void registerAttributes(EntityType<T> entityType, AttributeSupplier attributes) {
        REGISTRY.add(new Registration<>(entityType, attributes));
    }
}
