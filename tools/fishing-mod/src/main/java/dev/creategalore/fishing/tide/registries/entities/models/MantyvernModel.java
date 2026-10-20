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

public class MantyvernModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("mantyvern");

    public MantyvernModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = this.swimAnimSpeed();
        this.addSwimAnimation("front/head", speed * 2, 0.15f);
        this.addSwimAnimation("front/left", speed, 0.3f, SwimAxis.Z);
        this.addSwimAnimation("front/left/fin", speed, 0.4f, -0.5f, SwimAxis.Z);
        this.addSwimAnimation("front/right", speed, -0.3f, SwimAxis.Z);
        this.addSwimAnimation("front/right/fin", speed, -0.4f, -0.5f, SwimAxis.Z);
        this.addSwimAnimation("rear", speed, -0.12f, SwimAxis.X);
        this.addSwimAnimation("rear/tail", speed, -0.15f, -0.5f, SwimAxis.X);
        this.addSwimAnimation("rear/tail/fin", speed, -0.15f, -1f, SwimAxis.X);
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    @Override
    public float shadowRadius() {
        return 1.5f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.16f;
    }

    @Override
    public float swimAnimScale() {
        return 0.0f;
    }

    @Override
    public double xTiltScale() {
        return 1.8f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -5.5F, -17.0F, 9.0F, 7.0F, 17.0F, new CubeDeformation(0.0F))
                .texOffs(68, 42).addBox(0.0F, -8.5F, -5.0F, 0.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, 6.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 52).addBox(-3.5F, -3.0F, -6.0F, 7.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(48, 64).addBox(2.5F, -8.0F, -6.0F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(60, 64).addBox(-2.5F, -8.0F, -6.0F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, -17.0F));
        head.addOrReplaceChild("whisker_r_r1", CubeListBuilder.create().texOffs(0, 69).addBox(0.0F, 0.0F, 0.0F, 0.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(6, 69).addBox(5.0F, 0.0F, 0.0F, 0.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 3.0F, -6.0F, 0.6109F, 0.0F, 0.0F));

        PartDefinition left = front.addOrReplaceChild("left", CubeListBuilder.create().texOffs(52, 0).addBox(0.0F, 0.0F, -8.0F, 8.0F, 1.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(4.5F, -2.5F, -8.0F));
        left.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 38).addBox(0.0F, 0.0F, -7.0F, 15.0F, 0.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(8.0F, 0.0F, 0.0F));

        PartDefinition right = front.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 52).addBox(-8.0F, 0.0F, -8.0F, 8.0F, 1.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.5F, -2.5F, -8.0F));
        right.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 24).addBox(-15.0F, 0.0F, -7.0F, 15.0F, 0.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(-8.0F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(58, 23).addBox(-3.5F, -3.0F, 0.0F, 7.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(58, 42).addBox(0.0F, -5.0F, 0.0F, 0.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.5F, 6.0F));
        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(58, 34).addBox(-2.5F, -2.0F, 0.0F, 5.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(12, 69).addBox(0.0F, -4.0F, 0.0F, 0.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(51, 17).addBox(-5.5F, 0.0F, 0.0F, 11.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 4.0F));

        return LayerDefinition.create(mesh, 128, 128);
	}
}