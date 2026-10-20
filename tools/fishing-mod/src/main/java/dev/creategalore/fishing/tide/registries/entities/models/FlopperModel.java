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

public class FlopperModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("flopper");

    public FlopperModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("rear", 0.6f, 0.25f);
        this.addSwimAnimation("rear/tail", 0.6f, 0.25f);
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.5F, -2.0F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 22.5F, -1.0F));
        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(16, 17).addBox(0.0F, 0.0F, -0.5F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 0.75F, -1.0F, 2.4053F, 0.3035F, -2.8231F));
        front.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(14, 17).addBox(0.0F, 0.0F, -0.5F, 0.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 0.75F, -0.5F, 2.4053F, -0.3035F, 2.8231F));
        front.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 9).addBox(-1.0F, -0.5F, 0.0F, 0.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -3.75F, 3.0F, 0.0F, 3.1416F, 0.0F));

        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, 0.5F, -1.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(14, 9).addBox(0.5F, -2.25F, -0.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(14, 13).addBox(-2.5F, -2.25F, -0.5F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -0.5F, -2.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(8, 17).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 3.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(8, 9).addBox(0.0F, -2.5F, -2.5F, 0.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}