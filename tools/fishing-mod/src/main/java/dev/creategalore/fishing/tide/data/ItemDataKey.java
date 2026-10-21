/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public interface ItemDataKey<T> {
    T get(ItemStack stack);
    T getOrDefault(ItemStack stack, T defaultValue);
    Optional<T> getOptional(ItemStack stack);
    void set(ItemStack stack, T value);
    void remove(ItemStack stack);
    boolean isPresent(ItemStack stack);
}
