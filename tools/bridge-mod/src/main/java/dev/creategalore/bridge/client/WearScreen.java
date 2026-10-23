package dev.creategalore.bridge.client;

import dev.creategalore.bridge.wear.WearGroups;
import dev.creategalore.bridge.wear.WearLook;
import dev.creategalore.bridge.wear.WearMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The wear screen: the player with their armor on the left, every accessory group beside them head to feet, the
 * inventory below. Empty wear slots show what goes there and how many of the group are worn; a worn item has a small
 * eye in its corner (on hover, or always when hidden) that shows or hides it on the player. A pair of gloves greys out
 * the second hand.
 */
final class WearScreen extends AbstractContainerScreen<WearMenu> {
    private static final int BG = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, EDGE = 0xFF000000;
    private static final int SLOT_DARK = 0xFF373737, SLOT_LIGHT = 0xFFFFFFFF, SLOT_FILL = 0xFF8B8B8B;
    private static final int LABEL = 0x404040;

    WearScreen(WearMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = WearLook.WIDTH;
        imageHeight = WearLook.HEIGHT;
        inventoryLabelX = WearLook.INV_X;
        inventoryLabelY = WearLook.INV_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WearClient.Button(this, WearLook.MODEL_X1 - 11, WearLook.MODEL_Y0 + 1, s -> true, () -> {
            minecraft.player.closeContainer();
            minecraft.setScreen(new InventoryScreen(minecraft.player));
        }, Component.translatable("create_galore_bridge.wear.back")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // A panel in the vanilla style.
        g.fill(x + 1, y, x + imageWidth - 1, y + imageHeight, EDGE);
        g.fill(x, y + 1, x + imageWidth, y + imageHeight - 1, EDGE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG);
        g.fill(x + 1, y + 1, x + imageWidth - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + imageHeight - 2, LIGHT);
        g.fill(x + 3, y + imageHeight - 3, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
        g.fill(x + imageWidth - 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
        // The player.
        g.fill(x + WearLook.MODEL_X0 - 1, y + WearLook.MODEL_Y0 - 1, x + WearLook.MODEL_X1 + 1, y + WearLook.MODEL_Y1 + 1, SLOT_DARK);
        g.fill(x + WearLook.MODEL_X0, y + WearLook.MODEL_Y0, x + WearLook.MODEL_X1 + 1, y + WearLook.MODEL_Y1 + 1, SLOT_LIGHT);
        g.fill(x + WearLook.MODEL_X0, y + WearLook.MODEL_Y0, x + WearLook.MODEL_X1, y + WearLook.MODEL_Y1, EDGE);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + WearLook.MODEL_X0, y + WearLook.MODEL_Y0, x + WearLook.MODEL_X1,
                y + WearLook.MODEL_Y1, 40, 0.0625F, mouseX, mouseY, minecraft.player);
        for (Slot slot : menu.slots) frame(g, x + slot.x - 1, y + slot.y - 1);
    }

    private static void frame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_FILL);
        g.fill(x, y, x + 17, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + 17, SLOT_DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, SLOT_LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, SLOT_LIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
        List<String> shown = new ArrayList<>();
        for (WearGroups.Group group : menu.layout.groups) shown.add(group.id());
        for (WearLook.Label label : WearLook.LABELS) {
            if (WearLook.LABEL_GROUPS.get(label.key()).stream().noneMatch(shown::contains)) continue;
            g.drawString(font, Component.translatable("create_galore_bridge.wear.label." + label.key()), label.x() - 1, label.y(), LABEL, false);
        }
        // Covered hands, and the show/hide eyes.
        for (int i = 0; i < menu.wearEnd; i++) {
            Slot slot = menu.slots.get(i);
            if (menu.covered(i)) {
                ItemStack by = menu.coveredBy(i);
                if (!by.isEmpty()) g.renderFakeItem(by, slot.x, slot.y);
                g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 300, 0xB08B8B8B);
                continue;
            }
            if (!slot.hasItem()) continue;
            boolean hidden = menu.hidden(i);
            boolean hover = isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
            if (hidden || hover) eye(g, slot.x + 11, slot.y - 1, hidden, onEye(slot, mouseX, mouseY));
        }
    }

    /** A 6 by 5 eye: open when the item is shown on the player, struck through when hidden. */
    private static void eye(GuiGraphics g, int x, int y, boolean hidden, boolean hot) {
        int z = 310;
        int line = hot ? 0xFFFFFFA0 : 0xFFFFFFFF;
        g.fill(x - 1, y - 1, x + 7, y + 6, z, 0xC0000000);
        g.fill(x + 1, y, x + 5, y + 1, z, line);
        g.fill(x, y + 1, x + 1, y + 4, z, line);
        g.fill(x + 5, y + 1, x + 6, y + 4, z, line);
        g.fill(x + 1, y + 4, x + 5, y + 5, z, line);
        g.fill(x + 2, y + 2, x + 4, y + 3, z, hidden ? 0xFF808080 : 0xFF40A0FF);
        if (hidden) {
            for (int k = 0; k < 6; k++) g.fill(x + k, y + 4 - k * 4 / 5, x + k + 1, y + 5 - k * 4 / 5, z, 0xFFFF5050);
        }
    }

    private boolean onEye(Slot slot, double mouseX, double mouseY) {
        double mx = mouseX - leftPos, my = mouseY - topPos;
        return mx >= slot.x + 10 && mx < slot.x + 17 && my >= slot.y - 2 && my < slot.y + 5;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < menu.wearEnd; i++) {
                Slot slot = menu.slots.get(i);
                if (slot.hasItem() && !menu.covered(i) && onEye(slot, mouseX, mouseY) && menu.getCarried().isEmpty()) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot instanceof WearMenu.WearSlot slot) {
            if (slot.hasItem() && onEye(slot, mouseX, mouseY)) {
                g.renderTooltip(font, Component.translatable(menu.hidden(slot.wear) ? "create_galore_bridge.wear.tip.hidden"
                        : "create_galore_bridge.wear.tip.shown"), mouseX, mouseY);
                return;
            }
            if (!slot.hasItem()) {
                g.renderComponentTooltip(font, groupTip(slot), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    private List<Component> groupTip(WearMenu.WearSlot slot) {
        WearGroups.Group group = slot.group();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(group.nameKey()));
        if (menu.covered(slot.wear)) {
            lines.add(Component.translatable("create_galore_bridge.wear.tip.covered", menu.coveredBy(slot.wear).getHoverName())
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        int used = 0;
        int first = menu.layout.first(group);
        for (int k = 0; k < group.cap(); k++) {
            ItemStack s = menu.slots.get(first + k).getItem();
            if (!s.isEmpty()) used += WearGroups.weight(s, group);
        }
        lines.add(Component.translatable(group.nameKey() + ".what").withStyle(ChatFormatting.GRAY));
        if (group.cap() > 1) {
            lines.add(Component.translatable("create_galore_bridge.wear.tip.count", used, group.cap()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(group.same() == 1 ? "create_galore_bridge.wear.tip.no_two" : "create_galore_bridge.wear.tip.same",
                    group.same()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (group.id().equals("hands")) lines.add(Component.translatable("create_galore_bridge.wear.tip.pair").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }
}
