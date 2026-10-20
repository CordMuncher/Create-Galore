package dev.creategalore.fishing.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.mixin.EntityAccessor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a caught fish as its own mob's 3D model when it has one (an entity type with the item's id, as vanilla,
 * Tide, Aquaculture and Hybrid Aquatic fish have), else as the item, at its real length (a block is a metre).
 *
 * <p>Convention for callers: the fish is centred on the pose origin, head toward +X, back toward +Y. The model is a
 * client-only entity, one per type, that never ticks; it is told it's in water so every mod draws it in its
 * swimming pose rather than flopped on its side.
 */
final class FishView {
    private static final Map<EntityType<?>, Optional<Entity>> CACHE = new HashMap<>();
    /** The model's drawn extent per type, facing +X at scale 1: min x, y, z, max x, y, z. */
    private static final Map<EntityType<?>, float[]> BOUNDS = new HashMap<>();
    private static Level cacheLevel;
    private static long lastLog;

    private FishView() {
    }

    /** The display entity for a fish item, if the item has a mob of the same id. */
    static Optional<Entity> entity(ItemStack fish) {
        return entity(BuiltInRegistries.ITEM.getKey(fish.getItem()));
    }

    static Optional<Entity> entity(ResourceLocation id) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return Optional.empty();
        if (level != cacheLevel) {
            CACHE.clear();
            BOUNDS.clear();
            cacheLevel = level;
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) return Optional.empty();
        return CACHE.computeIfAbsent(type.get(), t -> {
            try {
                Entity e = t.create(level);
                if (e == null) return Optional.empty();
                ((EntityAccessor) e).cgfish$setWasTouchingWater(true);
                e.setSilent(true);
                return Optional.of(e);
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        });
    }

    /**
     * How big the mob's model really is: drawn once into a buffer that only records where the vertices land
     * (hitboxes are no guide; a pike's is a short box around a long model).
     */
    private static float[] bounds(Entity e) {
        return BOUNDS.computeIfAbsent(e.getType(), t -> {
            VertexBounds b = new VertexBounds();
            face(e);
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            try {
                dispatcher.render(e, 0, 0, 0, -90, 0, new PoseStack(), b, LightTexture.FULL_BRIGHT);
            } catch (RuntimeException ex) {
                // Leave whatever was recorded.
            } finally {
                dispatcher.setRenderShadow(true);
            }
            if (!b.any() || b.max[0] - b.min[0] < 0.05f) {
                float w = Math.max(0.2f, Math.max(e.getBbWidth(), e.getBbHeight()));
                return new float[]{-w / 2, 0, -w / 4, w / 2, e.getBbHeight(), w / 4};
            }
            float[] out = {b.min[0], b.min[1], b.min[2], b.max[0], b.max[1], b.max[2]};
            if (Boolean.getBoolean("cgfish.debug")) {
                CreateGaloreFishing.LOG.info("[cgfish] model bounds of {}: {}", BuiltInRegistries.ENTITY_TYPE.getKey(t),
                        Arrays.toString(out));
            }
            return out;
        });
    }

    /** Facing +X: a mob faces (-sin yaw, 0, cos yaw), so yaw -90. */
    private static void face(Entity e) {
        e.setYRot(-90);
        e.yRotO = -90;
        e.setXRot(0);
        e.xRotO = 0;
        if (e instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = -90;
            living.yHeadRot = living.yHeadRotO = -90;
        }
    }

    /** A fish fills about half its bounding box (an ellipsoid fills 52%; fins and tail add box, not fish). */
    private static final double FILL = 0.5;
    /** Fish are about as dense as water: 1.05 kg per litre. */
    private static final double KG_PER_CUBIC_BLOCK = 1050;

