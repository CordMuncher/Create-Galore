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
                                .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "case"))))))
                .then(Commands.literal("dump").then(Commands.argument("dir", StringArgumentType.string())
                        .executes(ctx -> dump(ctx.getSource(), Path.of(StringArgumentType.getString(ctx, "dir"))))))
                .then(Commands.literal("as").then(Commands.argument("case", StringArgumentType.string())
                        .then(Commands.argument("out", StringArgumentType.string())
                                .executes(ctx -> as(ctx.getSource(), Path.of(StringArgumentType.getString(ctx, "case")),
                                        Path.of(StringArgumentType.getString(ctx, "out")), false))
                                .then(Commands.literal("plain")
                                        .executes(ctx -> as(ctx.getSource(), Path.of(StringArgumentType.getString(ctx, "case")),
                                                Path.of(StringArgumentType.getString(ctx, "out")), true))))))
                .then(Commands.literal("call").then(Commands.argument("tool", StringArgumentType.word())
                        .then(Commands.argument("in", StringArgumentType.string())
                                .then(Commands.argument("out", StringArgumentType.string())
                                        .executes(ctx -> call(ctx.getSource(), StringArgumentType.getString(ctx, "tool"),
                                                Path.of(StringArgumentType.getString(ctx, "in")),
                                                Path.of(StringArgumentType.getString(ctx, "out"))))))))));
    }

    // ---------- the stand-in book: a model plays the book against this server, for the answer eval ----------

    /** The stand-in player's question being answered: its spot, the world box set up for it, and the answer's drafts. */
    private static final class Standin {
        BlockPos at;
        int[] box;
        BlockPos floor;
        /** The book as it was before the player snapshot and design briefs, for comparing against. */
        boolean plain;
        final List<Models.Draft> drafts = new ArrayList<>();
        final java.util.Set<String> briefs = new java.util.HashSet<>();
        final List<String> drawings = new ArrayList<>();
        JsonObject question;
    }

    private static Standin standin;

    /** Writes the book's two system blocks and its tool definitions, exactly as a question sends them. */
    private static int dump(CommandSourceStack src, Path dir) {
        try {
            Files.createDirectories(dir);
            GuideText.Guide guide = GuideText.load(src.getServer().getResourceManager());
            Files.writeString(dir.resolve("persona.txt"), GuideText.PERSONA, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("reference.txt"), GuideAi.reference(guide), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("tools.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                    .toJson(WorldTools.definitions()), StandardCharsets.UTF_8);
            src.sendSuccess(() -> Component.literal("Casebook: wrote the book's prompt to " + dir), false);
            return 1;
        } catch (IOException | RuntimeException e) {
            src.sendFailure(Component.literal("Casebook dump failed: " + e));
            return 0;
        }
    }

    /**
     * Sets the stand-in player up for a question case: its inventory, a fresh spot with the case's {@code world}
     * commands built there (marked as placed by a player, so the build tools see them), standing at {@code stand}
     * ([dx, dy, dz, yaw, pitch]) and looking at it. Writes the question as the book would get it, with its snapshot.
     */
    private static int as(CommandSourceStack src, Path caseFile, Path out, boolean plain) {
        MinecraftServer server = src.getServer();
        ServerLevel level = server.overworld();
        try {
            JsonObject c = JsonParser.parseString(Files.readString(caseFile, StandardCharsets.UTF_8)).getAsJsonObject();
            clearStandin(level);
            Standin s = new Standin();
            s.question = c;
            s.plain = plain;
            s.at = ORIGIN.south(SPACING * 4);
            s.box = box(c);
            forceLoad(level, s.at, c, true);
            CommandSourceStack run = server.createCommandSourceStack().withLevel(level).withPosition(Vec3.atLowerCornerOf(s.at))
                    .withPermission(4).withSuppressedOutput();
            for (JsonElement e : array(c, "world")) server.getCommands().performPrefixedCommand(run, place(e.getAsString(), s.at));
            // Commands run from inside a command wait until it returns, so the world is built by the next tick.
            server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> placeStandin(src, server, level, s, c, out)));
            return 1;
        } catch (IOException | RuntimeException e) {
            LOG.error("Casebook as failed", e);
            src.sendFailure(Component.literal("Casebook as failed: " + e));
            return 0;
        }
    }

    private static void placeStandin(CommandSourceStack src, MinecraftServer server, ServerLevel level, Standin s, JsonObject c, Path out) {
        try {
            PlacedBlocks placed = GuideAi.placed();
            // "built" (default: the whole box) is the player's own build; trees and ground around it aren't
            int[] built = c.has("built") ? box(c, "built") : s.box;
            if (placed != null) for (BlockPos p : BlockPos.betweenClosed(s.at.offset(built[0], built[1], built[2]), s.at.offset(built[3], built[4], built[5])))
                if (!level.getBlockState(p).isAir()) placed.add(level, p.immutable(), level.getBlockState(p));
            JsonArray stand = array(c, "stand");
            BlockPos feet = stand.size() >= 3 ? s.at.offset(stand.get(0).getAsInt(), stand.get(1).getAsInt(), stand.get(2).getAsInt()) : s.at;
            if (level.getBlockState(feet.below()).isAir()) {
                level.setBlock(feet.below(), Blocks.GLASS.defaultBlockState(), 3);
                s.floor = feet.below();
            }
            ServerPlayer p = tester(server, c, feet);
            p.setYRot(stand.size() >= 4 ? stand.get(3).getAsFloat() : 0);
            p.setXRot(stand.size() >= 5 ? stand.get(4).getAsFloat() : 0);
            p.setYHeadRot(p.getYRot());
            GuideAi.see(p);
            standin = s;
            String question = p.getGameProfile().getName() + " asks: " + GuideText.string(c, "question")
                    + (s.plain ? "" : "\n\n" + PlayerSnapshot.of(p, server));
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, question, StandardCharsets.UTF_8);
            src.sendSuccess(() -> Component.literal("Casebook: the stand-in is ready; question in " + out), false);
        } catch (IOException | RuntimeException e) {
            LOG.error("Casebook as failed", e);
            src.sendFailure(Component.literal("Casebook as failed: " + e));
        }
    }

    private static void clearStandin(ServerLevel level) {
        Standin s = standin;
        standin = null;
        if (s == null) return;
        PlacedBlocks placed = GuideAi.placed();
        for (BlockPos p : BlockPos.betweenClosed(s.at.offset(s.box[0], s.box[1] - 1, s.box[2]), s.at.offset(s.box[3], s.box[4], s.box[5]))) {
            if (placed != null) placed.remove(level, p);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        if (s.floor != null) level.setBlock(s.floor, Blocks.AIR.defaultBlockState(), 2 | 16);
        forceLoad(level, s.at, s.question, false);
    }

    /** One tool call as the book makes it, for the stand-in player; draft pictures are written next to the result. */
    private static int call(CommandSourceStack src, String tool, Path in, Path out) {
        MinecraftServer server = src.getServer();
        Standin s = standin;
        String result;
        byte[] picture = null;
        try {
            if (s == null) throw new IllegalStateException("set the stand-in up with /fieldguide casebook as first");
            JsonObject input = JsonParser.parseString(Files.readString(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray stand = array(s.question, "stand");
            BlockPos feet = stand.size() >= 3 ? s.at.offset(stand.get(0).getAsInt(), stand.get(1).getAsInt(), stand.get(2).getAsInt()) : s.at;
            ServerPlayer p = FakePlayerFactory.get(server.overworld(), TESTER);
            switch (tool) {
                case "build_model" -> {
                    PlacedBlocks placed = GuideAi.placed();
                    Models.Copier copier = placed == null ? null : (a, b, max) -> Builds.copy(p, placed, GuideAi.view(p.getUUID()), a, b, max);
                    Models.Result r = Models.build(input, server.overworld(), copier, p, s.drafts, s.plain ? null : s.briefs);
                    if (r.problem() != null) result = r.problem();
                    else if (r.draft()) {
                        picture = r.picture();
                        result = "Draft only, not in the answer. The picture (" + out.getFileName() + ".png) shows it from the "
                                + "front-left and back-right corners (top row) and straight from the front and the right side "
                                + "(bottom row), in each block's shape and map colour." + (r.check().isEmpty() ? "" : "\n" + r.check());
                    } else {
                        s.drawings.add("model");
                        result = "Built as diagram " + s.drawings.size() + "." + (r.check().isEmpty() ? "" : "\n" + r.check());
                    }
                }
                case "draw_diagram" -> {
                    String problem = Diagrams.check(input, new ArrayList<>());
                    if (problem == null) {
                        s.drawings.add("drawing");
                        result = "Drawn as diagram " + s.drawings.size() + ".";
                    } else result = problem;
                }
                default -> result = WorldTools.run(tool, input, p, server);
            }
        } catch (IOException | RuntimeException e) {
            result = "The tool failed: " + e;
        }
        try {
            Files.writeString(out, result, StandardCharsets.UTF_8);
            if (picture != null) Files.write(Path.of(out + ".png"), picture);
        } catch (IOException e) {
            src.sendFailure(Component.literal("Casebook call couldn't write " + out));
            return 0;
        }
        return 1;
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
                case "answer" -> judge(run, c, answer(run, c));
                case "loop" -> judge(run, c, loop(run, c));
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
        Models.Result r = Models.build(c.getAsJsonObject("model"), level, null, player, new ArrayList<>(), new java.util.HashSet<>());
        boolean built = r.problem() == null;
        return new Result(built, built ? r.check() : r.problem());
    }

    /** The answer audit on a written answer, with {@code diagrams} the diagrams its answer made (as JSON). */
    private static Result answer(Run run, JsonObject c) {
        List<String> made = new ArrayList<>();
        for (JsonElement e : array(c, "diagrams")) made.add(e.toString());
        List<String> issues = AnswerAudit.check(GuideText.string(c, "answer"), made, run.server);
        return new Result(null, issues.isEmpty() ? "(nothing to fix)" : String.join("\n", issues));
    }

    /** The book's loop replayed against the case's scripted API {@code turns}: how the answer ends for the player. */
    private static Result loop(Run run, JsonObject c) {
        List<JsonObject> turns = new ArrayList<>();
        for (JsonElement e : array(c, "turns")) turns.add(e.getAsJsonObject());
        try {
            return new Result(null, GuideAi.replay(run.server, GuideText.string(c, "question"), turns));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(null, "(interrupted)");
        }
    }

    private static Result tool(Run run, JsonObject c) {
        ServerPlayer player = tester(run, c, ORIGIN);
        JsonObject input = c.has("input") ? c.getAsJsonObject("input") : new JsonObject();
        return new Result(null, WorldTools.run(GuideText.string(c, "tool"), input, player, run.server));
    }

    /** The stand-in player: at the case's spot, holding the case's inventory and nothing else. */
    private static ServerPlayer tester(Run run, JsonObject c, BlockPos at) {
        return tester(run.server, c, at);
    }

    private static ServerPlayer tester(MinecraftServer server, JsonObject c, BlockPos at) {
        ServerLevel level = server.overworld();
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
        progress(server, p, c);
        p.moveTo(Vec3.atBottomCenterOf(at));
        return p;
    }

    /** Stats the last case gave the stand-in, zeroed before the next one. */
    private static final List<net.minecraft.stats.Stat<?>> GIVEN_STATS = new ArrayList<>();

    /**
     * Gives the stand-in a case's progress: {@code advancements} (ids, or "namespace:*" for every advancement of a mod)
     * done, and {@code crafted} and {@code placed} ({item id: count}) in its statistics. Anything an earlier case gave
     * is taken back first.
     */
    private static void progress(MinecraftServer server, ServerPlayer p, JsonObject c) {
        realAdvancements(server, p);
        for (net.minecraft.stats.Stat<?> stat : GIVEN_STATS) p.getStats().setValue(p, stat, 0);
        GIVEN_STATS.clear();
        List<String> want = new ArrayList<>();
        for (JsonElement e : array(c, "advancements")) want.add(e.getAsString());
        for (var holder : server.getAdvancements().getAllAdvancements()) {
            String id = holder.id().toString();
            boolean wanted = want.contains(id) || want.contains(holder.id().getNamespace() + ":*");
            var done = p.getAdvancements().getOrStartProgress(holder);
            if (wanted) {
                for (String criterion : toList(done.getRemainingCriteria()))
                    p.getAdvancements().award(holder, criterion);
            } else {
                for (String criterion : toList(done.getCompletedCriteria()))
                    p.getAdvancements().revoke(holder, criterion);
            }
        }
        for (String field : List.of("crafted", "placed")) {
            if (!c.has(field)) continue;
            for (var e : c.getAsJsonObject(field).entrySet()) {
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getKey()));
                net.minecraft.stats.Stat<?> stat = field.equals("crafted") ? net.minecraft.stats.Stats.ITEM_CRAFTED.get(item)
                        : net.minecraft.stats.Stats.ITEM_USED.get(item);
                p.getStats().setValue(p, stat, e.getValue().getAsInt());
                GIVEN_STATS.add(stat);
            }
        }
    }

    /**
     * A fake player's advancements ignore every award, so the stand-in gets real ones (kept in a file of the casebook's
     * own, never a player's) to show a case's progress.
     */
    private static void realAdvancements(MinecraftServer server, ServerPlayer p) {
        if (!p.getAdvancements().getClass().getSimpleName().contains("Fake")) return;
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("advancements");
            f.setAccessible(true);
            f.set(p, new net.minecraft.server.PlayerAdvancements(server.getFixerUpper(), server.getPlayerList(), server.getAdvancements(),
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("create_galore_field_guide_ai").resolve("casebook-advancements.json"), p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Casebook: the stand-in can't hold advancements ({})", e.toString());
        }
    }

    private static List<String> toList(Iterable<String> names) {
        List<String> out = new ArrayList<>();
        names.forEach(out::add);
        return out;
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
        return box(c, "box");
    }

    private static int[] box(JsonObject c, String key) {
        JsonArray a = array(c, key);
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
