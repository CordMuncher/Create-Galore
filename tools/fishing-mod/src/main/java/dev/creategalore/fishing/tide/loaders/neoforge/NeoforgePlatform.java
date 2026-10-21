/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.loaders.neoforge;

import dev.creategalore.fishing.tide.loaders.LoaderPlatform;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.registries.RegistryBuilder;

public class NeoforgePlatform implements LoaderPlatform {
    @Override
    public String getPlatformName() {
        return "Neoforge";
    }

    @Override
    public String getMCVersion() {

        return "1.21.1";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public CompoundTag getPlayerData(ServerPlayer player) {
        return player.getPersistentData();
    }

    @Override
    public <T> Registry<T> createRegistry(ResourceKey<Registry<T>> registryKey) {
        return new RegistryBuilder<>(registryKey).sync(true).create();
    }

    @Override
    public <T> T register(Registry<? super T> registry, ResourceLocation id, T toRegister) {
        return NeoforgeEntrypoint.registerSomething(registry, id.getPath(), toRegister);
    }

    @Override
    public <T> T register(Registry<? super T> registry, ResourceKey<T> id, T toRegister) {
        return NeoforgeEntrypoint.registerSomething(registry, id.location().getPath(), toRegister);
    }

    @Override
    public <T> Holder<T> registerForHolder(Registry<T> registry, ResourceLocation id, T toRegister) {
        return NeoforgeEntrypoint.registerSomethingForHolder(registry, id.getPath(), toRegister);
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> createMenuType(MenuType.MenuSupplier<T> menuSupplier, FeatureFlagSet flags) {
        return new MenuType<>(menuSupplier, flags);
    }
}
