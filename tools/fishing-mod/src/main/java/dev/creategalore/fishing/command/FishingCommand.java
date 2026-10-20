package dev.creategalore.fishing.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.creategalore.fishing.selftest.SelfTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /cgfish}: operator tools for testing and tuning the mod. */
public final class FishingCommand {
    private FishingCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cgfish")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("selftest")
                        .then(Commands.argument("suites", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("all");
                                    return SharedSuggestionProvider.suggest(SelfTest.names(), builder);
                                })
                                .executes(ctx -> selftest(ctx.getSource(), StringArgumentType.getString(ctx, "suites"))))));
    }

    private static int selftest(CommandSourceStack source, String suites) {
        int failures = SelfTest.run(source.getServer(), suites);
        source.sendSuccess(() -> Component.literal("Self-test done: " + failures
                + " failure(s). Reports are in the server folder under create_galore_fishing/."), true);
        return failures == 0 ? 1 : 0;
    }
}
