package dev.creategalore.bridge.hud;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glColorMaski;
import static org.lwjgl.opengl.GL30C.glDisablei;
import static org.lwjgl.opengl.GL30C.glEnablei;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glGetBooleani_v;
import static org.lwjgl.opengl.GL30C.glIsEnabledi;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL41C.glProgramUniform1i;
import static org.lwjgl.opengl.GL43C.glCopyImageSubData;
import static org.lwjgl.opengl.GL45C.GL_TEXTURE_INTERNAL_FORMAT;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glClearNamedFramebufferfv;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glDeleteTextures;
import static org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.creategalore.bridge.DisplaySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the HUD apart from the world it is drawn over, for passes that need both: each frame a copy of the picture
 * just before the HUD ("hudless": the world with its full-screen effects, nausea and the vignette, but no HUD) and,
 * in the frame's alpha channel, how much the HUD covers each pixel (0 where the world shows through, 1 under solid
 * HUD). The picture on screen doesn't change, only its alpha channel, which nothing shows (the copy to the window
 * and Perfect Presentation's swap chain ignore alpha; screenshots set it to opaque).
 *
 * The HUD's own colour then follows exactly, premultiplied: ui = frame - (1 - alpha) * hudless, so that
 * frame = ui + (1 - alpha) * hudless. Both "hudless + UI alpha" and "UI colour and alpha" come from the two.
 *
 * Where the copy is taken: in Gui.renderCameraOverlays just after the vignette, the first thing the HUD draws (with
 * Fast graphics, which has no vignette, as the camera overlays start). Fallbacks if that layer didn't run: before the
 * next HUD layer, else after the HUD. Then alpha is cleared, and until the frame goes to the window the HUD's blends
 * add their coverage to alpha (see {@link HudSplitBlend}): the rest of the HUD, mod layers, the frame counter, open
 * screens and toasts. A full-screen effect during the HUD (the blur behind the pause menu) makes the whole frame HUD.
 * ImmediatelyFast's HUD batching holds draws back, so the copy first draws what it holds.
 *
 * Testing (config/create_galore_bridge-display.properties, read again on F8): hudSplit=true turns it on;
 * hudSplitView=hudless, alpha, ui or marks shows that instead of the game (ui over a grey checkerboard; marks: magenta
 * where the HUD changed the picture with no coverage, yellow where the HUD darkened it, cyan where it added light);
 * hudSplitCheck=true compares them every 5 seconds ("HUD split check" log lines, pictures in screenshots/hud-split).
 * Either of the two turns it on too.
 */
public final class HudSplit {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final long CHECK_EVERY = 5_000_000_000L;
    private static final List<String> VIEWS = List.of("off", "hudless", "alpha", "ui", "marks");

    private static int generation = -1;
    private static boolean enabled;
    /** Index into VIEWS. */
    private static int view;
    private static boolean check;
    /** No OpenGL 4.5 (or the view's shader failed): off until a restart. */
    private static boolean unavailable;

    private static boolean inFrame;
    private static boolean taken;
    private static String takenAt;
    private static boolean seenCameraOverlays;
    private static int frames;
    /** The first frame's HUD layers in drawing order, logged once. */
    private static List<String> layers;
    private static boolean layersLogged;
    private static final Set<String> placesLogged = new HashSet<>();
    private static boolean hookWarned;

    /** The main target's colour texture the copies were made for (the game makes a new one when the window resizes). */
    private static int sourceTexture;
    private static int hudless;
    private static int scratch;
    private static int width;
    private static int height;
    private static int format;
    private static int program;
    private static int vao;
    private static long nextCheck;

    private HudSplit() {}

