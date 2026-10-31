package dev.creategalore.fishing.machine;

import dev.creategalore.fishing.FishingRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A Geared or Fuel Reel Drive's window (sneak and use with an empty hand): its bait slot, the Fuel drive's fuel slot,
 * and the drive's switches. Items go in and out at any time; the drive works on what is there.
 *
 * <p>Menu buttons (sent with vanilla's container button packet): 0 switches the drive on or off, 1 turns autocast on
 * or off, 2 casts once now (with autocast off, the only cast it makes), 3 picks the next mode, 4/5 the cast distance down/up a block, 6/7 down/up five,
 * 8/9 the top gear down/up: the same settings as the value boxes on the back, for when something covers them; 10
 * turns chat reports of lost fish on or off. The Basic drive's window has only the on/off switch and the reports.
 */
public class DriveMenu extends AbstractContainerMenu {
    public static final int BUTTON_POWER = 0;
    public static final int BUTTON_AUTOCAST = 1;
    public static final int BUTTON_CAST = 2;
    public static final int BUTTON_MODE = 3;
    public static final int BUTTON_DISTANCE_DOWN = 4;
    public static final int BUTTON_DISTANCE_UP = 5;
    public static final int BUTTON_DISTANCE_DOWN_5 = 6;
    public static final int BUTTON_DISTANCE_UP_5 = 7;
    public static final int BUTTON_GEAR_DOWN = 8;
    public static final int BUTTON_GEAR_UP = 9;
    public static final int BUTTON_REPORT = 10;

    /**
     * Synced values: switched on, autocast, fuel left (seconds), tank (mB), line out, mode, cast distance, top gear,
     * lost fish reported, something to cast (a rod with a reel, and a lure or bait).
     */
    public static final int DATA_ON = 0;
    public static final int DATA_AUTOCAST = 1;
    public static final int DATA_FUEL_SECONDS = 2;
    public static final int DATA_TANK_MB = 3;
    public static final int DATA_LINE_OUT = 4;
    public static final int DATA_MODE = 5;
    public static final int DATA_DISTANCE = 6;
    public static final int DATA_TOP_GEAR = 7;
    public static final int DATA_REPORT = 8;
    public static final int DATA_CAN_CAST = 9;
    private static final int DATA_SIZE = 10;

    private final DriveMount drive;
    private final ReelDrive.Kind kind;
    private final ContainerData data;
    private final int driveSlots;

    /** Client side: the slots are stand-ins that the server fills. */
    public DriveMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, ReelDrive.Kind.values()[buf.readByte()], new ItemStackHandler(1), new ItemStackHandler(1),
                new SimpleContainerData(DATA_SIZE));
    }

    public DriveMenu(int id, Inventory inventory, DriveMount drive) {
        this(id, inventory, drive, drive.drive().kind(), drive.drive().bait(),
                drive instanceof FuelReelDriveBlockEntity f ? f.fuelSlot() : new ItemStackHandler(1), new ServerData(drive));
    }

    private DriveMenu(int id, Inventory inventory, DriveMount drive, ReelDrive.Kind kind, IItemHandler bait, IItemHandler fuelSlot,
                      ContainerData data) {
        super(FishingRegistry.DRIVE_MENU.get(), id);
        this.drive = drive;
        this.kind = kind;
        this.data = data;
        if (kind.geared()) addSlot(new SlotItemHandler(bait, 0, 16, 26));
        if (kind == ReelDrive.Kind.FUEL) {
            addSlot(new SlotItemHandler(fuelSlot, 0, 16, 73));
        }
        driveSlots = slots.size();
        int top = inventoryY(kind);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, top + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, top + 58));
        addDataSlots(data);
    }

    /** Where the player's inventory starts: below the full control deck, or the Basic drive's one row. */
    public static int inventoryY(ReelDrive.Kind kind) {
        return kind.geared() ? 124 : 62;
    }

    public ReelDrive.Kind kind() {
        return kind;
    }

    public int data(int index) {
        return data.get(index);
    }

    @Override
    public boolean stillValid(Player player) {
        return drive == null || (!drive.blockEntity().isRemoved() && Container.stillValidBlockEntity(drive.blockEntity(), player));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (drive == null) return false;
        ReelDrive reel = drive.drive();
        DriveSlots.Settings settings = drive.settings();
        if (id == BUTTON_POWER) reel.setOn(!reel.on());
        else if (id == BUTTON_REPORT) reel.setReportLosses(!reel.reportLosses());
        if (id == BUTTON_POWER || id == BUTTON_REPORT) return true;
        // The rest are a geared drive's.
        if (settings == null) return false;
        switch (id) {
            case BUTTON_AUTOCAST -> reel.setAutocast(!reel.autocast());
            case BUTTON_CAST -> {
                if (!reel.on() || drive.mounted().hook() != null) return false;
                reel.castOnce();
            }
            case BUTTON_MODE -> settings.nextMode();
            case BUTTON_DISTANCE_DOWN -> settings.stepDistance(-1);
            case BUTTON_DISTANCE_UP -> settings.stepDistance(1);
            case BUTTON_DISTANCE_DOWN_5 -> settings.stepDistance(-5);
            case BUTTON_DISTANCE_UP_5 -> settings.stepDistance(5);
            case BUTTON_GEAR_DOWN -> settings.stepTopGear(-1);
            case BUTTON_GEAR_UP -> settings.stepTopGear(1);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < driveSlots) {
            if (!moveItemStackTo(stack, driveSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, driveSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    /** The drive's state, read live on the server and sent to the window. */
    private record ServerData(DriveMount drive) implements ContainerData {
        @Override
        public int get(int index) {
            ReelDrive reel = drive.drive();
            return switch (index) {
                case DATA_ON -> reel.on() ? 1 : 0;
                case DATA_AUTOCAST -> reel.autocast() ? 1 : 0;
                // Sent as a short.
                case DATA_FUEL_SECONDS -> drive instanceof FuelReelDriveBlockEntity f ? Math.min(Short.MAX_VALUE, f.fuelTicks() / 20) : 0;
                case DATA_TANK_MB -> drive instanceof FuelReelDriveBlockEntity f ? Math.min(Short.MAX_VALUE, f.tankMb()) : 0;
                case DATA_LINE_OUT -> drive.mounted().hook() != null ? 1 : 0;
                case DATA_MODE -> drive.settings() == null ? 0 : drive.settings().mode().getValue();
                case DATA_DISTANCE -> drive.settings() == null ? 0 : drive.settings().distance().getValue();
                case DATA_TOP_GEAR -> drive.settings() == null ? 0 : drive.settings().topGear().getValue();
                case DATA_REPORT -> reel.reportLosses() ? 1 : 0;
                case DATA_CAN_CAST -> reel.castState().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    }
}
