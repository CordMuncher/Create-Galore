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

public class TunaModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("tuna");

    public TunaModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.13f, -0.6f);
        this.addSwimAnimation("rear", speed, 0.13f);
        this.addSwimAnimation("rear/tail", speed, 0.15f, -1f);
        this.addSwimAnimation("rear/tail/fin", speed, 0.17f, -1.5f);
    }

    @Override
    public float shadowRadius() {
        return 0.6f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.25f;
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

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -11.0F, 6.0F, 9.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(8, 56).addBox(0.0F, 3.5F, -1.5F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 8).addBox(0.0F, -9.5F, -9.5F, 0.0F, 5.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 1.5F));

        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(47, 58).mirror().addBox(-7.0F, 0.0F, 0.0F, 7.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.0F, 2.25F, -11.0F, 0.0F, 0.7854F, -0.5236F));
        front.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(47, 61).addBox(0.0F, 0.0F, 0.0F, 7.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 2.25F, -11.0F, 0.0F, -0.7854F, 0.5236F));

        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 36).addBox(-2.0F, 1.5F, -4.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(34, 0).addBox(-2.0F, -3.5F, -5.0F, 4.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -11.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(22, 20).addBox(-2.0F, -3.5F, 0.0F, 4.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(34, 10).addBox(0.0F, 3.5F, -0.5F, 0.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(32, 35).addBox(0.0F, -4.5F, -0.5F, 0.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.5F, 1.5F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(12, 42).addBox(-1.0F, -2.5F, 0.0F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(16, 37).addBox(0.0F, -3.5F, -0.5F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(44, 27).addBox(0.0F, 2.5F, -0.5F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));

        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(22, 34).addBox(0.0F, -5.5F, 0.0F, 0.0F, 11.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}