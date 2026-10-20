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

public class SailfishModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("sailfish");

    public SailfishModel(EntityRendererProvider.Context context) {
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

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 29).addBox(-3.0F, -4.0F, -11.0F, 6.0F, 8.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(46, 51).addBox(0.0F, 3.0F, 0.0F, 0.0F, 4.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(9, 9).addBox(0.0F, -12.0F, -9.5F, 0.0F, 10.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 1.5F));

        front.addOrReplaceChild("fin_r_r1", CubeListBuilder.create().texOffs(18, 52).addBox(-5.0F, 0.0F, -1.5F, 5.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 1.5F, -9.0F, 2.0145F, 1.1029F, 0.3953F));
        front.addOrReplaceChild("fin_l_r1", CubeListBuilder.create().texOffs(17, 49).addBox(0.0F, 0.0F, -1.5F, 5.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 1.5F, -9.0F, 2.0145F, -1.1029F, -0.3953F));
        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(52, 0).addBox(-1.0F, 0.0F, 0.0F, 0.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 4.0F, -9.5F, 0.3491F, 0.0F, 0.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(38, 19).addBox(-2.0F, -2.5F, -5.0F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(34, 42).addBox(-0.5F, -0.5F, -13.0F, 1.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -11.0F));

        head.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.1745F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(34, 29).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 6.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(10, 0).addBox(0.0F, -12.0F, 0.5F, 0.0F, 10.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 1.5F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(34, 51).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(38, 0).addBox(0.0F, -6.0F, 0.0F, 0.0F, 12.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}