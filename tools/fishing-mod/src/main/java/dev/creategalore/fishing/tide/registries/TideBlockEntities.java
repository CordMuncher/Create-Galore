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
import dev.creategalore.fishing.tide.registries.blocks.entities.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;

public class
TideBlockEntities {
    public static final HashMap<String, BlockEntityType<?>> BLOCK_ENTITIES = new HashMap<>();

    public static final BlockEntityType<? extends LootCrateBlockEntity> WOODEN_CRATE =
            register("wooden_crate", BlockEntityType.Builder.of(WoodenCrateBlockEntity::new,
                    TideBlocks.WOODEN_CRATE).build(null));
    public static final BlockEntityType<? extends LootCrateBlockEntity> OBSIDIAN_CRATE =
            register("obsidian_crate", BlockEntityType.Builder.of(ObsidianCrateBlockEntity::new,
                    TideBlocks.OBSIDIAN_CRATE).build(null));
    public static final BlockEntityType<? extends LootCrateBlockEntity> PURPUR_CRATE =
            register("purpur_crate", BlockEntityType.Builder.of(PurpurCrateBlockEntity::new,
                    TideBlocks.PURPUR_CRATE).build(null));

    public static final BlockEntityType<FishDisplayBlockEntity> FISH_DISPLAY =
            register("fish_display", BlockEntityType.Builder.of(FishDisplayBlockEntity::new,
                    TideBlocks.FISH_DISPLAY).build(null));

    public static <T extends BlockEntity> BlockEntityType<T> register(String key, BlockEntityType<T> block) {
        BLOCK_ENTITIES.put(key, block);
        return Tide.PLATFORM.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Tide.resource(key), block);
    }

    public static void init() {}
}
