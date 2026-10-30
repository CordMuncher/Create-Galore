package dev.creategalore.bridge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlock;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.creategalore.bridge.mixin.AeroBurnerTimesAccessor;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The lit Aeronautics burner's flame under an Iris shader pack. Aeronautics paints the flame straight onto the screen
 * with a shader of its own, so a pack never sees it: Photon draws its picture from its own buffers and paints over the
 * flame's colour but keeps its depth, which left a flame-shaped hole showing Distant Horizons terrain, and with
 * Photon's render scale below 100% the hole sat off to the side at the wrong size. While a pack draws the level the
 * same flame (same shapes, palette, timing and posterized 32-pixel look) is worked out here into a small texture and
 * drawn as an ordinary block entity quad, so the pack lights, scales and sorts it like everything else. It carries the
 * pack's id for fire (soul fire for the soul variant), so packs that make fire glow make this flame glow too.
 * Without a pack nothing changes: Aeronautics draws it as before.
 */
public final class BurnerFlame {
    /** Cells across and up: Aeronautics posterizes the flame to 32, with x offset by half a cell, so 64 columns hold it exactly. */
    private static final int WIDTH = 64;
    private static final int HEIGHT = 32;
    private static final float RESOLUTION = 32.0F;
    private static final ResourceLocation PALETTE = ResourceLocation.fromNamespaceAndPath("aeronautics", "textures/effects/fire_palette.png");
    private static final long FORGET_NANOS = 5_000_000_000L;

    private static final Map<HotAirBurnerBlockEntity, Flame> FLAMES = new HashMap<>();
    private static int[][] palette;
    private static int nextId;
    private static long lastSweep;

    /** Set by the renderer for the burner it is drawing, read when it gets to the flame. */
    private static HotAirBurnerBlockEntity burner;
    private static MultiBufferSource buffers;
    private static float partialTicks;

    private BurnerFlame() {
    }

    public static void begin(HotAirBurnerBlockEntity be, MultiBufferSource buffer, float partial) {
        burner = be;
        buffers = buffer;
        partialTicks = partial;
    }

    /** Draws the flame through the pack, true when done (Aeronautics then skips its own draw). */
    public static boolean draw(PoseStack poseStack) {
        HotAirBurnerBlockEntity be = burner;
        MultiBufferSource buffer = buffers;
        burner = null;
        buffers = null;
        if (be == null || buffer == null || !NameTagDefer.inPackLevelPass()) {
            return false;
        }
        int[][] colours = palette();
        if (colours == null) {
            return false;
        }
        boolean soul = be.getBlockState().getValue(HotAirBurnerBlock.VARIANT) != HotAirBurnerBlock.Variant.FIRE;
        AeroBurnerTimesAccessor times = (AeroBurnerTimesAccessor) be;
        float time = (float) Mth.lerp(partialTicks, times.create_galore_bridge$lastRenderTime(), times.create_galore_bridge$renderTime())
                + be.getTimeOffset();
        float intensity = times.create_galore_bridge$flameIntensity(partialTicks);

        long now = System.nanoTime();
        Flame flame = FLAMES.computeIfAbsent(be, b -> new Flame());
        flame.lastUsed = now;
        flame.paint(time, intensity, colours[soul ? 1 : 0]);
        sweep(now);

        int previous = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        int fireId = fireId(soul ? Blocks.SOUL_FIRE.defaultBlockState() : Blocks.FIRE.defaultBlockState());
        if (fireId != -1) {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(fireId);
        }
        try {
            // The same 2x2 quad Aeronautics draws, both sides, at full brightness.
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(flame.location));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f m = pose.pose();
            vertex(vc, pose, m, 0.0F, 0.0F, 0.0F, 1.0F);
            vertex(vc, pose, m, 2.0F, 0.0F, 1.0F, 1.0F);
            vertex(vc, pose, m, 2.0F, 2.0F, 1.0F, 0.0F);
            vertex(vc, pose, m, 0.0F, 2.0F, 0.0F, 0.0F);
        } finally {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(previous);
        }
        return true;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v) {
        vc.addVertex(m, x, y, 0.0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    private static int fireId(BlockState state) {
        var ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        return ids == null ? -1 : ids.getOrDefault(state, -1);
    }

    /** Frees the textures of burners that haven't been drawn for a few seconds (unloaded, broken or out of sight). */
    private static void sweep(long now) {
        if (now - lastSweep < 1_000_000_000L) {
            return;
        }
        lastSweep = now;
        Iterator<Flame> it = FLAMES.values().iterator();
        while (it.hasNext()) {
            Flame flame = it.next();
            if (now - flame.lastUsed > FORGET_NANOS) {
                Minecraft.getInstance().getTextureManager().release(flame.location);
                it.remove();
            }
        }
    }

    /** Aeronautics' 7x2 fire palette as ABGR, fire on row 0 and soul fire on row 1. */
    private static int[][] palette() {
        if (palette == null) {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(PALETTE); NativeImage image = NativeImage.read(in)) {
                int[][] rows = new int[image.getHeight()][image.getWidth()];
                for (int y = 0; y < rows.length; y++) {
                    for (int x = 0; x < rows[y].length; x++) {
                        rows[y][x] = image.getPixelRGBA(x, y) | 0xFF000000;
                    }
                }
                palette = rows.length >= 2 ? rows : null;
            } catch (Exception e) {
                return null;
            }
        }
        return palette;
    }

    private static final class Flame {
        final DynamicTexture texture = new DynamicTexture(WIDTH, HEIGHT, true);
        final ResourceLocation location = ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "burner_flame/" + nextId++);
        long lastUsed;

        Flame() {
            Minecraft.getInstance().getTextureManager().register(location, texture);
        }

        void paint(float time, float intensity, int[] row) {
            NativeImage image = texture.getPixels();
            if (image == null) {
                return;
            }
            for (int r = 0; r < HEIGHT; r++) {
                for (int c = 0; c < WIDTH; c++) {
                    image.setPixelRGBA(c, r, BurnerFlameShape.cell(Mth.floor((c + 1.5F) / 2.0F) / RESOLUTION, (HEIGHT - 1 - r) / RESOLUTION, time, intensity, row));
                }
            }
            texture.upload();
        }
    }
}
