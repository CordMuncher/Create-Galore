/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public abstract class TideRenderTypes extends RenderType {
    // Extends RenderType only to reach its protected state shards; never instantiated.
    private TideRenderTypes() {
        super("", null, null, 0, false, false, null, null);
    }

    private static final Function<ResourceLocation, RenderType> FULL_WHITE_RENDER_TYPE;
    private static final Function<ResourceLocation, RenderType> FULL_WHITE_ITEM_RENDER_TYPE;
    private static final RenderStateShard.ShaderStateShard FULL_WHITE_SHADER;
    private static final RenderStateShard.ShaderStateShard FULL_WHITE_ITEM_SHADER;

    static {
        FULL_WHITE_SHADER = new RenderStateShard.ShaderStateShard(TideCoreShaders::fullWhite);
        FULL_WHITE_ITEM_SHADER = new RenderStateShard.ShaderStateShard(TideCoreShaders::fullWhiteItem);

        FULL_WHITE_RENDER_TYPE = Util.memoize(tex -> RenderType.create(
                "full_white",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                786432,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                        .setShaderState(FULL_WHITE_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .createCompositeState(false))
        );
        FULL_WHITE_ITEM_RENDER_TYPE = Util.memoize(tex -> RenderType.create(
                "full_white_item",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                786432,
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                        .setShaderState(FULL_WHITE_ITEM_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .createCompositeState(false))
        );
    }

    public static RenderType singleColor(ResourceLocation location) {
        return FULL_WHITE_RENDER_TYPE.apply(location);
    }

    @SuppressWarnings("deprecation")
    public static RenderType singleColorItem() {
        return FULL_WHITE_ITEM_RENDER_TYPE.apply(TextureAtlas.LOCATION_BLOCKS);
    }
}