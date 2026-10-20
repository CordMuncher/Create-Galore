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

public class CatfishModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("catfish");

    public CatfishModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.6f, -0.15f);
        this.addSwimAnimation("rear", 0.6f, 0.15f);
        this.addSwimAnimation("rear/tail", 0.6f, 0.15f);
        this.addSwimAnimation("rear/tail/fin", 0.6f, 0.15f);
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -3.5F, -4.0F, 4.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(12, 13).addBox(0.0F, 0.0F, 0.5F, 0.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.5F, -2.0F));

        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 13).addBox(-1.0F, -0.5F, -4.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -5.5F, 1.75F, 0.0F, 3.1416F, 0.0F));
        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(12, 21).addBox(-1.5F, -1.5F, -2.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, -4.5F));

        head.addOrReplaceChild("whisker_lt", CubeListBuilder.create().texOffs(24, 25).addBox(-0.0793F, -0.5F, -0.1152F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 1.0F, -2.0F, -1.5708F, -0.1309F, 0.6109F));
        head.addOrReplaceChild("whisker_rt", CubeListBuilder.create().texOffs(26, 0).addBox(-2.9207F, -0.5F, -0.1152F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 1.0F, -2.0F, -1.5708F, 0.1309F, -0.6109F));
        PartDefinition whiskerLb = head.addOrReplaceChild("whisker_lb", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 1.0F, -2.0F, -0.6279F, -1.0782F, 0.6892F));
        whiskerLb.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(24, 24).addBox(-0.0761F, -0.5F, -0.1173F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0031F, 0.0F, 0.0021F, 0.0F, 0.0F, 0.6109F));
        PartDefinition whiskerRb = head.addOrReplaceChild("whisker_rb", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 1.0F, -2.0F, -0.6279F, 1.0782F, -0.6892F));
        whiskerRb.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(26, 1).addBox(-2.9239F, -0.5F, -0.1173F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0031F, 0.0F, 0.0021F, 0.0F, 0.0F, -0.6109F));

        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(26, 2).addBox(-0.0793F, -1.0F, -0.1152F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -0.5F, -2.5F, -0.397F, -1.1429F, 0.4318F));
        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(26, 4).addBox(-1.9207F, -1.0F, -0.1152F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, -0.5F, -2.5F, -0.397F, 1.1429F, -0.4318F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 22).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 3.0F));
        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 20).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(24, 13).addBox(0.0F, -2.0F, 0.0F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}