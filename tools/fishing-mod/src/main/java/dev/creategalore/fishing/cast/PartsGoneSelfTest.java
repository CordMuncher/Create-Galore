package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TackleSelfTest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
 * The {@code partsgone} self-test: a line that is out when the rod in the hand can no longer cast (its line, reel or
 * hook taken off, or another rod switched in) is let go, whether a fish is biting or already on. Striking such a bite
 * used to look for the missing line and stop the server.
 */
public final class PartsGoneSelfTest {
    private PartsGoneSelfTest() {
    }

    public static void register() {
        SelfTest.suite("partsgone", PartsGoneSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        ServerLevel level = server.overworld();
        int y = level.getMaxBuildHeight() - 20;
        List<BlockPos> placed = new ArrayList<>();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        ItemStack held = player.getMainHandItem();
        try {
            for (int x = -1; x <= 31; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean water = x >= 0 && x <= 30 && Math.abs(z) <= 2;
                    set(level, new BlockPos(x, y - 2, z), Blocks.GLASS, placed);
                    set(level, new BlockPos(x, y - 1, z), water ? Blocks.WATER : Blocks.GLASS, placed);
                    set(level, new BlockPos(x, y, z), water ? Blocks.WATER : Blocks.GLASS, placed);
                }
            }
            Species cod = SpeciesManager.byId(ResourceLocation.parse("minecraft:cod")).orElse(null);
            if (!r.check(cod != null, "cod is loaded")) return;
            Vec3 at = new Vec3(30.5, y + 1, 0.5);

            // Control: with the whole rig a lift strikes the bite, so the cases below reach the strike.
            check(level, player, cod, at, true, stack -> { }, "nothing taken off: the lift strikes", r);
            check(level, player, cod, at, true,
                    stack -> RodItem.setParts(stack, RodItem.parts(stack).with(RodParts.Slot.LINE, ItemStack.EMPTY)),
                    "line taken off during a bite: the line is let go", r);
            check(level, player, cod, at, true,
                    stack -> RodItem.setParts(stack, RodItem.parts(stack).with(RodParts.Slot.REEL, ItemStack.EMPTY)),
                    "reel taken off during a bite: the line is let go", r);
            check(level, player, cod, at, true,
                    stack -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FishingRegistry.IRON_ROD.get())),
                    "bare rod switched in during a bite: the line is let go", r);
            check(level, player, cod, at, false,
                    stack -> RodItem.setParts(stack, RodItem.parts(stack).with(RodParts.Slot.LINE, ItemStack.EMPTY)),
                    "line taken off during a fight: the line is let go", r);
        } finally {
            RodLift.set(player, false);
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
     * Casts a fresh iron rig, gets a cod biting (or on, when {@code biting} is false), applies {@code change} to the
     * rod in the hand and lifts. With nothing taken off the fish is struck; otherwise the hook must be gone and no
     * tick may throw.
     */
    private static void check(ServerLevel level, FakePlayer player, Species cod, Vec3 at, boolean biting,
                              Consumer<ItemStack> change, String what, SelfTest.Report r) {
        ItemStack rod = TackleSelfTest.rig(FishingRegistry.IRON_ROD.get(), FishingRegistry.IRON_REEL.get(),
                FishingRegistry.MONO_LINE.get(), FishingRegistry.MEDIUM_HOOK.get(), FishingRegistry.FLOAT.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        player.setPos(at.x, at.y, at.z);
        player.setYRot(90);
        player.setXRot(0);
        RodLift.set(player, false);
        CastHook hook = CastHook.cast(player, InteractionHand.MAIN_HAND, RodStats.of(rod).orElseThrow(), 0.3);
        try {
            for (int i = 0; i < 200 && hook.phase() == CastHook.Phase.FLYING && !hook.isRemoved(); i++) hook.tick();
            hook.tick();
            double cm = (cod.size().typicalLow() + cod.size().typicalHigh()) / 2;
            BiteSampler.Bite bite = new BiteSampler.Bite(cod, cm, cod.size().weightKg(cm), cod.size().classify(cm));
            if (biting) {
                hook.forceBite(bite);
                for (int i = 0; i < 40 && hook.phase() != CastHook.Phase.BITE && !hook.isRemoved(); i++) hook.tick();
                if (!r.check(hook.phase() == CastHook.Phase.BITE && !hook.isRemoved(), what + ": a fish bites (" + hook.phase() + ")")) return;
            } else if (!r.check(hook.forceFight(bite), what + ": a fish is on")) {
                return;
            }
            boolean control = RodStats.of(player.getMainHandItem()).map(RodStats::canCast).orElse(false);
            change.accept(player.getMainHandItem());
            control = control && RodStats.of(player.getMainHandItem()).map(RodStats::canCast).orElse(false);
            RodLift.set(player, true);
            Throwable thrown = null;
            try {
                for (int i = 0; i < 5 && !hook.isRemoved(); i++) hook.tick();
            } catch (Throwable t) {
                thrown = t;
            }
            if (control) {
                r.check(thrown == null && !hook.isRemoved() && hook.phase() == CastHook.Phase.FIGHTING,
                        what + " (" + (thrown != null ? thrown : hook.isRemoved() ? "removed" : hook.phase()) + ")");
            } else {
                r.check(thrown == null && hook.isRemoved(), what + " (" + (thrown != null ? thrown : hook.isRemoved() ? "removed" : hook.phase()) + ")");
            }
        } finally {
            RodLift.set(player, false);
            hook.discard();
        }
    }
}
