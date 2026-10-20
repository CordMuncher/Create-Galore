/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.data.commands;
import dev.creategalore.fishing.tide.data.fishing.FishData;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.data.player.TidePlayerData;
import dev.creategalore.fishing.tide.util.TideUtils;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class JournalCommand {
    public JournalCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("journal").requires((stack) -> stack.hasPermission(2))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(Commands.literal("unlock")
                        .then(Commands.literal("all")
                                .executes(ctx -> unlockAll(ctx.getSource())))
                        .then(Commands.argument("fish", ItemArgument.item(buildContext))
                                .executes(ctx -> unlock(ctx.getSource(), ItemArgument.getItem(ctx, "fish").getItem())))));
    }

    private int unlock(CommandSourceStack source, Item fishItem) {
        ItemStack fish = fishItem.getDefaultInstance();
        if (source.getPlayer() == null) return 0;
        if (!TideUtils.isJournalFish(fish)) {
            source.sendFailure(Component.translatable("commands.journal.unlock.fail", fish.getHoverName().getString()));
            return 0;
        }
        TidePlayerData data = TidePlayerData.getOrCreate(source.getPlayer());
        boolean success = data.unlockFish(BuiltInRegistries.ITEM.wrapAsHolder(fishItem), source.getPlayer());
        if (success) {
            TideUtils.showFishToast(fish, source.getPlayer());
            data.syncTo(source.getPlayer());
            source.sendSuccess(() -> Component.translatable("commands.journal.unlock",
                    fish.getHoverName().getString(), source.getPlayer().getDisplayName()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("commands.journal.unlock.already_unlocked", fish.getDisplayName().getString()));
        return 0;
    }

    private int unlockAll(CommandSourceStack source) {
        if (source.getPlayer() == null) return 0;
        TidePlayerData data = TidePlayerData.getOrCreate(source.getPlayer());
        FishData.all().forEach(fishData ->
                data.unlockFish(fishData.fish(), source.getPlayer()));
        data.syncTo(source.getPlayer());
        source.sendSuccess(() -> Component.translatable("commands.journal.unlock_all",
                source.getPlayer().getDisplayName()), true);
        return 1;
    }

    private int reset(CommandSourceStack source) {
        if (source.getPlayer() == null) return 0;
        try {
            TidePlayerData data = new TidePlayerData();
            data.syncTo(source.getPlayer());
            source.sendSuccess(() -> Component.translatable("commands.journal.lock",
                    source.getPlayer().getDisplayName()), true);
        } catch (Exception e) {
            Tide.LOG.error("Could not reset player data: ", e);
            return 0;
        }

        return 1;
    }
}