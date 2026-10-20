package dev.creategalore.bridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.creategalore.bridge.guideai.GuideAiNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** The outline of the box an operator is about to mark as built (gold) or unmark (red), until they confirm. */
public final class ClaimBoxView {
    private static GuideAiNet.ClaimBox box;
    private static long until;

    private ClaimBoxView() {
    }

    public static void show(GuideAiNet.ClaimBox payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            box = payload.ticks() > 0 ? payload : null;
            until = mc.level == null ? 0 : mc.level.getGameTime() + payload.ticks();
        });
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || box == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() > until) {
            box = null;
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        AABB aabb = new AABB(box.min().getX(), box.min().getY(), box.min().getZ(), box.max().getX() + 1, box.max().getY() + 1,
                box.max().getZ() + 1).inflate(0.01);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float r = box.add() ? 1f : 0.95f, g = box.add() ? 0.75f : 0.2f, b = box.add() ? 0.15f : 0.2f;
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), aabb, r, g, b, 1f);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
