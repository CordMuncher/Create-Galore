package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.bait.Chum;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.machine.MachineAngler;
import dev.creategalore.fishing.machine.MachineInfo;
import dev.creategalore.fishing.machine.RodMount;
import dev.creategalore.fishing.sim.Controls;
import dev.creategalore.fishing.sim.Curve;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.FishFight;
import dev.creategalore.fishing.sim.Loss;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.sim.SizeModel;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TacklePart;
import dev.creategalore.fishing.tide.data.FishingCondition;import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.FishingPlace;
import dev.creategalore.fishing.tide.data.fishing.CatchTables;
import dev.creategalore.fishing.tide.data.fishing.CrateData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The cast rig in the world: the float, sinker or lure at the end of the line, and during a fight the hooked fish.
 *
 * <p>Extends vanilla's {@link FishingHook} so the rest of the game sees an ordinary fishing hook: the owner's
 * {@code player.fishing}, vanilla's "fishing rod hooked" advancement trigger, NeoForge's {@code ItemFishedEvent} and
 * other mods that look for a hook all keep working. Everything vanilla does in its own tick is replaced.
 *
 * <p>Phases: {@link Phase#FLYING} after the cast, {@link Phase#WAITING} in the water (holding use reels the rig in,
 * which also works a lure), {@link Phase#BITE} while a fish has taken it (use within the window to strike),
 * {@link Phase#FIGHTING} until the {@link Fight} ends, and {@link Phase#GROUNDED} if the cast landed on land. The
 * server runs all of it; clients get the state through synced data and draw it.
 */
public class CastHook extends FishingHook {
    public enum Phase { FLYING, WAITING, BITE, FIGHTING, GROUNDED }

    /** What kind of rig is on the line, for drawing it. */
    public enum Rig { HOOK, FLOAT, SINKER, LURE }

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> RIG = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.BYTE);
    /** Tension as a fraction of the breaking load (1 = snap). */
    private static final EntityDataAccessor<Float> TENSION = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DRAG = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BREAK = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROD_LOAD = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    /** How hard the reel works, as a fraction of what it can take (negative: no reel). */
    private static final EntityDataAccessor<Float> REEL_LOAD = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LINE_OUT = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LINE_LEFT = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.BYTE);
    /** The hooked fish's item id while fighting (empty otherwise), so clients can draw it. */
    private static final EntityDataAccessor<String> FISH = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> FISH_CM = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FISH_KG = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    /** The machine holding the rod, when one does (its angler is a server-side fake player clients can't see). */
    private static final EntityDataAccessor<Optional<BlockPos>> MOUNT = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** The angler's entity id (-1 for a machine), so clients follow the line when it changes hands. */
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.INT);
    /** How high the rod is lifted, 0..1 (see {@link RodLift}), so everyone sees it come up. */
    private static final EntityDataAccessor<Float> ROD_HEIGHT = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    /** Metres of slack line while fighting, so clients draw it sagging. */
    private static final EntityDataAccessor<Float> SLACK = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    /** What the hooked fish is doing ({@link Fight.Mood} ordinal), for the HUD. */
    private static final EntityDataAccessor<Byte> MOOD = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.BYTE);

    public static final int FLAG_SLIPPING = 1;
    public static final int FLAG_STALLED = 2;
    public static final int FLAG_RUNNING = 4;
    public static final int FLAG_SLACK = 8;
    public static final int FLAG_REELING = 16;
    /** The reel is grinding: cranked against a slipping drag past the half-second grace (it wears now). */
    public static final int FLAG_GRINDING = 32;
    /** The fish is swimming at the angler faster than the line is wound in: slack is building. */
    public static final int FLAG_COMING = 64;

    // Entity event ids: the client handles 21, 35 and 63 itself before the entity sees them (63 casts to Sniffer),
    // and Entity/FishingHook use 31 and 53, so ours stay clear of those.
    public static final byte EVENT_BITE = 61;
    public static final byte EVENT_JUMP = 62;
    public static final byte EVENT_SPLASH = 64;

    /** The rig's dead weight when junk or treasure is on the line. */
    static final FishFight DEAD_WEIGHT = new FishFight(0.6, 0.01, 0.4, Curve.preset("steady"), 0, 0, 0, false);
    private static final double GRAVITY = 0.04;

    private enum CatchKind { FISH, JUNK, TREASURE, CRATE }

    /** Chance per bite of a loot crate (Tide's crates), where a crate fits the spot; luck and a magnetic hook raise it. */
    private static final double CRATE_CHANCE = 0.013;

    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private String medium = "water";
    private double castLimit;
    /**
     * Line out while the rig waits (set when it lands or is handed over). The reel is closed, so an angler walking
     * off or a boat or machine moving away drags the rig along at this length; only winding or a lift shortens it.
     */
    private double towLength;
    /**
     * Line off the reel while the rig waits: what was cast, less what was wound back. It is what the HUD shows, so
     * looking round, strafing, bobbing or stepping toward the rig (slack) never change the number.
     */
    private double lineOff;
    private boolean openWater;
    private BiteSampler.Pool pool = BiteSampler.Pool.EMPTY;
    private long poolBuiltAt = Long.MIN_VALUE;
    private Vec3 poolPos = Vec3.ZERO;
    private double lureWorking;
    private int biteTicks;
    private int cooldown;
    private CatchKind catchKind = CatchKind.FISH;
    private BiteSampler.Bite bite;
    private List<ItemStack> loot = List.of();
    private CrateData crate;
    private FishingContext context;
    private Fight fight;
    private final Controls controls = new Controls();
    /** Rod height outside a fight (the fight keeps its own), 0 lowered to 1 lifted. */
    private double rodHeight;
    /** Where the angler (or the machine's rod tip) was last tick, for how fast it moves away from a hooked fish. */
    private Vec3 lastAnchor;
    /** {@link #recede}'s smoothed speed, m/s. */
    private double recedeSpeed;
    private double heading;
    private double fishDepth = 0.7;
    private boolean forceBite;
    /** Test tool: the fish the forced bite will be (null: whatever the pool gives). */
    private BiteSampler.Bite forcedBite;
    private boolean forcedCrate;

    // Client-side interpolation of the server's positions (vanilla's hook ignores them).
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public CastHook(EntityType<? extends CastHook> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    // ---- Casting ----

    /**
     * Casts from {@code player}'s rod with {@code charge} (0..1 of a full cast) and returns the new hook. The rig
     * flies a ballistic arc meant to land about {@code charge} of the rod's casting range away.
     */
    public static CastHook cast(Player player, InteractionHand hand, RodStats stats, double charge) {
        double distance = Math.max(3, stats.maxCast() * (0.25 + 0.75 * Mth.clamp(charge, 0, 1)));
        double elevation = Math.toRadians(Mth.clamp(-player.getXRot() + 20, 10, 55));
        double yaw = Math.toRadians(player.getYRot());
        return launch(player, hand, stats, tip(player), new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), elevation, distance,
                Mth.clamp(charge, 0, 1));
    }

    /**
     * A machine's cast: {@code distance} blocks out along {@code direction} (flattened) from {@code tip}, at a
     * medium arc. The Geared and Fuel Reel Drives cast this way.
     */
    public static CastHook castFrom(MachineAngler angler, RodStats stats, Vec3 tip, Vec3 direction, double distance) {
        Vec3 flat = new Vec3(direction.x, 0, direction.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(1, 0, 0);
        double d = Mth.clamp(distance, 3, stats.maxCast());
        return launch(angler, InteractionHand.MAIN_HAND, stats, tip, flat.normalize(), Math.toRadians(35), d,
                d / Math.max(3, stats.maxCast()));
    }

    private static CastHook launch(Player angler, InteractionHand hand, RodStats stats, Vec3 tip, Vec3 flat,
                                   double elevation, double distance, double charge) {
        CastHook hook = new CastHook(FishingRegistry.CAST_HOOK.get(), angler.level());
        hook.hand = hand;
        hook.setPos(tip.x, tip.y, tip.z);
        hook.handTo(angler);
        hook.castLimit = distance;
        double speed = Math.sqrt(distance * GRAVITY / Math.sin(2 * elevation));
        double horizontal = speed * Math.cos(elevation);
        hook.setDeltaMovement(flat.x * horizontal, speed * Math.sin(elevation), flat.z * horizontal);
        hook.entityData.set(RIG, (byte) rigOf(stats).ordinal());
        hook.entityData.set(LINE_LEFT, (float) stats.lineMeters());
        angler.level().addFreshEntity(hook);
        // A drag over what the line holds now (a frayed line holds less) snaps it on the first hard pull: say so.
        if (stats.dragOverLine() && angler instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.create_galore_fishing.drag_over_line",
                    RodStats.kg(stats.drag()), RodStats.kgHolds(stats.breakingLoad())).withStyle(ChatFormatting.RED), true);
        }
        float whoosh = (float) (0.4 + 0.6 * charge);
        angler.level().playSound(null, tip.x, tip.y, tip.z, FishingRegistry.CAST_WHOOSH.get(),
                SoundSource.PLAYERS, 0.4f + 0.4f * whoosh, 0.85f + 0.3f * whoosh);
        angler.level().playSound(null, tip.x, tip.y, tip.z, FishingRegistry.LINE_ZIP.get(),
                SoundSource.PLAYERS, 0.35f, 0.9f + 0.2f * whoosh);
        return hook;
    }

    // ---- Who holds the rod ----

    /** The machine holding this hook's rod (server side), or null when a player holds it. */
    public RodMount machine() {
        return getOwner() instanceof MachineAngler angler ? angler.mount() : null;
    }

    /** Where the machine holding this hook's rod is (both sides); empty while a player holds it. */
    public Optional<BlockPos> mountPos() {
        return entityData.get(MOUNT);
    }

    /**
     * Hands the line to {@code angler}: a player taking a cast rod out of a machine, or a machine given one. The rod
     * goes with it (the caller moves the stack); the line, the bite or the fight carry on.
     */
    public void handTo(Player angler) {
        if (getOwner() instanceof Player old && old.fishing == this) old.fishing = null;
        hand = InteractionHand.MAIN_HAND;
        towLength = 0;
        setOwner(angler);
        boolean machine = angler instanceof MachineAngler;
        entityData.set(MOUNT, machine ? Optional.of(((MachineAngler) angler).mount().mountPos()) : Optional.empty());
        entityData.set(OWNER, machine ? -1 : angler.getId());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == OWNER && level().isClientSide) {
            if (getOwner() instanceof Player old && old.fishing == this) old.fishing = null;
            int id = entityData.get(OWNER);
            setOwner(id < 0 ? null : level().getEntity(id));
        }
    }

    /** A machine's angler doesn't exist on clients, so its hook is sent without an owner. */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        Entity owner = getOwner();
        return new ClientboundAddEntityPacket(this, entity, owner == null || owner instanceof MachineAngler ? -1 : owner.getId());
    }

    /**
     * As Entity and Projectile do it, but without vanilla's fishing hook check that discards a hook whose player
     * isn't on this client: a machine's hook never has one there.
     */
    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        double x = packet.getX(), y = packet.getY(), z = packet.getZ();
        syncPacketPositionCodec(x, y, z);
        moveTo(x, y, z);
        setXRot(packet.getXRot());
        setYRot(packet.getYRot());
        setId(packet.getId());
        setUUID(packet.getUUID());
        Entity owner = level().getEntity(packet.getData());
        if (owner != null) setOwner(owner);
    }

    /** Where the rod tip is: the machine's, or ahead of a player's hand. */
    private static Vec3 tipOf(Player owner) {
        return owner instanceof MachineAngler angler ? angler.mount().tip() : tip(owner);
    }

    static Rig rigOf(RodStats stats) {
        return stats.terminal().map(t -> switch (t.kind()) {
            case FLOAT -> Rig.FLOAT;
            case SINKER -> Rig.SINKER;
            case LURE -> Rig.LURE;
            default -> Rig.HOOK;
        }).orElse(Rig.HOOK);
    }

    /** Roughly where the rod tip is for a player holding it: ahead of and above the hand. */
    public static Vec3 tip(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? Vec3.ZERO : right.normalize();
        return player.getEyePosition().add(look.scale(1.6)).add(right.scale(0.35)).add(0, -0.25, 0);
    }

    // ---- Synced state ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, (byte) Phase.FLYING.ordinal());
        builder.define(RIG, (byte) Rig.HOOK.ordinal());
        builder.define(TENSION, 0f);
        builder.define(DRAG, 0f);
        builder.define(BREAK, 1f);
        builder.define(ROD_LOAD, 0f);
        builder.define(REEL_LOAD, 0f);
        builder.define(LINE_OUT, 0f);
        builder.define(LINE_LEFT, 0f);
        builder.define(FLAGS, (byte) 0);
        builder.define(FISH, "");
        builder.define(FISH_CM, 0f);
        builder.define(FISH_KG, 0f);
        builder.define(MOUNT, Optional.empty());
        builder.define(OWNER, -1);
        builder.define(ROD_HEIGHT, 0f);
        builder.define(SLACK, 0f);
        builder.define(MOOD, (byte) 0);
    }

    public Phase phase() {
        return Phase.values()[entityData.get(PHASE)];
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, (byte) phase.ordinal());
    }

    public Rig rig() {
        return Rig.values()[entityData.get(RIG)];
    }

    public float tension() {
        return entityData.get(TENSION);
    }

    public float drag() {
        return entityData.get(DRAG);
    }

    public float breakingLoad() {
        return entityData.get(BREAK);
    }

    /** Rod load as a fraction of its rating; drives how far the 3D rod bends. */
    public float rodLoad() {
        return entityData.get(ROD_LOAD);
    }

    /** Reel load as a fraction of what it can take, or negative for a pole with no reel. */
    public float reelLoad() {
        return entityData.get(REEL_LOAD);
    }

    public float lineOut() {
        return entityData.get(LINE_OUT);
    }

    public float lineLeft() {
        return entityData.get(LINE_LEFT);
    }

    public boolean flag(int flag) {
        return (entityData.get(FLAGS) & flag) != 0;
    }

    /** Metres of slack line while fighting. */
    public float slack() {
        return entityData.get(SLACK);
    }

    /** What the hooked fish is doing. */
    public Fight.Mood mood() {
        Fight.Mood[] moods = Fight.Mood.values();
        return moods[Math.floorMod(entityData.get(MOOD), moods.length)];
    }

    // What the client shows: the synced loads eased a little, quick to rise (a jolt shows at once) and slower to fall,
    // so a bar or a bent rod reads as a strain rather than a flicker.
    private float shownTension;
    private float shownRodLoad;
    private float shownReelLoad;
    private float shownSlack;

    public float shownTension() {
        return shownTension;
    }

    public float shownRodLoad() {
        return shownRodLoad;
    }

    /** Negative without a reel, as {@link #reelLoad()}. */
    public float shownReelLoad() {
        return reelLoad() < 0 ? -1 : shownReelLoad;
    }

    public float shownSlack() {
        return shownSlack;
    }

    private static float ease(float shown, float target) {
        return shown + (target - shown) * (target > shown ? 0.6f : 0.25f);
    }

    /** The hooked fish's item, or null while nothing is on. */
    public ResourceLocation fishId() {
        String id = entityData.get(FISH);
        return id.isEmpty() ? null : ResourceLocation.tryParse(id);
    }

    public float fishLengthCm() {
        return entityData.get(FISH_CM);
    }

    public float fishKg() {
        return entityData.get(FISH_KG);
    }

    public InteractionHand hand() {
        return hand;
    }

    /** How high the angler holds the rod, 0 lowered to 1 lifted (server's value, synced). */
    public float rodHeight() {
        return entityData.get(ROD_HEIGHT);
    }

    /** Whether the angler is lifting the rod (a machine never does). */
    private boolean lifting(Player owner) {
        return !(owner instanceof MachineAngler) && RodLift.lifting(owner);
    }

    /**
     * Moves the rod outside a fight: up at a flick's speed while lifting, back down when let go. Returns the metres
     * of line the tip drew back this tick (0 when it isn't rising).
     */
    private double stepRod(Player owner, RodStats stats) {
        double before = rodHeight;
        rodHeight = lifting(owner) ? Math.min(1, rodHeight + Fight.liftRate(0) * Fight.DT)
                : Math.max(0, rodHeight - Fight.LOWER_RATE * Fight.DT);
        entityData.set(ROD_HEIGHT, (float) rodHeight);
        return Math.max(0, rodHeight - before) * liftSweep(stats);
    }

    /** Metres of line one full lift of this rod draws back: most of its length. */
    static double liftSweep(RodStats stats) {
        return 0.9 * stats.rod().length();
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        baseTick();
        if (level().isClientSide) {
            mountPos().ifPresent(pos -> {
                MachineInfo.CLIENT_HOOKS.put(pos, this);
                // A machine's line is nobody's on this client (the player who set it in may still think it theirs).
                for (Player p : level().players()) if (p.fishing == this) p.fishing = null;
            });
            if (lerpSteps > 0) {
                setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps,
                        getZ() + (lerpZ - getZ()) / lerpSteps);
                lerpSteps--;
            }
            shownTension = ease(shownTension, tension());
            shownRodLoad = ease(shownRodLoad, rodLoad());
            shownReelLoad = ease(shownReelLoad, Math.max(0, reelLoad()));
            shownSlack += (slack() - shownSlack) * 0.35f;
            return;
        }
        Player owner = getPlayerOwner();
        ItemStack rod = owner == null ? ItemStack.EMPTY : owner.getItemInHand(hand);
        RodStats stats = RodStats.of(rod).orElse(null);
        if (owner == null || !owner.isAlive() || owner.level() != level() || stats == null
                || owner.distanceTo(this) > stats.lineMeters() + 24) {
            if (owner != null && phase() == Phase.FIGHTING) {
                TackleWear.tell(owner, Component.translatable("message.create_galore_fishing.line_dropped"));
            }
            discard();
            return;
        }
        Vec3 tip = tipOf(owner);
        switch (phase()) {
            case FLYING -> {
                fly(owner, stats, tip);
                // Landed: the reel closes on the line that went out.
                if (phase() != Phase.FLYING) {
                    Vec3 anchor = towAnchor(owner, tip);
                    towLength = Math.hypot(getX() - anchor.x, getZ() - anchor.z);
                    lineOff = tip.distanceTo(position());
                }
            }
            case WAITING, GROUNDED -> waitForBite(owner, rod, stats, tip);
            case BITE -> biting(owner, rod, stats, tip, towAnchor(owner, tip));
            case FIGHTING -> fighting(owner, rod, stats, tip);
        }
        if (!isRemoved()) {
            double out = switch (phase()) {
                case FIGHTING -> fight != null ? fight.lineOut() : tip.distanceTo(position());
                case FLYING -> tip.distanceTo(position());
                default -> lineOff > 0 ? lineOff : tip.distanceTo(position());
            };
            entityData.set(LINE_OUT, (float) out);
        }
    }

    private void fly(Player owner, RodStats stats, Vec3 tip) {
        Vec3 motion = getDeltaMovement().add(0, -GRAVITY, 0);
        double out = Math.hypot(getX() - tip.x, getZ() - tip.z);
        if (out > castLimit || out > stats.lineMeters()) motion = new Vec3(0, Math.min(0, motion.y), 0);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        String fluid = FishingWater.medium(level(), blockPosition());
        boolean voidHook = stats.hook().map(h -> h.is("void")).orElse(false);
        if (fluid != null) {
            settle(owner, stats, fluid);
        } else if (voidHook && getY() <= FishingWater.voidSurface(level())) {
            settle(owner, stats, "void");
        } else if (onGround() || horizontalCollision || tickCount > 200) {
            setDeltaMovement(Vec3.ZERO);
            setPhase(Phase.GROUNDED);
        }
    }

    private void settle(Player owner, RodStats stats, String fluid) {
        if (fluid.equals("lava") && !lavaProof(stats)) {
            // The rig burns: hook, rig and bait are gone, the line comes back short.
            ItemStack rod = owner.getItemInHand(hand);
            RodItem.setParts(rod, RodItem.parts(rod).with(RodParts.Slot.HOOK, ItemStack.EMPTY)
                    .with(RodParts.Slot.TERMINAL, ItemStack.EMPTY).with(RodParts.Slot.BAIT, ItemStack.EMPTY));
            level().playSound(null, blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 0.6f, 1.2f);
            TackleWear.tell(owner, Component.translatable("message.create_galore_fishing.burnt"));
            discard();
            return;
        }
        medium = fluid;
        setDeltaMovement(Vec3.ZERO);
        setPhase(Phase.WAITING);
        openWater = FishingWater.openWater(level(), position());
        cooldown = 40;
        level().broadcastEntityEvent(this, EVENT_SPLASH);
        float heavy = (float) Mth.clamp(stats.terminalWeight() / 40, 0, 1);
        level().playSound(null, blockPosition(), FishingRegistry.CAST_PLOP.get(), SoundSource.NEUTRAL, 0.5f + 0.4f * heavy,
                1.25f - 0.5f * heavy);
    }

    static boolean lavaProof(RodStats stats) {
        return stats.rod().lava() || stats.hook().map(h -> h.is("lava")).orElse(false);
    }

    private boolean reeling(Player owner) {
        if (owner instanceof MachineAngler angler) return angler.mount().reeling(this);
        return owner.isUsingItem() && owner.getUsedItemHand() == hand && owner.getUseItem().getItem() instanceof RodItem;
    }

    private double surfaceY() {
        return medium.equals("void") ? FishingWater.voidSurface(level()) : FishingWater.surface(level(), blockPosition());
    }

    private void waitForBite(Player owner, ItemStack rod, RodStats stats, Vec3 tip) {
        boolean reeling = reeling(owner);
        // Lifting the rod twitches the rig toward the angler by the tip's travel (working a lure or jig).
        double twitch = stepRod(owner, stats);
        double retrieve = (stats.reel().map(TacklePart.Reel::retrieve).orElse(0.9)) / 20
                * (owner instanceof MachineAngler angler ? angler.mount().retrieveScale(this) : 1);
        Vec3 pos = position();
        if (reeling && Math.hypot(tip.x - pos.x, tip.z - pos.z) < 1.6) {
            discard();
            return;
        }
        // The line out is held at towLength: winding and lifts take line in (any slack first), and nothing lets it
        // out, so trolling from a boat, walking backwards or a machine on a moving ship tows the rig behind. Line only
        // leaves the spool to a fish pulling past the drag. A hand rod measures it from the angler, not the rod tip,
        // so looking round doesn't drag the rig about.
        Vec3 anchor = towAnchor(owner, tip);
        Vec3 toTip = new Vec3(anchor.x - pos.x, 0, anchor.z - pos.z);
        double flat = toTip.length();
        if (towLength <= 0) towLength = flat;
        double wound = Math.min(towLength - 0.5, (reeling ? retrieve : 0) + twitch);
        if (wound > 0) {
            towLength -= wound;
            lineOff = Math.max(0.5, lineOff - wound);
        }
        double pulled = Mth.clamp(flat - towLength, 0, flat);
        Vec3 step = pulled > 0 && flat > 1e-3 ? toTip.scale(pulled / flat) : Vec3.ZERO;
        boolean moving = step.lengthSqr() > 1e-8;
        double x = pos.x + step.x;
        double z = pos.z + step.z;
        double y = pos.y;
        boolean inMedium = phase() == Phase.WAITING && FishingWater.in(level(), BlockPos.containing(x, y, z), medium);
        if (phase() == Phase.GROUNDED || !inMedium) {
            if (moving) setPos(x, Math.max(y, tip.y - 1.5), z);
            return;
        }
        double surface = surfaceY();
        double bottom = medium.equals("void") ? surface - 64 : FishingWater.bottom(level(), BlockPos.containing(x, y, z));
        switch (rig()) {
            case FLOAT, HOOK -> y = surface - 0.15;
            case SINKER -> y = Math.max(bottom + 0.1, y - 0.08);
            case LURE -> {
                TacklePart.Lure lure = (TacklePart.Lure) stats.terminal().orElseThrow();
                // Wound in or towed, a lure dives to its depth and works if the speed suits it.
                double target = moving ? Math.max(bottom + 0.3, surface - lure.dive()) : bottom + 0.2;
                y += Mth.clamp(target - y, -0.06, 0.06);
                double speed = step.length() * 20;
                double working = moving ? (speed >= lure.speedMin() && speed <= lure.speedMax() ? 1 : 0.3)
                        : (y > bottom + 0.3 ? 0.5 : 0.05);
                lureWorking = 0.9 * lureWorking + 0.1 * working;
            }
        }
        setPos(x, Math.min(y, surface - 0.05), z);
        entityData.set(FLAGS, (byte) (reeling ? FLAG_REELING : 0));
        if (cooldown > 0) {
            cooldown--;
        } else if (tickCount % 10 == 0) {
            rollBite(owner, rod, stats);
        }
    }

    /**
     * Where the bait is, for who can bite: under a float it hangs at the depth set at the Tackle Bench (0.5-8 m,
     * 1.5 by default), stopping just above the bottom; every other rig fishes where it is.
     */
    private Vec3 baitPosition(ItemStack rod) {
        Vec3 at = position();
        if (rig() != Rig.FLOAT || medium.equals("void")) return at;
        float depth = rod.getOrDefault(FishingRegistry.FLOAT_DEPTH.get(), 1.5f);
        double bottom = FishingWater.bottom(level(), blockPosition());
        return new Vec3(at.x, Math.max(bottom + 0.3, at.y - depth), at.z);
    }

    private void rollBite(Player owner, ItemStack rod, RodStats stats) {
        ServerLevel level = (ServerLevel) level();
        int luck = luck(owner, rod, stats);
        if (level.getGameTime() - poolBuiltAt > 100 || position().distanceToSqr(poolPos) > 16) {
            // The Blazing rod fishes Nether fish in any lava, as in Tide.
            boolean netherLava = medium.equals("lava") && stats.rod().has("blazing");
            context = FishingPlace.context(level, baitPosition(rod), rod, RodItem.parts(rod).hook(), luck,
                    medium, temperatureOffset(stats), netherLava, openWater, random);
            pool = BiteSampler.pool(context, stats, lureWorking);
            poolBuiltAt = level.getGameTime();
            poolPos = position();
        }
        double wait = BiteSampler.meanWaitSeconds(pool);
        wait *= 1 - 0.15 * EnchantmentHelper.getFishingTimeReduction(level, rod, owner) / 5;
        wait /= FishingPressure.of(level).biteFactor(level, blockPosition());
        wait /= Chum.factor(level, position());
        boolean day = level.isDay();
        if (stats.hook().map(h -> h.is("barbed")).orElse(false) && day) wait *= 0.75;
        if (stats.hook().map(h -> h.is("glowing")).orElse(false) && !day) wait *= 0.75;
        if (medium.equals("lava")) wait *= 1.6;
        if (medium.equals("void")) wait *= 1.8;
        if (level.isRaining()) wait *= 0.85;
        if (stats.rod().has("prismarine") && level.isRainingAt(owner.blockPosition())) wait *= 0.8;
        wait = Mth.clamp(wait, 5, 240);
        if (!forceBite && random.nextDouble() >= 1 - Math.exp(-0.5 / wait)) return;
        forceBite = false;

        boolean magnetic = stats.hook().map(h -> h.is("magnetic")).orElse(false)
                || stats.bait().map(b -> b.is("magnetic")).orElse(false);
        double treasure = openWater ? Math.max(0, 0.02 + 0.015 * luck) : 0;
        if (magnetic) treasure *= 2;
        // The Nether's treasure is ancient debris and netherite scrap: by hand only, never from an unattended machine.
        if (owner instanceof MachineAngler && level.dimension() == Level.NETHER) treasure = 0;
        double junk = pool.total() <= 0 ? 0.6 : Math.max(0.02, 0.08 - 0.02 * luck);
        double crateChance = CRATE_CHANCE * Math.max(0.25, 1 + 0.3 * luck) * (magnetic ? 2 : 1);
        double r = random.nextDouble();
        if (forcedBite != null) {
            catchKind = CatchKind.FISH;
            bite = forcedBite;
            forcedBite = null;
        } else if ((forcedCrate || random.nextDouble() < crateChance)
                && (crate = CatchTables.pickCrate(context, random).orElse(null)) != null) {
            forcedCrate = false;
            catchKind = CatchKind.CRATE;
            bite = null;
        } else if (r < treasure || r < treasure + junk) {
            catchKind = r < treasure ? CatchKind.TREASURE : CatchKind.JUNK;
            loot = Catches.lootFrom(this, rod, context, catchKind == CatchKind.TREASURE, luck);
            if (loot.isEmpty()) return;
            bite = null;
        } else {
            var rolled = BiteSampler.roll(pool, stats, random);
            if (rolled.isEmpty()) {
                // Something nosed at it but couldn't take this hook.
                level.broadcastEntityEvent(this, EVENT_SPLASH);
                return;
            }
            catchKind = CatchKind.FISH;
            bite = rolled.get();
        }
        setPhase(Phase.BITE);
        if (stats.rod().has("echo")) echo(owner);
        biteTicks = rig() == Rig.LURE ? 20 : 28;
        if (owner instanceof MachineAngler angler) {
            biteTicks = angler.mount().biteTicks();
            angler.mount().bite(this);
        }
        level.broadcastEntityEvent(this, EVENT_BITE);
        if (rig() == Rig.FLOAT || rig() == Rig.HOOK) {
            level.playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35f,
                    1 + (random.nextFloat() - random.nextFloat()) * 0.4f);
        }
        // The rod tip taps (heard at the angler): the bite on a sinker or a lure is felt, not seen.
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), FishingRegistry.BITE_TAP.get(), SoundSource.PLAYERS,
                rig() == Rig.FLOAT ? 0.3f : 0.7f, 0.9f + random.nextFloat() * 0.2f);
    }

    /** The Echo rod names what took the bait, as Tide's did. */
    private void echo(Player owner) {
        Component what = switch (catchKind) {
            case FISH -> bite.species().item().getDescription();
            case CRATE -> crate.blockProvider().getState(random, blockPosition()).getBlock().getName();
            default -> loot.isEmpty() ? Component.literal("?") : loot.getFirst().getHoverName();
        };
        owner.displayClientMessage(Component.translatable("message.create_galore_fishing.echo", what)
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /**
     * Luck of the Sea, the angler's luck attribute, a lucky hook or bait, and the special rods' luck: Midas always,
     * Sunflower in sunlight, Prismarine in rain (as in Tide).
     */
    private static int luck(Player owner, ItemStack rod, RodStats stats) {
        Level level = owner.level();
        int luck = EnchantmentHelper.getFishingLuckBonus((ServerLevel) level, rod, owner);
        luck += Math.round(owner.getLuck());
        luck += stats.hook().map(TacklePart.Hook::luck).orElse(0);
        luck += stats.bait().map(TacklePart.Bait::luck).orElse(0);
        TacklePart.Rod blank = stats.rod();
        if (blank.has("midas")) luck++;
        if (blank.has("sunflower") && level.isDay() && !level.isRaining() && level.dimensionType().hasSkyLight()
                && level.canSeeSky(owner.blockPosition())) luck++;
        if (blank.has("prismarine") && level.isRainingAt(owner.blockPosition())) luck++;
        return luck;
    }

    private static float temperatureOffset(RodStats stats) {
        if (stats.hook().map(h -> h.is("fiery")).orElse(false)) return 0.25f;
        if (stats.hook().map(h -> h.is("permafrost")).orElse(false)) return -0.25f;
        return 0;
    }

    private void biting(Player owner, ItemStack rod, RodStats stats, Vec3 tip, Vec3 anchor) {
        if (forcedBite != null) {
            // A test bite asked for while another fish was already biting: that one gives way.
            catchKind = CatchKind.FISH;
            bite = forcedBite;
            forcedBite = null;
            forceBite = false;
        }
        // A player strikes by lifting the rod sharply; one already held high can't strike until it comes down.
        // Winding alone never sets the hook.
        double heightBefore = rodHeight;
        stepRod(owner, stats);
        boolean struck = owner instanceof MachineAngler angler ? angler.mount().strikes(this)
                : lifting(owner) && heightBefore < STRIKE_FROM && rodHeight > heightBefore;
        if (struck) {
            strike(owner, rod, stats, tip);
            return;
        }
        tow(anchor);
        if (--biteTicks > 0) return;
        // Missed: the fish lets go, and may take the bait with it.
        setPhase(Phase.WAITING);
        cooldown = 60;
        if (rig() != Rig.LURE && random.nextFloat() < 0.3f) {
            RodParts parts = RodItem.parts(rod);
            ItemStack bait = parts.bait();
            if (!bait.isEmpty()) {
                bait.shrink(1);
                RodItem.setParts(rod, parts.with(RodParts.Slot.BAIT, bait));
                TackleWear.tell(owner, Component.translatable("message.create_galore_fishing.bait_stolen"));
            }
        }
    }

    /**
     * Where a waiting line is measured from: a machine's rod tip, or the angler holding the rod (the tip swings a metre
     * and a half about them as they look round).
     */
    private static Vec3 towAnchor(Player owner, Vec3 tip) {
        return owner instanceof MachineAngler ? tip : owner.position();
    }

    /** A fish mouthing the bait goes where the rig is dragged: moving away doesn't let more line out. */
    private void tow(Vec3 anchor) {
        Vec3 pos = position();
        Vec3 toTip = new Vec3(anchor.x - pos.x, 0, anchor.z - pos.z);
        double flat = toTip.length();
        if (towLength <= 0 || flat <= towLength) return;
        Vec3 step = toTip.scale((flat - towLength) / flat);
        setPos(pos.x + step.x, pos.y, pos.z + step.z);
    }

    private void strike(Player owner, ItemStack rod, RodStats stats, Vec3 tip) {
        if (catchKind == CatchKind.FISH && summoningHook(owner, rod, stats)) return;
        FishFight ff;
        double kg;
        double hold;
        if (catchKind == CatchKind.FISH) {
            ff = bite.species().fight();
            kg = bite.kg();
            hold = SizeModel.hookHold(stats.hookSize(), bite.lengthCm());
            entityData.set(FISH, BuiltInRegistries.ITEM.getKey(bite.species().item()).toString());
            entityData.set(FISH_CM, (float) bite.lengthCm());
            entityData.set(FISH_KG, (float) bite.kg());
        } else {
            ff = DEAD_WEIGHT;
            kg = switch (catchKind) {
                case CRATE -> 9;
                case TREASURE -> 3;
                default -> 1;
            };
            hold = 1;
        }
        fight = new Fight(ff, stats.tackle(hold), kg, Math.max(3, tip.distanceTo(position())));
        fight.setRodHeight(rodHeight);
        TackleWear.setLimits(fight, rod);
        lastAnchor = null;
        heading = Math.atan2(getZ() - tip.z, getX() - tip.x);
        setPhase(Phase.FIGHTING);
        entityData.set(BREAK, (float) stats.breakingLoad());
        level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), FishingRegistry.BITE_STRIKE.get(), SoundSource.PLAYERS, 0.7f, 1f);
        level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6f, 0.8f);
    }

    /**
     * Hybrid Aquatic's Ominous and CreeperMagnet hooks (as the pack's Bridge made them work on Tide rods): what bites
     * is not a fish. The Ominous Hook brings up a Karkinos, the CreeperMagnet Hook a creeper, flung at the angler; the
     * hook wears by one use.
     */
    private boolean summoningHook(Player owner, ItemStack rod, RodStats stats) {
        String effect = stats.hook().flatMap(TacklePart.Hook::effect).orElse("");
        EntityType<?> type = switch (effect) {
            case "ominous" -> BuiltInRegistries.ENTITY_TYPE.getOptional(
                    ResourceLocation.fromNamespaceAndPath("hybrid_aquatic", "karkinos")).orElse(null);
            case "creeper_magnet" -> EntityType.CREEPER;
            default -> null;
        };
        if (type == null) return false;
        ServerLevel level = (ServerLevel) level();
        Entity summoned = type.spawn(level, blockPosition(), MobSpawnType.MOB_SUMMONED);
        if (summoned != null) {
            Vec3 toPlayer = owner.position().subtract(position());
            double lift = Math.sqrt(toPlayer.length()) * 0.08;
            summoned.setDeltaMovement(toPlayer.x * 0.15, toPlayer.y * 0.15 + lift, toPlayer.z * 0.15);
        }
        RodParts parts = RodItem.parts(rod);
        ItemStack hook = parts.hook();
        if (hook.isDamageableItem()) {
            hook.setDamageValue(hook.getDamageValue() + 1);
            if (hook.getDamageValue() >= hook.getMaxDamage()) {
                hook = ItemStack.EMPTY;
                level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1f);
            }
            RodItem.setParts(rod, parts.with(RodParts.Slot.HOOK, hook));
        }
        discard();
        return true;
    }

    private void fighting(Player owner, ItemStack rod, RodStats stats, Vec3 tip) {
        controls.drag = stats.drag();
        controls.crankLimit = Double.NaN;
        controls.retrieveScale = 1;
        controls.crankThroughSlip = true;
        // A machine's rod tip moves only with what carries it (a ship); a player's swings with every turn of the
        // head, so the player's body is what counts as moving away.
        controls.recede = recede(owner instanceof MachineAngler ? tip : owner.getEyePosition());
        if (owner instanceof MachineAngler angler) {
            // Held at a good working angle; the machine decides the rest.
            controls.reeling = false;
            controls.rodAngle = Math.toRadians(60);
            angler.mount().fight(this, fight, stats, controls);
        } else {
            controls.reeling = reeling(owner);
            controls.lifting = lifting(owner);
            controls.liftSweep = liftSweep(stats);
            controls.rodAngle = rodAngle(owner, tip, position(), fight.rodHeight());
        }
        fight.tick(controls, BiteSampler.adapt(random));
        rodHeight = fight.rodHeight();
        entityData.set(ROD_HEIGHT, (float) rodHeight);
        moveFish(tip);

        if (fight.jumped()) level().broadcastEntityEvent(this, EVENT_JUMP);
        double load = fight.peakTension();
        entityData.set(TENSION, (float) (load / Math.max(0.01, fight.tackle().breakingLoad())));
        entityData.set(ROD_LOAD, (float) (load / Math.max(0.01, fight.tackle().rodMaxLoad())));
        double reelLoad = fight.reelLoad(controls);
        entityData.set(REEL_LOAD, (float) (Double.isNaN(reelLoad) ? -1 : reelLoad));
        entityData.set(DRAG, (float) (stats.isPole() ? 0 : controls.drag));
        int flags = (fight.slipping() ? FLAG_SLIPPING : 0) | (fight.stalled() ? FLAG_STALLED : 0)
                | (fight.running() && fight.takingLine() ? FLAG_RUNNING : 0) | (fight.slackLine() ? FLAG_SLACK : 0)
                | (controls.reeling ? FLAG_REELING : 0) | (fight.grinding() ? FLAG_GRINDING : 0)
                | (fight.comingAtYou() ? FLAG_COMING : 0);
        entityData.set(FLAGS, (byte) flags);
        entityData.set(SLACK, (float) fight.slack());
        entityData.set(MOOD, (byte) fight.mood().ordinal());
        if (fight.jumped()) {
            float size = (float) Mth.clamp(fight.weight() / 20, 0, 1);
            level().playSound(null, blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.4f + 0.5f * size, 1.4f - 0.6f * size);
        }
        if (fight.outcome().over()) finish(owner, rod, stats, fight.outcome());
    }

    /**
     * How fast {@code anchor} (the angler's body, or a machine's rod tip) moved away from the fish since last tick, in
     * m/s: an angler walking off or a boat under way tows the fish (see {@link Fight}). Turning or looking around
     * doesn't move the anchor. A jump of more than 20 m (a teleport) counts as nothing.
     */
    private double recede(Vec3 anchor) {
        Vec3 last = lastAnchor == null ? anchor : lastAnchor;
        lastAnchor = anchor;
        if (last.distanceToSqr(anchor) > 400) return recedeSpeed = 0;
        double away = anchor.distanceTo(position()) - last.distanceTo(position());
        // Smoothed over a few ticks, so a step or a bob of the boat isn't a yank; walking or sailing off is.
        recedeSpeed = 0.7 * recedeSpeed + 0.3 * Math.max(0, away) / Fight.DT;
        return recedeSpeed;
    }

    /**
     * The hooked fish swims around the angler at the fight's distance, staying in its fluid: on a slow arc while it
     * holds, sweeping off to one side on a run, near the top while it shakes its head, deep while it runs or sulks.
     */
    private void moveFish(Vec3 tip) {
        heading += fight.turn() + random.nextGaussian() * 0.006;
        double distance = fight.distance();
        Vec3 next = null;
        for (int attempt = 0; attempt < 9 && next == null; attempt++) {
            double h = heading + (attempt == 0 ? 0 : (attempt % 2 == 0 ? 1 : -1) * 0.3 * ((attempt + 1) / 2));
            double x = tip.x + Math.cos(h) * distance;
            double z = tip.z + Math.sin(h) * distance;
            BlockPos column = BlockPos.containing(x, getY(), z);
            if (FishingWater.in(level(), column, medium) || FishingWater.in(level(), column.below(), medium)) {
                heading = h;
                next = new Vec3(x, getY(), z);
            }
        }
        if (next == null) return;
        double surface = medium.equals("void") ? FishingWater.voidSurface(level()) : FishingWater.surface(level(), BlockPos.containing(next));
        double want = switch (fight.mood()) {
            case RUN -> 1.4;
            case SHAKE -> 0.3;
            case CHARGE -> 0.5;
            case REST -> 1.1;
            case HOLD -> 0.7 + 0.4 * Math.sin(tickCount * 0.05);
        };
        fishDepth += (want - fishDepth) * 0.08;
        setPos(next.x, surface - (fight.jumped() ? -0.6 : fishDepth), next.z);
    }

    /**
     * The angle between the rod and the line. The rod is held about 25 degrees above where the angler looks, and
     * lifting raises it up to {@link #ROD_LIFT} more: lifted while looking at the fish it stands well off the line,
     * bends deep and soaks the fish's lunges; lowered and pointed at the water below the fish it stays straight and
     * soaks nothing.
     */
    public static double rodAngle(Player owner, Vec3 tip, Vec3 fish, double height) {
        Vec3 rod = Vec3.directionFromRotation(owner.getXRot() - ROD_ABOVE_LOOK - ROD_LIFT * (float) height, owner.getYRot());
        Vec3 toFish = fish.subtract(tip);
        if (toFish.lengthSqr() < 1e-6) return 0;
        return Math.acos(Mth.clamp(rod.dot(toFish.normalize()), -1, 1));
    }

    private void finish(Player owner, ItemStack rod, RodStats stats, Outcome outcome) {
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        if (owner instanceof MachineAngler angler) {
            finishOnMachine(angler, rod, stats, outcome);
            TackleWear.apply(owner, slot, rod, fight, outcome);
            discard();
            return;
        }
        if (outcome == Outcome.LANDED) {
            ServerLevel level = (ServerLevel) level();
            if (catchKind == CatchKind.FISH) {
                Species species = bite.species();
                ItemStack fish = Catches.fishItem(species, bite, owner.getGameProfile().getName(), level.getDayTime() / 24000);
                List<ItemStack> landed = new ArrayList<>(List.of(fish));
                Catches.perkBonus(this, owner, rod, stats, medium, luck(owner, rod, stats)).ifPresent(landed::add);
                List<ItemStack> drops = Catches.fire(this, owner, rod, landed, species, bite.kg());
                int xp = Catches.xp(species, bite, fight);
                ItemStack held = drops.stream().filter(s -> s.getItem() == species.item()).findFirst().orElse(ItemStack.EMPTY);
                if (!held.isEmpty() && owner instanceof net.minecraft.server.level.ServerPlayer player) {
                    // Lift it up: Keep or Release is the angler's call (see CatchHolding).
                    Catches.give(owner, drops.stream().filter(s -> s != held).toList(), 0);
                    CatchHolding.start(player, held, species, bite, xp, blockPosition());
                } else {
                    Catches.give(owner, drops, xp);
                    FishingPressure.of(level).addCatch(level, blockPosition());
                }
                TackleWear.tell(owner, Catches.describe(species, bite));
                if (owner instanceof net.minecraft.server.level.ServerPlayer player) Catches.logInJournal(player, fish, bite);
                if (rig() != Rig.LURE) {
                    RodParts parts = RodItem.parts(rod);
                    ItemStack bait = parts.bait();
                    if (!bait.isEmpty()) {
                        bait.shrink(1);
                        RodItem.setParts(rod, parts.with(RodParts.Slot.BAIT, bait));
                    }
                }
            } else if (catchKind == CatchKind.CRATE) {
                Catches.pullCrate(this, owner, rod, crate, luck(owner, rod, stats));
            } else {
                List<ItemStack> drops = Catches.fire(this, owner, rod, loot, null, 0);
                Catches.give(owner, drops, catchKind == CatchKind.TREASURE ? 3 : 1);
            }
        } else {
            TackleWear.tell(owner, Component.translatable("message.create_galore_fishing.lost." + outcome.name().toLowerCase(java.util.Locale.ROOT)));
            // What broke and why, in chat where it stays readable.
            Component why = lossReason();
            if (why != null) owner.sendSystemMessage(why.copy().withStyle(net.minecraft.ChatFormatting.GRAY));
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    outcome.losesTackle() || outcome == Outcome.ROD_BROKE ? FishingRegistry.LINE_SNAP.get() : FishingRegistry.LINE_WHIP.get(),
                    SoundSource.PLAYERS, outcome.losesTackle() ? 0.9f : 0.6f, 1f);
        }
        TackleWear.apply(owner, slot, rod, fight, outcome);
        discard();
    }

    /** What broke or gave and why, for a lost fight; null if the fight didn't say. */
    private Component lossReason() {
        Loss loss = fight.loss();
        if (loss == null) return null;
        String test = RodStats.kgHolds(fight.tackle().breakingLoad());
        return switch (loss) {
            case SNAP_DRAG -> Component.translatable(loss.key(), RodStats.kg(controls.drag), test);
            case SNAP_POLE -> Component.translatable(loss.key(), RodStats.kg(fight.highestTension()), test);
            case SPOOLED -> Component.translatable(loss.key(), Math.round(fight.tackle().lineCapacity()));
            case ROD_OVERLOAD -> Component.translatable(loss.key(), RodStats.kg(fight.highestTension()),
                    RodStats.kg(fight.tackle().rodMaxLoad()));
            // A hook two sizes under the ideal still holds well (see SizeModel#hookHold).
            case HOOK_HOLD -> Component.translatable(loss.key(), SizeModel.HOOK_SIZES[Mth.clamp((int) Math.ceil(
                    SizeModel.idealHook(bite == null ? 30 : bite.lengthCm()) - 2), 0, SizeModel.HOOK_SIZES.length - 1)]);
            default -> Component.translatable(loss.key());
        };
    }

    /** A machine's fight is over: what came up goes to the machine (it banks the XP and routes the items). */
    private void finishOnMachine(MachineAngler angler, ItemStack rod, RodStats stats, Outcome outcome) {
        RodMount machine = angler.mount();
        if (outcome != Outcome.LANDED) {
            level().playSound(null, blockPosition(), outcome.losesTackle() || outcome == Outcome.ROD_BROKE
                    ? FishingRegistry.LINE_SNAP.get() : FishingRegistry.LINE_WHIP.get(), SoundSource.BLOCKS, 0.7f, 1f);
            machine.lost(this, outcome);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        switch (catchKind) {
            case FISH -> {
                Species species = bite.species();
                ItemStack fish = Catches.fishItem(species, bite, machine.creditName(), level.getDayTime() / 24000);
                // No Midas / Village bonus from machines: it comes with every catch (gold blocks, and now and then
                // a Totem of Undying or an enchanted golden apple), which a bank of drives would print unattended.
                List<ItemStack> landed = new ArrayList<>(List.of(fish));
                List<ItemStack> drops = Catches.fire(this, angler, rod, landed, species, bite.kg());
                FishingPressure.of(level).addCatch(level, blockPosition());
                if (rig() != Rig.LURE) {
                    RodParts parts = RodItem.parts(rod);
                    ItemStack bait = parts.bait();
                    if (!bait.isEmpty()) {
                        bait.shrink(1);
                        RodItem.setParts(rod, parts.with(RodParts.Slot.BAIT, bait));
                    }
                }
                machine.landed(this, drops, Catches.xp(species, bite, fight));
            }
            case CRATE -> {
                Catches.pullCrate(this, angler, rod, crate, luck(angler, rod, stats));
                machine.landed(this, List.of(), 0);
            }
            default -> machine.landed(this, Catches.fire(this, angler, rod, loot, null, 0), catchKind == CatchKind.TREASURE ? 3 : 1);
        }
    }

    /** The fish that bit, for the machine's records (null for junk, treasure and crates). */
    public BiteSampler.Bite bite() {
        return catchKind == CatchKind.FISH ? bite : null;
    }

    // ---- Vanilla hook behaviour we replace ----

    /** Called by vanilla rods and other mods to reel in: just bring the line back. */
    @Override
    public int retrieve(ItemStack stack) {
        discard();
        return 0;
    }

    @Override
    public boolean isOpenWaterFishing() {
        return openWater;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, steps);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    public boolean canUsePortal(boolean force) {
        return false;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_BITE || id == EVENT_SPLASH || id == EVENT_JUMP) {
            int count = id == EVENT_JUMP ? 24 : id == EVENT_BITE ? 12 : 6;
            for (int i = 0; i < count; i++) {
                level().addParticle(level().getFluidState(blockPosition()).is(FluidTags.LAVA) ? ParticleTypes.LAVA : ParticleTypes.SPLASH,
                        getX() + random.nextGaussian() * 0.2, getY() + 0.2, getZ() + random.nextGaussian() * 0.2,
                        random.nextGaussian() * 0.05, 0.15, random.nextGaussian() * 0.05);
            }
            if (id == EVENT_BITE) level().addParticle(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 0, 0.1, 0);
            if (id == EVENT_JUMP) jumpedAt = tickCount;
            return;
        }
        super.handleEntityEvent(id);
    }

    /** How far above the angler's look the rod is held (degrees), as the first-person rod is drawn. */
    public static final float ROD_ABOVE_LOOK = 25;
    /**
     * How far a full lift raises the rod (degrees, on top of {@link #ROD_ABOVE_LOOK}). Drawn the same way, so what the
     * rod looks like is what the fight gets.
     */
    public static final float ROD_LIFT = 60;
    /** A strike needs the rod to start below this height (a lift already held high has nothing left to give). */
    static final double STRIKE_FROM = 0.6;

    /** Client: when the hooked fish last jumped (tick count), for the renderer's leap; -100 if never. */
    public int jumpedAt = -100;

    /** Test tool: the next bite roll succeeds (if anything at all would bite here). */
    public void forceBite() {
        forceBite = true;
        cooldown = 0;
    }

    /** Test tool: the next bite is this fish, whatever the spot and rig. */
    public void forceBite(BiteSampler.Bite fish) {
        forcedBite = fish;
        forceBite = true;
        cooldown = 0;
    }

    /**
     * Test tool: {@code fish} is on the line now, struck and fighting (a line in the water only). Lets tests put a
     * fish on a Basic Reel Drive, which can't strike by itself.
     */
    public boolean forceFight(BiteSampler.Bite fish) {
        if (phase() != Phase.WAITING && phase() != Phase.BITE) return false;
        Player owner = getPlayerOwner();
        ItemStack rod = owner == null ? ItemStack.EMPTY : owner.getItemInHand(hand);
        RodStats stats = RodStats.of(rod).orElse(null);
        if (stats == null) return false;
        catchKind = CatchKind.FISH;
        bite = fish;
        strike(owner, rod, stats, tipOf(owner));
        return phase() == Phase.FIGHTING;
    }

    /** Test tool: the next bite is a crate, if one fits the spot. */
    public void forceCrate() {
        forcedCrate = true;
        forceBite = true;
        cooldown = 0;
    }

    /** Who could bite here with this rig, as last worked out, for {@code /cgfish pool}. */
    public BiteSampler.Pool pool() {
        return pool;
    }

    /** One line describing the hook's state, for {@code /cgfish hook}. */
    public String describe() {
        StringBuilder b = new StringBuilder(phase().name()).append(' ').append(medium)
                .append(String.format(java.util.Locale.ROOT, " out %.1f m, rod %.2f", lineOut(), rodHeight()));
        b.append(String.format(java.util.Locale.ROOT, ", %d species could bite (quality %.2f, mean wait %.0f s)",
                pool.species().size(), pool.quality(), BiteSampler.meanWaitSeconds(pool)));
        if (level() instanceof ServerLevel server && Chum.around(server, position()) > 0) {
            b.append(String.format(java.util.Locale.ROOT, ", chum x%.2f", Chum.factor(server, position())));
        }
        if (context != null) {
            long crates = CatchTables.crates().stream().filter(c -> c.weightAt(context) > 0).count();
            b.append(String.format(java.util.Locale.ROOT, ", open water %s, %d crates fit", openWater, crates));
            for (CrateData crate : CatchTables.crates()) {
                String table = crate.lootTable().map(k -> k.location().getPath()).orElse("?");
                String failed = crate.conditions().stream().filter(c -> !c.test(context))
                        .map(c -> c.type().id().getPath()).findFirst().orElse("ok");
                b.append(' ').append(table.substring(table.lastIndexOf('/') + 1)).append(':').append(failed);
            }
        }
        if (fight != null) {
            b.append(String.format(java.util.Locale.ROOT, "; fight %s %.2f kg at %.1f m, tension %.1f/%.1f kg, drag %.1f, stamina %.2f, %s",
                    fishId(), fight.weight(), fight.distance(), fight.tension(), fight.tackle().breakingLoad(),
                    controls.drag, fight.stamina(), fight.outcome()));
            b.append(String.format(java.util.Locale.ROOT, ", slack %.2f m, angle %.0f, %s", fight.slack(), Math.toDegrees(controls.rodAngle), fight.mood()));
        }
        return b.toString();
    }

    /** The fight in progress (server side only), for tools. */
    public Fight fight() {
        return fight;
    }
}
