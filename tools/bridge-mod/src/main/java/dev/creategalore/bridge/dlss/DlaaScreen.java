package dev.creategalore.bridge.dlss;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.creategalore.bridge.DisplaySettings;
import dev.creategalore.bridge.client.FpsOverlay;
import dev.creategalore.bridge.latency.FrameLatency;
import dev.creategalore.bridge.present.Presentation;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
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
 * The Bridge's display settings, in two tabs. Anti-aliasing: the DLAA engine and DLSS preset, the render resolution,
 * the bring-your-own nvngx_dlss.dll (or one found on this PC, see {@link DlssFinder}). Display: the window switches,
 * NVIDIA Reflex (which turns Perfect Presentation on with it, see {@link FrameLatency}), GPU pacing and the frame counter
 * ({@link FpsOverlay}). Opens from the mod list
 * (Create Galore Bridge, Config) and from a DLAA button in Iris's shader pack menu. Saved to
 * config/create_galore_bridge-display.properties when it closes, the render resolution into the DLAA pack's own options
 * (Photon's TAAU and TAAU Render Scale, see {@link RenderScaleTarget}); DLAA changes reload the shaders.
 */
public final class DlaaScreen extends Screen {
    /** Where NVIDIA publishes nvngx_dlss.dll, in its DLSS SDK. */
    private static final String DLL_PAGE = "https://github.com/NVIDIA/DLSS/tree/main/lib/Windows_x86_64/rel";
    private static final String IRIS_PACK_SCREEN = "net.irisshaders.iris.gui.screen.ShaderPackScreen";
    private static final List<String> ENGINES = List.of("auto", "dlss", "fsr3", "off");
    /** The presets offered by name; "other" takes a typed letter. */
    private static final List<String> PRESETS = List.of("default", "K", "J", "L", "M", "other");
    /** The standard render resolutions (the pack's render scale option for each), then custom (the slider). */
    private static final List<String> SCALES = List.of("native", "quality", "balanced", "performance", "ultra", "custom");
    private static final Map<String, String> SCALE_VALUES = Map.of("quality", "0.67", "balanced", "0.58", "performance", "0.50", "ultra", "0.33");
    private static final List<String> REFLEX_MODES = List.of("off", "on", "boost");
    private static final List<String> PACING_MODES = List.of("off", "on", "lowest");
    private static final List<String> FPS_MODES = List.of(FpsOverlay.OFF, FpsOverlay.BASIC, FpsOverlay.DETAILED);
    private static final List<String> CORNERS = List.of(FpsOverlay.TOP_LEFT, FpsOverlay.TOP_RIGHT, FpsOverlay.BOTTOM_LEFT, FpsOverlay.BOTTOM_RIGHT);
    private static final int WIDTH = 300;
    private static final net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId RESTART =
            new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(10000L);

    private final Screen parent;
    /** The pack whose options hold the render resolution. */
    private final RenderScaleTarget target = RenderScaleTarget.current();
    private final Map<String, String> initial = new HashMap<>();
    private String engine;
    private String preset;
    private String letter;
    private boolean perfectPresentation;
    private boolean tenBitWindow;
    /** off, on or boost. */
    private String reflex;
    /** off, on or lowest. */
    private String gpuPacing;
    private String scale;
    private String fpsCounter;
    private String fpsCorner;
    private boolean fpsDim;
    /** The custom render scale, 25 to 100 (%). */
    private int customPercent;
    private Map<String, String> initialPackOptions;
    private EditBox letterBox;
    private ScaleSlider slider;
    private CycleButton<Boolean> presentationButton;
    private Choice<String> reflexChoice;
    private Choice<String> pacingChoice;
    private final Page antiAliasingPage = new Page("Anti-aliasing");
    private final Page displayPage = new Page("Display");
    /** The tab open: 0 anti-aliasing, 1 display. */
    private int page;
    private TabNavigationBar tabBar;
    /** The tab whose widgets init is making. */
    private List<AbstractWidget> building;
    /** The top of the buttons under the status lines: higher on a short screen, so Done still fits. */
    private int buttonsTop = 176;
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
        String reflexValue = DisplaySettings.getString(DisplaySettings.REFLEX, "off").toLowerCase(Locale.ROOT);
        reflex = reflexValue.equals("true") ? "on" : REFLEX_MODES.contains(reflexValue) ? reflexValue : "off";
        String pacingValue = DisplaySettings.getString(DisplaySettings.GPU_PACING, "off").toLowerCase(Locale.ROOT);
        gpuPacing = PACING_MODES.contains(pacingValue) ? pacingValue : "off";
        String fpsValue = DisplaySettings.getString(DisplaySettings.FPS_COUNTER, FpsOverlay.OFF).toLowerCase(Locale.ROOT);
        fpsCounter = FPS_MODES.contains(fpsValue) ? fpsValue : FpsOverlay.OFF;
        String cornerValue = DisplaySettings.getString(DisplaySettings.FPS_COUNTER_CORNER, FpsOverlay.TOP_RIGHT).toLowerCase(Locale.ROOT);
        fpsCorner = CORNERS.contains(cornerValue) ? cornerValue : FpsOverlay.TOP_RIGHT;
        fpsDim = DisplaySettings.get(DisplaySettings.FPS_COUNTER_DIM);
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
                                "NVIDIA DLSS or AMD FSR 3.1 in place of the shader pack's own anti-aliasing, at full resolution or upscaling"
                                        + " (shader packs made for DLAA, such as " + PhotonPatcher.PATCHED_NAME + ")")))
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
        antiAliasingPage.widgets.clear();
        displayPage.widgets.clear();

        building = antiAliasingPage.widgets;
        choice("Anti-aliasing", ENGINES, engine, DlaaScreen::engineLabel, DlaaScreen::engineTooltip, left, 60, WIDTH, value -> engine = value);
        choice("DLSS preset", PRESETS, preset, DlaaScreen::presetLabel, DlaaScreen::presetTooltip, left, 84, WIDTH - 64, value -> {
            preset = value;
            letterBox.visible = value.equals("other");
        });
        letterBox = new EditBox(font, left + WIDTH - 60, 84, 60, 20, Component.literal("Preset letter"));
        letterBox.setMaxLength(1);
        letterBox.setFilter(text -> text.isEmpty() || Character.isLetter(text.charAt(0)));
        letterBox.setValue(letter);
        letterBox.setHint(Component.literal("letter").withStyle(ChatFormatting.DARK_GRAY));
        letterBox.setResponder(text -> letter = text.toUpperCase(Locale.ROOT));
        letterBox.visible = preset.equals("other");
        add(letterBox);
        // Custom shares its row with the slider.
        Choice<String> resolution = choice("Render resolution", SCALES, scale, DlaaScreen::scaleLabel, DlaaScreen::scaleTooltip, left, 108,
                scale.equals("custom") ? half : WIDTH, null);
        resolution.onPick = value -> {
            scale = value;
            resolution.button.setWidth(value.equals("custom") ? half : WIDTH);
            slider.visible = value.equals("custom");
        };
        slider = new ScaleSlider(left + half + 4, 108, half);
        slider.visible = scale.equals("custom");
        add(slider);

        // The DLL buttons and Try again, then Done 28 lower; the status lines get one line more where it fits, and on
        // a short screen (a big GUI scale) one less.
        int below = (Dlss.crashed() ? 24 : 0) + 48;
        buttonsTop = 186 + below <= height ? 186 : 176 + below <= height ? 176 : 152;
        int y = buttonsTop;
        int third = (WIDTH - 8) / 3;
        add(Button.builder(Component.literal("Find on this PC"), button -> {
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
        add(Button.builder(Component.literal("Open DLSS folder"), button -> openDllFolder())
                .bounds(left + third + 4, y, third, 20).build());
        add(Button.builder(Component.literal("Get from NVIDIA"), ConfirmLinkScreen.confirmLink(this, DLL_PAGE))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "NVIDIA's DLSS SDK on GitHub: download nvngx_dlss.dll and put it in the DLSS folder.")))
                .bounds(left + 2 * (third + 4), y, WIDTH - 2 * (third + 4), 20).build());
        if (Dlss.crashed()) {
            y += 24;
            tryAgain(left, y);
        }
        int bottom = y;

        building = displayPage.widgets;
        y = 52;
        presentationButton = add(CycleButton.onOffBuilder(perfectPresentation)
                .withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Experimental, Windows only, needs a restart: shows the game through a DirectX swap chain, so borderless"
                                + " fullscreen runs at exclusive fullscreen speed and Alt-Tab never changes the display mode."
                                + " NVIDIA Reflex needs it.")))
                .create(left, y, half, 20, Component.literal("Perfect Presentation"), (button, value) -> {
                    perfectPresentation = value;
                    if (!value && !reflex.equals("off")) {
                        reflex = "off"; // Reflex runs on its DirectX device
                        reflexChoice.value = "off";
                        reflexChoice.update();
                    }
                }));
        add(CycleButton.onOffBuilder(tenBitWindow)
                .withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Experimental, needs a restart: asks for a 10-bit game window.")))
                .create(left + half + 4, y, half, 20, Component.literal("10-bit window"), (button, value) -> tenBitWindow = value));
        y += 24;
        reflexChoice = choice("NVIDIA Reflex", REFLEX_MODES, reflex, DlaaScreen::reflexLabel, DlaaScreen::reflexTooltip, left, y, half, value -> {
            reflex = value;
            if (!value.equals("off") && !perfectPresentation) {
                perfectPresentation = true; // Reflex runs on its DirectX device
                presentationButton.setValue(true);
            }
        });
        y += 24;
        pacingChoice = choice("GPU pacing", PACING_MODES, gpuPacing, DlaaScreen::pacingLabel, DlaaScreen::pacingTooltip, left, y, half,
                value -> gpuPacing = value);
        y += 24;
        choice("Frame counter", FPS_MODES, fpsCounter, DlaaScreen::fpsLabel, DlaaScreen::fpsTooltip, left, y, half, value -> fpsCounter = value);
        add(CycleButton.onOffBuilder(fpsDim)
                .withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "For OLED screens: the frame counter is see-through, without its dark backing, and moves by a pixel"
                                + " every two minutes, so it doesn't burn in.")))
                .create(left + half + 4, y, half, 20, Component.literal("Dim for OLED"), (button, value) -> fpsDim = value));
        y += 24;
        choice("Corner", CORNERS, fpsCorner, DlaaScreen::cornerLabel, value -> "Where the frame counter sits. In the top right"
                + " it moves down under the status effect icons.", left, y, half, value -> fpsCorner = value);
        if (FrameLatency.crashed()) {
            y += 24;
            tryAgain(left, y);
        }
        bottom = Math.max(bottom, y);
        building = null;

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 100, Math.max(bottom + 28, height - 28), 200, 20).build());
        TabManager tabs = new TabManager(this::addRenderableWidget, this::removeWidget) {
            @Override
            public void setCurrentTab(Tab tab, boolean playClickSound) {
                open = null;
                page = tab == displayPage ? 1 : 0;
                super.setCurrentTab(tab, playClickSound);
            }
        };
        tabBar = TabNavigationBar.builder(tabs, width).addTabs(antiAliasingPage, displayPage).build();
        addRenderableWidget(tabBar);
        tabBar.arrangeElements();
        int tabsBottom = tabBar.getRectangle().bottom();
        tabs.setTabArea(new ScreenRectangle(0, tabsBottom, width, height - tabsBottom));
        tabBar.selectTab(page, false);
        refreshDll();
    }

    /** Adds a widget to the tab being built (shown while that tab is open). */
    private <T extends AbstractWidget> T add(T widget) {
        building.add(widget);
        return widget;
    }

    private void tryAgain(int left, int y) {
        add(Button.builder(Component.literal("Try again (turned off after the game closed while starting)"), button -> {
                    Dlss.tryAgain();
                    FrameLatency.tryAgain();
                    button.active = false;
                })
                .bounds(left, y, WIDTH, 20).build());
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
        if (page == 0) {
            renderAntiAliasing(graphics, left);
        } else {
            renderDisplay(graphics, left, mouseX, mouseY);
        }
        if (open != null) {
            open.renderList(graphics, mouseX, mouseY);
        }
    }

    private void renderAntiAliasing(GuiGraphics graphics, int left) {
        lines(graphics, "Optional. Only with a shader pack made for DLAA, such as \""
                + PhotonPatcher.PATCHED_NAME + "\": pick it in the shader menu.", left, 28, 0xFFDD66, 3);
        int y = 132;
        String picked = preset.equals("other") ? letter : preset;
        if (picked.equals("L") || picked.equals("M")) {
            // NVIDIA (DLSS 4.5): RTX 20 and 30 cards have no native FP8, so L and M cost more there. Nothing is checked or locked.
            // Two lines at most, so the DLL line keeps its room.
            y = lines(graphics, "L and M are made for RTX 40 and 50 cards and cost more on RTX 20 and 30 (no FP8): if frames drop,"
                    + " use K.", left, y, 0xFFAA44, buttonsTop < 176 ? 1 : 2);
        }
        // What "Find on this PC" did takes the DLL line's place (it names the version), and room from the status.
        // The status keeps a line however short the screen; the DLL line takes what fits above the buttons.
        y = lines(graphics, Dlss.status(), left, y, 0xAAAAAA, Math.min(findLine != null ? 1 : 3, Math.max(1, (buttonsTop - 12 - y) / 10)));
        lines(graphics, findLine != null ? findLine : dllLine, left, y + 2, findLine != null ? 0x88DD88 : 0xFFFFFF, (buttonsTop - y) / 10);
    }

    private void renderDisplay(GuiGraphics graphics, int left, int mouseX, int mouseY) {
        lines(graphics, "Window, input delay and frame counter. Perfect Presentation and the 10-bit window need a restart.",
                left, 28, 0xAAAAAA, 2);
        FrameLatency.Status reflexStatus = reflexStatus();
        if (reflexStatus != null) {
            // Beside the Reflex setting: one line centred on the button, two filling its height; the rest on hover.
            int half = (WIDTH - 4) / 2;
            int x = left + half + 4;
            int rowY = reflexChoice.button.getY();
            int count = font.split(Component.literal(reflexStatus.text()), half).size();
            lines(graphics, reflexStatus.text(), x, count > 1 ? rowY + 1 : rowY + 6, reflexStatus.problem() ? 0xFFAA44 : 0x88DD88, 2, half);
            if (open == null && reflexStatus.detail() != null && mouseX >= x && mouseX < x + half && mouseY >= rowY && mouseY < rowY + 20) {
                setTooltipForNextRenderPass(font.split(Component.literal(reflexStatus.detail()), 220));
            }
        }
        if (!gpuPacing.equals("off")) {
            int half = (WIDTH - 4) / 2;
            String hint = gpuPacing.equals("on") ? "Shorter delay, same frame rate." : "Shortest delay, about 6% fewer frames.";
            int count = font.split(Component.literal(hint), half).size();
            int rowY = pacingChoice.button.getY();
            lines(graphics, hint, left + half + 4, count > 1 ? rowY + 1 : rowY + 6, gpuPacing.equals("on") ? 0x88DD88 : 0xFFDD66, 2, half);
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
        if (tabBar.keyPressed(key)) {
            return true; // Ctrl+Tab and Ctrl+1/2 switch tabs
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** Draws {@code text} wrapped to the screen's width, at most {@code max} lines; returns the y below it. */
    private int lines(GuiGraphics graphics, String text, int x, int y, int color, int max) {
        return lines(graphics, text, x, y, color, max, WIDTH);
    }

    private int lines(GuiGraphics graphics, String text, int x, int y, int color, int max, int width) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
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
            target.write(pack);
        }
        if (!values.equals(initial)) {
            DisplaySettings.save(values);
        }
        boolean presentationChanged = !values.get(DisplaySettings.PERFECT_PRESENTATION).equals(initial.get(DisplaySettings.PERFECT_PRESENTATION));
        if (presentationChanged && perfectPresentation) {
            Presentation.switchedOn();
        }
        if (presentationChanged || !values.get(DisplaySettings.TEN_BIT_WINDOW).equals(initial.get(DisplaySettings.TEN_BIT_WINDOW))) {
            minecraft.getToasts().addToast(net.minecraft.client.gui.components.toasts.SystemToast.multiline(minecraft, RESTART,
                    Component.literal("Restart the game"), Component.literal(presentationChanged && perfectPresentation && !reflex.equals("off")
                            ? "NVIDIA Reflex runs once Perfect Presentation starts, after a restart."
                            : presentationChanged ? "Perfect Presentation " + (perfectPresentation ? "starts" : "stops") + " after a restart."
                            : "The 10-bit window setting applies after a restart.")));
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
        values.put(DisplaySettings.REFLEX, reflex);
        values.put(DisplaySettings.GPU_PACING, gpuPacing);
        values.put(DisplaySettings.DLAA_CUSTOM_SCALE, Boolean.toString(scale.equals("custom")));
        values.put(DisplaySettings.FPS_COUNTER, fpsCounter);
        values.put(DisplaySettings.FPS_COUNTER_CORNER, fpsCorner);
        values.put(DisplaySettings.FPS_COUNTER_DIM, Boolean.toString(fpsDim));
        return values;
    }

    /** The pack's options for the render resolution picked: upscaling off at native, else on at the scale. */
    private Map<String, String> packOptions() {
        Map<String, String> options = new HashMap<>();
        options.put(target.upscaling(), Boolean.toString(!scale.equals("native")));
        if (!scale.equals("native")) {
            options.put(target.scale(), scale.equals("custom")
                    ? String.format(Locale.ROOT, "%.2f", customPercent / 100.0) : SCALE_VALUES.get(scale));
        }
        return options;
    }

    /** The render resolution as the DLAA pack's options have it. */
    private void readRenderScale() {
        RenderScaleTarget.Setting setting = target.read();
        String value = setting.scale() == null ? "0.75" : setting.scale();
        int percent;
        try {
            percent = Math.round(Float.parseFloat(value) * 100.0f);
        } catch (NumberFormatException e) {
            percent = 75;
        }
        customPercent = Math.max(25, Math.min(100, percent));
        String standard = SCALE_VALUES.entrySet().stream().filter(e -> e.getValue().equals(value)).map(Map.Entry::getKey).findFirst().orElse(null);
        scale = !setting.upscaling() ? "native" : DisplaySettings.get(DisplaySettings.DLAA_CUSTOM_SCALE) || standard == null ? "custom" : standard;
    }

    /** What Reflex does or will do, beside its setting; null while it is off. */
    private FrameLatency.Status reflexStatus() {
        if (reflex.equals("off")) {
            return null;
        }
        if (Util.getPlatform() != Util.OS.WINDOWS) {
            return new FrameLatency.Status("Needs Windows and an NVIDIA card.", "NVIDIA Reflex runs only on Windows, with an NVIDIA"
                    + " graphics card (GTX 900 or newer).", true);
        }
        if (!Presentation.active()) {
            return Presentation.requested()
                    ? new FrameLatency.Status("Can't run: Perfect Presentation failed.", "Perfect Presentation, which Reflex runs on,"
                            + " could not start this time; the game log says why (lines starting with Perfect Presentation:).", true)
                    : new FrameLatency.Status("Runs after you restart the game.", "Reflex runs on Perfect Presentation's DirectX"
                            + " device, and Perfect Presentation starts with the game: restart it once.", true);
        }
        FrameLatency.Status running = FrameLatency.status();
        return running == null || !reflex.equals(initial.get(DisplaySettings.REFLEX))
                ? new FrameLatency.Status("Starts when you close this screen.", null, false) : running;
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
            case "off" -> "Off (the pack's own)";
            default -> "Auto";
        });
    }

    private static String engineTooltip(String value) {
        return switch (value) {
            case "dlss" -> "Only NVIDIA DLSS (DLAA at full resolution): Windows, an NVIDIA RTX card and your own nvngx_dlss.dll.";
            case "fsr3" -> "Only AMD FSR 3.1: any graphics card with OpenGL 4.5, nothing to download.";
            case "off" -> "The shader pack's own anti-aliasing (in Photon: TAA, or TAAU when upscaling).";
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
                    + " the shader pack's own upscaling (Photon's TAAU) does it.";
        };
    }

    private static Component reflexLabel(String value) {
        return Component.literal(switch (value) {
            case "on" -> "On";
            case "boost" -> "On + Boost";
            default -> "Off";
        });
    }

    private static String reflexTooltip(String value) {
        return switch (value) {
            case "on" -> "Experimental, Windows and NVIDIA (GTX 900 or newer): the driver starts each frame as late as it can"
                    + " while keeping the graphics card busy, and the game reads the mouse just before it draws, to shorten the"
                    + " time from moving the mouse to seeing it. Needs Perfect Presentation, which this turns on (after a"
                    + " restart). Works best with a Max Framerate the card can hold, which Reflex then keeps.";
            case "boost" -> "Like On, and the graphics card keeps its clocks up even when it waits on the processor: a little"
                    + " less delay where the processor holds the frame rate back, for more power.";
            default -> "The game reads input and paces its frames as it always does.";
        };
    }

    private static Component pacingLabel(String value) {
        return Component.literal(switch (value) {
            case "on" -> "On";
            case "lowest" -> "Lowest";
            default -> "Off";
        });
    }

    private static String pacingTooltip(String value) {
        return switch (value) {
            case "on" -> "Any graphics card: the game waits just before drawing each frame, so the frame reaches the graphics"
                    + " card about when the card is ready for it instead of waiting in a queue behind earlier frames, while the"
                    + " mouse input in it ages. A short queue stays, so no frames are lost. For playing without a frame cap:"
                    + " a Max Framerate the card can hold already keeps the queue empty.";
            case "lowest" -> "Like On, with the queue as short as the frames' timing allows: a little less delay still, for"
                    + " about 6% fewer frames.";
            default -> "Frames queue on the graphics card as they always have.";
        };
    }

    private static Component fpsLabel(String value) {
        return Component.literal(switch (value) {
            case FpsOverlay.BASIC -> "Basic";
            case FpsOverlay.DETAILED -> "Detailed";
            default -> "Off";
        });
    }

    private static String fpsTooltip(String value) {
        return switch (value) {
            case FpsOverlay.BASIC -> "The frame rate, small, in a corner of the screen.";
            case FpsOverlay.DETAILED -> "The frame rate and frame time, the 1% and 0.1% lows of the last 10 seconds (how fast"
                    + " the slowest frames ran) and a graph of the last seconds' frame times: green, yellow for longer frames,"
                    + " red for stutters.";
            default -> "No frame counter (F3 still shows the frame rate).";
        };
    }

    private static Component cornerLabel(String value) {
        return Component.literal(switch (value) {
            case FpsOverlay.TOP_LEFT -> "Top left";
            case FpsOverlay.BOTTOM_LEFT -> "Bottom left";
            case FpsOverlay.BOTTOM_RIGHT -> "Bottom right";
            default -> "Top right";
        });
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
        choice.button = add(Button.builder(Component.empty(), button -> open = open == choice ? null : choice)
                .bounds(x, y, width, 20).build());
        choice.update();
        choices.add(choice);
        return choice;
    }

    /** A tab: the widgets init made for it, which the tab bar shows while it is open (laid out by init). */
    private static final class Page implements Tab {
        private final Component title;
        final List<AbstractWidget> widgets = new java.util.ArrayList<>();

        Page(String title) {
            this.title = Component.literal(title);
        }

        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public void visitChildren(java.util.function.Consumer<AbstractWidget> visitor) {
            widgets.forEach(visitor);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
        }
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
