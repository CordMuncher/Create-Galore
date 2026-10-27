package dev.creategalore.bridge.guideai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * /fieldguide casebook run DIR [CASE]: replays the book's casebook, every way the book went wrong before, kept as a
 * case with the verdict the book's checks should give (tools/book-casebook in the repo). It runs the checks and tools
 * the way the book calls them, with no API key and no model, so a change can be tested against every past failure.
 * <ul>
 * <li>{@code model}: a build_model input, as the book would send it.</li>
 * <li>{@code world}: commands that build something in the world (run from the case's origin, so {@code ~} works and
 * {@code {x}}, {@code {y+2}}... give absolute coordinates), a wait for it to start turning, then the live machine
 * check on every block in {@code box}. The area is cleared afterwards.</li>
 * <li>{@code tool}: one tool call as a stand-in player at the case's origin, holding {@code inventory}.</li>
 * <li>{@code question}: a question for the answer eval, which needs a model; the replay skips it.</li>
 * </ul>
 * A case passes when the result holds every {@code expect.has} text, none of {@code expect.lacks} (both ignore case)
 * and, for models, is built or refused as {@code expect.built} says. A case marked {@code open} is a failure the book
 * still has: it is expected to fail until the work that fixes it lands, and says FIXED when it passes. The report goes
 * to the console and to casebook-report.md in DIR. Operators only (it runs commands and builds in the world).
 */
final class Casebook {
    private static final Logger LOG = LogUtils.getLogger();
    /** Where world cases are built: high over the world's centre, each case further east, far from any player's build. */
    private static final BlockPos ORIGIN = new BlockPos(30000, 250, 30000);
    private static final int SPACING = 64;
    private static final GameProfile TESTER = new GameProfile(UUID.nameUUIDFromBytes("create_galore:casebook".getBytes(StandardCharsets.UTF_8)), "Casebook");

    private record Outcome(String id, String title, boolean open, String verdict, String detail) {
    }

    /** A run in progress: cases left, results so far, and the world case being built (waiting for its ticks). */
    private static final class Run {
        final MinecraftServer server;
        final CommandSourceStack source;
        final Path dir;
        final Deque<JsonObject> cases;
        final List<Outcome> results = new ArrayList<>();
        int slot;
        JsonObject waiting;
        BlockPos waitingAt;
        int ticksLeft;

        Run(MinecraftServer server, CommandSourceStack source, Path dir, Deque<JsonObject> cases) {
            this.server = server;
            this.source = source;
            this.dir = dir;
            this.cases = cases;
        }
    }

    private static Run running;

