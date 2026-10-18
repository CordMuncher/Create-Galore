package dev.creategalore.bridge.mixin;

import com.enzonic.aeronauticswinds.client.GliderCameraHandler;
import com.enzonic.aeronauticswinds.config.WindsConfig;
import com.enzonic.aeronauticswinds.sky.WindGliderManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aeronautics Winds 1.3.1 centres the third-person camera on a gliding player and pulls it back to its
 * gliderThirdPersonDistance, but its own Camera mixin shadows move(double, double, double) and getMaxZoom(double),
 * while 1.21.1's Camera has float versions. Mixin can't find them and drops the whole mixin at startup, so the glider
 * camera options do nothing. This is the same code with the 1.21.1 signatures.
 */
@Mixin(targets = "net.minecraft.client.Camera")
public abstract class GliderCameraMixin {
    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Shadow
    protected abstract float getMaxZoom(float maxZoom);

    @Inject(method = "setup", at = @At("TAIL"))
    private void create_galore_bridge$gliderCamera(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        if (!detached || entity == null || !WindsConfig.CLIENT.centerGliderCameraThirdPerson.get()) {
            return;
        }
        float progress;
        if (entity instanceof Player player && player.isLocalPlayer()) {
            progress = GliderCameraHandler.getProgress(partialTick);
        } else {
            progress = entity instanceof Player player && WindGliderManager.isGliding(player) ? 1.0f : 0.0f;
        }
        if (progress <= 0.001f) {
            return;
        }
        double verticalOffset = WindsConfig.CLIENT.gliderThirdPersonVerticalOffset.get() * progress;
        double distance = Mth.lerp(progress, 4.0, WindsConfig.CLIENT.gliderThirdPersonDistance.get());
        double eye = Mth.lerp(partialTick, eyeHeightOld, eyeHeight);
        setPosition(Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()) + eye + verticalOffset,
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
        move(-getMaxZoom((float) distance), 0.0f, 0.0f);
    }
}
