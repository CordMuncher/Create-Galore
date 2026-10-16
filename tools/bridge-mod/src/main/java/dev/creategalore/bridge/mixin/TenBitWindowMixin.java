package dev.creategalore.bridge.mixin;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import dev.creategalore.bridge.TenBitWindow;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks {@link TenBitWindow} around the game window's creation (NeoForge hands creation to ImmediateWindowHandler). */
@Mixin(targets = "com.mojang.blaze3d.platform.Window", remap = false)
public abstract class TenBitWindowMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private long create_galore_bridge$tenBitHints(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        TenBitWindow.beforeCreate();
        return ImmediateWindowHandler.setupMinecraftWindow(width, height, title, monitor);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void create_galore_bridge$tenBitReport(CallbackInfo ci) {
        TenBitWindow.afterCreate();
    }
}
