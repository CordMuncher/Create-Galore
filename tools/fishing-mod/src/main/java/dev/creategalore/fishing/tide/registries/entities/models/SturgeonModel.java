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

public class SturgeonModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("sturgeon");

    public SturgeonModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.075f, -1f);
        this.addSwimAnimation("front", speed, -0.07f);
        this.addSwimAnimation("rear", speed, 0.13f);
        this.addSwimAnimation("rear/tail", speed, 0.15f, -1f);
        this.addSwimAnimation("rear/tail/fin", speed, 0.17f, -1.5f);
    }

    @Override
    public float shadowRadius() {
        return 0.75f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.25f;
    }

    @Override
    public float swimAnimScale() {
        return 0.87f;
    }

    @Override
    public double xTiltScale() {
        return 1.85f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -7.5F, -9.0F, 7.0F, 7.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(32, 8).addBox(0.0F, -1.0F, -2.0F, 0.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(30, 16).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.25F, -9.0F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(42, 8).addBox(-2.0793F, 0.0F, -0.1152F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.75F, 2.25F, -0.25F, 0.0F, -1.5708F, -0.8727F));
        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(12, 42).addBox(-1.9207F, 0.0F, -0.1152F, 4.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.75F, 2.25F, -0.25F, 0.0F, 1.5708F, 0.8727F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 0).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.25F, -5.0F));
        head.addOrReplaceChild("whisker_l", CubeListBuilder.create().texOffs(38, 8).addBox(-1.0793F, 0.0F, -0.1152F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.75F, 2.0F, -2.25F, -0.5236F, -1.5708F, 0.0F));
        head.addOrReplaceChild("whisker_r", CubeListBuilder.create().texOffs(20, 42).addBox(-0.9207F, 0.0F, -0.1152F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.75F, 2.0F, -2.25F, -0.5236F, 1.5708F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 16).addBox(-2.5F, -3.0F, 0.0F, 5.0F, 6.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(16, 32).addBox(0.0F, -6.0F, 6.0F, 0.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 42).addBox(0.0F, 2.5F, 6.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 0.0F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 32).addBox(-1.5F, -2.5F, 0.0F, 3.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 10.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(30, 27).addBox(0.0F, -5.5F, 0.0F, 0.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}