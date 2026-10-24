package dev.creategalore.dlsstestkit;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The DLAA test kit: /dlaatest run [modes] plays every camera path at every viewpoint in each anti-aliasing mode and
 * saves screenshots at the same frames (screenshots/dlaa-test/RUN/MODE/...); /dlaatest stop ends a run; /dlaatest
 * where prints the camera, for adding viewpoints to config/create_galore_dlss_testkit.json.
 *
 * -Dcreate_galore_dlss_testkit.autorun=WORLD opens that world from the title screen, runs, and closes the game.
 * -Dcreate_galore_dlss_testkit.modes=a+b runs only those modes, -Dcreate_galore_dlss_testkit.viewpoints=a+b only those
 * viewpoints; -Dcreate_galore_dlss_testkit.quick=true runs only the first viewpoint with the still and flick paths (for
 * slow software rendering).
 */
@Mod(value = TestKit.MOD_ID, dist = Dist.CLIENT)
public final class TestKit {
    public static final String MOD_ID = "create_galore_dlss_testkit";
    private static boolean autorunOpened;

    public TestKit(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(TestKit::commands);
        NeoForge.EVENT_BUS.addListener(Capture::beforeFrame);
        NeoForge.EVENT_BUS.addListener(Capture::afterFrame);
        NeoForge.EVENT_BUS.addListener(MovingScene::serverTick);
        NeoForge.EVENT_BUS.addListener(TestKit::titleScreen);
    }

    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dlaatest")
                .then(Commands.literal("run")
                        .executes(c -> start(null))
                        .then(Commands.argument("modes", StringArgumentType.greedyString())
                                .executes(c -> start(StringArgumentType.getString(c, "modes")))))
                .then(Commands.literal("stop").executes(c -> {
                    Capture.stop("stopped by command");
                    return 1;
                }))
                .then(Commands.literal("where").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    String where = String.format(java.util.Locale.ROOT, "{\"x\": %.2f, \"y\": %.2f, \"z\": %.2f, \"yaw\": %.1f, \"pitch\": %.1f}",
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot());
                    mc.player.displayClientMessage(Component.literal(where), false);
                    return 1;
                })));
    }

    private static int start(String modes) {
        String error = Capture.start(modes != null ? modes : System.getProperty(MOD_ID + ".modes"), false);
        if (error != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("DLAA test: " + error), false);
        }
        return 1;
    }

    // -Dcreate_galore_dlss_testkit.autorun=WORLD: open the world once the title screen shows.
    private static void titleScreen(ScreenEvent.Init.Post event) {
        String world = System.getProperty(MOD_ID + ".autorun");
        if (world == null || autorunOpened || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        autorunOpened = true;
        Minecraft mc = Minecraft.getInstance();
        Capture.armAutorun();
        mc.execute(() -> mc.createWorldOpenFlows().openWorld(world, () -> {
            Capture.log("autorun: could not open world " + world);
            mc.stop();
        }));
    }
}
