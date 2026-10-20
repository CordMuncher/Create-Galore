package dev.creategalore.fishing.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-side tuning commands for the visuals, only registered with {@code -Dcgfish.debug=true}:
 * <ul>
 *   <li>{@code /cgfishc rodpose first|third <tx> <ty> <tz> <rx> <ry> <rz> <scale>}: the rod's placement in the hand;</li>
 *   <li>{@code /cgfishc rodload <0..1.2>} / {@code /cgfishc rodload off}: force the rod's bend;</li>
 *   <li>{@code /cgfishc holdpose <arm x> <arm y> <fish y> <fish z>}: the two-handed hold in third person.</li>
 * </ul>
 */
final class DebugCommands {
    private DebugCommands() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("cgfishc")
                        .then(Commands.literal("rodpose")
                                .then(Commands.argument("which", StringArgumentType.word())
                                        .then(Commands.argument("values", StringArgumentType.greedyString())
                                                .executes(c -> rodPose(c.getSource(), StringArgumentType.getString(c, "which"),
                                                        StringArgumentType.getString(c, "values"))))))
                        .then(Commands.literal("holdpose")
                                .then(Commands.argument("values", StringArgumentType.greedyString())
                                        .executes(c -> set(c.getSource(), "holdpose", HoldPose.TWO_HANDS,
                                                StringArgumentType.getString(c, "values")))))
                        .then(Commands.literal("rodload")
                                .then(Commands.literal("off").executes(c -> {
                                    RodItemRenderer.debugLoad = Float.NaN;
                                    return 1;
                                }))
                                .then(Commands.argument("load", FloatArgumentType.floatArg(0, 2)).executes(c -> {
                                    RodItemRenderer.debugLoad = FloatArgumentType.getFloat(c, "load");
                                    return 1;
                                })))));
    }

    private static int rodPose(CommandSourceStack source, String which, String values) {
        return set(source, which, which.equals("first") ? RodItemRenderer.FIRST_PERSON : RodItemRenderer.THIRD_PERSON, values);
    }

    /** Sets the numbers in {@code target} from a space-separated list; "_" keeps a value. */
    private static int set(CommandSourceStack source, String which, float[] target, String values) {
        String[] parts = values.trim().split("\\s+");
        for (int i = 0; i < Math.min(parts.length, target.length); i++) {
            if (!parts[i].equals("_")) target[i] = Float.parseFloat(parts[i]);
        }
        source.sendSystemMessage(Component.literal(which + " = " + Arrays.toString(target).toLowerCase(Locale.ROOT)));
        return 1;
    }
}
