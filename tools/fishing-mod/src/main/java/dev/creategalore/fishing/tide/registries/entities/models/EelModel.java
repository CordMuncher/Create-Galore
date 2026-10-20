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

public class EelModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("eel");

    public EelModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.15f, -1f);
        this.addSwimAnimation("rear", speed, 0.25f, -1f);
        this.addSwimAnimation("rear/tail", speed, 0.25f, -1.5f);
    }

    @Override
    public float swimAnimSpeed() {
        return 0.3f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(2, 26).addBox(-0.5F, -2.0F, -2.0F, 1.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(0, -7).addBox(0.0F, -3.0F, 0.0F, 0.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, -4.0F));

        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(32, 31).mirror().addBox(-0.1F, 0.0F, -0.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.5F, -1.0F, -2.0F, 0.0F, -0.5236F, 0.0F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(32, 31).addBox(0.1F, 0.0F, -0.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -1.0F, -2.0F, 0.0F, 0.5236F, 0.0F));
        front.addOrReplaceChild("fin_r2", CubeListBuilder.create().texOffs(34, 14).addBox(0.5F, 0.0F, 0.0F, 0.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 5.0F, -0.176F, -0.2175F, 0.0078F));
        front.addOrReplaceChild("fin_l2", CubeListBuilder.create().texOffs(34, 14).addBox(-0.5F, 0.0F, 0.0F, 0.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 5.0F, -0.176F, 0.2175F, -0.0078F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(20, 1).addBox(-0.5F, -1.5F, -3.0F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(34, 8).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, -2.0F));

        PartDefinition nose2 = head.addOrReplaceChild("nose2", CubeListBuilder.create(), PartPose.offsetAndRotation(1.0F, 0.5F, -3.0F, 0.5672F, 0.0F, 0.0F));
        nose2.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(34, 5).addBox(-1.5F, 0.0F, -2.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.4363F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(24, 24).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(0, 5).addBox(0.0F, -3.0F, 0.0F, 0.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 3.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 31).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(15, 5).addBox(0.0F, -2.5F, 0.0F, 0.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, 5.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}