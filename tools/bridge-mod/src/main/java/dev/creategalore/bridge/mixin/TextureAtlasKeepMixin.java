package dev.creategalore.bridge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the block atlas (and the other model atlases) in the texture manager. A client once lost
 * minecraft:textures/atlas/blocks.png mid-game: the next lookup found nothing, registered the purple-black placeholder
 * under that name, and Iris's terrain shader crashed casting the placeholder to an atlas. Nothing should ever drop
 * an atlas the model manager owns (they live until the game closes), so:
 * <ul>
 * <li>a release of one, or registering something else under its name, is refused (a release would also delete the
 * atlas's GL texture), and the caller is logged;</li>
 * <li>a lookup that finds an owned atlas missing puts it back instead of loading a placeholder;</li>
 * <li>a register or release from another thread is moved onto the render thread, because the manager's map is a
 * plain HashMap and a put racing a render-thread read can lose entries.</li>
 * </ul>
 */
@Mixin(TextureManager.class)
public abstract class TextureAtlasKeepMixin {
    private static final Logger create_galore_bridge$LOG = LoggerFactory.getLogger("create_galore_bridge");
    private static final Set<String> create_galore_bridge$warned = new HashSet<>();

    @Shadow @Final private Map<ResourceLocation, AbstractTexture> byPath;

    @Shadow public abstract void register(ResourceLocation path, AbstractTexture texture);

    @Shadow public abstract void release(ResourceLocation path);

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$keepAtlas(ResourceLocation path, CallbackInfo ci) {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            create_galore_bridge$warnOnce("release " + path, "released a texture off the render thread; moved to it");
            RenderSystem.recordRenderCall(() -> release(path));
            ci.cancel();
            return;
        }
        AbstractTexture texture = byPath.get(path);
        if (texture != null && texture == create_galore_bridge$ownedAtlas(path)) {
            create_galore_bridge$warnOnce("release " + path, "tried to release the model atlas " + path + "; kept it");
            ci.cancel();
        }
    }

    @Inject(method = "register(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V",
            at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$registerOnRenderThread(ResourceLocation path, AbstractTexture texture, CallbackInfo ci) {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            TextureAtlas atlas = create_galore_bridge$ownedAtlas(path);
            if (atlas != null && texture != atlas) {
                create_galore_bridge$warnOnce("register " + path, "tried to replace the model atlas " + path + "; kept it");
                ci.cancel();
            }
            return;
        }
        create_galore_bridge$warnOnce("register " + path.getNamespace(), "registered texture " + path + " off the render thread; moved to it");
        RenderSystem.recordRenderCall(() -> register(path, texture));
        ci.cancel();
    }

    @Inject(method = "getTexture(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/texture/AbstractTexture;",
            at = @At("HEAD"), cancellable = true)
    private void create_galore_bridge$restoreAtlas(ResourceLocation path, CallbackInfoReturnable<AbstractTexture> cir) {
        if (byPath.get(path) != null) return;
        TextureAtlas atlas = create_galore_bridge$ownedAtlas(path);
        if (atlas == null) return;
        create_galore_bridge$LOG.warn("Create Galore: the model atlas {} had gone missing from the texture manager; put it back", path);
        byPath.put(path, atlas);
        cir.setReturnValue(atlas);
    }

    /** The model manager's atlas for this path, or null if it has none (most textures aren't atlases). */
    private static TextureAtlas create_galore_bridge$ownedAtlas(ResourceLocation path) {
        if (!path.getPath().startsWith("textures/atlas/")) return null;
        Minecraft mc = Minecraft.getInstance();
        ModelManager models = mc == null ? null : mc.getModelManager();
        if (models == null) return null;
        try {
            return models.getAtlas(path);
        } catch (RuntimeException notAnAtlas) {
            return null;
        }
    }

    /** Logs once per key, with the stack, so the mod doing it shows up in the log. */
    private static void create_galore_bridge$warnOnce(String key, String what) {
        synchronized (create_galore_bridge$warned) {
            if (!create_galore_bridge$warned.add(key)) return;
        }
        create_galore_bridge$LOG.warn("Create Galore: something " + what + " (thread " + Thread.currentThread().getName() + ")",
                new Throwable("caller"));
    }
}
