/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.items;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.registries.TideBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import java.util.List;
import java.util.function.Consumer;

public class WeatherRadioItem extends BlockItem implements InformationalItem, TooltipItem {
    public WeatherRadioItem(Properties properties) {
        super(TideBlocks.WEATHER_RADIO, properties);
    }

    @Override
    public void addTooltip(ItemStack stack, Consumer<Component> tooltip) {
        Style gray = Component.empty().getStyle().withColor(ChatFormatting.GRAY);
        tooltip.accept(Component.translatable("item.tide.weather_radio.desc").setStyle(gray));
    }

    @Override
    public String getResult(ServerLevel level, ServerPlayer player) {
        ServerLevelData data = (ServerLevelData) level.getLevelData();

        int clearTime = data.getClearWeatherTime();
        int rainTime = data.getRainTime();
        int thunderTime = data.getThunderTime();

        boolean isRaining = data.isRaining();
        boolean isThundering = data.isThundering();

        FishingCondition.WeatherType next;
        int nextTicks;

        if (clearTime > 0) {
            next = FishingCondition.WeatherType.STORM;
            nextTicks = clearTime;
        }
        else if ((thunderTime < rainTime && isRaining)
                || (thunderTime == rainTime && !isRaining)) {
            next = isThundering ? FishingCondition.WeatherType.RAIN : FishingCondition.WeatherType.STORM;
            nextTicks = thunderTime;
        }
        else {
            next = isRaining ? FishingCondition.WeatherType.CLEAR : FishingCondition.WeatherType.RAIN;
            nextTicks = rainTime;
        }

        return next.ordinal() + "-" + nextTicks;
    }

    @Override
    public Component parseResult(String result) {
        String[] splits = result.split("-");
        if (splits.length != 2) return Component.literal("Couldn't get weather info...");

        FishingCondition.WeatherType next = FishingCondition.WeatherType.values()[Integer.parseInt(splits[0])];
        int nextTicks = Integer.parseInt(splits[1]);

        int seconds = Mth.positiveCeilDiv(nextTicks, 20);
        int minutes = Mth.positiveCeilDiv(seconds, 60);
        int hours = Mth.positiveCeilDiv(minutes, 60);

        Component weatherForecast = Component.translatable("journal.info.weather." + next.getSerializedName());

        Component timeForecast = Component.translatable("item.tide.weather_radio.forecast.hours", hours);
        if (minutes <= 1) timeForecast = Component.translatable("item.tide.weather_radio.forecast.seconds", seconds);
        else if (hours <= 1) timeForecast = Component.translatable("item.tide.weather_radio.forecast.minutes", minutes);

        return Component.translatable("item.tide.weather_radio.forecast", weatherForecast, timeForecast);
    }

    @Override
    public int updatePeriod() {
        return 20;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        this.addTooltip(stack, lines::add);
    }

}
