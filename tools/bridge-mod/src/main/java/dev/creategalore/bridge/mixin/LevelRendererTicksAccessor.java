package dev.creategalore.bridge.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The level renderer's tick count, Flywheel's clock (see ObjectMotion). */
@Mixin(LevelRenderer.class)
public interface LevelRendererTicksAccessor {
    @Accessor("ticks")
    int create_galore_bridge$ticks();
}
