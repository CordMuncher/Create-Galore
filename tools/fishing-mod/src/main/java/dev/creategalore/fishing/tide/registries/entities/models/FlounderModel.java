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

public class FlounderModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("flounder");

    public FlounderModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("rear", 0.6f, 0.2f, SwimAxis.X);
        this.addSwimAnimation("front", 0.3f, 0.08f, SwimAxis.X);
        this.addSwimAnimation("front/fin_l", 0.6f, 0.17f, SwimAxis.Z);
        this.addSwimAnimation("front/fin_r", 0.6f, 0.17f, SwimAxis.Z);
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    @Override
    public double xTiltScale() {
        return 1.75f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -0.5F, -8.0F, 7.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 3.0F));
        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(22, 13).addBox(-2.5F, -0.5F, -1.0F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -8.0F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(0, 9).addBox(0.0F, 0.0F, -4.0F, 3.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(3.5F, 0.0F, -4.0F));
        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(0, 17).addBox(-3.0F, 0.0F, -4.0F, 3.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.5F, 0.0F, -4.0F));
        front.addOrReplaceChild("fin_top", CubeListBuilder.create().texOffs(22, 15).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, -6.0F, 0.3491F, 0.0F, 0.0F));

        root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(22, 9).addBox(-3.5F, 0.0F, 0.0F, 7.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 3.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}