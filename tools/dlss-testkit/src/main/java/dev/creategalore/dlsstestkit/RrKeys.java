package dev.creategalore.dlsstestkit;

import java.lang.reflect.Method;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * The Photon RR research's hotkeys, for looking by eye: Ctrl+[ and Ctrl+] pick one of the Bridge's Ray Reconstruction
 * switches (dev.creategalore.bridge.dlss.RrResearch: what Photon leaves raw for Ray Reconstruction), Ctrl+\ flips it
 * between raw and Photon's own, Shift+\ flips every effect at once (raw, or Photon's own). With Ctrl or Shift, since
 * the pack has [, ] and \ alone for other mods (Xaero's settings, Create Railways Navigator); a key with its modifier
 * held goes to the mapping with that modifier only. Each switch is a uniform, so it changes on the next frame, no
 * reload. The action bar says what is picked or flipped, and whether the switches apply right now (only while Ray
 * Reconstruction runs, or a debug view shows its inputs). /dlaatest rr does the same by name.
 */
final class RrKeys {
    private static final String CATEGORY = "key.categories.create_galore_dlss_testkit";
    static final KeyMapping PREVIOUS = new KeyMapping("key.create_galore_dlss_testkit.rr_previous", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);
    static final KeyMapping NEXT = new KeyMapping("key.create_galore_dlss_testkit.rr_next", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
    static final KeyMapping FLIP = new KeyMapping("key.create_galore_dlss_testkit.rr_flip", KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY);
    static final KeyMapping ALL = new KeyMapping("key.create_galore_dlss_testkit.rr_all", KeyConflictContext.IN_GAME, KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY);
    private static final String RESEARCH = "dev.creategalore.bridge.dlss.RrResearch";

    private static int picked;

    private RrKeys() {}

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(PREVIOUS);
            e.register(NEXT);
            e.register(FLIP);
            e.register(ALL);
        });
        NeoForge.EVENT_BUS.addListener(RrKeys::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Capture.running()) {
            return;
        }
        List<String> tokens = tokens();
        if (tokens.isEmpty()) {
            return;
        }
        while (PREVIOUS.consumeClick()) {
            picked = (picked + tokens.size() - 1) % tokens.size();
            show("picked " + state(tokens.get(picked)));
        }
        while (NEXT.consumeClick()) {
            picked = (picked + 1) % tokens.size();
            show("picked " + state(tokens.get(picked)));
        }
        while (FLIP.consumeClick()) {
            show(set(tokens.get(picked % tokens.size()), null));
        }
        while (ALL.consumeClick()) {
            show(set("all", null));
        }
    }

    private static void show(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("RR: " + message + (applies() ? "" : " (applies once Ray Reconstruction runs)")), true);
        }
    }

    /** /dlaatest rr: every switch and what it is now. */
    static String status() {
        List<String> tokens = tokens();
        if (tokens.isEmpty()) {
            return "this Bridge has no Ray Reconstruction switches";
        }
        StringBuilder text = new StringBuilder("Ray Reconstruction switches" + (applies() ? "" : " (they apply once Ray Reconstruction runs)") + ":");
        for (String token : tokens) {
            text.append("\n- ").append(token).append(": ").append(state(token));
        }
        return text.toString();
    }

    /** /dlaatest rr SWITCH [raw|photon]: one switch (or all) raw, Photon's own, or flipped (wanted null). */
    static String set(String token, Boolean wanted) {
        Object message = call("set", new Class<?>[] {String.class, Boolean.class}, token, wanted);
        return message != null ? message.toString() : "this Bridge has no Ray Reconstruction switches";
    }

    /** /dlaatest rr use LIST: exactly these switches raw (a+b+c; "none", or "shipped" for Ray Reconstruction's default). */
    static String use(String list) {
        Object message = call("use", new Class<?>[] {String.class}, "shipped".equalsIgnoreCase(list) ? null : list.replace('+', ','));
        return message != null ? "now " + message : "this Bridge has no Ray Reconstruction switches";
    }

    @SuppressWarnings("unchecked")
    static List<String> tokens() {
        Object tokens = call("tokens", new Class<?>[0]);
        return tokens instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static String state(String token) {
        Object state = call("state", new Class<?>[] {String.class}, token);
        return state != null ? state.toString() : token;
    }

    private static boolean applies() {
        return Boolean.TRUE.equals(call("applies", new Class<?>[0]));
    }

    private static Object call(String name, Class<?>[] types, Object... args) {
        try {
            Method method = Class.forName(RESEARCH).getMethod(name, types);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
