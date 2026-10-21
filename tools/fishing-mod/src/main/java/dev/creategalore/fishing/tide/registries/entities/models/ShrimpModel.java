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

public class ShrimpModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("shrimp");

    public ShrimpModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
    }

    @Override
    public float shadowRadius() {
        return 0.15f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -4.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.25F))
                .texOffs(30, -1).addBox(-1.251F, 0.0F, -4.0F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(30, -1).mirror().addBox(0.251F, 0.0F, -4.0F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.5F, 21.0F, 2.5F));

        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(6, 8).addBox(0.0F, -2.5F, -3.0F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.8F, 0.5F, -4.0F, 0.0F, 0.2618F, 0.0F));
        front.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 8).addBox(0.0F, -2.5F, -3.0F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.2F, 0.5F, -4.0F, 0.0F, -0.2618F, 0.0F));

        PartDefinition legs = front.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 1.25F, -2.5F));
        legs.addOrReplaceChild("leg_fl", CubeListBuilder.create().texOffs(18, 3).mirror().addBox(0.1029F, -0.3262F, -0.4397F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.25F, 0.0F, 0.0F, 0.3341F, 0.103F, -0.288F));
        legs.addOrReplaceChild("leg_bl", CubeListBuilder.create().texOffs(18, 0).addBox(0.1029F, -0.3262F, -0.4397F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, 0.0F, 2.0F, 0.3341F, 0.103F, -0.288F));
        legs.addOrReplaceChild("leg_br", CubeListBuilder.create().texOffs(18, -1).addBox(-0.1029F, -0.3262F, -0.4397F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 2.0F, 0.3341F, -0.103F, 0.288F));
        legs.addOrReplaceChild("leg_ml", CubeListBuilder.create().texOffs(18, 2).addBox(0.1029F, -0.3262F, -0.4397F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, 0.0F, 1.0F, 0.3341F, 0.103F, -0.288F));
        legs.addOrReplaceChild("leg_mr", CubeListBuilder.create().texOffs(18, 1).addBox(-0.1029F, -0.3262F, -0.4397F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 1.0F, 0.3341F, -0.103F, 0.288F));
        legs.addOrReplaceChild("leg_fr", CubeListBuilder.create().texOffs(18, 3).addBox(-0.1029F, -0.3262F, -0.4397F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.25F, 0.0F, 0.0F, 0.3341F, -0.103F, 0.288F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(10, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.18F)), PartPose.offsetAndRotation(0.0F, 21.0F, 2.75F, -0.8727F, 0.0F, 0.0F));
        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(12, 3).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F, -0.8727F, 0.0F, 0.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(1, 6).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.5F, 2.0F, -0.6981F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}