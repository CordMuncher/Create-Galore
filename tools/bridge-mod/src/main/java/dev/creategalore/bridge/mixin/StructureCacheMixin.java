package dev.creategalore.bridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import dev.creategalore.bridge.StructureFixes;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Old structure files come from the converted-file cache instead of DFU on the server thread (see StructureFixes).
 * Reading a file goes through StructureFixes.readNoting, which reads it as NbtIo.readCompressed does and notes its key.
 */
@Mixin(StructureTemplateManager.class)
public abstract class StructureCacheMixin {
    @WrapOperation(method = "readStructure(Ljava/io/InputStream;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;readCompressed(Ljava/io/InputStream;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag noteFile(InputStream in, NbtAccounter accounter, Operation<CompoundTag> original) throws IOException {
        return StructureFixes.readNoting(in);
    }

    @WrapOperation(method = "readStructure(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/datafix/DataFixTypes;updateToCurrentVersion(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag useConverted(DataFixTypes type, DataFixer fixer, CompoundTag tag, int version, Operation<CompoundTag> original) {
        return StructureFixes.upgrade(tag, version, () -> original.call(type, fixer, tag, version));
    }
}
