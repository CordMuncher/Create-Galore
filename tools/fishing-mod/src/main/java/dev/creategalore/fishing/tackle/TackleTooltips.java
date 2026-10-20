package dev.creategalore.fishing.tackle;

import dev.creategalore.fishing.sim.SizeModel;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The rating lines shown under tackle items (and part slots), from the synced tackle data. */
public final class TackleTooltips {
    private TackleTooltips() {
    }

    private static final String P = "tooltip.create_galore_fishing.";

    static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Adds one or two grey lines describing {@code part}; {@code stack} supplies per-item values (metres left). */
    public static void describe(TacklePart part, ItemStack stack, List<Component> out) {
        switch (part) {
            case TacklePart.Rod r -> {
                out.add(grey(Component.translatable(P + "rod", num(r.length()),
                        Component.translatable(P + "action." + r.action().getSerializedName()), RodStats.kg(r.maxLoad()))));
                out.add(grey(Component.translatable(P + "rod.lures", num(r.lureMin()), num(r.lureMax()))));
                if (!r.reel()) out.add(grey(Component.translatable(P + "rod.pole", num(r.fixedLine()))));
                if (r.lava()) out.add(Component.translatable(P + "rod.lava").withStyle(ChatFormatting.GOLD));
                // Tide's special rods keep Tide's own line for their bonus.
                r.perk().ifPresent(perk -> out.add(Component.translatable("text.tide.rod_tooltip." + perk + "_bonus")
                        .withStyle(ChatFormatting.GOLD)));
            }
            case TacklePart.Reel r -> {
                out.add(grey(Component.translatable(P + "reel", RodStats.kg(r.maxDrag()), num(r.retrieve()), num(r.capacity()))));
                out.add(grey(Component.translatable(P + "reel.smooth", Math.round(r.smooth() * 100))));
            }
            case TacklePart.Line l -> {
                out.add(grey(Component.translatable(P + "line", RodStats.kg(l.test()), Math.round(l.stretch() * 100),
                        Math.round(l.visibility() * 100))));
                out.add(grey(Component.translatable(P + "line.meters", LineItem.meters(stack), l.meters())));
            }
            case TacklePart.Leader l -> out.add(grey(Component.translatable(l.steel() ? P + "leader.steel" : P + "leader",
                    RodStats.kg(l.test()), Math.round(l.visibility() * 100))));
            case TacklePart.Hook h -> {
                out.add(grey(Component.translatable(P + "hook", SizeModel.HOOK_SIZES[Math.max(0, Math.min(h.size(), 11))])));
                h.effect().ifPresent(e -> out.add(Component.translatable(P + "hook.effect." + e).withStyle(ChatFormatting.DARK_AQUA)));
            }
            case TacklePart.Bobber f -> out.add(grey(Component.translatable(P + "float", num(f.weight()))));
            case TacklePart.Sinker s -> out.add(grey(Component.translatable(P + "sinker", num(s.weight()))));
            case TacklePart.Lure l -> {
                out.add(grey(Component.translatable(P + "lure", Component.translatable(P + "bait." + l.bait().getSerializedName()),
                        num(l.weight()))));
                out.add(grey(Component.translatable(P + "lure.run", num(l.dive()), num(l.speedMin()), num(l.speedMax()))));
            }
            case TacklePart.Bait b -> {
                out.add(grey(Component.translatable(b.live() ? P + "bait.live" : P + "bait",
                        Component.translatable(P + "bait." + b.bait().getSerializedName()),
                        Component.translatable(P + "size." + b.size()))));
                if (b.luck() != 0) out.add(Component.translatable(P + "bait.luck", b.luck()).withStyle(ChatFormatting.DARK_AQUA));
                b.effect().ifPresent(e -> out.add(Component.translatable(P + "bait.effect." + e).withStyle(ChatFormatting.DARK_AQUA)));
            }
        }
    }

    static Component grey(Component c) {
        return c.copy().withStyle(ChatFormatting.GRAY);
    }
}
