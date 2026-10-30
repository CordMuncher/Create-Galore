package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.schematics.SchematicItem;
import dev.creategalore.bridge.SharedSchematics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Schematic's build kept with the paper when it is written, and read from there ({@link SharedSchematics}). */
@Mixin(value = SchematicItem.class, remap = false)
public abstract class SchematicSharedMixin {
    @Inject(method = "create", at = @At("RETURN"))
    private static void create_galore_bridge$keepBuild(Level level, String schematic, String owner, CallbackInfoReturnable<ItemStack> cir) {
        SharedSchematics.stamp(level, cir.getReturnValue());
    }

    @Inject(method = "loadSchematic", at = @At("HEAD"), cancellable = true)
    private static void create_galore_bridge$sharedBuild(Level level, ItemStack stack, CallbackInfoReturnable<StructureTemplate> cir) {
        StructureTemplate template = SharedSchematics.load(level, stack);
        if (template != null) cir.setReturnValue(template);
    }
}
