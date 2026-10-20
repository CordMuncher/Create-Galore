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

public record KeyedLootTableRef(ResourceKey<LootTable> key) implements LootTableRef {
    @Override
    public ResourceKey<LootTable> getKey() {
        return this.key;
    }

    @Override
    public ResourceLocation getId() {
        return this.key.location();
    }

    @Override
    public LootTable getTable(MinecraftServer server) {
        return server.reloadableRegistries().getLootTable(this.key);
    }
}
