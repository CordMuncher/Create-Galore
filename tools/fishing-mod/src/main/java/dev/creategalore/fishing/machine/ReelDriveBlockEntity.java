package dev.creategalore.fishing.machine;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Basic and Geared Reel Drives' block entity: a shaft runs through the drive, and its rotation turns the reel.
 *
 * <p>Winding against a fish costs stress, live: {@link #IDLE} SU per RPM while the reel just turns, plus {@link #K}
 * SU per RPM for every kgf of tension times the gear while it cranks against the fish. So the drag the rod was set
 * to (the most a fish can pull before line slips) has to suit the rotation the drive is on: too high, and a fish
 * pulling against it overstresses the network, which stops (all of it, as Create does) until the fish eases off.
 * The Geared drive's gearbox steps down to avoid that where it can. The stress is updated at most every half second
 * and only when it changes by a tenth, so busy networks don't churn.
 */
public class ReelDriveBlockEntity extends KineticBlockEntity implements DriveMount {
    /** Stress per RPM for each kgf of tension at gear 1.0. */
    public static final double K = 4;
    /** Stress per RPM of a drive that isn't cranking against a fish. */
    public static final double IDLE = 1;
    /** Shaft speed at which the reel turns at its own speed (faster or slower shafts scale it, within 1/4 to 3 times). */
    public static final float BASE_RPM = 32;

    /** Stress per RPM of a drive winding against {@code kgf} of tension at gear {@code gear} (0 kgf: not cranking). */
    public static double impact(double kgf, double gear) {
        return IDLE + K * kgf * gear;
    }

    /**
     * The pull in kgf a drive turning at {@code rpm} can wind against in gear 1.0, when its network can spare
     * {@code spare} SU for it (what it uses now included): the most that keeps the network from overstressing.
     */
    public static double torqueAtOne(double spare, double rpm) {
        return rpm < 0.01 ? 0 : Math.max(0, (spare / rpm - IDLE) / K);
    }

    private DriveSlots.Settings settings;
    private final MountedRod rod;
    private final ReelDrive drive;
    private float impact = (float) IDLE;
    private int sinceStressUpdate;

    public ReelDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        rod = new MountedRod(this, this);
        drive = new ReelDrive(geared(state) ? ReelDrive.Kind.GEARED : ReelDrive.Kind.BASIC, this, this, rod, new ShaftPower());
    }

    private static boolean geared(BlockState state) {
        return state.getBlock() instanceof ReelDriveBlock block && block.geared();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // Runs from Create's constructor, before this class's fields are set up: only the settings may be made here.
        if (!geared(getBlockState())) return;
        settings = DriveSlots.create(this);
        settings.addTo(behaviours);
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

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            ReelDrive.CLIENT_DRIVES.add(this);
            return;
        }
        if (settings != null) settings.applyTo(drive);
        rod.tick();
        drive.tick();
        if (drive.takeDirty()) sendData();
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = impact;
        return impact;
    }

    /** The shaft's side of the drive: torque is what the network can still spare, as stress. */
    private final class ShaftPower implements ReelDrive.Power {
        @Override
        public double torqueAtOne() {
            float rpm = Math.abs(getTheoreticalSpeed());
            if (isOverStressed()) return 0;
            return ReelDriveBlockEntity.torqueAtOne(capacity - stress + impact * rpm, rpm);
        }

        @Override
        public double speedScale() {
            float rpm = Math.abs(getSpeed());
            return rpm < 0.01f ? 0 : Mth.clamp(rpm / BASE_RPM, 0.25, 3);
        }

        @Override
        public boolean driven() {
            return Math.abs(getTheoreticalSpeed()) > 0.01f;
        }

        @Override
        public void load(double kgf, double gear) {
            sinceStressUpdate++;
            float wanted = (float) impact(kgf, gear);
            if (sinceStressUpdate < 10 || Math.abs(wanted - impact) <= Math.max(0.1f * impact, 0.25f)) return;
            impact = wanted;
            sinceStressUpdate = 0;
            if (hasNetwork()) getOrCreateNetwork().updateStressFor(ReelDriveBlockEntity.this, impact);
            sendData();
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
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return MachineInfo.describe(tooltip, rod, drive.info(false, 0, 0));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        rod.write(tag, registries);
        drive.write(tag, registries, clientPacket);
        tag.putFloat("impact", impact);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rod.read(tag, registries);
        drive.read(tag, registries, clientPacket);
        // No fish is on after a reload (hooks aren't saved): back to idle; Create re-adds it to its network with this.
        impact = clientPacket ? tag.getFloat("impact") : (float) IDLE;
        if (!clientPacket) lastStressApplied = impact;
    }
}
