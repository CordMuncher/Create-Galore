package dev.creategalore.bridge;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Diagonal Fences (Fuzs) gives every plain fence a twin block, diagonalfences:&lt;namespace&gt;/&lt;path&gt;, that can also
 * join diagonally, and the fence item places the twin. Both are the same fence to a player: one item, one drop, and a
 * sneak-right-click with an empty hand turns one into the other. Looked up by name, so nothing breaks without the mod.
 */
public final class DiagonalJoins {
    /** The Diagonal Blocks family (walls and windows work the same way, should they join the pack). */
    private static final List<String> KINDS = List.of("fences", "walls", "windows");

    private DiagonalJoins() {}

    /** The block a player's item would place for this state: the diagonal twin, or the state itself. */
    public static BlockState placed(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id.getNamespace().startsWith("diagonal")) return state;
        for (String kind : KINDS) {
            ResourceLocation twin = ResourceLocation.fromNamespaceAndPath("diagonal" + kind, id.getNamespace() + "/" + id.getPath());
            Block block = BuiltInRegistries.BLOCK.getOptional(twin).orElse(null);
            if (block == null) continue;
            // A fence the pack keeps straight still places itself.
            if (state.is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("diagonal" + kind, "non_diagonal_" + kind))))
                return state;
            return block.withPropertiesOf(state);
        }
        return state;
    }

    /** The plain block behind a diagonal twin (oak fence for diagonalfences:minecraft/oak_fence), else the block itself. */
    public static Block plain(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        int slash = id.getPath().indexOf('/');
        String ns = id.getNamespace();
        if (!ns.startsWith("diagonal") || !KINDS.contains(ns.substring("diagonal".length())) || slash < 0) return block;
        ResourceLocation base = ResourceLocation.tryBuild(id.getPath().substring(0, slash), id.getPath().substring(slash + 1));
        return base == null ? block : BuiltInRegistries.BLOCK.getOptional(base).orElse(block);
    }

    /** The plain fence in the plain fence's shape, for naming a block to the book or a player. */
    public static BlockState plain(BlockState state) {
        Block block = plain(state.getBlock());
        return block == state.getBlock() ? state : block.withPropertiesOf(state);
    }
}
