package dev.creategalore.bridge.mixin;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tide uses up one bait per fish by shrinking the bait stacks inside the rod's bait component in place. Those stacks
 * belong to the component the rod already holds, and Tide caches how that component is saved, so the save kept the
 * old count: bait went down while playing and came back in full after a relog or restart (Tide issues #23 and #61).
 * Work on copies so the rod gets a new component with the lower count.
 */
@Mixin(targets = "com.li64.tide.data.rods.BaitContents$Mutable", remap = false)
public abstract class TideBaitCopyMixin {
    @Shadow @Final private List<ItemStack> items;

    @Inject(method = "<init>(Lcom/li64/tide/data/rods/BaitContents;)V", at = @At("RETURN"))
    private void create_galore_bridge$copyStacks(CallbackInfo ci) {
        items.replaceAll(ItemStack::copy);
    }
}
