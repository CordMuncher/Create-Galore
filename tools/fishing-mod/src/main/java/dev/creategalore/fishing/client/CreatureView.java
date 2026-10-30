package dev.creategalore.fishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.aquarium.AquariumCreatures;
import dev.creategalore.fishing.mixin.EntityAccessor;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Draws a creature living in a tank (a net's mob, {@link AquariumCreatures}) as itself: a client-only copy of the
 * very mob that was netted, so it keeps its kind, colours and size. One per swimmer, never ticked: the tank tells it
 * where it is heading and how fast it walks, which drives its animations.
 */
final class CreatureView {
    private CreatureView() {
    }

    /** A copy of the mob a tank entry holds, or empty if it can't be made (then nothing is drawn for it). */
    static Optional<Entity> create(ItemStack creature, Level level) {
        Optional<CompoundTag> mob = AquariumCreatures.stored(creature);
        Optional<EntityType<?>> type = mob.flatMap(EntityType::by);
        if (type.isEmpty()) return Optional.empty();
        try {
            Entity e = type.get().create(level);
            if (e == null) return Optional.empty();
            e.load(mob.get());
            ((EntityAccessor) e).cgfish$setWasTouchingWater(true);
            e.setSilent(true);
            return Optional.of(e);
        } catch (RuntimeException ex) {
            CreateGaloreFishing.LOG.warn("Can't make a {} to draw in an aquarium: {}", mob.get().getString("id"), ex.toString());
            return Optional.empty();
        }
    }

    /** Points the mob along (vx, vz) and sets its walk to {@code speed} (blocks a tick); call once a client tick. */
    static void step(Entity e, double vx, double vz, double speed) {
        float yaw = (float) Math.toDegrees(Math.atan2(vz, vx)) - 90;
        e.yRotO = e.getYRot();
        e.setYRot(yaw);
        e.xRotO = e.getXRot();
        e.setXRot(0);
        e.tickCount++;
        if (e instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
            living.yBodyRot = living.yHeadRot = yaw;
            living.walkAnimation.update((float) Math.min(speed * 10, 1), 0.4f);
        }
    }

    /** Draws the mob with its feet at the pose origin (its yaw is its own, see {@link #step}). */
    static boolean draw(Entity e, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(e, 0, 0, 0, e.getYRot(), partial, pose, buffers, light);
            return true;
        } catch (RuntimeException ex) {
            CreateGaloreFishing.LOG.warn("Can't draw {} in an aquarium: {}", EntityType.getKey(e.getType()), ex.toString());
            return false;
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }
}
