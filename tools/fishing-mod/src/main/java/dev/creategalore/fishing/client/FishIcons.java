package dev.creategalore.fishing.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import dev.creategalore.fishing.CreateGaloreFishing;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Inventory icons for fish items: each fish's 3D model, drawn once into a small texture and reused, because a chest
 * of live mob models (some of them GeckoLib) would cost a frame's worth of time. Baked on the render thread at the
 * start of a frame, a few per frame, the first time an icon is wanted (the sprite stands in until then). The image
 * gets a one-pixel darker outline, like Minecraft's own item icons. Rebuilt after a resource reload.
 */
final class FishIcons {
    private FishIcons() {
    }

    /** Icon size in pixels: a slot is 16 GUI pixels, and GUI scale 4 shows 64. */
    private static final int SIZE = 64;
    private static final int PER_FRAME = 3;
    /** Rim width in icon pixels (a slot's pixel is four of them at this size). */
    private static final int RIM = 2;
    /** How the fish lies in its icon: head up and to the right, like the vanilla fish sprites, turned a little. */
    private static final float TILT = 35;
    private static final float TURN = -25;

    private static final Map<Item, ResourceLocation> ICONS = new HashMap<>();
    private static final Set<Item> FAILED = new HashSet<>();
    private static final Deque<ItemStack> QUEUE = new ArrayDeque<>();
    private static final Set<Item> QUEUED = new HashSet<>();
    private static TextureTarget target;
    private static int generation;

    static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> bakeQueued());
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> clear()));
    }

    /** The icon's texture, or null while it waits to be baked (or can't be). */
    static ResourceLocation icon(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation done = ICONS.get(item);
        if (done != null || FAILED.contains(item)) return done;
        if (QUEUED.add(item)) QUEUE.add(stack.copyWithCount(1));
        return null;
    }

    private static void clear() {
        RenderSystem.recordRenderCall(() -> {
            ICONS.values().forEach(loc -> Minecraft.getInstance().getTextureManager().release(loc));
            ICONS.clear();
            FAILED.clear();
            QUEUE.clear();
            QUEUED.clear();
            generation++;
        });
    }

    private static void bakeQueued() {
        if (QUEUE.isEmpty() || Minecraft.getInstance().level == null) return;
        for (int i = 0; i < PER_FRAME && !QUEUE.isEmpty(); i++) {
            ItemStack stack = QUEUE.poll();
            QUEUED.remove(stack.getItem());
            try {
                ResourceLocation loc = bake(stack);
                if (loc != null) ICONS.put(stack.getItem(), loc);
                else FAILED.add(stack.getItem());
            } catch (RuntimeException e) {
                FAILED.add(stack.getItem());
                CreateGaloreFishing.LOG.warn("Couldn't draw an icon for {}: {}", stack.getItem(), e.toString());
            }
        }
    }

    private static ResourceLocation bake(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (FishView.mob(stack).isEmpty()) return null;
        if (target == null) target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1, 1, -1, 1, -100, 100), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.identity();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();

        // Lay the fish along the icon's diagonal and scale it to fill the frame.
        float[] box = FishView.extent(stack);
        double tilt = Math.toRadians(TILT);
        float length = box[0], height = box[1];
        float wide = (float) (length * Math.cos(tilt) + height * Math.sin(tilt));
        float tall = (float) (length * Math.sin(tilt) + height * Math.cos(tilt));
        float fit = 1.86f / Math.max(wide, tall);
        PoseStack pose = new PoseStack();
        pose.scale(fit, fit, fit);
        pose.mulPose(Axis.ZP.rotationDegrees(TILT));
        pose.mulPose(Axis.YP.rotationDegrees(TURN));
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
        FishView.drawModel(stack, pose, buffers, LightTexture.FULL_BRIGHT);
        buffers.endBatch();

        NativeImage image = new NativeImage(SIZE, SIZE, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();

        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        mc.getMainRenderTarget().bindWrite(true);
        Lighting.setupFor3DItems();

        if (!outline(image)) {
            image.close();
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation loc = CreateGaloreFishing.id("fish_icon/" + generation + "/" + id.getNamespace() + "/" + id.getPath());
        mc.getTextureManager().register(loc, new DynamicTexture(image));
        return loc;
    }

    /**
     * Gives the drawn fish a one-pixel rim, a darker shade of the pixel it borders, as Minecraft's item icons have, and
     * makes the drawing's edge hard (no half-transparent pixels). False if nothing was drawn.
     */
    private static boolean outline(NativeImage image) {
        int n = image.getWidth();
        boolean[] solid = new boolean[n * n];
        boolean any = false;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = image.getPixelRGBA(x, y);
                solid[y * n + x] = (c >>> 24) > 40;
                any |= solid[y * n + x];
                image.setPixelRGBA(x, y, solid[y * n + x] ? c | 0xff000000 : 0);
            }
        }
        if (!any) return false;
        for (int pass = 0; pass < RIM; pass++) {
            boolean[] grown = solid.clone();
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (solid[y * n + x]) continue;
                    int next = neighbour(image, solid, n, x, y);
                    if (next == 0) continue;
                    // The rim takes the darkest shade at its first pixel and keeps it outward.
                    image.setPixelRGBA(x, y, pass == 0 ? darker(next) : next);
                    grown[y * n + x] = true;
                }
            }
            solid = grown;
        }
        return true;
    }

    private static int neighbour(NativeImage image, boolean[] solid, int n, int x, int y) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < n && ny < n && solid[ny * n + nx]) return image.getPixelRGBA(nx, ny);
        }
        return 0;
    }

    /** About a third of the colour's brightness: Minecraft's icon rims are dark versions of what they edge. */
    private static int darker(int abgr) {
        int b = (abgr >> 16) & 0xff, g = (abgr >> 8) & 0xff, r = abgr & 0xff;
        return 0xff000000 | (b * 35 / 100) << 16 | (g * 35 / 100) << 8 | (r * 35 / 100);
    }
}
