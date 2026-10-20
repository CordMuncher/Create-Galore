package dev.creategalore.fishing.aquarium;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.fish.SpeciesManager;
import dev.creategalore.fishing.sim.SizeClass;
import dev.creategalore.fishing.tide.data.item.TideDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One block of an Aquarium Tank. Every block knows its tank's controller (the tank's lowest corner); the controller
 * holds what the tank holds: its size, its water, the fish living in it and the fish meal waiting for them.
 *
 * <p>Fish are kept as their fish items (with their size), not as mobs, so a full tank costs no more than a chest; the
 * client draws them swimming ({@code client/AquariumRenderer}). A fish takes room by its size
 * ({@link #space}) and must be shorter than the tank's longest side. Two or more of one baitfish species
 * ({@code #create_galore_fishing:baitfish}) with fish meal to eat raise a young one every {@link #BREED_TICKS}.
 */
public class AquariumBlockEntity extends BlockEntity {
    /** A tank of this many fish is full however big it is: what the renderer draws stays bounded. */
    public static final int MOST_FISH = 48;
    public static final int MOST_FOOD = 64;
    /** A young baitfish every two minutes while there's a pair and fish meal. */
    public static final int BREED_TICKS = 20 * 120;
    /** Breeders a tank keeps of each baitfish species: scooping and hoppers only take the rest. */
    public static final int BREEDERS = 2;
    public static final TagKey<Item> BAITFISH = TagKey.create(Registries.ITEM, dev.creategalore.fishing.CreateGaloreFishing.id("baitfish"));

    private BlockPos controller;
    private int sizeX = 1, sizeY = 1, sizeZ = 1;
    private int water;
    private int food;
    private long nextBreed;
    private final List<ItemStack> fish = new ArrayList<>();
    private final Handlers handlers = new Handlers();

    /** The client's swimming state for this tank's fish, owned by the renderer (never touched on the server). */
    public Object clientView;

    public AquariumBlockEntity(BlockPos pos, BlockState state) {
        super(FishingRegistry.AQUARIUM_ENTITY.get(), pos, state);
    }

    // ---- membership ----

    public BlockPos controllerPos() {
        return controller;
    }

    public boolean isController() {
        return worldPosition.equals(controller);
    }

    /** This block's tank (its controller), or null while it hasn't joined one. */
    public AquariumBlockEntity tank() {
        if (controller == null || level == null) return null;
        if (isController()) return this;
        return level.getBlockEntity(controller) instanceof AquariumBlockEntity c && c.isController() ? c : null;
    }

    void joinTank(BlockPos controller) {
        this.controller = controller.immutable();
        if (!isController()) clearContents();
        changed();
    }

    /** Makes this block the controller of a tank of the given size (its contents are set separately). */
    void lead(int sx, int sy, int sz) {
        controller = worldPosition;
        sizeX = sx;
        sizeY = sy;
        sizeZ = sz;
        changed();
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    /** Every block of this (controller's) tank. */
    public List<BlockPos> members() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(worldPosition, worldPosition.offset(sizeX - 1, sizeY - 1, sizeZ - 1))) out.add(p.immutable());
        return out;
    }

    public AABB box() {
        return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + sizeX, worldPosition.getY() + sizeY, worldPosition.getZ() + sizeZ);
    }

    // ---- contents (controller) ----

    public int water() {
        return water;
    }

    public int capacity() {
        return volume() * 1000;
    }

    public int food() {
        return food;
    }

    public List<ItemStack> fish() {
        return fish;
    }

    /** What the tank holds, taken out of it (for merging and splitting tanks). */
    record Contents(List<ItemStack> fish, int water, int food) {
        static final Contents EMPTY = new Contents(List.of(), 0, 0);
    }

    Contents takeContents() {
        Contents out = new Contents(new ArrayList<>(fish), water, food);
        clearContents();
        changed();
        return out;
    }

    private void clearContents() {
        fish.clear();
        water = food = 0;
    }

    /** Puts {@code in} into this tank; returns the fish that didn't fit. */
    List<ItemStack> put(Contents in) {
        water = Math.min(capacity(), water + in.water());
        food = Math.min(MOST_FOOD, food + in.food());
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack f : in.fish()) {
            if (fits(f)) fish.add(f);
            else left.add(f);
        }
        changed();
        return left;
    }

    /** A fish's length in cm: its catch's, its Tide length, else the usual for its size class. */
    public static double cm(ItemStack fish) {
        CatchInfo info = fish.get(FishingRegistry.CATCH.get());
        if (info != null && info.cm().isPresent()) return info.cm().get();
        Double tide = fish.get(TideDataComponents.FISH_LENGTH);
        if (tide != null && tide > 0) return tide;
        SizeClass size = info != null ? info.size() : SizeClass.COMMON;
        return SpeciesManager.of(fish).map(s -> s.size().usualLengthCm(size)).orElse(30.0);
    }

    /** Water a fish needs, in blocks: a fiftieth of a block for a minnow, half a block for a metre-long fish. */
    public static double space(double cm) {
        double m = cm / 100;
        return Math.max(0.02, 0.5 * m * m);
    }

    public double used() {
        return fish.stream().mapToDouble(f -> space(cm(f))).sum();
    }

    public double room() {
        return volume() - used();
    }

    /** True if {@code f} can live here now: water at least half full, room for it, and the tank longer than it. */
    public boolean fits(ItemStack f) {
        double cm = cm(f);
        return water * 2 >= capacity() && fish.size() < MOST_FISH && space(cm) <= room() + 1e-9
                && cm / 100 <= Math.max(sizeX, sizeZ);
    }

    /** Lets a fish into the tank, its length written onto it so it keeps its size; false if it doesn't fit. */
    public boolean addFish(ItemStack f) {
        if (!fits(f)) return false;
        ItemStack in = f.copyWithCount(1);
        if (in.get(TideDataComponents.FISH_LENGTH) == null) in.set(TideDataComponents.FISH_LENGTH, cm(in));
        fish.add(in);
        changed();
        return true;
    }

    /** Takes out the first fish {@code which} accepts; empty if there's none. */
    public ItemStack takeFish(Predicate<ItemStack> which) {
        for (int i = 0; i < fish.size(); i++) {
            if (which.test(fish.get(i))) {
                ItemStack out = fish.remove(i);
                changed();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Baitfish beyond the breeders of their species: what scooping and hoppers may take. */
    public boolean spare(ItemStack f) {
        if (!f.is(BAITFISH)) return false;
        return fish.stream().filter(o -> o.getItem() == f.getItem()).count() > BREEDERS;
    }

    public int fill(int mb, boolean simulate) {
        int moved = Math.max(0, Math.min(mb, capacity() - water));
        if (!simulate && moved > 0) {
            water += moved;
            changed();
        }
        return moved;
    }

    public int drain(int mb, boolean simulate) {
        int moved = Math.max(0, Math.min(mb, water));
        if (!simulate && moved > 0) {
            water -= moved;
            changed();
        }
        return moved;
    }

    /** Feeds fish meal; returns how many were taken. */
    public int feed(int count) {
        int taken = Math.max(0, Math.min(count, MOST_FOOD - food));
        if (taken > 0) {
            food += taken;
            changed();
        }
        return taken;
    }

    // ---- breeding ----

    static void serverTick(Level level, BlockPos pos, BlockState state, AquariumBlockEntity be) {
        if (be.isController() && level.getGameTime() % 20 == 0) be.breed(level.getGameTime());
    }

    /** One breeding check at {@code now}: a young fish of a species with a pair here, if there's food and room. */
    public boolean breed(long now) {
        if (now < nextBreed || food <= 0 || water * 2 < capacity()) return false;
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack f : fish) if (f.is(BAITFISH)) counts.merge(f.getItem(), 1, Integer::sum);
        for (Map.Entry<Item, Integer> e : counts.entrySet()) {
            if (e.getValue() < 2) continue;
            ItemStack young = youngOf(e.getKey(), now);
            if (!fits(young)) continue;
            fish.add(young);
            food--;
            nextBreed = now + BREED_TICKS;
            changed();
            return true;
        }
        return false;
    }

    private static ItemStack youngOf(Item item, long now) {
        ItemStack young = new ItemStack(item);
        SpeciesManager.of(young).ifPresent(s -> {
            double cm = s.size().usualLengthCm(SizeClass.YOUNG);
            young.set(FishingRegistry.CATCH.get(), CatchInfo.of(SizeClass.YOUNG, cm, s.size().weightKg(cm), "", now / 24000));
            young.set(TideDataComponents.FISH_LENGTH, cm);
        });
        return young;
    }

    // ---- capabilities ----

    public IItemHandler items() {
        AquariumBlockEntity tank = tank();
        return tank == null ? null : tank.handlers;
    }

    public IFluidHandler fluids() {
        AquariumBlockEntity tank = tank();
        return tank == null ? null : tank.handlers;
    }

    /**
     * Pipes fill and drain the water; slot 0 takes fish meal (shown as the food waiting), and the other slots are
     * the fish, from which hoppers and funnels may take spare baitfish.
     */
    private final class Handlers implements IItemHandler, IFluidHandler {
        @Override
        public int getSlots() {
            return 1 + MOST_FISH;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 0) return food > 0 ? new ItemStack(FishingRegistry.FISH_MEAL.get(), food) : ItemStack.EMPTY;
            return slot - 1 < fish.size() ? fish.get(slot - 1) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !stack.is(FishingRegistry.FISH_MEAL.get())) return stack;
            int taken = Math.min(stack.getCount(), MOST_FOOD - food);
            if (taken <= 0) return stack;
            if (!simulate) feed(taken);
            return stack.copyWithCount(stack.getCount() - taken);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 0 || amount <= 0 || slot - 1 >= fish.size()) return ItemStack.EMPTY;
            ItemStack f = fish.get(slot - 1);
            if (!spare(f)) return ItemStack.EMPTY;
            if (simulate) return f.copy();
            fish.remove(slot - 1);
            changed();
            return f;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? MOST_FOOD : 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.is(FishingRegistry.FISH_MEAL.get());
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return water > 0 ? new FluidStack(Fluids.WATER, water) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(Fluids.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return resource.is(Fluids.WATER) ? AquariumBlockEntity.this.fill(resource.getAmount(), action.simulate()) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.is(Fluids.WATER) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            int moved = AquariumBlockEntity.this.drain(maxDrain, action.simulate());
            return moved > 0 ? new FluidStack(Fluids.WATER, moved) : FluidStack.EMPTY;
        }
    }

    // ---- saving and syncing ----

    void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void write(CompoundTag tag, HolderLookup.Provider registries) {
        if (controller != null) tag.put("controller", NbtUtils.writeBlockPos(controller));
        if (!isController()) return;
        tag.putIntArray("size", new int[]{sizeX, sizeY, sizeZ});
        tag.putInt("water", water);
        tag.putInt("food", food);
        ListTag list = new ListTag();
        for (ItemStack f : fish) list.add(f.save(registries));
        tag.put("fish", list);
    }

    private void read(CompoundTag tag, HolderLookup.Provider registries) {
        controller = NbtUtils.readBlockPos(tag, "controller").orElse(null);
        clearContents();
        int[] size = tag.getIntArray("size");
        if (size.length == 3) {
            sizeX = size[0];
            sizeY = size[1];
            sizeZ = size[2];
        } else {
            sizeX = sizeY = sizeZ = 1;
        }
        water = tag.getInt("water");
        food = tag.getInt("food");
        for (Tag t : tag.getList("fish", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, t).ifPresent(fish::add);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag, registries);
        tag.putLong("next_breed", nextBreed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(tag, registries);
        nextBreed = tag.getLong("next_breed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag, registries);
        // Never empty: an empty update tag isn't applied, and a tank that lost its fish would keep drawing them.
        tag.putBoolean("synced", true);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
