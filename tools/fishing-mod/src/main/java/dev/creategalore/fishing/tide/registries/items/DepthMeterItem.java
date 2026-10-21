/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class DepthMeterItem extends AbstractInformationalItem {
    public DepthMeterItem(Properties properties) {
        super(properties, Component.translatable("item.tide.depth_meter.desc"));
    }

    @Override
    public String getResult(ServerLevel level, ServerPlayer player) {
        return Integer.toString(level.getSeaLevel() - player.blockPosition().getY());
    }

    @Override
    public Component parseResult(String result) {
        int depth = Integer.parseInt(result);
        char addition = depth > 0 ? '-' : '+';
        return Component.literal(addition + result.replace("-", "") + " m");
    }

    @Override
    public int updatePeriod() {
        return 5;
    }
}
