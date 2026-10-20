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

public class MantaRayModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("manta_ray");

    public MantaRayModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = this.swimAnimSpeed();
        this.addSwimAnimation("front/left", speed, 0.3f, SwimAxis.Z);
        this.addSwimAnimation("front/left/fin", speed, 0.4f, -0.5f, SwimAxis.Z);
        this.addSwimAnimation("front/right", speed, -0.3f, SwimAxis.Z);
        this.addSwimAnimation("front/right/fin", speed, -0.4f, -0.5f, SwimAxis.Z);
        this.addSwimAnimation("rear", speed, -0.12f, SwimAxis.X);
        this.addSwimAnimation("rear/tail", speed, -0.15f, -0.5f, SwimAxis.X);
        this.addSwimAnimation("rear/tail/tip", speed, -0.15f, -1f, SwimAxis.X);
        this.addSwimAnimation("rear/left", speed, 0.2f, SwimAxis.Z);
        this.addSwimAnimation("rear/right", speed, -0.2f, SwimAxis.Z);
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
        return 0.1f;
    }

    @Override
    public float swimAnimScale() {
        return 0.0f;
    }

    @Override
    public double xTiltScale() {
        return 2.0f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-7.5F, -1.5F, -18.0F, 15.0F, 3.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, 5.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -18.0F));
        head.addOrReplaceChild("left", CubeListBuilder.create().texOffs(76, 77).addBox(4.5F, -2.0F, -17.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(74, 16).addBox(3.5F, -2.0F, -19.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, 14.0F));
        head.addOrReplaceChild("right", CubeListBuilder.create().texOffs(64, 77).addBox(-7.5F, -2.0F, -17.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(66, 16).addBox(-5.5F, -2.0F, -19.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, 14.0F));

        PartDefinition left = front.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 21).mirror().addBox(0.0F, 0.0F, -9.0F, 11.0F, 1.0F, 18.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(7.5F, -0.5F, -9.0F));
        left.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(-16, 59).mirror().addBox(0.0F, 0.0F, -8.0F, 18.0F, 0.0F, 16.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(-16, 83).mirror().addBox(0.0F, 0.02F, -8.0F, 18.0F, 0.0F, 16.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(11.0F, 0.0F, 0.0F));

        PartDefinition right = front.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 40).addBox(-11.0F, 0.0F, -9.0F, 11.0F, 1.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offset(-7.5F, -0.5F, -9.0F));
        right.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(46, 61).addBox(-18.0F, 0.0F, -8.0F, 18.0F, 0.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(46, 45).addBox(-18.0F, 0.02F, -8.0F, 18.0F, 0.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(-11.0F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(66, 0).addBox(-6.5F, -2.0F, 0.0F, 13.0F, 2.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 5.0F));
        rear.addOrReplaceChild("left", CubeListBuilder.create().texOffs(67, 10).addBox(0.0F, 0.0F, -3.0F, 9.0F, 0.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(85, 10).addBox(0.0F, 0.02F, -3.0F, 9.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(6.5F, -1.0F, 3.0F));
        rear.addOrReplaceChild("right", CubeListBuilder.create().texOffs(1, 76).addBox(-9.0F, 0.0F, -3.0F, 9.0F, 0.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(30, 69).addBox(-9.0F, 0.02F, -3.0F, 9.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.5F, -1.0F, 3.0F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(32, 75).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, 7.0F));
        tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(48, 77).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.5F, 7.0F));

        return LayerDefinition.create(mesh, 128, 128);
	}
}