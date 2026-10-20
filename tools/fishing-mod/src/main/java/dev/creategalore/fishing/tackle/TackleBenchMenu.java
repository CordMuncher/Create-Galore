package dev.creategalore.fishing.tackle;

import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Tackle Bench screen's logic. Slot 0 holds the rod; slots 1-6 are its reel, line, leader, hook, terminal
 * (float, sinker or lure) and bait. The part slots are a live view of the rod: putting a part in fits it, taking
 * one out removes it, so nothing can be lost or duplicated, and closing the screen just hands back the rod.
 *
 * <p>Menu buttons (sent with vanilla's container button packet): 0/1 drag down/up, 2/3 float depth down/up.
 */
public class TackleBenchMenu extends AbstractContainerMenu {
    public static final int ROD = 0;
    public static final int FIRST_PART = 1;
    public static final int PARTS = RodParts.Slot.values().length;
    public static final int SIZE = FIRST_PART + PARTS;
    private static final int PLAYER_START = SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;

    public static final float MAX_FLOAT_DEPTH = 8;

    private final ContainerLevelAccess access;
    final BenchContainer bench = new BenchContainer();

    /** Client side. */
    public TackleBenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public TackleBenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(FishingRegistry.TACKLE_BENCH_MENU.get(), id);
        this.access = access;
        addSlot(new RodSlot(bench, ROD, 17, 36));
        for (RodParts.Slot part : RodParts.Slot.values()) {
            addSlot(new PartSlot(bench, part, 48 + part.ordinal() * 20, 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 148 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 206));
    }

    public ItemStack rod() {
        return bench.getItem(ROD);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, FishingRegistry.TACKLE_BENCH.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // The parts are inside the rod, so only the rod goes back.
        access.execute((level, pos) -> {
            ItemStack rod = bench.removeItemNoUpdate(ROD);
            if (!rod.isEmpty()) player.getInventory().placeItemBackInInventory(rod);
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        ItemStack rod = rod();
        RodStats stats = RodStats.of(rod).orElse(null);
        if (stats == null) return false;
        switch (id) {
            case 0, 1 -> {
                if (stats.reel().isEmpty()) return false;
                int step = Math.max(0, Math.min(stats.dragSteps(), stats.dragStep() + (id == 0 ? -1 : 1)));
                rod.set(FishingRegistry.DRAG_STEP.get(), step);
            }
            case 2, 3 -> {
                float depth = rod.getOrDefault(FishingRegistry.FLOAT_DEPTH.get(), 1.5f) + (id == 2 ? -0.5f : 0.5f);
                rod.set(FishingRegistry.FLOAT_DEPTH.get(), Math.max(0.5f, Math.min(MAX_FLOAT_DEPTH, depth)));
            }
            default -> {
                return false;
            }
        }
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.6f));
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof RodItem) {
            if (!moveItemStackTo(stack, ROD, ROD + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, FIRST_PART, SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    /** Which bench slot a part belongs in, or null. */
    static RodParts.Slot slotFor(ItemStack stack) {
        return TackleManager.of(stack).map(p -> switch (p.kind()) {
            case REEL -> RodParts.Slot.REEL;
            case LINE -> RodParts.Slot.LINE;
            case LEADER -> RodParts.Slot.LEADER;
            case HOOK -> RodParts.Slot.HOOK;
            case FLOAT, SINKER, LURE -> RodParts.Slot.TERMINAL;
            case BAIT -> RodParts.Slot.BAIT;
            case ROD -> null;
        }).orElse(null);
    }

    /**
     * Slot 0 is the rod; 1-6 mirror its parts. The part stacks are held here while the screen is open (vanilla
     * merges stacks by growing the stack a slot returns) and written back to the rod on every change.
     */
    final class BenchContainer implements Container {
        private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

        private void loadParts() {
            RodParts parts = RodItem.parts(items.get(ROD));
            for (RodParts.Slot s : RodParts.Slot.values()) {
                items.set(FIRST_PART + s.ordinal(), items.get(ROD).isEmpty() ? ItemStack.EMPTY : parts.get(s));
            }
        }

        private void writeBack() {
            ItemStack rod = items.get(ROD);
            if (rod.isEmpty()) return;
            RodParts parts = RodParts.EMPTY;
            for (RodParts.Slot s : RodParts.Slot.values()) parts = parts.with(s, items.get(FIRST_PART + s.ordinal()));
            RodItem.setParts(rod, parts);
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            return items.get(ROD).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack out = ContainerHelper.removeItem(items, slot, amount);
            if (slot == ROD) {
                for (int i = FIRST_PART; i < SIZE; i++) items.set(i, ItemStack.EMPTY);
            } else {
                writeBack();
            }
            slotsChanged(this);
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack out = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            if (slot == ROD) for (int i = FIRST_PART; i < SIZE; i++) items.set(i, ItemStack.EMPTY);
            return out;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            if (slot == ROD) loadParts();
            else writeBack();
            slotsChanged(this);
        }

        @Override
        public void setChanged() {
            writeBack();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            items.clear();
        }
    }

    static final class RodSlot extends Slot {
        RodSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof RodItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** A part slot; only usable with a rod in the bench, and the reel slot not on a pole. */
    final class PartSlot extends Slot {
        final RodParts.Slot part;

        PartSlot(Container container, RodParts.Slot part, int x, int y) {
            super(container, FIRST_PART + part.ordinal(), x, y);
            this.part = part;
        }

        private boolean rodTakes() {
            return RodStats.of(rod()).map(s -> part != RodParts.Slot.REEL || !s.isPole()).orElse(false);
        }

        @Override
        public boolean isActive() {
            return rodTakes();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return rodTakes() && slotFor(stack) == part;
        }

        @Override
        public int getMaxStackSize() {
            return part == RodParts.Slot.BAIT ? RodItem.BAIT_CAPACITY : 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(getMaxStackSize(), stack.getMaxStackSize());
        }
    }
}
