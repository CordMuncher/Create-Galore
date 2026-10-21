package dev.creategalore.fishing.client;
import dev.creategalore.fishing.CreateGaloreFishing;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.sim.Fight;
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
 * The fishing HUD while a line is out. During a fight, three upright stress bars at the right of the screen show how
 * hard the line (against its test, with a tick where the drag slips), the reel (against its drag and crank) and the
 * rod (against its rating) are working: green, yellow, red near breaking, the line blue while slack. The bars ease a
 * little (quick up, slower down) so they read as strain, not flicker. Beside the rod's
 * bar a thin bar shows how high it is lifted. Above the hotbar: the drag setting, line out and left on the spool, and
 * alerts for a bite, a slipping drag, a stalled reel, a slack line, a fish swimming in, a run, head shakes and a sulking
 * fish.
 */
public final class FishingHud {
    private static final int TEXT_W = 220;
    private static final int BAR_H = 70;
    private static final int BAR_W = 6;
    private static final int BAR_GAP = 28;
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
        if (player == null || mc.options.hideGui || !(player.fishing instanceof CastHook hook)
                || hook.mountPos().isPresent()) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        // Above the action bar (drag changes are reported there) and the held item's name.
        int y = g.guiHeight() - 92;

        ItemStack rod = player.getItemInHand(hook.hand());
        RodStats stats = RodStats.of(rod).orElse(null);
        float out = hook.lineOut();
        float left = stats != null ? stats.lineMeters() : hook.lineLeft();

        if (hook.phase() == CastHook.Phase.FIGHTING) {
            boolean coming = hook.flag(CastHook.FLAG_COMING);
            boolean slack = hook.flag(CastHook.FLAG_SLACK) || hook.slack() > 0.1f;
            int x0 = cx - TEXT_W / 2;
            // Line, reel and rod, right of the crosshair's half of the screen, centred on it vertically.
            int right = g.guiWidth() - 16;
            int barsX = right - 2 * BAR_GAP - BAR_W;
            int top = g.guiHeight() / 2 - BAR_H / 2;
            float dragAt = hook.breakingLoad() > 0 ? hook.drag() / hook.breakingLoad() : 0;
            stressBar(g, font, barsX, top, P + "bar.line", hook.shownTension(), slack, dragAt);
            float reel = hook.shownReelLoad();
            if (reel >= 0) stressBar(g, font, barsX + BAR_GAP, top, P + "bar.reel", reel, false, 0);
            int rodX = barsX + 2 * BAR_GAP;
            stressBar(g, font, rodX, top, P + "bar.rod", hook.shownRodLoad(), false, 0);
            // How high the rod is lifted: a thin bar just right of the rod's.
            int h = Math.round(BAR_H * Mth.clamp(hook.rodHeight(), 0, 1));
            g.fill(rodX + BAR_W + 2, top - 1, rodX + BAR_W + 5, top + BAR_H + 1, 0xC0000000);
            if (h > 0) g.fill(rodX + BAR_W + 3, top + BAR_H - h, rodX + BAR_W + 4, top + BAR_H, 0xFFDDDDDD);
            // What the fish is doing, when it matters: coming in faster than the line is wound, shaking its head,
            // sulking (a moment to gain line).
            String status = hook.flag(CastHook.FLAG_STALLED) ? "stalled" : hook.flag(CastHook.FLAG_SLIPPING) ? "slipping"
                    : coming ? "coming" : slack ? "slack" : hook.flag(CastHook.FLAG_RUNNING) ? "run"
                    : hook.mood() == Fight.Mood.SHAKE ? "shaking" : hook.mood() == Fight.Mood.REST ? "sulking" : "";
            if (!status.isEmpty()) {
                Component text = Component.translatable(P + status);
                g.drawString(font, text, cx - font.width(text) / 2, y - 11, statusColor(status), true);
            }
            // The drag against what the line holds now; red when it is over, as the line bar's tick is.
            boolean over = hook.drag() > 0 && hook.drag() > hook.breakingLoad();
            Component drag = hook.drag() > 0
                    ? Component.translatable(P + "drag", RodStats.kg(hook.drag()), RodStats.kgHolds(hook.breakingLoad()))
                    : Component.translatable(P + "drag.none");
            g.drawString(font, drag, x0, y + 8, over ? 0xFFFF5050 : 0xFFDDDDDD, true);
            Component line = Component.translatable(P + "line", Math.round(out), Math.round(left));
            // Side by side when both fit, else the line count goes on the row below.
            boolean fits = font.width(drag) + font.width(line) + 8 <= TEXT_W;
            g.drawString(font, line, x0 + TEXT_W - font.width(line), fits ? y + 8 : y + 19, 0xFFDDDDDD, true);
        } else {
            Component line = Component.translatable(P + "line", Math.round(out), Math.round(left));
            g.drawString(font, line, cx - font.width(line) / 2, y + 8, 0xFFBBBBBB, true);
            if (hook.phase() == CastHook.Phase.BITE) {
                Component bite = Component.translatable(P + "bite", mc.options.keyAttack.getTranslatedKeyMessage());
                long blink = player.level().getGameTime() / 3 % 2;
                g.drawString(font, bite, cx - font.width(bite) / 2, y - 4, blink == 0 ? 0xFFFFE040 : 0xFFFF8020, true);
            }
        }
    }

    /**
     * One upright stress bar, filling from the bottom: green, yellow past 60%, red past 85% (blue while slack), with
     * a white tick at {@code mark} (a fraction of the bar) when set, red at the top when it is past the top, and its name below.
     */
    private static void stressBar(GuiGraphics g, Font font, int x, int top, String name, float load, boolean slack, float mark) {
        g.fill(x - 1, top - 1, x + BAR_W + 1, top + BAR_H + 1, 0xC0000000);
        int fill = Math.round(BAR_H * Mth.clamp(load, 0, 1));
        int color = slack ? 0xFF4A90E2 : load < 0.6f ? 0xFF4CC94C : load < 0.85f ? 0xFFE8C840 : 0xFFE04030;
        if (fill > 0) g.fill(x, top + BAR_H - fill, x + BAR_W, top + BAR_H, color);
        if (mark > 0) {
            int my = top + BAR_H - Math.round(BAR_H * Mth.clamp(mark, 0, 1));
            g.fill(x - 2, my, x + BAR_W + 2, my + 1, mark > 1 ? 0xFFFF4040 : 0xFFFFFFFF);
        }
        Component label = Component.translatable(name);
        g.drawString(font, label, x + BAR_W / 2 - font.width(label) / 2, top + BAR_H + 3, 0xFFDDDDDD, true);
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "stalled" -> 0xFFFF6060;
            case "slipping" -> 0xFFFFD040;
            case "slack", "coming" -> 0xFF80B0FF;
            case "sulking" -> 0xFFA0E0A0;
            default -> 0xFFFFFFFF;
        };
    }
}
