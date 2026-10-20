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
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * goes into an inventory, belt or funnel below or beside the machine; failing that the fish hangs at the line's end
 * for {@link #DANGLE_TICKS} and then wriggles free. Fish put on a belt are still alive for
 * {@link #ALIVE_TICKS}: dropped into water in that time they swim off.</li>
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

    public int bankedXp() {
        return bankedXp;
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
        double length = stats == null ? 1.5 : 0.55 * stats.rod().length() + 0.45;
        double reach = length * Math.cos(Math.toRadians(45));
        return base.add(facing.getStepX() * reach, length * Math.sin(Math.toRadians(45)), facing.getStepZ() * reach);
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
            if (!level.isClientSide) {
                ItemStack out = rod;
                CastHook hook = hook();
                rod = ItemStack.EMPTY;
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

    /** Sends catches on: into an inventory, belt or funnel below or beside, else the first fish hangs at the tip. */
    public void output(List<ItemStack> items) {
        Level level = be.getLevel();
        for (ItemStack stack : items) {
            ItemStack rest = insertNearby(stack);
            if (rest.isEmpty()) continue;
            if (dangling.isEmpty() && SpeciesManager.of(rest).isPresent()) {
                dangling = rest;
                dangleUntil = level.getGameTime() + DANGLE_TICKS;
            } else {
                Block.popResource(level, be.getBlockPos().above(), rest);
            }
        }
    }

    private ItemStack insertNearby(ItemStack stack) {
        Level level = be.getLevel();
        ItemStack rest = stack;
        for (Direction side : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos at = be.getBlockPos().relative(side);
            BlockEntity next = level.getBlockEntity(at);
            // A row of drives would otherwise feed each other's bait slots with the catch.
            if (next instanceof RodMount) continue;
            IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, at, side.getOpposite());
            if (target == null) continue;
            // On a belt the fish is still alive for a while (a sorting line can put it back in the water).
            boolean belt = isBelt(next);
            ItemStack sent = belt && SpeciesManager.of(rest).isPresent() ? alive(rest, level.getGameTime()) : rest;
            ItemStack left = ItemHandlerHelper.insertItemStacked(target, sent, false);
            if (left.getCount() < sent.getCount()) {
                rest = left.isEmpty() ? ItemStack.EMPTY : rest.copyWithCount(left.getCount());
                if (rest.isEmpty()) return rest;
            }
        }
        return rest;
    }

    private static boolean isBelt(BlockEntity target) {
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
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        rod = tag.contains("rod") ? ItemStack.parseOptional(registries, tag.getCompound("rod")) : ItemStack.EMPTY;
        dangling = tag.contains("dangling") ? ItemStack.parseOptional(registries, tag.getCompound("dangling")) : ItemStack.EMPTY;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        bankedXp = tag.getInt("banked_xp");
        dangleUntil = tag.getLong("dangle_until");
        if (angler != null) angler.hold(rod);
    }
}
