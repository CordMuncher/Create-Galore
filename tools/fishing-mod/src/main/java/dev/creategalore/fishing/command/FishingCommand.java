package dev.creategalore.fishing.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.MonteCarlo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /cgfish}: operator tools for testing and tuning the mod. All of it works from the server console. */
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
                                .executes(ctx -> selftest(ctx.getSource(), StringArgumentType.getString(ctx, "suites")))))
                .then(Commands.literal("hook")
                        .executes(ctx -> hook(ctx.getSource(), ctx.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> hook(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))))
                .then(Commands.literal("bite")
                        .executes(ctx -> hook(ctx.getSource(), ctx.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> hook(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))))
                .then(Commands.literal("simulate")
                        .then(Commands.literal("export").executes(ctx -> export(ctx.getSource())))
                        .then(Commands.argument("species", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("all");
                                    return SharedSuggestionProvider.suggest(
                                            SpeciesManager.all().stream().map(s -> "\"" + s.id() + "\""), builder);
                                })
                                .executes(ctx -> simulate(ctx.getSource(), StringArgumentType.getString(ctx, "species"), 300))
                                .then(Commands.argument("fights", IntegerArgumentType.integer(10, 100000))
                                        .executes(ctx -> simulate(ctx.getSource(), StringArgumentType.getString(ctx, "species"),
                                                IntegerArgumentType.getInteger(ctx, "fights")))))));
    }

    private static Path out(CommandSourceStack source) throws IOException {
        Path dir = source.getServer().getServerDirectory().resolve(CreateGaloreFishing.ID);
        Files.createDirectories(dir);
        return dir;
    }

    /** Shows a player's hook state, or (with {@code bite}) makes its next bite roll succeed. */
    private static int hook(CommandSourceStack source, ServerPlayer player, boolean bite) {
        if (!(player.fishing instanceof CastHook hook)) {
            source.sendFailure(Component.literal(player.getGameProfile().getName() + " has no line out"));
            return 0;
        }
        if (bite) hook.forceBite();
        String text = hook.describe();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int selftest(CommandSourceStack source, String suites) {
        int failures = SelfTest.run(source.getServer(), suites);
        source.sendSuccess(() -> Component.literal("Self-test done: " + failures
                + " failure(s). Reports are in the server folder under create_galore_fishing/."), true);
        return failures == 0 ? 1 : 0;
    }

    /** Runs the Monte Carlo for one species (or all) against every rig preset; writes simulate.csv. */
    private static int simulate(CommandSourceStack source, String which, int fights) {
        List<Species> targets = new ArrayList<>();
        for (Species s : SpeciesManager.all()) {
            if (which.equals("all") || s.id().toString().equals(which)) targets.add(s);
        }
        if (targets.isEmpty()) {
            source.sendFailure(Component.literal("No species " + which));
            return 0;
        }
        List<String> lines = new ArrayList<>();
        lines.add(MonteCarlo.Result.header());
        for (Species s : targets) {
            for (MonteCarlo.Result r : MonteCarlo.runAll(new MonteCarlo.Species(s.id().toString(), s.fight(), s.size()), fights, 1)) {
                lines.add(r.csv());
                if (targets.size() == 1) source.sendSuccess(() -> Component.literal(r.csv()), false);
            }
        }
        try {
            Path file = out(source).resolve("simulate.csv");
            Files.write(file, lines);
            source.sendSuccess(() -> Component.literal("Wrote " + (lines.size() - 1) + " rows to " + file), true);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Couldn't write simulate.csv: " + e));
        }
        return 1;
    }

    /** Writes species.csv for the standalone Monte Carlo runner. */
    private static int export(CommandSourceStack source) {
        List<String> lines = new ArrayList<>();
        lines.add("id,fight_per_kg,stamina,speed,profile,runs,jumps,charges,toothy,typical_low,typical_high,record,a,b");
        for (Species s : SpeciesManager.all()) {
            String profile = dev.creategalore.fishing.sim.Curve.PRESETS.entrySet().stream()
                    .filter(e -> e.getValue() == s.fight().profile()).map(java.util.Map.Entry::getKey).findFirst().orElse("middle");
            lines.add(String.format(Locale.ROOT, "%s,%.3f,%.3f,%.3f,%s,%.3f,%.3f,%.3f,%s,%.2f,%.2f,%.2f,%.5f,%.3f",
                    s.id(), s.fight().fightPerKg(), s.fight().stamina(), s.fight().speed(), profile, s.fight().runs(),
                    s.fight().jumps(), s.fight().charges(), s.fight().toothy(), s.size().typicalLow(), s.size().typicalHigh(),
                    s.size().record(), s.size().a(), s.size().b()));
        }
        try {
            Path file = out(source).resolve("species.csv");
            Files.write(file, lines);
            source.sendSuccess(() -> Component.literal("Wrote " + (lines.size() - 1) + " species to " + file), true);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Couldn't write species.csv: " + e));
        }
        return 1;
    }
}
