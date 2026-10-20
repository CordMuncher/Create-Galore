/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.registries.entities.models;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.FastColor;

public class MirageCatfishModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("mirage_catfish");

    public MirageCatfishModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.6f, -0.15f);
        this.addSwimAnimation("rear", 0.6f, 0.15f);
        this.addSwimAnimation("rear/tail", 0.6f, 0.15f);
        this.addSwimAnimation("rear/tail/fin", 0.6f, 0.15f);
    }

    @Override
    public int tint() {
        return FastColor.ARGB32.color(150, 255, 255, 255);
    }

    @Override
    public boolean renderTranslucent() {
        return true;
    }
}