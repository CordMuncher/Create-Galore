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

public class IncandescentLarvaModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("incandescent_larva");

    public IncandescentLarvaModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.6f, -0.12f);
        this.addSwimAnimation("rear", 0.6f, 0.2f);
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    @Override
    public int brightnessOverride() {
        return 10;
    }

    @Override
    public boolean renderTranslucent() {
        return true;
    }

    @Override
    public float shadowRadius() {
        return 0.15f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -0.5F, -1.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 6).addBox(-0.5F, 0.0F, -0.5F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 22.5F, -1.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(6, 10).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offset(0.5F, 0.5F, -1.0F));
        head.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(10, 8).addBox(-0.4F, 0.0F, -2.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -0.25F, -0.75F, -0.4363F, 0.4363F, 0.0F));
        head.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(10, 6).addBox(-0.6F, 0.0F, -2.0F, 1.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -0.25F, -0.75F, -0.4363F, -0.4363F, 0.0F));

        front.addOrReplaceChild("legs", CubeListBuilder.create().texOffs(0, 13).addBox(1.5F, 0.75F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 13).addBox(1.5F, 0.75F, 1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 13).addBox(-1.5F, 0.75F, 1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 13).addBox(-1.5F, 0.75F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 10).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 2.0F));

        return LayerDefinition.create(mesh, 16, 16);
	}
}