package dev.creategalore.bridge.mixin;

import com.simibubi.create.content.schematics.client.SchematicHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Create sets up a held Schematic again when this name changes; null makes it load the build again. */
@Mixin(value = SchematicHandler.class, remap = false)
public interface SchematicHandlerAccessor {
    @Accessor("displayedSchematic")
    void create_galore_bridge$setDisplayedSchematic(String name);
}
