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

public class DarknessEaterModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("darkness_eater");

    public DarknessEaterModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = swimAnimSpeed();
        this.addSwimAnimation("front/head", speed, -0.08f, -1f);
        this.addSwimAnimation("rear", speed, 0.13f);
        this.addSwimAnimation("rear/tail", speed, 0.15f, -1f);
        this.addSwimAnimation("rear/tail/fin", speed, 0.17f, -1.5f);
    }

    @Override
    public float shadowRadius() {
        return 0.8f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.2f;
    }

    @Override
    public float swimAnimScale() {
        return 0.85f;
    }

    @Override
    public double xTiltScale() {
        return 1.85f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(2, 2).addBox(-5.0F, -5.0F, -15.0F, 10.0F, 9.0F, 15.0F, new CubeDeformation(0.0F))
                .texOffs(32, 48).addBox(0.0F, -11.0F, -11.0F, 0.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.0F, 3.0F));

        front.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(0, 37).addBox(0.0F, 0.0F, -3.5F, 9.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 2.25F, -11.5F, 0.0F, -0.2182F, 0.5236F));
        front.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(32, 39).addBox(-9.0F, 0.0F, -3.5F, 9.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 2.25F, -11.5F, 0.0F, 0.2182F, -0.5236F));

        PartDefinition head = front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 26).addBox(-4.5F, -4.5F, -8.0F, 9.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(50, 46).addBox(3.5F, -1.5F, -8.0F, 1.0F, 5.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 53).addBox(-4.5F, -1.5F, -8.0F, 1.0F, 5.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 44).addBox(-3.5F, 2.5F, -8.0F, 7.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(60, 31).addBox(-3.5F, -1.5F, -1.0F, 7.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -15.0F));

        head.addOrReplaceChild("whiskers_r", CubeListBuilder.create().texOffs(54, 16).addBox(-6.0F, 0.0F, -2.5F, 6.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, 2.25F, -4.5F, 0.0F, 0.3491F, -1.309F));
        head.addOrReplaceChild("whiskers_l", CubeListBuilder.create().texOffs(54, 11).addBox(0.0F, 0.0F, -2.5F, 6.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 2.25F, -4.5F, 0.0F, -0.3491F, 1.309F));
        head.addOrReplaceChild("teeth", CubeListBuilder.create().texOffs(64, 37).addBox(-3.0F, -6.0F, -6.0F, 7.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(64, 41).addBox(-3.0F, -6.0F, -4.0F, 7.0F, 4.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(64, 41).addBox(-3.0F, -6.0F, -2.0F, 7.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 4.5F, -2.0F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(34, 26).addBox(-3.5F, -3.5F, 0.0F, 7.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 19.5F, 2.75F, -0.0436F, 0.0F, 0.0F));
        rear.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(54, 21).addBox(-5.0F, 0.0F, -2.5F, 5.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 3.0F, 1.5F, 0.0F, 0.3491F, -0.5236F));
        rear.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(60, 26).addBox(0.0F, 0.0F, -2.5F, 5.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 3.0F, 1.5F, 0.0F, -0.3491F, 0.5236F));

        PartDefinition tail = rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(54, 0).addBox(-2.0F, -3.0F, 0.0F, 4.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 5.75F, -0.0873F, 0.0F, 0.0F));
        PartDefinition fin = tail.addOrReplaceChild("fin", CubeListBuilder.create(), PartPose.offset(0.0F, -0.25F, 5.0F));
        fin.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(38, 62).addBox(0.0F, -7.0F, -2.0F, 0.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, -2.5307F, 0.0F, 0.0F));
        fin.addOrReplaceChild("top", CubeListBuilder.create().texOffs(18, 53).addBox(0.0F, -11.0F, -2.0F, 0.0F, 11.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, -0.9599F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
	}
}