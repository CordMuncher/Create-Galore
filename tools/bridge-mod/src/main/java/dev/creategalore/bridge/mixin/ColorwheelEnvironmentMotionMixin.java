package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.dlss.ColorwheelMotion;
import dev.creategalore.bridge.dlss.ObjectMotion;
import dev.djefrey.colorwheel.engine.embed.EmbeddedEnvironment;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DLAA: a contraption's, train's or ship's model matrix as Colorwheel drew it last frame (see {@link ColorwheelMotion}).
 * Colorwheel works the matrix out once a frame in flush; the one it had before that is last frame's.
 */
@Mixin(value = EmbeddedEnvironment.class, remap = false)
public abstract class ColorwheelEnvironmentMotionMixin implements ColorwheelMotion.Pose {
    @Shadow
    @Final
    public Matrix4f poseComposed;

    @Shadow
    public int matrixIndex;

    @Unique
    private final Matrix4f create_galore_bridge$previous = new Matrix4f();
    @Unique
    private long create_galore_bridge$flushedFrame = Long.MIN_VALUE;
    @Unique
    private boolean create_galore_bridge$fresh;

    @Inject(method = "flush", at = @At("HEAD"))
    private void create_galore_bridge$keepPrevious(long ptr, CallbackInfo ci) {
        long frame = ObjectMotion.frame();
        if (create_galore_bridge$flushedFrame == frame) {
            create_galore_bridge$fresh = false;
            return;
        }
        boolean lastFrame = create_galore_bridge$flushedFrame == frame - 1;
        if (lastFrame) {
            create_galore_bridge$previous.set(poseComposed);
        }
        create_galore_bridge$fresh = !lastFrame;
        create_galore_bridge$flushedFrame = frame;
    }

    @Inject(method = "flush", at = @At("TAIL"))
    private void create_galore_bridge$firstPose(long ptr, CallbackInfo ci) {
        // Not drawn last frame: no motion of its own this frame.
        if (create_galore_bridge$fresh) {
            create_galore_bridge$previous.set(poseComposed);
        }
    }

    @Inject(method = "setupDraw", at = @At("TAIL"))
    private void create_galore_bridge$previousUniform(GlProgram program, CallbackInfo ci) {
        program.setMat4(ColorwheelMotion.PREV_MODEL_UNIFORM, create_galore_bridge$previous);
    }

    @Override
    public Matrix4f create_galore_bridge$previousPose() {
        return create_galore_bridge$previous;
    }

    @Override
    public int create_galore_bridge$matrixIndex() {
        return matrixIndex;
    }
}
