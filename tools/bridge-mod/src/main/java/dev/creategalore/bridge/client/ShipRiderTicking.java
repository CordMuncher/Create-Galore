package dev.creategalore.bridge.client;

import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Keeps other players (and mobs) that stand on a ship ticking on this client.
 *
 * <p>For something standing on a ship the server sends its spot on the ship, not in the world. Sable keeps that spot
 * and turns it into a world position at the end of the entity's tick. Entity Culling skips most of that tick for an
 * entity it thinks you can't see ("tick culling": only the movement step runs), so the ship spot kept updating while the
 * world position stayed where the entity was last seen. On a moving ship that spot falls behind the ship at once, so it
 * stays out of sight and the entity never catches up: the other player is gone for good (and can't be hit, the hitbox
 * is back there too), or jumps when a glimpse of the old spot lets one tick through. Sitting players were fine because
 * Entity Culling never skips a passenger.</p>
 *
 * <p>So while an entity has a ship spot it is marked "never cull" (the vanilla flag Entity Culling honours), and the
 * mark comes off when it steps off. Entities that were never-cull by themselves are left alone.</p>
 */
public final class ShipRiderTicking {
    private static final Set<Entity> MARKED = Collections.newSetFromMap(new WeakHashMap<>());

    private ShipRiderTicking() {
    }

    /** Called after each position packet for the entity, once Sable has filed it as on a ship or not. */
    public static void update(Entity entity) {
        if (entity == null) return;
        boolean onShip = ((EntityStickExtension) entity).sable$getPlotPosition() != null;
        if (onShip) {
            if (!entity.noCulling) {
                entity.noCulling = true;
                MARKED.add(entity);
            }
        } else if (MARKED.remove(entity)) {
            entity.noCulling = false;
        }
    }
}
