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

public class MarstilusModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("marstilus");

    public MarstilusModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("rear", speed, 0.12f);
        this.addSwimAnimation("rear/tentacles", speed, 0.16f);
    }

    @Override
    public float swimAnimSpeed() {
        return 0.3f;
    }

    @Override
    public float swimAnimScale() {
        return 0.85f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.0F, -9.0F, 6.0F, 7.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, 1.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(12, 28).addBox(-2.5F, -2.0F, 0.0F, 5.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, 1.0F));

        PartDefinition tentacles = rear.addOrReplaceChild("tentacles", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 2.0F));
        tentacles.addOrReplaceChild("1", CubeListBuilder.create().texOffs(0, 22).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, -0.5F, 0.3142F, 0.0F, 0.0F));
        tentacles.addOrReplaceChild("2", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 2.0F, -0.5F, -0.3142F, 0.0F, 0.0F));
        tentacles.addOrReplaceChild("3", CubeListBuilder.create().texOffs(0, 28).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, 0.0F, -0.5F, 0.0F, -0.3142F, 0.0F));
        tentacles.addOrReplaceChild("4", CubeListBuilder.create().texOffs(24, 16).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 0.0F, -0.5F, 0.0F, 0.3142F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}