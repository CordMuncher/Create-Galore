package dev.creategalore.bridge.present;

import java.util.List;
import java.util.Set;

import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies the Perfect Presentation mixins only when it is switched on, so everyone else runs untouched code. */
public final class PresentationMixinPlugin implements IMixinConfigPlugin {
    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        apply = FMLEnvironment.dist.isClient() && Presentation.decide();
        if (FMLEnvironment.dist.isClient()) {
            // The DLAA shader pack copy has to exist before Iris first looks for the selected pack.
            dev.creategalore.bridge.dlss.PhotonPatcher.prepare();
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return apply;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
