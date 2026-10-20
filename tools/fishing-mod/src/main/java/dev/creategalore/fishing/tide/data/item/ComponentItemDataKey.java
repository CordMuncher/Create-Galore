/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.item;

import dev.creategalore.fishing.tide.data.ItemDataKey;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record ComponentItemDataKey<T>(DataComponentType<T> component) implements ItemDataKey<T> {
    @Override
    public T get(ItemStack stack) {
        return stack.get(component);
    }

    @Override
    public T getOrDefault(ItemStack stack, T defaultValue) {
        return stack.getOrDefault(component, defaultValue);
    }

    @Override
    public Optional<T> getOptional(ItemStack stack) {
        return Optional.ofNullable(this.get(stack));
    }

    @Override
    public void set(ItemStack stack, T value) {
        stack.set(component, value);
    }

    @Override
    public void remove(ItemStack stack) {
        stack.remove(component);
    }

    @Override
    public boolean isPresent(ItemStack stack) {
        return stack.has(component);
    }
}
