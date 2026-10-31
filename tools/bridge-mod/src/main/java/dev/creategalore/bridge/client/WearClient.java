package dev.creategalore.bridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.creategalore.bridge.CreateGaloreBridge;
import dev.creategalore.bridge.wear.Wear;
import dev.creategalore.bridge.wear.WearMenu;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The client side of the wear screen: one button in the inventory (where the three old ones were) and one key, both
 * opening it. The old screens' buttons are taken off the inventory, and their keys, if a player bound them again,
 * open the wear screen too.
 */
public final class WearClient {
    private WearClient() {
    }

    static final KeyMapping KEY = new KeyMapping("key.create_galore_bridge.wear", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.inventory");
    static final ResourceLocation BUTTON = ResourceLocation.fromNamespaceAndPath(CreateGaloreBridge.ID, "textures/gui/wear_button.png");

    /** The old screens' inventory buttons (Curios and The Aether; Accessories' button is a layer, stopped by a mixin). */
    private static final Set<String> OLD_BUTTONS = Set.of(
            "top.theillusivec4.curios.client.gui.CuriosButton",
            "com.aetherteam.aether.client.gui.component.inventory.AccessoryButton");
    /** The old screens' keys. */
    private static final Set<String> OLD_KEYS = Set.of("key.curios.open.desc", "key.aether.open_accessories.desc",
            "accessories.key.open_accessories_screen");

    public static void init(IEventBus modBus) {
        // The wear screen exists only with an accessory mod (see Wear.register); without one its menu is never made.
        if (!dev.creategalore.bridge.wear.Worn.HAS_CURIOS && !dev.creategalore.bridge.wear.Worn.HAS_ACCESSORIES) return;
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(WearMenu.TYPE.get(), WearScreen::new));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, WearClient::onInit);
        NeoForge.EVENT_BUS.addListener(WearClient::onTick);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre e) -> {
            for (GuiEventListener child : e.getScreen().children()) if (child instanceof Button b) b.follow();
        });
    }

    static void open() {
        PacketDistributor.sendToServer(Wear.Open.INSTANCE);
    }

    private static void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        boolean asked = false;
        while (KEY.consumeClick()) asked = true;
        for (KeyMapping key : mc.options.keyMappings) {
            if (OLD_KEYS.contains(key.getName())) while (key.consumeClick()) asked = true;
        }
        if (asked && mc.player != null && mc.screen == null) open();
    }

    private static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        for (GuiEventListener widget : List.copyOf(event.getListenersList())) {
            if (OLD_BUTTONS.contains(widget.getClass().getName())) event.removeListener(widget);
        }
        if (screen instanceof CreativeModeInventoryScreen creative) {
            event.addListener(new Button(screen, 95, 6, s -> creative.isInventoryOpen(), WearClient::open,
                    Component.translatable("create_galore_bridge.wear.button")));
        } else {
            event.addListener(new Button(screen, 65, 9, s -> true, WearClient::open, Component.translatable("create_galore_bridge.wear.button")));
        }
    }

    /** A small ring icon that stays at its place on the screen when the recipe book moves the inventory. */
    static final class Button extends AbstractButton {
        private final AbstractContainerScreen<?> screen;
        private final int dx, dy;
        private final Predicate<AbstractContainerScreen<?>> show;
        private final Runnable action;

        Button(AbstractContainerScreen<?> screen, int dx, int dy, Predicate<AbstractContainerScreen<?>> show, Runnable action, Component tip) {
            super(screen.getGuiLeft() + dx, screen.getGuiTop() + dy, 10, 10, tip);
            this.screen = screen;
            this.dx = dx;
            this.dy = dy;
            this.show = show;
            this.action = action;
            setTooltip(Tooltip.create(tip));
        }

        void follow() {
            visible = show.test(screen);
            setPosition(screen.getGuiLeft() + dx, screen.getGuiTop() + dy);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(BUTTON, getX(), getY(), 0, isHoveredOrFocused() ? 10 : 0, 10, 10, 10, 20);
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
