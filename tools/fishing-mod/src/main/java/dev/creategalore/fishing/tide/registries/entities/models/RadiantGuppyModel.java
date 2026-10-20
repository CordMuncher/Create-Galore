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

public class RadiantGuppyModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("radiant_guppy");

    public RadiantGuppyModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("tail", 0.6f, 0.3f);
        this.addSwimAnimation("front/fin_top", 0.6f, 0.15f, SwimAxis.Z);
        this.addSwimAnimation("front/fin_bottom", 0.6f, 0.15f, SwimAxis.Z);
    }

    @Override
    public float shadowRadius() {
        return 0.15f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(10, 0).addBox(-1.0F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 22.0F, -2.0F));
        front.addOrReplaceChild("eye_r1", CubeListBuilder.create().texOffs(10, 4).addBox(-0.501F, -0.5F, -0.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -1.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        front.addOrReplaceChild("eye_r2", CubeListBuilder.create().texOffs(10, 6).addBox(0.501F, -0.5F, -0.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -1.0F, 0.0F, 0.0F, -3.1416F, 0.0F));
        front.addOrReplaceChild("fin_top", CubeListBuilder.create().texOffs(6, 8).addBox(0.0F, -3.5509F, -1.0351F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -2.0F, 0.0F, 0.3491F, 0.0F, 0.0F));
        front.addOrReplaceChild("fin_bottom", CubeListBuilder.create().texOffs(0, 8).addBox(0.0F, -0.4491F, -1.0351F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 0.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, -1.5F, 0.0F, 0.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 21.0F, -1.0F));

        return LayerDefinition.create(mesh, 16, 16);
	}
}