/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.Level;

import java.time.Instant;

public record CatchTimestamp(Instant date, long ticks) {
    public static final Codec<CatchTimestamp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("date").xmap(Instant::parse, Instant::toString).forGetter(CatchTimestamp::date),
            Codec.LONG.fieldOf("ticks").forGetter(CatchTimestamp::ticks)
    ).apply(instance, CatchTimestamp::new));

    public static CatchTimestamp now(Level level) {
        return new CatchTimestamp(Instant.now(), level.getDayTime());
    }
}