    private static void readSettings() {
        generation = DisplaySettings.generation();
        String v = DisplaySettings.getString(DisplaySettings.HUD_SPLIT_VIEW, "off").toLowerCase(Locale.ROOT);
        view = Math.max(0, VIEWS.indexOf(v.equals("leaks") ? "marks" : v));
        check = DisplaySettings.get(DisplaySettings.HUD_SPLIT_CHECK);
        boolean was = enabled;
        enabled = DisplaySettings.get(DisplaySettings.HUD_SPLIT) || view != 0 || check;
        if (enabled && !GL.getCapabilities().OpenGL45) {
            if (!unavailable) {
                LOG.warn("HUD split: needs OpenGL 4.5, which this driver doesn't have; off");
            }
            unavailable = true;
        }
        if (enabled != was || enabled) {
            LOG.info("HUD split: {}{}{}", enabled && !unavailable ? "on" : "off",
                    view != 0 ? ", view " + VIEWS.get(view) : "", check ? ", checks every 5 s" : "");
        }
        nextCheck = 0L;
    }

    /** Gui.render starts: this frame's HUD. */
    public static void hudStart() {
        if (generation != DisplaySettings.generation()) {
            readSettings();
        }
        inFrame = enabled && !unavailable;
        if (!inFrame) {
            return;
        }
        taken = false;
        takenAt = null;
        seenCameraOverlays = false;
        frames++;
        if (!layersLogged) {
            layers = new ArrayList<>();
        }
    }

    /** Gui.renderCameraOverlays starts. With Fast graphics there is no vignette, so the copy is taken here. */
    public static void cameraOverlaysStart(GuiGraphics graphics) {
        if (inFrame && !taken && !Minecraft.useFancyGraphics()) {
            take(graphics, "as the camera overlays start (Fast graphics, no vignette)");
        }
    }

    /** Just after Gui.renderVignette. */
    public static void afterVignette(GuiGraphics graphics) {
        if (inFrame && !taken) {
            take(graphics, "after the vignette");
        }
    }

    /** RenderGuiLayerEvent.Pre, first of all listeners. */
    public static void beforeLayer(RenderGuiLayerEvent.Pre event) {
        if (!inFrame) {
            return;
        }
        ResourceLocation name = event.getName();
        if (layers != null) {
            layers.add(name.toString());
        }
        if (taken) {
            return;
        }
        if (name.equals(VanillaGuiLayers.CAMERA_OVERLAYS)) {
            seenCameraOverlays = true;
        } else if (seenCameraOverlays) {
            take(event.getGuiGraphics(), "before the HUD layer " + name);
        }
    }

    /** Gui.render returns. */
    public static void hudEnd(GuiGraphics graphics) {
        if (!inFrame) {
            return;
        }
        if (!taken) {
            take(graphics, "after the HUD");
        }
        if (layers != null) {
            int camera = layers.indexOf(VanillaGuiLayers.CAMERA_OVERLAYS.toString());
            LOG.info("HUD split: {} HUD layers; drawn before the camera overlays (so in the world copy): {}", layers.size(),
                    camera > 0 ? layers.subList(0, camera) : "none");
            layersLogged = true;
            layers = null;
        }
    }

