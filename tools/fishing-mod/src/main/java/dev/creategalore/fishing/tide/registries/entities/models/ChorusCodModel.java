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

public class ChorusCodModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("chorus_cod");

    public ChorusCodModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.6f, -0.15f);
        this.addSwimAnimation("rear", 0.6f, 0.25f);
        this.addSwimAnimation("rear/tail", 0.6f, 0.25f);
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.5F, -2.0F, 3.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 22.5F, -1.5F));
        front.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(20, 0).addBox(-1.0F, 0.0F, -2.0F, 0.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 1.0F, 2.5F, 2.7053F, 0.0F, 3.1416F));
        front.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 11).addBox(-1.0F, -0.5F, -3.0F, 0.0F, 3.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, -5.0F, 2.0F, 0.0F, 3.1416F, 0.0F));
        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(12, 11).addBox(-1.0F, -1.5F, -3.0F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -0.5F, -2.0F));

        PartDefinition fin_l = front.addOrReplaceChild("fin_l", CubeListBuilder.create(), PartPose.offset(2.0F, 1.0F, -0.5F));
        fin_l.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(18, 17).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, 0.0F, -0.4363F, 1.0472F));

        PartDefinition fin_r = front.addOrReplaceChild("fin_r", CubeListBuilder.create(), PartPose.offset(-1.0F, 1.0F, -0.5F));
        fin_r.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(18, 19).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.0F, 0.0F, 0.4363F, -1.0472F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.0F, 3.5F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(12, 17).addBox(0.0F, -2.0F, 0.0F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 1.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}