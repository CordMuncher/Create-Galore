package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.guideai.PlacedMoves;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the Field Guide's "placed by a player" mark on blocks Sable moves: assembling a ship or vehicle moves its
 * blocks into the ship's plot, and taking it apart moves them back into the world, both through moveBlocks.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SableMovePlacedMixin {
    @Unique
    private record create_galore_bridge$Move(ServerLevel from, ServerLevel to, List<BlockPos> olds, List<BlockPos> news) {
    }

    @Unique
    private static final ArrayDeque<create_galore_bridge$Move> create_galore_bridge$moves = new ArrayDeque<>();

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private static void create_galore_bridge$rememberPlaced(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                         Iterable<BlockPos> blocks, CallbackInfo ci) {
        List<BlockPos> olds = new ArrayList<>(), news = new ArrayList<>();
        if (PlacedMoves.active(level)) {
            for (BlockPos p : blocks) {
                if (!PlacedMoves.isPlaced(level, p)) continue;
                olds.add(p.immutable());
                news.add(transform.apply(p).immutable());
            }
        }
        create_galore_bridge$moves.push(new create_galore_bridge$Move(level, transform.getLevel(), olds, news));
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private static void create_galore_bridge$markLanded(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                     Iterable<BlockPos> blocks, CallbackInfo ci) {
        create_galore_bridge$Move move = create_galore_bridge$moves.poll();
        if (move == null) return;
        move.olds().forEach(p -> PlacedMoves.forget(move.from(), p));
        move.news().forEach(p -> PlacedMoves.mark(move.to(), p));
    }
}
