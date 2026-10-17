package dev.creategalore.structscan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Finds every structure in an area from the seed alone and writes density numbers.
 * It runs the game's own structure-start step (placement grid, frequency, exclusion zones, biome and
 * terrain checks, every mod's changes to them) on throwaway chunks, so nothing is generated or saved.
 *
 * In game: /structscan run <radius> [label], /structscan whatif <radius> [label] (uses config/structscan-rules.txt).
 * Batch: start the server with -Dstructscan=radius=6000,label=pack,rules=path,baseline=dir,dims=overworld,verify=512,exit=true
 */
@Mod(StructScan.ID)
public final class StructScan {
    public static final String ID = "create_galore_structscan";
    static final Logger LOG = LoggerFactory.getLogger("StructScan");
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    public StructScan(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    /** Settings for one scan. */
    record Options(int radius, int centerX, int centerZ, String label, List<String> dims, Path rules, Path baseline, int threads, int verify) {}

    private void onServerStarted(ServerStartedEvent event) {
        String arg = System.getProperty("structscan");
        if (arg == null || arg.isBlank()) return;
        MinecraftServer server = event.getServer();
        Map<String, String> kv = new LinkedHashMap<>();
        for (String part : arg.split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) kv.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        // rules=none+a.txt+b.txt runs one scan per entry in the same server start, so the slow first-time
        // loading of every structure template is only paid once ("none" is a plain scan).
        List<String> ruleFiles = kv.containsKey("rules") ? List.of(kv.get("rules").split("\\+")) : List.of("none");
        List<Options> runs = new ArrayList<>();
        String label = kv.getOrDefault("label", "scan");
        for (String rf : ruleFiles) {
            boolean plain = rf.equals("none");
            String name = plain ? "plain" : Path.of(rf).getFileName().toString().replaceFirst("\\.txt$", "");
            runs.add(new Options(
                    Integer.parseInt(kv.getOrDefault("radius", "6000")),
                    Integer.parseInt(kv.getOrDefault("x", "0")),
                    Integer.parseInt(kv.getOrDefault("z", "0")),
                    ruleFiles.size() > 1 ? label + "-" + name : label,
                    List.of(kv.getOrDefault("dims", "overworld").split("\\+")),
                    plain ? null : Path.of(rf),
                    kv.containsKey("baseline") ? Path.of(kv.get("baseline")) : defaultBaseline(server),
                    Integer.parseInt(kv.getOrDefault("threads", String.valueOf(Runtime.getRuntime().availableProcessors()))),
                    Integer.parseInt(kv.getOrDefault("verify", "0"))));
        }
        boolean exit = Boolean.parseBoolean(kv.getOrDefault("exit", "false"));
        start(server, runs, LOG::info, () -> {
            if (exit) server.execute(() -> server.halt(false));
        });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("structscan")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("run")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(256, 100000))
                                .executes(c -> command(c.getSource(), IntegerArgumentType.getInteger(c, "radius"), "scan", false))
                                .then(Commands.argument("label", StringArgumentType.word())
                                        .executes(c -> command(c.getSource(), IntegerArgumentType.getInteger(c, "radius"),
                                                StringArgumentType.getString(c, "label"), false)))))
                .then(Commands.literal("whatif")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(256, 100000))
                                .executes(c -> command(c.getSource(), IntegerArgumentType.getInteger(c, "radius"), "whatif", true))
                                .then(Commands.argument("label", StringArgumentType.word())
                                        .executes(c -> command(c.getSource(), IntegerArgumentType.getInteger(c, "radius"),
                                                StringArgumentType.getString(c, "label"), true))))));
    }

    private int command(CommandSourceStack src, int radius, String label, boolean whatif) {
        MinecraftServer server = src.getServer();
        Path rules = null;
        if (whatif) {
            rules = server.getServerDirectory().resolve("config/structscan-rules.txt");
            if (!Files.exists(rules)) {
                src.sendFailure(Component.literal("No config/structscan-rules.txt. Each line: <structure set pattern> <spacing scale> [frequency scale], e.g. \"dungeons_arise:* 2\""));
                return 0;
            }
        }
        String dim = src.getLevel().dimension().location().toString();
        Options opt = new Options(radius, (int) src.getPosition().x, (int) src.getPosition().z, label, List.of(dim), rules, defaultBaseline(server),
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 0);
        if (!start(server, List.of(opt), msg -> server.execute(() -> src.sendSystemMessage(Component.literal(msg))), () -> {})) {
            src.sendFailure(Component.literal("A scan is already running."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Scanning ±" + radius + " blocks around you in the background..."), false);
        return 1;
    }

    /** Runs the scans one after another, off the server thread. Returns false if one is already running. */
    static boolean start(MinecraftServer server, List<Options> runs, Consumer<String> say, Runnable done) {
        if (!RUNNING.compareAndSet(false, true)) return false;
        Thread t = new Thread(() -> {
            try {
                for (Options opt : runs) {
                    List<Rules.Rule> rules = opt.rules() == null ? List.of() : Rules.read(opt.rules());
                    Path outRoot = server.getServerDirectory().resolve("structscan");
                    for (String d : opt.dims()) {
                        ColumnCache.clear();
                        ServerLevel level = findLevel(server, d);
                        if (level == null) { say.accept("No dimension " + d); continue; }
                        Scanner.Result r = Scanner.scan(level, opt, rules, LOG::info);
                        Path dir = Report.write(outRoot, opt, r);
                        for (String line : Report.headline(r)) say.accept(line);
                        say.accept("Report: " + server.getServerDirectory().relativize(dir) + "/summary.md");
                        if (opt.verify() > 0 && !r.whatif()) {
                            List<String> v = Scanner.verify(level, r, opt.verify());
                            Files.write(dir.resolve("verify.txt"), v);
                            say.accept(v.get(0));
                        }
                    }
                }
            } catch (Throwable e) {
                LOG.error("Structure scan failed", e);
                say.accept("Structure scan failed: " + e);
            } finally {
                RUNNING.set(false);
                done.run();
            }
        }, "StructScan");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /** structscan/baseline/ (a copy of a vanilla scan's folder) is compared against when no other baseline is given. */
    private static Path defaultBaseline(MinecraftServer server) {
        Path p = server.getServerDirectory().resolve("structscan/baseline");
        return Files.exists(p.resolve("scan.properties")) ? p : null;
    }

    private static ServerLevel findLevel(MinecraftServer server, String name) {
        ResourceLocation want = ResourceLocation.parse(name.contains(":") ? name : "minecraft:" + name);
        List<String> seen = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().equals(want)) return l;
            seen.add(l.dimension().location().toString());
        }
        LOG.warn("Dimension {} not found. Have: {}", name, seen);
        return null;
    }
}
