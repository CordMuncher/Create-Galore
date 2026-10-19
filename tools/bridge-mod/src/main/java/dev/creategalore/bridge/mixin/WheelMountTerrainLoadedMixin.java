package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.creategalore.bridge.ChunkLoadGuard;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Each physics tick a wheel mount casts 5 blocks down to find the ground. On an airship flying over land that is
 * still being generated (a Discovery flyover with landing gear), that cast read the unfinished chunk and the server
 * thread waited for it (6 s in a test). A cast that starts or ends in a chunk that isn't ready now finds no ground,
 * which is what a wheel high above the terrain finds anyway; the next tick casts again.
 */
@Mixin(value = WheelMountBlockEntity.class, remap = false)
public abstract class WheelMountTerrainLoadedMixin {
    @WrapOperation(method = "computeMaxExtensionToTerrain", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private BlockHitResult create_galore_bridge$castOnlyIntoLoaded(Level level, ClipContext context, Operation<BlockHitResult> original) {
        SubLevel ship = Sable.HELPER.getContaining((WheelMountBlockEntity) (Object) this);
        Vec3 from = ship == null ? context.getFrom() : ship.logicalPose().transformPosition(context.getFrom());
        Vec3 to = ship == null ? context.getTo() : ship.logicalPose().transformPosition(context.getTo());
        if (!ChunkLoadGuard.ready(level, BlockPos.containing(from)) || !ChunkLoadGuard.ready(level, BlockPos.containing(to))) {
            return BlockHitResult.miss(context.getTo(), Direction.UP, BlockPos.containing(context.getTo()));
        }
        return original.call(level, context);
    }
}
