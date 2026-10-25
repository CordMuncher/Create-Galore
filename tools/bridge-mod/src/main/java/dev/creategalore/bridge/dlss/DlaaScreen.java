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
 * The Bridge's display settings: the DLAA engine and DLSS preset, the render resolution, the bring-your-own
 * nvngx_dlss.dll (or one found on this PC, see {@link DlssFinder}), and the window switches. Opens from the mod list
 * (Create Galore Bridge, Config) and from a DLAA button in Iris's shader pack menu. Saved to
 * config/create_galore_bridge-display.properties when it closes, the render resolution into the DLAA pack's own options
 * (Photon's TAAU and TAAU Render Scale); DLAA changes reload the shaders.
 */
public final class DlaaScreen extends Screen {
    /** Where NVIDIA publishes nvngx_dlss.dll, in its DLSS SDK. */
    private static final String DLL_PAGE = "https://github.com/NVIDIA/DLSS/tree/main/lib/Windows_x86_64/rel";
    private static final String IRIS_PACK_SCREEN = "net.irisshaders.iris.gui.screen.ShaderPackScreen";
    private static final List<String> ENGINES = List.of("auto", "dlss", "fsr3", "off");
    /** The presets offered by name; "other" takes a typed letter. */
    private static final List<String> PRESETS = List.of("default", "K", "J", "L", "M", "other");
    /** The standard render resolutions (Photon's TAAU Render Scale for each), then custom (the slider). */
    private static final List<String> SCALES = List.of("native", "quality", "balanced", "performance", "ultra", "custom");
    private static final Map<String, String> SCALE_VALUES = Map.of("quality", "0.67", "balanced", "0.58", "performance", "0.50", "ultra", "0.33");
    // Photon's options for it.
    private static final String TAA = "TAA";
    private static final String TAAU = "TAAU";
    private static final String TAAU_RENDER_SCALE = "TAAU_RENDER_SCALE";
    private static final int WIDTH = 300;

    private final Screen parent;
    private final Map<String, String> initial = new HashMap<>();
    private String engine;
    private String preset;
    private String letter;
    private boolean perfectPresentation;
    private boolean tenBitWindow;
    private String scale;
    /** The custom render scale, 25 to 100 (%). */
    private int customPercent;
    private Map<String, String> initialPackOptions;
    private EditBox letterBox;
    private ScaleSlider slider;
    private final List<Choice<?>> choices = new java.util.ArrayList<>();
    /** The setting whose list of values is open, if one is. */
    private Choice<?> open;
    private String dllLine;
    /** What "Find on this PC" did, until the screen closes. */
    private String findLine;
    /** "Find on this PC" put a DLL in place: closing reloads the shaders so DLSS can start with it. */
    private boolean dllAdded;
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
        readRenderScale();
        initial.putAll(values());
        initialPackOptions = packOptions();
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
                                "NVIDIA DLSS or AMD FSR 3.1 in place of Photon's TAA, at full resolution or upscaling (shader pack " + PhotonPatcher.PATCHED_NAME + ")")))
                        .build());
            }
        });
    }

    @Override
    protected void init() {
        int left = width / 2 - WIDTH / 2;
        int half = (WIDTH - 4) / 2;
        open = null;
        choices.clear();
        choice("Anti-aliasing", ENGINES, engine, DlaaScreen::engineLabel, DlaaScreen::engineTooltip, left, 50, WIDTH, value -> engine = value);
        choice("DLSS preset", PRESETS, preset, DlaaScreen::presetLabel, DlaaScreen::presetTooltip, left, 74, WIDTH - 64, value -> {
            preset = value;
            letterBox.visible = value.equals("other");
        });
        letterBox = new EditBox(font, left + WIDTH - 60, 74, 60, 20, Component.literal("Preset letter"));
        letterBox.setMaxLength(1);
        letterBox.setFilter(text -> text.isEmpty() || Character.isLetter(text.charAt(0)));
        letterBox.setValue(letter);
        letterBox.setHint(Component.literal("letter").withStyle(ChatFormatting.DARK_GRAY));
        letterBox.setResponder(text -> letter = text.toUpperCase(Locale.ROOT));
        letterBox.visible = preset.equals("other");
        addRenderableWidget(letterBox);
        // Custom shares its row with the slider.
        Choice<String> resolution = choice("Render resolution", SCALES, scale, DlaaScreen::scaleLabel, DlaaScreen::scaleTooltip, left, 98,
                scale.equals("custom") ? half : WIDTH, null);
        resolution.onPick = value -> {
            scale = value;
            resolution.button.setWidth(value.equals("custom") ? half : WIDTH);
            slider.visible = value.equals("custom");
        };
        slider = new ScaleSlider(left + half + 4, 98, half);
        slider.visible = scale.equals("custom");
        addRenderableWidget(slider);

        int y = 166;
        int third = (WIDTH - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Find on this PC"), button -> {
                    DlssFinder.Result result = DlssFinder.find();
                    findLine = result.message();
                    dllAdded |= result.changed();
                    refreshDll();
                })
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Looks for nvngx_dlss.dll where the NVIDIA app keeps the DLSS versions it downloaded"
                                + " (ProgramData\\NVIDIA\\NGX), and copies the newest into the DLSS folder. Nowhere else, and only"
                                + " when you press this.")))
                .bounds(left, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Open DLSS folder"), button -> openDllFolder())
                .bounds(left + third + 4, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Get from NVIDIA"), ConfirmLinkScreen.confirmLink(this, DLL_PAGE))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "NVIDIA's DLSS SDK on GitHub: download nvngx_dlss.dll and put it in the DLSS folder.")))
                .bounds(left + 2 * (third + 4), y, WIDTH - 2 * (third + 4), 20).build());
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
        // While a list is open, what lies under it doesn't light up or show its tooltip.
        super.render(graphics, open != null ? -1 : mouseX, open != null ? -1 : mouseY, partialTick);
        int left = width / 2 - WIDTH / 2;
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        lines(graphics, "Optional. Only with the shader pack \""
                + PhotonPatcher.PATCHED_NAME + "\": pick it in the shader menu.", left, 18, 0xFFDD66, 3);
        int y = 122;
        String picked = preset.equals("other") ? letter : preset;
        if (picked.equals("L") || picked.equals("M")) {
            // NVIDIA (DLSS 4.5): RTX 20 and 30 cards have no native FP8, so L and M cost more there. Nothing is checked or locked.
            // Two lines at most, so the DLL line keeps its room.
            y = lines(graphics, "L and M are made for RTX 40 and 50 cards and cost more on RTX 20 and 30 (no FP8): if frames drop,"
                    + " use K.", left, y, 0xFFAA44, 2);
        }
        // What "Find on this PC" did takes the DLL line's place (it names the version), and room from the status.
        y = lines(graphics, Dlss.status(), left, y, 0xAAAAAA, findLine != null ? 1 : 3);
        lines(graphics, findLine != null ? findLine : dllLine, left, y + 2, findLine != null ? 0x88DD88 : 0xFFFFFF, (164 - y) / 10);
        if (open != null) {
            open.renderList(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (open != null) {
            // A click picks what it is on, or else only closes the list.
            Choice<?> list = open;
            open = null;
            list.click(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (open != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            open = null;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
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
        Map<String, String> pack = packOptions();
        boolean packChanged = !pack.equals(initialPackOptions);
        if (packChanged) {
            savePackOptions(pack);
        }
        if (!values.equals(initial)) {
            DisplaySettings.save(values);
        }
        if (packChanged || dllAdded || !values.get(DisplaySettings.DLAA).equals(initial.get(DisplaySettings.DLAA))
                || !values.get(DisplaySettings.DLAA_PRESET).equals(initial.get(DisplaySettings.DLAA_PRESET))) {
            Dlss.settingsChanged();
        }
        minecraft.setScreen(parent);
    }

    private Map<String, String> values() {
        Map<String, String> values = new HashMap<>();
        values.put(DisplaySettings.DLAA, engine);
        values.put(DisplaySettings.DLAA_PRESET, preset.equals("other") ? (letter.isEmpty() ? "default" : letter) : preset);
        values.put(DisplaySettings.PERFECT_PRESENTATION, Boolean.toString(perfectPresentation));
        values.put(DisplaySettings.TEN_BIT_WINDOW, Boolean.toString(tenBitWindow));
        values.put(DisplaySettings.DLAA_CUSTOM_SCALE, Boolean.toString(scale.equals("custom")));
        return values;
    }

    /** Photon's options for the render resolution picked: TAAU off at native, else on at the scale. */
    private Map<String, String> packOptions() {
        Map<String, String> options = new HashMap<>();
        options.put(TAAU, Boolean.toString(!scale.equals("native")));
        if (!scale.equals("native")) {
            options.put(TAAU_RENDER_SCALE, scale.equals("custom")
                    ? String.format(Locale.ROOT, "%.2f", customPercent / 100.0) : SCALE_VALUES.get(scale));
        }
        return options;
    }

    /** The render resolution as the DLAA pack's options have it (Iris keeps them in its .txt beside the pack). */
    private void readRenderScale() {
        java.util.Properties options = new java.util.Properties();
        Path file = net.irisshaders.iris.Iris.getShaderpacksDirectory().resolve(PhotonPatcher.PATCHED_NAME + ".txt");
        try (java.io.Reader reader = Files.newBufferedReader(file)) {
            options.load(reader);
        } catch (IOException | RuntimeException e) {
            // Photon's defaults: TAA on, TAAU off
        }
        String value = options.getProperty(TAAU_RENDER_SCALE, "0.75").trim();
        int percent;
        try {
            percent = Math.round(Float.parseFloat(value) * 100.0f);
        } catch (NumberFormatException e) {
            percent = 75;
        }
        customPercent = Math.max(25, Math.min(100, percent));
        boolean upscaling = Boolean.parseBoolean(options.getProperty(TAAU, "false").trim())
                && !options.getProperty(TAA, "true").trim().equalsIgnoreCase("false");
        String standard = SCALE_VALUES.entrySet().stream().filter(e -> e.getValue().equals(value)).map(Map.Entry::getKey).findFirst().orElse(null);
        scale = !upscaling ? "native" : DisplaySettings.get(DisplaySettings.DLAA_CUSTOM_SCALE) || standard == null ? "custom" : standard;
    }

    /**
     * Into the DLAA pack's options: through Iris's queue when it is the pack in use (the reload after this applies and
     * saves them), else into its .txt for when it is picked.
     */
    private static void savePackOptions(Map<String, String> options) {
        if (PhotonPatcher.PATCHED_NAME.equals(net.irisshaders.iris.Iris.getCurrentPackName())) {
            net.irisshaders.iris.Iris.getShaderPackOptionQueue().putAll(options);
            return;
        }
        Path file = net.irisshaders.iris.Iris.getShaderpacksDirectory().resolve(PhotonPatcher.PATCHED_NAME + ".txt");
        java.util.Properties stored = new java.util.Properties();
        try {
            if (Files.isRegularFile(file)) {
                try (java.io.Reader reader = Files.newBufferedReader(file)) {
                    stored.load(reader);
                }
            }
            stored.putAll(options);
            try (java.io.Writer writer = Files.newBufferedWriter(file)) {
                stored.store(writer, null);
            }
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("create_galore_bridge").warn("DLAA: could not write {}: {}", file, e.toString());
        }
    }

    private void refreshDll() {
        String found = Dlss.foundDllVersion();
        dllLine = found == null
                ? "nvngx_dlss.dll: not found. DLSS needs your own (not in the pack): " + Dlss.TESTED_DLL_VERSION
                        + " or newer in the DLSS folder (find it on this PC or get it from NVIDIA), then reload shaders (F8)."
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
            case "dlss" -> "NVIDIA DLSS";
            case "fsr3" -> "AMD FSR 3.1";
            case "off" -> "Off (Photon's own)";
            default -> "Auto";
        });
    }

    private static String engineTooltip(String value) {
        return switch (value) {
            case "dlss" -> "Only NVIDIA DLSS (DLAA at full resolution): Windows, an NVIDIA RTX card and your own nvngx_dlss.dll.";
            case "fsr3" -> "Only AMD FSR 3.1: any graphics card with OpenGL 4.5, nothing to download.";
            case "off" -> "Photon's own anti-aliasing (TAA, or TAAU when upscaling), as in plain Photon.";
            default -> "NVIDIA DLSS where it can run (Windows, an NVIDIA RTX card, your own nvngx_dlss.dll), else AMD FSR 3.1.";
        };
    }

    private static Component scaleLabel(String value) {
        return Component.literal(switch (value) {
            case "quality" -> "Quality (67%)";
            case "balanced" -> "Balanced (58%)";
            case "performance" -> "Performance (50%)";
            case "ultra" -> "Ultra Performance (33%)";
            case "custom" -> "Custom";
            default -> "Native (100%)";
        });
    }

    private static String scaleTooltip(String value) {
        return switch (value) {
            case "native" -> "Renders at the window's size: DLSS (as DLAA) or FSR only smooth the edges.";
            case "custom" -> "Any render resolution from 25% to 100% of the window's width and height, with the slider."
                    + " For trying things out: the standard modes are the tested ones.";
            default -> "Renders at " + switch (value) {
                        case "quality" -> "2/3";
                        case "balanced" -> "58%";
                        case "performance" -> "half";
                        default -> "1/3";
                    } + " of the window's width and height and upscales: faster, a little softer. With anti-aliasing Off,"
                    + " Photon's own upscaling (TAAU) does it.";
        };
    }

    private static Component presetLabel(String value) {
        return Component.literal(switch (value) {
            case "default" -> "Default (K)";
            case "other" -> "Other letter:";
            default -> value;
        });
    }

    private <T> Choice<T> choice(String name, List<T> values, T value, java.util.function.Function<T, Component> label,
                                 java.util.function.Function<T, String> tooltip, int x, int y, int width,
                                 java.util.function.Consumer<T> onPick) {
        Choice<T> choice = new Choice<>(name, values, value, label, tooltip, onPick);
        choice.button = addRenderableWidget(Button.builder(Component.empty(), button -> open = open == choice ? null : choice)
                .bounds(x, y, width, 20).build());
        choice.update();
        choices.add(choice);
        return choice;
    }

    /**
     * A setting as a button that opens the list of its values under it (over it if there's no room): all of them in
     * view, one click to open and one to pick.
     */
    private final class Choice<T> {
        private static final int ROW = 16;
        final String name;
        final List<T> values;
        final java.util.function.Function<T, Component> label;
        final java.util.function.Function<T, String> tooltip;
        java.util.function.Consumer<T> onPick;
        T value;
        Button button;

        Choice(String name, List<T> values, T value, java.util.function.Function<T, Component> label,
               java.util.function.Function<T, String> tooltip, java.util.function.Consumer<T> onPick) {
            this.name = name;
            this.values = values;
            this.value = value;
            this.label = label;
            this.tooltip = tooltip;
            this.onPick = onPick;
        }

        void update() {
            button.setMessage(Component.literal(name + ": ").append(label.apply(value)).append(" \u25BE"));
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tooltip.apply(value))));
        }

        private int listWidth() {
            int widest = 0;
            for (T v : values) {
                widest = Math.max(widest, font.width(label.apply(v)));
            }
            return Math.max(button.getWidth(), widest + 16);
        }

        private int listTop() {
            int below = button.getBottom();
            return below + values.size() * ROW <= height ? below : button.getY() - values.size() * ROW;
        }

        /** The value at this point of the open list, or -1. */
        private int at(double mouseX, double mouseY) {
            int top = listTop();
            if (mouseX < button.getX() || mouseX >= button.getX() + listWidth() || mouseY < top) {
                return -1;
            }
            int row = (int) ((mouseY - top) / ROW);
            return row < values.size() ? row : -1;
        }

        void click(double mouseX, double mouseY) {
            int row = at(mouseX, mouseY);
            if (row >= 0) {
                value = values.get(row);
                update();
                onPick.accept(value);
            }
        }

        void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
            int x = button.getX();
            int top = listTop();
            int w = listWidth();
            int hovered = at(mouseX, mouseY);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 300.0f); // over the widgets under it
            graphics.fill(x, top, x + w, top + values.size() * ROW, 0xFF101010); // opaque: no text shows through
            graphics.renderOutline(x, top, w, values.size() * ROW, 0xFFA0A0A0);
            for (int i = 0; i < values.size(); i++) {
                int rowTop = top + i * ROW;
                if (i == hovered) {
                    graphics.fill(x + 1, rowTop, x + w - 1, rowTop + ROW, 0xFF404040);
                }
                boolean picked = values.get(i).equals(value);
                graphics.drawString(font, label.apply(values.get(i)), x + 8, rowTop + (ROW - 8) / 2, picked ? 0xFFFFDD66 : 0xFFFFFFFF);
            }
            graphics.pose().popPose();
            if (hovered >= 0) {
                setTooltipForNextRenderPass(font.split(Component.literal(tooltip.apply(values.get(hovered))), 220));
            }
        }
    }

    /** The custom render scale, 25% to 100% in steps of 1%. */
    private final class ScaleSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        ScaleSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), (customPercent - 25) / 75.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Render scale: " + customPercent + "%"));
        }

        @Override
        protected void applyValue() {
            customPercent = 25 + (int) Math.round(value * 75.0);
        }
    }

    private static String presetTooltip(String value) {
        return switch (value) {
            case "default" -> "K at every render resolution.";
            case "K" -> "NVIDIA's pick for the best image at full resolution and Quality (transformer model).";
            case "J" -> "Like K, a little less ghosting, a little more flicker.";
            case "L" -> "Made for Ultra Performance upscaling, worth trying from Balanced down; meters the exposure itself."
                    + " For RTX 40 and 50 cards: RTX 20 and 30 cards lack FP8 and lose much of what upscaling saves.";
            case "M" -> "Made for Performance upscaling, worth trying from Balanced down; meters the exposure itself."
                    + " For RTX 40 and 50 cards: RTX 20 and 30 cards lack FP8 and lose much of what upscaling saves.";
            default -> "Any letter goes to DLSS: a newer nvngx_dlss.dll may have more presets. A letter DLSS has no"
                    + " preset for runs as default (DLSS 310 has E, F and J to M).";
        };
    }
}
