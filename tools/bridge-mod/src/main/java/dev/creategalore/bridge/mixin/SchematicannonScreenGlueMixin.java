package dev.creategalore.bridge.mixin;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.cannon.SchematicannonMenu;
import com.simibubi.create.content.schematics.cannon.SchematicannonScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.item.TooltipHelper;
import dev.creategalore.bridge.SchematicannonGlue;
import java.util.List;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Pre-glue switch, last in the Schematicannon's placement settings row (shown and hidden with them). */
@Mixin(value = SchematicannonScreen.class, remap = false)
public abstract class SchematicannonScreenGlueMixin extends AbstractSimiContainerScreen<SchematicannonMenu> {
    @Shadow
    protected List<AbstractWidget> placementSettingWidgets;

    @Shadow
    protected abstract boolean placementSettingsHidden();

    @Unique
    private IconButton create_galore_bridge$glueButton;
    @Unique
    private static final ItemStack create_galore_bridge$GLUE = AllItems.SUPER_GLUE.asStack();

    private SchematicannonScreenGlueMixin(SchematicannonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Unique
    private SchematicannonGlue.Cannon create_galore_bridge$cannon() {
        return menu.contentHolder instanceof SchematicannonGlue.Cannon cannon ? cannon : null;
    }

    @Inject(method = "initPlacementSettings", at = @At("TAIL"))
    private void create_galore_bridge$addGlueButton(CallbackInfo ci) {
        create_galore_bridge$glueButton = null;
        if (placementSettingsHidden() || create_galore_bridge$cannon() == null) return;
        IconButton button = new IconButton(leftPos + 157, topPos + 111,
                (graphics, x, y) -> graphics.renderItem(create_galore_bridge$GLUE, x, y));
        button.withCallback(() -> {
            SchematicannonGlue.Cannon cannon = create_galore_bridge$cannon();
            if (cannon != null) PacketDistributor.sendToServer(
                    new SchematicannonGlue.Toggle(menu.contentHolder.getBlockPos(), !cannon.create_galore_bridge$preGlue()));
        });
        button.setToolTip(Component.translatable("create_galore_bridge.schematicannon.pre_glue"));
        placementSettingWidgets.add(button);
        addRenderableWidget(button);
        create_galore_bridge$glueButton = button;
    }

    /** Create rebuilds each settings tooltip every tick (title, then "hold Shift"); with Shift this adds the rest. */
    @Inject(method = "handleTooltips", at = @At("TAIL"))
    private void create_galore_bridge$glueTooltip(CallbackInfo ci) {
        IconButton button = create_galore_bridge$glueButton;
        SchematicannonGlue.Cannon cannon = create_galore_bridge$cannon();
        if (button == null || cannon == null || placementSettingsHidden()) return;
        button.green = cannon.create_galore_bridge$preGlue();
        if (!hasShiftDown() || !button.isHovered()) return;
        List<Component> tip = button.getToolTip();
        tip.add(Component.translatable(button.green ? "create.gui.schematicannon.optionEnabled" : "create.gui.schematicannon.optionDisabled")
                .withStyle(button.green ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        tip.addAll(TooltipHelper.cutTextComponent(
                Component.translatable("create_galore_bridge.schematicannon.pre_glue.description"), Palette.ALL_GRAY));
    }
}
