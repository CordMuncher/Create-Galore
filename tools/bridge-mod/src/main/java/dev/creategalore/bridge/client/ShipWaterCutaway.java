package dev.creategalore.bridge.client;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL32C;

/**
 * Sable hides the sea inside a ship's hull by drawing the hull's cells twice before the water: their nearest front
 * faces and their nearest back faces, and the water shader drops any water between the two. That is right only while
 * the line of sight stays inside the hull from the first face on. Create Deep Seas leaves the parts of the hull well
 * above the water out of those cells (railings, fences, anything not next to the sea), so the shape has notches above
 * the deck: a line of sight that dips into a notch's cell, out through its open side and back into the hold ended at
 * the first back face, and the sea surface in the hold drew in a wedge seen only from that spot (Emre's motor launch,
 * looking into the aft cockpit over the stern rail, or along the bow deck from the bow).
 * <p>
 * The sea's only surface there is its top, so ask the question at the water instead: where the line of sight crosses
 * the water's surface under the ship, is it inside the hull? It is when the last hull face in front of that point is a
 * front face. Then the water to drop lies between that face and the first back face past the surface. Faces are kept
 * or dropped by which side of the surface they are on (ship_water_side), and the last front and back faces before it
 * are drawn into two depth buffers, then turned into Sable's close buffer (ship_water_close). Sable's far buffer gets
 * the first back face past the surface.
 * <p>
 * From a camera inside a hull (a cabin, a hold above the water) the same notches broke Sable's own pass: it starts
 * every line of sight inside and hides water only up to the first back face, so a line of sight out through a doorway
 * (doors and the walls well above the water are left out of the cells too) and back in over the deck or into the next
 * room showed the sea there, from some angles only. There the line of sight starts inside, as if behind a front face
 * at the camera, and the same question is asked at the water. Only a camera inside a submerged hold keeps Sable's own
 * pass, since Create Deep Seas' underwater fog in there reads Sable's far buffer as the hull around the camera.
 */
