/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.screens.journal.components;
import dev.creategalore.fishing.fish.CatchInfo;
import com.google.common.collect.ImmutableList;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.client.gui.screens.journal.ProfileComponent;
import dev.creategalore.fishing.tide.data.player.CatchTimestamp;
import dev.creategalore.fishing.tide.data.player.FishStats;
import dev.creategalore.fishing.tide.util.TideUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

public class StatsComponent extends ProfileComponent {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);

    private final List<Component> lines;

    public StatsComponent(FishStats stats) {
        ImmutableList.Builder<Component> builder = ImmutableList.builder();
        // add total caught
        builder.add(Component.translatable("journal.info.stats.total", stats.getAmountCaught()));

        if (!stats.isEmpty()) {
            // add timestamp
            if (stats.getInitialCatchDate().isPresent()) {
                CatchTimestamp timestamp = stats.getInitialCatchDate().get();
                Component formatted;

                if (Tide.CLIENT_CONFIG.journal.useRealDate) {
                    Instant instant = timestamp.date();
                    ZonedDateTime localTime = instant.atZone(ZoneId.systemDefault());
                    formatted = Component.literal(localTime.format(DATE_FORMAT));
                }
                else formatted = Component.translatable("journal.info.stats.day", (int)(timestamp.ticks() / 24000L));

                builder.add(Component.translatable("journal.info.stats.first", formatted));
            }

            // add largest/smallest catch
            if (stats.getLargestCatch() > 0.0) {
                builder.add(Component.translatable("journal.info.stats.largest",
                        TideUtils.getFormattedLength(stats.getLargestCatch())));
                builder.add(Component.translatable("journal.info.stats.smallest",
                        TideUtils.getFormattedLength(stats.getSmallestCatch())));
            }
            if (stats.getHeaviestCatch() > 0.0) {
                builder.add(Component.translatable("journal.info.stats.heaviest", CatchInfo.weightText(stats.getHeaviestCatch())));
            }
        }
        this.lines = builder.build();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick) {
        int center = x + AREA_WIDTH / 2;
        int cursorY = 0;
        for (Component line : lines) {
            graphics.drawString(font, line, center - font.width(line) / 2, y + cursorY, TEXT_COLOR, false);
            cursorY += 11;
        }
    }

    @Override
    public int getRequiredHeight() {
        return lines.size() * 11;
    }
}
