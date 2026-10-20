/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.loaders.neoforge;

import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.commands.JournalCommand;
import dev.creategalore.fishing.tide.data.player.TidePlayerData;
import dev.creategalore.fishing.tide.events.TideEventHandler;
import dev.creategalore.fishing.tide.registries.TideFish;
import dev.creategalore.fishing.tide.registries.TideItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * Game events of the ported Tide content (registered with {@code NeoForge.EVENT_BUS.register(NeoforgeEventHandler.class)};
 * Tide used an annotation subscriber under its own mod id). Tide's data loading, {@code /fishing} command and vanilla
 * fishing-table capture went with its rod.
 */
public class NeoforgeEventHandler {
    @SubscribeEvent
    public static void registerCommands(final RegisterCommandsEvent event) {
        new JournalCommand(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event) {
        TideEventHandler.endServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(final PlayerEvent.PlayerLoggedInEvent event) {
        TideEventHandler.onPlayerJoinWorld((ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide()) return;
        TidePlayerData.getOrCreate((ServerPlayer) event.getOriginal())
                .syncTo((ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public static void onFurnaceFuelBurned(final FurnaceFuelBurnTimeEvent event) {
        if (event.getItemStack().is(TideItems.WOODEN_CRATE)) event.setBurnTime(300);
        if (event.getItemStack().is(TideItems.FISH_DISPLAY)) event.setBurnTime(300);
        if (event.getItemStack().is(TideItems.FISHY_NOTE)) event.setBurnTime(200);
        if (event.getItemStack().is(TideFish.INFERNO_GUPPY)) event.setBurnTime(3200);
    }

    @SubscribeEvent
    public static void modifyVillagerTrades(final VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.FISHERMAN) return;
        event.getTrades().get(4).add((entity, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, 15),
                new ItemStack(TideItems.VILLAGE_FISHING_ROD, 1),
                1, 15, 0.05f
        ));
    }
}
