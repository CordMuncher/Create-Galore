package dev.creategalore.fishing.client;

import dev.creategalore.fishing.FishingRegistry;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import dev.creategalore.fishing.tackle.TackleBenchMenu;
import dev.creategalore.fishing.tackle.TacklePart;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Tackle Bench screen: the rod on the left, a slot per part along the top, and the rod's ratings, drag and
 * float depth with their buttons, and any warnings (the weak link, a lure too heavy for the blank) below.
 */
public class TackleBenchScreen extends AbstractContainerScreen<TackleBenchMenu> {
    private static final String P = "screen.create_galore_fishing.bench.";
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0x404040;

    private final List<Button> dragButtons = new ArrayList<>();
    private final List<Button> depthButtons = new ArrayList<>();

    public TackleBenchScreen(TackleBenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = 93;
    }

    @Override
    protected void init() {
        super.init();
        dragButtons.clear();
        depthButtons.clear();
        dragButtons.add(addRenderableWidget(small("-", 0, leftPos + 138, topPos + 64)));
        dragButtons.add(addRenderableWidget(small("+", 1, leftPos + 152, topPos + 64)));
        depthButtons.add(addRenderableWidget(small("-", 2, leftPos + 138, topPos + 76)));
        depthButtons.add(addRenderableWidget(small("+", 3, leftPos + 152, topPos + 76)));
    }

    private Button small(String label, int id, int x, int y) {
        return Button.builder(Component.literal(label), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(x, y, 12, 11).build();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        RodStats stats = RodStats.of(menu.rod()).orElse(null);
        boolean reel = stats != null && stats.reel().isPresent();
        boolean bobber = stats != null && stats.terminal().map(t -> t.kind() == TacklePart.Kind.FLOAT).orElse(false);
        dragButtons.forEach(b -> b.visible = reel);
        depthButtons.forEach(b -> b.visible = bobber);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        bevel(g, x, y, imageWidth, imageHeight, PANEL);
        // A larger frame marks the rod slot.
        frame(g, x + 12, y + 31, 26, 26);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) frame(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
        // Stats area.
        g.fill(x + 46, y + 38, x + 168, y + 89, 0xFFB4B4B4);
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

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        ItemStack rod = menu.rod();
        RodStats stats = RodStats.of(rod).orElse(null);
        if (stats == null) {
            List<FormattedCharSequence> hint = font.split(Component.translatable(P + "empty"), (int) ((168 - 48) / 0.75f));
            for (int i = 0; i < hint.size(); i++) {
                g.pose().pushPose();
                g.pose().translate(48, 42 + i * 8, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.drawString(font, hint.get(i), 0, 0, TEXT, false);
                g.pose().popPose();
            }
            return;
        }
        int line = 0;
        if (stats.canCast()) {
            small(g, Component.translatable(P + "rated", RodStats.kg(stats.ratedLoad()), RodStats.kg(stats.ratedFishKg())), 48, 41 + line++ * 8, TEXT);
            small(g, Component.translatable(P + "cast", Math.round(stats.maxCast()), stats.lineMeters()), 48, 41 + line++ * 8, TEXT);
        } else {
            for (Component c : stats.missing()) {
                if (line < 3) small(g, c, 48, 41 + line++ * 8, 0xB02020);
            }
        }
        if (stats.reel().isPresent()) {
            small(g, Component.translatable(P + "drag", RodStats.kg(stats.drag()), RodStats.kg(stats.reel().get().maxDrag())), 48, 66, TEXT);
        }
        if (stats.terminal().map(t -> t.kind() == TacklePart.Kind.FLOAT).orElse(false)) {
            float depth = rod.getOrDefault(FishingRegistry.FLOAT_DEPTH.get(), 1.5f);
            small(g, Component.translatable(P + "depth", String.format(Locale.ROOT, "%.1f", depth)), 48, 78, TEXT);
        }
        List<Component> warnings = stats.warnings();
        if (!warnings.isEmpty()) {
            Component first = warnings.get(0);
            List<FormattedCharSequence> wrapped = font.split(first, (int) ((168 - 48) / 0.75f));
            if (!wrapped.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(48, 57, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.drawString(font, wrapped.get(0), 0, 0, 0xA05A00, false);
                g.pose().popPose();
            }
        }
    }

    private void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // Name empty part slots and show the full warning list over the stats area.
        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            int index = hoveredSlot.index;
            if (index == TackleBenchMenu.ROD) {
                g.renderTooltip(font, Component.translatable(P + "slot.rod"), mouseX, mouseY);
            } else if (index < TackleBenchMenu.SIZE) {
                RodParts.Slot part = RodParts.Slot.values()[index - TackleBenchMenu.FIRST_PART];
                g.renderTooltip(font, Component.translatable(P + "slot." + part.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
            }
        }
        if (mouseX >= leftPos + 46 && mouseX < leftPos + 168 && mouseY >= topPos + 38 && mouseY < topPos + 89) {
            RodStats.of(menu.rod()).ifPresent(stats -> {
                List<Component> lines = new ArrayList<>(stats.warnings());
                if (!lines.isEmpty()) {
                    lines.replaceAll(c -> c.copy().withStyle(ChatFormatting.GOLD));
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            });
        }
    }
}
