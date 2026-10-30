package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.schematics.SchematicPrinter;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The build a Schematicannon prints, and which of its entities is next (for pre-glue, SchematicannonGlueMixin). */
@Mixin(value = SchematicPrinter.class, remap = false)
public interface SchematicPrinterAccessor {
    @Accessor("blockReader")
    SchematicLevel create_galore_bridge$schematic();

    @Accessor("printingEntityIndex")
    int create_galore_bridge$entityIndex();
}
