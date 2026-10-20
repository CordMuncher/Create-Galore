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

public class SunEmblemModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("sun_emblem");

    public SunEmblemModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("bottom/1", 0.2f, 0.3f, 0f, -1.4f, SwimAxis.Y);
        this.addSwimAnimation("bottom/2", 0.2f, 0.3f, 0f, -1.4f, SwimAxis.Y);
        this.addSwimAnimation("bottom/3", 0.2f, 0.3f, 0f, 0.17f, SwimAxis.Z);
        this.addSwimAnimation("bottom/4", 0.2f, -0.3f, 0f, -0.17f, SwimAxis.Z);
    }

    @Override
    public int brightnessOverride() {
        return 15;
    }

    @Override
    public double xTiltScale() {
        return 0;
    }

    @Override
    public float swimAnimScale() {
        return 0;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition top = root.addOrReplaceChild("top", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -8.5F, -1.5F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 22.5F, -1.5F));
        top.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(30, 20).addBox(-3.0F, -2.0F, 0.0F, 3.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, -6.0F, 4.5F, 0.0F, 0.7854F, 0.0F));
        top.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(24, 17).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -8.5F, 4.5F, -0.7854F, 0.0F, 0.0F));
        top.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(24, 14).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -8.5F, -1.5F, 0.7854F, 0.0F, 0.0F));
        top.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(24, 7).addBox(0.0F, -3.0F, -2.0F, 0.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -8.5F, 1.5F, 0.0F, 0.0F, 0.7854F));
        top.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(24, 28).addBox(0.0F, -2.0F, 0.0F, 3.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -6.0F, 4.5F, 0.0F, -0.7854F, 0.0F));
        top.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(24, 24).addBox(0.0F, -2.0F, 0.0F, 3.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -6.0F, -1.5F, 0.0F, 0.7854F, 0.0F));
        top.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(24, 0).addBox(0.0F, -3.0F, -2.0F, 0.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, -8.5F, 1.5F, 0.0F, 0.0F, -0.7854F));
        top.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(24, 20).addBox(-3.0F, -2.0F, 0.0F, 3.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, -6.0F, -1.5F, 0.0F, -0.7854F, 0.0F));

        PartDefinition bottom = root.addOrReplaceChild("bottom", CubeListBuilder.create(), PartPose.offset(0.0F, 19.0F, -1.5F));
        bottom.addOrReplaceChild("1", CubeListBuilder.create().texOffs(12, 22).addBox(0.0F, 0.0F, -3.0F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.5F, -1.5708F, -1.3963F, 1.5708F));
        bottom.addOrReplaceChild("2", CubeListBuilder.create().texOffs(12, 11).addBox(0.0F, 0.0F, -3.0F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 4.5F, 1.5708F, -1.3963F, -1.5708F));
        bottom.addOrReplaceChild("3", CubeListBuilder.create().texOffs(0, 22).addBox(0.0F, 0.0F, -3.0F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 0.0F, 1.5F, 0.0F, 0.0F, 0.1745F));
        bottom.addOrReplaceChild("4", CubeListBuilder.create().texOffs(0, 11).addBox(0.0F, 0.0F, -3.0F, 0.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 0.0F, 1.5F, 0.0F, 0.0F, -0.1745F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}