/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.journal;

import dev.creategalore.fishing.tide.client.gui.screens.journal.FishingJournal;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum FishRarity implements StringRepresentable {
    COMMON("common", FishingJournal.TEXT_COLOR),
    UNCOMMON("uncommon", 0x75a743),
    RARE("rare", 0x3c5e8b),
    VERY_RARE("very_rare", 0x7a367b),
    LEGENDARY("legendary", 0xde9e41);

    public static final Codec<FishRarity> CODEC = StringRepresentable.fromEnumWithMapping(
            FishRarity::values, name -> name.toLowerCase(Locale.ROOT));

    private final String key;
    private final int color;

    FishRarity(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public String getKey() {
        return this.key;
    }

    public int getColor() {
        return this.color;
    }

    public int getNumStars() {
        return this.ordinal() + 1;
    }

    @Override
    public String getSerializedName() {
        return this.getKey();
    }
}
