/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.events;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.informational.FishingInfoManager;
import dev.creategalore.fishing.tide.data.player.TidePlayerData;
import dev.creategalore.fishing.tide.registries.TideItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class TideEventHandler {
    public static void onPlayerJoinWorld(ServerPlayer player) {
        TidePlayerData playerData = TidePlayerData.getOrCreate(player);
        if (Tide.SERVER_CONFIG.journal.giveJournal && !playerData.gotJournal) {
            if (player.addItem(TideItems.FISHING_JOURNAL.getDefaultInstance()))
                playerData.gotJournal = true;
        }
        playerData.syncTo(player);
    }

    public static void endServerTick(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(FishingInfoManager::tick);
    }
}
