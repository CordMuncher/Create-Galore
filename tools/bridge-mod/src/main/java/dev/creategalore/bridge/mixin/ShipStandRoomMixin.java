package dev.creategalore.bridge.mixin;

import dev.creategalore.bridge.ShipStandRoom;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Standing on a turning ship dropped players into the crawl pose with plenty of headroom (see {@link ShipStandRoom}).
 * Next to a ship the pose test uses the ship where it is now and only the middle of the player; everywhere else it is
 * Minecraft's own test, with Sable's ship check, unchanged.
 */
@Mixin(Player.class)
public abstract class ShipStandRoomMixin extends LivingEntity {
    protected ShipStandRoomMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$shipStandRoom(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        AABB box = this.getDimensions(pose).makeBoundingBox(this.position()).deflate(1.0E-7);
        if (!ShipStandRoom.nearShip(this.level(), box)) return;
        cir.setReturnValue(this.level().noCollision(this, box) && ShipStandRoom.roomOnShips(this.level(), box));
    }
}
