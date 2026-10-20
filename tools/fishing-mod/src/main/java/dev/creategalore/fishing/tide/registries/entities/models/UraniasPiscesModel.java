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

public class UraniasPiscesModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("uranias_pisces");

    public UraniasPiscesModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.13f, -0.5f);
        this.addSwimAnimation("rear", speed, 0.1f);
        this.addSwimAnimation("rear/tail", speed, 0.15f, -0.5f);
        this.addSwimAnimation("rear/tail/fin", speed, 0.2f, -1f);
    }

    @Override
    public float shadowRadius() {
        return 0.7f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.32f;
    }

    @Override
    public float swimAnimScale() {
        return 0.85f;
    }

    @Override
    public double xTiltScale() {
        return 1.6f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.0F, -11.0F, 6.0F, 8.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(22, 29).addBox(0.0F, -6.0F, -10.0F, 0.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(28, 41).addBox(0.0F, 4.0F, -10.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 0.0F));

        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(44, 16).addBox(-6.0F, -2.0F, 0.0F, 6.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 0.75F, -9.0F, -0.4092F, 1.0624F, -0.3648F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(42, 36).addBox(0.0F, -2.0F, 0.0F, 6.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 0.75F, -9.0F, -0.4092F, -1.0624F, 0.3648F));

        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(22, 19).addBox(-2.5F, -2.0F, -6.0F, 5.0F, 4.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(34, 10).addBox(-2.0F, 2.0F, -5.0F, 4.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(40, 41).addBox(-2.5F, -3.0F, -3.0F, 5.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -11.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 19).addBox(-2.5F, -3.5F, 0.0F, 5.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(42, 29).addBox(0.0F, -5.5F, 0.0F, 0.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(14, 41).addBox(0.0F, 3.0F, -1.5F, 0.0F, 3.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 0.0F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(34, 0).addBox(-1.5F, -3.0F, 0.0F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 6.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(2, 33).addBox(0.0F, -5.0F, 0.0F, 0.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}