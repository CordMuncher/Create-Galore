package dev.creategalore.fishing.cast;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.Species;
import dev.creategalore.fishing.sim.Controls;
import dev.creategalore.fishing.sim.Curve;
import dev.creategalore.fishing.sim.Fight;
import dev.creategalore.fishing.sim.FishFight;
import dev.creategalore.fishing.sim.Outcome;
import dev.creategalore.fishing.sim.SizeModel;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TacklePart;
import dev.creategalore.fishing.tide.data.FishingContext;
import dev.creategalore.fishing.tide.data.FishingPlace;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
    private static final EntityDataAccessor<Float> LINE_OUT = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LINE_LEFT = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.BYTE);
    /** The hooked fish's item id while fighting (empty otherwise), so clients can draw it. */
    private static final EntityDataAccessor<String> FISH = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> FISH_CM = SynchedEntityData.defineId(CastHook.class, EntityDataSerializers.FLOAT);

    public static final int FLAG_SLIPPING = 1;
    public static final int FLAG_STALLED = 2;
    public static final int FLAG_RUNNING = 4;
    public static final int FLAG_SLACK = 8;
    public static final int FLAG_REELING = 16;

    public static final byte EVENT_BITE = 61;
    public static final byte EVENT_JUMP = 62;
    public static final byte EVENT_SPLASH = 63;

    /** The rig's dead weight when junk or treasure is on the line. */
    static final FishFight DEAD_WEIGHT = new FishFight(0.6, 0.01, 0.4, Curve.preset("steady"), 0, 0, 0, false);
    private static final double GRAVITY = 0.04;

    private enum CatchKind { FISH, JUNK, TREASURE }

    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private String medium = "water";
    private double castLimit;
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
    private Fight fight;
    private final Controls controls = new Controls();
    private double heading;
    private int sound;
    private boolean forceBite;

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
        CastHook hook = new CastHook(FishingRegistry.CAST_HOOK.get(), player.level());
        hook.hand = hand;
        hook.setOwner(player);
        Vec3 tip = tip(player);
        hook.setPos(tip.x, tip.y, tip.z);
        double distance = Math.max(3, stats.maxCast() * (0.25 + 0.75 * Mth.clamp(charge, 0, 1)));
        hook.castLimit = distance;
        double elevation = Math.toRadians(Mth.clamp(-player.getXRot() + 20, 10, 55));
        double speed = Math.sqrt(distance * GRAVITY / Math.sin(2 * elevation));
        double yaw = Math.toRadians(player.getYRot());
        double horizontal = speed * Math.cos(elevation);
        hook.setDeltaMovement(-Math.sin(yaw) * horizontal, speed * Math.sin(elevation), Math.cos(yaw) * horizontal);
        hook.entityData.set(RIG, (byte) rigOf(stats).ordinal());
        hook.entityData.set(LINE_LEFT, (float) stats.lineMeters());
        player.level().addFreshEntity(hook);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.NEUTRAL, 0.5f, 0.4f / (player.getRandom().nextFloat() * 0.4f + 0.8f));
        return hook;
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
        builder.define(LINE_OUT, 0f);
        builder.define(LINE_LEFT, 0f);
        builder.define(FLAGS, (byte) 0);
        builder.define(FISH, "");
        builder.define(FISH_CM, 0f);
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

    public float lineOut() {
        return entityData.get(LINE_OUT);
    }

    public float lineLeft() {
        return entityData.get(LINE_LEFT);
    }

    public boolean flag(int flag) {
        return (entityData.get(FLAGS) & flag) != 0;
    }

    /** The hooked fish's item, or null while nothing is on. */
    public ResourceLocation fishId() {
        String id = entityData.get(FISH);
        return id.isEmpty() ? null : ResourceLocation.tryParse(id);
    }

    public float fishLengthCm() {
        return entityData.get(FISH_CM);
    }

    public InteractionHand hand() {
        return hand;
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        baseTick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps,
                        getZ() + (lerpZ - getZ()) / lerpSteps);
                lerpSteps--;
            }
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
        Vec3 tip = tip(owner);
        switch (phase()) {
            case FLYING -> fly(owner, stats, tip);
            case WAITING, GROUNDED -> waitForBite(owner, rod, stats, tip);
            case BITE -> biting(owner, rod, stats, tip);
            case FIGHTING -> fighting(owner, rod, stats, tip);
        }
        if (!isRemoved()) entityData.set(LINE_OUT, (float) tip.distanceTo(position()));
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
        level().playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3f, 1.2f);
    }

    static boolean lavaProof(RodStats stats) {
        return stats.rod().lava() || stats.hook().map(h -> h.is("lava")).orElse(false);
    }

    private boolean reeling(Player owner) {
        return owner.isUsingItem() && owner.getUsedItemHand() == hand && owner.getUseItem().getItem() instanceof RodItem;
    }

    private double surfaceY() {
        return medium.equals("void") ? FishingWater.voidSurface(level()) : FishingWater.surface(level(), blockPosition());
    }

    private void waitForBite(Player owner, ItemStack rod, RodStats stats, Vec3 tip) {
        boolean reeling = reeling(owner);
        double retrieve = (stats.reel().map(TacklePart.Reel::retrieve).orElse(0.9)) / 20;
        Vec3 pos = position();
        Vec3 toTip = new Vec3(tip.x - pos.x, 0, tip.z - pos.z);
        double flat = toTip.length();
        if (reeling && flat < 1.6) {
            discard();
            return;
        }
        Vec3 step = reeling && flat > 1e-3 ? toTip.scale(Math.min(flat, retrieve) / flat) : Vec3.ZERO;
        double x = pos.x + step.x;
        double z = pos.z + step.z;
        double y = pos.y;
        boolean inMedium = phase() == Phase.WAITING && FishingWater.in(level(), BlockPos.containing(x, y, z), medium);
        if (phase() == Phase.GROUNDED || !inMedium) {
            if (reeling) setPos(x, Math.max(y, tip.y - 1.5), z);
            return;
        }
        double surface = surfaceY();
        double bottom = medium.equals("void") ? surface - 64 : FishingWater.bottom(level(), BlockPos.containing(x, y, z));
        switch (rig()) {
            case FLOAT, HOOK -> y = surface - 0.15;
            case SINKER -> y = Math.max(bottom + 0.1, y - 0.08);
            case LURE -> {
                TacklePart.Lure lure = (TacklePart.Lure) stats.terminal().orElseThrow();
                double target = reeling ? Math.max(bottom + 0.3, surface - lure.dive()) : bottom + 0.2;
                y += Mth.clamp(target - y, -0.06, 0.06);
                double speed = step.length() * 20;
                double working = reeling ? (speed >= lure.speedMin() && speed <= lure.speedMax() ? 1 : 0.3)
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

    private void rollBite(Player owner, ItemStack rod, RodStats stats) {
        ServerLevel level = (ServerLevel) level();
        int luck = luck(owner, rod, stats);
        if (level.getGameTime() - poolBuiltAt > 100 || position().distanceToSqr(poolPos) > 16) {
            FishingContext context = FishingPlace.context(level, position(), rod, RodItem.parts(rod).hook(), luck,
                    medium, temperatureOffset(stats), false, openWater, random);
            pool = BiteSampler.pool(context, stats, lureWorking);
            poolBuiltAt = level.getGameTime();
            poolPos = position();
        }
        double wait = BiteSampler.meanWaitSeconds(pool);
        wait *= 1 - 0.15 * EnchantmentHelper.getFishingTimeReduction(level, rod, owner) / 5;
        wait /= FishingPressure.of(level).biteFactor(level, blockPosition());
        boolean day = level.isDay();
        if (stats.hook().map(h -> h.is("barbed")).orElse(false) && day) wait *= 0.75;
        if (stats.hook().map(h -> h.is("glowing")).orElse(false) && !day) wait *= 0.75;
        if (medium.equals("lava")) wait *= 1.6;
        if (medium.equals("void")) wait *= 1.8;
        if (level.isRaining()) wait *= 0.85;
        wait = Mth.clamp(wait, 5, 240);
        if (!forceBite && random.nextDouble() >= 1 - Math.exp(-0.5 / wait)) return;
        forceBite = false;

        double treasure = openWater ? Math.max(0, 0.02 + 0.015 * luck) : 0;
        if (stats.hook().map(h -> h.is("magnetic")).orElse(false)) treasure *= 2;
        double junk = pool.total() <= 0 ? 0.6 : Math.max(0.02, 0.08 - 0.02 * luck);
        double r = random.nextDouble();
        if (r < treasure || r < treasure + junk) {
            catchKind = r < treasure ? CatchKind.TREASURE : CatchKind.JUNK;
            loot = Catches.lootFrom(this, rod, catchKind == CatchKind.TREASURE, luck);
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
        biteTicks = rig() == Rig.LURE ? 20 : 28;
        level.broadcastEntityEvent(this, EVENT_BITE);
        level.playSound(null, blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.35f,
                1 + (random.nextFloat() - random.nextFloat()) * 0.4f);
    }

    /** Luck of the Sea, the angler's luck attribute and a lucky hook. */
    private static int luck(Player owner, ItemStack rod, RodStats stats) {
        int luck = EnchantmentHelper.getFishingLuckBonus((ServerLevel) owner.level(), rod, owner);
        luck += Math.round(owner.getLuck());
        luck += stats.hook().map(TacklePart.Hook::luck).orElse(0);
        return luck;
    }

    private static float temperatureOffset(RodStats stats) {
        if (stats.hook().map(h -> h.is("fiery")).orElse(false)) return 0.25f;
        if (stats.hook().map(h -> h.is("permafrost")).orElse(false)) return -0.25f;
        return 0;
    }

    private void biting(Player owner, ItemStack rod, RodStats stats, Vec3 tip) {
        if (reeling(owner)) {
            strike(owner, rod, stats, tip);
            return;
        }
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
        } else {
            ff = DEAD_WEIGHT;
            kg = catchKind == CatchKind.TREASURE ? 3 : 1;
            hold = 1;
        }
        fight = new Fight(ff, stats.tackle(hold), kg, Math.max(3, tip.distanceTo(position())));
        heading = Math.atan2(getZ() - tip.z, getX() - tip.x);
        setPhase(Phase.FIGHTING);
        entityData.set(BREAK, (float) stats.breakingLoad());
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
        controls.reeling = reeling(owner);
        controls.drag = stats.drag();
        controls.crankLimit = Double.NaN;
        controls.retrieveScale = 1;
        controls.crankThroughSlip = true;
        controls.rodAngle = rodAngle(owner, tip, position());
        fight.tick(controls, BiteSampler.adapt(random));
        moveFish(tip);

        if (fight.jumped()) level().broadcastEntityEvent(this, EVENT_JUMP);
        double load = fight.peakTension();
        entityData.set(TENSION, (float) (load / Math.max(0.01, fight.tackle().breakingLoad())));
        entityData.set(ROD_LOAD, (float) (load / Math.max(0.01, fight.tackle().rodMaxLoad())));
        entityData.set(DRAG, (float) (stats.isPole() ? 0 : controls.drag));
        int flags = (fight.slipping() ? FLAG_SLIPPING : 0) | (fight.stalled() ? FLAG_STALLED : 0)
                | (fight.running() ? FLAG_RUNNING : 0) | (fight.tension() < 0.06 * fight.maxPull() ? FLAG_SLACK : 0)
                | (controls.reeling ? FLAG_REELING : 0);
        entityData.set(FLAGS, (byte) flags);
        if (fight.slipping() && ++sound % 3 == 0) {
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), FishingRegistry.DRAG_CLICK.get(),
                    SoundSource.PLAYERS, 0.35f, 0.9f + random.nextFloat() * 0.3f);
        }
        if (fight.outcome().over()) finish(owner, rod, stats, fight.outcome());
    }

    /** The hooked fish swims around the angler at the fight's distance, staying in its fluid. */
    private void moveFish(Vec3 tip) {
        heading += random.nextGaussian() * (fight.running() ? 0.08 : 0.025);
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
        double depth = fight.jumped() ? -0.6 : 0.7 + 0.5 * Math.sin(tickCount * 0.05);
        setPos(next.x, surface - depth, next.z);
    }

    /**
     * The angle between the rod and the line. The rod is held about 25 degrees above where the angler looks, so
     * looking at the fish gives a good bend; looking away or up adds side pressure; pointing at the water below the
     * fish leaves the rod straight and unable to soak shocks.
     */
    public static double rodAngle(Player owner, Vec3 tip, Vec3 fish) {
        Vec3 rod = Vec3.directionFromRotation(owner.getXRot() - 25, owner.getYRot());
        Vec3 toFish = fish.subtract(tip);
        if (toFish.lengthSqr() < 1e-6) return 0;
        return Math.acos(Mth.clamp(rod.dot(toFish.normalize()), -1, 1));
    }

    private void finish(Player owner, ItemStack rod, RodStats stats, Outcome outcome) {
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        if (outcome == Outcome.LANDED) {
            ServerLevel level = (ServerLevel) level();
            if (catchKind == CatchKind.FISH) {
                Species species = bite.species();
                ItemStack fish = Catches.fishItem(species, bite, owner.getGameProfile().getName(), level.getDayTime() / 24000);
                List<ItemStack> drops = Catches.fire(this, owner, rod, List.of(fish), species, bite.kg());
                Catches.give(owner, drops, Catches.xp(species, bite, fight));
                FishingPressure.of(level).addCatch(level, blockPosition());
                TackleWear.tell(owner, Catches.describe(species, bite));
                if (rig() != Rig.LURE) {
                    RodParts parts = RodItem.parts(rod);
                    ItemStack bait = parts.bait();
                    if (!bait.isEmpty()) {
                        bait.shrink(1);
                        RodItem.setParts(rod, parts.with(RodParts.Slot.BAIT, bait));
                    }
                }
            } else {
                List<ItemStack> drops = Catches.fire(this, owner, rod, loot, null, 0);
                Catches.give(owner, drops, catchKind == CatchKind.TREASURE ? 3 : 1);
            }
        } else {
            TackleWear.tell(owner, Component.translatable("message.create_galore_fishing.lost." + outcome.name().toLowerCase(java.util.Locale.ROOT)));
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    outcome.losesTackle() ? FishingRegistry.LINE_SNAP.get() : SoundEvents.FISHING_BOBBER_RETRIEVE,
                    SoundSource.PLAYERS, 0.8f, 1f);
        }
        TackleWear.apply(owner, slot, rod, fight, outcome);
        discard();
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
            return;
        }
        super.handleEntityEvent(id);
    }

    /** Test tool: the next bite roll succeeds (if anything at all would bite here). */
    public void forceBite() {
        forceBite = true;
        cooldown = 0;
    }

    /** One line describing the hook's state, for {@code /cgfish hook}. */
    public String describe() {
        StringBuilder b = new StringBuilder(phase().name()).append(' ').append(medium)
                .append(String.format(java.util.Locale.ROOT, " out %.1f m", lineOut()));
        b.append(String.format(java.util.Locale.ROOT, ", %d species could bite (quality %.2f, mean wait %.0f s)",
                pool.species().size(), pool.quality(), BiteSampler.meanWaitSeconds(pool)));
        if (fight != null) {
            b.append(String.format(java.util.Locale.ROOT, "; fight %s %.2f kg at %.1f m, tension %.1f/%.1f kg, drag %.1f, stamina %.2f, %s",
                    fishId(), fight.weight(), fight.distance(), fight.tension(), fight.tackle().breakingLoad(),
                    controls.drag, fight.stamina(), fight.outcome()));
        }
        return b.toString();
    }

    /** The fight in progress (server side only), for tools. */
    public Fight fight() {
        return fight;
    }
}
