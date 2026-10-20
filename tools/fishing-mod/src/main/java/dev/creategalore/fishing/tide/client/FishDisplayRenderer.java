/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported from Tide 2 (https://github.com/Lightning-64/Tide-2) by Lightning-64,
 * modified for Create Galore Fishing.
 */
package dev.creategalore.fishing.tide.client;
import org.joml.Matrix4f;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.ChatFormatting;
import java.util.List;
import java.util.ArrayList;
import dev.creategalore.fishing.tide.util.TideUtils;
import dev.creategalore.fishing.client.FishingClient;
import dev.creategalore.fishing.tide.compat.CompatHelper;
import dev.creategalore.fishing.tide.data.fishing.DisplayData;
import dev.creategalore.fishing.tide.registries.blocks.FishDisplayBlock;
import dev.creategalore.fishing.tide.registries.blocks.entities.FishDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public record FishDisplayRenderer(EntityRenderDispatcher entityRenderer) implements BlockEntityRenderer<FishDisplayBlockEntity> {
    public FishDisplayRenderer(BlockEntityRendererProvider.Context entityRenderer) {
        this(entityRenderer.getEntityRenderer());
    }

    @Override
    public void render(FishDisplayBlockEntity display, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (display.isEmpty()) return;
        DisplayData displayData = display.getDisplayData();
        poseStack.pushPose();

        Direction dir = display.getBlockState().getValue(FishDisplayBlock.FACING);
        poseStack.translate(0.5f, 0.5f, 0.5f); // center rotation
        poseStack.mulPose(Axis.YP.rotationDegrees(-dir.toYRot() + 90)); // rotate based on facing direction
        poseStack.translate(0.5f, 0.0f, 0.0f); // move towards display
        poseStack.pushPose();

        // apply translations
        poseStack.translate(-displayData.z(), displayData.y(), displayData.x());
        // apply rotations
        poseStack.mulPose(Axis.ZP.rotationDegrees(displayData.roll() + 90f));
        poseStack.mulPose(Axis.XP.rotationDegrees(displayData.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(displayData.yaw()));

        EntityType<?> entityType = displayData.entityType();
        if (entityType == null) {
            poseStack.popPose();
            poseStack.popPose();
            return;
        }
        Entity entity = display.getRenderedEntity();
        if (entity == null || entity.getType() != entityType
                && Minecraft.getInstance().level != null) {
            entity = entityType.create(Minecraft.getInstance().level);
            if (entity != null) {
                if (displayData.nbt().isPresent()) entity.load(displayData.nbt().get());
                if (CompatHelper.isHybridAquaticLoaded()) CompatHelper.hybridAquaticApplyVariant(entity, display.getDisplayStack());
                entity.xRotO = entity.getXRot();
                entity.yRotO = entity.getYRot();
            }
            display.setRenderedEntity(entity);
        }

        if (entity != null) {
            poseStack.pushPose();

            double lengthCm = display.getFishLength();
            double baseLengthCm = display.getBaseLength();

            float scale = Mth.sqrt((float)(lengthCm / baseLengthCm));
            poseStack.scale(scale, scale, scale);

            this.entityRenderer.render(entity,
                    0, 0, 0,
                    0, 0,
                    poseStack, buffer,
                    packedLight
            );
            poseStack.popPose();
        }

        poseStack.popPose();
        poseStack.popPose();
        if (looking(display)) label(display, poseStack, buffer, packedLight);
    }

    /** True when the player's crosshair is on this mount, close enough to read it. */
    private static boolean looking(FishDisplayBlockEntity display) {
        Minecraft mc = Minecraft.getInstance();
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(display.getBlockPos()) && mc.player != null
                && mc.player.distanceToSqr(display.getBlockPos().getCenter()) < 64;
    }

    /**
     * The trophy's label (Create Galore Fishing): the fish's name and, for a fish caught with this mod, its size
     * class, weight, length and angler, floating above the mount like a name tag.
     */
    private void label(FishDisplayBlockEntity display, PoseStack poseStack, MultiBufferSource buffer, int light) {
        ItemStack stack = display.getDisplayStack();
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        lines.addAll(FishingClient.catchLines(stack));
        if (lines.size() == 1 && display.getFishLength() > 0)
            lines.add(TideUtils.getFormattedLength(display.getFishLength()).withStyle(ChatFormatting.GRAY));
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(0.5, 1.0 + 0.14 * lines.size(), 0.5);
        poseStack.mulPose(entityRenderer.cameraOrientation());
        poseStack.scale(0.0125f, -0.0125f, 0.0125f);
        Matrix4f pose = poseStack.last().pose();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255) << 24;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            // The tooltip's dark grey is unreadable on the label's dark backing.
            if (line.getStyle().getColor() != null && line.getStyle().getColor().getValue() == 0x555555)
                line = line.copy().withStyle(ChatFormatting.GRAY);
            font.drawInBatch(line, -font.width(line) / 2f, i * 11, 0xffffffff, false, pose, buffer,
                    Font.DisplayMode.NORMAL, background, light);
        }
        poseStack.popPose();
    }
}