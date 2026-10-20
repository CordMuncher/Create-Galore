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

public class SnatcherSquidModel extends FishModel {
    public static final ModelLayerLocation MODEL_LOCATION = createModelLocation("snatcher_squid");

    public SnatcherSquidModel(EntityRendererProvider.Context context) {
        super(context, MODEL_LOCATION);
        float speed = this.swimAnimSpeed();
        this.addSwimAnimation("back/tentacles/1", speed * 1.5f, 0.3f, SwimAxis.Z);
        this.addSwimAnimation("back/tentacles/2", speed * 1.5f, 0.3f, 1f, SwimAxis.Z);
        this.addSwimAnimation("back/tentacles/3", speed * 1.2f, 0.3f, SwimAxis.X);
        this.addSwimAnimation("back/tentacles/4", speed * 1.2f, 0.3f, 1f, SwimAxis.X);
        this.addSwimAnimation("back/tentacles/5", speed, 0.3f, SwimAxis.X);
        this.addSwimAnimation("back/tentacles/6", speed, 0.3f, 1f, SwimAxis.X);
        this.addSwimAnimation("back/tentacles/6", speed, 0.3f, 1f, SwimAxis.X);
        this.addSwimAnimation("front/tip", speed, 0.1f, SwimAxis.X);
    }

    @Override
    public boolean flipInAir() {
        return false;
    }

    @Override
    public float shadowRadius() {
        return 1.0f;
    }

    @Override
    public float swimAnimSpeed() {
        return 0.25f;
    }

    @Override
    public double xTiltScale() {
        return 1.5f;
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

        PartDefinition back = root.addOrReplaceChild("back", CubeListBuilder.create().texOffs(38, 10).addBox(-0.5F, -1.5F, -2.75F, 6.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, 22.5F, 5.75F));

        PartDefinition tentacles = back.addOrReplaceChild("tentacles", CubeListBuilder.create(), PartPose.offset(5.0F, 0.0F, 0.0F));

        PartDefinition t1 = tentacles.addOrReplaceChild("1", CubeListBuilder.create(), PartPose.offset(-5.0F, 0.0F, 0.0F));
        PartDefinition t1Pivot = t1.addOrReplaceChild("1_pivot", CubeListBuilder.create().texOffs(46, 28).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0873F, -1.0472F, 0.0F));
        t1Pivot.addOrReplaceChild("1_pivot_2", CubeListBuilder.create().texOffs(38, 44).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, -1.309F, 0.0F));

        PartDefinition t2 = tentacles.addOrReplaceChild("2", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition t2Pivot = t2.addOrReplaceChild("2_pivot", CubeListBuilder.create().texOffs(10, 47).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0873F, 1.0472F, 0.0F));
        t2Pivot.addOrReplaceChild("2_pivot_2", CubeListBuilder.create().texOffs(0, 46).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.0F, 1.309F, 0.0F));

        PartDefinition t3 = tentacles.addOrReplaceChild("3", CubeListBuilder.create(), PartPose.offset(-3.5F, 0.0F, 0.0F));
        PartDefinition t3Pivot = t3.addOrReplaceChild("3_pivot", CubeListBuilder.create().texOffs(40, 0).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0873F, -0.6981F, 0.0F));
        t3Pivot.addOrReplaceChild("3_pivot_2", CubeListBuilder.create().texOffs(40, 38).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.0F, -1.0472F, 0.0F));

        PartDefinition t4 = tentacles.addOrReplaceChild("4", CubeListBuilder.create(), PartPose.offset(-1.5F, 0.0F, 0.0F));
        PartDefinition t4Pivot = t4.addOrReplaceChild("4_pivot", CubeListBuilder.create().texOffs(0, 39).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0873F, 0.6981F, 0.0F));
        t4Pivot.addOrReplaceChild("4_pivot_2", CubeListBuilder.create().texOffs(40, 32).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.0F, 1.0472F, 0.0F));

        PartDefinition t5 = tentacles.addOrReplaceChild("5", CubeListBuilder.create(), PartPose.offset(-2.75F, 0.0F, 0.0F));
        PartDefinition t5Pivot = t5.addOrReplaceChild("5_pivot", CubeListBuilder.create().texOffs(32, 19).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.2618F, 0.0F));
        t5Pivot.addOrReplaceChild("5_pivot_2", CubeListBuilder.create().texOffs(14, 41).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.0F, -1.0472F, 0.0F));

        PartDefinition t6 = tentacles.addOrReplaceChild("6", CubeListBuilder.create(), PartPose.offset(-2.25F, 0.0F, 0.0F));
        PartDefinition t6Pivot = t6.addOrReplaceChild("6_pivot", CubeListBuilder.create().texOffs(22, 32).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.2618F, 0.0F));
        t6Pivot.addOrReplaceChild("6_pivot_2", CubeListBuilder.create().texOffs(26, 41).addBox(-0.5F, -0.5F, -0.25F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, 0.0F, 1.0472F, 0.0F));

        PartDefinition front = root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 19).addBox(-3.5F, -2.0F, -9.0F, 7.0F, 4.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-5.0F, 0.0F, -9.0F, 10.0F, 0.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 22.5F, 3.0F));

        front.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -1.0F, -5.0F, 6.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(32, 28).addBox(-2.0F, -0.5F, -8.0F, 4.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(0, 10).addBox(-5.0F, 0.0F, -9.0F, 10.0F, 0.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -9.0F));

        return LayerDefinition.create(mesh, 64, 64);
	}
}