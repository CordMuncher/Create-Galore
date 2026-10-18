package dev.creategalore.bridge.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * One missing block in a tag file makes vanilla drop the whole tag. Create Deep Seas 3.4 ships tag entries for its
 * Abyss blocks (create_abyss:magma, burning_wood, ...) but doesn't register those blocks, so minecraft:mineable/pickaxe,
 * mineable/axe, mineable/shovel and minecraft:logs (and every tag built on them) came out empty: pickaxes, axes and
 * shovels were the wrong tool for everything. Skip a missing element (with a warning) instead of failing the tag.
 */
@Mixin(TagEntry.class)
public abstract class TagEntryMissingElementMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private ResourceLocation id;

    @Shadow
    @Final
    private boolean tag;

    @Shadow
    @Final
    private boolean required;

    @Inject(method = "build", at = @At("HEAD"), cancellable = true)
    private <T> void skipMissingElement(TagEntry.Lookup<T> lookup, Consumer<T> consumer, CallbackInfoReturnable<Boolean> cir) {
        if (tag) return;
        T element = lookup.element(id);
        if (element == null && required) LOGGER.warn("Create Galore: skipped missing tag entry {} (vanilla would drop the whole tag)", id);
        else if (element != null) consumer.accept(element);
        cir.setReturnValue(true);
    }
}
