package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.forge.world.WorldSearch;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the level a ForgeEndertech block search walks (see ChimneySearchLoadedMixin). */
@Mixin(value = WorldSearch.BlockChain.class, remap = false)
public interface ChimneySearchLevelAccessor {
    @Accessor("level")
    LevelAccessor create_galore_bridge$level();
}
