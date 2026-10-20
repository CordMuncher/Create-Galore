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

public class GreatWhiteSharkModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("great_white_shark");

    public GreatWhiteSharkModel(EntityRendererProvider.Context context) {
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
        return 0.16f;
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

        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -13.0F, -12.0F, 14.0F, 13.0F, 22.0F, new CubeDeformation(0.0F))
                .texOffs(46, 57).addBox(0.0F, -24.0F, -9.0F, 0.0F, 11.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 35).addBox(-5.5F, -5.5F, -12.0F, 11.0F, 11.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 18.0F, -12.0F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(0, 68).addBox(-14.0F, 0.0F, -3.5F, 14.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.75F, 0.75F, -5.5F, 0.0F, 0.2182F, -0.6109F));
        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(0, 58).addBox(0.0F, 0.0F, -3.5F, 14.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.75F, 1.75F, -5.5F, 0.0F, -0.2182F, 0.6109F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, 1.25F, -12.0F));
        head.addOrReplaceChild("top", CubeListBuilder.create().texOffs(72, 0).addBox(-4.0F, -6.0F, -9.0F, 8.0F, 6.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.25F, 0.0F, -0.0436F, 0.0F, 0.0F));
        head.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 78).addBox(-3.5F, 0.25F, -7.0F, 7.0F, 3.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(0, 88).addBox(-3.5F, -0.75F, -7.0F, 7.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(46, 35).addBox(-5.5F, -5.5F, 0.0F, 11.0F, 11.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 17.5F, 9.75F, -0.0436F, 0.0F, 0.0F));
        rear.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(82, 57).addBox(-7.0F, 0.0F, -4.5F, 8.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, 2.85F, 5.5F, 0.0F, 0.3491F, -0.5236F));
        rear.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(82, 64).addBox(-1.0F, 0.0F, -4.5F, 8.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, 2.85F, 5.5F, 0.0F, -0.3491F, 0.5236F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(72, 15).addBox(-3.5F, -5.0F, 0.0F, 7.0F, 9.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.5F, 10.25F, -0.0873F, 0.0F, 0.0F));
        PartDefinition fin = tail.addOrReplaceChild("fin", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 8.0F));
        fin.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(82, 71).addBox(0.0F, -13.0F, -3.0F, 0.0F, 13.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.25F, 0.0F, -2.4435F, 0.0F, 0.0F));
        fin.addOrReplaceChild("top", CubeListBuilder.create().texOffs(28, 78).addBox(-0.01F, -15.0F, -4.0F, 0.0F, 15.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.25F, 0.0F, -0.5236F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
	}
}