    private Casebook() {
    }

    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fieldguide").then(Commands.literal("casebook")
                .requires(src -> src.hasPermission(4))
                .then(Commands.literal("run").then(Commands.argument("dir", StringArgumentType.string())
                        .executes(ctx -> start(ctx, null))
                        .then(Commands.argument("case", StringArgumentType.word())
                                .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "case"))))))));
    }

    private static int start(CommandContext<CommandSourceStack> ctx, String only) {
        CommandSourceStack src = ctx.getSource();
        if (running != null) {
            src.sendFailure(Component.literal("A casebook run is already going."));
            return 0;
        }
        Path dir = Path.of(StringArgumentType.getString(ctx, "dir"));
        Path casesDir = Files.isDirectory(dir.resolve("cases")) ? dir.resolve("cases") : dir;
        Deque<JsonObject> cases = new ArrayDeque<>();
        try (Stream<Path> files = Files.list(casesDir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject c = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!c.has("id")) c.addProperty("id", f.getFileName().toString().replace(".json", ""));
                if (only == null || GuideText.string(c, "id").equals(only)) cases.add(c);
            }
        } catch (IOException | RuntimeException e) {
            src.sendFailure(Component.literal("Couldn't read the casebook in " + casesDir + ": " + e.getMessage()));
            return 0;
        }
        if (cases.isEmpty()) {
            src.sendFailure(Component.literal("No cases" + (only == null ? "" : " named " + only) + " in " + casesDir + "."));
            return 0;
        }
        running = new Run(src.getServer(), src, dir, cases);
        src.sendSuccess(() -> Component.literal("Casebook: running " + cases.size() + " cases."), false);
        return 1;
    }

    static void onTick(ServerTickEvent.Post event) {
        Run run = running;
        if (run == null) return;
        try {
            step(run);
        } catch (RuntimeException | LinkageError e) {
            LOG.error("Casebook run stopped", e);
            running = null;
            run.source.sendFailure(Component.literal("Casebook run stopped: " + e));
        }
    }

    /** One tick of the run: a world case waits out its ticks; otherwise cases run until the next world case starts. */
    private static void step(Run run) {
        if (run.waiting != null) {
            if (--run.ticksLeft > 0) return;
            finishWorld(run);
        }
        while (!run.cases.isEmpty()) {
            JsonObject c = run.cases.poll();
            String kind = GuideText.string(c, "kind");
            switch (kind) {
                case "model" -> judge(run, c, model(run, c));
                case "tool" -> judge(run, c, tool(run, c));
                case "question" -> run.results.add(new Outcome(GuideText.string(c, "id"), GuideText.string(c, "title"),
                        open(c), "SKIPPED", "answer eval case (needs a model)"));
                case "world" -> {
                    startWorld(run, c);
                    return;
                }
                default -> run.results.add(new Outcome(GuideText.string(c, "id"), GuideText.string(c, "title"), open(c),
                        "ERROR", "unknown kind '" + kind + "'"));
            }
        }
        report(run);
        running = null;
    }

    private record Result(Boolean built, String text) {
    }

    private static Result model(Run run, JsonObject c) {
        ServerLevel level = run.server.overworld();
        ServerPlayer player = c.has("inventory") ? tester(run, c, ORIGIN) : null;
        Models.Result r = Models.build(c.getAsJsonObject("model"), level, null, player, new ArrayList<>());
        boolean built = r.problem() == null;
        return new Result(built, built ? r.check() : r.problem());
    }

    private static Result tool(Run run, JsonObject c) {
        ServerPlayer player = tester(run, c, ORIGIN);
        JsonObject input = c.has("input") ? c.getAsJsonObject("input") : new JsonObject();
        return new Result(null, WorldTools.run(GuideText.string(c, "tool"), input, player, run.server));
    }

    /** The stand-in player: at the case's spot, holding the case's inventory and nothing else. */
    private static ServerPlayer tester(Run run, JsonObject c, BlockPos at) {
        ServerLevel level = run.server.overworld();
        ServerPlayer p = FakePlayerFactory.get(level, TESTER);
        p.getInventory().clearContent();
        if (c.has("inventory")) {
            int slot = 0;
            for (JsonElement e : c.getAsJsonArray("inventory")) {
                JsonObject s = e.getAsJsonObject();
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(GuideText.string(s, "id")));
                int count = s.has("count") ? s.get("count").getAsInt() : 1;
                while (count > 0 && slot < p.getInventory().items.size()) {
                    int n = Math.min(count, item.getDefaultMaxStackSize());
                    p.getInventory().items.set(slot++, new ItemStack(item, n));
                    count -= n;
                }
            }
        }
        p.moveTo(Vec3.atBottomCenterOf(at));
        return p;
    }

    private static void startWorld(Run run, JsonObject c) {
        ServerLevel level = run.server.overworld();
        BlockPos at = ORIGIN.east(SPACING * ++run.slot);
        forceLoad(level, at, c, true);
        CommandSourceStack src = run.server.createCommandSourceStack().withLevel(level).withPosition(Vec3.atLowerCornerOf(at))
                .withPermission(4).withSuppressedOutput();
        for (JsonElement e : array(c, "setup")) run.server.getCommands().performPrefixedCommand(src, place(e.getAsString(), at));
        run.waiting = c;
        run.waitingAt = at;
        run.ticksLeft = Math.max(1, c.has("ticks") ? c.get("ticks").getAsInt() : 40);
    }

    private static void finishWorld(Run run) {
        JsonObject c = run.waiting;
        BlockPos at = run.waitingAt;
        run.waiting = null;
        ServerLevel level = run.server.overworld();
        int[] b = box(c);
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(b[0], b[1], b[2]), at.offset(b[3], b[4], b[5])))
            if (!level.getBlockState(p).isAir()) blocks.add(p.immutable());
        Result result = c.has("tool") ? new Result(null, WorldTools.run(GuideText.string(c, "tool"),
                c.has("input") ? c.getAsJsonObject("input") : new JsonObject(), tester(run, c, at), run.server))
                : new Result(null, MachineCheck.check(level, blocks));
        judge(run, c, result);
        for (BlockPos p : BlockPos.betweenClosed(at.offset(b[0], b[1], b[2]), at.offset(b[3], b[4], b[5])))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(at.offset(b[0], b[1], b[2]).getCenter(),
                at.offset(b[3], b[4], b[5]).getCenter()).inflate(2), e -> !(e instanceof ServerPlayer)).forEach(e -> e.discard());
        forceLoad(level, at, c, false);
    }

    private static int[] box(JsonObject c) {
        JsonArray a = array(c, "box");
        int[] b = new int[6];
        for (int i = 0; i < 6 && i < a.size(); i++) b[i] = a.get(i).getAsInt();
        return b;
    }

    private static void forceLoad(ServerLevel level, BlockPos at, JsonObject c, boolean on) {
        int[] b = box(c);
        ChunkPos min = new ChunkPos(at.offset(b[0] - 16, 0, b[2] - 16)), max = new ChunkPos(at.offset(b[3] + 16, 0, b[5] + 16));
        for (int x = min.x; x <= max.x; x++) for (int z = min.z; z <= max.z; z++) level.setChunkForced(x, z, on);
    }

    private static final Pattern COORD = Pattern.compile("\\{([xyz])([+-]\\d+)?}");

    /** A setup command with {x}, {y+1}... turned into the case's absolute coordinates. */
    static String place(String command, BlockPos at) {
        Matcher m = COORD.matcher(command);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int base = switch (m.group(1)) {
                case "x" -> at.getX();
                case "y" -> at.getY();
                default -> at.getZ();
            };
            m.appendReplacement(out, String.valueOf(base + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static void judge(Run run, JsonObject c, Result r) {
        JsonObject expect = c.has("expect") ? c.getAsJsonObject("expect") : new JsonObject();
        String text = r.text() == null ? "" : r.text();
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> wrong = new ArrayList<>();
        if (expect.has("built") && r.built() != null && expect.get("built").getAsBoolean() != r.built())
            wrong.add(r.built() ? "was built, should be refused" : "was refused, should be built");
        for (JsonElement e : array(expect, "has"))
            if (!lower.contains(e.getAsString().toLowerCase(Locale.ROOT))) wrong.add("missing \"" + e.getAsString() + "\"");
        for (JsonElement e : array(expect, "lacks"))
            if (lower.contains(e.getAsString().toLowerCase(Locale.ROOT))) wrong.add("should not say \"" + e.getAsString() + "\"");
        boolean pass = wrong.isEmpty();
        boolean open = open(c);
        String verdict = pass ? (open ? "FIXED" : "PASS") : (open ? "OPEN" : "FAIL");
        String detail = pass ? "" : String.join("; ", wrong);
        run.results.add(new Outcome(GuideText.string(c, "id"), GuideText.string(c, "title"), open, verdict, detail));
        LOG.info("Casebook {} {}: {}\n{}", verdict, GuideText.string(c, "id"), detail, text);
    }

    private static boolean open(JsonObject c) {
        return c.has("open") && c.get("open").getAsBoolean();
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static void report(Run run) {
        int pass = 0, fail = 0, open = 0, fixed = 0, skipped = 0, broken = 0;
        StringBuilder md = new StringBuilder("# Casebook report\n\n| Case | Verdict | Title | Why |\n|---|---|---|---|\n");
        for (Outcome o : run.results) {
            switch (o.verdict()) {
                case "PASS" -> pass++;
                case "FAIL" -> fail++;
                case "OPEN" -> open++;
                case "FIXED" -> fixed++;
                case "SKIPPED" -> skipped++;
                default -> broken++;
            }
            md.append("| ").append(o.id()).append(" | ").append(o.verdict()).append(" | ").append(o.title().replace("|", "/"))
                    .append(" | ").append(o.detail().replace("|", "/")).append(" |\n");
        }
        String summary = String.format(Locale.ROOT, "Casebook: %d pass, %d fail, %d open (known, not fixed yet), %d fixed (drop their open flag), %d errors, %d question cases skipped.",
                pass, fail, open, fixed, broken, skipped);
        md.append('\n').append(summary).append('\n');
        try {
            Files.writeString(run.dir.resolve("casebook-report.md"), md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Casebook: couldn't write the report: {}", e.getMessage());
        }
        LOG.info("{}\n{}", summary, md);
        run.source.sendSuccess(() -> Component.literal(summary), false);
    }
}
