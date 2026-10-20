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

public class EchoSnapperModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("echo_snapper");

    public EchoSnapperModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("rear", 0.2f, 0.1f, SwimAxis.X);
        this.addSwimAnimation("rear/tail", 0.2f, 0.1f, SwimAxis.X);
        this.addSwimAnimation("front/head", 0.15f, 0.08f, SwimAxis.X);
        this.addSwimAnimation("front/legs", -0.2f, 0.1f);
    }

    @Override
    public float swimAnimSpeed() {
        return 0.2f;
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    @Override
    public double xTiltScale() {
        return 1.5f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 10).addBox(-1.5F, -0.5F, -2.0F, 4.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 22.5F, -0.5F));
        front.addOrReplaceChild("legs", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -3.5F, 8.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 0.5F, 1.5F));
        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(16, 18).addBox(-1.5F, -0.5F, -1.5F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 6).addBox(-4.5F, 0.0F, -4.5F, 9.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 0.5F, -2.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(20, 10).addBox(-1.5F, -0.5F, 0.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 3.5F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 18).addBox(-2.5F, 0.0F, 0.0F, 5.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 1.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}