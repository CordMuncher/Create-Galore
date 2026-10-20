package dev.creategalore.fishing.aquarium;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.SizeClass;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * {@code /cgfish selftest aquarium}: tank blocks placed in the world join into one tank when they fill a box and
 * show as one piece; the tank takes water from pipes, lets fish in by room and length, breeds baitfish on fish meal,
 * survives saving and loading, lets a hopper take only spare baitfish; breaking a wall spills the fish, breaking off
 * the end of a tank keeps them, and the blocks join up again when the gap is filled.
 */
public final class AquariumSelfTest {
    private AquariumSelfTest() {
    }

    public static void register() {
        SelfTest.suite("aquarium", AquariumSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        ServerLevel level = server.overworld();
        BlockPos base = new BlockPos(40, level.getMaxBuildHeight() - 8, 0);
        List<BlockPos> placed = new ArrayList<>();
        try {
            bigTank(level, base, placed, r);
            endOfTank(level, base.offset(0, 0, 6), placed, r);
        } finally {
            for (BlockPos p : placed) level.removeBlock(p, false);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(12)).forEach(Entity::discard);
        }
    }

    private static void place(ServerLevel level, BlockPos pos, List<BlockPos> placed) {
        level.setBlock(pos, FishingRegistry.AQUARIUM_TANK.get().defaultBlockState(), Block.UPDATE_ALL);
        placed.add(pos);
        // What the scheduled tick does once the block entity exists.
        AquariumTanks.form(level, pos);
    }

    private static Item minnow() {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aquaculture", "minnow"));
        return item == Items.AIR ? Items.COD : item;
    }

    private static ItemStack sized(Item item, double cm) {
        ItemStack fish = new ItemStack(item);
        fish.set(TideDataComponents.FISH_LENGTH, cm);
        return fish;
    }

    private static void bigTank(ServerLevel level, BlockPos base, List<BlockPos> placed, SelfTest.Report r) {
        for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 3; x++) place(level, base.offset(x, y, z), placed);
        AquariumBlockEntity tank = level.getBlockEntity(base) instanceof AquariumBlockEntity be ? be.tank() : null;
        if (!r.check(tank != null && tank.isController() && tank.sizeX() == 3 && tank.sizeY() == 2 && tank.sizeZ() == 2,
                "twelve blocks filling a 3x2x2 box are one tank, its controller the low corner")) return;
        r.check(placed.stream().allMatch(p -> level.getBlockEntity(p) instanceof AquariumBlockEntity be && base.equals(be.controllerPos())),
                "every block knows its controller");
        BlockState corner = level.getBlockState(base);
        r.check(corner.getValue(AquariumBlock.SIDES.get(Direction.EAST)) && corner.getValue(AquariumBlock.SIDES.get(Direction.UP))
                        && corner.getValue(AquariumBlock.SIDES.get(Direction.SOUTH)) && !corner.getValue(AquariumBlock.SIDES.get(Direction.WEST))
                        && !corner.getValue(AquariumBlock.SIDES.get(Direction.DOWN)),
                "the corner joins its neighbours east, south and up, and shows glass west and a floor below");

        ItemStack minnow = sized(minnow(), 8);
        r.check(!tank.addFish(minnow.copy()), "no fish go in before the tank has water");
        IFluidHandler pipes = tank.fluids();
        r.check(pipes.fill(new FluidStack(Fluids.WATER, 20000), IFluidHandler.FluidAction.EXECUTE) == 12000
                && tank.water() == 12000, "a pipe fills it with 12 buckets of water, no more");
        r.check(tank.addFish(minnow.copy()) && tank.addFish(minnow.copy()), "two minnows go in");
        r.check(!tank.addFish(sized(Items.COD, 350)), "a 3.5 m fish doesn't: it is longer than the tank");
        int big = 0;
        while (big < 60 && tank.addFish(sized(Items.COD, 100))) big++;
        r.check(big > 0 && tank.used() <= tank.volume() + 1e-9,
                "metre-long fish fill it by room: " + big + " fit, " + String.format(java.util.Locale.ROOT, "%.2f", tank.used())
                        + " of " + tank.volume() + " blocks of water used");
        while (!tank.takeFish(f -> f.is(Items.COD)).isEmpty()) {
        }

        long now = level.getGameTime();
        r.check(!tank.breed(now), "no young without fish meal");
        r.check(tank.feed(3) == 3, "fish meal feeds it");
        r.check(tank.breed(now) && tank.fish().size() == 3 && tank.food() == 2, "a pair of minnows raises a young one on one fish meal");
        CatchInfo young = tank.fish().get(2).get(FishingRegistry.CATCH.get());
        r.check(young == null || young.size() == SizeClass.YOUNG, "the new one is young");
        r.check(!tank.breed(now + 20), "and the next has to wait");

        CompoundTag saved = tank.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(base, level.getBlockState(base), saved, level.registryAccess());
        r.check(loaded instanceof AquariumBlockEntity copy && copy.fish().size() == 3 && copy.water() == 12000 && copy.food() == 2
                        && copy.sizeX() == 3, "the tank and its fish survive saving and loading");

        var hopper = tank.items();
        int taken = 0;
        for (int slot = 1; slot < hopper.getSlots(); slot++) taken += hopper.extractItem(slot, 1, false).getCount();
        r.check(taken == 1 && tank.fish().size() == 2, "a hopper takes the one spare minnow and leaves the breeding pair");
        r.check(hopper.insertItem(0, new ItemStack(FishingRegistry.FISH_MEAL.get(), 5), false).isEmpty() && tank.food() == 7,
                "and a funnel feeds it fish meal");

        BlockPos wall = base.offset(1, 1, 1);
        level.removeBlock(wall, false);
        placed.remove(wall);
        long spilled = level.getEntitiesOfClass(ItemEntity.class, new AABB(wall).inflate(1.5)).stream()
                .filter(e -> e.getItem().is(minnow())).count();
        r.check(spilled == 2, "breaking a wall block spills the fish (" + spilled + ")");
        r.check(level.getBlockEntity(base) instanceof AquariumBlockEntity be && be.isController() && be.volume() == 1,
                "and what's left falls apart into single blocks");
        place(level, wall, placed);
        r.check(level.getBlockEntity(base) instanceof AquariumBlockEntity be && be.isController() && be.volume() == 12,
                "filling the gap joins them into the tank again");
    }

    /** A 2x1x1 tank that loses one end keeps its fish in the block that's left. */
    private static void endOfTank(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        place(level, at, placed);
        place(level, at.east(), placed);
        AquariumBlockEntity tank = level.getBlockEntity(at) instanceof AquariumBlockEntity be ? be.tank() : null;
        if (!r.check(tank != null && tank.volume() == 2, "two blocks in a row are a tank")) return;
        tank.fill(2000, false);
        tank.addFish(sized(minnow(), 8));
        level.removeBlock(at.east(), false);
        placed.remove(at.east());
        AquariumBlockEntity left = level.getBlockEntity(at) instanceof AquariumBlockEntity be ? be.tank() : null;
        r.check(left != null && left.volume() == 1 && left.fish().size() == 1 && left.water() == 1000,
                "breaking off the end keeps the fish (and as much water as fits) in what is left");
    }
}