    /**
     * Draws {@code fish} at the pose origin (see the class comment for orientation), {@code lengthCm} long and, if
     * {@code kg} is known (above 0), as deep and thick as a fish of that weight: Minecraft's fish models are slim,
     * and an 11 kg carp has a real carp's girth. {@code clock} drives the swimming animation (ticks).
     */
    static void draw(ItemStack fish, double lengthCm, double kg, PoseStack pose, MultiBufferSource buffers, int light,
                     float clock) {
        float length = (float) Mth.clamp(lengthCm / 100, 0.08, 4);
        Optional<Entity> mob = entity(fish);
        pose.pushPose();
        if (mob.isPresent()) {
            Entity e = mob.get();
            float[] box = bounds(e);
            float scale = Mth.clamp(length / (box[3] - box[0]), 0.05f, 20);
            float girth = girth(kg, length, (box[4] - box[1]) * scale, (box[5] - box[2]) * scale);
            if (Boolean.getBoolean("cgfish.debug") && System.currentTimeMillis() - lastLog > 3000) {
                lastLog = System.currentTimeMillis();
                CreateGaloreFishing.LOG.info("[cgfish] draw {} {} cm {} kg: scale {} girth {}, incoming pose scale {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), lengthCm, kg, scale, girth,
                        pose.last().pose().getScale(new Vector3f()));
            }
            pose.scale(scale, scale * girth, scale * girth);
            // Centre the model on the origin.
            pose.translate(-(box[0] + box[3]) / 2, -(box[1] + box[4]) / 2, -(box[2] + box[5]) / 2);
            face(e);
            e.tickCount = (int) clock;
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            try {
                dispatcher.render(e, 0, 0, 0, -90, clock - (int) clock, pose, buffers, light);
            } catch (RuntimeException ex) {
                // Some mob renderers expect a real, ticking entity; fall back to the item next time.
                CACHE.put(e.getType(), Optional.empty());
            } finally {
                dispatcher.setRenderShadow(true);
            }
        } else {
            // A fish sprite in an item frame is half a block wide and faces left.
            float scale = length / 0.5f;
            // A sprite is flat; only its depth can follow the weight (as if a fish's side is a third of its length).
            float girth = girth(kg, length, length / 3, length / 8);
            pose.scale(scale, scale * girth, scale);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            Minecraft.getInstance().getItemRenderer().renderStatic(fish, ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
        }
        pose.popPose();
    }

    /**
     * How much deeper and thicker than the model the fish must be for its weight, given the model's height and width
     * once scaled to the fish's length (blocks; a block is a metre). Only ever fattens: blocky models (fins and all)
     * are already stout, but a slim one would make a heavy fish look like a light one. Capped so odd models don't
     * turn into balls.
     */
    static float girth(double kg, float length, float height, float width) {
        if (kg <= 0 || height <= 0 || width <= 0) return 1;
        double box = kg / KG_PER_CUBIC_BLOCK / FILL;
        double crossSection = box / length;
        return (float) Mth.clamp(Math.sqrt(crossSection / (height * width)), 1, 2.2);
    }

    /**
     * Draws the fish into a screen box (x0, y0)-(x1, y1), side on, turning slowly about its vertical axis by
     * {@code spin} degrees, scaled to fit.
     */
    static void drawInGui(GuiGraphics g, ItemStack fish, double lengthCm, double kg, int x0, int y0, int x1, int y1,
                          float spin, float clock) {
        float length = (float) Mth.clamp(lengthCm / 100, 0.08, 4);
        float fit = Math.min((x1 - x0) * 0.9f, (y1 - y0) * 2.2f) / length;
        g.pose().pushPose();
        g.pose().translate((x0 + x1) / 2f, (y0 + y1) / 2f, 150);
        g.pose().mulPose(new Matrix4f().scaling(fit, fit, -fit));
        g.pose().mulPose(new Quaternionf().rotateZ(Mth.PI));
        g.pose().mulPose(Axis.XP.rotationDegrees(-12));
        g.pose().mulPose(Axis.YP.rotationDegrees(spin));
        Lighting.setupForEntityInInventory();
        RenderSystem.runAsFancy(() -> draw(fish, lengthCm, kg, g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT, clock));
        g.flush();
        Lighting.setupFor3DItems();
        g.pose().popPose();
    }
}
