package dev.creategalore.fishing.process;
import dev.creategalore.fishing.FishingRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The Cleaning Table's contents: the fish lying on it, how many strokes it has had and needs
 * ({@link Cleaning#strokes}), and the parts waiting to be taken.
 *
 * <p>A player cleaning by hand gets the parts straight into their inventory. A Deployer with a knife (or anything else
 * that isn't a real player) leaves them in the table's nine output slots, which hoppers and funnels empty. Its item
 * handler takes a fish into slot 0 only while the table is empty and the outputs are cleared, so an automatic line
 * waits for its parts to be carried off; slots 1 to 9 are the outputs, extract only.
 */
public class CleaningTableBlockEntity extends BlockEntity {
    public static final int OUTPUT_SLOTS = 9;

    private ItemStack fish = ItemStack.EMPTY;
    private int strokes;
    private int needed;
    private final NonNullList<ItemStack> outputs = NonNullList.withSize(OUTPUT_SLOTS, ItemStack.EMPTY);
    private final IItemHandler items = new Items();

    public CleaningTableBlockEntity(BlockPos pos, BlockState state) {
        super(FishingRegistry.CLEANING_TABLE_ENTITY.get(), pos, state);
    }

    public ItemStack fish() {
        return fish;
    }

    /** Strokes the fish has had so far. */
    public int strokes() {
        return strokes;
    }

    /** Strokes the fish needs in all. */
    public int needed() {
        return needed;
    }

    public IItemHandler items() {
        return items;
    }

    private boolean outputsEmpty() {
        return outputs.stream().allMatch(ItemStack::isEmpty);
    }

    /** Lays one fish from {@code stack} on the table; false if one is there already or it isn't a fish. */
    public boolean place(ItemStack stack) {
        if (!fish.isEmpty() || !Cleaning.isFish(stack)) return false;
        fish = stack.split(1);
        strokes = 0;
        needed = Cleaning.strokes(Cleaning.kg(fish));
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.SALMON_FLOP, SoundSource.BLOCKS, 0.6f, 1f);
        }
        changed();
        return true;
    }

    /** Takes the fish back off the table, uncut. */
    public ItemStack takeFish() {
        ItemStack out = fish;
        fish = ItemStack.EMPTY;
        strokes = needed = 0;
        changed();
        return out;
    }

    /** Takes everything waiting in the outputs. */
    public List<ItemStack> takeOutputs() {
        List<ItemStack> out = outputs.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
        outputs.replaceAll(s -> ItemStack.EMPTY);
        changed();
        return out;
    }

    public boolean hasOutputs() {
        return !outputsEmpty();
    }

    /** One knife stroke by {@code cutter}; on the last the fish comes apart. False if there is nothing to cut. */
    public boolean stroke(Player cutter) {
        if (fish.isEmpty() || !(level instanceof ServerLevel server)) return false;
        strokes++;
        server.playSound(null, worldPosition, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.7f,
                1.1f + server.random.nextFloat() * 0.3f);
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, fish), worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 6, 0.2, 0.05, 0.2, 0.05);
        if (strokes >= needed) finish(cutter);
        changed();
        return true;
    }

    private void finish(Player cutter) {
        List<ItemStack> parts = Cleaning.yields(fish, Cleaning.kg(fish), level.random);
        fish = ItemStack.EMPTY;
        strokes = needed = 0;
        boolean byHand = cutter != null && !(cutter instanceof FakePlayer);
        level.playSound(null, worldPosition, SoundEvents.COD_FLOP, SoundSource.BLOCKS, 0.8f, 0.8f);
        for (ItemStack part : parts) {
            if (byHand) {
                if (!cutter.getInventory().add(part)) cutter.drop(part, false);
            } else {
                ItemStack rest = store(part);
                if (!rest.isEmpty()) Block.popResourceFromFace(level, worldPosition, Direction.UP, rest);
            }
        }
    }

    private ItemStack store(ItemStack part) {
        for (int i = 0; i < OUTPUT_SLOTS && !part.isEmpty(); i++) {
            ItemStack slot = outputs.get(i);
            if (slot.isEmpty()) {
                outputs.set(i, part.copy());
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(slot, part)) {
                int moved = Math.min(part.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                part.shrink(moved);
            }
        }
        return part;
    }

    /** Spills the fish and the outputs when the table is broken. */
    public void dropContents() {
        if (level == null) return;
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), fish);
        Containers.dropContents(level, worldPosition, outputs);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!fish.isEmpty()) tag.put("fish", fish.save(registries));
        tag.putInt("strokes", strokes);
        tag.putInt("needed", needed);
        ContainerHelper.saveAllItems(tag, outputs, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fish = tag.contains("fish") ? ItemStack.parseOptional(registries, tag.getCompound("fish")) : ItemStack.EMPTY;
        strokes = tag.getInt("strokes");
        needed = tag.getInt("needed");
        outputs.replaceAll(s -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, outputs, registries);
    }

    /** The client needs the fish and the cut progress; the outputs stay on the server. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!fish.isEmpty()) tag.put("fish", fish.save(registries));
        // Always something in the tag: an empty one isn't applied, and a removed fish would stay drawn.
        tag.putInt("strokes", strokes);
        tag.putInt("needed", needed);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Slot 0 takes a fish (one, while the table is clear); slots 1 to 9 give the parts. */
    private final class Items implements IItemHandler {
        @Override
        public int getSlots() {
            return 1 + OUTPUT_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? fish : outputs.get(slot - 1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !fish.isEmpty() || !outputsEmpty() || !Cleaning.isFish(stack)) return stack;
            ItemStack rest = stack.copy();
            if (simulate) {
                rest.shrink(1);
                return rest;
            }
            place(rest);
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 0 || amount <= 0) return ItemStack.EMPTY;
            ItemStack in = outputs.get(slot - 1);
            if (in.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = in.copyWithCount(Math.min(amount, in.getCount()));
            if (!simulate) {
                in.shrink(out.getCount());
                changed();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && Cleaning.isFish(stack);
        }
    }
}
