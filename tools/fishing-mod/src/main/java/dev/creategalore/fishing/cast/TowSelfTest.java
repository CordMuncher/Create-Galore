package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TackleSelfTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The {@code tow} self-test: a hand rod's waiting rig is dragged along when the angler moves away (trolling from a
 * boat, walking backwards on land); the line out stays what it was, on water and on land.
 */
public final class TowSelfTest {
    private TowSelfTest() {
    }

    public static void register() {
        SelfTest.suite("tow", TowSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        ServerLevel level = server.overworld();
        int y = level.getMaxBuildHeight() - 20;
        List<BlockPos> placed = new ArrayList<>();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        ItemStack held = player.getMainHandItem();
        try {
            // A pool 47 long and 2 deep in glass; beside it a glass strip for land.
            for (int x = -1; x <= 47; x++) {
                for (int z = -3; z <= 3; z++) {
                    set(level, new BlockPos(x, y - 2, z), Blocks.GLASS, placed);
                    boolean water = x >= 0 && x <= 46 && z >= -2 && z <= 2;
                    set(level, new BlockPos(x, y - 1, z), water ? Blocks.WATER : Blocks.GLASS, placed);
                    set(level, new BlockPos(x, y, z), water ? Blocks.WATER : Blocks.GLASS, placed);
                }
                for (int z = 8; z <= 10; z++) set(level, new BlockPos(x, y, z), Blocks.GLASS, placed);
            }
            ItemStack rod = TackleSelfTest.rig(FishingRegistry.IRON_ROD.get(), FishingRegistry.IRON_REEL.get(),
                    FishingRegistry.MONO_LINE.get(), FishingRegistry.MEDIUM_HOOK.get(), FishingRegistry.FLOAT.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, rod);
            tow(level, player, rod, new Vec3(30.5, y + 1, 0.5), "on water", r);
            tow(level, player, rod, new Vec3(30.5, y + 1, 9.5), "on land", r);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            // Water first, so none of it is left to spill through the glass as that comes out.
            for (BlockPos p : placed) if (level.getBlockState(p).is(Blocks.WATER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (BlockPos p : placed) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void set(ServerLevel level, BlockPos pos, Block block, List<BlockPos> placed) {
        BlockState state = block.defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        placed.add(pos);
    }

    /**
     * Casts toward -x from {@code at}, then backs off along +x like a boat under way, steps back in, looks round,
     * strafes and goes off again. The line shown never changes, and the rig only moves when the line pulls it.
     */
    private static void tow(ServerLevel level, FakePlayer player, ItemStack rod, Vec3 at, String where, SelfTest.Report r) {
        player.setPos(at.x, at.y, at.z);
        player.setYRot(90);
        player.setXRot(0);
        CastHook hook = CastHook.cast(player, InteractionHand.MAIN_HAND, RodStats.of(rod).orElseThrow(), 0.2);
        try {
            for (int i = 0; i < 200 && hook.phase() == CastHook.Phase.FLYING && !hook.isRemoved(); i++) hook.tick();
            CastHook.Phase settled = hook.phase();
            if (!r.check(!hook.isRemoved() && settled != CastHook.Phase.FLYING, where + ": the rig settles (" + settled + ")")) return;
            hook.tick();
            double start = flat(player, hook);
            float shown = hook.lineOut();
            Vec3 rig = hook.position();
            // Away at 10 m/s for 12 m (a boat at speed).
            float[] range = move(player, hook, 12, 0, 0.5);
            double away = flat(player, hook);
            double dragged = hook.position().subtract(rig).horizontalDistance();
            r.check(Math.abs(away - start) < 0.05 && dragged > 11 && steady(range, shown), String.format(Locale.ROOT,
                    "%s: moving away 12 m drags the rig %.1f m, the line stays %.1f m (now %.1f, shown %.1f-%.1f of %.1f)",
                    where, dragged, start, away, range[0], range[1], shown));
            // Back toward the rig: the line goes slack, the rig stays put.
            Vec3 before = hook.position();
            range = move(player, hook, -5, 0, 0.5);
            r.check(hook.position().subtract(before).horizontalDistance() < 0.05 && steady(range, shown), String.format(Locale.ROOT,
                    "%s: stepping back toward the rig leaves it where it is (shown %.1f-%.1f)", where, range[0], range[1]));
            // Looking round swings the rod tip about the angler, but neither moves the rig nor changes the line.
            before = hook.position();
            range = new float[]{Float.MAX_VALUE, -Float.MAX_VALUE};
            for (float yaw : new float[]{30, -30, 180, 120, 60, 90}) {
                player.setYRot(yaw);
                hook.tick();
                range[0] = Math.min(range[0], hook.lineOut());
                range[1] = Math.max(range[1], hook.lineOut());
            }
            r.check(hook.position().subtract(before).horizontalDistance() < 0.05 && steady(range, shown), String.format(Locale.ROOT,
                    "%s: looking round leaves the rig and the line alone (shown %.1f-%.1f)", where, range[0], range[1]));
            // Strafing back and forth: the line shown stays what it is.
            range = move(player, hook, 0, 2, 0.2);
            float[] back = move(player, hook, 0, -2, 0.2);
            r.check(steady(range, shown) && steady(back, shown), String.format(Locale.ROOT,
                    "%s: strafing leaves the line shown at %.1f m", where, shown));
            // Off again, further than before: it follows at the same line out, never more.
            range = move(player, hook, 15, 0, 0.5);
            double again = flat(player, hook);
            r.check(Math.abs(again - start) < 0.05 && steady(range, shown), String.format(Locale.ROOT,
                    "%s: off again, the line out is still %.1f m (%.1f, shown %.1f-%.1f)", where, start, again, range[0], range[1]));
        } finally {
            hook.discard();
        }
    }

    private static boolean steady(float[] range, float shown) {
        return Math.abs(range[0] - shown) < 0.01 && Math.abs(range[1] - shown) < 0.01;
    }

    /** Moves the angler {@code dx}, {@code dz} metres at {@code perTick}; returns the lowest and highest line shown. */
    private static float[] move(FakePlayer player, CastHook hook, double dx, double dz, double perTick) {
        float[] range = {Float.MAX_VALUE, -Float.MAX_VALUE};
        int steps = (int) Math.ceil(Math.hypot(dx, dz) / perTick);
        for (int i = 0; i < steps && !hook.isRemoved(); i++) {
            player.setPos(player.getX() + dx / steps, player.getY(), player.getZ() + dz / steps);
            hook.tick();
            range[0] = Math.min(range[0], hook.lineOut());
            range[1] = Math.max(range[1], hook.lineOut());
        }
        return range;
    }

    private static double flat(FakePlayer player, CastHook hook) {
        return Math.hypot(player.getX() - hook.getX(), player.getZ() - hook.getZ());
    }
}
