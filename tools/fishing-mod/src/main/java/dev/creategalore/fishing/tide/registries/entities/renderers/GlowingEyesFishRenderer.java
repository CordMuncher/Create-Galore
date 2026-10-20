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
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;

public class GlowingEyesFishRenderer<M extends FishModel> extends FishRenderer<M> {
    public GlowingEyesFishRenderer(String key, M model, EntityRendererProvider.Context context) {
        super(key, model, context);
        this.addLayer(new GlowingEyesLayer<>(this));
    }
}
