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
import net.minecraft.util.Mth;

public class DragonFishModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("dragon_fish");

    public DragonFishModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.08f, -1f);
        this.addSwimAnimation("front", speed, -0.08f);
        this.addSwimAnimation("rear", speed, 0.13f);
        this.addSwimAnimation("rear/tail2", speed, 0.15f, -1f, Mth.PI, SwimAxis.Y);
        this.addSwimAnimation("rear/tail2/tail3", speed, 0.15f, -1.5f, Mth.PI, SwimAxis.Y);
        this.addSwimAnimation("rear/tail2/tail3/tail4", speed, 0.15f, -2f, Mth.PI, SwimAxis.Y);
        this.addSwimAnimation("rear/tail2/tail3/tail4/tail5", speed, 0.17f, -2.5f, Mth.PI, SwimAxis.Y);
    }

    @Override
    public float shadowRadius() {
        return 0.8f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.15f;
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
		PartDefinition partdefinition = mesh.getRoot();

        PartDefinition front = partdefinition.addOrReplaceChild("front", CubeListBuilder.create().texOffs(76, 79).addBox(-7.5F, -7.0F, -8.0F, 15.0F, 14.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, -27.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(122, 79).addBox(-5.5F, -6.0F, -12.0F, 11.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(72, 144).addBox(-5.5F, -6.0F, -4.0F, 11.0F, 11.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 139).addBox(-5.5F, 0.0F, -12.0F, 11.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, -8.0F));

        PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(38, 139).addBox(-5.0F, 0.0F, -7.0F, 10.0F, 4.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(122, 93).addBox(-5.0F, -1.0F, -7.0F, 10.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.0F, -4.0F, 0.2618F, 0.0F, 0.0F));

        PartDefinition wing_l = partdefinition.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(0, 71).addBox(6.0F, 1.0F, 0.0F, 20.0F, 0.0F, 18.0F, new CubeDeformation(0.0F))
                .texOffs(48, 101).addBox(0.0F, 0.0F, 0.0F, 6.0F, 2.0F, 18.0F, new CubeDeformation(0.0F))
                .texOffs(96, 114).addBox(6.0F, 0.5F, 2.0F, 11.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.5F, 17.0F, -24.0F, -0.0602F, -0.1639F, 0.354F));

        PartDefinition wing_r = partdefinition.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(0, 53).addBox(-26.0F, 1.0F, 0.0F, 20.0F, 0.0F, 18.0F, new CubeDeformation(0.0F))
                .texOffs(0, 89).addBox(-6.0F, 0.0F, 0.0F, 6.0F, 2.0F, 18.0F, new CubeDeformation(0.0F))
                .texOffs(108, 35).addBox(-17.0F, 0.5F, 2.0F, 11.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-9.5F, 17.0F, -24.0F, -0.0602F, 0.1639F, -0.354F));

        PartDefinition rear = partdefinition.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(76, 53).addBox(-8.0F, -7.0F, 0.0F, 16.0F, 15.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(144, 129).addBox(-1.0F, -13.5F, -0.5F, 2.0F, 7.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 15.5F, 8.0F));

        PartDefinition mid_wing_l = rear.addOrReplaceChild("mid_wing_l", CubeListBuilder.create().texOffs(72, 129).addBox(0.0F, 0.0F, 0.0F, 5.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(146, 114).addBox(5.0F, 0.5F, 2.0F, 8.0F, 1.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(96, 101).addBox(5.0F, 1.0F, 0.0F, 14.0F, 0.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.5F, 0.5F, 1.0F, -0.0692F, -0.2527F, 0.2706F));

        PartDefinition mid_wing_r = rear.addOrReplaceChild("mid_wing_r", CubeListBuilder.create().texOffs(108, 129).addBox(-5.0F, 0.0F, 0.0F, 5.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
                .texOffs(132, 147).addBox(-13.0F, 0.5F, 2.0F, 8.0F, 1.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(108, 0).addBox(-19.0F, 1.0F, 0.0F, 14.0F, 0.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.5F, 0.5F, 1.0F, -0.0692F, 0.2527F, -0.2706F));

        PartDefinition tail2 = rear.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(108, 13).addBox(-6.0F, -6.0F, -10.0F, 12.0F, 12.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(150, 101).addBox(-1.0F, -10.0F, -13.0F, 2.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.5F, 11.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(36, 121).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -10.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(102, 144).addBox(-4.0F, -4.0F, -7.0F, 8.0F, 8.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(152, 22).addBox(-1.0F, -8.0F, -6.0F, 2.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition tail5 = tail4.addOrReplaceChild("tail5", CubeListBuilder.create().texOffs(48, 150).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition rear_wing_l = tail5.addOrReplaceChild("rear_wing_l", CubeListBuilder.create().texOffs(48, 89).addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(72, 121).addBox(2.0F, 0.5F, 2.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(130, 50).addBox(2.0F, 1.0F, 0.0F, 10.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -1.0F, 0.0F, -0.0465F, -0.2577F, 0.1806F));

        PartDefinition rear_wing_r = tail5.addOrReplaceChild("rear_wing_r", CubeListBuilder.create().texOffs(24, 150).addBox(-2.0F, 0.0F, 0.0F, 2.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(130, 70).addBox(-8.0F, 0.5F, 2.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(130, 60).addBox(-12.0F, 1.0F, 0.0F, 10.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -1.0F, 0.0F, -0.0465F, 0.2577F, -0.1806F));

        PartDefinition tail6 = tail5.addOrReplaceChild("tail6", CubeListBuilder.create().texOffs(152, 13).addBox(-2.0F, -1.5F, -6.0F, 4.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(0, 0).addBox(-9.5F, -17.0F, -27.0F, 19.0F, 18.0F, 35.0F, new CubeDeformation(0.0F))
                .texOffs(0, 109).addBox(-1.0F, -26.0F, -15.5F, 2.0F, 9.0F, 16.0F, new CubeDeformation(0.0F))
                .texOffs(0, 149).addBox(-1.0F, -22.0F, -32.5F, 2.0F, 7.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        return LayerDefinition.create(mesh, 256, 256);
	}
}