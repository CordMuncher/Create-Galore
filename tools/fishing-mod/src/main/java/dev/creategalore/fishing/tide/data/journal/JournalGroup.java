/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.journal;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

// TODO: make this data-driven
public enum JournalGroup implements StringRepresentable {
    FRESHWATER("freshwater"),
    MISC("misc"),
    SALTWATER("saltwater"),
    UNDERGROUND("underground"),
    LAVA("lava"),
    VOID("void");

    public static final Codec<JournalGroup> CODEC = StringRepresentable.fromEnumWithMapping(
            JournalGroup::values, name -> name.toLowerCase(Locale.ROOT));

    private final String key;

    JournalGroup(String key) {
        this.key = key;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}
