/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import dev.creategalore.fishing.tide.Tide;

import java.util.Arrays;
import java.util.List;

public interface ModAssociatedEntry extends ValidatableDataEntry {
    List<String> associatedMods();

    @Override
    default boolean isValid() {
        if (this.associatedMods().isEmpty()) return true;
        return this.associatedMods().stream().allMatch(id -> Tide.PLATFORM.isModLoaded(id)
                || Tide.PLATFORM.isModLoaded(id.replace("-", "_")));
    }

    @Override
    default String invalidReason() {
        return "one or more mods from association list '" + Arrays.toString(this.associatedMods().toArray()) + "' are not loaded";
    }
}
