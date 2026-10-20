package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.tackle.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the cast rig and the line back to the rod tip. The line sags when slack and pulls straight as the tension
 * rises. (The rod itself, bending under load, is drawn by the rod's item renderer.)
 */
public class CastHookRenderer extends EntityRenderer<CastHook> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType BOBBER = RenderType.entityCutout(TEXTURE);

    public CastHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CastHook hook, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Player owner = hook.getPlayerOwner();
        if (owner == null) return;
        pose.pushPose();
        if (hook.fishId() == null && hook.rig() != CastHook.Rig.LURE) {
            pose.pushPose();
            pose.scale(0.5f, 0.5f, 0.5f);
            if (hook.phase() == CastHook.Phase.BITE) pose.translate(0, -0.25 * Math.abs(Math.sin(hook.tickCount * 0.9)), 0);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            PoseStack.Pose last = pose.last();
            VertexConsumer vc = buffers.getBuffer(BOBBER);
            vertex(vc, last, light, 0, 0, 0, 1);
            vertex(vc, last, light, 1, 0, 1, 1);
            vertex(vc, last, light, 1, 1, 1, 0);
            vertex(vc, last, light, 0, 1, 0, 0);
            pose.popPose();
        }
        Vec3 tip = RodTip.position(owner, hook.hand(), partialTick, entityRenderDispatcher);
        Vec3 at = hook.getPosition(partialTick).add(0, 0.25, 0);
        float dx = (float) (tip.x - at.x);
        float dy = (float) (tip.y - at.y);
        float dz = (float) (tip.z - at.z);
        float sag = hook.phase() == CastHook.Phase.FIGHTING ? Mth.clamp(1 - hook.tension() * 1.5f, 0.02f, 1) : 1;
        VertexConsumer line = buffers.getBuffer(RenderType.lineStrip());
        PoseStack.Pose last = pose.last();
        for (int i = 0; i <= 16; i++) stringVertex(dx, dy, dz, sag, line, last, i / 16f, (i + 1) / 16f);
        pose.popPose();
        super.render(hook, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        vc.addVertex(pose, x - 0.5f, y - 0.5f, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 1, 0);
    }

    /** One point of the line, as vanilla draws it but with the droop scaled by {@code sag}. */
    private static void stringVertex(float x, float y, float z, float sag, VertexConsumer vc, PoseStack.Pose pose, float t, float next) {
        float px = x * t;
        float py = y * (t * t + t) * 0.5f * sag + y * t * (1 - sag) + 0.25f;
        float pz = z * t;
        float nx = x * next - px;
        float ny = y * (next * next + next) * 0.5f * sag + y * next * (1 - sag) + 0.25f - py;
        float nz = z * next - pz;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        vc.addVertex(pose, px, py, pz).setColor(0xFF000000).setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(CastHook hook) {
        return TEXTURE;
    }

    /** Where the rod tip is for drawing: in front of the first-person hand, or at the third-person hand. */
    public static final class RodTip {
        private RodTip() {
        }

        public static Vec3 position(Player player, net.minecraft.world.InteractionHand hand, float partialTick,
                                    net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher) {
            int side = (player.getMainArm() == HumanoidArm.RIGHT) == (hand == net.minecraft.world.InteractionHand.MAIN_HAND) ? 1 : -1;
            if (!(player.getItemInHand(hand).getItem() instanceof RodItem)) side = -side;
            float swing = player.getAttackAnim(partialTick);
            float swingAngle = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
            if (dispatcher.options.getCameraType().isFirstPerson() && player == Minecraft.getInstance().player) {
                double fovScale = 960.0 / dispatcher.options.fov().get();
                Vec3 offset = dispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525f, -0.1f).scale(fovScale)
                        .yRot(swingAngle * 0.5f).xRot(-swingAngle * 0.7f);
                return player.getEyePosition(partialTick).add(offset);
            }
            float body = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * (float) (Math.PI / 180);
            double sin = Mth.sin(body);
            double cos = Mth.cos(body);
            float scale = player.getScale();
            double across = side * 0.35 * scale;
            double ahead = 0.8 * scale;
            float crouch = player.isCrouching() ? -0.1875f : 0;
            return player.getEyePosition(partialTick).add(-cos * across - sin * ahead, crouch - 0.45 * scale,
                    -sin * across + cos * ahead);
        }
    }
}
