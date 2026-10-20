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

public class SleepyCarpModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("sleepy_carp");

    public SleepyCarpModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.2f, -0.15f);
        this.addSwimAnimation("rear", 0.2f, 0.25f);
        this.addSwimAnimation("rear/tail", 0.2f, 0.25f);
    }

    @Override
    public float swimAnimSpeed() {
        return 0.2f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(2, 3).addBox(-2.0F, -3.5F, -3.0F, 4.0F, 4.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(16, 14).addBox(0.0F, 0.5F, -1.5F, 0.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.5F, -1.0F));
        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(1, 15).addBox(-1.0F, -0.5F, -3.0F, 0.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -6.0F, 1.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(16, 22).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, -3.0F));

        PartDefinition whisker_l = head.addOrReplaceChild("whisker_l", CubeListBuilder.create(), PartPose.offsetAndRotation(1.25F, 1.0F, -2.5F, 0.0F, -1.1781F, 0.0F));
        whisker_l.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(24, 13).addBox(-0.0761F, -0.5F, -0.1173F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0031F, 0.0F, 0.0021F, 0.0F, 0.0F, 0.6109F));

        PartDefinition whisker_r = head.addOrReplaceChild("whisker_r", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.25F, 1.0F, -2.5F, 0.0F, 1.1781F, 0.0F));
        whisker_r.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(8, 25).addBox(-1.9239F, -0.5F, -0.1173F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0031F, 0.0F, 0.0021F, 0.0F, 0.0F, -0.6109F));

        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(0, 25).addBox(-0.0793F, -0.25F, -0.1152F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -1.5F, -1.5F, 0.0F, -1.1781F, 0.0F));
        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(4, 25).addBox(-1.9207F, -0.25F, -0.1152F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, -1.5F, -1.5F, 0.0F, 1.1781F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(17, 5).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 3.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(25, 0).addBox(0.0F, -2.5F, 0.0F, 0.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}