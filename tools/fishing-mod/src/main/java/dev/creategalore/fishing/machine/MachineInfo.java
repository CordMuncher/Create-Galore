package dev.creategalore.fishing.machine;
import dev.creategalore.fishing.cast.CastHook;
import dev.creategalore.fishing.fish.CatchInfo;
import dev.creategalore.fishing.sim.Gearbox;
import dev.creategalore.fishing.tackle.RodItem;
import dev.creategalore.fishing.tackle.RodParts;
import dev.creategalore.fishing.tackle.RodStats;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * What Engineer's Goggles show on a rod machine: the rod, what the line is doing (waiting, a bite, a fish on and how
 * hard it pulls), drag, bait, reel wear, and for the drives the gear, how much pull the drive can still wind against
 * and its fuel, plus the XP banked. Built on the client from the machine's synced state and its hook, which clients
 * find through {@link #CLIENT_HOOKS}.
 */
public final class MachineInfo {
    private MachineInfo() {
    }

    /** What a drive adds to the readout (null for a plain holder). */
    public record Drive(int gear, double headroomKg, boolean stalled, int fuelTicks, boolean fuelled) {
    }

    /** Client side: each machine's hook, by the machine's position (kept up by the hooks as they tick). */
    public static final Map<BlockPos, CastHook> CLIENT_HOOKS = new HashMap<>();

    private static final String P = "goggles.create_galore_fishing.";

    public static CastHook clientHook(BlockPos pos) {
        CastHook hook = CLIENT_HOOKS.get(pos);
        if (hook == null || hook.isRemoved() || !hook.mountPos().map(pos::equals).orElse(false)) {
            CLIENT_HOOKS.remove(pos);
            return null;
        }
        return hook;
    }

    public static boolean describe(List<Component> tooltip, MountedRod rod, Drive drive) {
        tooltip.add(line(Component.translatable(P + "title").withStyle(ChatFormatting.WHITE), 0));
        ItemStack stack = rod.rod();
        if (stack.isEmpty()) {
            tooltip.add(line(Component.translatable(P + "no_rod").withStyle(ChatFormatting.GRAY), 1));
        } else {
            tooltip.add(line(stack.getHoverName().copy().withStyle(ChatFormatting.GOLD), 1));
            CastHook hook = clientHook(rod.mountPos());
            RodStats stats = RodStats.of(stack).orElse(null);
            if (hook == null) {
                tooltip.add(line(Component.translatable(P + "line_in").withStyle(ChatFormatting.GRAY), 1));
            } else {
                switch (hook.phase()) {
                    case BITE -> tooltip.add(line(Component.translatable(P + "bite").withStyle(ChatFormatting.YELLOW), 1));
                    case FIGHTING -> {
                        Component fish = hook.fishId() == null ? Component.translatable(P + "something")
                                : BuiltInRegistries.ITEM.get(hook.fishId()).getDescription();
                        tooltip.add(line(Component.translatable(P + "fish_on", fish, CatchInfo.weightText(hook.fishKg()))
                                .withStyle(ChatFormatting.AQUA), 1));
                        tooltip.add(line(Component.translatable(P + "tension", percent(hook.tension()))
                                .withStyle(hook.tension() > 0.8f ? ChatFormatting.RED : ChatFormatting.GRAY), 1));
                    }
                    default -> tooltip.add(line(Component.translatable(P + "waiting").withStyle(ChatFormatting.GRAY), 1));
                }
                tooltip.add(line(Component.translatable(P + "line_out", Math.round(hook.lineOut())).withStyle(ChatFormatting.GRAY), 1));
            }
            if (stats != null && stats.reel().isPresent()) {
                tooltip.add(line(Component.translatable(P + "drag", kg(stats.drag())).withStyle(ChatFormatting.GRAY), 1));
            }
            RodParts parts = RodItem.parts(stack);
            ItemStack bait = parts.bait();
            tooltip.add(line(bait.isEmpty() ? Component.translatable(P + "no_bait").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable(P + "bait", bait.getHoverName(), bait.getCount()).withStyle(ChatFormatting.GRAY), 1));
            ItemStack reel = parts.reel();
            if (!reel.isEmpty() && reel.isDamageableItem()) {
                tooltip.add(line(Component.translatable(P + "reel_wear", percent(reel.getDamageValue() / (float) reel.getMaxDamage()))
                        .withStyle(ChatFormatting.GRAY), 1));
            }
        }
        if (drive != null) {
            tooltip.add(line(Component.translatable(P + "gear", String.format(Locale.ROOT, "%.2f", Gearbox.GEARS[drive.gear()]))
                    .withStyle(ChatFormatting.GRAY), 1));
            tooltip.add(line((drive.stalled() ? Component.translatable(P + "stalled").withStyle(ChatFormatting.RED)
                    : Component.translatable(P + "headroom", kg(drive.headroomKg())).withStyle(ChatFormatting.GRAY)), 1));
            if (drive.fuelled()) {
                tooltip.add(line(Component.translatable(P + "fuel", drive.fuelTicks() / 20 / 60, String.format(Locale.ROOT, "%02d",
                        drive.fuelTicks() / 20 % 60)).withStyle(drive.fuelTicks() > 0 ? ChatFormatting.GRAY : ChatFormatting.RED), 1));
            }
        }
        if (rod.bankedXp() > 0) {
            tooltip.add(line(Component.translatable(P + "banked_xp", rod.bankedXp()).withStyle(ChatFormatting.GREEN), 1));
        }
        return true;
    }

    private static MutableComponent line(Component text, int indent) {
        return Component.literal(indent == 0 ? "    " : "     ").append(text);
    }

    private static String percent(float f) {
        return Math.round(f * 100) + "%";
    }

    private static String kg(double kg) {
        return String.format(Locale.ROOT, "%.1f kg", kg);
    }
}
