package dev.creategalore.bridge.guideai;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * /fieldguide builds add|remove FROM TO: lets an operator mark every block in a box as placed by a player (or no
 * longer), for builds made before the book kept track. The box is outlined for the operator, who
 * then confirms or cancels in chat. Fluids are left out, since the book reads the water and lava touching a build
 * anyway.
 */
final class BuildClaims {
    static final int MAX_SIDE = 128;
    private static final int SHOW_TICKS = 20 * 60;

    private record Pending(boolean add, ServerLevel level, BlockPos min, BlockPos max, long until) {
    }

    private static final Map<UUID, Pending> pending = new HashMap<>();

    private BuildClaims() {
    }

    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fieldguide").then(Commands.literal("builds")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("add").then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(ctx -> propose(ctx, true)))))
                .then(Commands.literal("remove").then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(ctx -> propose(ctx, false)))))
                .then(Commands.literal("confirm").executes(BuildClaims::confirm))
                .then(Commands.literal("cancel").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Pending was = pending.remove(player.getUUID());
                    boolean had = was != null;
                    if (had) outline(player, was, 0);
                    ctx.getSource().sendSuccess(() -> Component.literal(had ? "Cancelled; nothing changed." : "Nothing to cancel."), false);
                    return had ? 1 : 0;
                }))));
    }

    private static int propose(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        if (GuideAi.placed() == null) {
            src.sendFailure(Component.literal("The Field Guide only keeps track of builds on a dedicated server."));
            return 0;
        }
        BlockPos a = BlockPosArgument.getBlockPos(ctx, "from"), b = BlockPosArgument.getBlockPos(ctx, "to");
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        int sx = max.getX() - min.getX() + 1, sy = max.getY() - min.getY() + 1, sz = max.getZ() - min.getZ() + 1;
        if (sx > MAX_SIDE || sy > MAX_SIDE || sz > MAX_SIDE) {
            src.sendFailure(Component.literal("The box can be at most " + MAX_SIDE + " blocks a side; this one is " + sx + "x" + sy + "x" + sz + "."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunksAt(min, max)) {
            src.sendFailure(Component.literal("Part of the box isn't loaded. Stand closer and try again."));
            return 0;
        }
        int blocks = count(level, min, max);
        Pending pend = new Pending(add, level, min, max, level.getGameTime() + SHOW_TICKS);
        pending.put(player.getUUID(), pend);
        outline(player, pend, SHOW_TICKS);
        MutableComponent msg = Component.literal("The outlined box is " + sx + "x" + sy + "x" + sz + " and holds " + blocks + " blocks (air and "
                + "fluids left out). " + (add ? "Mark them all as placed by a player, so the Field Guide's voice can find and read "
                + "them as a build? Keep the box tight: ground inside it counts as built too. " : "Forget that players placed them? ")
                + "The outline shows for a minute. ");
        msg.append(Component.literal("[Confirm]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fieldguide builds confirm"))));
        msg.append(" ");
        msg.append(Component.literal("[Cancel]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fieldguide builds cancel"))));
        src.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        Pending p = pending.remove(player.getUUID());
        if (p != null) outline(player, p, 0);
        PlacedBlocks placed = GuideAi.placed();
        if (p == null || placed == null || p.level.getGameTime() > p.until) {
            src.sendFailure(Component.literal("Nothing to confirm (it runs out after a minute). Run the add or remove command again."));
            return 0;
        }
        if (!p.level.hasChunksAt(p.min, p.max)) {
            src.sendFailure(Component.literal("Part of the box isn't loaded any more. Stand closer and run the command again."));
            return 0;
        }
        if (p.add && placed.size() + count(p.level, p.min, p.max) > PlacedBlocks.MAX_BLOCKS) {
            src.sendFailure(Component.literal("That would go over the " + PlacedBlocks.MAX_BLOCKS + " placed blocks the Field Guide keeps."));
            return 0;
        }
        int done = 0;
        for (BlockPos pos : BlockPos.betweenClosed(p.min, p.max)) {
            BlockState state = p.level.getBlockState(pos);
            if (!counts(state)) continue;
            if (p.add) placed.add(p.level, pos.immutable(), state);
            else placed.remove(p.level, pos);
            done++;
        }
        int n = done;
        src.sendSuccess(() -> Component.literal((p.add ? "Marked " : "Forgot ") + n + " blocks " + (p.add ? "as placed by a player." : "as placed by a player.")
                + " " + placed.size() + " placed blocks are known in all."), true);
        return n;
    }

    private static int count(ServerLevel level, BlockPos min, BlockPos max) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) if (counts(level.getBlockState(pos))) n++;
        return n;
    }

    private static boolean counts(BlockState state) {
        return PlacedBlocks.counts(state);
    }

    /** Drops boxes that ran out or whose operator left, and takes the outline away. */
    static void onTick(ServerTickEvent.Post event) {
        if (pending.isEmpty() || event.getServer().getTickCount() % 20 != 0) return;
        pending.entrySet().removeIf(e -> {
            Pending p = e.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player != null && p.level.getGameTime() <= p.until) return false;
            if (player != null) outline(player, p, 0);
            return true;
        });
    }

    /** The outline is drawn by the operator's game (client/ClaimBoxView), so it shows as lines even far away. */
    private static void outline(ServerPlayer player, Pending p, int ticks) {
        PacketDistributor.sendToPlayer(player, new GuideAiNet.ClaimBox(p.min, p.max, p.add, ticks));
    }
}
