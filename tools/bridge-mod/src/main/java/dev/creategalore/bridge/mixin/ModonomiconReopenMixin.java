package dev.creategalore.bridge.mixin;

import com.klikli_dev.modonomicon.book.entries.BookContentEntry;
import com.klikli_dev.modonomicon.bookstate.BookVisualStateManager;
import com.klikli_dev.modonomicon.bookstate.visual.CategoryVisualState;
import com.klikli_dev.modonomicon.client.gui.book.BookAddress;
import com.klikli_dev.modonomicon.client.gui.book.BookCategoryScreen;
import com.klikli_dev.modonomicon.networking.SaveCategoryStateMessage;
import com.klikli_dev.modonomicon.platform.Services;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modonomicon reopens the page a book was closed on, and should open just the section map when the page was put away
 * first. It forgets that second part: opening a page clears the remembered page only on the client, and the server's
 * copy (still naming the page) comes back with the next sync and is saved again when the book closes. So once a
 * player closed the Field Guide with a page open, it opened on that page every time after. Tell the server too that
 * no page is open; closing the book on a page still remembers it as before.
 */
@Mixin(targets = "com.klikli_dev.modonomicon.client.gui.BookGuiManager", remap = false)
public abstract class ModonomiconReopenMixin {
    @Shadow
    public BookCategoryScreen openBookCategoryScreen;

    @Inject(method = "openContentEntry", at = @At("TAIL"))
    private void create_galore_bridge$forgetOpenEntry(BookContentEntry entry, BookAddress address, CallbackInfo ci) {
        if (openBookCategoryScreen == null || Minecraft.getInstance().player == null) return;
        CategoryVisualState state = BookVisualStateManager.get().getCategoryStateFor(Minecraft.getInstance().player,
                openBookCategoryScreen.getCategory());
        if (state != null) Services.NETWORK.sendToServer(new SaveCategoryStateMessage(openBookCategoryScreen.getCategory(), state));
    }
}
