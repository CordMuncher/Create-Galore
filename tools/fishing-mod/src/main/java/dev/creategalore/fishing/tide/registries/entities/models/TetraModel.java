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
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class TetraModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("tetra");

    public TetraModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("rear", 0.6f, 0.4f);
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -0.9F, -4.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.1F))
                .texOffs(0, 6).addBox(0.0F, -2.9F, -4.0F, 0.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 3.0F));

        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(8, 6).addBox(-1.0F, -1.249F, -2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(13, -1).addBox(1.001F, -0.749F, -1.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(14, -1).mirror().addBox(-1.001F, -0.749F, -1.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.25F, -4.0F));

        root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(8, 10).addBox(0.0F, -1.9F, 0.0F, 0.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 3.0F));

        return LayerDefinition.create(mesh, 16, 16);
	}
}