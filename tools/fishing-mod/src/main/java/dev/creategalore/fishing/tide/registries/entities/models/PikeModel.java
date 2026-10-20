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

public class PikeModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("pike");

    public PikeModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.6f, -0.15f);
        this.addSwimAnimation("rear", 0.6f, 0.25f);
        this.addSwimAnimation("rear/tail", 0.6f, 0.25f);
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -4.5F, -7.0F, 3.0F, 5.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(20, 7).addBox(0.0F, 0.0F, 2.5F, 0.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, 0.0F));

        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 11).mirror().addBox(1.0F, -0.5F, -4.0F, 0.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(1.0F, -6.5F, 4.0F, 0.0F, -3.1416F, 0.0F));
        front.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 11).addBox(-1.0F, -0.5F, -4.0F, 0.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -6.5F, 4.0F, 0.0F, 3.1416F, 0.0F));
        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(18, 19).addBox(-1.0F, -1.5F, -2.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.75F, -7.5F));
        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(8, 26).addBox(-3.3827F, -1.0F, 0.0761F, 3.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, -0.5F, -4.0F, -1.1802F, 0.7688F, -1.0959F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(0, 2).mirror().addBox(0.3827F, -1.0F, 0.0761F, 3.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(1.25F, -0.5F, -4.0F, -1.1802F, -0.7688F, 1.0959F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(18, 25).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.5F, 7.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 19).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}