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

public class GuppyModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("guppy");

    public GuppyModel(EntityRendererProvider.Context context) {
        this(context, MODEL_LOCATION);
    }

    public GuppyModel(EntityRendererProvider.Context context, ModelLayerLocation modelLocation) {
        super(context, modelLocation);
        this.addSwimAnimation("front/head", 0.6f, -0.15f);
        this.addSwimAnimation("rear", 0.6f, 0.25f);
        this.addSwimAnimation("rear/tail", 0.6f, 0.25f);
    }

    @Override
    public float shadowRadius() {
        return 0.15f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.1F))
                .texOffs(8, 0).addBox(-0.5F, -0.5F, -0.75F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(6, 4).addBox(-0.5F, -1.75F, -0.25F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 24.0F, -2.0F));

        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 10).addBox(-0.5F, -0.5F, -0.75F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, -0.5F, -1.25F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(6, 8).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.5F, 0.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 4).addBox(0.0F, -1.5F, 0.0F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 1.0F));

        return LayerDefinition.create(mesh, 16, 16);
	}
}