/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.items;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.util.TideUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class PocketWatchItem extends AbstractInformationalItem {
    public PocketWatchItem(Properties properties) {
        super(properties, Component.translatable("item.tide.pocket_watch.desc"));
    }

    @Override
    public String getResult(ServerLevel level, ServerPlayer player) {
        return Long.toString(TideUtils.getTimeOfDay(level));
    }

    @Override
    public Component parseResult(String result) {
        return Component.literal(TideUtils.ticksToRealTime(Long.parseLong(result), Tide.CLIENT_CONFIG.journal.useAmPm));
    }

    @Override
    public int updatePeriod() {
        return 5;
    }
}
