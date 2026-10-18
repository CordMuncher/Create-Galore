package dev.creategalore.bridge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Berries and Cherries plants hurt anyone without armor who walks through them, like a sweet berry bush. Grapevines,
 * blueberry and strawberry bushes and its cherry and oak leaves have no thorns, so they only slow you down now.
 * Raspberry bushes still prick.
 */
@Mixin(targets = {
    "net.mcreator.berriesandcherries.block.BlackGrapeTreeBottomBlock",
    "net.mcreator.berriesandcherries.block.BlackGrapeTreeTopBlock",
    "net.mcreator.berriesandcherries.block.NoneBlackGrapeTreeBottomBlock",
    "net.mcreator.berriesandcherries.block.NoneBlackGrapeTreeTopBlock",
    "net.mcreator.berriesandcherries.block.GreenGrapeTreeBottomBlock",
    "net.mcreator.berriesandcherries.block.GreenGrapeTreeTopBlock",
    "net.mcreator.berriesandcherries.block.NoneGreenGrapeTreeBottomBlock",
    "net.mcreator.berriesandcherries.block.NoneGreenGrapeTreeTopBlock",
    "net.mcreator.berriesandcherries.block.BlueberryBushBottomBlock",
    "net.mcreator.berriesandcherries.block.BlueberryBushTopBlock",
    "net.mcreator.berriesandcherries.block.NoneBlueberryBushBottomBlock",
    "net.mcreator.berriesandcherries.block.NoneBlueberryBushTopBlock",
    "net.mcreator.berriesandcherries.block.StrawberrybushBlock",
    "net.mcreator.berriesandcherries.block.StrawberryBushNoneBlock",
    "net.mcreator.berriesandcherries.block.CherryLeavesBlock",
    "net.mcreator.berriesandcherries.block.OakLeavesBlock"
}, remap = false)
public abstract class BerryBushNoThornsMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void createGalore$noThorns(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
