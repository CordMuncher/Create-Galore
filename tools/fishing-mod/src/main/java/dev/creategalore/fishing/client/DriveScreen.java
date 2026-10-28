package dev.creategalore.fishing.client;

import dev.creategalore.fishing.machine.DriveMenu;
import dev.creategalore.fishing.machine.DriveMode;
import dev.creategalore.fishing.machine.ReelDrive;
import dev.creategalore.fishing.sim.Gearbox;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A Reel Drive's window: the on/off and autocast switches and the Cast button along the top, the bait slot (and the
 * Fuel drive's fuel slot) on the left, and on the right the settings the value boxes on the back also set (mode,
 * cast distance, top gear), with the fuel left and the lost-fish reports switch under them. The Basic drive shows
 * only the on/off and reports switches.
 */
public class DriveScreen extends AbstractContainerScreen<DriveMenu> {
    private static final String P = "screen.create_galore_fishing.drive.";
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0x404040;

    private Button power;
    private Button autocast;
    private Button cast;
    private Button mode;
    private Button report;
    private boolean geared;

    public DriveScreen(DriveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 206;
        inventoryLabelY = 113;
        geared = menu.kind().geared();
    }

    @Override
    protected void init() {
        super.init();
        power = addRenderableWidget(button(DriveMenu.BUTTON_POWER, 8, 18, 40));
        autocast = addRenderableWidget(button(DriveMenu.BUTTON_AUTOCAST, 50, 18, 84));
        cast = addRenderableWidget(button(DriveMenu.BUTTON_CAST, 136, 18, 32));
        cast.setMessage(Component.translatable(P + "cast"));
        mode = addRenderableWidget(button(DriveMenu.BUTTON_MODE, 40, 42, 128));
        report = addRenderableWidget(button(DriveMenu.BUTTON_REPORT, 94, 92, 74));
        report.setTooltip(Tooltip.create(Component.translatable(P + "report_hint")));
        if (geared) {
            addRenderableWidget(stepper("-", DriveMenu.BUTTON_DISTANCE_DOWN, DriveMenu.BUTTON_DISTANCE_DOWN_5, 141, 62));
            addRenderableWidget(stepper("+", DriveMenu.BUTTON_DISTANCE_UP, DriveMenu.BUTTON_DISTANCE_UP_5, 155, 62));
            addRenderableWidget(stepper("-", DriveMenu.BUTTON_GEAR_DOWN, DriveMenu.BUTTON_GEAR_DOWN, 141, 78));
            addRenderableWidget(stepper("+", DriveMenu.BUTTON_GEAR_UP, DriveMenu.BUTTON_GEAR_UP, 155, 78));
        } else {
            autocast.visible = false;
            mode.visible = false;
            report.setPosition(leftPos + 50, topPos + 18);
        }
        update();
    }

    private Button button(int id, int x, int y, int width) {
        return Button.builder(Component.empty(), b -> press(id)).bounds(leftPos + x, topPos + y, width, 16).build();
    }

    /** A small - or + that sends {@code shifted} instead of {@code id} while Shift is held. */
    private Button stepper(String label, int id, int shifted, int x, int y) {
        Button b = Button.builder(Component.literal(label), btn -> press(Screen.hasShiftDown() ? shifted : id))
                .bounds(leftPos + x, topPos + y, 13, 12).build();
        if (shifted != id) b.setTooltip(Tooltip.create(Component.translatable(P + "step_hint")));
        return b;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        update();
    }

    private void update() {
        boolean on = menu.data(DriveMenu.DATA_ON) != 0;
        boolean auto = menu.data(DriveMenu.DATA_AUTOCAST) != 0;
        power.setMessage(Component.translatable(P + (on ? "on" : "off")));
        autocast.setMessage(Component.translatable(P + (auto ? "autocast_on" : "autocast_off")));
        report.setMessage(Component.translatable(P + (menu.data(DriveMenu.DATA_REPORT) != 0 ? "report_on" : "report_off")));
        // Casting by hand only makes sense with autocast off and the line in.
        cast.visible = geared && !auto;
        cast.active = on && menu.data(DriveMenu.DATA_LINE_OUT) == 0;
        DriveMode[] modes = DriveMode.values();
        DriveMode current = modes[Mth.clamp(menu.data(DriveMenu.DATA_MODE), 0, modes.length - 1)];
        mode.setMessage(Component.translatable(P + "mode", Component.translatable(current.getTranslationKey())));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        bevel(g, x, y, imageWidth, imageHeight, PANEL);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) frame(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if (!geared) return;
        Slot bait = menu.slots.get(0);
        centred(g, Component.translatable(P + "bait"), bait.x + 8, bait.y - 8);
        small(g, Component.translatable(P + "distance", menu.data(DriveMenu.DATA_DISTANCE)), 40, 65, TEXT);
        int top = Mth.clamp(menu.data(DriveMenu.DATA_TOP_GEAR), 1, Gearbox.GEARS.length);
        small(g, Component.translatable(P + "top_gear", String.format(Locale.ROOT, "%.2fx", Gearbox.GEARS[top - 1])), 40, 81, TEXT);
        if (menu.kind() == ReelDrive.Kind.FUEL) {
            Slot fuel = menu.slots.get(1);
            centred(g, Component.translatable(P + "fuel"), fuel.x + 8, fuel.y - 8);
            int seconds = menu.data(DriveMenu.DATA_FUEL_SECONDS);
            Component left = Component.translatable(P + "fuel_left", seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60));
            small(g, left, 40, 94, seconds > 0 ? TEXT : 0xB02020);
            int tank = menu.data(DriveMenu.DATA_TANK_MB);
            if (tank > 0) small(g, Component.translatable(P + "tank", tank), 40, 102, TEXT);
        }
    }

    private void centred(GuiGraphics g, Component text, int cx, int y) {
        g.pose().pushPose();
        g.pose().translate(cx - font.width(text) * 0.375f, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, 0, 0, TEXT, false);
        g.pose().popPose();
    }

    private void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w - 1, y + 1, LIGHT);
        g.fill(x, y, x + 1, y + h - 1, LIGHT);
        g.fill(x + 1, y + h - 1, x + w, y + h, DARK);
        g.fill(x + w - 1, y + 1, x + w, y + h, DARK);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT);
        g.fill(x, y, x + w - 1, y + 1, DARK);
        g.fill(x, y, x + 1, y + h - 1, DARK);
        g.fill(x + 1, y + h - 1, x + w, y + h, LIGHT);
        g.fill(x + w - 1, y + 1, x + w, y + h, LIGHT);
    }
}
