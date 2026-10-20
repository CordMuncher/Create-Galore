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

public class CoelacanthModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("coelacanth");

    public CoelacanthModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.15f, -0.6f);
        this.addSwimAnimation("rear", speed, 0.18f);
        this.addSwimAnimation("rear/fin", speed, 0.22f, -1f);
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

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.5F, -16.0F, 6.0F, 8.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(32, 35).addBox(0.0F, -8.5F, -12.5F, 0.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(44, 10).addBox(0.0F, -8.5F, -3.5F, 0.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(44, 0).addBox(0.0F, 4.5F, -3.0F, 0.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(10, 46).addBox(0.0F, 4.5F, -9.0F, 0.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, 4.5F));

        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(0, 43).addBox(0.0F, 0.0F, -2.5F, 0.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 3.5F, -13.5F, 0.3295F, -0.1172F, 0.3295F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(36, 24).addBox(0.0F, 0.0F, -2.5F, 0.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 3.5F, -13.5F, 0.3295F, 0.1172F, -0.3295F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(16, 24).addBox(-2.5F, -3.0F, -5.0F, 5.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(47, 21).addBox(-2.5F, -2.5F, -7.0F, 5.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -16.0F));
        head.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(50, 49).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.17F, -5.0F, 0.7854F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(16, 35).addBox(-2.0F, -3.5F, 0.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.5F, 4.5F));
        rear.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 24).addBox(0.0F, -5.5F, 0.0F, 0.0F, 11.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}