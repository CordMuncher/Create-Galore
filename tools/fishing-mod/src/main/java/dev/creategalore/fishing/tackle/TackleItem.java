package dev.creategalore.fishing.tackle;

import java.util.List;
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
    }
}
