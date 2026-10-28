package dev.creategalore.fishing.machine;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.creategalore.fishing.compat.LiquidFuels;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * The Fuel Reel Drive: a geared drive with its own engine and no shafts, made for boats. The engine winds against
 * up to {@link #RATING} kgf in gear 1.0 (four times that in the lowest gear) and bogs and stalls beyond it. It burns
 * furnace fuel from its fuel slot or a liquid fuel (see {@link LiquidFuels}) from its tank, by load: a tenth while the
 * line waits, all of it while it winds against the most it can. It only runs while it is switched on and a rod is
 * set in with a line out or one to cast. Its window (sneak and use with an empty hand) holds the fuel and bait slots
 * and the on and autocast switches.
 *
 * <p>Hoppers and funnels reach two slots (bait, fuel); pipes fill the tank.
 */
public class FuelReelDriveBlockEntity extends SmartBlockEntity implements DriveMount, IHaveGoggleInformation {
    /** Pull in kgf the engine winds against in gear 1.0. */
    public static final double RATING = 12;
    /** The engine turns the reel a little faster than a drive on a 32 RPM shaft. */
    public static final double SPEED = 1.25;
    public static final int TANK_MB = 4000;
    /** Share of full burn while the engine idles. */
    public static final double IDLE_BURN = 0.1;

    private DriveSlots.Settings settings;
    private final MountedRod rod;
    private final ReelDrive drive;
    private final ItemStackHandler fuelSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getBurnTime(RecipeType.SMELTING) > 0 && !ReelDrive.isBait(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final FluidTank tank = new FluidTank(TANK_MB, fluid -> LiquidFuels.ticksPerMb(fluid.getFluid()) > 0) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private IItemHandler items;
    private double burnLeft;
    private double load;
    private boolean running;
    private int fuelTicks;
    private int syncIn;

    public FuelReelDriveBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        rod = new MountedRod(this, this);
        drive = new ReelDrive(ReelDrive.Kind.FUEL, this, this, rod, new Engine());
        items = new CombinedInvWrapper(drive.bait(), fuelSlot);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Runs from Create's constructor, before this class's fields are set up: only the settings may be made here.
        settings = DriveSlots.create(this);
        settings.addTo(behaviours);
    }

    @Override
    public DriveSlots.Settings settings() {
        return settings;
    }

    @Override
    public ReelDrive drive() {
        return drive;
    }

    @Override
    public MountedRod mounted() {
        return rod;
    }

    @Override
    public BlockEntity blockEntity() {
        return this;
    }

    /** Bait slot then fuel slot, for hoppers and funnels. */
    public IItemHandler items() {
        return items;
    }

    public ItemStackHandler fuelSlot() {
        return fuelSlot;
    }

    public IFluidHandler tank() {
        return tank;
    }

    /** Fuel left in ticks at full load, as last synced. */
    public int fuelTicks() {
        return fuelTicks;
    }

    public int tankMb() {
        return tank.getFluidAmount();
    }

    public boolean running() {
        return running;
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            ReelDrive.CLIENT_DRIVES.add(this);
            if (running && level.random.nextInt(3) == 0) {
                Vec3 at = exhaust();
                level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.04, 0);
            }
            return;
        }
        settings.applyTo(drive);
        rod.tick();
        drive.tick();
        boolean nowRunning = drive.on() && !rod.rod().isEmpty() && (rod.hook() != null || drive.willCast()) && hasFuel();
        if (nowRunning) burnLeft -= IDLE_BURN + (1 - IDLE_BURN) * load;
        int nowFuel = (int) Math.max(0, burnLeft) + storedTicks();
        boolean changed = drive.takeDirty() || nowRunning != running;
        if (--syncIn <= 0 && Math.abs(nowFuel - fuelTicks) >= 20) {
            changed = true;
            syncIn = 20;
        }
        running = nowRunning;
        if (changed) {
            fuelTicks = nowFuel;
            sendData();
        }
    }

    /** True if the engine has fuel burning or can take more from the tank or the slot. */
    private boolean hasFuel() {
        if (burnLeft > 0) return true;
        FluidStack fluid = tank.getFluid();
        double perMb = fluid.isEmpty() ? 0 : LiquidFuels.ticksPerMb(fluid.getFluid());
        if (perMb > 0) {
            int mb = Math.min(100, fluid.getAmount());
            tank.drain(mb, IFluidHandler.FluidAction.EXECUTE);
            burnLeft += mb * perMb;
            return true;
        }
        ItemStack stack = fuelSlot.getStackInSlot(0);
        int burn = stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
        if (burn <= 0) return false;
        ItemStack left = stack.getCraftingRemainingItem();
        fuelSlot.extractItem(0, 1, false);
        if (!left.isEmpty()) {
            // A lava bucket's empty bucket stays in the slot for a hopper to take, if there's room.
            if (fuelSlot.getStackInSlot(0).isEmpty()) fuelSlot.setStackInSlot(0, left);
            else Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, left);
        }
        burnLeft += burn;
        return true;
    }

    /** Full-load ticks of fuel waiting in the tank and the slot. */
    private int storedTicks() {
        FluidStack fluid = tank.getFluid();
        double ticks = fluid.isEmpty() ? 0 : fluid.getAmount() * LiquidFuels.ticksPerMb(fluid.getFluid());
        ItemStack stack = fuelSlot.getStackInSlot(0);
        if (!stack.isEmpty()) ticks += (double) stack.getBurnTime(RecipeType.SMELTING) * stack.getCount();
        return (int) Math.min(Integer.MAX_VALUE, ticks);
    }

    /** Where the smoke comes out: the top of the stack at the back. */
    private Vec3 exhaust() {
        Direction back = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        return Vec3.atCenterOf(worldPosition).add(back.getStepX() * 0.3, 0.75, back.getStepZ() * 0.3);
    }

    /** The engine: a fixed rating while it has fuel. */
    private final class Engine implements ReelDrive.Power {
        @Override
        public double torqueAtOne() {
            return hasFuel() ? RATING : 0;
        }

        @Override
        public double speedScale() {
            return burnLeft > 0 ? SPEED : 0;
        }

        @Override
        public boolean driven() {
            return burnLeft > 0;
        }

        @Override
        public void load(double kgf, double gear) {
            load = Math.min(1, kgf * gear / RATING);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        rod.removed();
    }

    @Override
    public void destroy() {
        super.destroy();
        rod.dropContents();
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), drive.bait().getStackInSlot(0));
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), fuelSlot.getStackInSlot(0));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return MachineInfo.describe(tooltip, rod, drive.info(true, fuelTicks, tank.getFluidAmount()));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        rod.write(tag, registries);
        drive.write(tag, registries, clientPacket);
        tag.put("fuel", fuelSlot.serializeNBT(registries));
        tag.put("tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("burn_left", burnLeft);
        if (clientPacket) {
            tag.putBoolean("running", running);
            tag.putInt("fuel_ticks", fuelTicks);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rod.read(tag, registries);
        drive.read(tag, registries, clientPacket);
        if (tag.contains("fuel")) fuelSlot.deserializeNBT(registries, tag.getCompound("fuel"));
        tank.readFromNBT(registries, tag.getCompound("tank"));
        burnLeft = tag.getDouble("burn_left");
        if (clientPacket) {
            running = tag.getBoolean("running");
            fuelTicks = tag.getInt("fuel_ticks");
        }
    }
}
