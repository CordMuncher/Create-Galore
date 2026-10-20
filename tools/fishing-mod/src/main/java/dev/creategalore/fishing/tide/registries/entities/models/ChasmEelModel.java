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

public class ChasmEelModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("chasm_eel");

    public ChasmEelModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.15f, -1f);
        this.addSwimAnimation("rear", speed, 0.25f, -1f);
        this.addSwimAnimation("rear/tail", speed, 0.25f, -1.5f);
    }

    @Override
    public float swimAnimSpeed() {
        return 0.25f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -2.0F, 2.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(0, 12).addBox(0.0F, -3.0F, 0.0F, 0.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, -4.0F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, -0.5F, -2.0F));
        head.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(14, 12).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 0.3491F, 0.0F, 0.0F));
        head.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(26, 21).addBox(-0.5F, -2.0F, -4.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(-0.5F, 0.5F, 0.0F, -0.3491F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -2.0F, 0.0F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(22, 0).addBox(0.0F, -3.0F, 0.0F, 0.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 23.0F, 3.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(26, 27).addBox(-0.5F, -2.5F, 0.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(14, 21).addBox(0.0F, -3.5F, 0.0F, 0.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, 5.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}