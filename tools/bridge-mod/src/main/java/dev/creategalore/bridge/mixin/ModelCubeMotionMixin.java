package dev.creategalore.bridge.mixin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.creategalore.bridge.dlss.EntityMotion;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DLAA: a model cube draws; its vertices are counted on their own for their motion (see {@link EntityMotion#cube}).
 * Sodium draws the same faces from here (its fast path starts later in this method).
 */
@Mixin(ModelPart.Cube.class)
public abstract class ModelCubeMotionMixin implements EntityMotion.Cube {
    // ModelPart.Polygon is private to ModelPart, so its array can't be shadowed by name: read once by reflection, from
    // inside the cube's own class.
    @Unique
    private static Field create_galore_bridge$polygons;
    @Unique
    private int create_galore_bridge$vertices = -1;

    @Override
    public int create_galore_bridge$vertices() {
        if (create_galore_bridge$vertices < 0) {
            try {
                if (create_galore_bridge$polygons == null) {
                    Field field = ModelPart.Cube.class.getDeclaredField("polygons");
                    field.setAccessible(true);
                    create_galore_bridge$polygons = field;
                }
                create_galore_bridge$vertices = 4 * Array.getLength(create_galore_bridge$polygons.get(this));
            } catch (ReflectiveOperationException | RuntimeException e) {
                create_galore_bridge$vertices = 0; // counted with the rest of the model, as before
            }
        }
        return create_galore_bridge$vertices;
    }

    @Inject(method = "compile", at = @At("HEAD"))
    private void create_galore_bridge$cubeMotion(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        EntityMotion.cube(this, create_galore_bridge$vertices());
    }
}
