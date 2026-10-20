/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client.gui.screens.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public abstract class ProfileComponent {
    public static final int AREA_X = 203;
    public static final int AREA_Y = 24;
    public static final int AREA_WIDTH = 174;
    protected static final int TEXT_COLOR = FishingJournal.TEXT_COLOR;
    protected static final ResourceLocation LINE_TOP = FishingJournal.LINE_TOP;
    protected static final ResourceLocation LINE_BOTTOM = FishingJournal.LINE_BOTTOM;

    public abstract void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY, float partialTick);
    public abstract int getRequiredHeight();
}
