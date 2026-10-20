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

public class SaturnCuttlefishModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("saturn_cuttlefish");

    public SaturnCuttlefishModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("rear", 0.3f, 0.1f);
        this.addSwimAnimation("front/tentacles", 0.3f, 0.1f, SwimAxis.X);
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.3f;
    }

    @Override
    public float swimAnimScale() {
        return 0.6f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 25).addBox(-2.0F, -1.5F, -4.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, -1.0F));
        front.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 19).addBox(-3.5F, 0.0F, -3.0F, 7.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, -0.9163F, 0.0F, 0.0F));

        PartDefinition tentacles = front.addOrReplaceChild("tentacles", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -3.75F));
        tentacles.addOrReplaceChild("1", CubeListBuilder.create().texOffs(24, 10).addBox(-1.5F, 0.0F, -6.0F, 3.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, 0.0873F, 0.0F, 0.0F));
        tentacles.addOrReplaceChild("2", CubeListBuilder.create().texOffs(16, 25).addBox(-1.5F, 0.0F, -5.0F, 3.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1745F, 0.0F, 0.0F));
        tentacles.addOrReplaceChild("3", CubeListBuilder.create().texOffs(26, 16).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

        root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 10).addBox(-2.5F, -1.0F, 0.0F, 5.0F, 2.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(26, 20).addBox(-1.5F, -0.5F, 7.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, -1.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}