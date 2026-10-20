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

public class BlazingSwordfishModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("blazing_swordfish");

    public BlazingSwordfishModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.13f, -0.6f);
        this.addSwimAnimation("rear", speed, 0.13f);
        this.addSwimAnimation("rear/tail", speed, 0.15f, -1f);
        this.addSwimAnimation("rear/tail/fin", speed, 0.17f, -1.5f);
    }

    @Override
    public int brightnessOverride() {
        return 15;
    }

    @Override
    public float shadowRadius() {
        return 0.6f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.22f;
    }

    @Override
    public float swimAnimScale() {
        return 0.85f;
    }

    @Override
    public double xTiltScale() {
        return 1.8f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -12.0F, 6.0F, 7.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(24, 33).addBox(0.0F, -8.0F, -10.0F, 0.0F, 5.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 3.5F));
        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(16, 44).addBox(-1.0F, 0.0F, 0.0F, 0.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 4.0F, -9.5F, 0.3491F, 0.0F, 0.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 44).addBox(-2.0F, -2.5F, -4.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 32).addBox(-0.5F, -1.5F, -15.0F, 1.0F, 1.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(24, 18).addBox(0.0F, -5.5F, -15.0F, 0.0F, 4.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -12.0F));
        head.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(46, 0).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 0.1745F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(46, 4).addBox(0.0F, 2.5F, 3.0F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(42, 33).addBox(0.0F, -7.75F, 0.0F, 0.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.5F, 2.5F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(42, 45).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 8.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(34, 0).addBox(0.0F, -6.0F, 0.0F, 0.0F, 12.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}