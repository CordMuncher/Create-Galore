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

public class AquathornModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("aquathorn");

    public AquathornModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        this.addSwimAnimation("front/head", 0.6f, -0.15f);
        this.addSwimAnimation("rear", 0.6f, 0.25f);
        this.addSwimAnimation("rear/tail", 0.6f, 0.25f);
    }

    @Override
    public float shadowRadius() {
        return 0.23f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -2.0F, -1.0F, 2.0F, 3.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(0, 9).addBox(-0.5F, -3.5F, 1.0F, 0.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 23.0F, -2.0F));

        front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(8, 9).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(8, 8).addBox(1.01F, -0.5F, -1.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(8, 9).mirror().addBox(-1.01F, -0.5F, -1.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-0.5F, -0.5F, -1.0F));

        PartDefinition spikes = front.addOrReplaceChild("spikes", CubeListBuilder.create(), PartPose.offset(0.0F, -0.5F, 2.0F));
        spikes.addOrReplaceChild("spike_m4", CubeListBuilder.create().texOffs(16, 7).addBox(-2.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 0.0F, 1.25F, -1.5708F, 0.1309F, 0.0F));
        spikes.addOrReplaceChild("spike_m3", CubeListBuilder.create().texOffs(16, 1).addBox(-2.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 0.0F, -1.25F, -1.5708F, -0.1309F, 0.0F));
        spikes.addOrReplaceChild("spike_m2", CubeListBuilder.create().texOffs(16, 4).addBox(0.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 0.0F, 1.25F, -1.5708F, -0.1309F, 0.0F));
        spikes.addOrReplaceChild("spike_m1", CubeListBuilder.create().texOffs(16, 6).addBox(0.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 0.0F, -1.25F, -1.5708F, 0.1309F, 0.0F));
        spikes.addOrReplaceChild("spike_t4", CubeListBuilder.create().texOffs(16, 5).addBox(-2.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -1.0F, 1.75F, -1.5708F, 0.2618F, 0.8727F));
        spikes.addOrReplaceChild("spike_t3", CubeListBuilder.create().texOffs(4, 16).addBox(0.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -1.0F, 1.75F, -1.5708F, -0.2618F, -0.8727F));
        spikes.addOrReplaceChild("spike_t2", CubeListBuilder.create().texOffs(16, 3).addBox(0.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -1.0F, -1.75F, -1.5708F, 0.2618F, -0.8727F));
        spikes.addOrReplaceChild("spike_t1", CubeListBuilder.create().texOffs(16, 2).addBox(-2.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -1.0F, -1.75F, -1.5708F, -0.2618F, 0.8727F));
        spikes.addOrReplaceChild("spike_b4", CubeListBuilder.create().texOffs(4, 15).addBox(-2.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 1.0F, 1.75F, -1.5708F, 0.2618F, -0.8727F));
        spikes.addOrReplaceChild("spike_b3", CubeListBuilder.create().texOffs(0, 16).addBox(0.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 1.0F, 1.75F, -1.5708F, -0.2618F, 0.8727F));
        spikes.addOrReplaceChild("spike_b2", CubeListBuilder.create().texOffs(16, 0).addBox(-2.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 1.0F, -1.75F, -1.5708F, -0.2618F, -0.8727F));
        spikes.addOrReplaceChild("spike_b1", CubeListBuilder.create().texOffs(0, 15).addBox(0.039F, -0.5F, 0.0142F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 1.0F, -1.75F, -1.5708F, 0.2618F, 0.8727F));

        PartDefinition rear = root.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(8, 13).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, 3.0F));
        rear.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(14, 13).addBox(0.0F, -1.5F, 0.0F, 0.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 1.0F));

        return LayerDefinition.create(mesh, 32, 32);
	}
}