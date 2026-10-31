package dev.creategalore.bridge.mixin;

import com.endertech.minecraft.mods.adpother.blocks.FilterFrame;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.creategalore.bridge.CreateGaloreBridge;
import dev.creategalore.bridge.FilterUpkeep;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A filter frame's block entity: always working, takes no filter material, shows a Precision filter inside, and uses
 * up whatever material and byproduct an older frame still held. Engineer's Goggles show the pressure under it.
 */
@Mixin(value = FilterFrame.BlockTile.class, remap = false)
public abstract class PotrFilterTileMixin implements FilterUpkeep.Budget, IHaveGoggleInformation {
    @Unique
    private long create_galore_bridge$second = -1;
    @Unique
    private int create_galore_bridge$used;

    @Override
    public long create_galore_bridge$second() {
        return create_galore_bridge$second;
    }

    @Override
    public int create_galore_bridge$used() {
        return create_galore_bridge$used;
    }

    @Override
    public void create_galore_bridge$set(long second, int used) {
        create_galore_bridge$second = second;
        create_galore_bridge$used = used;
    }

    /** Engineer's Goggles read the frame: green, yellow or red for the pressure under it (see FilterPressure). */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        FilterFrame.BlockTile tile = (FilterFrame.BlockTile) (Object) this;
        if (tile.getLevel() == null || !tile.getLevel().isClientSide) return false;
        return dev.creategalore.bridge.client.FilterGoggles.addToTooltip(tile.getBlockPos(), tooltip, isPlayerSneaking);
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$alwaysActive(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(((FilterFrame.BlockTile) (Object) this).getLevel() != null);
    }

    @Inject(method = "isValidFilterMaterial", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$noMaterial(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "getRenderMaterial", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$showFilter(CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(CreateGaloreBridge.FILTER_MEDIA.get().getDefaultInstance());
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void create_galore_bridge$useUpOldContents(CallbackInfo ci) {
        FilterFrame.BlockTile tile = (FilterFrame.BlockTile) (Object) this;
        var inventory = tile.getTileInventory();
        boolean changed = false;
        for (int slot = 0; slot < 2; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        Level level = tile.getLevel();
        // The frame's own tick then clears its fullness and dirty look.
        if (changed && level != null) level.scheduleTick(tile.getBlockPos(), tile.getBlockState().getBlock(), 1);
    }
}
