package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.DiagonalJoins;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Sable gives blocks their mass and other physics properties by walking each tag's block list once, and a Diagonal
 * Fences twin (the block a fence item places) only answers to its plain fence's tags when asked, so it isn't in those
 * lists: every fence a player put on a ship weighed a full block (1.0) instead of a fence's 0.25. After Sable's pass,
 * each twin takes the properties of its plain block, in the same priority order.
 */
@Mixin(value = PhysicsBlockPropertiesDefinitionLoader.class, remap = false)
public abstract class SableDiagonalTwinPropertiesMixin {
    @Inject(method = "applyAll", at = @At("TAIL"))
    private void create_galore_bridge$twinsLikePlain(CallbackInfo ci) {
        List<Block[]> twins = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Block plain = DiagonalJoins.plain(block);
            if (plain != block) twins.add(new Block[] {block, plain});
        }
        if (twins.isEmpty()) return;
        for (PhysicsBlockPropertiesDefinition definition : ((PhysicsBlockPropertiesDefinitionLoader) (Object) this).getDefinitions()) {
            TagKey<Block> tag = definition.selector().tag() ? TagKey.create(Registries.BLOCK, definition.selector().id()) : null;
            for (Block[] pair : twins) {
                Block plain = pair[1];
                boolean matches = tag != null ? plain.builtInRegistryHolder().is(tag)
                        : BuiltInRegistries.BLOCK.getKey(plain).equals(definition.selector().id());
                if (!matches) continue;
                for (BlockState state : pair[0].getStateDefinition().getPossibleStates())
                    ((BlockStateExtension) state).sable$loadProperties(pair[0].getStateDefinition(), definition);
            }
        }
    }
}
