package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modonomicon always stacks up to 13 section tabs down the left of a book, whatever the window size, and puts the
 * scroll-down arrow at the bottom of the frame. The Field Guide has 15 sections, so at the default (auto) GUI scale
 * of a 1080p or 1440p screen the lower tabs run off the bottom of the screen and cover the arrow: Food and Farming
 * and Home, Storage and Maps can't be clicked. Show only as many tabs as fit above the arrow; the arrows scroll the
 * rest into view.
 */
@Mixin(targets = "com.klikli_dev.modonomicon.client.gui.book.node.BookParentNodeScreen", remap = false)
public abstract class ModonomiconCategoryTabsMixin {
    @Unique
    private int create_galore_bridge$visibleTabs = 13;

    @Shadow
    public abstract int getFrameHeight();

    @Shadow
    public abstract int getFrameThicknessH();

    // Arguments: x, y of the first tab, tab width, tab height, gap between tabs.
    @Inject(method = "updateCategoryButtons", at = @At("HEAD"))
    private void create_galore_bridge$fitTabs(int x, int y, int width, int height, int spacing, CallbackInfo ci) {
        // Modonomicon draws the scroll-down arrow at this y; keep the last tab clear of it.
        int arrowY = getFrameHeight() + getFrameThicknessH() - 10;
        create_galore_bridge$visibleTabs = Math.max(1, Math.min(13, (arrowY - 2 - y) / (height + spacing)));
    }

    @ModifyConstant(method = {"updateCategoryButtons", "lambda$updateCategoryButtons$9"}, constant = @Constant(intValue = 13))
    private int create_galore_bridge$tabCount(int thirteen) {
        return create_galore_bridge$visibleTabs;
    }
}
