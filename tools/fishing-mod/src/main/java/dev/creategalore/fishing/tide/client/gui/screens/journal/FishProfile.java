/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.screens.journal;
import dev.creategalore.fishing.tide.data.Season;
import dev.creategalore.fishing.tide.data.FishingModifier;
import dev.creategalore.fishing.tide.data.FishingCondition;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.client.gui.screens.journal.components.*;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.data.journal.FishRarity;
import dev.creategalore.fishing.tide.data.player.FishStats;
import dev.creategalore.fishing.tide.data.player.TidePlayerData;
import dev.creategalore.fishing.tide.util.TideUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FishProfile implements Renderable {
    private static final ResourceLocation BORDERS = Tide.resource("textures/gui/journal/profile_borders.png");
    private static final ResourceLocation STAR = Tide.resource("textures/gui/journal/star.png");

    private static final int BG_WIDTH = FishingJournal.BG_WIDTH;
    private static final int BG_HEIGHT = FishingJournal.BG_HEIGHT;
    private static final int TEXT_COLOR = FishingJournal.TEXT_COLOR;

    private final ItemStack fish;
    private final FishData data;
    private final List<ProfileComponent> profileComponents;
    private final Font font;
    private final Component description;
    private final FishRarity rarity;
    private final Component rarityPrefix;

    public FishProfile(ItemStack fish, FishingJournal journal) {
        this.font = journal.getFont();
        this.fish = fish;
        this.data = FishData.getOrThrow(fish);

        Component description = Component.translatable(data.profile().description().orElse("journal.description.missing"));
        if (description.getString().contains("journal.description") || description.getString().isBlank()) description = Component.translatable("journal.description.missing");
        this.description = description;
        this.rarity = data.profile().rarity();
        Style defaultStyle = Component.literal("").getStyle();
        this.rarityPrefix = Component.translatable("journal.rarity.title").append(": ")
                .append(Component.translatable("journal.rarity." + rarity.getKey())
                        .withStyle(defaultStyle.withColor(rarity.getColor()))).append(" (");
        this.profileComponents = buildComponentsWithStats(fish, data);
    }

    public static ArrayList<ProfileComponent> buildComponentsWithStats(ItemStack fish, FishData data) {
        ArrayList<ProfileComponent> builder = buildComponents(data);
        if (!builder.isEmpty() && !(builder.get(builder.size() - 1) instanceof HorizontalLineComponent))
            builder.add(new HorizontalLineComponent(false));

        // add fish player stats info area
        FishStats stats = TidePlayerData.CLIENT_DATA.getDataFor(fish.getItem())
                .flatMap(d -> d.stats).orElse(new FishStats());
        builder.add(new StatsComponent(stats));

        return builder;
    }

    public static ArrayList<ProfileComponent> buildComponents(FishData data) {
        ArrayList<ProfileComponent> builder = new ArrayList<>(5);

        // add location info area
        data.profile().location().ifPresent(location -> {
            builder.add(new LocationComponent(Component.translatable(location)));
        });

        // add luck info area
        first(data.conditions(), FishingCondition.Luck.class).ifPresent(condition ->
                builder.add(new LuckComponent(condition.min(), condition.max())));

        // first line break (if applicable)
        if (!builder.isEmpty()) builder.add(new HorizontalLineComponent(false));

        // add dimensions info area
        Optional<FishingCondition.Dimensions> dimensions = first(data.conditions(), FishingCondition.Dimensions.class);
        dimensions.ifPresent(condition -> {
            if (!DimensionsComponent.shouldCreate(condition, data.conditions())) return;
            builder.add(new DimensionsComponent(condition.dimensions()));
        });

        // add seasons info area
        if (Season.seasonsActive()) {
            first(data.conditions(), FishingCondition.Seasons.class).ifPresent(condition ->
                    builder.add(new SeasonsComponent(condition.seasons())));
        }

        // add temperature info area
        data.modifiers().stream().filter(mod -> mod instanceof FishingModifier.Temperature)
                .map(mod -> (FishingModifier.Temperature) mod).findFirst().ifPresent(mod -> {
                    float minTemp = Math.max(mod.preferred() - mod.tolerance() * 0.95f, -1f);
                    float maxTemp = Math.max(mod.preferred() + mod.tolerance() * 0.95f, -1f);
                    builder.add(new TemperatureComponent(minTemp, maxTemp));
                });

        // add time info area
        first(data.conditions(), FishingCondition.TimeOfDay.class).ifPresent(condition ->
                builder.add(new TimeComponent(condition.ranges())));

        // add depth info area (only for overworld fish below the surface)
        Optional<FishingCondition.DepthRange> depthRange = first(data.conditions(), FishingCondition.DepthRange.class);
        if (depthRange.isPresent() && dimensions.map(FishingCondition.Dimensions::isOverworldOnly).orElse(false)) {
            FishingCondition.DepthRange range = depthRange.get();
            float min = range.minY() != Integer.MIN_VALUE ? DepthComponent.depthToFloat(range.minY()) : 0f;
            float max = range.maxY() != Integer.MAX_VALUE ? DepthComponent.depthToFloat(range.maxY()) : 1f;
            if (min < 0.7f || max != 1) builder.add(new DepthComponent(min, max));
        }

        // add moon phase info area
        first(data.conditions(), FishingCondition.MoonPhase.class).ifPresent(condition ->
                builder.add(new MoonPhaseComponent(condition.phases())));

        // add weather info area
        first(data.conditions(), FishingCondition.Weather.class).ifPresent(condition ->
                builder.add(new WeatherComponent(condition.types())));

        return builder;
    }

    private static <T> Optional<T> first(List<FishingCondition> conditions, Class<T> type) {
        return conditions.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // render background
        int tlX = (graphics.guiWidth() - BG_WIDTH) / 2;
        int tlY = (graphics.guiHeight() - BG_HEIGHT) / 2;
        graphics.blit(BORDERS, tlX, tlY, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);

        // render title
        int titleX = tlX + 110;
        int titleY = tlY + 30;
        Component title = TideUtils.removeRawTextInName(fish.getHoverName());
        int titleWidth = font.width(title);
        int underlineWidth = titleWidth + 6;
        graphics.drawString(font, title, titleX - titleWidth / 2, titleY, TEXT_COLOR, false);

        graphics.blitSprite(FishingJournal.LINE_BOTTOM, titleX - underlineWidth / 2,
                titleY + font.lineHeight + 2, underlineWidth, 2);

        // get fish sizing and offsets
        boolean isLarge = data.profile().altSprite().isPresent();
        ResourceLocation alternateTexture = data.profile().altSprite().orElse(null);
        int fishSize = isLarge ? data.profile().altSpriteSize().orElse(16) : 16;
        int itemCenterX = tlX + 109;
        int itemCenterY = tlY + 100;
        final int shadowOffset = 2;
        final float scale = 2f;

        // scale around the fish's center
        graphics.pose().pushPose();
        graphics.pose().translate(itemCenterX, itemCenterY, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-itemCenterX, -itemCenterY, 0);

        // offset for shadow rendering
        graphics.pose().pushPose();
        graphics.pose().translate(shadowOffset / scale, shadowOffset / scale, 0f);

        // set shadow color
        graphics.flush();
        RenderSystem.setShaderColor(0.8431f, 0.7098f, 0.5804f, 1f);

        // render fish silhouette
        if (!isLarge) FishingJournal.renderItemSilhouette(graphics, fish,
                itemCenterX - fishSize / 2, itemCenterY - fishSize / 2);
        else FishingJournal.renderTextureSilhouette(graphics, alternateTexture,
                itemCenterX - fishSize / 2, itemCenterY - fishSize / 2, fishSize, fishSize);

        // reset shader color
        graphics.flush();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // pop shadow offset
        graphics.pose().popPose();

        if (!isLarge) graphics.renderItem(fish, itemCenterX - fishSize / 2, itemCenterY - fishSize / 2);
        else graphics.blit(alternateTexture,
                itemCenterX - fishSize / 2, itemCenterY - fishSize / 2,
                0, 0, fishSize, fishSize, fishSize, fishSize);

        // pop fish scaling
        graphics.pose().popPose();

        // render rarity text
        int stars = rarity.getNumStars();
        int rarityX = tlX + 40;
        int rarityY = tlY + 167;
        graphics.drawString(font, rarityPrefix, rarityX, rarityY, TEXT_COLOR, false);
        for (int i = 0; i < stars; i++) graphics.blit(STAR,
                rarityX + font.width(rarityPrefix) + i * 8, rarityY, 0, 0,
                7, 6, 7, 6);
        graphics.drawString(font, ")", rarityX + font.width(rarityPrefix) + stars * 8, rarityY, TEXT_COLOR, false);

        // render description
        int descX = tlX + 38;
        int descY = tlY + 207;
        List<FormattedCharSequence> descriptionLines = font.split(this.description, 156);
        descY -= (descriptionLines.size() * font.lineHeight) / 2;
        for (int i = 0; i < descriptionLines.size(); i++) {
            graphics.drawString(font, descriptionLines.get(i), descX,
                    descY + i * font.lineHeight, TEXT_COLOR, false);
        }

        // render profile components
        int padding = 4;
        int cursorY = padding + 8;
        for (ProfileComponent area : profileComponents) {
            area.render(graphics, font, tlX + ProfileComponent.AREA_X,
                    tlY + ProfileComponent.AREA_Y + cursorY, mouseX, mouseY, partialTick);
            cursorY += area.getRequiredHeight() + padding;
        }
    }
}