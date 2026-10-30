package dev.creategalore.fishing.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.machine.DriveMenu;
import dev.creategalore.fishing.machine.DriveMode;
import dev.creategalore.fishing.machine.ReelDrive;
import dev.creategalore.fishing.sim.Gearbox;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A Reel Drive's window, drawn like Create's machine screens: a stone panel with a dark control deck sunk into it.
 * On the deck, the bait slot (and the Fuel drive's fuel slot with its fuel left) sit in raised stone plates with a
 * status window between them, and the
 * controls are stone push buttons with etched glyphs: power, autocast and cast now along the top, the reports switch
 * at the right, the two-way mode switch under them, and cast distance and top gear as readout windows with - and +
 * either side, the same settings as the value boxes on the back. A switch that is on stays pressed in. The Basic drive
 * shows only power and reports. Sprites are drawn by {@code art/make_gui.py}.
 */
public class DriveScreen extends AbstractContainerScreen<DriveMenu> {
    private static final String P = "screen.create_galore_fishing.drive.";
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SUNK = 0xFF373737;
    private static final int TEXT = 0x404040;
    private static final int READOUT = 0xCFE0E6;
    private static final int READOUT_LOW = 0xE09A8A;

    private static final ResourceLocation DECK = sprite("deck");
    private static final ResourceLocation PLATE = sprite("plate");
    private static final ResourceLocation WINDOW = sprite("window");
    private static final ResourceLocation SLOT = sprite("slot");

    /** The deck, inside the panel: geared drives get room for the slots and settings, the Basic drive one row. */
    private static final int DECK_X = 7;
    private static final int DECK_Y = 17;
    private static final int DECK_W = 162;

    private final boolean geared;
    private final boolean fuel;
    private final int deckH;
    /** Etched labels on the deck and plates, with what they name, for tooltips. */
    private final List<Hint> hints = new ArrayList<>();

    /** Tooltip lines from a hovered button, drawn after everything else this frame. */
    private List<Component> pendingTooltip = List.of();

    private record Hint(int x, int y, int w, int h, Supplier<List<Component>> text) {
    }

    public DriveScreen(DriveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        geared = menu.kind().geared();
        fuel = menu.kind() == ReelDrive.Kind.FUEL;
        deckH = geared ? 92 : 30;
        imageWidth = 176;
        imageHeight = DriveMenu.inventoryY(menu.kind()) + 82;
        inventoryLabelY = DriveMenu.inventoryY(menu.kind()) - 11;
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateGaloreFishing.ID, "drive/" + name);
    }

    private boolean on() {
        return menu.data(DriveMenu.DATA_ON) != 0;
    }

    private boolean autocast() {
        return menu.data(DriveMenu.DATA_AUTOCAST) != 0;
    }

    private DriveMode mode() {
        DriveMode[] modes = DriveMode.values();
        return modes[Mth.clamp(menu.data(DriveMenu.DATA_MODE), 0, modes.length - 1)];
    }

    @Override
    protected void init() {
        super.init();
        hints.clear();
        addRenderableWidget(new GlyphButton(57, 21, 18, 18, "power", DriveMenu.BUTTON_POWER, this::on,
                () -> List.of(Component.translatable(P + (on() ? "on" : "off")), hint("power_hint"))));
        addRenderableWidget(new GlyphButton(147, 21, 18, 18, "report", DriveMenu.BUTTON_REPORT,
                () -> menu.data(DriveMenu.DATA_REPORT) != 0,
                () -> List.of(Component.translatable(P + (menu.data(DriveMenu.DATA_REPORT) != 0 ? "report_on" : "report_off")),
                        hint("report_hint"))));
        if (!geared) return;
        addRenderableWidget(new GlyphButton(77, 21, 18, 18, "autocast", DriveMenu.BUTTON_AUTOCAST, this::autocast,
                () -> List.of(Component.translatable(P + (autocast() ? "autocast_on" : "autocast_off")), hint("autocast_hint"))));
        GlyphButton cast = new GlyphButton(97, 21, 18, 18, "cast", DriveMenu.BUTTON_CAST, () -> false, () -> {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(P + "cast"));
            if (!on()) lines.add(hint("cast_off"));
            else if (menu.data(DriveMenu.DATA_LINE_OUT) != 0) lines.add(hint("cast_out"));
            lines.add(hint("cast_hint"));
            return lines;
        });
        cast.enabled = () -> on() && menu.data(DriveMenu.DATA_LINE_OUT) == 0;
        addRenderableWidget(cast);
        // A two-way switch: the mode in use stays pressed in; pressing the other one moves it.
        for (DriveMode m : DriveMode.values()) {
            int x = 57 + m.ordinal() * 17;
            String glyph = m == DriveMode.WAIT ? "wait" : "retrieve";
            addRenderableWidget(new GlyphButton(x, 45, 18, 18, glyph, -1, () -> mode() == m,
                    () -> List.of(Component.translatable(m.getTranslationKey()), hint("mode_" + m.name().toLowerCase(Locale.ROOT))))
                    .onPress(() -> {
                        if (mode() != m) press(DriveMenu.BUTTON_MODE);
                    }));
        }
        stepper(69, DriveMenu.BUTTON_DISTANCE_DOWN, DriveMenu.BUTTON_DISTANCE_DOWN_5, DriveMenu.BUTTON_DISTANCE_UP,
                DriveMenu.BUTTON_DISTANCE_UP_5);
        stepper(89, DriveMenu.BUTTON_GEAR_DOWN, DriveMenu.BUTTON_GEAR_DOWN, DriveMenu.BUTTON_GEAR_UP, DriveMenu.BUTTON_GEAR_UP);
        hint(57, 70, 12, 12, () -> List.of(Component.translatable(P + "distance_label")));
        hint(57, 90, 12, 12, () -> List.of(Component.translatable(P + "top_gear_label")));
        hint(36, 28, 12, 12, () -> List.of(Component.translatable(P + "bait")));
        hint(11, 51, 42, 14, () -> List.of(Component.translatable(P + "status_label"), hint("status_hint")));
        if (fuel) {
            hint(36, 75, 12, 12, () -> List.of(Component.translatable(P + "fuel")));
            hint(14, 92, 36, 11, () -> {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(P + "fuel_label"));
                int tank = menu.data(DriveMenu.DATA_TANK_MB);
                if (tank > 0) lines.add(Component.translatable(P + "tank", tank).withStyle(ChatFormatting.GRAY));
                return lines;
            });
        }
    }

    private void hint(int x, int y, int w, int h, Supplier<List<Component>> text) {
        hints.add(new Hint(x, y, w, h, text));
    }

    /** What the drive is doing, for the status window. */
    private Component status() {
        String key;
        if (!on()) key = "status_off";
        else if (menu.data(DriveMenu.DATA_LINE_OUT) != 0) key = "status_out";
        else if (menu.data(DriveMenu.DATA_CAN_CAST) == 0) key = "status_empty";
        else key = autocast() ? "status_casting" : "status_ready";
        return Component.translatable(P + key);
    }

    /** The - and + either side of a readout window; with Shift they send the five-step buttons where there are any. */
    private void stepper(int y, int down, int down5, int up, int up5) {
        Supplier<List<Component>> tip = down5 != down ? () -> List.of(hint("step_hint")) : List::of;
        addRenderableWidget(new GlyphButton(71, y, 12, 14, "minus", -1, () -> false, tip)
                .onPress(() -> press(Screen.hasShiftDown() ? down5 : down)));
        addRenderableWidget(new GlyphButton(151, y, 12, 14, "plus", -1, () -> false, tip)
                .onPress(() -> press(Screen.hasShiftDown() ? up5 : up)));
    }

    private static Component hint(String key) {
        return Component.translatable(P + key).withStyle(ChatFormatting.GRAY);
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        for (Hint h : hints) {
            int x = leftPos + h.x;
            int y = topPos + h.y;
            if (mouseX >= x && mouseX < x + h.w && mouseY >= y && mouseY < y + h.h) {
                g.renderComponentTooltip(font, h.text.get(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // The stone panel, outlined like Create's.
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        g.fill(x + 1, y + 1, x + imageWidth - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + imageHeight - 2, LIGHT);
        g.fill(x + 2, y + imageHeight - 3, x + imageWidth - 1, y + imageHeight - 1, DARK);
        g.fill(x + imageWidth - 3, y + 2, x + imageWidth - 1, y + imageHeight - 1, DARK);
        // The deck, sunk into it.
        int dx = x + DECK_X;
        int dy = y + DECK_Y;
        g.fill(dx - 1, dy - 1, dx + DECK_W + 1, dy + deckH + 1, LIGHT);
        g.fill(dx - 1, dy - 1, dx + DECK_W, dy + deckH, SUNK);
        g.blitSprite(DECK, dx, dy, DECK_W, deckH);
        g.fill(dx, dy, dx + DECK_W, dy + 1, 0x60000000);
        g.fill(dx, dy + 1, dx + 1, dy + deckH, 0x60000000);
        if (geared) {
            plate(g, 11, 21, 42, 26);
            etch(g, "glyph_bait", 37, 28);
            g.blitSprite(WINDOW, x + 11, y + 51, 42, 14);
            if (fuel) {
                plate(g, 11, 69, 42, 37);
                etch(g, "glyph_fuel", 37, 75);
                g.blitSprite(WINDOW, x + 14, y + 92, 36, 11);
            }
            etch(g, "etch_distance", 58, 71);
            etch(g, "etch_gear", 58, 91);
            g.blitSprite(WINDOW, x + 85, y + 69, 64, 14);
            g.blitSprite(WINDOW, x + 85, y + 89, 64, 14);
        }
        // Inventory slots, and the drive's own in their plates.
        for (Slot slot : menu.slots) {
            if (slot.isActive()) g.blitSprite(SLOT, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
    }

    private void plate(GuiGraphics g, int px, int py, int w, int h) {
        g.blitSprite(PLATE, leftPos + px, topPos + py, w, h);
    }

    private void etch(GuiGraphics g, String glyph, int gx, int gy) {
        RenderSystem.enableBlend();
        g.blitSprite(sprite(glyph), leftPos + gx, topPos + gy, 11, 11);
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        if (!geared) return;
        // The mode's name beside its switch, shrunk to fit the deck.
        Component name = Component.translatable(mode().getTranslationKey());
        float scale = Math.min(1f, 68f / Math.max(1, font.width(name)));
        g.pose().pushPose();
        g.pose().translate(96, 54 - 3.5f * scale, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, name, 0, 0, READOUT, false);
        g.pose().popPose();
        readout(g, Component.translatable(P + "distance", menu.data(DriveMenu.DATA_DISTANCE)), 85, 69, 64, 14, READOUT);
        int top = Mth.clamp(menu.data(DriveMenu.DATA_TOP_GEAR), 1, Gearbox.GEARS.length);
        readout(g, Component.literal(String.format(Locale.ROOT, "%.2fx", Gearbox.GEARS[top - 1])), 85, 89, 64, 14, READOUT);
        readout(g, status(), 11, 51, 42, 14, on() ? READOUT : READOUT_LOW);
        if (fuel) {
            int seconds = menu.data(DriveMenu.DATA_FUEL_SECONDS);
            Component left = Component.translatable(P + "fuel_left", seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60));
            readout(g, left, 14, 92, 36, 11, seconds > 0 ? READOUT : READOUT_LOW);
        }
    }

    /** Text centred in a readout window, shrunk to fit if it has to be. */
    private void readout(GuiGraphics g, Component text, int x, int y, int w, int h, int color) {
        float scale = Math.min(1f, (w - 4) / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f - 3.5f * scale + 0.5f, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }

    /** A stone push button with an etched glyph; pressed in while {@code down} says so. */
    private class GlyphButton extends AbstractButton {
        private final ResourceLocation glyph;
        private final BooleanSupplier down;
        private final Supplier<List<Component>> tooltip;
        private Runnable action;
        BooleanSupplier enabled = () -> true;

        GlyphButton(int x, int y, int w, int h, String glyph, int id, BooleanSupplier down, Supplier<List<Component>> tooltip) {
            super(leftPos + x, topPos + y, w, h, Component.empty());
            this.glyph = sprite("glyph_" + glyph);
            this.down = down;
            this.tooltip = tooltip;
            this.action = () -> press(id);
        }

        GlyphButton onPress(Runnable action) {
            this.action = action;
            return this;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            active = enabled.getAsBoolean();
            boolean pressed = down.getAsBoolean();
            boolean hover = isHoveredOrFocused() && active;
            String face = !active ? "button_off" : pressed ? (hover ? "button_on_hover" : "button_on") : (hover ? "button_hover" : "button");
            RenderSystem.enableBlend();
            g.blitSprite(sprite(face), getX(), getY(), width, height);
            if (!active) g.setColor(1, 1, 1, 0.4f);
            // The glyph sinks a pixel with the button.
            // Glyphs are 10 pixels across plus their lip, so they centre on even sizes.
            int gx = getX() + (width - 10) / 2;
            int gy = getY() + (height - 10) / 2 + (pressed ? 1 : 0);
            g.blitSprite(glyph, gx, gy, 11, 11);
            g.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            if (isHovered()) pendingTooltip = tooltip.get();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        if (!pendingTooltip.isEmpty()) {
            g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
            pendingTooltip = List.of();
        }
    }
}
