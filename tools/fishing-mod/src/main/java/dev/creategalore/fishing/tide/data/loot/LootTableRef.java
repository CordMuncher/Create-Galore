/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.loot;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootTable;

import net.minecraft.core.registries.Registries;

public interface LootTableRef {

    default ResourceKey<LootTable> get() { return this.getKey(); }

    static LootTableRef createNew(ResourceLocation id) {
        return of(ResourceKey.create(Registries.LOOT_TABLE, id));
    }

    static LootTableRef of(ResourceKey<LootTable> key) {
        return new KeyedLootTableRef(key);
    }

    ResourceKey<LootTable> getKey();
    ResourceLocation getId();
    LootTable getTable(MinecraftServer server);
}
