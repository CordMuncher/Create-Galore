/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.client.gui.TideToasts;
import dev.creategalore.fishing.tide.client.gui.screens.FishyNoteScreen;
import dev.creategalore.fishing.tide.client.gui.screens.journal.FishingJournal;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.entity.player.Player;

public class TideClientHelper {
    public static void openJournalScreen() {
        openJournalScreen(null);
    }

    public static void openJournalScreen(Item turnTo) {
        if (Minecraft.getInstance().player == null) return;
        FishingJournal screen = new FishingJournal(Minecraft.getInstance().player);
        Minecraft.getInstance().setScreen(screen);
    }

    public static void showToast(Component title, Component description, ItemStack display) {
        if (Tide.SERVER_CONFIG.journal.showToasts) TideToasts.display(new TideToasts.NewPageToast(title, description, display));
    }

    public static void openNoteScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new FishyNoteScreen(stack));
    }

}
