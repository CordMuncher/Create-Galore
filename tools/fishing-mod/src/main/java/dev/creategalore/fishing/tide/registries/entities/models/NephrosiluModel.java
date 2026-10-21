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

public class NephrosiluModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("nephrosilu");

    public NephrosiluModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.3f, -0.15f);
        this.addSwimAnimation("front/fin_l", 0.3f, 0.13f, SwimAxis.Z);
        this.addSwimAnimation("front/fin_r", 0.3f, -0.13f, SwimAxis.Z);
        this.addSwimAnimation("rear", 0.3f, 0.15f, SwimAxis.X);
        this.addSwimAnimation("rear/tail", 0.3f, 0.2f, SwimAxis.X);
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 12).addBox(-1.5F, -0.5F, -1.0F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, -3.0F));
        front.addOrReplaceChild("legs_r", CubeListBuilder.create().texOffs(18, 1).addBox(-3.0F, 0.0F, -2.0F, 3.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 0.5F, 3.0F, 0.0F, 0.0F, -0.5236F));
        front.addOrReplaceChild("legs_l", CubeListBuilder.create().texOffs(17, 16).addBox(0.0F, 0.0F, -2.0F, 3.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 0.5F, 3.0F, 0.0F, 0.0F, 0.5236F));
        front.addOrReplaceChild("antenna_r", CubeListBuilder.create().texOffs(6, 19).addBox(0.0F, -1.0F, -3.0F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 0.5F, -1.0F, -0.3604F, 0.2457F, -0.0914F));
        front.addOrReplaceChild("antenna_l", CubeListBuilder.create().texOffs(0, 19).addBox(0.0F, -1.0F, -3.0F, 0.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 0.5F, -1.0F, -0.3604F, -0.2457F, 0.0914F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(18, 8).addBox(-1.5F, -1.0F, -1.75F, 3.0F, 2.0F, 2.0F, new CubeDeformation(-0.25F))
                .texOffs(18, 9).addBox(-0.5F, -0.5F, -1.51F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, -1.0F));
        head.addOrReplaceChild("whisker_r", CubeListBuilder.create().texOffs(16, 19).addBox(0.0F, 0.0F, -2.0F, 0.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.75F, -1.5F, 2.1799F, -0.05F, 0.0715F));
        head.addOrReplaceChild("whisker_l", CubeListBuilder.create().texOffs(12, 19).addBox(0.0F, 0.0F, -2.0F, 0.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.25F, 0.75F, -1.5F, 2.1799F, 0.05F, -0.0715F));

        PartDefinition fin_l = front.addOrReplaceChild("fin_l", CubeListBuilder.create(), PartPose.offset(1.5F, 0.5F, 0.0F));
        fin_l.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -5.0F, 3.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.5F, 0.0F, -0.7854F, 0.3491F));

        PartDefinition fin_r = front.addOrReplaceChild("fin_r", CubeListBuilder.create(), PartPose.offset(-1.5F, 0.5F, 0.0F));
        fin_r.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 6).addBox(-1.0F, 0.0F, -5.0F, 3.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.5F, 0.0F, 0.7854F, -0.3491F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(18, 4).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.1F)), PartPose.offset(0.0F, 23.0F, 1.0F));
        rear.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(17, 17).mirror().addBox(-2.0F, 0.0F, -2.0F, 2.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.0F, 0.0F, 2.0F, 0.0F, 0.0F, -0.5236F));
        rear.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(17, 17).addBox(0.0F, 0.0F, -2.0F, 2.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.5236F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(16, 12).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}