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

public class ClimateGaugeItem extends AbstractInformationalItem {
    public ClimateGaugeItem(Properties properties) {
        super(properties,
                Component.translatable("item.tide.climate_gauge.desc_0"),
                Component.translatable("item.tide.climate_gauge.desc_1")
        );
    }

    @Override
    public String getResult(ServerLevel level, ServerPlayer player) {
        float temp = TideUtils.getTemperatureAt(player.blockPosition(), level);
        float degrees = Math.round(TideUtils.mcTempToRealTemp(temp) * 10) / 10.0f;
        return Float.toString(degrees);
    }

    @Override
    public Component parseResult(String result) {
        return Component.literal(Float.parseFloat(result) + (Tide.CLIENT_CONFIG.journal.useFahrenheit ? "°F" : "°C"));
    }

    @Override
    public int updatePeriod() {
        return 5;
    }
}
