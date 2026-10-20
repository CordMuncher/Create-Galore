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

public class SharkModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("shark");

    public SharkModel(EntityRendererProvider.Context context) {
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
        return 0.8f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.2f;
    }

    @Override
    public float swimAnimScale() {
        return 0.85f;
    }

    @Override
    public double xTiltScale() {
        return 1.85f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -9.0F, -9.0F, 10.0F, 9.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(26, 42).addBox(0.0F, -14.0F, -5.0F, 0.0F, 6.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 22).addBox(-4.0F, -3.5F, -6.0F, 8.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, -9.0F));
        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(32, 35).addBox(-9.0F, 0.0F, -3.5F, 9.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 2.25F, -1.5F, 0.0F, 0.2182F, -0.5236F));
        front.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 35).addBox(0.0F, 0.0F, -3.5F, 9.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 2.25F, -1.5F, 0.0F, -0.2182F, 0.5236F));
        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(46, 11).addBox(-2.5F, 1.25F, -5.0F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(0, 42).addBox(-3.0F, -2.75F, -7.0F, 6.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -6.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(28, 22).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(46, 42).addBox(0.0F, -6.5F, 2.0F, 0.0F, 4.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(0, 58).addBox(0.0F, 2.5F, 2.0F, 0.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 19.5F, 3.75F, -0.0436F, 0.0F, 0.0F));
        rear.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 53).addBox(-5.0F, 0.0F, -2.5F, 5.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 3.0F, 1.5F, 0.0F, 0.3491F, -0.5236F));
        rear.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(46, 53).addBox(0.0F, 0.0F, -2.5F, 5.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 3.0F, 1.5F, 0.0F, -0.3491F, 0.5236F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(46, 0).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 5.75F, -0.0873F, 0.0F, 0.0F));

        PartDefinition fin = tail.addOrReplaceChild("fin", CubeListBuilder.create(), PartPose.offset(0.0F, -0.25F, 5.0F));
        fin.addOrReplaceChild("bottom_r1", CubeListBuilder.create().texOffs(12, 58).addBox(0.0F, -7.0F, -2.0F, 0.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, -2.5307F, 0.0F, 0.0F));
        fin.addOrReplaceChild("top_r1", CubeListBuilder.create().texOffs(54, 18).addBox(0.0F, -11.0F, -2.0F, 0.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, -0.9599F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
	}
}