package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import dev.creategalore.bridge.guideai.PlacedMoves;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Field Guide's "placed by a player" mark on blocks that ride a Create contraption (trains, bearings,
 * pistons, gantries, elevators): remembered when the contraption lifts them out of the world, saved with the
 * contraption, and marked again where they land when it is taken apart.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionPlacedMixin {
    @Unique
    private static final String create_galore_bridge$KEY = "create_galore_bridge:placed";

    @Shadow
    public BlockPos anchor;
    @Shadow
    protected Map<BlockPos, StructureBlockInfo> blocks;

    /** Contraption-local positions of the blocks a player placed. */
    @Unique
    private LongOpenHashSet create_galore_bridge$placed = new LongOpenHashSet();

    @Inject(method = "removeBlocksFromWorld", at = @At("HEAD"))
    private void create_galore_bridge$rememberPlaced(Level world, BlockPos offset, CallbackInfo ci) {
        if (!PlacedMoves.active(world)) return;
        for (BlockPos local : blocks.keySet())
            if (PlacedMoves.isPlaced(world, local.offset(anchor).offset(offset))) create_galore_bridge$placed.add(local.asLong());
    }

    @Inject(method = "removeBlocksFromWorld", at = @At("RETURN"))
    private void create_galore_bridge$forgetLifted(Level world, BlockPos offset, CallbackInfo ci) {
        for (LongIterator it = create_galore_bridge$placed.iterator(); it.hasNext(); )
            PlacedMoves.forget(world, BlockPos.of(it.nextLong()).offset(anchor).offset(offset));
    }

    @Inject(method = "addBlocksToWorld", at = @At("RETURN"))
    private void create_galore_bridge$markLanded(Level world, StructureTransform transform, CallbackInfo ci) {
        if (create_galore_bridge$placed.isEmpty() || !PlacedMoves.tracking()) return;
        for (LongIterator it = create_galore_bridge$placed.iterator(); it.hasNext(); )
            PlacedMoves.mark(world, transform.apply(BlockPos.of(it.nextLong())));
        create_galore_bridge$placed.clear();
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void create_galore_bridge$save(HolderLookup.Provider registries, boolean spawnPacket, CallbackInfoReturnable<CompoundTag> cir) {
        if (!spawnPacket && !create_galore_bridge$placed.isEmpty())
            cir.getReturnValue().putLongArray(create_galore_bridge$KEY, create_galore_bridge$placed.toLongArray());
    }

    @Inject(method = "readNBT", at = @At("HEAD"))
    private void create_galore_bridge$load(Level world, CompoundTag nbt, boolean spawnData, CallbackInfo ci) {
        if (nbt.contains(create_galore_bridge$KEY)) create_galore_bridge$placed = new LongOpenHashSet(nbt.getLongArray(create_galore_bridge$KEY));
    }
}
