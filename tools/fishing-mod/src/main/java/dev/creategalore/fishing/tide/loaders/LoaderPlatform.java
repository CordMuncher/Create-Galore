/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.loaders;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public interface LoaderPlatform {
    String getPlatformName();

    default String getMCVersion() {
        return "1.21.1";

    }

    boolean isModLoaded(String modId);

    boolean isDevEnvironment();

    CompoundTag getPlayerData(ServerPlayer player);

    <T> Registry<T> createRegistry(ResourceKey<Registry<T>> registryKey);

    <T> T register(Registry<? super T> registry, ResourceLocation id, T toRegister);
    <T> T register(Registry<? super T> registry, ResourceKey<T> id, T toRegister);
    <T> Holder<T> registerForHolder(Registry<T> registry, ResourceLocation id, T toRegister);

    <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuType.MenuSupplier<T> menuSupplier, FeatureFlagSet flags);
}
