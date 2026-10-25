package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.creategalore.bridge.DisplaySettings;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Bridge's display settings: the DLAA engine and DLSS preset, the bring-your-own nvngx_dlss.dll, and the window
 * switches. Opens from the mod list (Create Galore Bridge, Config) and from a DLAA button in Iris's shader pack menu.
 * Saved to config/create_galore_bridge-display.properties when it closes; DLAA changes reload the shaders.
 */
public final class DlaaScreen extends Screen {
    /** Where NVIDIA publishes nvngx_dlss.dll, in its DLSS SDK. */
    private static final String DLL_PAGE = "https://github.com/NVIDIA/DLSS/tree/main/lib/Windows_x86_64/rel";
    private static final String IRIS_PACK_SCREEN = "net.irisshaders.iris.gui.screen.ShaderPackScreen";
    private static final List<String> ENGINES = List.of("auto", "dlss", "fsr3", "off");
    /** The presets offered by name; "other" takes a typed letter. */
    private static final List<String> PRESETS = List.of("default", "K", "J", "L", "M", "other");
    private static final int WIDTH = 300;

    private final Screen parent;
    private final Map<String, String> initial = new HashMap<>();
    private String engine;
    private String preset;
    private String letter;
    private boolean perfectPresentation;
    private boolean tenBitWindow;
    private EditBox letterBox;
    private String dllLine;
    private int ticks;

    public DlaaScreen(Screen parent) {
        super(Component.literal("Create Galore display settings"));
        this.parent = parent;
        DisplaySettings.reload();
        engine = DisplaySettings.dlaaEngine();
        String presetValue = DisplaySettings.getString(DisplaySettings.DLAA_PRESET, "default");
        letter = presetValue.length() == 1 ? presetValue.toUpperCase(Locale.ROOT) : "";
        preset = presetValue.equalsIgnoreCase("default") || presetValue.isEmpty() ? "default"
                : PRESETS.contains(letter) ? letter : "other";
        perfectPresentation = DisplaySettings.get(DisplaySettings.PERFECT_PRESENTATION);
        tenBitWindow = DisplaySettings.get(DisplaySettings.TEN_BIT_WINDOW);
        initial.putAll(values());
    }

