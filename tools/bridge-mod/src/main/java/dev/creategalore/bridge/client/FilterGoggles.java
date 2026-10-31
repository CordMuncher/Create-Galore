package dev.creategalore.bridge.client;

import dev.creategalore.bridge.FilterPressure;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Engineer's Goggles on a filter frame: a green, yellow or red reading of the pressure under it, worked out by the
 * server ({@link FilterPressure}) and asked for about twice a second while the goggles rest on the frame.
 */
public final class FilterGoggles {
    private FilterGoggles() {}

    private static final int ASK_EVERY = 10;
    /** Readings older than this are not shown (the frame is asked again). */
    private static final int STALE_AFTER = 60;
    /** How far back the room's smog is compared to say whether it is filling up or clearing. */
    private static final int TREND_SPAN = 40;
    private static final int BAR_CELLS = 20;
    private static final String INDENT = "    ";
    private static final String DETAIL = "     ";

    private static final class Entry {
        FilterPressure.Reading reading;
        long readAt = Long.MIN_VALUE;
        long askedAt = Long.MIN_VALUE;
        long snapAt = Long.MIN_VALUE;
        int snapUnits;
        int trend;
    }

    private static final Map<BlockPos, Entry> entries = new HashMap<>();

    public static void onReading(FilterPressure.Reading reading) {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            long now = mc.level.getGameTime();
            Entry entry = entries.computeIfAbsent(reading.frame(), k -> new Entry());
            entry.reading = reading;
            entry.readAt = now;
            if (entry.snapAt == Long.MIN_VALUE || now - entry.snapAt >= TREND_SPAN || now < entry.snapAt) {
                if (entry.snapAt != Long.MIN_VALUE && now >= entry.snapAt) entry.trend = Integer.signum(reading.units() - entry.snapUnits);
                entry.snapAt = now;
                entry.snapUnits = reading.units();
            }
        });
    }

    /** Called by the frame's block entity while Engineer's Goggles look at it. */
    public static boolean addToTooltip(BlockPos frame, List<Component> tooltip, boolean sneaking) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        ClientPacketListener connection = mc.getConnection();
        // A server without this Bridge version has no gauge: say nothing rather than wait forever.
        if (connection == null || !connection.hasChannel(FilterPressure.Request.TYPE)) return false;
        long now = mc.level.getGameTime();
        if (entries.size() > 64) entries.clear();
        Entry entry = entries.computeIfAbsent(frame.immutable(), k -> new Entry());
        if (entry.askedAt == Long.MIN_VALUE || now - entry.askedAt >= ASK_EVERY || now < entry.askedAt) {
            entry.askedAt = now;
            PacketDistributor.sendToServer(new FilterPressure.Request(frame.immutable()));
        }

        tooltip.add(Component.literal(INDENT).append(Component.translatable("create_galore_bridge.filter_gauge.title")));
        FilterPressure.Reading r = entry.reading;
        if (r == null || now - entry.readAt > STALE_AFTER || now < entry.readAt) {
            tooltip.add(detail(Component.translatable("create_galore_bridge.filter_gauge.reading").withStyle(ChatFormatting.DARK_GRAY)));
            return true;
        }
        FilterPressure.State state = r.state();
        ChatFormatting colour = switch (state.level()) {
            case 2 -> ChatFormatting.RED;
            case 1 -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GREEN;
        };
        String word = switch (state.level()) {
            case 2 -> "overpressure";
            case 1 -> state == FilterPressure.State.OPEN_BLOCKED ? "not_filtering" : "pressure";
            default -> "normal";
        };
        tooltip.add(detail(Component.literal("● ").withStyle(colour)
                .append(Component.translatable("create_galore_bridge.filter_gauge.state." + word).withStyle(colour))));
        tooltip.add(detail(Component.translatable("create_galore_bridge.filter_gauge.why." + state.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY)));

        if (r.room() > 0) {
            int percent = Math.min(100, Math.round(100f * r.units() / r.room()));
            String shown = r.units() > 0 && percent == 0 ? "<1%" : percent + "%";
            int filled = Math.min(BAR_CELLS, Math.round((float) BAR_CELLS * r.units() / r.room()));
            if (r.units() > 0 && filled == 0) filled = 1;
            MutableComponent bar = Component.literal("|".repeat(filled)).withStyle(colour)
                    .append(Component.literal("|".repeat(BAR_CELLS - filled)).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(detail(Component.translatable("create_galore_bridge.filter_gauge.room").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" ")).append(bar)
                    .append(Component.literal(" " + shown).withStyle(colour))));
            if (r.units() > 0 || entry.trend != 0) {
                String trend = entry.trend > 0 ? "filling" : entry.trend < 0 ? "clearing" : "steady";
                tooltip.add(detail(Component.translatable("create_galore_bridge.filter_gauge.trend." + trend)
                        .withStyle(entry.trend > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
            }
        }
        String rateKey = r.frames() == 1 ? "create_galore_bridge.filter_gauge.rate.one" : "create_galore_bridge.filter_gauge.rate";
        tooltip.add(detail(Component.translatable(rateKey, r.used(), r.rate(), r.frames())
                .withStyle(ChatFormatting.GRAY)));
        if (sneaking && r.cells() > 0) {
            tooltip.add(detail(Component.translatable("create_galore_bridge.filter_gauge.size", r.cells(), r.units(), r.room())
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
        return true;
    }

    private static Component detail(Component line) {
        return Component.literal(DETAIL).append(line);
    }
}
