package dev.creategalore.fishing.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The falling loot crate (Tide's {@code LootCrateEntity}) sets and reads the block it carries. */
@Mixin(value = FallingBlockEntity.class, remap = false)
public interface FallingBlockEntityAccessor {
    @Accessor("blockState")
    BlockState cgfish$getBlockState();

    @Accessor("blockState")
    void cgfish$setBlockState(BlockState state);
}
