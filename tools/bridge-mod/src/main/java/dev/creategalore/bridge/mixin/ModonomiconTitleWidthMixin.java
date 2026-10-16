package dev.creategalore.bridge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Modonomicon shrinks a page title to at most 120 px and centres it on the page, so a long title on a right-hand
 * page runs about 6 px into the stacked page edges of the book texture. The Field Guide narrows its text box with
 * book_text_offset_* in book.json, but titles ignore that, so cap them at 108 px instead (fits the clean paper on
 * both pages).
 */
@Mixin(targets = "com.klikli_dev.modonomicon.client.render.page.BookPageRenderer", remap = false)
public abstract class ModonomiconTitleWidthMixin {
    @ModifyConstant(method = "renderTitle", constant = @Constant(floatValue = 120.0f))
    private float create_galore_bridge$titleWidth(float width) {
        return 108.0f;
    }
}
