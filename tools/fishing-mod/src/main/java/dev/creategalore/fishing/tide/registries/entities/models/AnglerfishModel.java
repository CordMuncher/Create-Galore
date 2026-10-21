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

public class AnglerfishModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("anglerfish");

    public AnglerfishModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/lure", 0.45f, -0.1f);
        this.addSwimAnimation("rear", 0.45f, 0.2f);
        this.addSwimAnimation("rear/tail", 0.45f, 0.25f);
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(6, 17).addBox(0.0F, -3.5F, 0.5F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, -2.0F));

        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(0, 16).addBox(0.0F, -1.5F, 0.0F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 0.0F, 1.0F, 0.3491F, -0.48F, 0.0F));
        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(16, 12).addBox(0.0F, -1.5F, 0.0F, 0.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 0.0F, 1.0F, 0.3491F, 0.48F, 0.0F));

        PartDefinition jaw = front.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(8, 8).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(6, 16).addBox(-0.5F, -1.0F, -3.0F, 1.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 2.0F, -1.0F, -0.9599F, 0.0F, 0.0F));
        jaw.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(14, 0).addBox(-3.5F, -3.0F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 1.5708F, 0.0F, 0.0F));

        PartDefinition lure = front.addOrReplaceChild("lure", CubeListBuilder.create().texOffs(12, 17).addBox(0.0F, -2.0F, -1.0F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.3491F, 0.0F, 0.0F));
        PartDefinition mid = lure.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(14, 17).addBox(0.0F, -2.0F, -1.0F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.6981F, 0.0F, 0.0F));
        PartDefinition end = mid.addOrReplaceChild("end", CubeListBuilder.create().texOffs(18, 8).addBox(0.0F, -2.0F, -1.0F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.8727F, 0.0F, 0.0F));
        end.addOrReplaceChild("bulb", CubeListBuilder.create().texOffs(14, 4).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.0F, -0.5F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(8, 12).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 0.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 8).addBox(0.0F, -2.0F, 0.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}