package dev.creategalore.bridge;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Field Guide made small and hung on a chain, worn in a necklace slot (tag curios:necklace). While it is worn, the
 * pack's keys open the Field Guide and Ask the Book without the book in hand; right-click in hand opens the guide like
 * the book does. The keys and the worn look are client-side (PendantClient). Recipe lives in the pack datapack.
 */
public class FieldGuidePendantItem extends Item {
    /** Opens the Field Guide; set by the client, does nothing on a server. */
    public static Runnable openGuide = () -> {
    };

    public FieldGuidePendantItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) openGuide.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_galore_bridge.field_guide_pendant.tooltip",
                Component.keybind("key.create_galore_bridge.pendant_guide").withStyle(ChatFormatting.YELLOW),
                Component.keybind("key.create_galore_bridge.pendant_ask").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
    }
}
