package dev.creategalore.fishing.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.BiteSampler;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.cast.CatchHolding;
import dev.creategalore.fishing.cast.Catches;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.machine.RodMount;import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
                                .executes(ctx -> hook(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))
                                .then(Commands.argument("species", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SpeciesManager.all().stream().map(s -> "\"" + s.id() + "\""), builder))
                                        .then(Commands.argument("cm", DoubleArgumentType.doubleArg(1, 1000))
                                                .executes(ctx -> biteWith(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "species"),
                                                        DoubleArgumentType.getDouble(ctx, "cm")))))))
                .then(Commands.literal("machine")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> machine(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), null, 0))
                                .then(Commands.literal("bite")
                                        .executes(ctx -> machine(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"), "", 0))
                                        .then(Commands.argument("species", StringArgumentType.string())
                                                .then(Commands.argument("cm", DoubleArgumentType.doubleArg(1, 1000))
                                                        .executes(ctx -> machine(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                                StringArgumentType.getString(ctx, "species"),
                                                                DoubleArgumentType.getDouble(ctx, "cm"))))))))
                .then(Commands.literal("crate")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> crate(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("hold")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SpeciesManager.all().stream().map(s -> "\"" + s.id() + "\""), builder))
                                        .then(Commands.argument("cm", DoubleArgumentType.doubleArg(1, 1000))
                                                .executes(ctx -> hold(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "species"),
                                                        DoubleArgumentType.getDouble(ctx, "cm")))))))
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

    /** Test tool: the player's next bite is this species at this length. */
    private static int biteWith(CommandSourceStack source, ServerPlayer player, String speciesId, double cm) {
        Species species = species(source, speciesId);
        if (species == null) return 0;
        if (!(player.fishing instanceof CastHook hook)) {
            source.sendFailure(Component.literal(player.getGameProfile().getName() + " has no line out"));
            return 0;
        }
        hook.forceBite(new BiteSampler.Bite(species, cm, species.size().weightKg(cm), species.size().classify(cm)));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Next bite: %s, %.0f cm, %.2f kg",
                species.id(), cm, species.size().weightKg(cm))), false);
        return 1;
    }

    /**
     * Test tool for rod machines: shows the machine's hook state, or (with {@code bite}) makes its next bite roll
     * succeed, as a given fish if {@code speciesId} is set ({@code ""}: whatever bites there).
     */
    private static int machine(CommandSourceStack source, BlockPos pos, String speciesId, double cm) {
        if (!(source.getLevel().getBlockEntity(pos) instanceof RodMount mount)) {
            source.sendFailure(Component.literal("No rod machine at " + pos.toShortString()));
            return 0;
        }
        CastHook hook = mount.mounted().hook();
        if (hook == null) {
            source.sendFailure(Component.literal("The machine at " + pos.toShortString() + " has no line out"
                    + (mount.mounted().rod().isEmpty() ? " (and no rod)" : "")));
            return 0;
        }
        if (speciesId == null) {
            source.sendSuccess(() -> Component.literal(hook.describe()), false);
            return 1;
        }
        if (speciesId.isEmpty()) {
            hook.forceBite();
            source.sendSuccess(() -> Component.literal("Next bite at the machine: whatever comes"), false);
            return 1;
        }
        Species species = species(source, speciesId);
        if (species == null) return 0;
        hook.forceBite(new BiteSampler.Bite(species, cm, species.size().weightKg(cm), species.size().classify(cm)));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Next bite at the machine: %s, %.0f cm",
                species.id(), cm)), false);
        return 1;
    }

    /** Test tool: the next bite on a player's line is a loot crate (if one fits the spot). */
    private static int crate(CommandSourceStack source, ServerPlayer player) {
        if (!(player.fishing instanceof CastHook hook)) {
            source.sendFailure(Component.literal(player.getGameProfile().getName() + " has no line out"));
            return 0;
        }
        hook.forceCrate();
        source.sendSuccess(() -> Component.literal("Next bite: a crate"), false);
        return 1;
    }

    private static Species species(CommandSourceStack source, String speciesId) {
        ResourceLocation id = ResourceLocation.tryParse(speciesId);
        Species species = id == null ? null : SpeciesManager.byId(id).orElse(null);
        if (species == null) source.sendFailure(Component.literal("Unknown species " + speciesId));
        return species;
    }

    /**
     * Test tool: puts a landed fish of {@code cm} into a player's hands, as if just caught (Keep / Release screen and
     * all), at the player's feet as the release spot.
     */
    private static int hold(CommandSourceStack source, ServerPlayer player, String speciesId, double cm) {
        Species species = species(source, speciesId);
        if (species == null) return 0;
        double kg = species.size().weightKg(cm);
        BiteSampler.Bite bite = new BiteSampler.Bite(species, cm, kg, species.size().classify(cm));
        ItemStack fish = Catches.fishItem(species, bite, player.getGameProfile().getName(), player.level().getDayTime() / 24000);
        CatchHolding.start(player, fish, species, bite, 5, player.blockPosition());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s holds a %s of %.0f cm, %.2f kg",
                player.getGameProfile().getName(), species.id(), cm, kg)), false);
        return 1;
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
