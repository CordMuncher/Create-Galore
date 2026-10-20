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

public class ArapaimaModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("arapaima");

    public ArapaimaModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.3f, -0.05f, 2f);
        this.addSwimAnimation("front", 0.3f, -0.06f, 1f);
        this.addSwimAnimation("rear", 0.3f, 0.1f, -0.5f);
        this.addSwimAnimation("rear/tail", 0.3f, 0.1f, -1f);
        this.addSwimAnimation("rear/tail/fin", 0.3f, 0.1f, -1.5f);
    }

    @Override
    public float shadowRadius() {
        return 0.8f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.3f;
    }

    @Override
    public float swimAnimScale() {
        return 0.75f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 26).addBox(-3.5F, -9.0F, -10.0F, 7.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));
        root.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(0, 12).addBox(-1.5793F, 0.0F, -0.1152F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 22.5F, -0.75F, -0.3491F, -1.5708F, 0.0F));
        root.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(8, 0).mirror().addBox(-1.4207F, 0.0F, -0.1152F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.5F, 22.5F, -0.75F, -0.3491F, 1.5708F, 0.0F));

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(29, 0).addBox(-3.0F, -3.0F, -7.0F, 6.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.5F, -10.0F));
        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(26, 28).addBox(-2.0F, -2.0F, -5.5F, 4.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.5F, -7.5F));
        front.addOrReplaceChild("fin_l2", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0793F, 0.0F, -0.1152F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 2.5F, -3.25F, -0.6109F, -1.5708F, 0.0F));
        front.addOrReplaceChild("fin_r2", CubeListBuilder.create().texOffs(0, 3).mirror().addBox(-1.9207F, 0.0F, -0.1152F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.0F, 2.5F, -3.25F, -0.6109F, 1.5708F, 0.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.5F, 0.0F, 6.0F, 9.0F, 17.0F, new CubeDeformation(0.0F))
                .texOffs(0, 29).addBox(0.0F, 3.5F, 0.5F, 0.0F, 4.0F, 17.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.0F, 2.0F));
        rear.addOrReplaceChild("fin_top", CubeListBuilder.create().texOffs(0, 33).addBox(-1.0F, -1.5F, -11.0F, 0.0F, 4.0F, 17.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -6.0F, 6.5F, 0.0F, 3.1416F, 0.0F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(6, 4).addBox(-1.5F, -2.0F, 0.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 17.0F));
        tail.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 3).addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}