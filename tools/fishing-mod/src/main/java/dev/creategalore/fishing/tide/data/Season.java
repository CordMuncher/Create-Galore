/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * adapted for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** The four seasons as fish data writes them. Read from Serene Seasons when it is installed. */
public enum Season implements StringRepresentable {
    SPRING("spring"),
    SUMMER("summer"),
    FALL("fall"),
    WINTER("winter");

    public static final Codec<Season> CODEC = StringRepresentable.fromEnum(Season::values);

    private final String id;

    Season(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Whether a season mod is running; without one, season conditions always pass. */
    public static boolean seasonsActive() {
        return ModList.get().isLoaded("sereneseasons");
    }

    /** The season in {@code level}, or spring without a season mod. */
    public static Season of(Level level) {
        if (!seasonsActive()) return SPRING;
        try {
            return SereneSeasons.season(level);
        } catch (RuntimeException | LinkageError e) {
            return SPRING;
        }
    }

    /** Kept in its own class so Serene Seasons' classes only load when the mod is there. */
    private static final class SereneSeasons {
        static Season season(Level level) {
            return switch (sereneseasons.api.season.SeasonHelper.getSeasonState(level).getSeason()) {
                case SPRING -> SPRING;
                case SUMMER -> SUMMER;
                case AUTUMN -> FALL;
                case WINTER -> WINTER;
            };
        }
    }
}
