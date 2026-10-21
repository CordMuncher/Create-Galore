/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.loaders.neoforge;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import dev.creategalore.fishing.tide.client.gui.TideGuiOverlays;
import dev.creategalore.fishing.tide.Tide;
import dev.creategalore.fishing.tide.client.FishDisplayRenderer;
import dev.creategalore.fishing.tide.client.TideCoreShaders;
import dev.creategalore.fishing.tide.client.TideItemModelProperties;
import dev.creategalore.fishing.tide.client.VoidParticleSpawner;
import dev.creategalore.fishing.tide.data.DoubleJumper;
import dev.creategalore.fishing.tide.events.TideClientEventHandler;
import dev.creategalore.fishing.tide.registries.TideBlockEntities;
import dev.creategalore.fishing.tide.registries.TideEntityModels;
import dev.creategalore.fishing.tide.registries.TideItems;
import dev.creategalore.fishing.tide.registries.TideParticleTypes;
import dev.creategalore.fishing.tide.registries.particles.MagicChain;
import dev.creategalore.fishing.tide.registries.particles.VoidRipple;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client setup of the ported Tide content, wired explicitly from {@code FishingClient} (Tide used annotation
 * subscribers under its own mod id). Without Tide's rod: no Angling Table screen, rod tooltips or config screen. The
 * information HUD is a GUI layer instead of Tide's mixin into {@code Gui}.
 */
public final class NeoforgeClientEntrypoint {
    private NeoforgeClientEntrypoint() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(NeoforgeClientEntrypoint::onClientSetup);
        modBus.addListener(NeoforgeClientEntrypoint::registerShaders);
        modBus.addListener(NeoforgeClientEntrypoint::registerParticleProviders);
        modBus.addListener(NeoforgeClientEntrypoint::registerEntityModels);
        modBus.addListener(NeoforgeClientEntrypoint::registerEntityRenderers);
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAboveAll(Tide.resource("information"), (graphics, delta) -> TideGuiOverlays.render(graphics)));
        NeoForge.EVENT_BUS.addListener(NeoforgeClientEntrypoint::itemTooltipEvent);
        NeoForge.EVENT_BUS.addListener(NeoforgeClientEntrypoint::endClientTick);
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(TideItemModelProperties::registerAll);
    }

    private static void registerShaders(final RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Tide.resource("full_white"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), instance -> TideCoreShaders.FULL_WHITE = instance);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Tide.resource("full_white_item"),
                    DefaultVertexFormat.NEW_ENTITY), instance -> TideCoreShaders.FULL_WHITE_ITEM = instance);
        } catch (Exception e) {
            Tide.LOG.error("Failed to load Tide shaders! {}", e.toString());
        }
    }

    private static void registerParticleProviders(final RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(TideParticleTypes.VOID_RIPPLE_LARGE, VoidRipple.LargeProvider::new);
        event.registerSpriteSet(TideParticleTypes.VOID_RIPPLE_SMALL, VoidRipple.SmallProvider::new);
        event.registerSpriteSet(TideParticleTypes.MAGIC_CHAIN, MagicChain.Provider::new);
    }

    private static void registerEntityModels(final EntityRenderersEvent.RegisterLayerDefinitions event) {
        if (TideEntityModels.LAYER_DEFS.isEmpty()) TideEntityModels.init();
        TideEntityModels.LAYER_DEFS.forEach(event::registerLayerDefinition);
    }

    private static void registerEntityRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        if (TideEntityModels.RENDERERS.isEmpty()) TideEntityModels.init();
        TideEntityModels.RENDERERS.forEach(reg -> registerEntityRenderer(reg, event));
        event.registerBlockEntityRenderer(TideBlockEntities.FISH_DISPLAY, FishDisplayRenderer::new);
    }

    public static <T extends Entity> void registerEntityRenderer(TideEntityModels.RendererRegistration<T> reg, EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(reg.entityType(), reg.renderer());
    }

    private static void itemTooltipEvent(final ItemTooltipEvent event) {
        TideClientEventHandler.onTooltipRender(event.getItemStack(), event.getToolTip());
    }

    private static void endClientTick(final ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!Minecraft.getInstance().isPaused() && player.getRandom().nextFloat() > 0.5f) {
            VoidParticleSpawner.spawnParticles(Minecraft.getInstance().player, 1);
        }
        if (player.getItemBySlot(EquipmentSlot.FEET).is(TideItems.DRAGONFIN_BOOTS)
                && player instanceof DoubleJumper jumper) {
            if (!jumper.tide$hasDoubleJump() && player.onGround()) jumper.tide$restoreDoubleJump();
            if (player.input.jumping) jumper.tide$doDoubleJump();
            jumper.tide$setWasJumpPressed(player.input.jumping);
        }
    }
}
