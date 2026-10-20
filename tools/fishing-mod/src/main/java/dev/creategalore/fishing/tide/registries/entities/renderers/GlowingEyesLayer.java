/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.renderers;

import dev.creategalore.fishing.tide.registries.entities.models.FishModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.world.entity.Mob;

public class GlowingEyesLayer<M extends FishModel> extends EyesLayer<Mob, M> {
    private final RenderType eyeRenderType;

    public GlowingEyesLayer(RenderLayerParent<Mob, M> parent) {
        super(parent);
        this.eyeRenderType = RenderType.beaconBeam(parent.getTextureLocation(null).withPath(original ->
                original.substring(0, original.indexOf(".png")) + "_eyes.png"), true);
    }

    public RenderType renderType() {
            return eyeRenderType;
        }
}
