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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;

public class TideSoundEvents {
    public static final HashMap<String, SoundEvent> SOUND_EVENTS = new HashMap<>();

    public static final SoundEvent JOURNAL_OPEN = register("journal_open");
    public static final SoundEvent JOURNAL_CLOSE = register("journal_close");
    public static final SoundEvent PAGE_FLIP = register("page_flip");
    public static final SoundEvent FISHING_REEL = register("fishing_reel");

    public static SoundEvent register(String key) {
        SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(Tide.resource(key));
        SOUND_EVENTS.put(key, soundEvent);
        return Tide.PLATFORM.register(BuiltInRegistries.SOUND_EVENT, Tide.resource(key), soundEvent);
    }

    public static void init() {}
}
