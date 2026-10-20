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

public class SnailModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("snail");

    public SnailModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
    }

    @Override
    public float swimAnimScale() {
        return 0.0f;
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(14, 10).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 24.0F, -1.0F));
        head.addOrReplaceChild("eye_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-0.5F, -2.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(14, 15).addBox(1.0F, -2.0F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.75F, -2.0F, -3.0F, 0.2618F, 0.0F, 0.0F));

        root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -4.0F, -1.0F, 3.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 22.25F, -1.0F, -0.0873F, 0.0F, 0.0F));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 10).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}