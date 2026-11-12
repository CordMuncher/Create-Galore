package dev.creategalore.bridge.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL41C;

/**
 * Sable and Create Deep Seas keep the sea out of a ship's hull only in the view from the player: the hull's cells mark
 * where the world's water is, and the terrain shader drops it there. A shader pack also draws the world's water into its
 * sun shadow map, and there nothing was dropped. So under the sun the sea surface still lay over a hold or a deck below
 * the waterline, and the pack lit that floor as if it were under water: Photon's water caustics (and its water tint)
 * showed on the deck planks inside the hull.
 * <p>
 * The shadow programs of the shader pack's terrain now ask the same question at each fragment of the world's own
 * terrain: is this point inside a ship's hull cells? If it is, the fragment is dropped from the shadow map, as it is from
 * the view. The cells of the nearest hulls go into one small 3D texture (one byte per cell), with a matrix per hull from
 * camera-relative world space to the hull's cells, so a moving or tilted ship keeps its cut-out in place.
 * Only the world's terrain goes through these programs; the ships themselves draw through Sable's own programs, so their
 * blocks still cast shadows (and their glass still tints them).
 */
public final class ShipShadowWater {
    /** The texture unit of the hull cells; Deep Seas keeps 8 and 9 for its own hull textures. */
    public static final int UNIT = 10;
    public static final String SAMPLER = "cg_HullCells";
    private static final int MAX_HULLS = 8;
    /** Hulls farther than this from the camera are left out (no shader pack draws shadows that far). */
    private static final double MAX_DISTANCE = 512.0;
    /** How often the cells are read again even when no hull changed (a hull edited in place). */
    private static final long REFRESH_NANOS = 1_000_000_000L;

    private static final Pattern VERSION = Pattern.compile("#version\\s+(\\d+)");
    private static final Pattern VERT_INIT = Pattern.compile("_vert_init\\s*\\(\\s*\\)\\s*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)\\s*\\{");

    private static final Map<Integer, int[]> LOCATIONS = new HashMap<>();
    private static final FloatBuffer MATRICES = BufferUtils.createFloatBuffer(16 * MAX_HULLS);
    private static final IntBuffer BOXES = BufferUtils.createIntBuffer(4 * MAX_HULLS);

    private static int texture;
    private static int count;
    private static List<Object> uploadedKey = List.of();
    private static long uploadedAt;
    private static int maxSize = -1;

    private ShipShadowWater() {
    }

    // ------------------------------------------------------------------ shader patch

    /**
     * Adds the hull test to one stage pair of a terrain shadow program, or returns null when the program's shape is not
     * one this knows (then it stays as the pack wrote it).
     */
    public static String[] patch(String vertex, String fragment) {
        if (vertex == null || fragment == null) return null;
        if (vertex.contains("cg_ShipRel") || fragment.contains("cg_ShipRel")) return null;
        Matcher init = VERT_INIT.matcher(vertex);
        Matcher vertMain = MAIN.matcher(vertex);
        Matcher fragMain = MAIN.matcher(fragment);
        if (!vertex.contains("u_RegionOffset") || !vertex.contains("_get_draw_translation") || !init.find()
                || !vertMain.find() || !fragMain.find() || init.start() < vertMain.end()) return null;
        // texelFetch, integer uniforms and in/out need GLSL 1.30; every pack worth patching is newer.
        if (version(vertex) < 130 || version(fragment) < 130) return null;

        String v = vertex.substring(0, vertMain.start())
                + "out vec3 cg_ShipRel;\n"
                + vertex.substring(vertMain.start(), init.end())
                + "\n    cg_ShipRel = _vert_position + u_RegionOffset + _get_draw_translation(_draw_id);\n"
                + vertex.substring(init.end());

        String f = fragment.substring(0, fragMain.start())
                + "in vec3 cg_ShipRel;\n"
                + "uniform sampler3D " + SAMPLER + ";\n"
                + "uniform int cg_HullCount;\n"
                + "uniform mat4 cg_HullMatrix[" + MAX_HULLS + "];\n"
                + "uniform ivec4 cg_HullBox[" + MAX_HULLS + "];\n"
                + "bool cg_inShipHull(vec3 rel) {\n"
                + "    for (int cg_i = 0; cg_i < " + MAX_HULLS + "; cg_i++) {\n"
                + "        if (cg_i >= cg_HullCount) break;\n"
                + "        ivec3 cg_cell = ivec3(floor((cg_HullMatrix[cg_i] * vec4(rel, 1.0)).xyz));\n"
                + "        if (any(lessThan(cg_cell, ivec3(0))) || any(greaterThanEqual(cg_cell, cg_HullBox[cg_i].xyz))) continue;\n"
                + "        if (texelFetch(cg_HullCells, cg_cell + ivec3(cg_HullBox[cg_i].w, 0, 0), 0).r > 0.5) return true;\n"
                + "    }\n"
                + "    return false;\n"
                + "}\n"
                + fragment.substring(fragMain.start(), fragMain.end())
                + "\n    if (cg_HullCount > 0 && cg_inShipHull(cg_ShipRel)) discard;\n"
                + fragment.substring(fragMain.end());
        return new String[] {v, f};
    }