    /** Minecraft.runTick, just before the frame goes to the window. */
    public static void frameEnd() {
        HudSplitBlend.end();
        if (!inFrame) {
            return;
        }
        inFrame = false;
        if (!HudSplitBlend.seen && frames > 10 && !hookWarned) {
            hookWarned = true;
            LOG.warn("HUD split: the blend hook isn't in (HudSplitBlendMixin), so alpha won't record the HUD");
        }
        if (!taken) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width != width || main.height != height) {
            return; // resized during the frame
        }
        if (check) {
            long now = System.nanoTime();
            if (now - nextCheck >= 0L) {
                nextCheck = now + CHECK_EVERY;
                HudSplitCheck.run(main.getColorTextureId(), hudless, width, height, takenAt);
            }
        }
        if (view != 0) {
            drawView(main);
        }
    }

    private static void take(GuiGraphics graphics, String place) {
        graphics.flush();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        ensureTextures(main);
        glCopyImageSubData(main.getColorTextureId(), GL_TEXTURE_2D, 0, 0, 0, 0, hudless, GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
        setAlpha(main.frameBufferId, 0.0f);
        HudSplitBlend.begin();
        taken = true;
        takenAt = place;
        if (placesLogged.add(place)) {
            LOG.info("HUD split: world copy taken {}", place);
        }
    }

    private static void ensureTextures(RenderTarget main) {
        int source = main.getColorTextureId();
        if (hudless != 0 && source == sourceTexture && main.width == width && main.height == height) {
            return;
        }
        sourceTexture = source;
        int sourceFormat = glGetTextureLevelParameteri(source, 0, GL_TEXTURE_INTERNAL_FORMAT);
        if (hudless != 0 && main.width == width && main.height == height && sourceFormat == format) {
            return;
        }
        if (hudless != 0) {
            glDeleteTextures(hudless);
            glDeleteTextures(scratch);
        }
        width = main.width;
        height = main.height;
        format = sourceFormat;
        hudless = texture();
        scratch = texture();
        LOG.info("HUD split: {}x{}, format 0x{}", width, height, Integer.toHexString(format));
    }

    private static int texture() {
        int texture = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(texture, 1, format, width, height);
        glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }

    /**
     * PostChain.process returns. One that runs during the HUD is a full-screen effect over everything so far (the
     * blurred background of the pause menu and other screens): the whole frame becomes HUD.
     */
    public static void afterPostChain(String name) {
        if (inFrame && taken) {
            setAlpha(Minecraft.getInstance().getMainRenderTarget().frameBufferId, 1.0f);
            if (placesLogged.add("post " + name)) {
                LOG.info("HUD split: full-screen effect {} during the HUD: whole frame counts as HUD", name);
            }
        }
    }

    /** Sets the main target's alpha and nothing else, leaving OpenGL's state as it was (GlStateManager's copy stays true). */
    private static void setAlpha(int framebuffer, float alpha) {
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        try (MemoryStack stack = stackPush()) {
            ByteBuffer mask = stack.malloc(4);
            glGetBooleani_v(GL_COLOR_WRITEMASK, 0, mask);
            glDisable(GL_SCISSOR_TEST);
            glColorMaski(0, false, false, false, true);
            glClearNamedFramebufferfv(framebuffer, GL_COLOR, 0, stack.floats(0.0f, 0.0f, 0.0f, alpha));
            glColorMaski(0, mask.get(0) == GL_TRUE, mask.get(1) == GL_TRUE, mask.get(2) == GL_TRUE, mask.get(3) == GL_TRUE);
        }
        if (scissor) {
            glEnable(GL_SCISSOR_TEST);
        }
    }

    /** Replaces the frame's colour with the chosen view (alpha stays), then puts OpenGL's state back. */
    private static void drawView(RenderTarget main) {
        if (program == 0 && !makeProgram()) {
            return;
        }
        glCopyImageSubData(main.getColorTextureId(), GL_TEXTURE_2D, 0, 0, 0, 0, scratch, GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
        int top = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) - 1;
        int[] units = {top, top - 1};
        int[] textures = {scratch, hudless};
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int[] oldTextures = new int[2];
        int[] oldSamplers = new int[2];
        for (int i = 0; i < 2; i++) {
            glActiveTexture(GL_TEXTURE0 + units[i]);
            oldTextures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            oldSamplers[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        glActiveTexture(oldActive);
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean stencilTest = glIsEnabled(GL_STENCIL_TEST);
        boolean scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        boolean cullFace = glIsEnabled(GL_CULL_FACE);
        boolean blend = glIsEnabledi(GL_BLEND, 0);
        try (MemoryStack stack = stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            ByteBuffer mask = stack.malloc(4);
            glGetBooleani_v(GL_COLOR_WRITEMASK, 0, mask);
            try {
                for (int i = 0; i < 2; i++) {
                    glBindTextureUnit(units[i], textures[i]);
                    glBindSampler(units[i], 0);
                }
                glProgramUniform1i(program, glGetUniformLocation(program, "cg_frame"), units[0]);
                glProgramUniform1i(program, glGetUniformLocation(program, "cg_hudless"), units[1]);
                glProgramUniform1i(program, glGetUniformLocation(program, "cg_view"), view);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, main.frameBufferId);
                glViewport(0, 0, width, height);
                glDisable(GL_DEPTH_TEST);
                glDisable(GL_STENCIL_TEST);
                glDisable(GL_SCISSOR_TEST);
                glDisable(GL_CULL_FACE);
                glDisablei(GL_BLEND, 0);
                glColorMaski(0, true, true, true, false);
                glUseProgram(program);
                glBindVertexArray(vao);
                glDrawArrays(GL_TRIANGLES, 0, 3);
            } finally {
                glBindVertexArray(oldVao);
                glUseProgram(oldProgram);
                glColorMaski(0, mask.get(0) == GL_TRUE, mask.get(1) == GL_TRUE, mask.get(2) == GL_TRUE, mask.get(3) == GL_TRUE);
                if (blend) {
                    glEnablei(GL_BLEND, 0);
                }
                setEnabled(GL_DEPTH_TEST, depthTest);
                setEnabled(GL_STENCIL_TEST, stencilTest);
                setEnabled(GL_SCISSOR_TEST, scissorTest);
                setEnabled(GL_CULL_FACE, cullFace);
                glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldFramebuffer);
                for (int i = 0; i < 2; i++) {
                    glActiveTexture(GL_TEXTURE0 + units[i]);
                    glBindSampler(units[i], oldSamplers[i]);
                    glBindTexture(GL_TEXTURE_2D, oldTextures[i]);
                }
                glActiveTexture(oldActive);
            }
        }
    }

    private static void setEnabled(int cap, boolean on) {
        if (on) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }

    private static boolean makeProgram() {
        try {
            program = link(VERTEX, FRAGMENT);
            vao = glGenVertexArrays();
            return true;
        } catch (IllegalStateException e) {
            LOG.warn("HUD split: no views: {}", e.getMessage());
            view = 0;
            return false;
        }
    }

    // One triangle that covers the screen.
    private static final String VERTEX = """
            #version 330 core
            void main() {
                vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    // Tolerances as HudSplitCheck's: a frame that differs from the copy by more than 1 of 255 where alpha is 0, and the
    // HUD colour more than 2.5 of 255 below 0 or above alpha (rounding of 8-bit alpha and colour stays inside those).
    private static final String FRAGMENT = """
            #version 330 core
            uniform sampler2D cg_frame;
            uniform sampler2D cg_hudless;
            uniform int cg_view;
            out vec4 cg_out;
            void main() {
                ivec2 p = ivec2(gl_FragCoord.xy);
                vec4 f = texelFetch(cg_frame, p, 0);
                vec3 h = texelFetch(cg_hudless, p, 0).rgb;
                float a = f.a;
                vec3 ui = f.rgb - (1.0 - a) * h;
                vec3 c;
                if (cg_view == 1) {
                    c = h;
                } else if (cg_view == 2) {
                    c = vec3(a);
                } else if (cg_view == 3) {
                    float grey = ((p.x >> 4) + (p.y >> 4)) % 2 == 0 ? 0.3 : 0.5;
                    c = ui + (1.0 - a) * vec3(grey);
                } else {
                    float tolerance = 2.5 / 255.0;
                    c = f.rgb * 0.35;
                    if (any(greaterThan(ui, vec3(a + tolerance)))) c = vec3(0.0, 1.0, 1.0);
                    if (any(lessThan(ui, vec3(-tolerance)))) c = vec3(1.0, 1.0, 0.0);
                    if (a < 0.5 / 255.0 && any(greaterThan(abs(f.rgb - h), vec3(1.5 / 255.0)))) c = vec3(1.0, 0.0, 1.0);
                }
                cg_out = vec4(c, a);
            }
            """;

    private static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
        int linked = glCreateProgram();
        glAttachShader(linked, vertex);
        glAttachShader(linked, fragment);
        glLinkProgram(linked);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(linked, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(linked);
            glDeleteProgram(linked);
            throw new IllegalStateException("view program did not link: " + log);
        }
        return linked;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("view shader did not compile: " + log);
        }
        return shader;
    }
}
