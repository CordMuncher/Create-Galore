package dev.creategalore.fishing.client;

import dev.creategalore.fishing.CreateGaloreFishing;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Lines of text floating over a block like a name tag, each on a dark backing (the Cleaning Table's and the fish
 * mount's labels). They are drawn with the HUD, at the block's place on screen and the size a name tag would have
 * there, not in the world: shader packs redraw world text their own way, and every way of drawing it in the world
 * left the backing over the letters under one pack or another (Photon, Complementary).
 */
public final class FloatingLabel {
    /** A font pixel's size in the world, blocks (a name tag's 0.025, halved). */
    private static final float PIXEL = 0.0125f;
    private static final Map<BlockPos, Label> LABELS = new HashMap<>();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private record Label(Vec3 top, List<Component> lines) {}

    private FloatingLabel() {}

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerBelowAll(CreateGaloreFishing.id("floating_labels"), FloatingLabel::render));
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> LABELS.clear());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
            VIEW.set(event.getModelViewMatrix());
            PROJECTION.set(event.getProjectionMatrix());
            camera = event.getCamera().getPosition();
        });
    }

    /**
     * Shows the lines this frame, the top of the first line at {@code top}. Called from a block entity renderer, which
     * may run more than once a frame (a shader pack's shadow pass), so one label per block.
     */
    public static void show(BlockPos pos, Vec3 top, List<Component> lines) {
        LABELS.put(pos.immutable(), new Label(top, List.copyOf(lines)));
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (LABELS.isEmpty() || Minecraft.getInstance().options.hideGui) return;
        Font font = Minecraft.getInstance().font;
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255) << 24;
        for (Label label : LABELS.values()) {
            Vector4f at = new Vector4f((float) (label.top.x - camera.x), (float) (label.top.y - camera.y),
                    (float) (label.top.z - camera.z), 1);
            PROJECTION.transform(VIEW.transform(at));
            if (at.w < 0.05f) continue;
            float x = (at.x / at.w + 1) / 2 * g.guiWidth(), y = (1 - at.y / at.w) / 2 * g.guiHeight();
            float scale = PIXEL * PROJECTION.m11() / at.w * g.guiHeight() / 2;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            for (int i = 0; i < label.lines.size(); i++) {
                Component line = label.lines.get(i);
                int left = -font.width(line) / 2;
                g.fill(left - 1, i * 11 - 1, left + font.width(line), i * 11 + 9, background);
                g.drawString(font, line, left, i * 11, 0xffffffff, false);
            }
            g.pose().popPose();
        }
    }
}
