package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.tackle.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
        if (hook.fishId() != null && hook.phase() == CastHook.Phase.FIGHTING) drawFish(hook, owner, partialTick, pose, buffers, light);
        Vec3 tip = RodItemRenderer.tip(owner, hook.hand(), partialTick)
                .orElseGet(() -> RodTip.position(owner, hook.hand(), partialTick, entityRenderDispatcher));
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

    /**
     * The hooked fish at the end of the line, its own model at its own length: facing away from the angler (it's
     * pulling), swimming hard, rolling a little with the strain, and leaping clear of the water when it jumps.
     */
    private static void drawFish(CastHook hook, Player owner, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                 int light) {
        ItemStack fish = new ItemStack(BuiltInRegistries.ITEM.get(hook.fishId()));
        if (fish.isEmpty()) return;
        double cm = hook.fishLengthCm() > 0 ? hook.fishLengthCm() : 30;
        float clock = hook.tickCount + partialTick;
        Vec3 away = hook.getPosition(partialTick).subtract(owner.getPosition(partialTick));
        float yaw = (float) Math.atan2(-away.z, away.x);
        float since = clock - hook.jumpedAt;
        float leap = since < 14 ? Mth.sin(since / 14 * Mth.PI) : 0;
        // Just under the surface, where it can be seen through the water; close in it comes up and wallows.
        double near = Mth.clamp((away.horizontalDistance() - 2.5) / 4, 0, 1);
        float wallow = (float) (1 - near);
        pose.pushPose();
        pose.translate(0, -0.08 - 0.1 * near + leap * (0.6 + cm / 200), 0);
        pose.mulPose(Axis.YP.rotation(yaw + Mth.sin(clock * 0.35f) * (0.25f + 0.35f * wallow)));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(clock * 0.5f) * 35 * wallow));
        // Nose up on the way out of a jump, down on the way back in.
        if (leap > 0) pose.mulPose(Axis.ZP.rotationDegrees((since < 7 ? 1 : -1) * 35 * leap));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(clock * 0.2f) * 10 * hook.tension() / Math.max(0.1f, hook.breakingLoad())));
        FishView.draw(fish, cm, hook.fishKg(), pose, buffers, light, clock * 2.5f);
        pose.popPose();
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

    /**
     * Where the rod tip is, estimated, for when the rod wasn't drawn this frame (out of view, another mod's hand
     * renderer): in front of the first-person hand, or at the third-person hand.
     */
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
