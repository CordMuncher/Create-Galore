package dev.creategalore.bridge.mixin;

import com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler;
import dev.creategalore.bridge.client.ShipWaterCutaway;
import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegion;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the sea in a ship's hull by where the line of sight meets the water (see ShipWaterCutaway). */
@Mixin(value = WaterOcclusionRenderer.class, remap = false)
public abstract class ShipWaterCutawayMixin {
    @Shadow @Final private Set<SimpleCulledRenderRegion> regions;
    @Shadow private AdvancedFbo closeBuffer;
    @Shadow private AdvancedFbo farBuffer;
    @Shadow private Level level;

    @Shadow
    private void updateFramebuffers(boolean needed) {
        throw new AssertionError();
    }

    @Inject(method = "preRenderTranslucent", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$atTheWater(Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (!WaterOcclusionRenderer.isEnabled() || regions.isEmpty() || !ShipWaterCutaway.ready()) return;
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(level);
        if (container == null) return;
        boolean inside = container.isOccluded(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        // A camera inside a submerged hold keeps Sable's own pass: Deep Seas' fog in there reads its far buffer.
        if (inside && SubmarineFogHandler.shouldFog()) return;
        updateFramebuffers(true);
        ShipWaterCutaway.render(regions, closeBuffer, farBuffer, modelView, projection, inside);
        ci.cancel();
    }
}
