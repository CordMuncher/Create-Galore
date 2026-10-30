package dev.creategalore.bridge.dlss;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
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
import static org.lwjgl.opengl.GL30C.GL_R32F;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL32C.GL_ALREADY_SIGNALED;
import static org.lwjgl.opengl.GL32C.GL_CONDITION_SATISFIED;
import static org.lwjgl.opengl.GL32C.GL_SYNC_FLUSH_COMMANDS_BIT;
import static org.lwjgl.opengl.GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL32C.glClientWaitSync;
import static org.lwjgl.opengl.GL32C.glDeleteSync;
import static org.lwjgl.opengl.GL32C.glFenceSync;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL41C.glProgramUniform1f;
import static org.lwjgl.opengl.GL41C.glProgramUniform1i;
import static org.lwjgl.opengl.GL41C.glProgramUniform2f;
import static org.lwjgl.opengl.GL41C.glProgramUniform2i;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_ACCESS;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_FORMAT;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LAYER;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LAYERED;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LEVEL;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_NAME;
import static org.lwjgl.opengl.GL42C.GL_MAX_IMAGE_UNITS;
import static org.lwjgl.opengl.GL42C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL42C.glBindImageTexture;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BLOCK;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43C.glGetProgramResourceIndex;
import static org.lwjgl.opengl.GL43C.glShaderStorageBlockBinding;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For testing the first-person hand, which a player saw flash with DLSS a couple of seconds after stopping ("relit",
 * as if DLSS lost its history there): one CSV row per frame, logs/create-galore-hand-watch-*.csv, with what could
 * change on the hand from one frame to the next. On the CPU: the hand's draws, the light it was drawn with, how its
 * vertices matched last frame's ({@link EntityMotion}), how far its view matrix moved, and the shadow pass's draws of
 * the player and how far its model moved (the pack draws the player into its shadow map, so the player's own shadow
 * can fall on the hand). On the
 * GPU, from DLSS's inputs and its output: the hand's and the rest of the world's mean brightness, how much it changed
 * from last frame, and the hand's motion vectors and depth. The hand is where DLSS's depth (near over clip w) is above
 * {@link #HAND_DEPTH}: only the hand is that close. Read back a few frames late, so the frame rate stays as it is.
 *
 * Turned on by dlaaHandWatch=frames in the display settings (testing only, not written by the game) or the system
 * property create_galore_bridge.dlss.handWatch; it starts when the settings are read (a shader reload) and logs that
 * many frames in which DLSS, Ray Reconstruction or FSR 3 ran. tools/dlss-testkit/handwatch.py finds the flashes in it.
 */
public final class HandWatch {
    private static final Logger LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final String PROPERTY = "create_galore_bridge.dlss.handWatch";
    /** DLSS depth (near over clip w, near 0.05) above which a pixel is the hand: closer than 0.15 blocks. */
    private static final float HAND_DEPTH = 0.33f;
    /** Values per stage: see the shader. */
    private static final int N = 11;
    private static final int RING = 4;
    private static final int GROUP = 16;

    private static int framesLeft;
    private static String configured = "";
    private static BufferedWriter out;
    private static Path file;
    private static long seq;
    private static int written;

    // This frame's CPU side, from the start of the level frame.
    private static int handPasses;
    private static int handLight = -1;
    private static int handVerts;
    private static int handUnknown;
    private static float handMaxMove;
    private static int handLastVerts;
    private static float handViewDelta;
    private static boolean handViewKnown;
    private static final Matrix4f LAST_HAND_VIEW = new Matrix4f();
    private static boolean frameHandViewSet;
    private static int shadowPlayerDraws;
    /** Things drawn this frame that drew a different number of vertices last frame than the frame before. */
    private static int countChanges;
    private static int shadowPlayerVerts;
    /** The shadow pass's player vertices this frame and last (camera-relative, in drawing order) and how far one moved. */
    private static float[] shadowPlayerNow = new float[3 * 1024];
    private static float[] shadowPlayerLast = new float[3 * 1024];
    private static int shadowPlayerLastVerts;
    private static float shadowPlayerMove;
    private static double camMove;
    private static float camTurn;
    private static Vec3 lastCam;
    private static float lastYaw;
    private static float lastPitch;

    private static int program;
    private static int partialIn;
    private static int partialOut;
    private static int partialCapacity;
    private static int result;
    private static final int[] ring = new int[RING];
    private static final long[] fences = new long[RING];
    private static final String[] rows = new String[RING];
    private static int prevIn;
    private static int prevInW;
    private static int prevInH;
    private static int prevOut;
    private static int prevOutW;
    private static int prevOutH;

    private HandWatch() {}

    /** The display settings were read: starts a watch if dlaaHandWatch (or the system property) asks for one. */
    static void configure(String setting) {
        String value = setting == null || setting.isBlank() ? System.getProperty(PROPERTY, "") : setting.trim();
        if (value.equals(configured) && out != null) {
            return;
        }
        configured = value;
        int frames;
        try {
            frames = value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.warn("DLAA hand watch: {} is no number of frames", value);
            frames = 0;
        }
        finish();
        if (frames <= 0) {
            return;
        }
        try {
            Path logs = FMLPaths.GAMEDIR.get().resolve("logs");
            Files.createDirectories(logs);
            file = logs.resolve("create-galore-hand-watch-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
            out = Files.newBufferedWriter(file);
            out.write("seq,nanos,engine,gameTime,partialTick,dayTime,frameMs,reset,jitterX,jitterY,camMove,camTurn,"
                    + "handPasses,handLight,handVerts,handLastVerts,handUnknown,handMaxMove,handViewDelta,"
                    + "shadowPlayerDraws,shadowPlayerVerts,shadowPlayerMove,countChanges,"
                    + "inHandPx,inHandLuma,inHandDLuma,inHandMaxDLuma,inHandMv,inHandMaxMv,inHandDepth,inWorldPx,inWorldLuma,inWorldDLuma,inWorldMaxMv,"
                    + "outHandPx,outHandLuma,outHandDLuma,outHandMaxDLuma,outWorldPx,outWorldLuma,outWorldDLuma\n");
            framesLeft = frames;
            written = 0;
            LOG.info("DLAA hand watch: logging {} frames to {}", frames, file);
        } catch (IOException e) {
            LOG.warn("DLAA hand watch: could not start: {}", e.toString());
            out = null;
        }
    }

    static boolean on() {
        return out != null && framesLeft > 0;
    }

    // ---- CPU side, from EntityMotion ----

    static void beginFrame(Vec3 camera) {
        if (!on()) {
            return;
        }
        Camera main = Minecraft.getInstance().gameRenderer.getMainCamera();
        camMove = lastCam == null ? 0.0 : camera.distanceTo(lastCam);
        camTurn = lastCam == null ? 0.0f : Math.abs(main.getYRot() - lastYaw) + Math.abs(main.getXRot() - lastPitch);
        lastCam = camera;
        lastYaw = main.getYRot();
        lastPitch = main.getXRot();
        handPasses = 0;
        handLight = -1;
        handVerts = 0;
        handUnknown = 0;
        handMaxMove = 0.0f;
        handLastVerts = 0;
        shadowPlayerDraws = 0;
        countChanges = 0;
        float[] swap = shadowPlayerLast;
        shadowPlayerLast = shadowPlayerNow;
        shadowPlayerNow = swap;
        shadowPlayerLastVerts = shadowPlayerVerts;
        shadowPlayerVerts = 0;
        shadowPlayerMove = 0.0f;
        frameHandViewSet = false;
        handViewDelta = 0.0f;
    }

    static void handPass(int light, int lastCount) {
        if (!on()) {
            return;
        }
        handPasses++;
        handLight = light;
        handLastVerts = Math.max(handLastVerts, lastCount);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        if (!frameHandViewSet) {
            frameHandViewSet = true;
            if (handViewKnown) {
                float delta = 0.0f;
                for (int c = 0; c < 4; c++) {
                    for (int r = 0; r < 4; r++) {
                        delta = Math.max(delta, Math.abs(view.get(c, r) - LAST_HAND_VIEW.get(c, r)));
                    }
                }
                handViewDelta = delta;
            }
            LAST_HAND_VIEW.set(view);
            handViewKnown = true;
        }
    }

    /** A hand vertex was written: known (matched to last frame's) or not, and how far it moved in view space. */
    static void handVertex(boolean known, float move) {
        if (!on()) {
            return;
        }
        handVerts++;
        if (!known) {
            handUnknown++;
        } else {
            handMaxMove = Math.max(handMaxMove, move);
        }
    }

    static void countChange() {
        countChanges++;
    }

    static void shadowPlayerDraw() {
        if (on()) {
            shadowPlayerDraws++;
        }
    }

    /** A vertex of the player in the shadow pass, at x, y, z (relative to the camera, as written). */
    static void shadowPlayerVertex(float x, float y, float z) {
        if (!on()) {
            return;
        }
        int i = shadowPlayerVerts++;
        if (3 * i + 3 > shadowPlayerNow.length) {
            shadowPlayerNow = java.util.Arrays.copyOf(shadowPlayerNow, Math.max(2 * shadowPlayerNow.length, 3 * i + 3));
        }
        shadowPlayerNow[3 * i] = x;
        shadowPlayerNow[3 * i + 1] = y;
        shadowPlayerNow[3 * i + 2] = z;
        if (i < shadowPlayerLastVerts && 3 * i + 3 <= shadowPlayerLast.length) {
            float dx = x - shadowPlayerLast[3 * i];
            float dy = y - shadowPlayerLast[3 * i + 1];
            float dz = z - shadowPlayerLast[3 * i + 2];
            shadowPlayerMove = Math.max(shadowPlayerMove, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
    }

    // ---- GPU side, from Dlss after DLSS, Ray Reconstruction or FSR 3 ran ----

    /** {@code exposed}: the colour went in already times the exposure (Ray Reconstruction). */
    static void measure(DlssPass pass, String engine, boolean exposed, boolean reset, float jitterX, float jitterY, float frameMs) {
        if (!on()) {
            return;
        }
        try {
            harvest(false);
            int slot = (int) (seq % RING);
            if (fences[slot] != 0L) {
                harvest(true);
            }
            dispatch(pass, exposed);
            glCopyNamedBufferSubData(result, ring[slot], 0L, 0L, 2L * N * 4L);
            fences[slot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            Minecraft mc = Minecraft.getInstance();
            float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
            long gameTime = mc.level == null ? 0L : mc.level.getGameTime();
            float dayTime = mc.level == null ? 0.0f : mc.level.getTimeOfDay(partial);
            rows[slot] = String.format(Locale.ROOT, "%d,%d,%s,%d,%.4f,%.7f,%.3f,%d,%.4f,%.4f,%.6f,%.5f,%d,%d,%d,%d,%d,%.6f,%.7f,%d,%d,%.6f,%d",
                    seq, System.nanoTime(), engine, gameTime, partial, dayTime, frameMs, reset ? 1 : 0, jitterX, jitterY, camMove, camTurn,
                    handPasses, handLight, handVerts, handLastVerts, handUnknown, handMaxMove, handViewDelta,
                    shadowPlayerDraws, shadowPlayerVerts, shadowPlayerMove, countChanges);
            seq++;
            if (--framesLeft <= 0) {
                harvest(true);
                LOG.info("DLAA hand watch: {} frames written to {}", written, file);
                finish();
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("DLAA hand watch: stopped: {}", e.toString());
            finish();
        }
    }

    /** Writes the rows whose GPU values are back; {@code all} waits for every one still out. */
    private static void harvest(boolean all) throws IOException {
        for (int i = 0; i < RING; i++) {
            long oldest = seq - RING + i;
            if (oldest < 0) {
                continue;
            }
            int slot = (int) (oldest % RING);
            long fence = fences[slot];
            if (fence == 0L) {
                continue;
            }
            int status = glClientWaitSync(fence, all ? GL_SYNC_FLUSH_COMMANDS_BIT : 0, all ? 1_000_000_000L : 0L);
            if (status != GL_ALREADY_SIGNALED && status != GL_CONDITION_SATISFIED) {
                if (all) {
                    LOG.warn("DLAA hand watch: the GPU did not finish frame {} in a second", oldest);
                } else {
                    return; // in order: later ones are not done either
                }
            }
            glDeleteSync(fence);
            fences[slot] = 0L;
            float[] values = new float[2 * N];
            glGetNamedBufferSubData(ring[slot], 0L, values);
            StringBuilder row = new StringBuilder(rows[slot]);
            appendStage(row, values, 0, true);
            appendStage(row, values, N, false);
            out.write(row.append('\n').toString());
            written++;
            if (written % 120 == 0) {
                out.flush();
            }
        }
    }

    private static void appendStage(StringBuilder row, float[] v, int o, boolean input) {
        float handPx = v[o];
        float worldPx = v[o + 7];
        row.append(String.format(Locale.ROOT, ",%d,%.5f,%.5f,%.5f", (int) handPx, mean(v[o + 1], handPx), mean(v[o + 2], handPx), v[o + 3]));
        if (input) {
            row.append(String.format(Locale.ROOT, ",%.5f,%.5f,%.5f", mean(v[o + 4], handPx), v[o + 5], mean(v[o + 6], handPx)));
        }
        row.append(String.format(Locale.ROOT, ",%d,%.5f,%.5f", (int) worldPx, mean(v[o + 8], worldPx), mean(v[o + 9], worldPx)));
        if (input) {
            row.append(String.format(Locale.ROOT, ",%.5f", v[o + 10]));
        }
    }

    private static float mean(float sum, float count) {
        return count > 0.0f ? sum / count : 0.0f;
    }

    private static void dispatch(DlssPass pass, boolean exposed) {
        ensureProgram();
        int inW = pass.inputWidth();
        int inH = pass.inputHeight();
        int outW = pass.outputWidth();
        int outH = pass.outputHeight();
        if (prevIn == 0 || prevInW != inW || prevInH != inH) {
            prevIn = remake(prevIn, inW, inH);
            prevInW = inW;
            prevInH = inH;
        }
        if (prevOut == 0 || prevOutW != outW || prevOutH != outH) {
            prevOut = remake(prevOut, outW, outH);
            prevOutW = outW;
            prevOutH = outH;
        }
        int groupsIn = groups(inW) * groups(inH);
        int groupsOut = groups(outW) * groups(outH);
        int needed = Math.max(groupsIn, groupsOut);
        if (partialCapacity < needed) {
            if (partialIn != 0) {
                glDeleteBuffers(new int[] {partialIn, partialOut});
            }
            partialIn = glCreateBuffers();
            partialOut = glCreateBuffers();
            glNamedBufferStorage(partialIn, (long) needed * N * 4L, GL_DYNAMIC_STORAGE_BIT);
            glNamedBufferStorage(partialOut, (long) needed * N * 4L, GL_DYNAMIC_STORAGE_BIT);
            partialCapacity = needed;
        }

        int topUnit = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) - 1;
        int imageUnit = glGetInteger(GL_MAX_IMAGE_UNITS) - 1;
        int topBinding = glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) - 1;
        int[] textures = {pass.colorTexture(), pass.motionTexture(), pass.depthTexture(), pass.outputTexture(), pass.exposureTexture()};
        String[] samplers = {"colorTex", "motionTex", "depthTex", "outputTex", "exposureTex"};

        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int[] oldTextures = new int[textures.length];
        int[] oldSamplers = new int[textures.length];
        for (int i = 0; i < textures.length; i++) {
            glActiveTexture(GL_TEXTURE0 + topUnit - i);
            oldTextures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            oldSamplers[i] = glGetInteger(GL_SAMPLER_BINDING);
        }
        int oldImage = glGetIntegeri(GL_IMAGE_BINDING_NAME, imageUnit);
        int oldImageLevel = glGetIntegeri(GL_IMAGE_BINDING_LEVEL, imageUnit);
        boolean oldImageLayered = glGetIntegeri(GL_IMAGE_BINDING_LAYERED, imageUnit) != 0;
        int oldImageLayer = glGetIntegeri(GL_IMAGE_BINDING_LAYER, imageUnit);
        int oldImageAccess = glGetIntegeri(GL_IMAGE_BINDING_ACCESS, imageUnit);
        int oldImageFormat = glGetIntegeri(GL_IMAGE_BINDING_FORMAT, imageUnit);
        int oldBuffer0 = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, topBinding);
        int oldBuffer1 = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, topBinding - 1);
        int oldBuffer = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            glMemoryBarrier(GL_ALL_BARRIER_BITS);
            for (int i = 0; i < textures.length; i++) {
                glActiveTexture(GL_TEXTURE0 + topUnit - i);
                glBindTexture(GL_TEXTURE_2D, textures[i]);
                glBindSampler(topUnit - i, 0);
                glProgramUniform1i(program, glGetUniformLocation(program, samplers[i]), topUnit - i);
            }
            glProgramUniform1i(program, glGetUniformLocation(program, "prevImg"), imageUnit);
            glProgramUniform1f(program, glGetUniformLocation(program, "handDepth"), HAND_DEPTH);
            glProgramUniform1f(program, glGetUniformLocation(program, "useExposure"), exposed ? 0.0f : 1.0f);
            glProgramUniform2f(program, glGetUniformLocation(program, "toRender"), (float) inW / outW, (float) inH / outH);
            glUseProgram(program);
            // Stage 0: the inputs, per group into partialIn; stage 2 sums them into result[0..N).
            stage(0, inW, inH, imageUnit, prevIn, topBinding, partialIn, groups(inW), groups(inH));
            stage(1, outW, outH, imageUnit, prevOut, topBinding, partialOut, groups(outW), groups(outH));
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, topBinding - 1, result);
            finalStage(topBinding, partialIn, groupsIn, 0);
            finalStage(topBinding, partialOut, groupsOut, N);
            glMemoryBarrier(GL_ALL_BARRIER_BITS);
        } finally {
            glUseProgram(oldProgram);
            for (int i = textures.length - 1; i >= 0; i--) {
                glActiveTexture(GL_TEXTURE0 + topUnit - i);
                glBindSampler(topUnit - i, oldSamplers[i]);
                glBindTexture(GL_TEXTURE_2D, oldTextures[i]);
            }
            glActiveTexture(oldActive);
            glBindImageTexture(imageUnit, oldImage, oldImageLevel, oldImageLayered, oldImageLayer, oldImageAccess == 0 ? GL_READ_WRITE : oldImageAccess,
                    oldImageFormat == 0 ? GL_R32F : oldImageFormat);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, topBinding, oldBuffer0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, topBinding - 1, oldBuffer1);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, oldBuffer);
        }
    }

    private static void stage(int stage, int w, int h, int imageUnit, int prev, int binding, int partial, int gx, int gy) {
        glProgramUniform1i(program, glGetUniformLocation(program, "stage"), stage);
        glProgramUniform2i(program, glGetUniformLocation(program, "size"), w, h);
        glBindImageTexture(imageUnit, prev, 0, false, 0, GL_READ_WRITE, GL_R32F);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, partial);
        glDispatchCompute(gx, gy, 1);
    }

    private static void finalStage(int binding, int partial, int groups, int offset) {
        glMemoryBarrier(GL_ALL_BARRIER_BITS);
        glProgramUniform1i(program, glGetUniformLocation(program, "stage"), 2);
        glProgramUniform1i(program, glGetUniformLocation(program, "groups"), groups);
        glProgramUniform1i(program, glGetUniformLocation(program, "offset"), offset);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, partial);
        glDispatchCompute(1, 1, 1);
    }

    private static int groups(int size) {
        return (size + GROUP - 1) / GROUP;
    }

    private static int remake(int old, int w, int h) {
        if (old != 0) {
            glDeleteTextures(old);
        }
        int texture = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(texture, 1, GL_R32F, w, h);
        return texture;
    }

    private static void ensureProgram() {
        if (program != 0) {
            return;
        }
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, SHADER);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("hand watch shader: " + log);
        }
        int p = glCreateProgram();
        glAttachShader(p, shader);
        glLinkProgram(p);
        glDeleteShader(shader);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(p);
            glDeleteProgram(p);
            throw new IllegalStateException("hand watch program: " + log);
        }
        // The blocks' bindings: the top two, which the game and Iris leave alone (put back after each use anyway).
        int top = glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) - 1;
        glShaderStorageBlockBinding(p, glGetProgramResourceIndex(p, GL_SHADER_STORAGE_BLOCK, "Partials"), top);
        glShaderStorageBlockBinding(p, glGetProgramResourceIndex(p, GL_SHADER_STORAGE_BLOCK, "Result"), top - 1);
        program = p;
        result = glCreateBuffers();
        glNamedBufferStorage(result, 2L * N * 4L, GL_DYNAMIC_STORAGE_BIT);
        for (int i = 0; i < RING; i++) {
            ring[i] = glCreateBuffers();
            glNamedBufferStorage(ring[i], 2L * N * 4L, GL_DYNAMIC_STORAGE_BIT);
        }
    }

    private static void finish() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                LOG.warn("DLAA hand watch: {}", e.toString());
            }
            out = null;
        }
        framesLeft = 0;
        for (int i = 0; i < RING; i++) {
            if (fences[i] != 0L) {
                glDeleteSync(fences[i]);
                fences[i] = 0L;
            }
        }
        seq = 0;
        lastCam = null;
        handViewKnown = false;
    }

    /**
     * Stage 0 runs over DLSS's inputs (render size), stage 1 over its output (window size; a pixel is the hand where
     * the input under it is), each writing one set of sums per 16x16 group; stage 2 (one group) adds a stage's sets
     * up into result. Per set: 0 hand pixels, 1 their brightness (luminance times the exposure, at most 64), 2 its
     * change from last frame, 3 the largest change, 4 the motion vectors' length (render pixels), 5 the longest, 6 the
     * DLSS depth, 7 world pixels (not the hand, not the sky), 8 their brightness, 9 its change. Last frame's
     * brightness is kept in prevImg, one for each stage.
     */
    private static final String SHADER = """
            #version 450
            layout(local_size_x = 16, local_size_y = 16) in;
            uniform sampler2D colorTex;
            uniform sampler2D motionTex;
            uniform sampler2D depthTex;
            uniform sampler2D outputTex;
            uniform sampler2D exposureTex;
            layout(r32f) uniform image2D prevImg;
            layout(std430) buffer Partials { float partial[]; };
            layout(std430) buffer Result { float result[]; };
            uniform int stage;
            uniform ivec2 size;
            uniform vec2 toRender;
            uniform float handDepth;
            uniform float useExposure;
            uniform int groups;
            uniform int offset;
            #define N 11
            shared float sh[N][256];

            float luma(vec3 c) {
                float e = mix(1.0, texelFetch(exposureTex, ivec2(0), 0).r, useExposure);
                return clamp(dot(c, vec3(0.2126, 0.7152, 0.0722)) * e, 0.0, 64.0);
            }

            bool isMax(int i) { return i == 3 || i == 5 || i == 10; }

            void main() {
                uint t = gl_LocalInvocationIndex;
                float v[N];
                for (int i = 0; i < N; i++) v[i] = 0.0;
                if (stage == 2) {
                    for (int g = int(t); g < groups; g += 256) {
                        for (int i = 0; i < N; i++) {
                            float p = partial[g * N + i];
                            v[i] = isMax(i) ? max(v[i], p) : v[i] + p;
                        }
                    }
                } else {
                    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
                    if (all(lessThan(p, size))) {
                        ivec2 r = stage == 0 ? p : min(ivec2((vec2(p) + 0.5) * toRender), textureSize(depthTex, 0) - 1);
                        float d = texelFetch(depthTex, r, 0).r;
                        float l = luma(stage == 0 ? texelFetch(colorTex, p, 0).rgb : texelFetch(outputTex, p, 0).rgb);
                        float dl = abs(l - imageLoad(prevImg, p).r);
                        imageStore(prevImg, p, vec4(l));
                        if (d >= handDepth) {
                            v[0] = 1.0; v[1] = l; v[2] = dl; v[3] = dl;
                            if (stage == 0) {
                                float mv = length(texelFetch(motionTex, p, 0).rg);
                                v[4] = mv; v[5] = mv; v[6] = d;
                            }
                        } else if (d > 0.0) {
                            v[7] = 1.0; v[8] = l; v[9] = dl;
                            if (stage == 0) v[10] = length(texelFetch(motionTex, p, 0).rg);
                        }
                    }
                }
                for (int i = 0; i < N; i++) sh[i][t] = v[i];
                barrier();
                for (uint s = 128u; s > 0u; s >>= 1u) {
                    if (t < s) {
                        for (int i = 0; i < N; i++) {
                            sh[i][t] = isMax(i) ? max(sh[i][t], sh[i][t + s]) : sh[i][t] + sh[i][t + s];
                        }
                    }
                    barrier();
                }
                if (t == 0u) {
                    if (stage == 2) {
                        for (int i = 0; i < N; i++) result[offset + i] = sh[i][0];
                    } else {
                        uint g = gl_WorkGroupID.y * gl_NumWorkGroups.x + gl_WorkGroupID.x;
                        for (int i = 0; i < N; i++) partial[g * N + i] = sh[i][0];
                    }
                }
            }
            """;
}
