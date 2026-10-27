package dev.creategalore.bridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.creategalore.bridge.CreateGaloreBridge;
import dev.creategalore.bridge.FieldGuidePendantItem;
import dev.creategalore.bridge.wear.Worn;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * The Field Guide Pendant on the client: while it is worn, one key opens the Field Guide and another opens Ask the
 * Book, from anywhere in the game with nothing in hand. A worn pendant can't glow green in a hotbar slot the way the
 * book does when an answer is waiting, so the answer notice (GuideAiClient) shows the pendant and the key instead.
 * On the player it hangs from the neck on its chain, over armor too.
 */
public final class PendantClient {
    private PendantClient() {
    }

    private static final String CATEGORY = "key.categories.create_galore_bridge.field_guide";
    // I was the only letter no mod in the pack uses (The Aether's and TFMG's I keys were moved off it in pack 0.2.1).
    static final KeyMapping GUIDE = new KeyMapping("key.create_galore_bridge.pendant_guide", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);
    static final KeyMapping ASK = new KeyMapping("key.create_galore_bridge.pendant_ask", KeyConflictContext.IN_GAME, KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(GUIDE);
            e.register(ASK);
        });
        NeoForge.EVENT_BUS.addListener(PendantClient::onTick);
        FieldGuidePendantItem.openGuide = PendantClient::openGuide;
        if (Worn.HAS_CURIOS) modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() ->
                CuriosRendererRegistry.register(CreateGaloreBridge.FIELD_GUIDE_PENDANT.get(), Look::new)));
    }

    /** Whether the player has the pendant on (in any worn slot, so a pack that moves it to another slot still counts). */
    static boolean worn(Player player) {
        if (player == null) return false;
        for (Worn.Loc loc : Worn.all(player)) {
            if (Worn.get(player, loc).is(CreateGaloreBridge.FIELD_GUIDE_PENDANT.get())) return true;
        }
        return false;
    }

    /** The key's name, short enough for a notice ("Shift+I" for NeoForge's "SHIFT + I"), or empty when the key is unbound. */
    static String keyName(KeyMapping key) {
        if (key.isUnbound()) return "";
        String name = key.getTranslatedKeyMessage().getString().replace(" + ", "+");
        for (String mod : new String[] {"SHIFT", "CTRL", "ALT"})
            if (name.startsWith(mod + "+")) name = mod.charAt(0) + mod.substring(1).toLowerCase(java.util.Locale.ROOT) + name.substring(mod.length());
        return name;
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean guide = false, ask = false;
        while (GUIDE.consumeClick()) guide = true;
        while (ASK.consumeClick()) ask = true;
        if (!(guide || ask) || mc.player == null || mc.screen != null) return;
        if (!worn(mc.player)) {
            mc.player.displayClientMessage(Component.translatable("create_galore_bridge.pendant.not_worn"), true);
            return;
        }
        if (ask) mc.setScreen(new AskScreen(null));
        else openGuide();
    }

    static void openGuide() {
        if (!ModList.get().isLoaded("modonomicon")) return;
        if (!Modonomicon.open()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("create_galore_bridge.pendant.no_guide"), true);
        }
    }

    /** Kept apart so Modonomicon's classes are only loaded when it is installed. */
    private static final class Modonomicon {
        static boolean open() {
            var book = com.klikli_dev.modonomicon.data.BookDataManager.get().getBook(GuideAiClient.FIELD_GUIDE);
            if (book == null) return false;
            com.klikli_dev.modonomicon.client.gui.BookGuiManager.get()
                    .openBook(com.klikli_dev.modonomicon.client.gui.book.BookAddress.defaultFor(book));
            return true;
        }
    }

    /** The pendant on the player: the item, small, hanging from the neck on the chest, turning and crouching with the body. */
    private static final class Look implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slot, PoseStack pose,
                RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> model)) return;
            LivingEntity entity = slot.entity();
            pose.pushPose();
            model.body.translateAndRotate(pose);
            // Model space: y down from the neck, the chest's front at z -2/16; a chestplate stands a pixel further out.
            boolean armor = !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
            pose.translate(0, 0.22, armor ? -0.205 : -0.14);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(0.36f, 0.36f, 0.36f);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId());
            pose.popPose();
        }
    }
}
