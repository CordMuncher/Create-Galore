/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.particles;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.util.function.BiConsumer;

public class MagicChain extends TextureSheetParticle {
    private static final float OFFSET = 1.0f;
    private static final float MOVE_DST = 2.0f;
    private static final float MOVE_SPEED = 6f;

    private final BiConsumer<Quaternionf, Camera> facingCameraFunc;

    protected MagicChain(ClientLevel level, double x, double y, double z, SpriteSet sprites, float quadSize, float rotation) {
        super(level, x, y, z, 0, 0, 0);
        this.lifetime = 18;
        this.hasPhysics = false;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.quadSize = quadSize;
        this.setSpriteFromAge(sprites);
        this.facingCameraFunc = (q, cam) -> q.set(cam.rotation()).mul(Axis.ZP.rotationDegrees(rotation));
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) this.age / this.lifetime;
        this.alpha = 1f - t;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return (q, cam, f) -> facingCameraFunc.accept(q, cam);
    }

    @Override
    public void render(VertexConsumer buffer, Camera cam, float partialTicks) {
        Vec3 camPos = cam.getPosition();
        float x = (float)(Mth.lerp(partialTicks, this.xo, this.x) - camPos.x());
        float y = (float)(Mth.lerp(partialTicks, this.yo, this.y) - camPos.y());
        float z = (float)(Mth.lerp(partialTicks, this.zo, this.z) - camPos.z());
        Quaternionf q = new Quaternionf();
        facingCameraFunc.accept(q, cam);
        if (this.roll != 0.0F) q.rotateZ(Mth.lerp(partialTicks, this.oRoll, this.roll));

        this.renderRotatedQuad(buffer, q, x, y, z, partialTicks);
    }

    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf quaternion,
                                     float x, float y, float z, float partialTicks) {
        float size = this.getQuadSize(partialTicks);
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int color = this.getLightColor(partialTicks);
        float moveT = Mth.clamp(((float) this.age + partialTicks) * MOVE_SPEED / this.lifetime, 0f, 1.0f);
        float yOffset = OFFSET;
        float vOffset = MOVE_DST * moveT;
        final int order = 1;
        this.renderVertex(buffer, quaternion, x, y, z, 1f * order, 1f + yOffset - vOffset, size, u1, v1, color);
        this.renderVertex(buffer, quaternion, x, y, z, 1f * order, 1f + yOffset, size, u1, Mth.lerp(moveT, v1, v0), color);
        this.renderVertex(buffer, quaternion, x, y, z, -1f * order, 1f + yOffset, size, u0, Mth.lerp(moveT, v1, v0), color);
        this.renderVertex(buffer, quaternion, x, y, z, -1f * order, 1f + yOffset - vOffset, size, u0, v1, color);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type,
                                                ClientLevel level,
                                                double x, double y, double z,
                                                double vx, double vy, double vz) {
            return new MagicChain(level, x, y, z, sprites,
                    1.0f + level.random.nextFloat() * 0.6f,
                    (level.random.nextFloat() - 0.5f) * 200f);
        }
    }

    /** Vanilla's SingleQuadParticle.renderVertex (private there). */
    private void renderVertex(VertexConsumer buffer, Quaternionf quaternion, float x, float y, float z, float xOffset,
                              float yOffset, float quadSize, float u, float v, int packedLight) {
        Vector3f corner = new Vector3f(xOffset, yOffset, 0.0F).rotate(quaternion).mul(quadSize).add(x, y, z);
        buffer.addVertex(corner.x(), corner.y(), corner.z()).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(packedLight);
    }
}
