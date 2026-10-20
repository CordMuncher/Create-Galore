package dev.creategalore.fishing.client;

import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.tackle.RodStats;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The fishing HUD above the hotbar while a line is out: a tension bar against the breaking load (blue when the
 * line is slack, green, yellow, red near a snap) with a tick where the drag starts to slip, the drag setting, line
 * out and left on the spool, and alerts for a bite, a slipping drag and a stalled reel.
 */
public final class FishingHud {
    private static final int BAR_W = 140;
    private static final String P = "hud.create_galore_fishing.";

    private FishingHud() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAbove(VanillaGuiLayers.HOTBAR,
                CreateGaloreFishing.id("fishing"), FishingHud::render));
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || !(player.fishing instanceof CastHook hook)) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        // Above the action bar (drag changes are reported there) and the held item's name.
        int y = g.guiHeight() - 92;

        ItemStack rod = player.getItemInHand(hook.hand());
        RodStats stats = RodStats.of(rod).orElse(null);
        float out = hook.lineOut();
        float left = stats != null ? stats.lineMeters() : hook.lineLeft();

        if (hook.phase() == CastHook.Phase.FIGHTING) {
            float tension = hook.tension();
            boolean slack = hook.flag(CastHook.FLAG_SLACK);
            int x0 = cx - BAR_W / 2;
            g.fill(x0 - 1, y - 1, x0 + BAR_W + 1, y + 6, 0xC0000000);
            int fill = (int) (BAR_W * Mth.clamp(tension, 0, 1));
            int color = slack ? 0xFF4A90E2 : tension < 0.6f ? 0xFF4CC94C : tension < 0.85f ? 0xFFE8C840 : 0xFFE04030;
            g.fill(x0, y, x0 + Math.max(1, fill), y + 5, color);
            // Where the drag lets go, as a fraction of the breaking load.
            float dragAt = hook.breakingLoad() > 0 ? hook.drag() / hook.breakingLoad() : 0;
            if (dragAt > 0 && dragAt <= 1.2f) {
                int dx = x0 + (int) (BAR_W * Mth.clamp(dragAt, 0, 1));
                g.fill(dx, y - 2, dx + 1, y + 7, dragAt > 1 ? 0xFFFF4040 : 0xFFFFFFFF);
            }
            String status = hook.flag(CastHook.FLAG_STALLED) ? "stalled" : hook.flag(CastHook.FLAG_SLIPPING) ? "slipping"
                    : slack ? "slack" : hook.flag(CastHook.FLAG_RUNNING) ? "run" : "";
            if (!status.isEmpty()) {
                Component text = Component.translatable(P + status);
                g.drawString(font, text, cx - font.width(text) / 2, y - 11, statusColor(status), true);
            }
            String drag = hook.drag() > 0 ? String.format(Locale.ROOT, "%.1f kg", hook.drag()) : "-";
            g.drawString(font, Component.translatable(P + "drag", drag), x0, y + 8, 0xFFDDDDDD, true);
            Component line = Component.translatable(P + "line", Math.round(out), Math.round(left));
            g.drawString(font, line, x0 + BAR_W - font.width(line), y + 8, 0xFFDDDDDD, true);
        } else {
            Component line = Component.translatable(P + "line", Math.round(out), Math.round(left));
            g.drawString(font, line, cx - font.width(line) / 2, y + 8, 0xFFBBBBBB, true);
            if (hook.phase() == CastHook.Phase.BITE) {
                Component bite = Component.translatable(P + "bite");
                long blink = player.level().getGameTime() / 3 % 2;
                g.drawString(font, bite, cx - font.width(bite) / 2, y - 4, blink == 0 ? 0xFFFFE040 : 0xFFFF8020, true);
            }
        }
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "stalled" -> 0xFFFF6060;
            case "slipping" -> 0xFFFFD040;
            case "slack" -> 0xFF80B0FF;
            default -> 0xFFFFFFFF;
        };
    }
}
