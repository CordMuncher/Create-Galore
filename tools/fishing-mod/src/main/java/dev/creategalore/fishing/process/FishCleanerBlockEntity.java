package dev.creategalore.fishing.process;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The Fish Cleaner's contents and work. One stack waits in its input. A fish too big to use whole (the ones the
 * furnace and smoker refuse, {@link WholeFishIngredient#small}) is cut there, stroke by stroke, as the Cleaning Table
 * cuts it ({@link Cleaning#strokes}, {@link Cleaning#yields}); anything else goes straight on, powered or not. The
 * saw needs rotation: one stroke per {@link #STROKE_WORK} RPM-ticks, so two a second at 32 RPM, and it costs
 * {@link #IMPACT} SU per RPM like Create's saw. A fish is only cut once the parts of the last one have left, so they
 * always fit in the nine output slots.
 *
 * <p>What is ready leaves by the front, one stack a tick: into whatever takes items straight from Create machines (a
 * belt, depot, chute or funnel), else into an inventory, else onto the ground if the front is open. Funnels and
 * hoppers can also pull the outputs. Items come in through any side but the front: Create's belts and chutes, and
 * anything that inserts into an item handler.
 */
public class FishCleanerBlockEntity extends KineticBlockEntity {
    public static final int OUTPUT_SLOTS = 9;
    /** Stress per RPM, as Create's saw. */
    public static final double IMPACT = 4;
    /** Rotation for one stroke, in RPM-ticks: a stroke every 10 ticks at 32 RPM. */
    public static final float STROKE_WORK = 320;
    /** Ticks between two stacks dropped on the ground, so a pile doesn't spray out at once. */
    private static final int DROP_DELAY = 8;

    private ItemStack input = ItemStack.EMPTY;
    private int strokes;
    private int needed;
    private float work;
    private int dropCooldown;
    private final NonNullList<ItemStack> outputs = NonNullList.withSize(OUTPUT_SLOTS, ItemStack.EMPTY);
    private final IItemHandler items = new Items();

    public FishCleanerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** True for a fish the furnace won't take whole, which only a Cleaning Table or this cleaner can use. */
    public static boolean needsCleaning(ItemStack stack) {
        return Cleaning.isFish(stack) && !WholeFishIngredient.small(stack);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // Belts and chutes hand items over through this; the default puts them in the item handler below. Nothing
        // comes in by the front, where the cleaner puts things out (side is the way the item is moving).
        behaviours.add(new DirectBeltInputBehaviour(this).onlyInsertWhen(side -> side != front().getOpposite()));
    }

    public Direction front() {
        return getBlockState().getValue(FishCleanerBlock.HORIZONTAL_FACING);
    }

    public IItemHandler items() {
        return items;
    }

    /** What waits in the input: the fish being cut, or a stack about to pass. */
    public ItemStack input() {
        return input;
    }

    public int strokes() {
        return strokes;
    }

    public int needed() {
        return needed;
    }

    public boolean hasOutputs() {
        return outputs.stream().anyMatch(s -> !s.isEmpty());
    }

    public boolean isEmpty() {
        return input.isEmpty() && !hasOutputs();
    }

    /** Takes out everything inside: the parts and passed items first, then the input, uncut. */
    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>();
        outputs.stream().filter(s -> !s.isEmpty()).forEach(out::add);
        outputs.replaceAll(s -> ItemStack.EMPTY);
        if (!input.isEmpty()) out.add(input);
        setInput(ItemStack.EMPTY);
        return out;
    }

    private void setInput(ItemStack stack) {
        input = stack;
        strokes = 0;
        work = 0;
        needed = needsCleaning(stack) ? Cleaning.strokes(Cleaning.kg(stack)) : 0;
        changed();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (dropCooldown > 0) dropCooldown--;
        eject();
        if (input.isEmpty()) return;
        if (!needsCleaning(input)) {
            ItemStack rest = store(input);
            if (rest.getCount() != input.getCount() || rest.isEmpty()) setInput(rest);
            return;
        }
        // A big fish: cut it once the last one's parts are gone, while the saw turns.
        float rpm = Math.abs(getSpeed());
        if (hasOutputs() || rpm < 0.01f || isOverStressed()) return;
        work += rpm;
        if (work < STROKE_WORK) return;
        work -= STROKE_WORK;
        stroke();
    }

    private void stroke() {
        strokes++;
        ServerLevel server = (ServerLevel) level;
        server.playSound(null, worldPosition, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.6f,
                1.1f + server.random.nextFloat() * 0.3f);
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, input), worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5, 5, 0.15, 0.05, 0.15, 0.05);
        if (strokes < needed) {
            changed();
            return;
        }
        List<ItemStack> parts = Cleaning.yields(input, Cleaning.kg(input), level.random);
        server.playSound(null, worldPosition, SoundEvents.COD_FLOP, SoundSource.BLOCKS, 0.8f, 0.8f);
        for (ItemStack part : parts) {
            ItemStack rest = store(part);
            // The nine slots were empty and no fish gives more than six stacks, but never lose anything.
            if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, rest);
        }
        setInput(ItemStack.EMPTY);
    }

    /** Moves what fits of {@code stack} into the outputs; returns what didn't fit. */
    private ItemStack store(ItemStack stack) {
        ItemStack part = stack.copy();
        for (int i = 0; i < OUTPUT_SLOTS && !part.isEmpty(); i++) {
            ItemStack slot = outputs.get(i);
            if (ItemStack.isSameItemSameComponents(slot, part)) {
                int moved = Math.min(part.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                part.shrink(moved);
            }
        }
        for (int i = 0; i < OUTPUT_SLOTS && !part.isEmpty(); i++) {
            if (outputs.get(i).isEmpty()) {
                outputs.set(i, part);
                part = ItemStack.EMPTY;
            }
        }
        setChanged();
        return part;
    }

    /** Puts the first waiting stack out of the front, as much of it as the front takes. */
    private void eject() {
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack stack = outputs.get(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = push(stack.copy());
            if (rest.getCount() != stack.getCount()) {
                outputs.set(i, rest);
                // The client only knows whether parts are waiting (for the goggles); tell it when they are all gone.
                if (hasOutputs()) setChanged();
                else changed();
            }
            return;
        }
    }

    private ItemStack push(ItemStack stack) {
        Direction front = front();
        BlockPos pos = worldPosition.relative(front);
        DirectBeltInputBehaviour belt = BlockEntityBehaviour.get(level, pos, DirectBeltInputBehaviour.TYPE);
        if (belt != null) return belt.canInsertFromSide(front) ? belt.handleInsertion(stack, front, false) : stack;
        IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, front.getOpposite());
        if (inventory != null) return ItemHandlerHelper.insertItemStacked(inventory, stack, false);
        if (dropCooldown > 0 || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return stack;
        Vec3 at = Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(front.getNormal()).scale(0.75)).subtract(0, 0.25, 0);
        ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, stack);
        drop.setDeltaMovement(Vec3.atLowerCornerOf(front.getNormal()).scale(0.1));
        level.addFreshEntity(drop);
        dropCooldown = DROP_DELAY;
        return ItemStack.EMPTY;
    }

    @Override
    public void destroy() {
        super.destroy();
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), input);
        Containers.dropContents(level, worldPosition, outputs);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        String p = "block.create_galore_fishing.fish_cleaner.";
        if (input.isEmpty()) {
            tooltip.add(Component.literal("    ").append(Component.translatable(p + "empty")).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("    ").append(input.getHoverName()).withStyle(ChatFormatting.WHITE));
            Component state = needed == 0 ? Component.translatable(p + "passing")
                    : hasOutputs() ? Component.translatable(p + "waiting")
                    : Math.abs(getSpeed()) < 0.01f ? Component.translatable(p + "no_power")
                    : Component.translatable(p + "progress", strokes, needed);
            tooltip.add(Component.literal("    ").append(state).withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!input.isEmpty()) tag.put("input", input.save(registries));
        tag.putInt("strokes", strokes);
        tag.putInt("needed", needed);
        tag.putBoolean("parts", hasOutputs());
        if (clientPacket) return;
        tag.putFloat("work", work);
        ContainerHelper.saveAllItems(tag, outputs, registries);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        input = tag.contains("input") ? ItemStack.parseOptional(registries, tag.getCompound("input")) : ItemStack.EMPTY;
        strokes = tag.getInt("strokes");
        needed = tag.getInt("needed");
        if (clientPacket) {
            clientParts = tag.getBoolean("parts");
            return;
        }
        work = tag.getFloat("work");
        outputs.replaceAll(s -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, outputs, registries);
    }

    /** On the client: whether parts are waiting to leave (the outputs themselves stay on the server). */
    private boolean clientParts;

    public boolean clientHasParts() {
        return clientParts;
    }

    /** Slot 0 takes items in (one big fish, or a stack of anything else); slots 1 to 9 give out what is ready. */
    private final class Items implements IItemHandler {
        @Override
        public int getSlots() {
            return 1 + OUTPUT_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? input : outputs.get(slot - 1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) return stack;
            int room;
            if (input.isEmpty()) room = needsCleaning(stack) ? 1 : stack.getMaxStackSize();
            else if (!needsCleaning(input) && ItemStack.isSameItemSameComponents(input, stack)) room = input.getMaxStackSize() - input.getCount();
            else room = 0;
            int moved = Math.min(room, stack.getCount());
            if (moved <= 0) return stack;
            if (!simulate) {
                if (input.isEmpty()) setInput(stack.copyWithCount(moved));
                else {
                    input.grow(moved);
                    changed();
                }
            }
            return stack.copyWithCount(stack.getCount() - moved);
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
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0;
        }
    }
}
