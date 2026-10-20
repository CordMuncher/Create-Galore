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

public class MahiMahiModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("mahi_mahi");

    public MahiMahiModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.15f, -0.6f);
        this.addSwimAnimation("rear", speed, 0.2f);
        this.addSwimAnimation("rear/fin", speed, 0.22f, -1.1f);
    }

    @Override
    public float shadowRadius() {
        return 0.6f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.35f;
    }

    @Override
    public float swimAnimScale() {
        return 0.85f;
    }

    @Override
    public double xTiltScale() {
        return 1.6f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 17).addBox(-2.0F, -3.5F, -8.0F, 4.0F, 7.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(24, 17).addBox(0.0F, -8.5F, -8.5F, 0.0F, 6.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(0, 32).addBox(0.0F, 2.5F, -6.5F, 0.0F, 3.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.5F, 4.5F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.5F, -9.0F, 6.0F, 8.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(18, 34).addBox(0.0F, -9.0F, -6.5F, 0.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, -8.0F));

        head.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(43, 34).addBox(-6.0F, 0.0F, -1.5F, 6.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -0.25F, -4.0F, 2.0145F, 1.1029F, 0.3953F));
        head.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(43, 31).addBox(0.0F, 0.0F, -1.5F, 6.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -0.25F, -4.0F, 2.0145F, -1.1029F, -0.3953F));
        head.addOrReplaceChild("fin_b", CubeListBuilder.create().texOffs(42, 37).addBox(-1.0F, 0.0F, -1.5F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 2.75F, -3.5F, 0.4363F, 0.0F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(30, 34).addBox(-1.0F, -2.5F, 0.0F, 2.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.5F, 4.5F));
        rear.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(30, 0).addBox(0.0F, -4.5F, 0.0F, 0.0F, 9.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 4.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}