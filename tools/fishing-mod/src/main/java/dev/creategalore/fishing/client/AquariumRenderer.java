package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.creategalore.fishing.aquarium.AquariumBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

/**
 * Draws a tank (from its controller): the water up to its level, as tinted water inside the glass, and every fish
 * swimming in it as its own 3D model ({@link FishView}) at its true length, moving as {@link AquariumSwim} says.
 */
final class AquariumRenderer implements BlockEntityRenderer<AquariumBlockEntity> {
    /** The water sits just inside the glass, so the two don't flicker against each other. */
    private static final float INSET = 0.02f;
    /** The sand floor's top. */
    private static final float FLOOR = 1 / 16f;

    AquariumRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AquariumBlockEntity tank, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        if (!tank.isController() || tank.getLevel() == null) return;
        float level = AquariumSwim.waterLevel(tank);
        if (level > FLOOR + 0.01f) water(tank, level, pose, buffers, light);
        AquariumSwim swim = AquariumSwim.of(tank);
        float clock = tank.getLevel().getGameTime() + partialTick;
        BlockPos origin = tank.getBlockPos();
        for (AquariumSwim.Fish f : swim.fish) {
            double x = Mth.lerp(partialTick, f.px, f.x), y = Mth.lerp(partialTick, f.py, f.y), z = Mth.lerp(partialTick, f.pz, f.z);
            double horizontal = Math.sqrt(f.vx * f.vx + f.vz * f.vz);
            float yaw = (float) Math.atan2(f.vz, f.vx);
            float pitch = (float) Mth.clamp(Math.atan2(f.vy, horizontal + 1e-4), -0.5, 0.5);
            int here = LevelRenderer.getLightColor(tank.getLevel(), origin.offset(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.YP.rotation(-yaw));
            pose.mulPose(Axis.ZP.rotation(pitch));
            FishView.draw(f.stack, AquariumSwim.length(tank, f.stack) * 100, 0, pose, buffers, here, clock * 1.5f + f.phase);
            pose.popPose();
        }
    }

    /** The water as a box of tinted water texture, one quad per block face so the texture tiles as in the world. */
    private static void water(AquariumBlockEntity tank, float top, PoseStack pose, MultiBufferSource buffers, int light) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.withDefaultNamespace("block/water_still"));
        int rgb = BiomeColors.getAverageWaterColor(tank.getLevel(), tank.getBlockPos());
        int color = 0xb0000000 | (rgb & 0xffffff);
        VertexConsumer v = buffers.getBuffer(RenderType.translucent());
        PoseStack.Pose p = pose.last();
        float x0 = INSET, z0 = INSET, x1 = tank.sizeX() - INSET, z1 = tank.sizeZ() - INSET;
        for (int i = 0; i < tank.sizeX(); i++) {
            for (int k = 0; k < tank.sizeZ(); k++) {
                float ax = Math.max(x0, i), bx = Math.min(x1, i + 1), az = Math.max(z0, k), bz = Math.min(z1, k + 1);
                quad(v, p, sprite, color, light, ax, top, az, ax, top, bz, bx, top, bz, bx, top, az, 0, 1, 0, 1, 1);
            }
        }
        for (int j = 0; j < Mth.ceil(top); j++) {
            float ay = Math.max(FLOOR, j), by = Math.min(top, j + 1);
            if (by <= ay) continue;
            float vh = by - ay;
            for (int i = 0; i < tank.sizeX(); i++) {
                float ax = Math.max(x0, i), bx = Math.min(x1, i + 1);
                quad(v, p, sprite, color, light, ax, ay, z0, ax, by, z0, bx, by, z0, bx, ay, z0, 0, 0, -1, 1, vh);
                quad(v, p, sprite, color, light, bx, ay, z1, bx, by, z1, ax, by, z1, ax, ay, z1, 0, 0, 1, 1, vh);
            }
            for (int k = 0; k < tank.sizeZ(); k++) {
                float az = Math.max(z0, k), bz = Math.min(z1, k + 1);
                quad(v, p, sprite, color, light, x0, ay, bz, x0, by, bz, x0, by, az, x0, ay, az, -1, 0, 0, 1, vh);
                quad(v, p, sprite, color, light, x1, ay, az, x1, by, az, x1, by, bz, x1, ay, bz, 1, 0, 0, 1, vh);
            }
        }
    }

    /** A four-corner quad with the sprite (its {@code uw} by {@code vh} part) stretched over it. */
    private static void quad(VertexConsumer v, PoseStack.Pose p, TextureAtlasSprite s, int color, int light,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz, float uw, float vh) {
        float u0 = s.getU0(), u1 = s.getU(uw), v0 = s.getV0(), v1 = s.getV(vh);
        v.addVertex(p, x1, y1, z1).setColor(color).setUv(u0, v1).setLight(light).setNormal(p, nx, ny, nz);
        v.addVertex(p, x2, y2, z2).setColor(color).setUv(u0, v0).setLight(light).setNormal(p, nx, ny, nz);
        v.addVertex(p, x3, y3, z3).setColor(color).setUv(u1, v0).setLight(light).setNormal(p, nx, ny, nz);
        v.addVertex(p, x4, y4, z4).setColor(color).setUv(u1, v1).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(AquariumBlockEntity tank) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(AquariumBlockEntity tank) {
        return tank.box();
    }
}
