package dev.creategalore.fishing.machine;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.process.FishAttributes;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.Gearbox;
import dev.creategalore.fishing.sim.SizeClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * {@code /cgfish selftest drops}: where a drive turns its rod to drop a catch and what it won't drop into (a funnel
 * three blocks down, a lid-up barrel that opens and closes, a sideways barrel, a target out of reach or behind a
 * block, a chest touching the drive), how fast the rod turns by top gear, a drop surviving a save, and the fish
 * attributes Create's Attribute Filter gets.
 */
public final class DropSelfTest {
    private DropSelfTest() {
    }

    public static void register() {
        SelfTest.suite("drops", DropSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        attributes(r);
        turnSpeed(r);
        ServerLevel level = server.overworld();
        BlockPos base = new BlockPos(38, level.getMaxBuildHeight() - 12, 4);
        List<BlockPos> placed = new ArrayList<>();
        try {
            funnel(level, base, placed, r);
            clear(level, placed);
            barrel(level, base, placed, r);
            clear(level, placed);
            notTargets(level, base, placed, r);
            clear(level, placed);
            touching(level, base, placed, r);
            clear(level, placed);
            saved(level, base, placed, r);
        } finally {
            clear(level, placed);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(10)).forEach(Entity::discard);
        }
    }

    private static void clear(ServerLevel level, List<BlockPos> placed) {
        for (int i = placed.size() - 1; i >= 0; i--) {
            if (level.getBlockEntity(placed.get(i)) instanceof Container c) c.clearContent();
            level.removeBlock(placed.get(i), false);
        }
        placed.clear();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> placed) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        placed.add(pos);
    }

    /** A geared drive facing north at {@code at}, with a rod in. */
    private static MountedRod drive(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        set(level, at, FishingRegistry.GEARED_REEL_DRIVE.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), placed);
        if (!(level.getBlockEntity(at) instanceof DriveMount drive)) {
            r.fail("the drive has its block entity");
            return null;
        }
        CompoundTag rod = new CompoundTag();
        rod.put("rod", new ItemStack(FishingRegistry.IRON_ROD.get()).save(level.registryAccess()));
        drive.mounted().read(rod, level.registryAccess());
        return drive.mounted();
    }

    /** Steps the rod's clock until its catches are dealt with and it is back; the game time it got there. */
    private static long settle(MountedRod rod, long from) {
        long t = from;
        for (; t < from + 400; t++) {
            rod.step(t);
            if (rod.carried().isEmpty() && rod.turn().atRest() && rod.turn().done(t) && t > from) break;
        }
        return t;
    }

    private static boolean holds(ServerLevel level, BlockPos pos, ItemStack want) {
        return level.getBlockEntity(pos) instanceof Container c && c.countItem(want.getItem()) == want.getCount();
    }

    /** An upward funnel on a chest, one column east and three blocks down: the rod turns over it and drops in. */
    private static void funnel(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        MountedRod rod = drive(level, at, placed, r);
        if (rod == null) return;
        BlockPos chest = at.east().below(3);
        set(level, chest, Blocks.CHEST.defaultBlockState(), placed);
        set(level, chest.above(), AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP), placed);
        // A funnel finds the inventory it sits on in its first tick, as it does in the world.
        if (level.getBlockEntity(chest.above()) instanceof SmartBlockEntity funnel) funnel.tick();
        long t0 = level.getGameTime();
        rod.output(new ArrayList<>(List.of(new ItemStack(Items.COD, 2))));
        rod.step(t0);
        r.check(!rod.carried().isEmpty() && Math.abs(rod.turn().toYaw() - 90) < 0.01f,
                "a drive facing north turns its rod a quarter to the east, the cod hanging from it (to " + rod.turn().toYaw() + ")");
        r.check(rod.busy(), "and it won't cast meanwhile");
        r.check(rod.releaseAt() > rod.turn().end() && rod.fallTicks() >= 6,
                "it holds the fish over the funnel, then lets it fall (" + rod.fallTicks() + " ticks)");
        long done = settle(rod, t0);
        r.check(holds(level, chest, new ItemStack(Items.COD, 2)), "the funnel passes it into the chest it sits on");
        r.check(rod.turn().atRest() && done - t0 < 120, "and the rod turns back (" + (done - t0) + " ticks in all)");
    }

    /** A barrel with its lid up, four blocks down, catches a fish without a funnel and opens to do it. */
    private static void barrel(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        MountedRod rod = drive(level, at, placed, r);
        if (rod == null) return;
        BlockPos barrel = at.south().below(4);
        set(level, barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), placed);
        long t0 = level.getGameTime();
        rod.output(new ArrayList<>(List.of(new ItemStack(Items.SALMON))));
        rod.step(t0);
        r.check(Math.abs(Math.abs(rod.turn().toYaw()) - 180) < 0.01f, "for a barrel behind it the rod turns right round");
        long release = rod.releaseAt();
        for (long t = t0 + 1; t <= release; t++) rod.step(t);
        r.check(level.getBlockState(barrel).getValue(BarrelBlock.OPEN), "the barrel opens as the fish is let go");
        long landed = release + rod.fallTicks();
        for (long t = release + 1; t <= landed; t++) rod.step(t);
        r.check(holds(level, barrel, new ItemStack(Items.SALMON)), "the salmon lands in it");
        for (long t = landed + 1; t <= landed + CatchDrop.BARREL_OPEN_AFTER + 1; t++) rod.step(t);
        r.check(!level.getBlockState(barrel).getValue(BarrelBlock.OPEN), "and it closes again");
    }

    /** A barrel on its side, a barrel out of reach, and a lid-up barrel under a solid block: none of them take it. */
    private static void notTargets(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        MountedRod rod = drive(level, at, placed, r);
        if (rod == null) return;
        set(level, at.east().below(2), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.NORTH), placed);
        set(level, at.south().below(CatchDrop.REACH_DOWN + 1), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), placed);
        set(level, at.west().below(1), Blocks.STONE.defaultBlockState(), placed);
        set(level, at.west().below(3), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), placed);
        // Two blocks out north: not touching the drive's column.
        set(level, at.north(2).below(2), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), placed);
        long t0 = level.getGameTime();
        rod.output(new ArrayList<>(List.of(new ItemStack(Items.COD))));
        settle(rod, t0);
        r.check(rod.dangling().is(Items.COD), "a sideways barrel, one " + (CatchDrop.REACH_DOWN + 1)
                + " down, one under a block and one a block away take nothing: the cod hangs at the tip");
    }

    /** A chest right beside the drive, at its height: any block touching a side takes it, as before. */
    private static void touching(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        MountedRod rod = drive(level, at, placed, r);
        if (rod == null) return;
        set(level, at.west(), Blocks.CHEST.defaultBlockState(), placed);
        long t0 = level.getGameTime();
        rod.output(new ArrayList<>(List.of(new ItemStack(Items.COD))));
        rod.step(t0);
        r.check(Math.abs(rod.turn().toYaw() + 90) < 0.01f, "for a chest touching its west side the rod turns west");
        settle(rod, t0);
        r.check(holds(level, at.west(), new ItemStack(Items.COD)), "and drops the cod in");
        set(level, at.below(), Blocks.CHEST.defaultBlockState(), placed);
        rod.output(new ArrayList<>(List.of(new ItemStack(Items.SALMON))));
        r.check(holds(level, at.below(), new ItemStack(Items.SALMON)) && rod.turn().atRest() && rod.carried().isEmpty(),
                "a chest under the drive takes the catch straight away, without a turn");
    }

    /** A fish on its way survives saving and loading, and still lands. */
    private static void saved(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        MountedRod rod = drive(level, at, placed, r);
        if (rod == null) return;
        BlockPos barrel = at.east().below(2);
        set(level, barrel, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), placed);
        long t0 = level.getGameTime();
        rod.output(new ArrayList<>(List.of(new ItemStack(Items.COD), new ItemStack(Items.SALMON))));
        rod.step(t0);
        CompoundTag tag = new CompoundTag();
        rod.write(tag, level.registryAccess());
        rod.read(tag, level.registryAccess());
        r.check(rod.carried().is(Items.COD) && Math.abs(rod.turn().toYaw() - 90) < 0.01f, "a save mid-turn keeps the fish and the turn");
        settle(rod, t0);
        r.check(holds(level, barrel, new ItemStack(Items.COD)) && holds(level, barrel, new ItemStack(Items.SALMON)),
                "and both catches still land, one after the other");
    }

    /** The rod turns at its top gear's pace: eight times slower in the lowest than in the highest. */
    private static void turnSpeed(SelfTest.Report r) {
        RodTurn fast = RodTurn.REST.to(0, 180, RodTurn.REST_RISE, Gearbox.GEARS[Gearbox.GEARS.length - 1]);
        RodTurn slow = RodTurn.REST.to(0, 180, RodTurn.REST_RISE, Gearbox.GEARS[0]);
        r.check(fast.ticks() == 10 && slow.ticks() == 80,
                "a half turn takes " + fast.ticks() + " ticks in the top gear and " + slow.ticks() + " in the lowest");
    }

    private static ItemStack fish(int grams, SizeClass size) {
        ItemStack cod = new ItemStack(Items.COD);
        cod.set(FishingRegistry.CATCH.get(), grams <= 0
                ? new CatchInfo(size, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
                : new CatchInfo(size, Optional.of(800), Optional.of(grams), Optional.empty(), Optional.of(1L)));
        return cod;
    }

    private static void attributes(SelfTest.Report r) {
        ItemStack six = fish(6000, SizeClass.LARGE);
        ItemStack four = fish(4000, SizeClass.COMMON);
        ItemStack eight = fish(8000, SizeClass.TROPHY);
        ItemStack small = fish(0, SizeClass.COMMON);
        List<ItemAttribute> offered = new ArrayList<>();
        offered.addAll(FishAttributes.WHOLE.getAllAttributes(six, null));
        offered.addAll(FishAttributes.WEIGHT.getAllAttributes(six, null));
        offered.addAll(FishAttributes.SIZE.getAllAttributes(six, null));
        FishAttributes.Weight atLeast = new FishAttributes.Weight(6000, true);
        FishAttributes.Weight atMost = new FishAttributes.Weight(6000, false);
        r.check(offered.contains(new FishAttributes.Whole(false)) && offered.contains(atLeast) && offered.contains(atMost)
                        && offered.contains(new FishAttributes.Size(SizeClass.LARGE)),
                "a 6 kg cod offers: too big to cook whole, 6 kg or more, 6 kg or less, large");
        r.check(atLeast.appliesTo(eight, null) && atLeast.appliesTo(six, null) && !atLeast.appliesTo(four, null) && !atLeast.appliesTo(small, null),
                "6 kg or more: an 8 kg and the 6 kg cod, not a 4 kg one or a small one");
        r.check(atMost.appliesTo(four, null) && atMost.appliesTo(small, null) && !atMost.appliesTo(eight, null),
                "6 kg or less: the 4 kg and the small one, not the 8 kg");
        r.check(new FishAttributes.Whole(true).appliesTo(small, null) && !new FishAttributes.Whole(true).appliesTo(four, null)
                        && !new FishAttributes.Whole(true).appliesTo(new ItemStack(Items.CARROT), null),
                "cookable whole: the small cod, not a 4 kg one, and a carrot isn't a fish");
        r.check(FishAttributes.WHOLE.getAllAttributes(small, null).equals(List.of(new FishAttributes.Whole(true)))
                        && FishAttributes.WEIGHT.getAllAttributes(small, null).isEmpty(),
                "a small cod (no weight shown) offers cookable whole and no weight");
        r.check(new FishAttributes.Size(SizeClass.TROPHY).appliesTo(eight, null) && !new FishAttributes.Size(SizeClass.TROPHY).appliesTo(six, null),
                "trophy: the trophy cod only");
        r.check("6.00 kg".equals(atLeast.getTranslationParameters()[0]), "its weight reads " + atLeast.getTranslationParameters()[0]);
    }
}
