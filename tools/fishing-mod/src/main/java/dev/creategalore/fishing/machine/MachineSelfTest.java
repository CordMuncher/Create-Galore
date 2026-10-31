package dev.creategalore.fishing.machine;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.AliveFish;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.compat.LiquidFuels;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.selftest.SelfTest;
import dev.creategalore.fishing.sim.Angler;
import dev.creategalore.fishing.sim.Gearbox;
import dev.creategalore.fishing.sim.MonteCarlo;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.sim.TacklePresets;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * {@code /cgfish selftest machines}: the torque-against-drag rule (what stalls a water wheel and what doesn't), the
 * gearbox, the Basic drive wearing its reel faster than the Geared one on the same fish, where a machine's catch
 * goes (the inventory below, never another drive's bait slot, else it hangs at the tip), banked XP and its pay-out,
 * the drives' settings surviving a reload, the Fuel drive taking fuel, alive fish going back into the water, and a
 * geared drive refusing only a rod that can't be cast (a poor rig still goes out).
 */
public final class MachineSelfTest {
    private MachineSelfTest() {
    }

    public static void register() {
        SelfTest.suite("machines", MachineSelfTest::run);
    }

    private static void run(MinecraftServer server, SelfTest.Report r) {
        stall(r);
        gearbox(r);
        wear(r);
        ServerLevel level = server.overworld();
        BlockPos base = new BlockPos(20, level.getMaxBuildHeight() - 8, 8);
        List<BlockPos> placed = new ArrayList<>();
        try {
            catches(level, base, placed, r);
            settings(level, base.offset(0, 0, 4), placed, r);
            fuel(level, base.offset(0, 0, 6), placed, r);
            alive(level, base.offset(0, 0, 8), placed, r);
            castable(level, base.offset(0, 0, 10), placed, r);
        } finally {
            for (BlockPos p : placed) level.removeBlock(p, false);
            AABB area = new AABB(base).inflate(14);
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard);
            level.getEntitiesOfClass(ExperienceOrb.class, area).forEach(Entity::discard);
            level.getEntitiesOfClass(Entity.class, area, e -> e.getType() == EntityType.COD).forEach(Entity::discard);
        }
    }

    /** A water wheel: 8 RPM, 256 SU, the drive's own share included. */
    private static void stall(SelfTest.Report r) {
        double rpm = 8, capacity = 256;
        r.check(ReelDriveBlockEntity.impact(0, 1) == ReelDriveBlockEntity.IDLE, "a drive that isn't winding against a fish costs only its idle stress");
        double limit = ReelDriveBlockEntity.torqueAtOne(capacity, rpm);
        r.check(Math.abs(limit - 7.75) < 1e-9, String.format(Locale.ROOT, "on a water wheel a drive winds against %.2f kgf in gear 1", limit));
        r.check(ReelDriveBlockEntity.impact(6, 1) * rpm <= capacity, "a 6 kg drag never overstresses it (the most a fish can pull before line slips)");
        r.check(ReelDriveBlockEntity.impact(10, 1) * rpm > capacity, "a 10 kg drag does, once a fish leans on it");
        r.check(ReelDriveBlockEntity.impact(10, Gearbox.GEARS[0]) * rpm <= capacity, "but not in the lowest gear (a quarter of the torque)");
        r.check(ReelDriveBlockEntity.torqueAtOne(capacity, 0) == 0, "a shaft that doesn't turn can't wind anything");
    }

    private static void gearbox(SelfTest.Report r) {
        Gearbox box = new Gearbox(0, Gearbox.GEARS.length - 1);
        r.check(box.ratio() == 1.0, "the gearbox starts in gear 1");
        box.update(8, 7.5);
        r.check(box.ratio() < 1.0, "a pull near what the drive can turn shifts it down");
        for (int i = 0; i < 60; i++) box.update(8, 7.5);
        r.check(box.ratio() == Gearbox.GEARS[0] || 7.5 < 0.85 * 8 / box.ratio(), "and keeps shifting until it has room (gear " + box.ratio() + ")");
        for (int i = 0; i < 200; i++) box.update(8, 0.2);
        r.check(box.ratio() == Gearbox.GEARS[Gearbox.GEARS.length - 1], "a tired fish lets it climb back to the top gear");
        Gearbox limited = new Gearbox(0, 2);
        for (int i = 0; i < 200; i++) limited.update(8, 0.2);
        r.check(limited.ratio() == Gearbox.GEARS[2], "never above the top gear set on the drive");
    }

    /** The same fish against the same tackle and drag: the Basic drive grinds its reel through every run. */
    private static void wear(SelfTest.Report r) {
        Species tuna = SpeciesManager.byId(ResourceLocation.fromNamespaceAndPath("aquaculture", "tuna"))
                .or(() -> SpeciesManager.byId(ResourceLocation.withDefaultNamespace("salmon"))).orElse(null);
        if (!r.check(tuna != null, "a strong fish to test the drives on")) return;
        MonteCarlo.Species fish = new MonteCarlo.Species(tuna.id().toString(), tuna.fight(), tuna.size());
        TacklePresets.Preset rig = TacklePresets.ALL.get("steel");
        MonteCarlo.Result basic = MonteCarlo.run(fish, rig, () -> Angler.basicDrive(8, 40), 120, 7);
        MonteCarlo.Result geared = MonteCarlo.run(fish, rig, () -> Angler.gearedDrive(8, 40), 120, 7);
        r.check(basic.avgReelWear() > 2 * geared.avgReelWear(), String.format(Locale.ROOT,
                "the Basic drive wears its reel faster (%.2f against %.2f per fight)", basic.avgReelWear(), geared.avgReelWear()));
        MonteCarlo.Result weakBasic = MonteCarlo.run(fish, rig, () -> Angler.basicDrive(8, 3), 120, 7);
        MonteCarlo.Result weakGeared = MonteCarlo.run(fish, rig, () -> Angler.gearedDrive(8, 3), 120, 7);
        r.check(weakGeared.rate(Outcome.LANDED) >= weakBasic.rate(Outcome.LANDED), String.format(Locale.ROOT,
                "on weak rotation the gearbox lands at least as many (%.0f%% against %.0f%%)",
                100 * weakGeared.rate(Outcome.LANDED), 100 * weakBasic.rate(Outcome.LANDED)));
    }

    private static void place(ServerLevel level, BlockPos pos, Block block, List<BlockPos> placed) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        placed.add(pos);
    }

    /** A holder with a chest below and a geared drive beside it. */
    private static void catches(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        place(level, at, FishingRegistry.ROD_HOLDER.get(), placed);
        place(level, at.below(), Blocks.CHEST, placed);
        level.setBlock(at.east(), FishingRegistry.GEARED_REEL_DRIVE.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
        placed.add(at.east());
        if (!(level.getBlockEntity(at) instanceof RodHolderBlockEntity holder) || !(level.getBlockEntity(at.east()) instanceof DriveMount drive)) {
            r.fail("the holder and the drive have their block entities");
            return;
        }
        MountedRod rod = holder.mounted();
        CastHook hook = new CastHook(FishingRegistry.CAST_HOOK.get(), level);
        rod.landed(hook, new ArrayList<>(List.of(new ItemStack(Items.COD))), 9);
        r.check(rod.bankedXp() == 5, "a landed fish banks half the XP by hand, rounded (" + rod.bankedXp() + " of 9)");
        boolean inChest = level.getBlockEntity(at.below()) instanceof ChestBlockEntity chest && chest.getItem(0).is(Items.COD);
        r.check(inChest, "the catch goes into the chest below");
        r.check(drive.drive().bait().getStackInSlot(0).isEmpty(), "and never into the drive beside it (a cod is cut bait, its bait slot would take it)");

        level.removeBlock(at.below(), false);
        rod.output(new ArrayList<>(List.of(new ItemStack(Items.COD), new ItemStack(Items.SALMON))));
        rod.step(level.getGameTime());
        rod.step(level.getGameTime() + 1);
        r.check(rod.dangling().is(Items.COD), "with nowhere to go, the fish hangs at the tip");
        r.check(!level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2), e -> e.getItem().is(Items.SALMON)).isEmpty(),
                "and a second one is dropped");

        var player = FakePlayerFactory.getMinecraft(level);
        player.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        r.check(rod.use(player, InteractionHand.MAIN_HAND) && rod.dangling().isEmpty(),
                "an empty hand takes the hanging fish");
        ItemStack rodItem = new ItemStack(FishingRegistry.IRON_ROD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rodItem);
        int orbs = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at).inflate(3)).size();
        r.check(rod.use(player, InteractionHand.MAIN_HAND) && rod.rod() == rodItem, "a rod goes in");
        int paid = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at).inflate(3)).stream().mapToInt(ExperienceOrb::getValue).sum();
        r.check(rod.bankedXp() == 0 && paid >= 5 && level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at).inflate(3)).size() > orbs,
                "and the banked XP is paid out (" + paid + ")");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** A geared drive's three settings and its bait survive saving and loading. */
    private static void settings(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        level.setBlock(at, FishingRegistry.GEARED_REEL_DRIVE.get().defaultBlockState(), Block.UPDATE_ALL);
        placed.add(at);
        if (!(level.getBlockEntity(at) instanceof ReelDriveBlockEntity be)) {
            r.fail("the geared drive has its block entity");
            return;
        }
        var mode = be.getBehaviour(ScrollValueBehaviour.TYPE);
        var distance = be.getBehaviour(DriveSetting.CAST_DISTANCE);
        var top = be.getBehaviour(DriveSetting.TOP_GEAR);
        if (!r.check(mode != null && distance != null && top != null, "it has its three value boxes")) return;
        r.check(mode.getValue() == DriveMode.WAIT.ordinal() && distance.getValue() == 12 && top.getValue() == Gearbox.GEARS.length,
                "which start at Wait, 12 m and the top gear");
        mode.setValue(DriveMode.CAST.ordinal());
        distance.setValue(30);
        top.setValue(3);
        be.drive().bait().setStackInSlot(0, baitItem());
        CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(at, level.getBlockState(at), saved, level.registryAccess());
        r.check(loaded instanceof ReelDriveBlockEntity copy
                        && copy.getBehaviour(ScrollValueBehaviour.TYPE).getValue() == DriveMode.CAST.ordinal()
                        && copy.getBehaviour(DriveSetting.CAST_DISTANCE).getValue() == 30 && copy.getBehaviour(DriveSetting.TOP_GEAR).getValue() == 3
                        && ItemStack.isSameItem(copy.drive().bait().getStackInSlot(0), be.drive().bait().getStackInSlot(0)),
                "and they come back after saving and loading, each on its own (mode, distance, top gear, bait)");
        r.check(!be.drive().bait().isItemValid(0, new ItemStack(Items.COAL)), "the bait slot won't take coal");
    }

    /** Worms (the pack's bait), or cod as cut bait without Aquaculture. */
    private static ItemStack baitItem() {
        Item worm = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aquaculture", "worm"));
        return new ItemStack(worm == Items.AIR ? Items.COD : worm, 8);
    }

    private static void fuel(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        place(level, at, FishingRegistry.FUEL_REEL_DRIVE.get(), placed);
        if (!(level.getBlockEntity(at) instanceof FuelReelDriveBlockEntity be)) {
            r.fail("the fuel drive has its block entity");
            return;
        }
        r.check(LiquidFuels.ticksPerMb(Fluids.LAVA) > 0 && LiquidFuels.ticksPerMb(FishingRegistry.FISH_OIL.get()) > 0
                && LiquidFuels.ticksPerMb(Fluids.WATER) == 0, "lava and fish oil are fuel, water isn't");
        r.check(be.tank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "its tank refuses water");
        r.check(be.tank().fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "and takes lava");
        r.check(be.items().insertItem(1, new ItemStack(Items.COAL, 4), false).isEmpty() && be.fuelSlot().getStackInSlot(0).getCount() == 4,
                "a hopper puts coal in its fuel slot");
        r.check(!be.items().insertItem(1, baitItem(), true).isEmpty() && be.items().insertItem(0, baitItem(), true).isEmpty(),
                "and bait only in its bait slot");
        r.check(be.drive().describe().contains("winds against 12.0"), "with fuel it has its engine's rating: " + be.drive().describe());
        r.check(be.tank().getFluidInTank(0).getAmount() < 1000 && be.fuelSlot().getStackInSlot(0).getCount() == 4,
                "it burns the tank first and leaves the coal for later");
        double light = FuelReelDriveBlockEntity.burn(3.6, true), heavy = FuelReelDriveBlockEntity.burn(9.8, true);
        r.check(light > FuelReelDriveBlockEntity.burn(0, true) && heavy > 1.5 * light && heavy <= 1,
                String.format(Locale.ROOT, "a fight against a 3.6 kg drag burns %.2f of full, one near 10 kg %.2f, winding in an empty line %.2f, waiting %.2f",
                        light, heavy, FuelReelDriveBlockEntity.burn(0, true), FuelReelDriveBlockEntity.burn(0, false)));
    }

    /**
     * A geared drive casts any rod that can be cast, a bare hook with no bait or float too, and refuses one short of a
     * line, a reel, or a hook or lure (the autocast after a fish broke the line used to cast a rod with none).
     */
    private static void castable(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        place(level, at, FishingRegistry.GEARED_REEL_DRIVE.get(), placed);
        if (!(level.getBlockEntity(at) instanceof ReelDriveBlockEntity be)) {
            r.fail("the geared drive for the cast checks has its block entity");
            return;
        }
        var player = FakePlayerFactory.getMinecraft(level);
        player.setPos(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        ReelDrive drive = be.drive();
        r.check(drive.castState() == ReelDrive.CastState.NO_ROD && !drive.wantsToCast(), "with no rod in, it has nothing to cast");
        record Rig(String what, RodParts parts, boolean casts) {
        }
        ItemStack reel = new ItemStack(FishingRegistry.IRON_REEL.get());
        ItemStack line = new ItemStack(FishingRegistry.MONO_LINE.get());
        ItemStack hook = new ItemStack(FishingRegistry.MEDIUM_HOOK.get());
        ItemStack lure = new ItemStack(FishingRegistry.SPOON.get());
        List<Rig> rigs = new ArrayList<>(List.of(
                new Rig("a reel, line and a bare hook (no bait, no float)", RodParts.EMPTY.with(RodParts.Slot.REEL, reel.copy())
                        .with(RodParts.Slot.LINE, line.copy()).with(RodParts.Slot.HOOK, hook.copy()), true),
                new Rig("no line (the fish broke it)", RodParts.EMPTY.with(RodParts.Slot.REEL, reel.copy())
                        .with(RodParts.Slot.HOOK, hook.copy()).with(RodParts.Slot.BAIT, baitItem().split(4)), false),
                new Rig("no reel", RodParts.EMPTY.with(RodParts.Slot.LINE, line.copy()).with(RodParts.Slot.HOOK, hook.copy()), false),
                new Rig("bait but no hook", RodParts.EMPTY.with(RodParts.Slot.REEL, reel.copy()).with(RodParts.Slot.LINE, line.copy())
                        .with(RodParts.Slot.BAIT, baitItem().split(4)), false),
                new Rig("a lure and no hook", RodParts.EMPTY.with(RodParts.Slot.REEL, reel.copy()).with(RodParts.Slot.LINE, line.copy())
                        .with(RodParts.Slot.TERMINAL, lure.copy()), true)));
        for (Rig rig : rigs) {
            ItemStack rod = new ItemStack(FishingRegistry.IRON_ROD.get());
            RodItem.setParts(rod, rig.parts());
            player.setItemInHand(InteractionHand.MAIN_HAND, rod);
            be.mounted().use(player, InteractionHand.MAIN_HAND);
            ReelDrive.CastState state = drive.castState();
            r.check(rig.casts() ? state == ReelDrive.CastState.READY && drive.wantsToCast()
                            : state == ReelDrive.CastState.CANT_CAST && !drive.wantsToCast(),
                    "a rod with " + rig.what() + (rig.casts() ? " is cast" : " is refused") + " (" + state + ")");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            be.mounted().use(player, InteractionHand.MAIN_HAND);
            if (!r.check(be.mounted().rod().isEmpty(), "and comes out again")) break;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** An alive fish dropped into water goes back; a dead one loses its marker and stays. */
    private static void alive(ServerLevel level, BlockPos at, List<BlockPos> placed, SelfTest.Report r) {
        level.setBlock(at, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        placed.add(at);
        long now = level.getGameTime();
        ItemEntity live = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, AliveFish.mark(new ItemStack(Items.COD, 2), now + 200));
        level.addFreshEntity(live);
        r.check(AliveFish.ticksLeft(live.getItem(), level) == 200, "a fish marked alive counts down");
        r.check(AliveFish.update(live) && live.isRemoved(), "dropped into water while alive, it goes back");
        ItemEntity dead = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, AliveFish.mark(new ItemStack(Items.COD), now - 1));
        level.addFreshEntity(dead);
        r.check(!AliveFish.update(dead) && !dead.isRemoved() && !dead.getItem().has(FishingRegistry.ALIVE_UNTIL.get()),
                "a dead one stays an item and loses the marker, so it stacks again");
    }
}