public final class ShipWaterCutaway {
    private static final ResourceLocation SIDE_ID = ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "ship_water_side");
    private static final ResourceLocation CLOSE_ID = ResourceLocation.fromNamespaceAndPath("create_galore_bridge", "ship_water_close");
    /** How far above and below a ship to look for the water's surface. */
    private static final int SEARCH = 32;

    private static ShaderInstance side;
    private static ShaderInstance close;
    private static AdvancedFbo lastFront;
    private static AdvancedFbo lastBack;

    private ShipWaterCutaway() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SIDE_ID, DefaultVertexFormat.POSITION), s -> side = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), CLOSE_ID, DefaultVertexFormat.POSITION), s -> close = s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean ready() {
        return side != null && close != null;
    }

    /** Fills Sable's close and far buffers (already sized to the screen), for a camera outside every hull or inside one. */
    public static void render(Collection<SimpleCulledRenderRegion> regions, AdvancedFbo closeBuffer, AdvancedFbo farBuffer,
                              Matrix4f modelView, Matrix4f projection, boolean cameraInside) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        int width = closeBuffer.getWidth(), height = closeBuffer.getHeight();
        lastFront = sized(lastFront, width, height);
        lastBack = sized(lastBack, width, height);

        // Per region: the water's surface as a plane in the region's own space, signed positive on the camera's side.
        float[][] planes = new float[regions.size()][];
        int i = 0;
        for (SimpleCulledRenderRegion region : regions) planes[i++] = plane(level, region, camera.y);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11C.glEnable(GL32C.GL_DEPTH_CLAMP);
        RenderSystem.setShader(() -> side);
        RenderSystem.depthFunc(GL11C.GL_GREATER);
        // The last front faces and the last back faces in front of the water's surface.
        draw(lastFront, 0.0F, GL11C.GL_BACK, regions, planes, 1.0F, modelView, projection);
        draw(lastBack, 0.0F, GL11C.GL_FRONT, regions, planes, 1.0F, modelView, projection);
        // The first back faces past it.
        RenderSystem.depthFunc(GL11C.GL_LESS);
        draw(farBuffer, 1.0F, GL11C.GL_FRONT, regions, planes, -1.0F, modelView, projection);

        // Inside the hull at the surface: the close depth is that last front face (or the camera). Outside: nothing to hide.
        closeBuffer.bind(true);
        closeBuffer.clear(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, closeBuffer.getClearMask());
        RenderSystem.depthFunc(GL11C.GL_ALWAYS);
        RenderSystem.disableCull();
        close.setSampler("FrontDepth", lastFront.getDepthTextureAttachment().getId());
        close.setSampler("BackDepth", lastBack.getDepthTextureAttachment().getId());
        // Every back face drawn before the surface also wrote its color, so the mark tells a face at the camera from none.
        close.setSampler("BackMark", lastBack.getColorTextureAttachment(0).getId());
        close.safeGetUniform("CameraInside").set(cameraInside ? 1.0F : 0.0F);
        RenderSystem.setShader(() -> close);
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        quad.addVertex(-1.0F, -1.0F, 0.0F);
        quad.addVertex(1.0F, -1.0F, 0.0F);
        quad.addVertex(1.0F, 1.0F, 0.0F);
        quad.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(quad.buildOrThrow());

        AdvancedFbo.unbind();
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        RenderSystem.enableCull();
        GL11C.glCullFace(GL11C.GL_BACK);
        GL11C.glDisable(GL32C.GL_DEPTH_CLAMP);
    }

    private static void draw(AdvancedFbo target, float clearDepth, int cull, Collection<SimpleCulledRenderRegion> regions, float[][] planes,
                             float sideSign, Matrix4f modelView, Matrix4f projection) {
        target.bind(true);
        target.clear(0.0F, 0.0F, 0.0F, 0.0F, clearDepth, target.getClearMask());
        GL11C.glCullFace(cull);
        int i = 0;
        for (SimpleCulledRenderRegion region : regions) {
            float[] p = planes[i++];
            side.safeGetUniform("CgPlane").set(p[0] * sideSign, p[1] * sideSign, p[2] * sideSign, p[3] * sideSign);
            region.render(modelView, projection);
        }
    }

    /**
     * The water's surface under a region's ship as a + b.x + c.y + d.z over the region's own coordinates (the ones its
     * mesh is drawn in), positive on the camera's side. With no surface near the ship (out of the water, or deep under
     * it) every face counts as on the camera's side, so the region hides nothing.
     */
    private static float[] plane(Level level, SimpleCulledRenderRegion region, double cameraY) {
        if (region.getOrigin() == null) region.build();
        Vec3 origin = region.getOrigin();
        SubLevel ship = Sable.HELPER.getContaining(level, origin);
        Vec3 worldOrigin = origin;
        Quaternionf orientation = new Quaternionf();
        Vec3 shipPos = origin;
        if (ship instanceof ClientSubLevel client) {
            // The same pose SimpleCulledRenderRegion draws the region with.
            worldOrigin = client.renderPose().transformPosition(origin);
            orientation.set(client.renderPose().orientation());
            shipPos = new Vec3(client.renderPose().position().x(), client.renderPose().position().y(), client.renderPose().position().z());
        }
        Double surface = surface(level, shipPos);
        if (surface == null) return new float[] {0.0F, 0.0F, 0.0F, 1.0F};
        float sign = cameraY >= surface ? 1.0F : -1.0F;
        Vector3f x = orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f y = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f z = orientation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        return new float[] {sign * x.y, sign * y.y, sign * z.y, sign * (float) (worldOrigin.y - surface)};
    }

    /** The top of the water in the ship's column, or null when the column has none near the ship or is water all the way up. */
    private static Double surface(Level level, Vec3 at) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = (int) Math.floor(at.y) + SEARCH;
        for (int y = top; y >= (int) Math.floor(at.y) - SEARCH; y--) {
            pos.set(Math.floor(at.x), y, Math.floor(at.z));
            // The world's own water, not the dry hold Deep Seas reports inside a hull.
            FluidState fluid = CompartmentTracker.realFluidState(level, pos);
            if (!fluid.is(FluidTags.WATER)) continue;
            return y == top ? null : y + (double) fluid.getOwnHeight();
        }
        return null;
    }

    private static AdvancedFbo sized(AdvancedFbo fbo, int width, int height) {
        if (fbo != null && fbo.getWidth() == width && fbo.getHeight() == height) return fbo;
        if (fbo != null) fbo.free();
        return AdvancedFbo.withSize(width, height).addColorTextureBuffer().setDepthTextureBuffer().build(true);
    }
}
