package dev.creategalore.fishing.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.creategalore.fishing.process.CleaningTableBlock;
import dev.creategalore.fishing.process.CleaningTableBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Draws the fish on a Cleaning Table as its own 3D model, lying on its side at its size (a long fish overhangs the
 * table, up to {@link #LONGEST}), and, while the player looks at the table, how far the cleaning has got.
 */
final class CleaningTableRenderer implements BlockEntityRenderer<CleaningTableBlockEntity> {
    /** Longest fish drawn on the table, blocks: a tuna overhangs, but not by two metres. */
    static final float LONGEST = 1.6f;
    /** The table top's height, blocks. */
    private static final float TOP = 15 / 16f;

    private final EntityRenderDispatcher entities;
    private final Font font;

    CleaningTableRenderer(BlockEntityRendererProvider.Context context) {
        this.entities = context.getEntityRenderer();
        this.font = context.getFont();
    }

    @Override
    public void render(CleaningTableBlockEntity table, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        ItemStack fish = table.fish();
        if (fish.isEmpty()) return;
        double cm = FishItemRenderer.lengthCm(fish);
        float length = Mth.clamp((float) cm / 100, 0.15f, LONGEST);
        double kg = FishItemRenderer.drawnKg(fish, cm, length * 100);
        float[] box = FishView.extent(fish);
        // Lying on its side, the model's width is its height above the table; the fins (which widen the box) sink
        // into the board a little rather than holding the fish up in the air.
        float lift = 0.6f * box[2] * length / Math.max(0.05f, box[0]) / 2;
        Direction facing = table.getBlockState().getValue(CleaningTableBlock.FACING);
        pose.pushPose();
        pose.translate(0.5f, TOP + lift, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        FishView.draw(fish, length * 100, kg, pose, buffers, light, 0);
        pose.popPose();
        if (looking(table)) label(table, fish, pose, buffers, light);
    }

    private static boolean looking(CleaningTableBlockEntity table) {
        Minecraft mc = Minecraft.getInstance();
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(table.getBlockPos()) && mc.player != null
                && mc.player.distanceToSqr(table.getBlockPos().getCenter()) < 64;
    }

    /** The fish's name and the strokes done, floating above the table like a name tag. */
    private void label(CleaningTableBlockEntity table, ItemStack fish, PoseStack pose, MultiBufferSource buffers, int light) {
        Component progress = table.strokes() == 0
                ? Component.translatable("block.create_galore_fishing.cleaning_table.hint").withStyle(ChatFormatting.GRAY)
                : Component.translatable("block.create_galore_fishing.cleaning_table.progress", table.strokes(), table.needed())
                .withStyle(ChatFormatting.GRAY);
        List<Component> lines = List.of(fish.getHoverName(), progress);
        pose.pushPose();
        pose.translate(0.5, 1.35 + 0.14 * lines.size(), 0.5);
        pose.mulPose(entities.cameraOrientation());
        pose.scale(0.0125f, -0.0125f, 0.0125f);
        FloatingLabel.draw(font, lines, pose.last().pose(), buffers, light);
        pose.popPose();
    }
}
