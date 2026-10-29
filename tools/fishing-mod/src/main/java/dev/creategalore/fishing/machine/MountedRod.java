package dev.creategalore.fishing.machine;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.cast.AliveFish;
import dev.creategalore.fishing.cast.BiteSampler;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.cast.CatchHolding;
import dev.creategalore.fishing.cast.Catches;
import dev.creategalore.fishing.compat.SableShips;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodStats;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * What every rod machine shares, kept by its block entity: the rod (with its line out or not), who set it up, the
 * {@link MachineAngler} it fishes as, the XP it has banked, a landed fish left hanging at the tip, the bite alarm and
 * its redstone pulse, and where catches go.
 *
 * <ul>
 * <li>Right-click with a rod puts it in (a cast line goes with it), an empty hand takes it out (line, bite and fight
 * go with it) or takes a fish that hangs at the tip, which is keeping it. Banked XP is paid out to the player each time
 * a rod goes in or comes out.</li>
 * <li>A machine that lands a fish banks {@link #MACHINE_XP} of what a player would have earned, at once. The catch
 * goes straight into an inventory, belt or funnel under the machine. Failing that the rod turns, the fish hanging from
 * it, and lets it go over a place beside the machine ({@link CatchDrop}: a block touching that side, or a belt,
 * upward funnel, lid-up barrel or hopper a few blocks lower in the same column), one catch at a time, then turns
 * back. With nowhere to go the fish hangs at the line's end for {@link #DANGLE_TICKS} and then wriggles free. Fish
 * put on a belt are still alive for {@link #ALIVE_TICKS}: dropped into water in that time they swim off.</li>
 * </ul>
 */
public final class MountedRod {
    public static final double MACHINE_XP = 0.5;
    public static final int DANGLE_TICKS = 20 * 30;
    public static final int ALIVE_TICKS = CatchHolding.ALIVE_TICKS;
    public static final int PULSE_TICKS = 10;

    private final BlockEntity be;
    private final RodMount mount;
    private ItemStack rod = ItemStack.EMPTY;
    private UUID owner;
    private String ownerName = "";
    private int bankedXp;
    private ItemStack dangling = ItemStack.EMPTY;
    private long dangleUntil;
    private int pulse;
    private int alarmCooldown;
    private int lastComparator;
    private MachineAngler angler;
    private boolean dirty;
    /** Catches waiting for the rod to take them over. */
    private final ArrayDeque<ItemStack> waiting = new ArrayDeque<>();
    /** The catch hanging from the rod on its way to {@link #target}, let go at {@link #release}. */
    private ItemStack carried = ItemStack.EMPTY;
    private CatchDrop.Target target;
    private long release;
    private int fall;
    private boolean letGo;
    private RodTurn turn = RodTurn.REST;
    /** A barrel the machine opened to drop a fish in, and when it closes it again. */
    private BlockPos openBarrel;
    private long closeBarrelAt;

    /**
     * Client side: the player just put a rod in or took one out with the use button, and hasn't let go of it yet
     * (see {@code FishingInput}). Held on, vanilla repeats the use every few ticks: the rod just set in came straight
     * back out, and a rod just taken out reeled its line in.
     */
    public static boolean heldUseAfterRodSwap;

    public MountedRod(BlockEntity be, RodMount mount) {
        this.be = be;
        this.mount = mount;
    }

    public ItemStack rod() {
        return rod;
    }

    public ItemStack dangling() {
        return dangling;
    }

    /** The catch hanging from the rod on its way somewhere (empty if none). */
    public ItemStack carried() {
        return carried;
    }

    /** How the rod is turned on its mount (see {@link RodTurn}). */
    public RodTurn turn() {
        return turn;
    }

    /** Game time the carried catch is let go at. */
    public long releaseAt() {
        return release;
    }

    /** Ticks the carried catch falls for once it is let go. */
    public int fallTicks() {
        return fall;
    }

    /** Height above the machine block's bottom where the carried catch lands (blocks; below zero further down). */
    public float dropTop() {
        return target == null ? 0 : target.top();
    }

    /** True while the rod has a catch to deal with or is still turning: a drive doesn't cast then. */
    public boolean busy() {
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        return !dangling.isEmpty() || !carried.isEmpty() || !waiting.isEmpty() || !turn.atRest() || !turn.done(now);
    }

    public int bankedXp() {
        return bankedXp;
    }

    /** The player who set the rod in, if they are online (server side). */
    public ServerPlayer ownerOnline() {
        return owner != null && be.getLevel() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(owner) : null;
    }

    public String ownerName() {
        return ownerName;
    }

    public BlockPos mountPos() {
        return mount.mountPos();
    }

    public boolean pulsing() {
        return pulse > 0;
    }

    public RodStats stats() {
        return RodStats.of(rod).orElse(null);
    }

    /** The machine's angler (made on first use; server side). */
    public MachineAngler angler() {
        if (angler == null && be.getLevel() instanceof ServerLevel level) angler = new MachineAngler(level, mount);
        if (angler != null && angler.getMainHandItem() != rod) angler.hold(rod);
        return angler;
    }

    /** The hook the machine has out, if any (server side). */
    public CastHook hook() {
        return angler != null && angler.fishing instanceof CastHook hook && !hook.isRemoved() ? hook : null;
    }

    /**
     * Where the tip of a rod mounted at {@code base} (the top centre of the machine) is: the rod rises at 45 degrees
     * over {@code facing}, as long as it is drawn.
     */
    public static Vec3 tip(Vec3 base, Direction facing, RodStats stats) {
        double length = length(stats);
        double reach = length * Math.cos(Math.toRadians(RodTurn.REST_RISE));
        return base.add(facing.getStepX() * reach, length * Math.sin(Math.toRadians(RodTurn.REST_RISE)), facing.getStepZ() * reach);
    }

    /** How far the drawn rod reaches from its butt to its tip, blocks. */
    static double length(RodStats stats) {
        return stats == null ? 1.5 : 0.55 * stats.rod().length() + 0.45;
    }

    /** The rise that puts the tip over the middle of the block beside the machine (one block out from the butt). */
    private float deliveryRise() {
        double length = length(stats());
        return length <= 1 ? 0 : (float) Math.toDegrees(Math.acos(1 / length));
    }

    // ---- players ----

    /**
     * A player's right-click with {@code hand}: takes a hanging fish, puts a rod in, or takes the rod out. True if
     * something happened (on the client: if something would).
     */
    public boolean use(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Level level = be.getLevel();
        if (!dangling.isEmpty() && held.isEmpty()) {
            if (!level.isClientSide) {
                give(player, dangling);
                dangling = ItemStack.EMPTY;
                changed();
            }
            return true;
        }
        if (rod.isEmpty() && held.getItem() instanceof RodItem) {
            if (level.isClientSide) heldUseAfterRodSwap = true;
            if (!level.isClientSide) {
                rod = held;
                player.setItemInHand(hand, ItemStack.EMPTY);
                owner = player.getUUID();
                ownerName = player.getGameProfile().getName();
                MachineAngler machine = angler();
                // The line stays out: a cast rod goes in with its hook, bite or fish.
                if (player.fishing instanceof CastHook hook && !hook.isRemoved() && hook.hand() == hand) hook.handTo(machine);
                payXp(player);
                level.playSound(null, be.getBlockPos(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.BLOCKS, 0.7f, 1.2f);
                changed();
            }
            return true;
        }
        if (!rod.isEmpty() && held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (level.isClientSide) heldUseAfterRodSwap = true;
            if (!level.isClientSide) {
                ItemStack out = rod;
                CastHook hook = hook();
                rod = ItemStack.EMPTY;
                // A catch on its way goes with the rod, to the player.
                if (!carried.isEmpty()) give(player, carried);
                waiting.forEach(s -> give(player, s));
                waiting.clear();
                carried = ItemStack.EMPTY;
                target = null;
                turn = RodTurn.REST;
                if (angler != null) angler.hold(ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, out);
                if (hook != null) hook.handTo(player);
                payXp(player);
                level.playSound(null, be.getBlockPos(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.BLOCKS, 0.7f, 0.9f);
                changed();
            }
            return true;
        }
        return false;
    }

    private void payXp(Player player) {
        if (bankedXp <= 0 || !(be.getLevel() instanceof ServerLevel level)) return;
        ExperienceOrb.award(level, player.position().add(0, 0.5, 0), bankedXp);
        bankedXp = 0;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    // ---- the machine's side ----

    /** A bite: the alarm (a ratchet clicker and a bell) and a redstone pulse. */
    public void alarm() {
        Level level = be.getLevel();
        pulse = PULSE_TICKS;
        level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
        if (alarmCooldown <= 0) {
            level.playSound(null, be.getBlockPos(), FishingRegistry.MACHINE_ALARM.get(), SoundSource.BLOCKS, 1f, 1f);
            level.playSound(null, be.getBlockPos(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5f, 1.4f);
            alarmCooldown = 20;
        }
    }

    /**
     * A landed catch: half the XP banked, the journal and personal best updated for the player who set it up (if they
     * are online), the items sent on.
     */
    public void landed(CastHook hook, List<ItemStack> items, int handXp) {
        bankedXp += (int) Math.round(handXp * MACHINE_XP);
        BiteSampler.Bite bite = hook.bite();
        if (bite != null && owner != null && be.getLevel() instanceof ServerLevel level
                && level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer player) {
            items.stream().filter(s -> s.getItem() == bite.species().item()).findFirst()
                    .ifPresent(fish -> Catches.logInJournal(player, fish, bite));
            CatchHolding.recordBest(player, bite.species(), bite.kg());
        }
        output(items);
        changed();
    }

    /**
     * Sends catches on: straight into an inventory, belt or funnel below the machine, else the rod takes them over to
     * a place beside it one at a time (see {@link #deliver}).
     */
    public void output(List<ItemStack> items) {
        for (ItemStack stack : items) {
            ItemStack rest = insertBelow(stack);
            if (!rest.isEmpty()) waiting.add(rest);
        }
    }

    private ItemStack insertBelow(ItemStack stack) {
        Level level = be.getLevel();
        BlockPos at = be.getBlockPos().below();
        if (level.getBlockEntity(at) instanceof RodMount) return stack;
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, at, Direction.UP);
        if (target == null) return stack;
        // On a belt the fish is still alive for a while (a sorting line can put it back in the water).
        ItemStack sent = isBelt(level.getBlockEntity(at)) && SpeciesManager.of(stack).isPresent() ? alive(stack, level.getGameTime()) : stack;
        ItemStack left = ItemHandlerHelper.insertItemStacked(target, sent, false);
        return left.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(left.getCount());
    }

    /**
     * The rod's side of sending catches on, every tick: takes the next waiting catch, turns over where it goes, holds
     * it there a moment, lets it go and puts it in where it lands; turns back once there is nothing left to take.
     */
    private void deliver(Level level, long now) {
        if (carried.isEmpty()) {
            if (waiting.isEmpty()) {
                if (!turn.atRest()) {
                    turn = turn.to(now, 0, RodTurn.REST_RISE, mount.turnSpeed());
                    changed();
                }
                return;
            }
            carried = waiting.poll();
            plan(level, now);
            return;
        }
        // Saved mid-drop by an older version, or the plan was dropped.
        if (target == null) {
            plan(level, now);
            return;
        }
        if (!letGo) {
            if (now < release) return;
            // Still takes it? The chest may have filled or the funnel been switched off while the rod turned.
            if (CatchDrop.insert(level, target, carried, true).getCount() >= carried.getCount()) {
                plan(level, now);
                return;
            }
            letGo = true;
            if (target.kind() == CatchDrop.Kind.BARREL) openBarrel(level, target.pos(), release + fall + CatchDrop.BARREL_OPEN_AFTER);
            return;
        }
        if (now < release + fall) return;
        boolean fish = SpeciesManager.of(carried).isPresent();
        ItemStack sent = fish && CatchDrop.onBelt(level, target) ? alive(carried, now) : carried;
        ItemStack left = CatchDrop.insert(level, target, sent, false);
        // Filled up in the last moment: it bounces off.
        if (!left.isEmpty()) Block.popResource(level, target.pos().above(), carried.copyWithCount(left.getCount()));
        if (fish) level.playSound(null, target.pos(), SoundEvents.COD_FLOP, SoundSource.BLOCKS, 0.5f, 1.1f);
        carried = ItemStack.EMPTY;
        target = null;
        letGo = false;
        changed();
    }

    /** Picks where the carried catch goes and turns the rod over it; with nowhere to go, a fish hangs at the tip. */
    private void plan(Level level, long now) {
        target = CatchDrop.find(level, be.getBlockPos(), carried);
        letGo = false;
        if (target == null) {
            if (dangling.isEmpty() && SpeciesManager.of(carried).isPresent()) {
                dangling = carried;
                dangleUntil = now + DANGLE_TICKS;
            } else {
                Block.popResource(level, be.getBlockPos().above(), carried);
            }
            carried = ItemStack.EMPTY;
            changed();
            return;
        }
        float rise = deliveryRise();
        turn = turn.to(now, Mth.wrapDegrees(target.side().toYRot() - mount.rodFacing().toYRot()), rise, mount.turnSpeed());
        release = turn.end() + CatchDrop.HOLD_TICKS;
        // The fish hangs about a short line and half a fish below the tip.
        double hang = mount.rodBase().y + length(stats()) * Math.sin(Math.toRadians(rise)) - 0.65;
        fall = CatchDrop.fallTicks(hang - target.top());
        changed();
    }

    /** Opens a barrel to catch a fish (its lid and sound, as when a player opens it), closed again at {@code closeAt}. */
    private void openBarrel(Level level, BlockPos pos, long closeAt) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BarrelBlock)) return;
        if (!state.getValue(BarrelBlock.OPEN)) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), Block.UPDATE_ALL);
            barrelSound(level, pos, SoundEvents.BARREL_OPEN);
        }
        openBarrel = pos;
        closeBarrelAt = closeAt;
    }

    private void closeBarrel(Level level, long now) {
        if (openBarrel == null || now < closeBarrelAt) return;
        BlockPos pos = openBarrel;
        openBarrel = null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BarrelBlock) || !state.getValue(BarrelBlock.OPEN)) return;
        // Someone looking in keeps it open; it closes when they do, as usual.
        BlockEntity barrel = level.getBlockEntity(pos);
        if (level.players().stream().anyMatch(p -> p.containerMenu instanceof ChestMenu m && m.getContainer() == barrel)) return;
        level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), Block.UPDATE_ALL);
        barrelSound(level, pos, SoundEvents.BARREL_CLOSE);
    }

    private static void barrelSound(Level level, BlockPos pos, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
    }

    static boolean isBelt(BlockEntity target) {
        return target != null && target.getClass().getName().startsWith("com.simibubi.create.content.kinetics.belt.");
    }

    /** {@code fish} marked alive until {@link #ALIVE_TICKS} from {@code now} (see {@link AliveFish}). */
    public static ItemStack alive(ItemStack fish, long now) {
        return AliveFish.mark(fish, now + ALIVE_TICKS);
    }

    /** Every tick on the server: the redstone pulse, a hanging fish working itself free, parts left with the angler. */
    public void tick() {
        Level level = be.getLevel();
        if (alarmCooldown > 0) alarmCooldown--;
        if (pulse > 0 && --pulse == 0) level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
        if (!dangling.isEmpty() && level.getGameTime() > dangleUntil && level instanceof ServerLevel server) {
            Vec3 at = mount.tip();
            server.sendParticles(ParticleTypes.SPLASH, at.x, at.y - 0.5, at.z, 12, 0.2, 0.1, 0.2, 0.1);
            level.playSound(null, BlockPos.containing(at), SoundEvents.FISH_SWIM, SoundSource.BLOCKS, 0.6f, 1.2f);
            dangling = ItemStack.EMPTY;
            changed();
        }
        step(level.getGameTime());
        if (angler != null) {
            // Where the machine really is (on a ship its block entity sits in the ship's plot).
            Vec3 at = SableShips.toWorld(level, Vec3.atCenterOf(mount.mountPos()));
            angler.setPos(at.x, at.y, at.z);
            if (angler.getMainHandItem() != rod) angler.hold(rod);
            // Parts a broken rod gave back, and anything else the angler was handed, come out of the machine.
            var inventory = angler.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (i == inventory.selected) continue;
                ItemStack s = inventory.getItem(i);
                if (!s.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                    output(List.of(s));
                }
            }
        }
        int signal = comparator(hook());
        if (signal != lastComparator) {
            lastComparator = signal;
            level.updateNeighbourForOutputSignal(be.getBlockPos(), be.getBlockState().getBlock());
        }
        if (dirty) {
            dirty = false;
            be.setChanged();
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Moves catches on their way at game time {@code now} (the self-test steps it through its own clock). */
    void step(long now) {
        deliver(be.getLevel(), now);
        closeBarrel(be.getLevel(), now);
    }

    /** The machine is broken or unloaded: its line comes back. */
    public void removed() {
        CastHook hook = hook();
        if (hook != null) hook.discard();
    }

    /** The machine is broken: the rod, a hanging fish and the banked XP come out. */
    public void dropContents() {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        removed();
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), rod);
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), dangling);
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), carried);
        waiting.forEach(s -> Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), s));
        waiting.clear();
        carried = ItemStack.EMPTY;
        target = null;
        if (bankedXp > 0 && level instanceof ServerLevel server) ExperienceOrb.award(server, Vec3.atCenterOf(pos), bankedXp);
        rod = dangling = ItemStack.EMPTY;
        bankedXp = 0;
    }

    /** 0 to 15 for a comparator: full while a fish bites, else the line's tension. */
    public int comparator(CastHook hook) {
        if (hook == null) return 0;
        if (hook.phase() == CastHook.Phase.BITE) return 15;
        if (hook.phase() != CastHook.Phase.FIGHTING) return 0;
        return Math.min(15, Math.round(hook.tension() * 15));
    }

    public void changed() {
        dirty = true;
        be.setChanged();
    }

    // ---- saving ----

    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        if (!rod.isEmpty()) tag.put("rod", rod.save(registries));
        if (!dangling.isEmpty()) tag.put("dangling", dangling.save(registries));
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("owner_name", ownerName);
        tag.putInt("banked_xp", bankedXp);
        tag.putLong("dangle_until", dangleUntil);
        if (!carried.isEmpty()) tag.put("carried", carried.save(registries));
        if (target != null) tag.put("drop_target", target.save());
        tag.putLong("drop_release", release);
        tag.putInt("drop_fall", fall);
        tag.putBoolean("drop_let_go", letGo);
        tag.put("rod_turn", turn.save());
        ListTag queue = new ListTag();
        waiting.forEach(s -> queue.add(s.save(registries)));
        if (!queue.isEmpty()) tag.put("waiting", queue);
        if (openBarrel != null) {
            tag.put("open_barrel", NbtUtils.writeBlockPos(openBarrel));
            tag.putLong("close_barrel_at", closeBarrelAt);
        }
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        rod = tag.contains("rod") ? ItemStack.parseOptional(registries, tag.getCompound("rod")) : ItemStack.EMPTY;
        dangling = tag.contains("dangling") ? ItemStack.parseOptional(registries, tag.getCompound("dangling")) : ItemStack.EMPTY;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        bankedXp = tag.getInt("banked_xp");
        dangleUntil = tag.getLong("dangle_until");
        carried = tag.contains("carried") ? ItemStack.parseOptional(registries, tag.getCompound("carried")) : ItemStack.EMPTY;
        target = tag.contains("drop_target") ? CatchDrop.Target.load(tag.getCompound("drop_target")) : null;
        release = tag.getLong("drop_release");
        fall = tag.getInt("drop_fall");
        letGo = tag.getBoolean("drop_let_go");
        turn = RodTurn.load(tag.getCompound("rod_turn"));
        waiting.clear();
        for (Tag t : tag.getList("waiting", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.parseOptional(registries, (CompoundTag) t);
            if (!s.isEmpty()) waiting.add(s);
        }
        openBarrel = NbtUtils.readBlockPos(tag, "open_barrel").orElse(null);
        closeBarrelAt = tag.getLong("close_barrel_at");
        if (angler != null) angler.hold(rod);
    }
}