    private static int version(String source) {
        Matcher m = VERSION.matcher(source);
        return m.find() ? Integer.parseInt(m.group(1)) : 110;
    }

    // ------------------------------------------------------------------ program setup

    /** True when a linked program has the hull test (its sampler must stay off the pack's texture units). */
    public static boolean hasTest(int program) {
        return GL20C.glGetUniformLocation(program, SAMPLER) >= 0;
    }

    /** Points a program's hull sampler at its own unit for good, before anything draws with it. */
    public static void setUpProgram(int program) {
        int sampler = GL20C.glGetUniformLocation(program, SAMPLER);
        if (sampler >= 0) GL41C.glProgramUniform1i(program, sampler, UNIT);
        LOCATIONS.remove(program);
    }

    // ------------------------------------------------------------------ per draw

    /** Called when Sodium starts a terrain pass with the program now bound. Hands it this frame's hulls. */
    public static void beginTerrain() {
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (program <= 0) return;
        int[] loc = LOCATIONS.computeIfAbsent(program, p -> new int[] {
                GL20C.glGetUniformLocation(p, SAMPLER),
                GL20C.glGetUniformLocation(p, "cg_HullCount"),
                GL20C.glGetUniformLocation(p, "cg_HullMatrix"),
                GL20C.glGetUniformLocation(p, "cg_HullBox")});
        if (loc[0] < 0 || loc[1] < 0) return;
        GL20C.glUniform1i(loc[0], UNIT);
        if (!NameTagDefer.inShadowPass()) {
            GL20C.glUniform1i(loc[1], 0);
            return;
        }
        int hulls = 0;
        try {
            hulls = update();
        } catch (Throwable t) {
            hulls = 0;
        }
        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT);
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, texture);
        GL13C.glActiveTexture(active);
        GL20C.glUniform1i(loc[1], texture == 0 ? 0 : hulls);
        if (hulls > 0) {
            if (loc[2] >= 0) GL20C.glUniformMatrix4fv(loc[2], false, MATRICES);
            if (loc[3] >= 0) GL20C.glUniform4iv(loc[3], BOXES);
        }
    }

    private record Hull(BoundedBitVolume3i volume, Pose3dc pose, double distance) {
    }

    /** Fills the matrices and boxes for the nearest hulls, and their cells into the texture when they changed. */
    private static int update() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return count = 0;
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        if (container == null || container.getRegions().isEmpty()) return count = 0;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();

        List<Hull> hulls = new ArrayList<>();
        for (WaterOcclusionRegion region : container.getRegions()) {
            BoundedBitVolume3i volume = region.getVolume();
            if (volume == null) continue;
            SubLevel ship = Sable.HELPER.getContaining(level, volume.getMinBlockPos());
            if (!(ship instanceof ClientSubLevel client)) continue;
            Pose3dc pose = client.renderPose();
            double d = camera.distanceTo(new Vec3(pose.position().x(), pose.position().y(), pose.position().z()));
            if (d > MAX_DISTANCE) continue;
            hulls.add(new Hull(volume, pose, d));
        }
        hulls.sort(Comparator.comparingDouble(Hull::distance));
        if (maxSize < 0) maxSize = GL11C.glGetInteger(GL12C.GL_MAX_3D_TEXTURE_SIZE);
        List<Hull> kept = new ArrayList<>();
        int width = 0, height = 1, depth = 1;
        for (Hull hull : hulls) {
            if (kept.size() == MAX_HULLS) break;
            BoundedBitVolume3i v = hull.volume();
            if (width + v.xSpan() > maxSize || v.ySpan() > maxSize || v.zSpan() > maxSize) continue;
            kept.add(hull);
            width += v.xSpan();
            height = Math.max(height, v.ySpan());
            depth = Math.max(depth, v.zSpan());
        }
        if (kept.isEmpty()) return count = 0;

        List<Object> key = new ArrayList<>();
        for (Hull hull : kept) key.add(hull.volume());
        long now = System.nanoTime();
        if (!key.equals(uploadedKey) || now - uploadedAt > REFRESH_NANOS) {
            upload(kept, width, height, depth, !key.equals(uploadedKey));
            uploadedKey = key;
            uploadedAt = now;
        }

        MATRICES.clear();
        BOXES.clear();
        Matrix4f m = new Matrix4f();
        int offset = 0;
        for (Hull hull : kept) {
            BoundedBitVolume3i v = hull.volume();
            BlockPos min = v.getMinBlockPos();
            Vector3d o = hull.pose().transformPositionInverse(new Vector3d(camera.x, camera.y, camera.z), new Vector3d());
            Vector3d x = hull.pose().transformPositionInverse(new Vector3d(camera.x + 1.0, camera.y, camera.z), new Vector3d()).sub(o);
            Vector3d y = hull.pose().transformPositionInverse(new Vector3d(camera.x, camera.y + 1.0, camera.z), new Vector3d()).sub(o);
            Vector3d z = hull.pose().transformPositionInverse(new Vector3d(camera.x, camera.y, camera.z + 1.0), new Vector3d()).sub(o);
            o.sub(min.getX(), min.getY(), min.getZ());
            m.set((float) x.x, (float) x.y, (float) x.z, 0.0F,
                    (float) y.x, (float) y.y, (float) y.z, 0.0F,
                    (float) z.x, (float) z.y, (float) z.z, 0.0F,
                    (float) o.x, (float) o.y, (float) o.z, 1.0F);
            m.get(MATRICES);
            MATRICES.position(MATRICES.position() + 16);
            BOXES.put(v.xSpan()).put(v.ySpan()).put(v.zSpan()).put(offset);
            offset += v.xSpan();
        }
        MATRICES.flip();
        BOXES.flip();
        return count = kept.size();
    }

    private static byte[] lastBytes;

    private static void upload(List<Hull> hulls, int width, int height, int depth, boolean changedSet) {
        byte[] bytes = new byte[width * height * depth];
        int offset = 0;
        for (Hull hull : hulls) {
            BoundedBitVolume3i v = hull.volume();
            BlockPos min = v.getMinBlockPos();
            for (int z = 0; z < v.zSpan(); z++)
                for (int y = 0; y < v.ySpan(); y++)
                    for (int x = 0; x < v.xSpan(); x++)
                        if (v.getOccupied(min.getX() + x, min.getY() + y, min.getZ() + z))
                            bytes[(offset + x) + width * (y + height * z)] = (byte) 255;
            offset += v.xSpan();
        }
        if (!changedSet && Arrays.equals(bytes, lastBytes)) return;
        lastBytes = bytes;
        ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
        data.put(bytes).flip();

        int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT);
        if (texture == 0) texture = GL11C.glGenTextures();
        GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, texture);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_WRAP_R, GL12C.GL_CLAMP_TO_EDGE);
        int unpackBuffer = GL11C.glGetInteger(GL30C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int alignment = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11C.glGetInteger(GL11C.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS);
        int imageHeight = GL11C.glGetInteger(GL12C.GL_UNPACK_IMAGE_HEIGHT);
        int skipImages = GL11C.glGetInteger(GL12C.GL_UNPACK_SKIP_IMAGES);
        GL30C.glBindBuffer(GL30C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_IMAGES, 0);
        GL12C.glTexImage3D(GL12C.GL_TEXTURE_3D, 0, GL30C.GL_R8, width, height, depth, 0, GL11C.GL_RED, GL11C.GL_UNSIGNED_BYTE, data);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, alignment);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, rowLength);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, skipRows);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, skipPixels);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_IMAGE_HEIGHT, imageHeight);
        GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_IMAGES, skipImages);
        GL30C.glBindBuffer(GL30C.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        GL13C.glActiveTexture(active);
    }

    /** For the test log: how many hulls the last shadow pass cut. */
    public static int lastCount() {
        return count;
    }
}
