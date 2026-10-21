package dev.creategalore.fishing.client;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.machine.FuelReelDriveBlockEntity;
import dev.creategalore.fishing.machine.ReelDrive;
import dev.creategalore.fishing.machine.ReelDriveBlockEntity;
import dev.creategalore.fishing.machine.RodMount;
import dev.creategalore.fishing.sim.Gearbox;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The fight's continuous sounds, worked out on each client from the hooks' synced state so they follow the action
 * exactly and cost no network traffic:
 * <ul>
 *   <li>drag ratchet: one click per {@link #CM_PER_CLICK} cm of line actually going out while the drag slips, so the
 *       click rate is the fish's run speed; on a fast run ({@link #SCREAM_SPEED} m/s and up) the clicks run together
 *       into the drag's scream, a loop pitched up with the speed; pitched by the reel (copper rattly, brass smooth,
 *       big-game deep);</li>
 *   <li>reel crank: a looping whirr while reeling, slower and lower as the load rises;</li>
 *   <li>grind: cranking against a slipping drag past the half-second grace, while it wears the reel;</li>
 *   <li>line strain: creaks that start at about 65% of the breaking load and come faster and louder toward a snap.</li>
 * </ul>
 * One-off sounds (cast, splash, bite, strike, snap) are played by the server.
 */
public final class FishingSounds {
    private static final double CM_PER_CLICK = 12;
    /** Line speed (m/s) from which the drag screams instead of clicking. */
    private static final double SCREAM_SPEED = 0.8;
    private static final double HEAR_DISTANCE = 48;

    private static final class State {
        float lastLineOut = -1;
        double payout;
        /** Line going out, m/s, smoothed. */
        double speed;
        Crank crank;
        Scream scream;
        int strainCooldown;
        int grindCooldown;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<BlockEntity, DriveLoop> LOOPS = new HashMap<>();

