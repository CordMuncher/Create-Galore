package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TackleSelfTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The {@code side} self-test: a hooked fish goes round the angler at its own swimming speed, not faster the further
 * out it is, and an angler moving sideways (a boat under way) swings the line instead of carrying the fish along.
 */
public final class SideSelfTest {
    private static final int HALF = 40;

    private SideSelfTest() {
    }

    public static void register() {
        SelfTest.suite("side", SideSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        ServerLevel level = server.overworld();
        int y = level.getMaxBuildHeight() - 20;
        List<BlockPos> placed = new ArrayList<>();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        ItemStack held = player.getMainHandItem();
        try {
            // A glass pool 81 across and 2 deep, the angler over its middle.
            for (int x = -HALF - 1; x <= HALF + 1; x++) {
                for (int z = -HALF - 1; z <= HALF + 1; z++) {
                    boolean water = Math.abs(x) <= HALF && Math.abs(z) <= HALF;
                    set(level, new BlockPos(x, y - 2, z), Blocks.GLASS, placed);
                    set(level, new BlockPos(x, y - 1, z), water ? Blocks.WATER : Blocks.GLASS, placed);
                    set(level, new BlockPos(x, y, z), water ? Blocks.WATER : Blocks.GLASS, placed);
                }
            }
            ItemStack rod = TackleSelfTest.rig(FishingRegistry.BOAT_ROD.get(), FishingRegistry.TROLLING_REEL.get(),
                    FishingRegistry.HEAVY_LINE.get(), FishingRegistry.BIG_GAME_HOOK.get(), FishingRegistry.SINKER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, rod);
            for (String id : new String[]{"tide:sailfish", "aquaculture:tuna", "aquaculture:carp", "minecraft:cod"}) {
                Species species = SpeciesManager.byId(ResourceLocation.parse(id)).orElse(null);
                if (species == null) {
                    r.info(id + " isn't loaded, skipped");
                    continue;
                }
                double record = species.size().record();
                double usual = (species.size().typicalLow() + species.size().typicalHigh()) / 2;
                fight(level, player, rod, species, usual, new Vec3(0.5, y + 1, 0.5), r);
                fight(level, player, rod, species, record, new Vec3(0.5, y + 1, 0.5), r);
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            for (BlockPos p : placed) if (level.getBlockState(p).is(Blocks.WATER)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (BlockPos p : placed) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void set(ServerLevel level, BlockPos pos, Block block, List<BlockPos> placed) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        placed.add(pos);
    }

    /**
     * Casts 30 m, hooks a fish {@code lengthCm} long and lets it fight for 30 s with the reel left alone, then strafes
     * the angler sideways at 10 m/s for 8 m. The fish's speed round the angler never beats the most its species can
     * swim at that size (on a run, fresh), and the strafe doesn't carry it along (a small one is towed a little).
     */
    private static void fight(ServerLevel level, FakePlayer player, ItemStack rod, Species species, double lengthCm,
                              Vec3 at, SelfTest.Report r) {
        player.setPos(at.x, at.y, at.z);
        player.setYRot(90);
        player.setXRot(0);
        CastHook hook = CastHook.cast(player, InteractionHand.MAIN_HAND, RodStats.of(rod).orElseThrow(), 0.85);
        String name = String.format(Locale.ROOT, "%s %.0f cm", species.id(), lengthCm);
        try {
            for (int i = 0; i < 200 && hook.phase() == CastHook.Phase.FLYING && !hook.isRemoved(); i++) hook.tick();
            hook.tick();
            BiteSampler.Bite bite = new BiteSampler.Bite(species, lengthCm, species.size().weightKg(lengthCm),
                    species.size().classify(lengthCm));
            if (!r.check(hook.forceFight(bite), name + ": hooked")) return;
            double usual = (species.size().typicalLow() + species.size().typicalHigh()) / 2;
            // Fresh, on a run, at full intensity: the most it can swim.
            double most = species.fight().speed() * Math.min(1.6, Math.max(0.6, Math.sqrt(lengthCm / usual))) * 1.8;
            double fastest = 0;
            double turned = 0;
            int ticks = 0;
            Vec3 last = hook.position();
            for (; ticks < 600 && !hook.isRemoved() && hook.phase() == CastHook.Phase.FIGHTING; ticks++) {
                hook.tick();
                Vec3 tip = CastHook.tip(player);
                fastest = Math.max(fastest, sideways(tip, last, hook.position()) / Fight.DT);
                turned += angle(tip, last, hook.position());
                last = hook.position();
            }
            r.check(fastest <= most * 1.05 + 0.05, String.format(Locale.ROOT,
                    "%s: round the angler at most %.1f m/s (it can swim %.1f) over %.0f s, %.0f deg in all",
                    name, fastest, most, ticks * Fight.DT, Math.toDegrees(turned)));
            if (hook.isRemoved() || hook.phase() != CastHook.Phase.FIGHTING) {
                r.info(name + ": the fight ended before the strafe");
                return;
            }
            // A boat going sideways at 10 m/s: the fish stays about where it was; the line swings.
            Vec3 fishBefore = hook.position();
            int steps = 16;
            for (int i = 0; i < steps && !hook.isRemoved(); i++) {
                player.setPos(player.getX(), player.getY(), player.getZ() + 0.5);
                hook.tick();
            }
            // A small fish on this heavy rig is towed a little as the line lengthens; it isn't carried along.
            double carried = Math.abs(hook.getZ() - fishBefore.z);
            r.check(carried < 4, String.format(Locale.ROOT,
                    "%s: the angler moving 8 m sideways moves the fish %.1f m that way (it can swim %.1f in that time)",
                    name, carried, most * steps * Fight.DT));
        } finally {
            hook.discard();
        }
    }

    /** How far the fish moved round the rod tip (across the line) this tick, metres. */
    private static double sideways(Vec3 tip, Vec3 from, Vec3 to) {
        Vec3 out = new Vec3(from.x - tip.x, 0, from.z - tip.z);
        if (out.lengthSqr() < 1e-6) return 0;
        Vec3 across = new Vec3(-out.z, 0, out.x).normalize();
        return Math.abs(to.subtract(from).dot(across));
    }

    private static double angle(Vec3 tip, Vec3 from, Vec3 to) {
        double a = Math.atan2(from.z - tip.z, from.x - tip.x);
        double b = Math.atan2(to.z - tip.z, to.x - tip.x);
        double d = b - a;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return Math.abs(d);
    }
}
