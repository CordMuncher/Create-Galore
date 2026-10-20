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

public class ElytroutModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("elytrout");

    public ElytroutModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.6f, -0.15f);
        this.addSwimAnimation("front/wing_l", 0.3f, 0.25f, SwimAxis.Z);
        this.addSwimAnimation("front/wing_r", 0.3f, -0.25f, SwimAxis.Z);
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create(), PartPose.offset(-0.5F, 23.5F, -1.0F));
        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 14).addBox(-1.0F, -2.0F, -3.0F, 2.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(10, 14).addBox(1.01F, -1.5F, -2.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(12, 14).addBox(-1.01F, -1.5F, -2.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 0.5F, 0.0F));

        PartDefinition wingL = front.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offset(-0.75F, -0.5F, -1.75F));
        wingL.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 5).addBox(0.0F, -0.01F, 0.0F, 7.0F, 0.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(0, 10).addBox(0.0F, -0.51F, 1.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 0.0F, 0.0F, 0.0F, -0.9599F, 0.0F));

        PartDefinition wingR = front.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offsetAndRotation(1.75F, -0.5F, -1.75F, 0.0F, 0.0F, -0.0436F));
        wingR.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, 0.0F, 0.0F, 7.0F, 0.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(12, 10).addBox(-3.0F, -0.5F, 1.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 0.0F, 0.0F, 0.0F, 0.9599F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}