    private FishingSounds() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            if (level == null) clear();
            return;
        }
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof CastHook hook) || e.distanceTo(mc.player) > HEAR_DISTANCE) continue;
            Angler angler = Angler.of(hook);
            if (angler == null) continue;
            State s = STATES.computeIfAbsent(hook.getId(), id -> new State());
            sounds(mc, hook, angler, s);
        }
        drives(mc, level);
        // A held fish flaps now and then.
        for (Player p : level.players()) {
            if (p.distanceTo(mc.player) < HEAR_DISTANCE && HoldPose.holding(p) && p.getRandom().nextInt(40) == 0) {
                play(p, FishingRegistry.CATCH_FLOP.get(), 0.5f, 0.85f + p.getRandom().nextFloat() * 0.3f);
            }
        }
        for (Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, State> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (!(entity instanceof CastHook) || entity.isRemoved()) {
                if (e.getValue().crank != null) e.getValue().crank.finish();
                if (e.getValue().scream != null) e.getValue().scream.finish();
                it.remove();
            }
        }
    }

    private static void clear() {
        STATES.values().forEach(s -> {
            if (s.crank != null) s.crank.finish();
            if (s.scream != null) s.scream.finish();
        });
        STATES.clear();
        LOOPS.values().forEach(DriveLoop::finish);
        LOOPS.clear();
        ReelDrive.CLIENT_DRIVES.clear();
    }

    /**
     * Who holds the line, for where its sounds come from: the player, or the machine the rod is set in (a machine's
     * angler doesn't exist on clients).
     */
    record Angler(Supplier<Vec3> at, ItemStack rod, RandomSource random, BooleanSupplier gone) {
        static Angler of(CastHook hook) {
            Player owner = hook.getPlayerOwner();
            if (owner != null) {
                return new Angler(() -> new Vec3(owner.getX(), owner.getEyeY() - 0.4, owner.getZ()), owner.getItemInHand(hook.hand()),
                        owner.getRandom(), owner::isRemoved);
            }
            BlockPos pos = hook.mountPos().orElse(null);
            if (pos == null || !(hook.level().getBlockEntity(pos) instanceof RodMount mount)) return null;
            Vec3 at = Vec3.atCenterOf(pos).add(0, 0.6, 0);
            BlockEntity be = (BlockEntity) mount;
            return new Angler(() -> at, mount.mounted().rod(), hook.getRandom(), be::isRemoved);
        }
    }

    private static void sounds(Minecraft mc, CastHook hook, Angler owner, State s) {
        RandomSource random = owner.random();
        float out = hook.lineOut();
        boolean fighting = hook.phase() == CastHook.Phase.FIGHTING;
        boolean slipping = fighting && hook.flag(CastHook.FLAG_SLIPPING);
        boolean reeling = hook.flag(CastHook.FLAG_REELING);
        float reelPitch = reelPitch(owner.rod());

        double gone = s.lastLineOut >= 0 && slipping ? Math.max(0, out - s.lastLineOut) : 0;
        s.speed = 0.6 * s.speed + 0.4 * gone * 20;
        boolean screaming = slipping && s.speed >= SCREAM_SPEED;
        if (screaming && (s.scream == null || s.scream.isStopped())) {
            s.scream = new Scream(owner, hook, s, reelPitch);
            mc.getSoundManager().play(s.scream);
        } else if (!screaming && s.scream != null) {
            s.scream.finish();
            s.scream = null;
        }
        if (screaming) {
            s.payout = 0;
        } else if (s.lastLineOut >= 0 && slipping) {
            s.payout += gone * 100;
            int clicks = 0;
            while (s.payout >= CM_PER_CLICK && clicks < 4) {
                s.payout -= CM_PER_CLICK;
                clicks++;
                play(owner, FishingRegistry.DRAG_CLICK.get(), 0.45f, reelPitch * (0.92f + random.nextFloat() * 0.16f));
            }
            if (s.payout > CM_PER_CLICK * 4) s.payout = 0;
        } else {
            s.payout = 0;
        }
        s.lastLineOut = out;

        boolean cranking = reeling && hook.phase() != CastHook.Phase.FLYING;
        if (cranking && (s.crank == null || s.crank.isStopped())) {
            s.crank = new Crank(owner, hook, reelPitch);
            mc.getSoundManager().play(s.crank);
        } else if (!cranking && s.crank != null) {
            s.crank.finish();
            s.crank = null;
        }

        if (s.grindCooldown > 0) s.grindCooldown--;
        if (fighting && hook.flag(CastHook.FLAG_GRINDING) && s.grindCooldown == 0) {
            play(owner, FishingRegistry.REEL_GRIND.get(), 0.5f, reelPitch * 0.9f);
            s.grindCooldown = 8;
        }

        if (s.strainCooldown > 0) s.strainCooldown--;
        float tension = fighting ? hook.shownTension() : 0;
        if (tension > 0.65f && s.strainCooldown == 0) {
            float over = Math.min(1, (tension - 0.65f) / 0.35f);
            play(owner, FishingRegistry.LINE_STRAIN.get(), 0.25f + 0.55f * over, 0.9f + 0.35f * over);
            s.strainCooldown = (int) (16 - 11 * over) + random.nextInt(4);
        }
    }

    private static void play(Player at, SoundEvent sound, float volume, float pitch) {
        at.level().playLocalSound(at.getX(), at.getEyeY() - 0.4, at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static void play(Angler angler, SoundEvent sound, float volume, float pitch) {
        Vec3 at = angler.at().get();
        Minecraft.getInstance().level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    /**
     * The drives' own sounds: a shaft drive hums while its reel winds (higher with shaft speed and gear, a low groan
     * when it stalls), the fuel drive's engine putters while it runs and revs while it winds.
     */
    private static void drives(Minecraft mc, ClientLevel level) {
        for (Iterator<BlockEntity> it = ReelDrive.CLIENT_DRIVES.iterator(); it.hasNext(); ) {
            BlockEntity be = it.next();
            if (be.isRemoved() || be.getLevel() != level) {
                it.remove();
                continue;
            }
            boolean near = Vec3.atCenterOf(be.getBlockPos()).distanceTo(mc.player.position()) < HEAR_DISTANCE;
            boolean on = near && DriveLoop.sounding(be);
            DriveLoop loop = LOOPS.get(be);
            if (on && (loop == null || loop.isStopped())) {
                loop = new DriveLoop(be);
                LOOPS.put(be, loop);
                mc.getSoundManager().play(loop);
            } else if (!on && loop != null) {
                loop.finish();
                LOOPS.remove(be);
            }
        }
        LOOPS.entrySet().removeIf(e -> e.getValue().isStopped());
    }

    /** A drive's motor hum or engine putter, looping while it runs. */
    static final class DriveLoop extends AbstractTickableSoundInstance {
        private final BlockEntity be;

        DriveLoop(BlockEntity be) {
            super(be instanceof FuelReelDriveBlockEntity ? FishingRegistry.MACHINE_ENGINE.get() : FishingRegistry.MACHINE_MOTOR.get(),
                    SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.be = be;
            looping = true;
            delay = 0;
            Vec3 at = Vec3.atCenterOf(be.getBlockPos());
            x = at.x;
            y = at.y;
            z = at.z;
            tick();
        }

        static boolean sounding(BlockEntity be) {
            if (be instanceof FuelReelDriveBlockEntity engine) return engine.running();
            return be instanceof ReelDriveBlockEntity drive && drive.drive().winding();
        }

        @Override
        public void tick() {
            if (be.isRemoved()) {
                stop();
                return;
            }
            if (be instanceof FuelReelDriveBlockEntity engine) {
                volume = 0.4f;
                pitch = engine.drive().winding() ? (engine.drive().stalled() ? 0.75f : 1.15f) : 0.9f;
            } else if (be instanceof ReelDriveBlockEntity drive) {
                double turn = Math.abs(drive.getSpeed()) / ReelDriveBlockEntity.BASE_RPM * Gearbox.GEARS[drive.drive().gear()];
                volume = 0.3f;
                pitch = drive.drive().stalled() ? 0.5f : (float) Mth.clamp(0.7 + 0.3 * turn, 0.6, 1.6);
            }
        }

        void finish() {
            stop();
        }
    }

    /** Copper reels rattle high, brass purrs, big-game reels are deep. */
    static float reelPitch(ItemStack rod) {
        if (!(rod.getItem() instanceof RodItem)) return 1;
        ItemStack reel = RodItem.parts(rod).get(RodParts.Slot.REEL);
        if (reel.isEmpty()) return 1.1f;
        return switch (BuiltInRegistries.ITEM.getKey(reel.getItem()).getPath()) {
            case "copper_reel" -> 1.2f;
            case "brass_reel" -> 0.95f;
            case "steel_baitcaster" -> 1.05f;
            case "trolling_reel" -> 0.72f;
            default -> 1.0f;
        };
    }

    /** The drag screaming while a fish runs: higher and louder the faster line goes out. */
    static final class Scream extends AbstractTickableSoundInstance {
        private final Angler owner;
        private final CastHook hook;
        private final State state;
        private final float basePitch;

        Scream(Angler owner, CastHook hook, State state, float basePitch) {
            super(FishingRegistry.DRAG_SCREAM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.owner = owner;
            this.hook = hook;
            this.state = state;
            this.basePitch = basePitch;
            looping = true;
            delay = 0;
            tick();
        }

        @Override
        public void tick() {
            if (owner.gone().getAsBoolean() || hook.isRemoved()) {
                stop();
                return;
            }
            Vec3 at = owner.at().get();
            x = at.x;
            y = at.y;
            z = at.z;
            float speed = (float) state.speed;
            pitch = basePitch * Mth.clamp(0.55f + 0.22f * speed, 0.55f, 1.6f);
            volume = Mth.clamp(0.25f + 0.1f * speed, 0.25f, 0.6f);
        }

        void finish() {
            stop();
        }
    }

    /** The looping crank whirr that follows the angler while they reel; heavier load, slower and lower. */
    static final class Crank extends AbstractTickableSoundInstance {
        private final Angler owner;
        private final CastHook hook;
        private final float basePitch;

        Crank(Angler owner, CastHook hook, float basePitch) {
            super(FishingRegistry.REEL_CRANK.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.owner = owner;
            this.hook = hook;
            this.basePitch = basePitch;
            looping = true;
            delay = 0;
            volume = 0.35f;
            pitch = basePitch;
            follow();
        }

        private void follow() {
            Vec3 at = owner.at().get();
            x = at.x;
            y = at.y;
            z = at.z;
        }

        @Override
        public void tick() {
            if (owner.gone().getAsBoolean() || hook.isRemoved()) {
                stop();
                return;
            }
            follow();
            float load = hook.phase() == CastHook.Phase.FIGHTING ? Math.min(1, hook.shownTension()) : 0;
            boolean stalled = hook.flag(CastHook.FLAG_STALLED);
            pitch = basePitch * (stalled ? 0.6f : 1.05f - 0.35f * load);
            volume = 0.3f + 0.2f * load;
        }

        void finish() {
            stop();
        }
    }
}
