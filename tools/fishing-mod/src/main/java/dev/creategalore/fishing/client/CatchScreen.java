package dev.creategalore.fishing.client;

import dev.creategalore.fishing.cast.CatchHolding;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.net.CatchChoice;
import dev.creategalore.fishing.net.CatchOffer;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The fish you just landed, held up: what it is and how big, and two buttons, Keep (the fish and its XP) and
 * Release (more XP, the fish goes back). Closing the screen keeps you holding the fish; use reopens it. Once the
 * fish has died (two minutes after the catch) only Keep is left.
 */
public class CatchScreen extends Screen {
    private static final String P = "screen.create_galore_fishing.catch.";
    private static final int W = 200;
    private static final int H = 150;

    /** The offer behind the screen, kept so the screen can be reopened. */
    static CatchOffer current;

    private final CatchOffer offer;
    private Button release;

    CatchScreen(CatchOffer offer) {
        super(Component.translatable(P + "title"));
        this.offer = offer;
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener(CatchScreen::onUse);
    }

    /** From the network thread's handler: show the offer. */
    public static void open(CatchOffer offer) {
        current = offer;
        Minecraft.getInstance().setScreen(new CatchScreen(offer));
    }

    /** Pressing use while holding a fish reopens the screen instead of using the item. */
    private static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !event.isUseItem() || current == null) return;
        if (!player.getData(CatchHolding.HELD).holding()) {
            current = null;
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        Minecraft.getInstance().setScreen(new CatchScreen(current));
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.translatable(P + "keep", offer.keepXp()), b -> choose(false))
                .bounds(x + 10, y + H - 28, 85, 20).build());
        release = addRenderableWidget(Button.builder(Component.translatable(P + "release", offer.releaseXp()), b -> choose(true))
                .bounds(x + W - 95, y + H - 28, 85, 20).build());
    }

    private void choose(boolean let) {
        PacketDistributor.sendToServer(new CatchChoice(let));
        current = null;
        onClose();
    }

    private long ticksLeft() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : offer.aliveUntil() - level.getGameTime();
    }

    @Override
    public void tick() {
        release.active = ticksLeft() > 0;
        Player player = Minecraft.getInstance().player;
        if (player != null && !player.getData(CatchHolding.HELD).holding() && ticksLeft() < CatchHolding.ALIVE_TICKS - 40) {
            // The server already decided (it died, or you logged in again): nothing left to choose.
            current = null;
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, 0xFFC6C6C6);
        g.fill(x, y, x + W - 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + H - 1, 0xFFFFFFFF);
        g.fill(x + 1, y + H - 1, x + W, y + H, 0xFF555555);
        g.fill(x + W - 1, y + 1, x + W, y + H, 0xFF555555);
        g.fill(x + 8, y + 22, x + 64, y + 78, 0xFF8B8B8B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.drawString(font, offer.fish().getHoverName(), x + 8, y + 8, 0x404040, false);
        // The fish, big.
        g.pose().pushPose();
        g.pose().translate(x + 12, y + 26, 0);
        g.pose().scale(3, 3, 1);
        g.renderItem(offer.fish(), 0, 0);
        g.pose().popPose();

        int tx = x + 72;
        int ty = y + 24;
        ChatFormatting sizeColor = offer.size().equals("trophy") || offer.size().equals("unique") ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY;
        g.drawString(font, Component.translatable("tooltip.create_galore_fishing.size_class." + offer.size()).withStyle(sizeColor), tx, ty, 0x404040, false);
        g.drawString(font, Component.translatable(P + "weight", CatchInfo.weightText(offer.kg())), tx, ty + 11, 0x404040, false);
        g.drawString(font, Component.translatable(P + "length", String.format(Locale.ROOT, "%.0f", offer.cm())), tx, ty + 22, 0x404040, false);
        g.drawString(font, Component.translatable(P + "rarity." + offer.rarity()).withStyle(rarityColor(offer.rarity())), tx, ty + 33, 0x404040, false);
        Component best = offer.bestGrams() <= 0 ? Component.translatable(P + "first")
                : offer.kg() * 1000 > offer.bestGrams() ? Component.translatable(P + "new_best").withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable(P + "best", CatchInfo.weightText(offer.bestGrams() / 1000.0));
        g.drawString(font, best, tx, ty + 44, 0x404040, false);

        long left = Math.max(0, ticksLeft());
        Component alive = left > 0
                ? Component.translatable(P + "alive", String.format(Locale.ROOT, "%d:%02d", left / 1200, (left / 20) % 60))
                : Component.translatable(P + "dead").withStyle(ChatFormatting.DARK_RED);
        g.drawString(font, alive, x + 10, y + H - 42, 0x404040, false);
    }

    private static ChatFormatting rarityColor(String rarity) {
        return switch (rarity) {
            case "uncommon" -> ChatFormatting.DARK_GREEN;
            case "rare" -> ChatFormatting.BLUE;
            case "very_rare" -> ChatFormatting.DARK_PURPLE;
            case "legendary" -> ChatFormatting.GOLD;
            default -> ChatFormatting.DARK_GRAY;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
