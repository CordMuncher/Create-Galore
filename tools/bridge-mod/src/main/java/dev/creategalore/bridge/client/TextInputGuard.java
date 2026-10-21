package dev.creategalore.bridge.client;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;

/**
 * Tells whether the player is typing into a text box on the open screen, so key presses go to the text and not to
 * hotkeys. Inventory Profiles Next sorts on R (and moves or throws everything on R+T and R+M) in any container
 * screen. It skips its hotkeys while one of the screen's own text boxes has focus, but JEI's search box sits on top
 * of the screen and isn't one of them, so every R typed into JEI sorted the inventory.
 *
 * Counts the screen's focused text box (Field Guide search, Ask the Book, anvil, creative search, sign and book
 * editing), JEI's search box and EMI's.
 */
public final class TextInputGuard {
    private static Field emiSearch;
    private static boolean emiLooked;

    private TextInputGuard() {
    }

    public static boolean isTyping() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        if (focusedTextBox(screen)) return true;
        if (ModList.get().isLoaded("jei") && JeiSearchFocus.hasFocus()) return true;
        return ModList.get().isLoaded("emi") && emiSearchFocused();
    }

    private static boolean focusedTextBox(Screen screen) {
        GuiEventListener focused = screen.getFocused();
        while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
            if (isTextBox(focused)) return true;
            focused = container.getFocused();
        }
        if (isTextBox(focused)) return true;
        // Some screens give a text box focus without making it the screen's focused child.
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isVisible() && box.isFocused()) return true;
        }
        return false;
    }

    private static boolean isTextBox(GuiEventListener listener) {
        return listener instanceof EditBox box ? box.isVisible() && box.canConsumeInput()
                : listener instanceof MultiLineEditBox area && area.isFocused();
    }

    /** EMI keeps its search box in a static field of its screen manager. */
    private static boolean emiSearchFocused() {
        if (!emiLooked) {
            emiLooked = true;
            try {
                emiSearch = Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("search");
            } catch (ReflectiveOperationException | LinkageError e) {
                emiSearch = null;
            }
        }
        if (emiSearch == null) return false;
        try {
            return emiSearch.get(null) instanceof EditBox box && box.isFocused();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
