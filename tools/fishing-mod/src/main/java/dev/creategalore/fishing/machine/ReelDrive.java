package dev.creategalore.fishing.machine;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.bait.LiveBait;
import dev.creategalore.fishing.bait.LiveBaitBucketItem;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.compat.SableShips;
import dev.creategalore.fishing.sim.Controls;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.Gearbox;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TackleManager;
import dev.creategalore.fishing.tackle.TacklePart;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * What the three Reel Drives share. The Basic drive only cranks: a fish someone hooked by hand and set in is wound in
 * at the shaft's speed, even while the drag slips (so the reel wears fast). The Geared and Fuel drives have an
 * automatic {@link Gearbox}, never crank while the drag slips, strike a bite themselves, and cast: in
 * {@link DriveMode#CAST} they lob the rig out and wind it back slowly to work a lure, in {@link DriveMode#WAIT} they
 * cast once and hold. They re-bait from a bait slot (filled by hand, hopper or funnel) before each cast.
 *
 * <p>A drive can be switched off (it then neither casts, strikes nor winds, as if its power were cut), and a geared
 * one can have its autocast off: it then casts once each time it is switched on or told to, and leaves its line in once
 * it comes back. Both are set in the drive's window, as is whether a lost fish is reported in chat. The Basic drive's
 * window has only the on/off switch and the reports.
 *
 * <p>The torque the reel needs is the line's tension times the gear. Where it comes from is the drive's
 * {@link Power}: Create rotation (as stress) for the shaft drives, an engine for the fuel drive. A fish that pulls
 * more than that stalls the reel until it eases off.
 */
public final class ReelDrive {
    public enum Kind {
        BASIC, GEARED, FUEL;

        public boolean geared() {
            return this != BASIC;
        }
    }

    /** Where a drive's turning force comes from. */
    public interface Power {
        /** The pull in kgf the reel can wind against in gear 1.0 right now; 0 if it is stopped or stalled. */
        double torqueAtOne();

        /** Retrieve speed from the input, as a multiple of the reel's own before gearing; 0 if it isn't turning. */
        double speedScale();

        /** Whether the input is meant to turn (a shaft is driven, the engine has fuel), stalled or not. */
        boolean driven();

        /** What the reel winds against this tick: {@code kgf} of tension at gear {@code gear} (0 while it isn't cranking). */
        void load(double kgf, double gear);
    }

    /** Ticks between a geared drive feeling a bite and striking. */
    public static final int STRIKE_DELAY = 6;
    /** Ticks a geared drive waits after its line comes back before casting again. */
    public static final int RECAST_DELAY = 60;
    /** A bite on a geared drive lasts as long as one by hand would; it strikes long before. */
    public static final int GEARED_BITE_TICKS = 40;
    /** Retrieve speed in Cast mode, as a multiple of the reel's: slow, to work a lure. */
    public static final double CAST_RETRIEVE = 0.45;
    /** The rod is held at a good working angle to the line. */
    private static final double ROD_ANGLE = Math.toRadians(40);

    /** Client side: the drives in loaded chunks, for their motor and engine sounds. */
    public static final Set<BlockEntity> CLIENT_DRIVES = Collections.newSetFromMap(new WeakHashMap<>());

    private final Kind kind;
    private final BlockEntity be;
    private final RodMount mount;
    private final MountedRod rod;
    private final Power power;
    private final ItemStackHandler bait;

    private DriveMode mode = DriveMode.WAIT;
    private boolean on = true;
    private boolean autocast = true;
    private boolean reportLosses;
    /** Autocast off: a cast is waiting, from switching the drive on or the window's Cast button. */
    private boolean castArmed;
    private int castDistance = 12;
    private int topGear = Gearbox.GEARS.length - 1;
    private Gearbox gearbox = new Gearbox(0, topGear);
    private int biteAge;
    private int recastIn;
    private boolean lineWasOut;
    private boolean cranking;
    private int stallSoundIn;
    private int syncIn;

    // Synced to clients for the goggles and sounds.
    private int gear = gearbox.index();
    private double headroomKg;
    private boolean stalled;
    private boolean winding;
    private boolean dirty;

    public ReelDrive(Kind kind, BlockEntity be, RodMount mount, MountedRod rod, Power power) {
        this.kind = kind;
        this.be = be;
        this.mount = mount;
        this.rod = rod;
        this.power = power;
        this.bait = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return isBait(stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                be.setChanged();
            }
        };
    }

    public Kind kind() {
        return kind;
    }

    /** The bait slot (geared drives): bait items or a Live-Bait Bucket. */
    public ItemStackHandler bait() {
        return bait;
    }

    public static boolean isBait(ItemStack stack) {
        return stack.getItem() instanceof LiveBaitBucketItem
                || TackleManager.of(stack).map(p -> p.kind() == TacklePart.Kind.BAIT).orElse(false);
    }

    /** The settings from the drive's value boxes, read each tick. {@code top} is an index into {@link Gearbox#GEARS}. */
    public void configure(DriveMode mode, int castDistance, int top) {
        this.mode = mode;
        this.castDistance = castDistance;
        if (top != topGear) {
            topGear = top;
            gearbox = new Gearbox(0, top);
        }
    }

    public boolean on() {
        return on;
    }

    public boolean autocast() {
        return autocast;
    }

    /** Switches the drive on or off; switching it on with autocast off lets it cast once. */
    public void setOn(boolean on) {
        if (on && !this.on) castArmed = true;
        this.on = on;
        changed();
    }

    /** Whether a lost fish is reported in chat to the player who set the rod in (off by default). */
    public boolean reportLosses() {
        return reportLosses;
    }

    public void setReportLosses(boolean report) {
        reportLosses = report;
        changed();
    }

    public void setAutocast(boolean autocast) {
        this.autocast = autocast;
        castArmed = false;
        changed();
    }

    /** The window's Cast button (autocast off): cast once, now, if the line is in. */
    public void castOnce() {
        castArmed = true;
        recastIn = 0;
        changed();
    }

    private void changed() {
        dirty = true;
        be.setChanged();
    }

    /** What the power can wind against now: nothing while the drive is switched off. */
    private double torque() {
        return on ? power.torqueAtOne() : 0;
    }

    public double gearRatio() {
        return kind.geared() ? gearbox.ratio() : 1;
    }

    // ---- what the hook asks (see RodMount) ----

    /** Outside a fight only geared drives wind: a lure worked back in Cast mode, or a rig that landed on the bank. */
    public boolean reeling(CastHook hook) {
        if (!kind.geared() || torque() <= 0) return false;
        return mode == DriveMode.CAST || hook.phase() == CastHook.Phase.GROUNDED;
    }

    public double retrieveScale(CastHook hook) {
        return (hook.phase() == CastHook.Phase.GROUNDED ? 1 : CAST_RETRIEVE) * (on ? power.speedScale() : 0);
    }

    public int biteTicks() {
        return kind.geared() ? GEARED_BITE_TICKS : RodHolderBlockEntity.BITE_TICKS;
    }

    public void bite(CastHook hook) {
        biteAge = 0;
        rod.alarm();
    }

    /** Geared drives strike after a moment; the Basic drive can't, so it keeps sounding the alarm like a holder. */
    public boolean strikes(CastHook hook) {
        if (!kind.geared()) {
            if (be.getLevel().getGameTime() % 20 == 0) rod.alarm();
            return false;
        }
        return ++biteAge > STRIKE_DELAY && torque() > 0;
    }

    public void fight(Fight fight, Controls c) {
        double torque = torque();
        if (kind.geared()) {
            gearbox.update(torque, fight);
            gearbox.apply(c, torque);
            c.retrieveScale *= on ? power.speedScale() : 0;
            c.reeling = on && power.driven() && !fight.slipping();
            c.crankThroughSlip = false;
        } else {
            c.crankLimit = torque;
            c.retrieveScale = power.speedScale();
            c.reeling = on && power.driven();
            c.crankThroughSlip = true;
        }
        c.rodAngle = ROD_ANGLE;
        cranking = c.reeling;
    }

    // ---- ticking (server) ----

    public void tick() {
        Level level = be.getLevel();
        CastHook hook = rod.hook();
        Fight fight = hook != null && hook.phase() == CastHook.Phase.FIGHTING ? hook.fight() : null;
        if (fight == null) cranking = false;
        power.load(cranking ? fight.tension() : 0, gearRatio());

        boolean nowStalled = cranking && fight.stalled();
        if (stallSoundIn > 0) stallSoundIn--;
        if (nowStalled && !stalled && stallSoundIn == 0) {
            level.playSound(null, be.getBlockPos(), FishingRegistry.MACHINE_STALL.get(), SoundSource.BLOCKS, 0.8f, 1f);
            stallSoundIn = 30;
        }
        if (kind.geared() && gearbox.index() != gear) {
            level.playSound(null, be.getBlockPos(), FishingRegistry.MACHINE_SHIFT.get(), SoundSource.BLOCKS, 0.6f,
                    gearbox.index() > gear ? 1.1f : 0.9f);
        }
        boolean nowWinding = hook != null && hook.flag(CastHook.FLAG_REELING);
        double nowHeadroom = torque() / gearRatio();
        if (nowStalled != stalled || nowWinding != winding || gearbox.index() != gear
                || (Math.abs(nowHeadroom - headroomKg) > 0.25 && syncIn <= 0)) {
            stalled = nowStalled;
            winding = nowWinding;
            gear = gearbox.index();
            headroomKg = nowHeadroom;
            dirty = true;
            syncIn = 10;
        }
        if (syncIn > 0) syncIn--;

        if (hook != null) {
            lineWasOut = true;
            return;
        }
        if (lineWasOut) {
            lineWasOut = false;
            recastIn = RECAST_DELAY;
        }
        if (kind.geared()) castWhenReady();
    }

    /** True once if what clients see has changed (the block entity then sends an update). */
    public boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    private void castWhenReady() {
        if (recastIn > 0) {
            recastIn--;
            return;
        }
        if (!willCast() || torque() <= 0) return;
        rebait();
        RodStats stats = rod.stats();
        // The bait in store may not be what's on the hook (and an empty hook isn't worth casting).
        if (!lure(stats) && RodItem.parts(rod.rod()).bait().isEmpty()) return;
        Direction facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        // On a ship, out over the side the drive faces as the ship is turned now.
        Vec3 out = SableShips.directionToWorld(be.getLevel(), Vec3.atCenterOf(be.getBlockPos()),
                new Vec3(facing.getStepX(), 0, facing.getStepZ()));
        CastHook.castFrom(rod.angler(), stats, mount.tip(), out, castDistance);
        recastIn = 20;
        castArmed = false;
    }

    /**
     * True if a geared drive with its line in would cast: a rod with a reel is set in, no fish hangs at the tip, and
     * there is a lure on, or bait on the rod or in store.
     */
    public boolean wantsToCast() {
        if (!kind.geared() || rod.rod().isEmpty() || !rod.dangling().isEmpty()) return false;
        RodStats stats = rod.stats();
        if (stats == null || stats.isPole()) return false;
        return lure(stats) || !RodItem.parts(rod.rod()).bait().isEmpty() || !bait.getStackInSlot(0).isEmpty();
    }

    /** True if the drive would cast now its line is in: it wants to, is switched on, and autocasts or was told to. */
    public boolean willCast() {
        return on && (autocast || castArmed) && wantsToCast();
    }

    private static boolean lure(RodStats stats) {
        return stats.terminal().map(t -> t.kind() == TacklePart.Kind.LURE).orElse(false);
    }

    /** Tops the rod's bait up from the bait slot: more of the same bait, or any bait if the rod has none. */
    private void rebait() {
        ItemStack stack = rod.rod();
        RodParts parts = RodItem.parts(stack);
        ItemStack fitted = parts.bait();
        ItemStack store = bait.getStackInSlot(0);
        if (store.isEmpty()) return;
        int room = Math.min(RodItem.BAIT_CAPACITY, fitted.isEmpty() ? RodItem.BAIT_CAPACITY : fitted.getMaxStackSize()) - fitted.getCount();
        if (room <= 0) return;
        if (store.getItem() instanceof LiveBaitBucketItem) {
            if (!fitted.isEmpty() && !LiveBait.isLive(fitted)) return;
            ItemStack bucket = store.copy();
            ItemStack taken = LiveBaitBucketItem.takeBait(bucket, room, fitted);
            if (taken.isEmpty()) return;
            if (!fitted.isEmpty() && !ItemStack.isSameItemSameComponents(fitted, taken)) {
                LiveBaitBucketItem.putBack(bucket, taken);
                return;
            }
            if (fitted.isEmpty()) fitted = taken;
            else fitted.grow(taken.getCount());
            bait.setStackInSlot(0, bucket);
        } else {
            if (!fitted.isEmpty() && !ItemStack.isSameItemSameComponents(fitted, store)) return;
            ItemStack taken = bait.extractItem(0, room, false);
            if (fitted.isEmpty()) fitted = taken;
            else fitted.grow(taken.getCount());
        }
        RodItem.setParts(stack, parts.with(RodParts.Slot.BAIT, fitted));
        rod.changed();
    }

    // ---- goggles ----

    public MachineInfo.Drive info(boolean fuelled, int fuelTicks, int tankMb) {
        return new MachineInfo.Drive(kind.geared(), gear, headroomKg, stalled, bait.getStackInSlot(0), fuelled, fuelTicks, tankMb,
                on, autocast);
    }

    /** The gear in use, an index into {@link Gearbox#GEARS} (synced, for clients). */
    public int gear() {
        return gear;
    }

    public boolean winding() {
        return winding;
    }

    public boolean stalled() {
        return stalled;
    }

    /** One line for {@code /cgfish machine}: gear, what it can wind against, whether it cranks or stalled. */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "%s gear %.2f, winds against %.1f kgf, cranking %s, stalled %s, recast in %d, on %s, autocast %s",
                kind, gearRatio(), torque() / gearRatio(), cranking, stalled, recastIn, on, autocast);
    }

    // ---- saving ----

    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("bait", bait.serializeNBT(registries));
        tag.putBoolean("on", on);
        tag.putBoolean("autocast", autocast);
        tag.putBoolean("cast_armed", castArmed);
        tag.putBoolean("report_losses", reportLosses);
        if (clientPacket) {
            tag.putInt("gear", gear);
            tag.putDouble("headroom", headroomKg);
            tag.putBoolean("stalled", stalled);
            tag.putBoolean("winding", winding);
        }
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (tag.contains("bait")) bait.deserializeNBT(registries, tag.getCompound("bait"));
        // Drives from before the switches were added are on, with autocast.
        on = !tag.contains("on") || tag.getBoolean("on");
        autocast = !tag.contains("autocast") || tag.getBoolean("autocast");
        castArmed = tag.getBoolean("cast_armed");
        reportLosses = tag.getBoolean("report_losses");
        if (clientPacket) {
            gear = tag.getInt("gear");
            headroomKg = tag.getDouble("headroom");
            stalled = tag.getBoolean("stalled");
            winding = tag.getBoolean("winding");
        }
    }
}