    /** At mod construction (client): the mod list's Config button and the shader pack menu's DLAA button. */
    public static void register(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new DlaaScreen(parent));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            Screen screen = event.getScreen();
            if (screen.getClass().getName().equals(IRIS_PACK_SCREEN)) {
                event.addListener(Button.builder(Component.literal("DLAA..."),
                                button -> screen.getMinecraft().setScreen(new DlaaScreen(screen)))
                        .bounds(screen.width - 66, 6, 60, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                                "Experimental: NVIDIA DLAA or AMD FSR 3.1 in place of Photon's TAA (shader pack " + PhotonPatcher.PATCHED_NAME + ")")))
                        .build());
            }
        });
    }

    @Override
    protected void init() {
        int left = width / 2 - WIDTH / 2;
        int half = (WIDTH - 4) / 2;
        addRenderableWidget(CycleButton.<String>builder(DlaaScreen::engineLabel)
                .withValues(ENGINES).withInitialValue(engine)
                .withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(engineTooltip(value))))
                .create(left, 50, WIDTH, 20, Component.literal("Anti-aliasing"), (button, value) -> engine = value));
        addRenderableWidget(CycleButton.<String>builder(DlaaScreen::presetLabel)
                .withValues(PRESETS).withInitialValue(preset)
                .withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(presetTooltip(value))))
                .create(left, 74, WIDTH - 64, 20, Component.literal("DLSS preset"), (button, value) -> {
                    preset = value;
                    letterBox.visible = value.equals("other");
                }));
        letterBox = new EditBox(font, left + WIDTH - 60, 74, 60, 20, Component.literal("Preset letter"));
        letterBox.setMaxLength(1);
        letterBox.setFilter(text -> text.isEmpty() || Character.isLetter(text.charAt(0)));
        letterBox.setValue(letter);
        letterBox.setHint(Component.literal("letter").withStyle(ChatFormatting.DARK_GRAY));
        letterBox.setResponder(text -> letter = text.toUpperCase(Locale.ROOT));
        letterBox.visible = preset.equals("other");
        addRenderableWidget(letterBox);

        int y = 164;
        addRenderableWidget(Button.builder(Component.literal("Open DLSS folder"), button -> openDllFolder())
                .bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Get it from NVIDIA"), ConfirmLinkScreen.confirmLink(this, DLL_PAGE))
                .bounds(left + half + 4, y, half, 20).build());
        y += 24;
        if (Dlss.crashed()) {
            addRenderableWidget(Button.builder(Component.literal("Try again (turned off after the game closed while starting)"), button -> {
                        Dlss.tryAgain();
                        button.active = false;
                    })
                    .bounds(left, y, WIDTH, 20).build());
            y += 24;
        }
        addRenderableWidget(CycleButton.onOffBuilder(perfectPresentation)
                .withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Experimental, Windows only, needs a restart: shows the game through a DirectX swap chain, so borderless"
                                + " fullscreen runs at exclusive fullscreen speed and Alt-Tab never changes the display mode.")))
                .create(left, y, half, 20, Component.literal("Perfect Presentation"), (button, value) -> perfectPresentation = value));
        addRenderableWidget(CycleButton.onOffBuilder(tenBitWindow)
                .withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Experimental, needs a restart: asks for a 10-bit game window.")))
                .create(left + half + 4, y, half, 20, Component.literal("10-bit window"), (button, value) -> tenBitWindow = value));
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 100, Math.max(y + 28, height - 28), 200, 20).build());
        refreshDll();
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) {
            refreshDll(); // the player may have just put the DLL in
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - WIDTH / 2;
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        lines(graphics, "EXPERIMENTAL and optional, not widely tested yet. Only with the shader pack \""
                + PhotonPatcher.PATCHED_NAME + "\": pick it in the shader menu.", left, 18, 0xFFDD66, 3);
        int y = lines(graphics, Dlss.status(), left, 98, 0xAAAAAA, 3);
        lines(graphics, dllLine, left, y + 2, 0xFFFFFF, (162 - y) / 10);
    }

    /** Draws {@code text} wrapped to the screen's width, at most {@code max} lines; returns the y below it. */
    private int lines(GuiGraphics graphics, String text, int x, int y, int color, int max) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), WIDTH);
        for (int i = 0; i < Math.min(max, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x, y, color);
            y += 10;
        }
        return y;
    }

    @Override
    public void onClose() {
        Map<String, String> values = values();
        if (!values.equals(initial)) {
            DisplaySettings.save(values);
            if (!values.get(DisplaySettings.DLAA).equals(initial.get(DisplaySettings.DLAA))
                    || !values.get(DisplaySettings.DLAA_PRESET).equals(initial.get(DisplaySettings.DLAA_PRESET))) {
                Dlss.settingsChanged();
            }
        }
        minecraft.setScreen(parent);
    }

    private Map<String, String> values() {
        Map<String, String> values = new HashMap<>();
        values.put(DisplaySettings.DLAA, engine);
        values.put(DisplaySettings.DLAA_PRESET, preset.equals("other") ? (letter.isEmpty() ? "default" : letter) : preset);
        values.put(DisplaySettings.PERFECT_PRESENTATION, Boolean.toString(perfectPresentation));
        values.put(DisplaySettings.TEN_BIT_WINDOW, Boolean.toString(tenBitWindow));
        return values;
    }

    private void refreshDll() {
        String found = Dlss.foundDllVersion();
        dllLine = found == null
                ? "nvngx_dlss.dll: not found. DLSS needs your own (not in the pack): " + Dlss.TESTED_DLL_VERSION
                        + " or newer in the DLSS folder, then reload shaders (F8)."
                : "nvngx_dlss.dll: " + found + " (tested with " + Dlss.TESTED_DLL_VERSION + "). Close the game before replacing it.";
    }

    private void openDllFolder() {
        Path folder = Dlss.dllFolder();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            // opens what is there
        }
        Util.getPlatform().openPath(folder);
    }

    private static Component engineLabel(String value) {
        return Component.literal(switch (value) {
            case "dlss" -> "NVIDIA DLAA";
            case "fsr3" -> "AMD FSR 3.1 native";
            case "off" -> "Off (Photon's TAA)";
            default -> "Auto";
        });
    }

    private static String engineTooltip(String value) {
        return switch (value) {
            case "dlss" -> "Only NVIDIA DLAA (DLSS at full resolution): Windows, an NVIDIA RTX card and your own nvngx_dlss.dll.";
            case "fsr3" -> "Only AMD FSR 3.1 at full resolution: any graphics card with OpenGL 4.5, nothing to download.";
            case "off" -> "Photon's own anti-aliasing (TAA), as in plain Photon.";
            default -> "NVIDIA DLAA where it can run (Windows, an NVIDIA RTX card, your own nvngx_dlss.dll), else AMD FSR 3.1 at full resolution.";
        };
    }

    private static Component presetLabel(String value) {
        return Component.literal(switch (value) {
            case "default" -> "Default (DLSS picks, K now)";
            case "other" -> "Other letter:";
            default -> value;
        });
    }

    private static String presetTooltip(String value) {
        return switch (value) {
            case "default" -> "Lets DLSS choose; at full resolution that is K with DLSS 310.";
            case "K" -> "NVIDIA's pick for the best image at full resolution (transformer model).";
            case "J" -> "Like K, a little less ghosting, a little more flicker.";
            case "L" -> "Made for Ultra Performance upscaling; meters the exposure itself.";
            case "M" -> "Made for Performance upscaling; meters the exposure itself.";
            default -> "Any letter goes to DLSS: a newer nvngx_dlss.dll may have more presets. A letter DLSS has no"
                    + " preset for runs as default (DLSS 310 has E, F and J to M).";
        };
    }
}
