package dev.creategalore.fishing.tackle;

import dev.creategalore.fishing.FishingRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A tackle part (reel, hook, lure...). Its ratings come from tackle data, so the class itself does little. */
public class TackleItem extends Item {
    public TackleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TackleManager.of(stack).ifPresent(part -> TackleTooltips.describe(part, stack, tooltip));
        Float frayed = stack.get(FishingRegistry.LINE_WEAR.get());
        if (frayed != null && frayed > 0) {
            tooltip.add(Component.translatable("tooltip.create_galore_fishing.frayed", Math.round(frayed * 100)).withStyle(ChatFormatting.GRAY));
        }
        Float worn = stack.get(FishingRegistry.PART_WEAR.get());
        if (worn != null && worn > 0) {
            tooltip.add(Component.translatable("tooltip.create_galore_fishing.worn", Math.round(worn * 100)).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(FishingRegistry.PART_WEAR.get()) || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Float worn = stack.get(FishingRegistry.PART_WEAR.get());
        return worn == null || stack.isDamageableItem() ? super.getBarWidth(stack) : Math.round(13f * (1 - Math.min(1, worn)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        Float worn = stack.get(FishingRegistry.PART_WEAR.get());
        if (worn == null || stack.isDamageableItem()) return super.getBarColor(stack);
        return Mth.hsvToRgb(Math.max(0f, 1 - worn) / 3f, 1f, 1f);
    }
}
